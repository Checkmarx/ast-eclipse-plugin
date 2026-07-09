package com.checkmarx.eclipse.views.findings.ignored;

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

import com.checkmarx.eclipse.views.problems.model.ScanProblem;

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
	private final Map<String, ScanProblem> ignoredProblemsCache = Collections.synchronizedMap(new HashMap<>());
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
	 * Add a problem to the ignored list with full problem details.
	 * This allows findings from the Findings View to be properly displayed in the Ignored Problems View.
	 */
	public void ignoreProblem(ScanProblem problem) {
		if (problem != null && problem.getId() != null) {
			System.out.println("[IGNORED-STORE] ignoreProblem(ScanProblem) called with ID: " + problem.getId());
			ignoreProblem(problem.getId());
			// Cache the full problem details for later retrieval
			ignoredProblemsCache.put(problem.getId(), problem);
			System.out.println("[IGNORED-STORE] ✓ Cached problem details. Cache size: " + ignoredProblemsCache.size());
		} else {
			System.out.println("[IGNORED-STORE] ✗ ERROR: problem is null or ID is null!");
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
	 * Filter a list of problems, returning only non-ignored ones.
	 */
	public List<ScanProblem> filterActiveProblems(List<ScanProblem> problems) {
		List<ScanProblem> active = new ArrayList<>();
		for (ScanProblem problem : problems) {
			if (!isIgnored(problem.getId())) {
				active.add(problem);
			}
		}
		return active;
	}

	/**
	 * Get only ignored problems from a list.
	 */
	public List<ScanProblem> getIgnoredProblems(List<ScanProblem> allProblems) {
		List<ScanProblem> ignored = new ArrayList<>();
		for (ScanProblem problem : allProblems) {
			if (isIgnored(problem.getId())) {
				ignored.add(problem);
			}
		}
		return ignored;
	}

	/**
	 * Get all ignored problems including cached findings from the Findings View.
	 * Combines problems from the provided list with cached problem details.
	 */
	public List<ScanProblem> getAllIgnoredProblems(List<ScanProblem> allProblems) {
		List<ScanProblem> result = new ArrayList<>();

		// First add ignored problems from the provided list
		if (allProblems != null) {
			for (ScanProblem problem : allProblems) {
				if (isIgnored(problem.getId())) {
					result.add(problem);
				}
			}
		}

		// Then add any cached problems not yet in the result (e.g., findings from Findings View)
		for (Map.Entry<String, ScanProblem> entry : ignoredProblemsCache.entrySet()) {
			if (isIgnored(entry.getKey()) && !result.stream().anyMatch(p -> p.getId().equals(entry.getKey()))) {
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
