package com.checkmarx.eclipse.devassist.basescanner;

import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.common.ScannerConfig;
import com.checkmarx.eclipse.common.utils.CxLogger;
import org.eclipse.core.resources.IProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Base implementation of {@link ScannerService} that wires respective ScannerConfig called
 * from different scannerServices.
 * Provides helpers for deciding when to scan files and scanners managing temporary folders.
 *
 * @param <T> is type of ScanResult produced by concrete scanner Scan method implementations
 */
public abstract class BaseScannerService<T> implements ScannerService<T> {

	protected final IProject project;
	public ScannerConfig config;
	private static final String LOG_TAG = "[SCANNER-SERVICE]";

	/**
	 * Creates a new scanner service with the supplied configuration.
	 *
	 * @param project Eclipse project
	 * @param config configuration values to be used by the scanner
	 */
	public BaseScannerService(IProject project, ScannerConfig config) {
		this.project = project;
		this.config = config;
	}

	/**
	 * Determines whether the file at the given path should be scanned.
	 * Files inside /node_modules/ are skipped by default.
	 *
	 * @param filePath absolute or project-relative file path
	 * @return true if the file should be scanned; false otherwise
	 */
	@Override
	public boolean shouldScanFile(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return false;
		}

		// Common exclusions
		if (filePath.contains("/node_modules/") || filePath.contains("\\node_modules\\")) {
			return false;
		}

		return isFileTypeSupported(filePath);
	}

	/**
	 * Subclasses implement scanner-specific file type checking.
	 *
	 * @param filePath File path
	 * @return true if scanner supports this file
	 */
	protected abstract boolean isFileTypeSupported(String filePath);

	/**
	 * Perform scan - subclasses must implement this.
	 *
	 * @param filePath File to scan
	 * @return ScanResult of type T or null
	 */
	@Override
	public abstract ScanResult<T> scan(String filePath);

	/**
	 * Get the configuration.
	 *
	 * @return Scanner config
	 */
	@Override
	public ScannerConfig getConfig() {
		return config;
	}

	/**
	 * Builds the path to a temporary sub-folder within the system temp directory.
	 *
	 * @param baseDir name of the sub-folder to create under java.io.tmpdir
	 * @return absolute path string for the temporary sub-folder
	 */
	protected String getTempSubFolderPath(String baseDir) {
		String tempOS = System.getProperty("java.io.tmpdir");
		Path tempDir = Paths.get(tempOS, baseDir);
		return tempDir.toString();
	}

	/**
	 * Ensures that the specified temporary folder exists, creating any missing directories.
	 *
	 * @param folderPath target temporary folder path
	 */
	protected void createTempFolder(Path folderPath) {
		try {
			Files.createDirectories(folderPath);
		} catch (IOException e) {
			CxLogger.warning("Failed to create temporary folder:" + folderPath);
		}
	}

	/**
	 * Recursively deletes the provided temporary folder and files in it, if it has been created.
	 *
	 * @param tempFolder root path of the temporary folder to remove
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
							CxLogger.warning("Failed to delete file in temp folder:" + path);
						}
					});
		} catch (IOException e) {
			CxLogger.warning("Failed to delete temporary folder:" + tempFolder);
		}
	}

	/**
	 * Close the scanner and release resources.
	 *
	 * @throws Exception if close fails
	 */
	@Override
	public void close() throws Exception {
		CxLogger.info(LOG_TAG + " Closed for project: " + project.getName());
	}
}
