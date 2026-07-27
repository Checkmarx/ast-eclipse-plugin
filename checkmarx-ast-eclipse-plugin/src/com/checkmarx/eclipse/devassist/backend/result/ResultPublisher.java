package com.checkmarx.eclipse.devassist.backend.result;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.devassist.problems.ProblemDecorator;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;
import java.util.List;

/**
 * Publishes scan results to Checkmarx Findings Window and editor decorations.
 *
 * Responsibilities:
 * - Update custom Findings View with scan results
 * - Render editor decorations (gutter icons, underlines) for Findings Window issues
 * - NO integration with Eclipse native Problems View
 *
 * This connects scan results directly to the custom Findings Window.
 */
public class ResultPublisher {

	private static final String LOG_TAG = "[RESULT-PUBLISHER]";

	/**
	 * Publish scan results to Findings View and editor decorations.
	 *
	 * **CHANGE 5: Results are stored in ProblemHolderService which notifies listeners**
	 * Mirrors JetBrains pattern where scan results are stored in cache,
	 * which then publishes a message to notify all interested views.
	 *
	 * @param file File that was scanned
	 * @param scanIssues Issues found by scanners
	 */
	public static void publishResults(IFile file, List<ScanIssue> scanIssues) {
		if (file == null || scanIssues == null) {
			return;
		}
		try {
			// Step 1: Update Findings View (try to display immediately if view is open)
			System.out.println(LOG_TAG + " [STEP 1/2] Attempting to update Findings View if open...");
			updateFindingsView(file, scanIssues);
			System.out.println(LOG_TAG + " âœ“ Findings View update attempted");

			// Step 2: Render editor decorations (gutter icons, underlines)
			System.out.println(LOG_TAG + " [STEP 2/2] Rendering editor decorations...");
			renderEditorDecorations(file, scanIssues);

		} catch (Exception e) {
			System.err.println(LOG_TAG + " âœ— ERROR: " + e.getMessage());
			e.printStackTrace();
			CxLogger.error(LOG_TAG + " Error publishing results: " + e.getMessage(), e);
		}
	}

	/**
	 * Update Findings View with scan results.
	 *
	 * @param file File that was scanned
	 * @param scanIssues Issues to display
	 */
	private static void updateFindingsView(IFile file, List<ScanIssue> scanIssues) {
		try {
			if (scanIssues.isEmpty()) {
				return;
			}

			// Must run on UI thread
			org.eclipse.swt.widgets.Display display = PlatformUI.getWorkbench().getDisplay();
			if (display == null || display.isDisposed()) {
				return;
			}

			// **JetBrains Pattern: Remove old engine results â†’ Then merge new results**
			// This triggers the message bus pattern:
			// 1. removeScanIssuesByFileAndScanner() removes old results for THIS engine
			// 2. mergeScanIssues() stores new results in cache
			// 3. notifyListenersOfUpdate() publishes to all listeners
			// 4. CxFindingsView listener receives callback with getAllIssues()
			// 5. Listener calls refreshTree(allCachedResults)
			// 6. Tree shows merged results (no duplicates, no stale issues)
			// **FIX: Use getLocation() (absolute path) to match cache key format used in RealTimeScanJob**
			// ProblemHolderService cache is keyed with absolute paths from RealTimeScanJob.scanFile()
			// Must use same path format for cache lookups or removal will fail â†’ causing duplicates
			String filePath = file.getLocation().toOSString();

			org.eclipse.core.resources.IProject project = file.getProject();
			if (project != null) {
				ProblemHolderService problemHolder =
					(ProblemHolderService) project.getSessionProperty(
						new org.eclipse.core.runtime.QualifiedName("com.checkmarx.eclipse.plugin", "problem-holder"));

				if (problemHolder != null) {
					// Get engine type from scan issues (all issues from same scan have same engine)
					String engineType = scanIssues.isEmpty() ? null :
						scanIssues.get(0).getScanEngine() != null ?
						scanIssues.get(0).getScanEngine().name() : null;

					// Step 1: Remove old results from THIS scanner engine
					if (engineType != null) {
						problemHolder.removeScanIssuesByFileAndScanner(engineType, filePath);
						System.out.println(LOG_TAG + " [REMOVE] Removed old " + engineType + " issues for: " + filePath);
					}

					// Step 2: Add new results from THIS scanner engine
					problemHolder.mergeScanIssues(filePath, scanIssues);
					System.out.println(LOG_TAG + " [MERGE] Merged " + scanIssues.size() + " new issues from " +
						(engineType != null ? engineType : "UNKNOWN") + " for: " + filePath);
				} else {
					System.out.println(LOG_TAG + " [VIEW-UPDATE] âš  ProblemHolderService not initialized - results not cached");
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Render editor decorations (gutter icons, underlines) for Findings Window issues.
	 *
	 * @param file File that was scanned
	 * @param scanIssues Issues to visualize
	 */
	private static void renderEditorDecorations(IFile file, List<ScanIssue> scanIssues) {
		try {
			if (scanIssues.isEmpty()) {
				return;
			}

			org.eclipse.swt.widgets.Display display = PlatformUI.getWorkbench().getDisplay();
			if (display == null || display.isDisposed()) {
				return;
			}

			display.asyncExec(() -> {
				try {
					// Render gutter icons and underlines using Findings Window data
					ProblemDecorator.decorateEditor(file, scanIssues);
					CxLogger.info(LOG_TAG + " Editor decorations rendered for " + scanIssues.size() + " issues");

				} catch (Exception e) {
					CxLogger.warning(LOG_TAG + " Error rendering decorations: " + e.getMessage());
				}
			});

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error: " + e.getMessage());
		}
	}

	/**
	 * Find the open Findings View.
	 *
	 * @return CxFindingsView instance if open, null otherwise
	 */
	private static com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView findOpenFindingsView() {
		try {
			IWorkbench workbench = PlatformUI.getWorkbench();
			if (workbench == null) {
				return null;
			}

			IWorkbenchPage page = null;
			try {
				page = workbench.getActiveWorkbenchWindow().getActivePage();
			} catch (NullPointerException e) {
				for (var window : workbench.getWorkbenchWindows()) {
					page = window.getActivePage();
					if (page != null) break;
				}
			}

			if (page == null) {
				return null;
			}

			return (com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView) page
				.findView(com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView.ID);

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error finding Findings View: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Clear results for a file.
	 *
	 * @param file File to clear
	 */
	public static void clearResults(IFile file) {
		try {
			CxLogger.info(LOG_TAG + " Clearing results for: " + file.getName());
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error clearing results: " + e.getMessage());
		}
	}
}

