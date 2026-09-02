package com.checkmarx.eclipse.devassist.basescanner;

import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.common.ScannerConfig;

/**
 * Generic interface for scanner services.
 * Each scanner produces a specific result type T.
 *
 * @param <T> The result type produced by this scanner
 */
public interface ScannerService<T> {

	/**
	 * Check if this scanner should scan the file.
	 *
	 * @param filePath File path
	 * @return true if file should be scanned
	 */
	boolean shouldScanFile(String filePath);

	/**
	 * Perform a scan on the file and return result.
	 *
	 * @param filePath File path
	 * @return ScanResult of type T or null
	 */
	ScanResult<T> scan(String filePath);

	/**
	 * Get the scanner configuration.
	 *
	 * @return Scanner config
	 */
	ScannerConfig getConfig();

	/**
	 * Close scanner and release resources.
	 *
	 * @throws Exception if close fails
	 */
	void close() throws Exception;
}
