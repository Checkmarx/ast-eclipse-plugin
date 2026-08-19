package com.checkmarx.eclipse.devassist.scanners.iac;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.wrapper.WrapperProvider;
import com.checkmarx.eclipse.devassist.basescanner.BaseScannerService;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.common.ScannerConfig;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;

/**
 * Realtime IaC scanner service for Eclipse.
 *
 * Manages temporary folder creation, file hash generation, type extraction
 * (Terraform, CloudFormation, Kubernetes, Dockerfile, etc.), execution of
 * Checkmarx IaC real-time scans, and updating ignored issue tracking data.
 */
public class IacScannerService extends BaseScannerService<IacRealtimeResults> {

    private static final String LOG_TAG = "[IAC-SERVICE]";
    private static final String IAC_DIR = "CxIaC";
    private static final String DOCKERFILE = "dockerfile";
    private static final Object SCAN_LOCK = new Object();
    private final WrapperProvider wrapperProvider = new WrapperProvider();

    // Supported glob patterns for IaC files
    private static final List<String> IAC_SUPPORTED_PATTERNS = List.of(
            "*.tf", "*.tf.json",
            "*.yaml", "*.yml",
            "*.json",
            "Dockerfile", "Dockerfile.*", "*.dockerfile", "dockerfile", "dockerfile.*"
    );

    // Supported extensions for IaC files
    private static final Set<String> IAC_FILE_EXTENSIONS = Set.of(
            "tf", "tf.json", "yaml", "yml", "json", "dockerfile"
    );

    private String fileType;

    public IacScannerService(IProject project) {
        super(project, createConfig());
    }

    /**
     * Create default IaC scanner configuration.
     */
    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(ScanEngine.IAC.name())
                .configSection(DevAssistConstants.IAC_REALTIME_SCANNER)
                .activateKey(DevAssistConstants.ACTIVATE_IAC_REALTIME_SCANNER)
                .enabledMessage(DevAssistConstants.IAC_REALTIME_SCANNER_START)
                .disabledMessage(DevAssistConstants.IAC_REALTIME_SCANNER_DISABLED)
                .errorMessage(DevAssistConstants.ERROR_IAC_REALTIME_SCANNER)
                .build();
    }

    /**
     * Checks if the provided file path corresponds to a supported IaC file.
     * Also detects and assigns the appropriate file type (e.g., dockerfile or extension).
     */
    @Override
    protected boolean isFileTypeSupported(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }

        String lowerPath = filePath.toLowerCase();
        List<PathMatcher> pathMatchers = IAC_SUPPORTED_PATTERNS.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());

        Path path = Paths.get(lowerPath);
        for (PathMatcher pathMatcher : pathMatchers) {
            if (pathMatcher.matches(path.getFileName())) {
                fileType = isDockerFile(lowerPath) ? DOCKERFILE : getFileExtension(filePath);
                return true;
            }
        }

        String extension = getFileExtension(filePath);
        if (extension == null) {
            return false;
        }

        fileType = extension.toLowerCase();
        return IAC_FILE_EXTENSIONS.contains(fileType);
    }

    @Override
    public void close() throws Exception {
        // No resources to release
    }

    /**
     * Primary scan method. Converts editor/document contents to a temporary isolated file
     * and executes the real-time IaC scan via CxWrapperFactory.
     */
    public ScanResult<IacRealtimeResults> scan(String filePath, IDocument document, IProject proj) {
        if (!shouldScanFile(filePath)) {
            return null;
        }

        Path tempFolderPath = getSecureTempDirectory();
        Pair<Path, Path> saveResult = null;

        synchronized (SCAN_LOCK) {
            try {
                createTempFolder(tempFolderPath);
                
                String fileContent = getFileContent(filePath, document);
                if (fileContent == null || fileContent.isBlank()) {
                    CxLogger.warning(LOG_TAG + " No content found in file: " + filePath);
                    return null;
                }

                saveResult = saveTempFiles(tempFolderPath, filePath, fileContent);
                if (Objects.nonNull(saveResult)) {
                    String tempFilePath = saveResult.getLeft().toString();
                    CxLogger.info(LOG_TAG + " Start IAC Realtime Scan On File: " + filePath);

                    String containerTool = "docker";
//                    String ignoreFilePath = getIgnoreFilePath(proj);

                    IacRealtimeResults scanResults = null;
					try {
						scanResults = wrapperProvider
						        .iacRealtimeScan(tempFilePath, containerTool, "");
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

                    if (scanResults == null) {
                        return null;
                    }

                    IacScanResultAdaptor scanResultAdaptor = new IacScanResultAdaptor(scanResults, filePath);

                    // Perform secondary scan to sync updated line numbers for ignored issues if needed
//                    updateIgnoredFileDataOnLatestResult(tempFilePath, proj, filePath);

                    return scanResultAdaptor;
                }
            } catch (IOException e) {
                CxLogger.error(LOG_TAG + " Error executing IaC scanner for " + filePath + ": " + e.getMessage(), e);
            } finally {
                CxLogger.info(filePath);
                if (Objects.nonNull(saveResult)) {
                    deleteTempFolder(saveResult.getRight());
                }
            }
        }
        return null;
    }

    /**
     * Compatibility method matching ScannerService interface.
     */
    @Override
    public ScanResult<IacRealtimeResults> scan(String filePath) {
        if (!shouldScanFile(filePath)) {
            return null;
        }
        IDocument liveDocument = com.checkmarx.eclipse.devassist.utils.DevAssistUtils.getLiveDocumentForFile(filePath);
        return scan(filePath, liveDocument != null ? liveDocument : new Document(), project);
    }

    /**
     * Performs a full scan without passing the ignore file to update line numbers of ignored entries.
     */
    private void updateIgnoredFileDataOnLatestResult(String tempFilePath, IProject proj, String filePath) {
//        try {
//            String ignoreFilePath = getIgnoreFilePath(proj);
//            if (ignoreFilePath != null && !ignoreFilePath.isBlank() && new File(ignoreFilePath).exists()) {
//                CxLogger.debug(LOG_TAG + " IaC: Performing full scan without ignore file to update line numbers");
//                
//                IacRealtimeResults fullScanResults = CxWrapperFactory.build()
//                        .iacRealtimeScan(tempFilePath, DevAssistUtils.getContainerTool(), "");
//
//                if (fullScanResults != null) {
//                    IacScanResultAdaptor fullScanResultAdaptor = new IacScanResultAdaptor(fullScanResults, fileType, filePath);
//                    // Hook for updating ignored line markers if IgnoreManager is active
//                }
//            }
//        } catch (IOException | CxException | InterruptedException e) {
//            CxLogger.warning(LOG_TAG + " RTS-IaC: Exception occurred while performing full scan without ignore file: " + e.getMessage());
//        }
    }

    /**
     * Saves file content to an isolated subfolder inside the temporary directory using a hashed name.
     */
    private Pair<Path, Path> saveTempFiles(Path tempFolder, String filePath, String fileContent) throws IOException {
        String fileName = Paths.get(filePath).getFileName().toString();
        Path tempSubFolder = tempFolder.resolve(fileName + "-" + generateFileHash(fileName));
        return createSubFolderAndSaveFile(tempSubFolder, fileName, fileContent);
    }

    /**
     * Creates a target subfolder and writes the file content.
     */
    private Pair<Path, Path> createSubFolderAndSaveFile(Path tempSubFolder, String fileName, String fileContent) throws IOException {
        createTempFolder(tempSubFolder);
        Path fullTargetPath = tempSubFolder.resolve(fileName);
        Files.writeString(fullTargetPath, fileContent, StandardCharsets.UTF_8);
        return Pair.of(fullTargetPath, tempSubFolder);
    }

    /**
     * Generates a 16-character SHA-256 hash derived from the relative file path and timestamp.
     */
    private String generateFileHash(String relativePath) {
        try {
            LocalTime time = LocalTime.now();
            String timeSuffix = String.format("%02d%02d", time.getMinute(), time.getSecond());
            String combined = relativePath + timeSuffix;
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
        return Paths.get(tempOSPath, IAC_DIR).toAbsolutePath().normalize();
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

    protected void deleteTempFolder(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }
        try (var stream = Files.walk(tempDir)) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception e) {
            CxLogger.warning(LOG_TAG + " Failed to clean up temp folder: " + e.getMessage());
        }
    }

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

    private boolean isDockerFile(String filePath) {
        String fileName = Paths.get(filePath).getFileName().toString().toLowerCase();
        return fileName.contains("dockerfile");
    }

    private String getFileExtension(String filePath) {
        if (filePath == null) {
            return null;
        }
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filePath.length() - 1) {
            return filePath.substring(lastDot + 1).toLowerCase();
        }
        return null;
    }

//    private String getIgnoreFilePath(IProject proj) {
//        try {
//            return DevAssistUtils.getIgnoreFilePath(proj);
//        } catch (Exception e) {
//            return "";
//        }
//    }
}