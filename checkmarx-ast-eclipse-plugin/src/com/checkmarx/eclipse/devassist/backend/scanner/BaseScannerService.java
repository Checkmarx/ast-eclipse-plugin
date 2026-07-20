package com.checkmarx.eclipse.devassist.backend.scanner;

import java.util.List;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Abstract base class for all scanner implementations.
 *
 * Provides common functionality:
 * - Common file type filtering (node_modules, .git, etc.)
 * - Logging infrastructure
 * - Project context access
 *
 * Subclasses implement:
 * - shouldScanFile() — file type detection
 * - executeNativeScanner() — actual scan logic
 * - adaptResults() — convert results to ScanIssue model
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
		this.logTag = "[" + getScannerType().name() + "-SCANNER]";
	}

	/**
	 * Check if a file should be scanned by this scanner.
	 *
	 * First applies common filters, then delegates to subclass
	 * for scanner-specific file type detection.
	 *
	 * @param filePath File path to check
	 * @return true if file should be scanned
	 */
	@Override
	public boolean shouldScanFile(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return false;
		}

		// Common exclusions for all scanners
		if (isCommonlyExcluded(filePath)) {
			return false;
		}

		// Subclass-specific file type detection
		return isFileTypeSupported(filePath);
	}

	/**
	 * Apply common filters to all scanners.
	 *
	 * @param filePath File path to check
	 * @return true if file should be excluded
	 */
	private boolean isCommonlyExcluded(String filePath) {
		// Exclude node_modules (JavaScript)
		if (filePath.contains("/node_modules/") || filePath.contains("\\node_modules\\")) {
			return true;
		}

		// Exclude .git directory
		if (filePath.contains("/.git/") || filePath.contains("\\.git\\")) {
			return true;
		}

		// Exclude .vscode, .idea, and other IDE config directories
		if (filePath.contains("/.vscode/") || filePath.contains("\\.vscode\\") ||
			filePath.contains("/.idea/") || filePath.contains("\\.idea\\")) {
			return true;
		}

		// Exclude build directories
		if (filePath.contains("/build/") || filePath.contains("\\build\\") ||
			filePath.contains("/dist/") || filePath.contains("\\dist\\") ||
			filePath.contains("/target/") || filePath.contains("\\target\\")) {
			return true;
		}

		return false;
	}

	/**
	 * Subclasses implement scanner-specific file type detection.
	 *
	 * @param filePath File path to check
	 * @return true if this scanner supports this file type
	 */
	protected abstract boolean isFileTypeSupported(String filePath);

	/**
	 * Execute a scan on a file.
	 *
	 * Handles logging and result adaptation.
	 *
	 * @param filePath Absolute file path to scan
	 * @return Standardized list of ScanIssue objects
	 * @throws Exception if scan fails
	 */
	@Override
	public List<ScanIssue> scan(String filePath) throws Exception {
		if (filePath == null || filePath.isEmpty()) {
			return List.of();
		}

		CxLogger.info(logTag + " Starting scan on: " + filePath);

		try {
			// Execute native scanner and get raw results
			Object rawResults = executeNativeScanner(filePath);

			// Adapt raw results to standard ScanIssue model
			List<ScanIssue> issues = adaptResults(rawResults);

			CxLogger.info(logTag + " ✓ Scan complete, found " + issues.size() + " issues");
			return issues;

		} catch (Exception e) {
			CxLogger.warning(logTag + " Scan failed: " + e.getMessage());
			throw e;
		}
	}

	/**
	 * Subclasses implement native scanner execution.
	 * This calls the actual Checkmarx CLI or wrapper.
	 *
	 * @param filePath File to scan
	 * @return Raw results from scanner (type varies by scanner)
	 * @throws Exception if scan fails
	 */
	protected abstract Object executeNativeScanner(String filePath) throws Exception;

	/**
	 * Subclasses implement result adaptation.
	 * Converts raw scanner output to standard ScanIssue model.
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @return Standardized ScanIssue list
	 */
	protected abstract List<ScanIssue> adaptResults(Object rawResults);

	/**
	 * Get the project this scanner belongs to.
	 *
	 * @return Eclipse project
	 */
	protected IProject getProject() {
		return project;
	}

	/**
	 * Log tag for this scanner.
	 *
	 * @return Logging tag
	 */
	protected String getLogTag() {
		return logTag;
	}

	/**
	 * Default cleanup implementation.
	 * Subclasses override if they have resources to clean up.
	 */
	@Override
	public void close() throws Exception {
		CxLogger.info(logTag + " Closed for project: " + project.getName());
	}
}
