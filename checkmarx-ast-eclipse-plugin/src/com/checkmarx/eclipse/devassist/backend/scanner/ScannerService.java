package com.checkmarx.eclipse.devassist.backend.scanner;

import java.util.List;

import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;

/**
 * Interface for all scanner implementations.
 *
 * Each scanner (OSS, Secrets, ASCA, Containers, IAC) implements this
 * to provide consistent scan execution and file type detection.
 */
public interface ScannerService extends AutoCloseable {

	/**
	 * Check if this scanner supports a file type.
	 *
	 * @param filePath File path to check
	 * @return true if this scanner can scan this file
	 */
	boolean shouldScanFile(String filePath);

	/**
	 * Execute a scan on a file.
	 *
	 * @param filePath Absolute file path to scan
	 * @return List of issues found by this scanner
	 * @throws Exception if scan fails
	 */
	List<ScanIssue> scan(String filePath) throws Exception;

	/**
	 * Get the display name of this scanner.
	 *
	 * @return Human-readable name (e.g., "Open Source Supply Chain")
	 */
	String getDisplayName();

	/**
	 * Get the scanner type.
	 *
	 * @return Scanner type enum
	 */
	ScannerType getScannerType();

	/**
	 * Cleanup resources when scanner is no longer needed.
	 */
	@Override
	void close() throws Exception;

	/**
	 * Scanner type enumeration.
	 */
	enum ScannerType {
		OSS("Open Source Supply Chain"),
		SECRETS("Secrets Scanning"),
		CONTAINERS("Container Scanning"),
		IAC("Infrastructure as Code"),
		ASCA("Application Security Code Analysis");

		private final String displayName;

		ScannerType(String displayName) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return displayName;
		}
	}
}
