package com.checkmarx.eclipse.views.problems.commands;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.widgets.Shell;

import com.checkmarx.eclipse.views.problems.CxProblemsServices;
import com.checkmarx.eclipse.views.findings.ignored.IgnoredProblemsStore;
import com.checkmarx.eclipse.views.problems.marker.ProblemMarkerConstants;

/**
 * Handler for "Ignore This Problem" action in Problems View context menu.
 * Marks selected Checkmarx problems as ignored and removes them from the
 * native Problems View, moving them to the Ignored Problems custom window.
 */
public class ProblemAction1Handler extends AbstractProblemHandler {

	@Override
	protected void perform(List<IMarker> markers, Shell shell) {
		if (markers == null || markers.isEmpty()) {
			System.out.println("[IGNORE-ACTION] No markers selected");
			return;
		}

		IgnoredProblemsStore ignoredStore = IgnoredProblemsStore.getInstance();
		int ignoredCount = 0;
		int skippedCount = 0;

		// Add each CHECKMARX marker's finding ID to ignored list
		for (IMarker marker : markers) {
			try {
				// Only process Checkmarx markers
				if (!isCheckmarxMarker(marker)) {
					skippedCount++;
					continue;
				}

				Object findingIdObj = marker.getAttribute(ProblemMarkerConstants.ATTR_FINDING_ID);
				if (findingIdObj != null) {
					String findingId = findingIdObj.toString();
					ignoredStore.ignoreProblem(findingId);
					ignoredCount++;
					System.out.println("[IGNORE-ACTION] ✓ Ignored: " + findingId);
				}
			} catch (Exception e) {
				System.err.println("[IGNORE-ACTION] Error ignoring marker: " + e.getMessage());
			}
		}

		if (ignoredCount > 0) {
			System.out.println("[IGNORE-ACTION] ✓ " + ignoredCount + " Checkmarx problem(s) ignored");
			if (skippedCount > 0) {
				System.out.println("[IGNORE-ACTION] Skipped " + skippedCount + " non-Checkmarx problem(s)");
			}
			// Republish problems (will filter out ignored ones)
			CxProblemsServices.publisher().publish();
			System.out.println("[IGNORE-ACTION] ✓ Problems republished");
		} else if (skippedCount > 0) {
			System.out.println("[IGNORE-ACTION] No Checkmarx problems selected (skipped " + skippedCount + " other problems)");
		}
	}

	private boolean isCheckmarxMarker(IMarker marker) {
		try {
			return marker.exists() && marker.isSubtypeOf(ProblemMarkerConstants.MARKER_TYPE);
		} catch (Exception e) {
			return false;
		}
	}
}
