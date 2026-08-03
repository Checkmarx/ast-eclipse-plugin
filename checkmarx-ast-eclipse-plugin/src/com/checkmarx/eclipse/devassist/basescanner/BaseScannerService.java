package com.checkmarx.eclipse.devassist.basescanner;

import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Base implementation of ScannerService providing common functionality.
 *
 * Provides:
 * - File filtering (node_modules exclusion, etc.)
 * - Temporary folder management
 * - Template methods for subclasses
 */
public abstract class BaseScannerService implements ScannerService {

	protected final IProject project;
	protected final String logTag;

	/**
	 * Create a scanner for a project.
	 *
	 * @param project Eclipse project
	 */
	public BaseScannerService(IProject project) {
		this.project = project;
		this.logTag = "[" + getScannerName() + "-SCANNER]";
	}

	/**
	 * Check if file should be scanned.
	 *
	 * Applies common exclusions then delegates to subclass for type checking.
	 *
	 * @param filePath File path
	 * @param project Eclipse project
	 * @return true if file should be scanned
	 */
	@Override
	public boolean shouldScanFile(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return false;
		}

		// Common exclusions
		if (isCommonlyExcluded(filePath)) {
			return false;
		}

		return isFileTypeSupported(filePath);
	}

	/**
	 * Apply common exclusions.
	 *
	 * @param filePath File path
	 * @return true if file should be excluded
	 */
	private boolean isCommonlyExcluded(String filePath) {
		return filePath.contains("/node_modules/") || filePath.contains("\\node_modules\\");
	}

	/**
	 * Subclasses implement scanner-specific file type checking.
	 *
	 * @param filePath File path
	 * @return true if scanner supports this file
	 */
	protected abstract boolean isFileTypeSupported(String filePath);

	/**
	 * Get the scanner name for logging (e.g., "OSS", "SECRETS").
	 *
	 * @return Scanner name
	 */
	protected abstract String getScannerName();

	/**
	 * Get the log tag for this scanner.
	 *
	 * @return Log tag
	 */
	protected String getLogTag() {
		return logTag;
	}

	/**
	 * Build path to temp sub-folder in system temp directory.
	 *
	 * @param baseDir Sub-folder name
	 * @return Absolute path to temp directory
	 */
	protected String getTempSubFolderPath(String baseDir) {
		String tempOS = System.getProperty("java.io.tmpdir");
		Path tempDir = Paths.get(tempOS, baseDir);
		return tempDir.toString();
	}

	/**
	 * Create temp folder if it doesn't exist.
	 *
	 * @param folderPath Folder path
	 */
	protected void createTempFolder(Path folderPath) {
		try {
			Files.createDirectories(folderPath);
		} catch (IOException e) {
			CxLogger.warning("Failed to create temp folder: " + folderPath);
		}
	}

	/**
	 * Recursively delete temp folder and contents.
	 *
	 * @param tempFolder Folder to delete
	 */
	protected void deleteTempFolder(Path tempFolder) {
		if (Files.notExists(tempFolder)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(tempFolder)) {
			walk.sorted(Comparator.reverseOrder())
				.forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (Exception e) {
						CxLogger.warning("Failed to delete temp file: " + path);
					}
				});
		} catch (IOException e) {
			CxLogger.warning("Failed to delete temp folder: " + tempFolder);
		}
	}

	/**
	 * Close the scanner and release resources.
	 *
	 * @throws Exception if close fails
	 */
	public void close() throws Exception {
		CxLogger.info(logTag + " Closed for project: " + project.getName());
	}
}
