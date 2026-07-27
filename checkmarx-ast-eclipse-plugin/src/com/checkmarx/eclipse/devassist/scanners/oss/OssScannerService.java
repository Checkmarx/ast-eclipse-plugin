package com.checkmarx.eclipse.devassist.scanners.oss;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.factory.CxWrapperFactory;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.backend.DevAssistUtils;
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
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
public class OssScannerService {

	private static final String LOG_TAG = "[OSS-SERVICE]";
	private static final String OSS_DIR = "CxOSS";
	private static final Object SCAN_LOCK = new Object();

	private static final List<String> MANIFEST_FILE_PATTERNS = List.of(
			"package.json", "package-lock.json", "npm-shrinkwrap.json",
			"pom.xml",
			"go.mod", "go.sum",
			"requirements.txt", "Pipfile", "Pipfile.lock", "setup.py",
			"Gemfile", "Gemfile.lock",
			"Cargo.toml", "Cargo.lock",
			"composer.json", "composer.lock",
			"packages.config", "*.csproj",
			"yarn.lock", ".npm"
	);

	private final IProject project;

	public OssScannerService(IProject project) {
		this.project = project;
	}

	public String getScannerName() {
		return "OSS";
	}

	/**
	 * Checks whether the supplied file path matches any of the manifest glob patterns.
	 */
	public boolean isFileTypeSupported(String filePath) {
		if (filePath == null) {
			return false;
		}

		Path path = Paths.get(filePath);
		List<PathMatcher> pathMatchers = MANIFEST_FILE_PATTERNS.stream()
				.map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
				.collect(Collectors.toList());

		for (PathMatcher pathMatcher : pathMatchers) {
			if (pathMatcher.matches(path.getFileName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determines if a given file should be scanned by the OSS scanner.
	 */
	public boolean shouldScanFile(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return false;
		}
		String normalized = filePath.replace("\\", "/");
		return !normalized.contains("/node_modules/") && isFileTypeSupported(filePath);
	}

	public void close() throws Exception {
		// No resources to close
	}

	/**
	 * Primary scan method - gets file content, isolates into temp folder with companion files, and executes scan.
	 */
	public ScanResult<OssRealtimeResults> scan(String filePath, IDocument document, IProject proj) {
		if (!shouldScanFile(filePath)) {
			return null;
		}

		String fileContent = getFileContent(filePath, document);
		if (fileContent == null || fileContent.isBlank()) {
			CxLogger.warning(LOG_TAG + " Could not read or empty file content: " + filePath);
			return null;
		}

		Path tempSubFolder = getTempSubFolderPath(filePath);

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
//				String ignoreFilePath = getIgnoreFilePath(proj);

				OssRealtimeResults scanResults = CxWrapperFactory.build().ossRealtimeScan(mainTempPath.get(), "");
				if (scanResults == null) {
					return null;
				}

				OssScanResultAdaptor scanResultAdaptor = new OssScanResultAdaptor(scanResults, filePath);

				// Performs secondary scan if needed to keep line numbers updated for ignored packages
//				updateIgnoredFileDataOnLatestResult(mainTempPath.get(), proj, filePath);

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
	 * Compatibility method matching ScannerService interface returning issue lists.
	 */
	public List<ScanIssue> scan(String filePath) throws Exception {
		if (!shouldScanFile(filePath)) {
			return List.of();
		}
		ScanResult<OssRealtimeResults> result = scan(filePath, new Document(), project);
		return result != null ? result.getIssues() : List.of();
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
	 * Copies a companion lock file (e.g., package-lock.json) into the temporary directory
	 * when it exists alongside the scanned manifest.
	 */
	private void saveCompanionFile(Path tempFolderPath, String originalFilePath) {
		if (originalFilePath == null || originalFilePath.isEmpty() || tempFolderPath == null) {
			return;
		}

		Path originalPath = Paths.get(originalFilePath);
		String parentFileName = originalPath.getFileName().toString();
		String companionFileName = getCompanionFileName(parentFileName);

		if (companionFileName.isEmpty()) {
			return;
		}

		Path parentPath = originalPath.getParent();
		if (parentPath == null) {
			return;
		}

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

	/**
	 * Infers companion lock file name based on manifest file name.
	 */
	private String getCompanionFileName(String fileName) {
		if ("package.json".equalsIgnoreCase(fileName)) {
			return "package-lock.json";
		}
		if (fileName.toLowerCase().endsWith(".csproj")) {
			return "package.lock.json";
		}
		return "";
	}

	/**
	 * Resolves temporary sub-folder path allocated for the file scan.
	 */
	private Path getTempSubFolderPath(String filePath) {
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

	private void createTempFolder(Path tempDir) throws IOException {
		if (!Files.exists(tempDir)) {
			Files.createDirectories(tempDir);
		}
	}

	private void deleteTempFolder(Path tempDir) {
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

//	private String getIgnoreFilePath(IProject proj) {
//		try {
//			return DevAssistUtils.getIgnoreFilePath(proj);
//		} catch (Exception e) {
//			return "";
//		}
//	}
}