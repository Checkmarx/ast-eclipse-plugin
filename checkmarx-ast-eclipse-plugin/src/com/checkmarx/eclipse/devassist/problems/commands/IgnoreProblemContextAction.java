package com.checkmarx.eclipse.devassist.problems.commands;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.action.Action;
import org.eclipse.ui.IWorkbenchPage;

import com.checkmarx.eclipse.devassist.problems.CxProblemsServices;
import com.checkmarx.eclipse.devassist.ui.findings.ignored.IgnoredProblemsStore;
import com.checkmarx.eclipse.devassist.problems.marker.ProblemMarkerConstants;

/**
 * Context menu action to ignore a problem from the native Problems View.
 * When executed, removes the marker from the Problems View and adds it to
 * the Ignored Problems window.
 */
public class IgnoreProblemContextAction extends Action {

	private IMarker marker;

	public IgnoreProblemContextAction(IWorkbenchPage workbenchPage) {
		super("Ignore This Problem");
		setToolTipText("Mark this problem as ignored (will move to Ignored Problems window)");
	}

	public void setMarker(IMarker marker) {
		this.marker = marker;
	}

	public boolean isCheckmarxMarker(IMarker marker) {
		try {
			return marker.isSubtypeOf(ProblemMarkerConstants.MARKER_TYPE);
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public void run() {
		if (marker == null) {
			System.out.println("[IGNORE-ACTION] No marker selected");
			return;
		}

		try {
			// Get the problem ID from the marker
			Object findingIdObj = marker.getAttribute(ProblemMarkerConstants.ATTR_FINDING_ID);
			String findingId = findingIdObj != null ? findingIdObj.toString() : null;

			if (findingId == null) {
				System.out.println("[IGNORE-ACTION] No finding ID in marker");
				return;
			}

			System.out.println("[IGNORE-ACTION] Ignoring problem: " + findingId);

			// Add to ignored store
			IgnoredProblemsStore ignoredStore = IgnoredProblemsStore.getInstance();
			ignoredStore.ignoreProblem(findingId);

			// Get message for logging
			Object messageObj = marker.getAttribute(IMarker.MESSAGE);
			String message = messageObj != null ? messageObj.toString() : "Unknown";
			System.out.println("[IGNORE-ACTION] ✓ Problem ignored: " + message);

			// Republish problems (will exclude ignored ones)
			CxProblemsServices.publisher().publish();

		} catch (Exception e) {
			System.err.println("[IGNORE-ACTION] Error ignoring problem: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void dispose() {
		marker = null;
	}
}
