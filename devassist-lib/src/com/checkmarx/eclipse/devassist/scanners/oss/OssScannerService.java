package com.checkmarx.eclipse.devassist.scanners.oss;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.eclipse.devassist.basescanner.BaseScannerService;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.common.ScannerConfig;
import com.checkmarx.eclipse.devassist.factory.CxWrapperFactory;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.devassist.utils.PackageManager;
import com.checkmarx.eclipse.common.utils.CxLogger;
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
 * Realtime OSS manifest scanner service for Eclipse that handles temporary file isolation,
 * companion lock file resolution (e.g. package-lock.json), and invocation of the Checkmarx OSS engine.
 *
 * Adapted to mirror JetBrains scanner service features.
 */
public class OssScannerService extends BaseScannerService<OssRealtimeResults> {

	private static final String LOG_TAG = "[OSS-SERVICE]";
	private static final String OSS_DIR = "CxOSS";
	private static final Object SCAN_LOCK = new Object();

	public OssScannerService(IProject project) {
		super(project, createConfig());
	}

	/**
	 * Create default OSS scanner configuration.
	 */
	public static ScannerConfig createConfig() {
		return ScannerConfig.builder()
				.engineName(ScanEngine.OSS.name())
				.configSection(DevAssistConstants.OSS_REALTIME_SCANNER)
				.activateKey(DevAssistConstants.ACTIVATE_OSS_REALTIME_SCANNER)
				.enabledMessage(DevAssistConstants.OSS_REALTIME_SCANNER_START)
				.disabledMessage(DevAssistConstants.OSS_REALTIME_SCANNER_DISABLED)
				.errorMessage(DevAssistConstants.ERROR_OSS_REALTIME_SCANNER)
				.build();
	}

	@Override
	protected boolean isFileTypeSupported(String filePath) {
		if (filePath == null) {
			return false;
		}

		Path path = Paths.get(filePath);
		List<PathMatcher> pathMatchers = PackageManager.getAllPatterns().stream()
				.map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
				.collect(Collectors.toList());

		for (PathMatcher pathMatcher : pathMatchers) {
			if (pathMatcher.matches(path)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void close() throws Exception {
		// No resources to close
	}

	@Override
	public com.checkmarx.eclipse.devassist.common.ScanResult<OssRealtimeResults> scan(String filePath) {
		IDocument liveDocument = com.checkmarx.eclipse.devassist.utils.DevAssistUtils.getLiveDocumentForFile(filePath);
		return scanWithDocument(filePath, liveDocument != null ? liveDocument : new Document());
	}

	/**
	 * Primary scan method - gets file content, isolates into temp folder with companion files, and executes scan.
	 */
	public ScanResult<OssRealtimeResults> scanWithDocument(String filePath, IDocument document) {
		if (!shouldScanFile(filePath)) {
			return null;
		}

		String fileContent = getFileContent(filePath, document);
		if (fileContent == null || fileContent.isBlank()) {
			CxLogger.warning(LOG_TAG + " Could not read or empty file content: " + filePath);
			return null;
		}

		Path tempSubFolder = getTempSubFolderPathAsPath(filePath);

		synchronized (SCAN_LOCK) {
			try {
				createTempFolder(tempSubFolder);

				Optional<String> mainTempPath = saveMainManifestFile(tempSubFolder, filePath, fileContent);
				if (mainTempPath.isEmpty()) {
					return null;
				}

				// Copy companion lock file (e.g., package-lock.json) into temp folder if available
				saveCompanionFile(tempSubFolder, filePath);

				CxLogger.info(LOG_TAG + " Starting Realtime OSS Scan on File: " + filePath);

				OssRealtimeResults scanResults = CxWrapperFactory.build().ossRealtimeScan(mainTempPath.get(), "");
				if (scanResults == null) {
					return null;
				}

				OssScanResultAdaptor scanResultAdaptor = new OssScanResultAdaptor(scanResults, filePath);

				return scanResultAdaptor;

			} catch (Exception e) {
				CxLogger.error(LOG_TAG + " Scan failed for file " + filePath + ": " + e.getMessage(), e);
				return null;
			} finally {
				CxLogger.info(LOG_TAG + " Deleting temporary OSS folder");
				deleteTempFolder(tempSubFolder);
			}
		}
	}

	/**
	 * Performs full scan without passing ignore file to update line numbers of ignored entries.
	 */
//	private void updateIgnoredFileDataOnLatestResult(String tempFilePath, IProject proj, String filePath) {
//		try {
//			// Extension point for ignore manager syncing when ignore files are active
//			String ignoreFilePath = getIgnoreFilePath(proj);
//			if (ignoreFilePath != null && !ignoreFilePath.isBlank() && new File(ignoreFilePath).exists()) {
//				CxLogger.info(LOG_TAG + " Performing full scan to update line numbers for ignored packages");
//				OssRealtimeResults fullScanResults = CxWrapperFactory.build().ossRealtimeScan(tempFilePath, "");
//				if (fullScanResults != null && fullScanResults.getPackages() != null) {
//					OssScanResultAdaptor fullScanResultAdaptor = new OssScanResultAdaptor(fullScanResults, filePath);
//					// Connects with ignore manager line number updater if implemented
//				}
//			}
//		} catch (Exception e) {
//			CxLogger.warning(LOG_TAG + " Exception occurred while performing full scan without ignore file: " + e.getMessage());
//		}
//	}

	/**
	 * Persists the main manifest file into the temporary directory for scanning.
	 */
	private Optional<String> saveMainManifestFile(Path tempSubFolder, String originalFilePath, String fileContent) {
		try {
			String fileName = Paths.get(originalFilePath).getFileName().toString();
			Path tempFilePath = tempSubFolder.resolve(fileName);
			Files.writeString(tempFilePath, fileContent, StandardCharsets.UTF_8);
			return Optional.of(tempFilePath.toString());
		} catch (IOException e) {
			CxLogger.warning(LOG_TAG + " Failed to write main manifest temp file: " + e.getMessage());
			return Optional.empty();
		}
	}

	/**
     * Copies companion lock files (e.g., package-lock.json, yarn.lock) into the temporary directory
     * when they exist alongside the scanned manifest.
     *
     * @param tempFolderPath   temp directory where companion files should be written
     * @param originalFilePath original manifest path used to locate companion files
     */
	private void saveCompanionFile(Path tempFolderPath, String originalFilePath) {
		if (originalFilePath == null || originalFilePath.isEmpty() || tempFolderPath == null) {
			return;
		}
		Path originalPath = Paths.get(originalFilePath);
		String parentFileName = originalPath.getFileName().toString();
		List<String> companionFileNameList = PackageManager.getCompanionFileNames(parentFileName);

		if (companionFileNameList.isEmpty()) {
			return;
		}

		Path parentPath = originalPath.getParent();
		if (parentPath == null) {
			return;
		}
		for (String companionFileName : companionFileNameList) {
			Path companionOriginalPath = parentPath.resolve(companionFileName);
			if (!Files.exists(companionOriginalPath)) {
				return;
			}

			Path companionTempPath = tempFolderPath.resolve(companionFileName);
			try {
				Files.copy(companionOriginalPath, companionTempPath, StandardCopyOption.REPLACE_EXISTING);
				CxLogger.info(LOG_TAG + " Copied companion file: " + companionFileName);
			} catch (IOException e) {
				CxLogger.warning(LOG_TAG + " Error occurred while saving companion file: " + e.getMessage());
			}
		}
	}

	/**
	 * Resolves temporary sub-folder path allocated for the file scan.
	 */
	private Path getTempSubFolderPathAsPath(String filePath) {
		String baseTempPath = System.getProperty("java.io.tmpdir");
		Path baseDir = Paths.get(baseTempPath).resolve(OSS_DIR);
		String relativePath = Paths.get(filePath).getFileName().toString();
		return baseDir.resolve(toSafeTempFileName(relativePath, filePath));
	}

	/**
	 * Creates a deterministic, filesystem-safe file name for storing the manifest in the temp directory.
	 */
	private String toSafeTempFileName(String relativePath, String fullPath) {
		String baseName = Paths.get(relativePath).getFileName().toString();
		String hash = generateFileHash(fullPath);
		return baseName + "-" + hash;
	}

	/**
	 * Generates a short hash based on the manifest path and current time to avoid collisions.
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

	protected void createTempFolder(Path tempDir) {
		try {
			if (!Files.exists(tempDir)) {
				Files.createDirectories(tempDir);
			}
		} catch (IOException e) {
			CxLogger.warning(LOG_TAG + " Failed to create temporary folder: " + e.getMessage());
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
}