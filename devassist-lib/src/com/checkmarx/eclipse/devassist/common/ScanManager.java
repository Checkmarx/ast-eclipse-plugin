package com.checkmarx.eclipse.devassist.common;

import java.util.ArrayList;
import java.util.List;

import com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType;
import com.checkmarx.eclipse.devassist.basescanner.ScannerService;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * Orchestrates the scanning process for a file.
 *
 * Responsibilities: - Use ScannerFactory to select appropriate scanners for a
 * file - Check if file has changed since last scan (skip redundant scans) -
 * Execute all applicable scanners in sequence - Merge results from multiple
 * scanners - Update file state timestamp to prevent re-scanning
 *
 * This is the main entry point for initiating scans. Called from
 * FileEditorListener when a file is modified.
 *
 * NOTE: Uses backend.DevAssistScanStateHolder (not inspection.version) to maintain compatibility
 * with existing code that passes backend version to super().
 */
public class ScanManager {

	private static final String LOG_TAG = "[SCAN-MANAGER]";

	private final ScannerFactory factory;
	private final com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder stateHolder;

	/**
	 * Create a scan manager for a project.
	 *
	 * @param registry    Project's scanner registry
	 * @param stateHolder State holder for tracking file modification
	 */
	public ScanManager(ScannerRegistry registry, DevAssistScanStateHolder stateHolder) {
		this.factory = new ScannerFactory(registry);
		this.stateHolder = stateHolder;
	}

	/**
	 * Scan a file using all applicable scanners.
	 *
	 * High-level flow: 1. Compute current file state hash 2. Check if file changed
	 * since last scan 3. If unchanged, return cached results 4. Get all scanners
	 * that support this file 5. Execute each scanner sequentially 6. Merge results
	 * from all scanners 7. Update state hash to mark as scanned 8. Return merged
	 * results
	 *
	 * @param filePath Absolute file path to scan
	 * @return List of issues found by all scanners
	 * @throws Exception if scan fails
	 */
	public List<ScanIssue> scanFile(String filePath) throws Exception {
		if (filePath == null || filePath.isEmpty()) {
			
			return List.of();
		}

		
		
		
		

		// 1. Compute current file state hash
		
		long currentStateHash = DevAssistScanStateHolder.computeFileStateHash(filePath);
		

		// 2. Check if file changed since last scan
		// NOTE: hasChanged() atomically marks the file as "in-flight" when it returns true.
		// We MUST call stateHolder.markScanComplete(filePath) once we're done (success or
		// failure) or every subsequent edit will be permanently BLOCKED as "already in-flight".
		if (!stateHolder.hasChanged(filePath, currentStateHash)) {

			return List.of();
		}

		try {
			// 3. Get all scanners that support this file

			List<ScannerService<?>> applicableScanners = factory.getAllSupportedScanners(filePath);


			for (ScannerService<?> scanner : applicableScanners) {
				String displayName = scanner.getConfig() != null ? scanner.getConfig().getEngineName() : "Unknown";

			}

			if (applicableScanners.isEmpty()) {

				// Still update state to avoid re-checking unsupported files
				stateHolder.updateStateHash(filePath, currentStateHash);
				return List.of();
			}

			// 4. Execute all scanners and merge results

			List<ScanIssue> allIssues = new ArrayList<>();
			int scannerIndex = 1;
			int successfulScanners = 0;

			for (ScannerService<?> scanner : applicableScanners) {
				String displayName = scanner.getConfig() != null ? scanner.getConfig().getEngineName() : "Unknown";
				try {
					var scanResult = scanner.scan(filePath);
					List<ScanIssue> scannerResults = scanResult != null ? scanResult.getIssues() : null;

					if (scannerResults != null) {
						for (ScanIssue issue : scannerResults) {
						}
						allIssues.addAll(scannerResults);
					}
					successfulScanners++;

				} catch (Exception e) {
					e.printStackTrace();
				}
				scannerIndex++;
			}

			// 5. Update state hash only if at least one scanner succeeded
			// If all scanners failed, don't update hash so file will be re-scanned on next change
			if (successfulScanners > 0) {
				stateHolder.updateStateHash(filePath, currentStateHash);
			}

			return allIssues;
		} finally {
			// Always release the in-flight marker so the next edit can trigger a scan.
			stateHolder.markScanComplete(filePath);
		}
	}

	/**
	 * Scan a file using a specific scanner type.
	 *
	 * Used when you want to force a scan with a particular scanner, regardless of
	 * file type.
	 *
	 * @param filePath    File to scan
	 * @param scannerType Specific scanner to use
	 * @return Issues from that scanner, or empty list if scanner doesn't support
	 *         file
	 * @throws Exception if scan fails
	 */
	public List<ScanIssue> scanFileWithScanner(String filePath, ScannerType scannerType) throws Exception {

		if (filePath == null || scannerType == null) {
			return List.of();
		}

		CxLogger.info(LOG_TAG + " Starting " + scannerType.getDisplayName() + " scan: " + filePath);

		ScannerService<?> scanner = factory.getScannerForFile(filePath, scannerType);
		if (scanner == null) {
			CxLogger.warning(LOG_TAG + " Scanner does not support file: " + filePath);
			return List.of();
		}

		try {
			var scanResult = scanner.scan(filePath);
			List<ScanIssue> results = scanResult != null ? scanResult.getIssues() : List.of();
			CxLogger.info(LOG_TAG + " Found " + results.size() + " issues");
			return results;
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Scan failed: " + e.getMessage(), e);
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

