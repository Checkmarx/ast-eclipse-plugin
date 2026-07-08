package com.checkmarx.eclipse.views.problems.filter;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.enums.Severity;

/**
 * Manages persistent filter state for Checkmarx problem filters.
 * Stores selected severities in Eclipse preferences so they persist across sessions.
 */
public class FilterStateManager {

	private static final String FILTER_PREFIX = "checkmarx.filter.";
	private static FilterStateManager instance;
	private Set<Severity> activeSeverities = new HashSet<>();
	private List<FilterChangeListener> listeners = new ArrayList<>();

	/**
	 * Listener interface for filter changes
	 */
	public interface FilterChangeListener {
		void onFilterChanged();
	}

	private FilterStateManager() {
		loadState();
	}

	/**
	 * Get singleton instance
	 */
	public static synchronized FilterStateManager getInstance() {
		if (instance == null) {
			instance = new FilterStateManager();
		}
		return instance;
	}

	/**
	 * Load filter state from preferences
	 */
	private void loadState() {
		IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();
		activeSeverities.clear();

		for (Severity severity : Severity.values()) {
			String key = FILTER_PREFIX + severity.name();
			if (prefs.getBoolean(key)) {
				activeSeverities.add(severity);
			}
		}

		System.out.println("[FILTER-STATE] Loaded filter state: " + activeSeverities);
	}

	/**
	 * Save filter state to preferences
	 */
	private void saveState() {
		IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();

		for (Severity severity : Severity.values()) {
			String key = FILTER_PREFIX + severity.name();
			prefs.setValue(key, activeSeverities.contains(severity));
		}

		System.out.println("[FILTER-STATE] Saved filter state: " + activeSeverities);
	}

	/**
	 * Toggle a severity filter
	 */
	public void toggleSeverity(Severity severity) {
		if (activeSeverities.contains(severity)) {
			activeSeverities.remove(severity);
		} else {
			activeSeverities.add(severity);
		}
		saveState();
		notifyListeners();
		System.out.println("[FILTER-STATE] Toggled " + severity + " -> " + activeSeverities);
	}

	/**
	 * Set a single severity (replaces all others)
	 */
	public void setSeverity(Severity severity) {
		activeSeverities.clear();
		if (severity != null) {
			activeSeverities.add(severity);
		}
		saveState();
		notifyListeners();
	}

	/**
	 * Clear all filters
	 */
	public void clearAll() {
		activeSeverities.clear();
		saveState();
		notifyListeners();
		System.out.println("[FILTER-STATE] Cleared all filters");
	}

	/**
	 * Check if a severity is selected
	 */
	public boolean isSelected(Severity severity) {
		return activeSeverities.contains(severity);
	}

	/**
	 * Get all selected severities
	 */
	public Set<Severity> getSelectedSeverities() {
		return new HashSet<>(activeSeverities);
	}

	/**
	 * Check if any filters are active
	 */
	public boolean hasFilters() {
		return !activeSeverities.isEmpty();
	}

	/**
	 * Add a listener for filter changes
	 */
	public void addFilterChangeListener(FilterChangeListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
			System.out.println("[FILTER-STATE] Listener registered: " + listener.getClass().getSimpleName());
		}
	}

	/**
	 * Remove a listener
	 */
	public void removeFilterChangeListener(FilterChangeListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Notify all listeners of filter changes
	 */
	private void notifyListeners() {
		for (FilterChangeListener listener : listeners) {
			try {
				listener.onFilterChanged();
			} catch (Exception e) {
				System.err.println("[FILTER-STATE] Error notifying listener: " + e.getMessage());
			}
		}
	}
}
