package com.checkmarx.eclipse.views.problems.filter;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.core.resources.IMarker;
import java.util.HashSet;
import java.util.Set;

import com.checkmarx.eclipse.views.problems.marker.ProblemMarkerConstants;
import com.checkmarx.eclipse.enums.Severity;

/**
 * ViewerFilter for Checkmarx Problems View to filter by severity.
 * Applied to the native Eclipse Problems View to show only selected severity levels.
 * Supports multiple severity selection.
 */
public class CxProblemsViewFilter extends ViewerFilter {

	private Set<Severity> activeSeverities = new HashSet<>(); // Empty = show all
	private boolean showOnlyCheckmarx = false;

	public CxProblemsViewFilter() {
		this.activeSeverities = new HashSet<>(); // Show all by default
		this.showOnlyCheckmarx = true; // Show only Checkmarx problems
	}

	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {
		// Categories (like "Checkmarx Problems") are always shown
		if (element != null && element.getClass().getSimpleName().equals("MarkerCategory")) {
			return true;
		}

		// Extract IMarker from MarkerEntry wrapper
		IMarker marker = extractMarkerFromElement(element);

		if (marker != null) {
			boolean result = shouldShowMarker(marker);
			try {
				String markerMsg = (String) marker.getAttribute(IMarker.MESSAGE);
				String severity = (String) marker.getAttribute(ProblemMarkerConstants.ATTR_SEVERITY);
				System.out.println("[CX-FILTER] Marker: " + severity + " -> " + (result ? "✓ SHOW" : "✗ HIDE"));
			} catch (Exception e) {
				// Ignore logging errors
			}
			return result;
		}

		// For other elements, show by default
		return true;
	}

	/**
	 * Extract IMarker from MarkerEntry wrapper object
	 * The Problems View wraps markers in MarkerEntry objects
	 */
	private IMarker extractMarkerFromElement(Object element) {
		if (element instanceof IMarker) {
			return (IMarker) element;
		}

		// Try to extract marker from MarkerEntry via reflection
		if (element != null && element.getClass().getSimpleName().equals("MarkerEntry")) {
			try {
				java.lang.reflect.Field markerField = element.getClass().getDeclaredField("marker");
				markerField.setAccessible(true);
				Object markerObj = markerField.get(element);
				if (markerObj instanceof IMarker) {
					return (IMarker) markerObj;
				}
			} catch (Exception e) {
				// Try alternative field names
				try {
					java.lang.reflect.Field markerField = element.getClass().getDeclaredField("fMarker");
					markerField.setAccessible(true);
					Object markerObj = markerField.get(element);
					if (markerObj instanceof IMarker) {
						return (IMarker) markerObj;
					}
				} catch (Exception e2) {
					// Last resort - try to get marker through getter method
					try {
						java.lang.reflect.Method getMarkerMethod = element.getClass().getMethod("getMarker");
						Object markerObj = getMarkerMethod.invoke(element);
						if (markerObj instanceof IMarker) {
							return (IMarker) markerObj;
						}
					} catch (Exception e3) {
						System.err.println("[CX-FILTER] Could not extract marker from " + element.getClass().getName());
					}
				}
			}
		}

		return null;
	}

	/**
	 * Determine if a marker should be shown based on current filters
	 */
	private boolean shouldShowMarker(IMarker marker) {
		try {
			// Check if it's a Checkmarx marker
			if (showOnlyCheckmarx) {
				boolean isCheckmarx = marker.exists() && marker.isSubtypeOf(ProblemMarkerConstants.MARKER_TYPE);
				if (!isCheckmarx) {
					return false; // Hide non-Checkmarx markers
				}
			}

			// Get active severities from the persistent state manager
			Set<Severity> activeSeverities = FilterStateManager.getInstance().getSelectedSeverities();

			// If no severity filters selected, show all
			if (activeSeverities.isEmpty()) {
				return true;
			}

			// Check if marker severity matches any of the active severities
			Object severityAttr = marker.getAttribute(ProblemMarkerConstants.ATTR_SEVERITY);
			if (severityAttr instanceof String) {
				String markerSeverity = (String) severityAttr;
				for (Severity severity : activeSeverities) {
					if (markerSeverity.equalsIgnoreCase(severity.name())) {
						return true;
					}
				}
				return false; // Marker severity not in selected severities
			}

			return true;
		} catch (Exception e) {
			System.err.println("[CX-FILTER] Error filtering marker: " + e.getMessage());
			return true; // Show on error
		}
	}

	/**
	 * Set a single severity filter (replaces previous selections)
	 */
	public void setSeverityFilter(Severity severity) {
		this.activeSeverities.clear();
		if (severity != null) {
			this.activeSeverities.add(severity);
			System.out.println("[CX-FILTER] Severity filter set to: " + severity.name());
		}
	}

	/**
	 * Add a severity to the active filters (multi-select)
	 */
	public void addSeverityFilter(Severity severity) {
		if (severity != null) {
			this.activeSeverities.add(severity);
			System.out.println("[CX-FILTER] Added severity filter: " + severity.name() + " (total: " + activeSeverities + ")");
		}
	}

	/**
	 * Remove a severity from the active filters
	 */
	public void removeSeverityFilter(Severity severity) {
		if (severity != null) {
			this.activeSeverities.remove(severity);
			System.out.println("[CX-FILTER] Removed severity filter: " + severity.name() + " (total: " + activeSeverities + ")");
		}
	}

	/**
	 * Check if a severity is currently filtered
	 */
	public boolean isSeverityFiltered(Severity severity) {
		return activeSeverities.contains(severity);
	}

	/**
	 * Get the active severity filters
	 */
	public Set<Severity> getSeverityFilters() {
		return new HashSet<>(activeSeverities);
	}

	/**
	 * Clear the severity filter (show all severities)
	 */
	public void clearSeverityFilter() {
		this.activeSeverities.clear();
		System.out.println("[CX-FILTER] Severity filter cleared (showing all)");
	}

	/**
	 * Set whether to show only Checkmarx problems
	 */
	public void setShowOnlyCheckmarx(boolean showOnly) {
		this.showOnlyCheckmarx = showOnly;
		System.out.println("[CX-FILTER] Show only Checkmarx: " + showOnly);
	}
}
