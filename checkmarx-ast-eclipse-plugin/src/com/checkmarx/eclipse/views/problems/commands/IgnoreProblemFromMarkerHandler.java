package com.checkmarx.eclipse.views.problems.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import com.checkmarx.eclipse.views.problems.CxProblemsServices;
import com.checkmarx.eclipse.views.problems.ignored.IgnoredProblemsStore;
import com.checkmarx.eclipse.views.problems.marker.ProblemMarkerConstants;

/**
 * Handler for ignoring a problem directly from a marker in the Problems View.
 * Extracts problem information from the marker and adds it to the ignored list.
 */
public class IgnoreProblemFromMarkerHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			ISelection selection = HandlerUtil.getCurrentSelection(event);
			if (selection instanceof IStructuredSelection) {
				IStructuredSelection ssel = (IStructuredSelection) selection;
				Object obj = ssel.getFirstElement();
				if (obj instanceof IMarker) {
					IMarker marker = (IMarker) obj;
					ignoreProblem(marker);
				}
			}
		} catch (Exception e) {
			System.err.println("[IGNORE-MARKER-HANDLER] Error: " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	private void ignoreProblem(IMarker marker) {
		try {
			// Verify it's a Checkmarx marker
			if (!marker.isSubtypeOf(ProblemMarkerConstants.MARKER_TYPE)) {
				System.out.println("[IGNORE-MARKER-HANDLER] Not a Checkmarx marker, skipping");
				return;
			}

			// Get the finding ID from marker
			Object findingIdObj = marker.getAttribute(ProblemMarkerConstants.ATTR_FINDING_ID);
			if (findingIdObj == null) {
				System.out.println("[IGNORE-MARKER-HANDLER] No finding ID in marker");
				return;
			}

			String findingId = findingIdObj.toString();
			System.out.println("[IGNORE-MARKER-HANDLER] Ignoring problem: " + findingId);

			// Get message for logging
			Object messageObj = marker.getAttribute(IMarker.MESSAGE);
			String message = messageObj != null ? messageObj.toString() : "Unknown";
			System.out.println("[IGNORE-MARKER-HANDLER] Problem: " + message);

			// Add to ignored store
			IgnoredProblemsStore ignoredStore = IgnoredProblemsStore.getInstance();
			ignoredStore.ignoreProblem(findingId);
			System.out.println("[IGNORE-MARKER-HANDLER] ✓ Added to ignored list");

			// Republish problems (will filter out ignored ones and update views)
			System.out.println("[IGNORE-MARKER-HANDLER] Republishing problems...");
			CxProblemsServices.publisher().publish();
			System.out.println("[IGNORE-MARKER-HANDLER] ✓ Problems republished - problem removed from native view");

		} catch (Exception e) {
			System.err.println("[IGNORE-MARKER-HANDLER] Error ignoring problem: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
