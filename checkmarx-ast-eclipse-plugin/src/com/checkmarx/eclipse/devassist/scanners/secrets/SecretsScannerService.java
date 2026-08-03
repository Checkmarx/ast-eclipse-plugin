package com.checkmarx.eclipse.devassist.scanners.secrets;

import com.checkmarx.ast.secretsrealtime.SecretsRealtimeResults;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.eclipse.devassist.basescanner.BaseScannerService;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.common.ScannerConfig;
import com.checkmarx.eclipse.devassist.factory.CxWrapperFactory;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.backend.DevAssistUtils;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.Document;
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
 * Realtime Secrets scanner service for Eclipse.
 *
 * Manages temporary directory creation, file hashing, file exclusion filtering,
 * execution of Checkmarx Secrets real-time scans via CxWrapperFactory, and updating
 * line numbers for ignored secrets.
 */
public class SecretsScannerService extends BaseScannerService<SecretsRealtimeResults> {

    private static final String LOG_TAG = "[SECRETS-SERVICE]";
    private static final String SECRETS_DIR = "CxSecrets";
    private static final Object SCAN_LOCK = new Object();

    // Glob patterns for manifest files that should be excluded from Secrets scanning
    private static final List<String> MANIFEST_FILE_PATTERNS = List.of(
            "package.json", "pom.xml", "go.mod", "requirements.txt", 
            "Gemfile", "Cargo.toml", "composer.json", "package-lock.json", "yarn.lock"
    );

    public SecretsScannerService(IProject project) {
        super(project, createConfig());
    }

    /**
     * Create default Secrets scanner configuration.
     */
    public static ScannerConfig createConfig() {
        return ScannerConfig.builder()
                .engineName(ScanEngine.SECRETS.name())
                .configSection(DevAssistConstants.SECRETS_REALTIME_SCANNER)
                .activateKey(DevAssistConstants.ACTIVATE_SECRETS_REALTIME_SCANNER)
                .enabledMessage(DevAssistConstants.SECRETS_REALTIME_SCANNER_START)
                .disabledMessage(DevAssistConstants.SECRETS_REALTIME_SCANNER_DISABLED)
                .errorMessage(DevAssistConstants.ERROR_SECRETS_REALTIME_SCANNER)
                .build();
    }

    /**
     * Determines whether a file should be excluded from Secrets scanning.
     */
    private boolean isExcludedFileForSecretsScanning(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return true;
        }

        Path path = Paths.get(filePath.toLowerCase());
        List<PathMatcher> manifestMatchers = MANIFEST_FILE_PATTERNS.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());

        for (PathMatcher matcher : manifestMatchers) {
            if (matcher.matches(path.getFileName())) {
                return true;
            }
        }

        // Exclude Checkmarx ignore list files
        String normalized = filePath.replace("\\", "/");
        return normalized.contains("/.checkmarxIgnored") ||
               normalized.contains("/.checkmarxIgnoredTempList");
    }

    @Override
    protected boolean isFileTypeSupported(String filePath) {
        return !isExcludedFileForSecretsScanning(filePath);
    }

    @Override
    public void close() throws Exception {
        // No resources to release
    }

    /**
     * Primary scan method. Converts editor/document contents to an isolated temporary file
     * and executes the real-time Secrets scan via CxWrapperFactory.
     */
    public ScanResult<SecretsRealtimeResults> scan(String filePath, IDocument document, IProject proj) {
        if (!shouldScanFile(filePath)) {
            return null;
        }

        Path tempSubFolder = getTempSubFolderPathAsPath(filePath);

        synchronized (SCAN_LOCK) {
            try {
                createTempFolder(tempSubFolder);

                String fileContent = getFileContent(filePath, document);
                if (fileContent == null || fileContent.isBlank()) {
                    CxLogger.warning(filePath + " Secrets scanner: file content is empty or unreadable");
                    return null;
                }

                Optional<String> tempFilePath = saveFileForScanning(tempSubFolder, filePath, fileContent);
                if (tempFilePath.isEmpty()) {
                    CxLogger.warning(LOG_TAG + " Secrets scanner: failed to save file - " + filePath);
                    return null;
                }

                CxLogger.info(LOG_TAG + " Starting scan: " + filePath);
//                String ignoreFilePath = getIgnoreFilePath(proj);

                SecretsRealtimeResults scanResults = CxWrapperFactory.build()
                        .secretsRealtimeScan(tempFilePath.get(), "");

                if (scanResults == null) {
                    CxLogger.warning(LOG_TAG + " Secrets scanner: no results returned - " + filePath);
                    return null;
                }

                int secretCount = scanResults.getSecrets() != null ? scanResults.getSecrets().size() : 0;
                CxLogger.info(LOG_TAG + " Scan completed: " + filePath + " (" + secretCount + " secrets found)");

                SecretsScanResultAdaptor scanResultAdaptor = new SecretsScanResultAdaptor(scanResults, filePath);

                // Perform secondary scan to update line numbers for ignored entries if required
                updateIgnoredFileDataOnLatestResult(tempFilePath.get(), proj, filePath);

                return scanResultAdaptor;

            } catch (Exception e) {
                CxLogger.error(LOG_TAG + " Secrets scanner error for " + filePath + ": " + e.getMessage(), e);
            } finally {
                CxLogger.warning(LOG_TAG + " Cleaning up temp folder: " + tempSubFolder);
                deleteTempFolder(tempSubFolder);
            }
        }
        return null;
    }

    /**
     * Compatibility method matching ScannerService interface.
     */
    @Override
    public ScanResult<SecretsRealtimeResults> scan(String filePath) {
        if (!shouldScanFile(filePath)) {
            return null;
        }
        return scan(filePath, new Document(), project);
    }

    /**
     * Performs a full scan without passing the ignore file to update line numbers of ignored entries.
     */
    private void updateIgnoredFileDataOnLatestResult(String tempFilePath, IProject proj, String filePath) {
////        String ignoreFilePath = getIgnoreFilePath(proj);
//		if (ignoreFilePath != null && !ignoreFilePath.isBlank() && new File(ignoreFilePath).exists()) {
//		    CxLogger.warning(LOG_TAG + " Secrets: Performing full scan without ignore file to update line numbers");
//
//		    SecretsRealtimeResults fullScanResults = null;
//			try {
//				fullScanResults = CxWrapperFactory.build()
//				        .secretsRealtimeScan(tempFilePath, "");
//			} catch (Exception e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//
//		    if (fullScanResults != null) {
//		        SecretsScanResultAdaptor fullScanResultAdaptor = new SecretsScanResultAdaptor(fullScanResults, filePath);
//		        // Hook for updating ignored line markers if IgnoreManager is active
//		    }
//		}
    }

    /**
     * Resolves a unique subfolder path for storing the temporary file.
     */
    private Path getTempSubFolderPathAsPath(String originalFilePath) {
        Path baseTempPath = getSecureTempDirectory();
        String safeFileName = toSafeTempFileName(originalFilePath);
        return baseTempPath.resolve(safeFileName);
    }

    /**
     * Creates a deterministic, filesystem-safe file name containing base name and a hash suffix.
     */
    private String toSafeTempFileName(String filePath) {
        String baseName = Paths.get(filePath).getFileName().toString();
        String hash = generateFileHash(filePath);
        return baseName + "-" + hash;
    }

    /**
     * Generates a 16-character SHA-256 hash derived from the relative file path, timestamp, and UUID.
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

    /**
     * Saves the content into a temporary file inside the target subfolder.
     */
    private Optional<String> saveFileForScanning(Path tempSubFolder, String originalFilePath, String fileContent) throws IOException {
        String fileName = Paths.get(originalFilePath).getFileName().toString();
        Path tempFilePath = tempSubFolder.resolve(fileName);
        Files.writeString(tempFilePath, fileContent, StandardCharsets.UTF_8);
        return Optional.of(tempFilePath.toString());
    }

    private Path getSecureTempDirectory() {
        String tempOSPath = System.getProperty("java.io.tmpdir");
        return Paths.get(tempOSPath, SECRETS_DIR).toAbsolutePath().normalize();
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

//    private String getIgnoreFilePath(IProject proj) {
//        try {
//            return DevAssistUtils.getIgnoreFilePath(proj);
//        } catch (Exception e) {
//            return "";
//        }
//    }
}