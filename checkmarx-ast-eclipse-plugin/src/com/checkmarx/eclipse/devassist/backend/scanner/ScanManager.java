package com.checkmarx.eclipse.devassist.backend.scanner;

import java.util.ArrayList;
import java.util.List;

import com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Orchestrates the scanning process for a file.
 *
 * Responsibilities:
 * - Use ScannerFactory to select appropriate scanners for a file
 * - Check if file has changed since last scan (skip redundant scans)
 * - Execute all applicable scanners in sequence
 * - Merge results from multiple scanners
 * - Update file state timestamp to prevent re-scanning
 *
 * This is the main entry point for initiating scans.
 * Called from FileEditorListener when a file is modified.
 */
public class ScanManager {

	private static final String LOG_TAG = "[SCAN-MANAGER]";

	private final ScannerFactory factory;
	private final DevAssistScanStateHolder stateHolder;

	/**
	 * Create a scan manager for a project.
	 *
	 * @param registry Project's scanner registry
	 * @param stateHolder State holder for tracking file modification
	 */
	public ScanManager(ScannerRegistry registry, DevAssistScanStateHolder stateHolder) {
		this.factory = new ScannerFactory(registry);
		this.stateHolder = stateHolder;
	}

	/**
	 * Scan a file using all applicable scanners.
	 *
	 * High-level flow:
	 * 1. Compute current file state hash
	 * 2. Check if file changed since last scan
	 * 3. If unchanged, return cached results
	 * 4. Get all scanners that support this file
	 * 5. Execute each scanner sequentially
	 * 6. Merge results from all scanners
	 * 7. Update state hash to mark as scanned
	 * 8. Return merged results
	 *
	 * @param filePath Absolute file path to scan
	 * @return List of issues found by all scanners
	 * @throws Exception if scan fails
	 */
	public List<ScanIssue> scanFile(String filePath) throws Exception {
		if (filePath == null || filePath.isEmpty()) {
			return List.of();
		}

		CxLogger.info(LOG_TAG + " Starting scan: " + filePath);

		// 1. Compute current file state hash
		long currentStateHash = DevAssistScanStateHolder.computeFileStateHash(filePath);

		// 2. Check if file changed since last scan
		if (!stateHolder.hasChanged(filePath, currentStateHash)) {
			CxLogger.info(LOG_TAG + " File unchanged, skipping scan (using cache): " +
				filePath);
			return List.of();
		}

		// 3. Get all scanners that support this file
		List<ScannerService> applicableScanners =
			factory.getAllSupportedScanners(filePath);

		if (applicableScanners.isEmpty()) {
			CxLogger.info(LOG_TAG + " No scanners support this file type: " +
				filePath);
			// Still update state to avoid re-checking unsupported files
			stateHolder.updateStateHash(filePath, currentStateHash);
			return List.of();
		}

		// 4. Execute all scanners and merge results
		List<ScanIssue> allIssues = new ArrayList<>();

		for (ScannerService scanner : applicableScanners) {
			try {
				CxLogger.info(LOG_TAG + " Executing " + scanner.getDisplayName());

				List<ScanIssue> scannerResults = scanner.scan(filePath);
				allIssues.addAll(scannerResults);

				CxLogger.info(LOG_TAG + " ✓ " + scanner.getDisplayName() +
					" found " + scannerResults.size() + " issues");

			} catch (Exception e) {
				// Log but continue with other scanners
				CxLogger.warning(LOG_TAG + " " + scanner.getDisplayName() +
					" failed: " + e.getMessage());
			}
		}

		// 5. Update state hash to mark as scanned
		stateHolder.updateStateHash(filePath, currentStateHash);

		CxLogger.info(LOG_TAG + " ✓ Scan complete, total issues: " + allIssues.size());
		return allIssues;
	}

	/**
	 * Scan a file using a specific scanner type.
	 *
	 * Used when you want to force a scan with a particular scanner,
	 * regardless of file type.
	 *
	 * @param filePath File to scan
	 * @param scannerType Specific scanner to use
	 * @return Issues from that scanner, or empty list if scanner doesn't support file
	 * @throws Exception if scan fails
	 */
	public List<ScanIssue> scanFileWithScanner(String filePath,
		ScannerService.ScannerType scannerType) throws Exception {

		if (filePath == null || scannerType == null) {
			return List.of();
		}

		CxLogger.info(LOG_TAG + " Starting " + scannerType.getDisplayName() +
			" scan: " + filePath);

		ScannerService scanner = factory.getScannerForFile(filePath, scannerType);
		if (scanner == null) {
			CxLogger.warning(LOG_TAG + " " + scannerType.getDisplayName() +
				" does not support file: " + filePath);
			return List.of();
		}

		try {
			List<ScanIssue> results = scanner.scan(filePath);
			CxLogger.info(LOG_TAG + " ✓ " + scannerType.getDisplayName() +
				" found " + results.size() + " issues");
			return results;
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " " + scannerType.getDisplayName() +
				" scan failed: " + e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Get factory statistics.
	 *
	 * @return Summary string
	 */
	public String getStatistics() {
		return factory.getStatistics();
	}
}
