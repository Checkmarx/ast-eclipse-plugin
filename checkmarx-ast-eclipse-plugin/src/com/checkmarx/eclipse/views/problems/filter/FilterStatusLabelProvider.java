package com.checkmarx.eclipse.views.problems.filter;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.menus.WorkbenchWindowControlContribution;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.views.problems.marker.ProblemMarkerConstants;

import java.util.Set;

/**
 * Displays filter status in Problems View toolbar.
 * Shows: "Showing X of Y problems | Active Filters: [CRITICAL] [HIGH]"
 */
public class FilterStatusLabelProvider extends WorkbenchWindowControlContribution {

	private Label statusLabel;
	private FilterStateManager filterStateManager;

	public FilterStatusLabelProvider() {
		this.filterStateManager = FilterStateManager.getInstance();
	}

	@Override
	protected org.eclipse.swt.widgets.Control createControl(Composite parent) {
		statusLabel = new Label(parent, SWT.NONE);
		statusLabel.setText("Loading filter status...");

		// Initial update
		updateLabel();

		// Register as filter change listener
		filterStateManager.addFilterChangeListener(this::updateLabel);

		// Listen for marker changes
		startListening();

		return statusLabel;
	}

	/**
	 * Update the status label with current filter state and marker counts
	 */
	private void updateLabel() {
		if (statusLabel == null || statusLabel.isDisposed()) {
			return;
		}

		try {
			int totalMarkers = countTotalCheckmarxMarkers();
			int visibleMarkers = countVisibleMarkers();
			String activeFilters = getActiveFiltersText();

			String statusText = String.format("Showing %d of %d problems%s",
					visibleMarkers, totalMarkers, activeFilters);

			statusLabel.setText(statusText);
			statusLabel.getParent().layout();

			System.out.println("[FILTER-STATUS] " + statusText);

		} catch (Exception e) {
			System.err.println("[FILTER-STATUS] Error updating label: " + e.getMessage());
		}
	}

	/**
	 * Count total Checkmarx markers in workspace
	 */
	private int countTotalCheckmarxMarkers() {
		try {
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IMarker[] markers = workspace.getRoot().findMarkers(
					ProblemMarkerConstants.MARKER_TYPE, true, IResource.DEPTH_INFINITE);
			return markers.length;
		} catch (CoreException e) {
			System.err.println("[FILTER-STATUS] Error counting markers: " + e.getMessage());
			return 0;
		}
	}

	/**
	 * Count markers that pass the current filter
	 */
	private int countVisibleMarkers() {
		try {
			Set<Severity> activeSeverities = filterStateManager.getSelectedSeverities();

			// If no filters active, all markers are visible
			if (activeSeverities.isEmpty()) {
				return countTotalCheckmarxMarkers();
			}

			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IMarker[] allMarkers = workspace.getRoot().findMarkers(
					ProblemMarkerConstants.MARKER_TYPE, true, IResource.DEPTH_INFINITE);

			int visibleCount = 0;
			for (IMarker marker : allMarkers) {
				try {
					Object severityAttr = marker.getAttribute(ProblemMarkerConstants.ATTR_SEVERITY);
					if (severityAttr instanceof String) {
						String markerSeverity = (String) severityAttr;
						for (Severity severity : activeSeverities) {
							if (markerSeverity.equalsIgnoreCase(severity.name())) {
								visibleCount++;
								break;
							}
						}
					}
				} catch (CoreException e) {
					// Skip markers with errors
				}
			}

			return visibleCount;

		} catch (CoreException e) {
			System.err.println("[FILTER-STATUS] Error counting visible markers: " + e.getMessage());
			return 0;
		}
	}

	/**
	 * Get formatted text of active filters
	 * Returns: " | Active Filters: [CRITICAL] [HIGH]" or empty string if no filters
	 */
	private String getActiveFiltersText() {
		Set<Severity> activeSeverities = filterStateManager.getSelectedSeverities();

		if (activeSeverities.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder(" | Active Filters:");
		for (Severity severity : activeSeverities) {
			sb.append(" [").append(severity.name()).append("]");
		}

		return sb.toString();
	}

	/**
	 * Start listening for filter changes and marker changes
	 */
	private void startListening() {
		// Update when markers change
		ResourcesPlugin.getWorkspace().addResourceChangeListener(event -> {
			if (statusLabel != null && !statusLabel.isDisposed()) {
				statusLabel.getDisplay().asyncExec(this::updateLabel);
			}
		}, org.eclipse.core.resources.IResourceChangeEvent.POST_CHANGE);

		System.out.println("[FILTER-STATUS] Listener started");
	}

	/**
	 * Called by FilterBySeverityHandler to refresh the status
	 */
	public static void refreshStatus() {
		// This will be called from handler to trigger immediate update
		System.out.println("[FILTER-STATUS] Refresh triggered by handler");
	}
}
