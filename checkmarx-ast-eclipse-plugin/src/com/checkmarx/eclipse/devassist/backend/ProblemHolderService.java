package com.checkmarx.eclipse.devassist.backend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.e4.core.services.events.IEventBroker;

import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginUtils;

/**
 * In-memory cache for scan results (ScanIssue), keyed by file path.
 *
 * Thread-safe via ConcurrentHashMap. Used to avoid redundant scans
 * and to enable result restoration when files are reopened.
 *
 * Mirrors JetBrains ProblemHolderService pattern with Eclipse IEventBroker for notifications.
 */
public class ProblemHolderService {

	private static final String LOG_TAG = "[PROBLEM-HOLDER]";
	public static final String ISSUES_UPDATED_TOPIC = "com/checkmarx/issues/updated";

	// Session property key for storing service in project
	public static final String SERVICE_KEY = ProblemHolderService.class.getName() +
		".INSTANCE";

	private final ConcurrentHashMap<String, List<ScanIssue>> fileToScanIssues =
		new ConcurrentHashMap<>();
	
    /**
     * Returns the instance of this service for the given project.
     *
     * @param project the project.
     * @return the instance of this service for the given project.
     */
	public static ProblemHolderService getInstance(IProject project) {
        return ProblemHolderService.getInstance(project);
    }

	/**
	 * Cache scan issues for a file.
	 *
	 * @param filePath Absolute file path
	 * @param issues Issues found by scanners
	 */
	public void addScanIssues(String filePath, List<ScanIssue> issues) {
		if (filePath == null || issues == null) {
			return;
		}
		fileToScanIssues.put(filePath, new ArrayList<>(issues));
		// **KEY: Notify all listeners of the update (JetBrains pattern)**
		publishIssuesUpdated();
	}

	/**
	 * Get cached scan issues for a file.
	 *
	 * @param filePath Absolute file path
	 * @return Cached issues or empty list
	 */
	public List<ScanIssue> getScanIssuesByFile(String filePath) {
		if (filePath == null) {
			return Collections.emptyList();
		}

		List<ScanIssue> cached = fileToScanIssues.get(filePath);
		return cached != null ? new ArrayList<>(cached) : Collections.emptyList();
	}

	/**
	 * Get all cached issues across all files.
	 *
	 * @return Map of file path â†’ issues
	 */
	public Map<String, List<ScanIssue>> getAllScanIssues() {

		Map<String, List<ScanIssue>> result = new HashMap<>();
		for (Map.Entry<String, List<ScanIssue>> entry : fileToScanIssues.entrySet()) {
			result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		int totalIssues = result.values().stream().mapToInt(List::size).sum();
		return result;
	}

	/**
	 * Merge new issues with existing issues for a file.
	 * Deduplicates by issue ID.
	 *
	 * @param filePath Absolute file path
	 * @param newIssues Issues to merge
	 */
	public void mergeScanIssues(String filePath, List<ScanIssue> newIssues) {
		if (filePath == null || newIssues == null) {
			return;
		}

		List<ScanIssue> existing = fileToScanIssues.getOrDefault(filePath, new ArrayList<>());
		Map<String, ScanIssue> merged = new HashMap<>();

		// Add existing issues
		for (ScanIssue issue : existing) {
			merged.put(issue.getScanIssueId(), issue);
		}

		// Add/override with new issues (by ID)
		for (ScanIssue issue : newIssues) {
			merged.put(issue.getScanIssueId(), issue);
		}

		fileToScanIssues.put(filePath, new ArrayList<>(merged.values()));
		CxLogger.info(LOG_TAG + " Merged " + newIssues.size() + " issues for: " + filePath);

		// **KEY: Notify listeners when cache is modified**
		publishIssuesUpdated();
	}

	/**
	 * Clear cached issues for a file.
	 *
	 * @param filePath Absolute file path
	 */
	public void removeScanIssues(String filePath) {
		if (filePath == null) {
			return;
		}

		fileToScanIssues.remove(filePath);
		CxLogger.info(LOG_TAG + " Cleared cache for: " + filePath);
	}

	/**
	 * Remove cached scan issues for a specific scanner type and file.
	 * Mirrors JetBrains DevAssistScanScheduler.cacheScanResults() pattern.
	 *
	 * When a partial re-scan is performed (e.g., only ASCA is rescanned),
	 * this method removes the old results for THAT scanner type before
	 * merging the new results.
	 *
	 * @param scannerType Name of the scanner engine (e.g., "ASCA", "OSS", "IaC")
	 * @param filePath Absolute file path
	 */
	public void removeScanIssuesByFileAndScanner(String scannerType, String filePath) {
		if (filePath == null || scannerType == null) {
			return;
		}

		List<ScanIssue> existing = fileToScanIssues.getOrDefault(filePath, new ArrayList<>());
		List<ScanIssue> filtered = new ArrayList<>();

		// Keep only issues from OTHER scanners
		for (ScanIssue issue : existing) {
			if (issue.getScanEngine() != null &&
				!issue.getScanEngine().name().equals(scannerType)) {
				filtered.add(issue);
			}
		}

		fileToScanIssues.put(filePath, filtered);
		CxLogger.info(LOG_TAG + " Removed " + scannerType + " issues for: " + filePath +
			" (kept " + filtered.size() + " issues from other scanners)");
	}

	/**
	 * Clear all caches (on project close).
	 */
	public void clearAll() {
		fileToScanIssues.clear();
		CxLogger.info(LOG_TAG + " All caches cleared");
	}

	/**
	 * Get cache statistics for debugging.
	 *
	 * @return Summary string
	 */
	public String getCacheStats() {
		int fileCount = fileToScanIssues.size();
		int totalIssues = fileToScanIssues.values().stream()
			.mapToInt(List::size)
			.sum();
		return "Files: " + fileCount + ", Total Issues: " + totalIssues;
	}

	/**
	 * Publish issues update via Eclipse IEventBroker.
	 * Subscribers listen on ISSUES_UPDATED_TOPIC using @UIEventTopic annotation.
	 *
	 * @see com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView
	 */
	private void publishIssuesUpdated() {
		try {
			IEventBroker eventBroker = PluginUtils.getEventBroker();
			if (eventBroker != null) {
				Map<String, List<ScanIssue>> allIssues = getAllScanIssues();
				System.out.println(LOG_TAG + " [EVENT-BROKER] Publishing issues update: " + allIssues.size() + " files");
				eventBroker.post(ISSUES_UPDATED_TOPIC, allIssues);
			} else {
				System.err.println(LOG_TAG + " [EVENT-BROKER] âœ— EventBroker not available");
			}
		} catch (Exception e) {
			System.err.println(LOG_TAG + " [EVENT-BROKER] Error publishing event: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void addToCxOneFindings(IFile file, List<ScanIssue> problemsList) {
        getInstance(file.getProject()).addScanIssues(file.getFullPath().toOSString(), problemsList);
    }
	

}

