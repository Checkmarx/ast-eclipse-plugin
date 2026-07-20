package com.checkmarx.eclipse.devassist.backend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.devassist.problems.model.ScanProblem;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * In-memory cache for scan results, keyed by file path.
 *
 * Stores:
 * - ScanIssue: Rich model with locations, vulnerabilities, metadata
 * - ScanProblem: UI-agnostic representation for Problems View markers
 *
 * Thread-safe via ConcurrentHashMap. Used to avoid redundant scans
 * and to enable result restoration when files are reopened.
 */
public class ProblemHolderService {

	private static final String LOG_TAG = "[PROBLEM-HOLDER]";

	private final ConcurrentHashMap<String, List<ScanIssue>> fileToScanIssues =
		new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, List<ScanProblem>> fileToScanProblems =
		new ConcurrentHashMap<>();

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
		CxLogger.info(LOG_TAG + " Cached " + issues.size() + " issues for: " + filePath);
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
	 * @return Map of file path → issues
	 */
	public Map<String, List<ScanIssue>> getAllScanIssues() {
		Map<String, List<ScanIssue>> result = new HashMap<>();
		for (Map.Entry<String, List<ScanIssue>> entry : fileToScanIssues.entrySet()) {
			result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		return result;
	}

	/**
	 * Merge new issues with existing cached issues for a file.
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
		fileToScanProblems.remove(filePath);
		CxLogger.info(LOG_TAG + " Cleared cache for: " + filePath);
	}

	/**
	 * Cache problem descriptors (for Problems View) for a file.
	 *
	 * @param filePath Absolute file path
	 * @param problems Problem descriptors
	 */
	public void addScanProblems(String filePath, List<ScanProblem> problems) {
		if (filePath == null || problems == null) {
			return;
		}

		fileToScanProblems.put(filePath, new ArrayList<>(problems));
	}

	/**
	 * Get cached problems for a file.
	 *
	 * @param filePath Absolute file path
	 * @return Cached problems or empty list
	 */
	public List<ScanProblem> getScanProblemsByFile(String filePath) {
		if (filePath == null) {
			return Collections.emptyList();
		}

		List<ScanProblem> cached = fileToScanProblems.get(filePath);
		return cached != null ? new ArrayList<>(cached) : Collections.emptyList();
	}

	/**
	 * Clear all caches (on project close).
	 */
	public void clearAll() {
		fileToScanIssues.clear();
		fileToScanProblems.clear();
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
}
