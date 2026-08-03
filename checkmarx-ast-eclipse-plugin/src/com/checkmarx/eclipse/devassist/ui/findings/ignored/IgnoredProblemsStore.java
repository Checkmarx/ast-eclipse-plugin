package com.checkmarx.eclipse.devassist.ui.findings.ignored;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.service.prefs.BackingStoreException;

import com.checkmarx.eclipse.devassist.model.ScanIssue;

/**
 * Persistent storage for ignored problems. Uses Eclipse preferences to store
 * ignored problem IDs. Provides thread-safe access to ignored problems list.
 */
public class IgnoredProblemsStore {

	private static final String PLUGIN_ID = "com.checkmarx.ast.eclipse";
	private static final String PREF_IGNORED_PROBLEMS = "ignoredProblems";
	private static final String SEPARATOR = ",";

	private static final IgnoredProblemsStore INSTANCE = new IgnoredProblemsStore();
	private final Set<String> ignoredProblemIds = Collections.synchronizedSet(new HashSet<>());
	private final Map<String, ScanIssue> ignoredProblemsCache = Collections.synchronizedMap(new HashMap<>());
	private final List<IgnoredProblemsListener> listeners = Collections.synchronizedList(new ArrayList<>());

	private IgnoredProblemsStore() {
		loadFromPreferences();
	}

	public static IgnoredProblemsStore getInstance() {
		return INSTANCE;
	}

	/**
	 * Add a problem to the ignored list (by ID only).
	 */
	public void ignoreProblem(String problemId) {
		if (problemId != null && ignoredProblemIds.add(problemId)) {
			System.out.println("[IGNORED-STORE] Added to ignored: " + problemId);
			saveToPreferences();
			notifyListeners();
		}
	}

	/**
	 * Add a finding to the ignored list with full finding details.
	 * This allows findings from the Findings View to be properly displayed in the Ignored Problems View.
	 */
	public void ignoreProblem(ScanIssue issue) {
		if (issue != null && issue.getScanIssueId() != null) {
			System.out.println("[IGNORED-STORE] ignoreProblem(ScanIssue) called with ID: " + issue.getScanIssueId());
			ignoreProblem(issue.getScanIssueId());
			// Cache the full issue details for later retrieval
			ignoredProblemsCache.put(issue.getScanIssueId(), issue);
			System.out.println("[IGNORED-STORE] âœ“ Cached issue details. Cache size: " + ignoredProblemsCache.size());
		} else {
			System.out.println("[IGNORED-STORE] âœ— ERROR: issue is null or ID is null!");
		}
	}

	/**
	 * Remove a problem from the ignored list (restore it).
	 */
	public void restoreProblem(String problemId) {
		if (problemId != null && ignoredProblemIds.remove(problemId)) {
			System.out.println("[IGNORED-STORE] Removed from ignored: " + problemId);
			ignoredProblemsCache.remove(problemId);
			saveToPreferences();
			notifyListeners();
		}
	}

	/**
	 * Check if a problem is ignored.
	 */
	public boolean isIgnored(String problemId) {
		return problemId != null && ignoredProblemIds.contains(problemId);
	}

	/**
	 * Get all ignored problem IDs.
	 */
	public Set<String> getIgnoredProblemIds() {
		return new HashSet<>(ignoredProblemIds);
	}

	/**
	 * Filter a list of issues, returning only non-ignored ones.
	 */
	public List<ScanIssue> filterActiveProblems(List<ScanIssue> issues) {
		List<ScanIssue> active = new ArrayList<>();
		for (ScanIssue issue : issues) {
			if (!isIgnored(issue.getScanIssueId())) {
				active.add(issue);
			}
		}
		return active;
	}

	/**
	 * Get only ignored issues from a list.
	 */
	public List<ScanIssue> getIgnoredProblems(List<ScanIssue> allIssues) {
		List<ScanIssue> ignored = new ArrayList<>();
		for (ScanIssue issue : allIssues) {
			if (isIgnored(issue.getScanIssueId())) {
				ignored.add(issue);
			}
		}
		return ignored;
	}

	/**
	 * Get all ignored issues including cached findings from the Findings View.
	 * Combines issues from the provided list with cached issue details.
	 */
	public List<ScanIssue> getAllIgnoredProblems(List<ScanIssue> allIssues) {
		List<ScanIssue> result = new ArrayList<>();

		// First add ignored issues from the provided list
		if (allIssues != null) {
			for (ScanIssue issue : allIssues) {
				if (isIgnored(issue.getScanIssueId())) {
					result.add(issue);
				}
			}
		}

		// Then add any cached issues not yet in the result (e.g., findings from Findings View)
		for (Map.Entry<String, ScanIssue> entry : ignoredProblemsCache.entrySet()) {
			if (isIgnored(entry.getKey()) && !result.stream().anyMatch(i -> i.getScanIssueId().equals(entry.getKey()))) {
				result.add(entry.getValue());
			}
		}

		return result;
	}

	/**
	 * Clear all ignored problems.
	 */
	public void clearAll() {
		ignoredProblemIds.clear();
		ignoredProblemsCache.clear();
		saveToPreferences();
		notifyListeners();
		System.out.println("[IGNORED-STORE] Cleared all ignored problems");
	}

	/**
	 * Register listener for ignore/restore events.
	 */
	public void addListener(IgnoredProblemsListener listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	/**
	 * Unregister listener.
	 */
	public void removeListener(IgnoredProblemsListener listener) {
		listeners.remove(listener);
	}

	private void notifyListeners() {
		for (IgnoredProblemsListener listener : listeners) {
			listener.onIgnoredProblemsChanged();
		}
	}

	private void loadFromPreferences() {
		try {
			IEclipsePreferences prefs = ConfigurationScope.INSTANCE.getNode(PLUGIN_ID);
			String ignored = prefs.get(PREF_IGNORED_PROBLEMS, "");
			if (!ignored.isEmpty()) {
				String[] ids = ignored.split(SEPARATOR);
				for (String id : ids) {
					if (!id.trim().isEmpty()) {
						ignoredProblemIds.add(id.trim());
					}
				}
				System.out.println("[IGNORED-STORE] Loaded " + ignoredProblemIds.size() + " ignored problems from preferences");
			}
		} catch (Exception e) {
			System.err.println("[IGNORED-STORE] Error loading preferences: " + e.getMessage());
		}
	}

	private void saveToPreferences() {
		try {
			IEclipsePreferences prefs = ConfigurationScope.INSTANCE.getNode(PLUGIN_ID);
			String ignored = String.join(SEPARATOR, ignoredProblemIds);
			prefs.put(PREF_IGNORED_PROBLEMS, ignored);
			prefs.flush();
			System.out.println("[IGNORED-STORE] Saved " + ignoredProblemIds.size() + " ignored problems to preferences");
		} catch (BackingStoreException e) {
			System.err.println("[IGNORED-STORE] Error saving preferences: " + e.getMessage());
		}
	}

	public interface IgnoredProblemsListener {
		void onIgnoredProblemsChanged();
	}
}

