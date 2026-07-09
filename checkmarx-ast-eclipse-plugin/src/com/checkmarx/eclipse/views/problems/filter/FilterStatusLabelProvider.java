package com.checkmarx.eclipse.views.problems.filter;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.menus.WorkbenchWindowControlContribution;
import org.eclipse.swt.graphics.Image;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.views.problems.marker.ProblemMarkerConstants;
import com.checkmarx.eclipse.views.findings.icons.IconRegistry;

import java.util.Set;

/**
 * Displays filter status in Problems View toolbar.
 * Shows: "Showing X of Y problems | Active Filters: [CRITICAL] [HIGH]"
 */
public class FilterStatusLabelProvider extends WorkbenchWindowControlContribution {

	private Label statusLabel;
	private Composite iconComposite;
	private FilterStateManager filterStateManager;

	public FilterStatusLabelProvider() {
		this.filterStateManager = FilterStateManager.getInstance();
	}

	@Override
	protected org.eclipse.swt.widgets.Control createControl(Composite parent) {
		// Create main composite with row layout
		Composite mainComposite = new Composite(parent, SWT.NONE);
		RowLayout mainLayout = new RowLayout(SWT.HORIZONTAL);
		mainLayout.spacing = 5;
		mainLayout.marginHeight = 2;
		mainLayout.marginWidth = 2;
		mainComposite.setLayout(mainLayout);

		// Create status text label
		statusLabel = new Label(mainComposite, SWT.NONE);
		statusLabel.setText("Loading filter status...");
		statusLabel.setLayoutData(new RowData(SWT.DEFAULT, SWT.DEFAULT));

		// Create composite for icons (filter status)
		iconComposite = new Composite(mainComposite, SWT.NONE);
		RowLayout iconLayout = new RowLayout(SWT.HORIZONTAL);
		iconLayout.spacing = 3;
		iconLayout.marginHeight = 0;
		iconLayout.marginWidth = 0;
		iconComposite.setLayout(iconLayout);
		iconComposite.setLayoutData(new RowData(SWT.DEFAULT, SWT.DEFAULT));

		// Initial update
		updateLabel();

		// Register as filter change listener
		filterStateManager.addFilterChangeListener(this::updateLabel);

		// Listen for marker changes
		startListening();

		return mainComposite;
	}

	/**
	 * Update the status label with current filter state and marker counts.
	 * Also displays severity icons for active filters.
	 */
	private void updateLabel() {
		if (statusLabel == null || statusLabel.isDisposed()) {
			return;
		}

		try {
			int totalMarkers = countTotalCheckmarxMarkers();
			int visibleMarkers = countVisibleMarkers();

			String statusText = String.format("Showing %d of %d problems",
					visibleMarkers, totalMarkers);

			statusLabel.setText(statusText);

			// Update the icon composite with active filter icons
			updateFilterIconsDisplay();

			statusLabel.getParent().layout();

			System.out.println("[FILTER-STATUS] " + statusText);

		} catch (Exception e) {
			System.err.println("[FILTER-STATUS] Error updating label: " + e.getMessage());
		}
	}

	/**
	 * Display icons for active filter severity levels instead of text labels
	 */
	private void updateFilterIconsDisplay() {
		if (iconComposite == null || iconComposite.isDisposed()) {
			return;
		}

		// Clear existing icons
		for (org.eclipse.swt.widgets.Control child : iconComposite.getChildren()) {
			child.dispose();
		}

		Set<Severity> activeSeverities = filterStateManager.getSelectedSeverities();

		if (activeSeverities.isEmpty()) {
			// No filters active - show nothing or a "no filter" text
			return;
		}

		// Create icons in order: CRITICAL, HIGH, MEDIUM, LOW
		Severity[] orderSeverities = {
			Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW
		};

		for (Severity severity : orderSeverities) {
			if (activeSeverities.contains(severity)) {
				createSeverityIconLabel(severity);
			}
		}

		iconComposite.layout();
	}

	/**
	 * Create a label with severity icon and count
	 */
	private void createSeverityIconLabel(Severity severity) {
		try {
			// Get the icon for this severity
			Image icon = getIconForSeverity(severity);
			if (icon == null) {
				return;
			}

			// Count how many markers have this severity
			int count = countMarkersBySeverity(severity);

			// Create label with icon and count
			Label iconLabel = new Label(iconComposite, SWT.NONE);
			iconLabel.setImage(icon);
			iconLabel.setToolTipText(severity.name() + ": " + count + " problem(s)");
			iconLabel.setLayoutData(new RowData(16, 16)); // Icon size 16x16

		} catch (Exception e) {
			System.err.println("[FILTER-STATUS] Error creating severity icon: " + e.getMessage());
		}
	}

	/**
	 * Get the icon image for a severity level
	 */
	private Image getIconForSeverity(Severity severity) {
		try {
			// Get the 16px icon for the severity
			return IconRegistry.getIcon(severity.name(), IconRegistry.Size.SMALL);
		} catch (Exception e) {
			System.err.println("[FILTER-STATUS] Could not load icon for " + severity.name() + ": " + e.getMessage());
			return null;
		}
	}

	/**
	 * Count how many markers have a specific severity
	 */
	private int countMarkersBySeverity(Severity severity) {
		try {
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IMarker[] allMarkers = workspace.getRoot().findMarkers(
					ProblemMarkerConstants.MARKER_TYPE, true, IResource.DEPTH_INFINITE);

			int count = 0;
			for (IMarker marker : allMarkers) {
				try {
					Object severityAttr = marker.getAttribute(ProblemMarkerConstants.ATTR_SEVERITY);
					if (severityAttr instanceof String) {
						String markerSeverity = (String) severityAttr;
						if (markerSeverity.equalsIgnoreCase(severity.name())) {
							count++;
						}
					}
				} catch (CoreException e) {
					// Skip markers with errors
				}
			}
			return count;
		} catch (CoreException e) {
			System.err.println("[FILTER-STATUS] Error counting markers by severity: " + e.getMessage());
			return 0;
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
