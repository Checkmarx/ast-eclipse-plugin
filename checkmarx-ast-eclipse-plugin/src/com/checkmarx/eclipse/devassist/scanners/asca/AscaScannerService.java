package com.checkmarx.eclipse.devassist.scanners.asca;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.factory.CxWrapperFactory;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * ASCA (Application Source Code Analysis) scanner service.
 *
 * Scans source code files for vulnerabilities using the CxWrapper. Includes
 * comprehensive file handling, temporary file management with security checks,
 * and proper error handling.
 *
 * Adapted from JetBrains implementation for Eclipse platform.
 */
public class AscaScannerService {

	private final IProject project;
	private static final String LOG_TAG = "[ASCA-SERVICE]";
	private static final String ASCA_DIR = "CxASCA";
	private static final Object SCAN_LOCK = new Object();

	// Supported extensions for ASCA scanning (based on VSCode/JetBrains
	// implementation)
	private static final String[] SUPPORTED_EXTENSIONS = { "java", "py", "js", "jsx", "ts", "tsx", "go", "rb", "cs",
			"cpp" };

	public AscaScannerService(IProject project) {
		this.project = project;
	}

	private String getScannerName() {
		return "ASCA";
	}

	private String getLogTag() {
		return LOG_TAG;
	}

	/**
	 * Check if file has a supported extension for ASCA scanning.
	 */
	private boolean isFileTypeSupported(String filePath) {
		if (filePath == null) {
			return false;
		}

		String lowerPath = filePath.toLowerCase();
		for (String ext : SUPPORTED_EXTENSIONS) {
			if (lowerPath.endsWith("." + ext)) {
				return true;
			}
		}
		return false;
	}

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
	 * Primary scan method - gets file content and executes scan.
	 */
	public ScanResult<Object> scan(String filePath, IDocument document, IProject proj) {
		if (!shouldScanFile(filePath)) {
			return null;
		}
		try {
			// Get file content from document or file system
			String fileContent = getFileContent(filePath, document);
			if (fileContent == null) {
				CxLogger.warning(getLogTag() + " Could not read file content: " + filePath);
				return null;
			}
			// Run ASCA scan with proper temp file management
			Object rawResults = runAscaScan(filePath, fileContent);
			if (rawResults == null) {
				return null;
			}
			return new AscaScanResultAdaptor((com.checkmarx.ast.asca.ScanResult) rawResults, filePath);
		} catch (Exception e) {
			CxLogger.error(getLogTag() + " Scan failed: " + e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Get file content from document (if available) or from file system.
	 */

	private String getFileContent(String filePath, IDocument document) {
		// 1. Try reading from the in-memory document buffer first
		if (document != null) {
			String content = document.get();
			if (content != null && !content.isEmpty()) {
				return content;
			}
		}
		if (filePath == null || filePath.isBlank()) {
			return null;
		}
		// 2. Try resolving filesystem location via Workspace without taking workspace
		// locks
		java.nio.file.Path nioPath = null;
		try {
			org.eclipse.core.runtime.IPath eclipsePath = new org.eclipse.core.runtime.Path(filePath);
			IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(eclipsePath);

			if (file != null && file.getLocation() != null) {
				// Get direct OS filesystem path from IFile (prevents blocking
				// file.getContents() lock)
				nioPath = file.getLocation().toFile().toPath();
			}
		} catch (Exception e) {
			// Fallback if path isn't a valid workspace path
		}
		if (nioPath == null) {
			try {
				nioPath = java.nio.file.Paths.get(filePath);
			} catch (Exception e) {
				return null;
			}
		}
		// 3. Perform standard Java NIO read on physical path (Interrupt-safe)
		try {
			if (java.nio.file.Files.exists(nioPath) && java.nio.file.Files.isRegularFile(nioPath)) {
				return java.nio.file.Files.readString(nioPath, java.nio.charset.StandardCharsets.UTF_8);
			}
		} catch (java.io.IOException e) {
			CxLogger.warning(getLogTag() + " Failed to read file content from disk: " + e.getMessage());
		} catch (Exception e) {
			if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
				// Restore interrupted flag without failing the application
				Thread.currentThread().interrupt();
				CxLogger.warning(getLogTag() + " File reading interrupted for: " + filePath);
			} else {
				CxLogger.warning(getLogTag() + " Unexpected error reading file: " + e.getMessage());
			}
		}
		return null;
	}

	/**
	 * Run ASCA scan with synchronized temp file management. Ensures temp files are
	 * properly created and cleaned up.
	 */
	private Object runAscaScan(String filePath, String fileContent) {
		synchronized (SCAN_LOCK) {
			String tempFilePath = saveTempFile(Paths.get(filePath).getFileName().toString(), fileContent);
			if (tempFilePath == null) {
				CxLogger.warning(getLogTag() + " Failed to create temporary file");
				return null;
			}

			try {
				CxLogger.info(getLogTag() + " Starting ASCA scan: " + filePath);
				String ignoreFilePath = getIgnoreFilePath();
				Object scanResult = executeAscaScanner(tempFilePath, ignoreFilePath);
				CxLogger.info(getLogTag() + " ASCA scan completed");
				return scanResult;
			} finally {
				deleteFile(tempFilePath);
			}
		}
	}

	/**
	 * Execute ASCA scan using CxWrapperFactory.
	 */
	private Object executeAscaScanner(String filePath, String ignoreFilePath) {
		try {
			return scanAscaFile(filePath, true, "Eclipse", ignoreFilePath);
		} catch (Exception e) {
			CxLogger.error(getLogTag() + " ASCA scan error: " + e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Get ignore file path for ASCA scanning.
	 * Returns empty string by default - can be extended to read from .checkmarxIgnored file.
	 */
	private String getIgnoreFilePath() {
		return "";
	}

	/**
	 * Get secure temporary directory with validation. Prevents directory traversal
	 * attacks.
	 */
	private Path getSecureTempDirectory() throws SecurityException {
		try {
			String tempOSPath = System.getProperty("java.io.tmpdir");
			if (tempOSPath == null || tempOSPath.trim().isEmpty()) {
				throw new SecurityException("System temp directory not available");
			}

			Path baseTempDir = Paths.get(tempOSPath).toAbsolutePath().normalize();

			if (!Files.exists(baseTempDir) || !Files.isDirectory(baseTempDir)) {
				throw new SecurityException("System temp directory not valid: " + baseTempDir);
			}

			Path ascaTempDir = baseTempDir.resolve(ASCA_DIR).normalize();

			// Security check: ensure ASCA dir is within system temp
			if (!ascaTempDir.startsWith(baseTempDir)) {
				throw new SecurityException("ASCA temp directory outside system temp");
			}

			return ascaTempDir;

		} catch (Exception e) {
			throw new SecurityException("Failed to create secure temp directory", e);
		}
	}

	private String saveTempFile(String fileName, String fileContent) {
		try {
			// Get secure temp directory
			Path tempDir = getSecureTempDirectory();
			createTempFolder(tempDir);

			// Sanitize fileName to prevent directory traversal attacks
			String sanitizedFileName = sanitizeFileName(fileName);

			// Create secure path with normalization
			Path tempFilePath = tempDir.resolve(sanitizedFileName).normalize();

			// Security check: ensure the resolved path is still within the temp directory
			if (!tempFilePath.startsWith(tempDir)) {
				return null;
			}

			Files.write(tempFilePath, fileContent.getBytes());
			return tempFilePath.toAbsolutePath().toString();
		} catch (SecurityException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Create temp folder if it doesn't exist.
	 */
	private void createTempFolder(Path tempDir) throws IOException {
		if (!Files.exists(tempDir)) {
			Files.createDirectories(tempDir);
		}
	}

	/**
	 * Sanitize file name to prevent directory traversal attacks.
	 */
	private String sanitizeFileName(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			return "temp_asca.tmp";
		}

		// Remove path separators and dangerous characters
		String sanitized = fileName.replaceAll("[/\\\\:*?\"<>|]", "_").replaceAll("\\.\\.+", ".") // Replace multiple
																									// dots
				.trim();

		if (sanitized.isEmpty() || sanitized.equals(".") || sanitized.equals("..")) {
			sanitized = "temp_asca.tmp";
		}

		// Limit length for filesystem compatibility
		if (sanitized.length() > 200) {
			String extension = "";
			int lastDot = sanitized.lastIndexOf('.');
			if (lastDot > 0) {
				extension = sanitized.substring(lastDot);
				sanitized = sanitized.substring(0, Math.min(200 - extension.length(), lastDot));
			} else {
				sanitized = sanitized.substring(0, 200);
			}
			sanitized = sanitized + extension;
		}

		return sanitized;
	}

	/**
	 * Delete temporary file with security checks.
	 */
	private void deleteFile(String filePath) {
		if (filePath == null || filePath.trim().isEmpty()) {
			return;
		}

		try {
			Path path = Paths.get(filePath).toAbsolutePath().normalize();
			Path tempDir = getSecureTempDirectory();

			// Security check: only delete files in temp directory
			if (!path.startsWith(tempDir)) {
				CxLogger.warning(getLogTag() + " Security violation: file outside temp: " + filePath);
				return;
			}

			Files.deleteIfExists(path);
			CxLogger.info(getLogTag() + " Temporary file deleted: " + path);

		} catch (SecurityException e) {
			CxLogger.error(getLogTag() + " Security error deleting file: " + e.getMessage(), e);
		} catch (IOException e) {
			CxLogger.warning(getLogTag() + " Failed to delete temp file: " + filePath);
		} catch (Exception e) {
			CxLogger.warning(getLogTag() + " Unexpected error deleting temp file: " + e.getMessage());
		}
	}

	/**
	 * Compatibility method matching basescanner.ScannerService interface.
	 */
	public List<ScanIssue> scan(String filePath) throws Exception {
		if (!shouldScanFile(filePath)) {
			return List.of();
		}
		var result = scan(filePath, new Document(), project);
		return result != null ? result.getIssues() : List.of();
	}

	private com.checkmarx.ast.asca.ScanResult scanAscaFile(String path, boolean ascaLatestVersion, String agent,
			String ignoreFilePath) throws IOException, CxException, InterruptedException {
		com.checkmarx.ast.asca.ScanResult scanResult = null;
		try {
			scanResult = CxWrapperFactory.build().ScanAsca(path, ascaLatestVersion, agent, null);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (CxException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return scanResult;
	}

}
