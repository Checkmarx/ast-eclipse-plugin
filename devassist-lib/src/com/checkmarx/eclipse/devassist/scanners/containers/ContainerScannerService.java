package com.checkmarx.eclipse.devassist.scanners.containers;

import com.checkmarx.ast.containersrealtime.ContainersRealtimeResults;
import com.checkmarx.eclipse.devassist.basescanner.BaseScannerService;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;
import com.checkmarx.eclipse.devassist.common.ScannerConfig;
import com.checkmarx.eclipse.devassist.factory.CxWrapperFactory;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.common.utils.CxLogger;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Container image scanner service for Eclipse.
 *
 * Handles file detection (Docker, Docker Compose, Helm), secure temporary folder management,
 * and direct invocation of Checkmarx Container Realtime scanning via CxWrapperFactory.
 */
public class ContainerScannerService extends BaseScannerService<ContainersRealtimeResults> {

    private static final String LOG_TAG = "[CONTAINER-SERVICE]";
    private static final String CONTAINER_DIR = "CxContainer";
    private static final Object SCAN_LOCK = new Object();

    private static final List<String> CONTAINERS_FILE_PATTERNS = List.of(
            "**/dockerfile*",
            "**/*.containerfile",
            "**/*.image",
            "**/docker-compose*.yml",
            "**/docker-compose*.yaml"
    );

    private static final List<String> CONTAINER_HELM_EXCLUDED_FILES = List.of(
            "chart.yaml",
            "chart.yml",
            "values.yaml",
            "values.yml"
    );

    private String fileType;

    public ContainerScannerService(IProject project) {
        super(project, createConfig());
    }

    /**
     * Create default Container scanner configuration.
     */
    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(ScanEngine.CONTAINERS.name())
                .configSection(DevAssistConstants.CONTAINER_REALTIME_SCANNER)
                .activateKey(DevAssistConstants.ACTIVATE_CONTAINER_REALTIME_SCANNER)
                .enabledMessage(DevAssistConstants.CONTAINER_REALTIME_SCANNER_START)
                .disabledMessage(DevAssistConstants.CONTAINER_REALTIME_SCANNER_DISABLED)
                .errorMessage(DevAssistConstants.ERROR_CONTAINER_REALTIME_SCANNER)
                .build();
    }

    @Override
    protected boolean isFileTypeSupported(String filePath) {
        return isContainersFilePatternMatching(filePath) || isHelmFile(filePath);
    }

    /**
     * Checks whether the supplied file path matches container file patterns (Dockerfile, Docker Compose, etc.).
     */
    private boolean isContainersFilePatternMatching(String filePath) {
        String lowerPath = filePath.toLowerCase();
        List<PathMatcher> pathMatchers = CONTAINERS_FILE_PATTERNS.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());

        Path path = Paths.get(lowerPath);
        for (PathMatcher pathMatcher : pathMatchers) {
            if (pathMatcher.matches(path) || lowerPath.contains("dockerfile")) {
                if (DevAssistUtils.isDockerComposeFile(lowerPath)) {
                    this.fileType = DevAssistUtils.DOCKER_COMPOSE;
                } else if (DevAssistUtils.isDockerFile(lowerPath)) {
                    this.fileType = DevAssistUtils.DOCKERFILE;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the supplied file path is part of a Helm chart.
     */
    public boolean isHelmFile(String filePath) {
        if (filePath == null) {
            return false;
        }
        String lowerPath = filePath.toLowerCase();
        if (DevAssistUtils.isYamlFile(lowerPath)) {
            String fileName = Paths.get(filePath).getFileName().toString().toLowerCase();
            if (CONTAINER_HELM_EXCLUDED_FILES.contains(fileName)) {
                return false;
            }
            if (lowerPath.contains("/helm/")) {
                this.fileType = DevAssistUtils.HELM;
                return true;
            }
        }
        return false;
    }

    /**
     * Primary scan method. Reads content, creates isolated temporary directory structure,
     * executes the container realtime scan, and updates ignored issues.
     */
    public ScanResult<ContainersRealtimeResults> scan(String filePath, IDocument document, IProject proj) {
        if (!shouldScanFile(filePath)) {
            return null;
        }

        synchronized (SCAN_LOCK) {
            String fileContent = getFileContent(filePath, document);
            if (fileContent == null || fileContent.isBlank()) {
                CxLogger.warning(LOG_TAG + " Could not read or file empty: " + filePath);
                return null;
            }

            Path tempBaseDir = getSecureTempDirectory();
            Path tempSubFolder = null;
            Path tempFilePath = null;

            try {
                String fileName = Paths.get(filePath).getFileName().toString();
                String prefix = isHelmFile(filePath) ? "helm-" : fileName + "-";
                String folderName = prefix + generateFileHash(filePath);

                tempSubFolder = tempBaseDir.resolve(folderName).normalize();
                createTempFolder(tempSubFolder);

                tempFilePath = tempSubFolder.resolve(fileName).normalize();
                Files.writeString(tempFilePath, fileContent, StandardCharsets.UTF_8);

                CxLogger.info(LOG_TAG + " Start Container Realtime Scan On File: " + filePath);
//                String ignoreFilePath = DevAssistUtils.getIgnoreFilePath(proj != null ? proj : this.project);

                ContainersRealtimeResults scanResults = null;
				try {
					scanResults = CxWrapperFactory.build().containersRealtimeScan(tempFilePath.toString(), "");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

                updateIgnoredFileDataOnLatestResult(tempFilePath.toString(), proj != null ? proj : this.project, filePath);

                return new ContainerScanResultAdaptor(scanResults, this.fileType, filePath);

            } catch (IOException e) {
                CxLogger.error(LOG_TAG + " Container Realtime Scan failed: " + e.getMessage(), e);
            } finally {
                if (Objects.nonNull(tempSubFolder)) {
                    deleteTempFolder(tempSubFolder);
                }
            }
        }
        return null;
    }

    /**
     * Re-runs scan without ignore settings to calculate line updates for ignored entries.
     */
    private void updateIgnoredFileDataOnLatestResult(String tempFilePath, IProject proj, String filePath) {
//        try {
//            IgnoreManager ignoreManager = new IgnoreManager(proj);
//            if (ignoreManager.hasIgnoredEntries(ScanEngine.CONTAINERS)) {
//                CxLogger.info(LOG_TAG + " Performing full scan to update line numbers for ignored packages");
//                ContainersRealtimeResults fullScanResults = CxWrapperFactory.build()
//                        .containersRealtimeScan(tempFilePath, "");
//
//                if (fullScanResults != null) {
//                    ContainerScanResultAdaptor fullScanResultAdaptor = new ContainerScanResultAdaptor(fullScanResults, this.fileType, filePath);
//                    ignoreManager.updateLineNumbersForIgnoredEntries(fullScanResultAdaptor, filePath);
//                }
//            }
//        } catch (Exception e) {
//            CxLogger.warning(LOG_TAG + " Exception occurred while updating ignored file line numbers: " + e.getMessage());
//        }
    }

    /**
     * Reads file content from Eclipse IDocument buffer or disk filesystem.
     */
    private String getFileContent(String filePath, IDocument document) {
        if (document != null) {
            String content = document.get();
            if (content != null && !content.isEmpty()) {
                return content;
            }
        }

        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        try {
            Path nioPath = Paths.get(filePath);
            if (Files.exists(nioPath) && Files.isRegularFile(nioPath)) {
                return Files.readString(nioPath, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            CxLogger.warning(LOG_TAG + " Failed to read file content from disk: " + e.getMessage());
        }
        return null;
    }

    /**
     * Generates a unique 16-character hexadecimal hash using SHA-256 for temporary directory names.
     */
    private String generateFileHash(String relativePath) {
        try {
            LocalTime time = LocalTime.now();
            String timeSuffix = String.format("%02d%02d", time.getMinute(), time.getSecond());
            String combined = relativePath + timeSuffix + UUID.randomUUID().toString().substring(0, 5);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString((relativePath + System.currentTimeMillis()).hashCode());
        }
    }

    private Path getSecureTempDirectory() {
        String tempOSPath = System.getProperty("java.io.tmpdir");
        if (tempOSPath == null || tempOSPath.isBlank()) {
            tempOSPath = System.getProperty("user.home");
        }
        Path baseTempDir = Paths.get(tempOSPath).toAbsolutePath().normalize();
        return baseTempDir.resolve(CONTAINER_DIR).normalize();
    }

    protected void createTempFolder(Path tempDir) {
        if (!Files.exists(tempDir)) {
            try {
                Files.createDirectories(tempDir);
            } catch (IOException e) {
                CxLogger.warning(LOG_TAG + " Failed to create temp folder: " + e.getMessage());
            }
        }
    }

    protected void deleteTempFolder(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            CxLogger.info(LOG_TAG + " Temporary folder deleted: " + path.toAbsolutePath());
        } catch (Exception e) {
            CxLogger.warning(LOG_TAG + " Failed to delete temporary directory: " + e.getMessage());
        }
    }

    /**
     * Compatibility method matching ScannerService interface.
     */
    @Override
    public ScanResult<ContainersRealtimeResults> scan(String filePath) {
        if (!shouldScanFile(filePath)) {
            return null;
        }
        IDocument liveDocument = DevAssistUtils.getLiveDocumentForFile(filePath);
        return scan(filePath, liveDocument, project);
    }

    @Override
    public void close() throws Exception {
        // No persistent connections to close
    }
}