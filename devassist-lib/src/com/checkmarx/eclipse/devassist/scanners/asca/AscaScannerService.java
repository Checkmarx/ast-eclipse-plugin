package com.checkmarx.eclipse.devassist.scanners.asca;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.wrapper.WrapperProvider;
import com.checkmarx.eclipse.devassist.basescanner.BaseScannerService;
import com.checkmarx.eclipse.devassist.common.ScannerConfig;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.devassist.utils.ScanEngine;

/**
 * ASCA (Application Source Code Analysis) scanner service.
 *
 * Scans source code files for vulnerabilities using the CxWrapper. Includes
 * comprehensive file handling, temporary file management with security checks,
 * and proper error handling.
 *
 * Adapted from JetBrains implementation for Eclipse platform.
 */
public class AscaScannerService extends BaseScannerService<ScanResult> {

	private static final String LOG_TAG = "[ASCA-SERVICE]";
	private static final String ASCA_DIR = "CxASCA";
	private static final Object SCAN_LOCK = new Object();
	private final WrapperProvider wrapperProvider = new WrapperProvider();

	public AscaScannerService(IProject project) {
		super(project, createConfig());
	}

	/**
	 * Create default ASCA scanner configuration.
	 */
	public static ScannerConfig createConfig() {
		return ScannerConfig.builder()
				.engineName(ScanEngine.ASCA.name())
				.configSection(DevAssistConstants.ASCA_REALTIME_SCANNER)
				.activateKey(DevAssistConstants.ACTIVATE_ASCA_REALTIME_SCANNER)
				.enabledMessage(DevAssistConstants.ASCA_REALTIME_SCANNER_START)
				.disabledMessage(DevAssistConstants.ASCA_REALTIME_SCANNER_DISABLED)
				.errorMessage(DevAssistConstants.ERROR_ASCA_REALTIME_SCANNER)
				.build();
	}

	@Override
	protected boolean isFileTypeSupported(String filePath) {
		if (filePath == null) {
			return false;
		}

		String lowerPath = filePath.toLowerCase();
		for (String ext : DevAssistConstants.ASCA_SUPPORTED_EXTENSIONS) {
			if (lowerPath.endsWith("." + ext)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public com.checkmarx.eclipse.devassist.common.ScanResult<ScanResult> scan(String filePath) {
		IDocument liveDocument = com.checkmarx.eclipse.devassist.utils.DevAssistUtils.getLiveDocumentForFile(filePath);
		com.checkmarx.eclipse.devassist.common.ScanResult<Object> result = scanWithDocument(filePath,
				liveDocument != null ? liveDocument : new Document());
		return (com.checkmarx.eclipse.devassist.common.ScanResult<ScanResult>) (com.checkmarx.eclipse.devassist.common.ScanResult<?>) result;
	}

	/**
	 * Primary scan method - gets file content and executes scan.
	 */
	public com.checkmarx.eclipse.devassist.common.ScanResult<Object> scanWithDocument(String filePath,
			IDocument document) {
		return scanInternal(filePath, document, project);
	}

	@Override
	public void close() throws Exception {
		// No resources to close
	}

	private com.checkmarx.eclipse.devassist.common.ScanResult<Object> scanInternal(String filePath, IDocument document,
			IProject proj) {
		if (!shouldScanFile(filePath)) {
			return null;
		}
		try {
			// Get file content from document or file system
			String fileContent = getFileContent(filePath, document);
			if (fileContent == null) {
				CxLogger.warning(LOG_TAG + " Could not read file content: " + filePath);
				return null;
			}
			// Run ASCA scan with proper temp file management
			Object rawResults = runAscaScan(filePath, fileContent);
			if (rawResults == null) {
				return null;
			}
			return new AscaScanResultAdaptor((com.checkmarx.ast.asca.ScanResult) rawResults, filePath);
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Scan failed: " + e.getMessage(), e);
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
			CxLogger.warning(LOG_TAG + " Failed to read file content from disk: " + e.getMessage());
		} catch (Exception e) {
			if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
				// Restore interrupted flag without failing the application
				Thread.currentThread().interrupt();
				CxLogger.warning(LOG_TAG + " File reading interrupted for: " + filePath);
			} else {
				CxLogger.warning(LOG_TAG + " Unexpected error reading file: " + e.getMessage());
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
				CxLogger.warning(LOG_TAG + " Failed to create temporary file");
				return null;
			}

			try {
				CxLogger.info(LOG_TAG + " Starting ASCA scan: " + filePath);
				String ignoreFilePath = getIgnoreFilePath();
				Object scanResult = executeAscaScanner(tempFilePath, ignoreFilePath);
				CxLogger.info(LOG_TAG + " ASCA scan completed");
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
			CxLogger.error(LOG_TAG + " ASCA scan error: " + e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Get ignore file path for ASCA scanning.
	 * Returns empty string by default - can be extended to read from
	 * .checkmarxIgnored file.
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
				CxLogger.warning(LOG_TAG + " Security violation: file outside temp: " + filePath);
				return;
			}

			Files.deleteIfExists(path);
			CxLogger.info(LOG_TAG + " Temporary file deleted: " + path);

		} catch (SecurityException e) {
			CxLogger.error(LOG_TAG + " Security error deleting file: " + e.getMessage(), e);
		} catch (IOException e) {
			CxLogger.warning(LOG_TAG + " Failed to delete temp file: " + filePath);
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Unexpected error deleting temp file: " + e.getMessage());
		}
	}

	private com.checkmarx.ast.asca.ScanResult scanAscaFile(String path, boolean ascaLatestVersion, String agent,
			String ignoreFilePath) throws IOException, CxException, InterruptedException {
		com.checkmarx.ast.asca.ScanResult scanResult = null;
		try {
			scanResult = wrapperProvider.scanAsca(path, ascaLatestVersion, agent, null);
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
