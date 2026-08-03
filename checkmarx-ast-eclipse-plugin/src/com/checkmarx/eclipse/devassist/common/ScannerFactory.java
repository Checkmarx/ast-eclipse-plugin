package com.checkmarx.eclipse.devassist.common;

import java.util.ArrayList;
import java.util.List;

import com.checkmarx.eclipse.devassist.backend.GlobalScannerController;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType;
import com.checkmarx.eclipse.devassist.basescanner.ScannerService;
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.resources.IProject;

/**
 * Factory for selecting appropriate scanners by file type.
 *
 * Responsibilities:
 * - Query all available scanners
 * - Filter by file type compatibility
 * - Filter by global enabled state
 * - Return ordered list of applicable scanners
 *
 * Mirrors the JetBrains ScannerFactory pattern.
 */
public class ScannerFactory {

	private static final String LOG_TAG = "[SCANNER-FACTORY]";

	private final ScannerRegistry registry;
	private final GlobalScannerController controller;

	/**
	 * Create a scanner factory for a project.
	 *
	 * @param registry Project's scanner registry
	 */
	public ScannerFactory(ScannerRegistry registry) {
		this.registry = registry;
		this.controller = GlobalScannerController.getInstance();
	}

	/**
	 * Get all enabled scanners that support a file.
	 *
	 * Queries all scanner types, filters by:
	 * 1. Global enabled state (GlobalScannerController)
	 * 2. File type support (ScannerService.shouldScanFile())
	 *
	 * @param filePath File to scan
	 * @return List of applicable scanners (empty if none match)
	 */
	public List<ScannerService> getAllSupportedScanners(String filePath) {
		List<ScannerService> supported = new ArrayList<>();

		CxLogger.info(LOG_TAG + " Finding scanners for: " + filePath);

		// Check each scanner type
		for (ScannerType type : ScannerType.values()) {
			// Check if globally enabled
			if (!controller.isScannerEnabled(type)) {
				CxLogger.info(LOG_TAG + " ⊘ " + type.getDisplayName() + " disabled globally");
				continue;
			}

			// Get scanner from registry
			ScannerService scanner = getScannerService(type);
			if (scanner == null) {
				CxLogger.warning(LOG_TAG + " Scanner not initialized: " + type);
				continue;
			}

			// Check if supports this file type
			if (scanner.shouldScanFile(filePath)) {
				supported.add(scanner);
				CxLogger.info(LOG_TAG + " ✓ " + type.getDisplayName() + " supports file");
			} else {
				CxLogger.info(LOG_TAG + " ⊘ " + type.getDisplayName() + " does not support file");
			}
		}

		if (supported.isEmpty()) {
			CxLogger.info(LOG_TAG + " No scanners support this file");
		} else {
			CxLogger.info(LOG_TAG + " ✓ Found " + supported.size() + " supporting scanner(s)");
		}

		return supported;
	}

	/**
	 * Get a specific scanner by type if it supports the file.
	 *
	 * @param filePath File to scan
	 * @param type Scanner type to retrieve
	 * @return Scanner if enabled and supports file, null otherwise
	 */
	public ScannerService getScannerForFile(String filePath, ScannerType type) {
		if (filePath == null || type == null) {
			return null;
		}

		// Check if globally enabled
		if (!controller.isScannerEnabled(type)) {
			CxLogger.info(LOG_TAG + " " + type.getDisplayName() + " is disabled globally");
			return null;
		}

		// Get scanner from registry
		ScannerService scanner = getScannerService(type);
		if (scanner == null) {
			CxLogger.warning(LOG_TAG + " Scanner not initialized: " + type);
			return null;
		}

		// Check if supports file type
		if (!scanner.shouldScanFile(filePath)) {
			CxLogger.info(LOG_TAG + " " + type.getDisplayName() + " does not support file: " +
				filePath);
			return null;
		}

		return scanner;
	}

	/**
	 * Get a scanner service by type.
	 * Retrieves from the registry which manages scanner lifecycle.
	 *
	 * @param type Scanner type
	 * @return Scanner instance, or null if not available
	 */
	private ScannerService getScannerService(ScannerType type) {
		try {
			Object scanner = registry.getScannerService(type);
			return scanner instanceof ScannerService ? (ScannerService) scanner : null;
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error getting scanner for type " + type + ": " + e.getMessage());
			return null;
		}
	}

	/**
	 * Get scanner by file name pattern (useful for quick lookups).
	 * Returns the primary scanner for a file type.
	 *
	 * @param filePath File path
	 * @return Primary scanner type for this file, or null
	 */
	public ScannerType getPrimaryScannerType(String filePath) {
		if (filePath == null) {
			return null;
		}

		String lowerPath = filePath.toLowerCase();

		// Manifest files → OSS
		if (lowerPath.matches(".*\\.(package\\.json|pom\\.xml|go\\.mod|requirements\\.txt|" +
			"Gemfile|Cargo\\.toml|Pipfile)$")) {
			return ScannerType.OSS;
		}

		// Source code files → ASCA
		if (lowerPath.matches(".*\\.(java|py|js|ts|cpp|cs|go|php|rb|swift)$")) {
			return ScannerType.ASCA;
		}

		// Infrastructure files → IAC
		if (lowerPath.matches(".*\\.(tf|yaml|yml|json|hcl)$")) {
			return ScannerType.IAC;
		}

		// Container files → CONTAINERS
		if (lowerPath.matches(".*(Dockerfile|docker-compose\\.ya?ml)")) {
			return ScannerType.CONTAINERS;
		}

		// Everything else can be scanned for secrets
		return ScannerType.SECRETS;
	}

	/**
	 * Get factory statistics.
	 *
	 * @return Summary string
	 */
	public String getStatistics() {
		int enabledCount = controller.getEnabledScannerCount();
		return "Scanners enabled: " + enabledCount + "/" + ScannerType.values().length;
	}
}
