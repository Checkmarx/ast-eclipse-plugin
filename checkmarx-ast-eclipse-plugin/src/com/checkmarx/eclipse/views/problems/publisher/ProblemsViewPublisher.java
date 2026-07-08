package com.checkmarx.eclipse.views.problems.publisher;

import java.util.List;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.views.problems.marker.IMarkerManager;
import com.checkmarx.eclipse.views.problems.model.ScanProblem;
import com.checkmarx.eclipse.views.problems.provider.IProblemProvider;
import com.checkmarx.eclipse.views.problems.ignored.IgnoredProblemsStore;
import com.checkmarx.eclipse.views.problems.ignored.CxIgnoredProblemsView;

/**
 * Default {@link IProblemsPublisher}: wires a {@link IProblemProvider} (source
 * of findings) to a {@link IMarkerManager} (Problems View integration) and
 * reveals the native Problems View.
 *
 * <p>
 * This class contains <b>no</b> knowledge of where findings come from or how
 * markers are created; it only coordinates. Both collaborators are injected,
 * which is what makes the mock &rarr; real-scan swap a change in the composition
 * root alone.
 * </p>
 */
public class ProblemsViewPublisher implements IProblemsPublisher {

	private final IProblemProvider problemProvider;
	private final IMarkerManager markerManager;

	public ProblemsViewPublisher(IProblemProvider problemProvider, IMarkerManager markerManager) {
		this.problemProvider = problemProvider;
		this.markerManager = markerManager;
	}

	@Override
	public void publish() {
		System.out.println("[PROBLEMS] Publishing problems to Problems View...");
		List<ScanProblem> allProblems = problemProvider.getProblems();

		// Get ignored problems store and filter active problems
		IgnoredProblemsStore ignoredStore = IgnoredProblemsStore.getInstance();
		List<ScanProblem> activeProblems = ignoredStore.filterActiveProblems(allProblems);

		System.out.println("[PROBLEMS] Total problems: " + allProblems.size() +
				" | Active: " + activeProblems.size() +
				" | Ignored: " + (allProblems.size() - activeProblems.size()));

		System.out.println("[PROBLEMS] Clearing old markers...");
		markerManager.clearMarkers();

		System.out.println("[PROBLEMS] Creating " + activeProblems.size() + " markers for active problems...");
		markerManager.createMarkers(activeProblems);
		System.out.println("[PROBLEMS] ✓ Markers created successfully");

		// Update ignored problems view with all problems (it will filter internally)
		updateIgnoredProblemsView(allProblems);

		showProblemsView();
	}

	@Override
	public void clear() {
		markerManager.clearMarkers();
	}

	/**
	 * Update the ignored problems view with the full problem list.
	 */
	private void updateIgnoredProblemsView(List<ScanProblem> allProblems) {
		Display.getDefault().asyncExec(() -> {
			try {
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (window == null) {
					return;
				}
				IWorkbenchPage page = window.getActivePage();
				if (page != null) {
					CxIgnoredProblemsView ignoredView = (CxIgnoredProblemsView) page.findView(CxIgnoredProblemsView.ID);
					if (ignoredView != null) {
						System.out.println("[PROBLEMS] Updating Ignored Problems View...");
						ignoredView.updateProblems(allProblems);
					}
				}
			} catch (Exception e) {
				System.err.println("[PROBLEMS] Error updating ignored problems view: " + e.getMessage());
			}
		});
	}

	/**
	 * Bring the native Problems View to the front. UI-thread safe.
	 */
	private void showProblemsView() {
		Display.getDefault().asyncExec(() -> {
			try {
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (window == null) {
					System.out.println("[PROBLEMS] ✗ No active workbench window");
					return;
				}
				IWorkbenchPage page = window.getActivePage();
				if (page != null) {
					System.out.println("[PROBLEMS] Opening native Problems View...");
					page.showView(IPageLayout.ID_PROBLEM_VIEW);
					System.out.println("[PROBLEMS] ✓ Problems View opened and made visible");
				}
			} catch (PartInitException e) {
				System.out.println("[PROBLEMS] ✗ Failed to open Problems View: " + e.getMessage());
				CxLogger.error("Failed to open the Problems View: " + e.getMessage(), e);
			}
		});
	}
}
