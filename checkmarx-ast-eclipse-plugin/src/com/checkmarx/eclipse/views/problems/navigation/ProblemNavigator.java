package com.checkmarx.eclipse.views.problems.navigation;

import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;

/**
 * Opens the editor for a marker and positions the caret on the finding's line,
 * mirroring the behaviour of native Eclipse compilation-error navigation.
 *
 * <p>
 * For markers created on a real file this is largely automatic in the Problems
 * View (double-click / Enter). This helper exists for programmatic navigation
 * (e.g. from the context-menu handlers or the future scan integration) and uses
 * the same platform APIs the Problems View itself uses:
 * {@link IDE#openEditor(IWorkbenchPage, IMarker)} followed by
 * {@link IDE#gotoMarker}.
 * </p>
 */
public class ProblemNavigator {

	/**
	 * Reveal the given marker in an editor. Safe to call from any thread: the
	 * UI work is marshalled onto the SWT display thread.
	 *
	 * @param marker marker to navigate to; ignored if {@code null} or gone.
	 */
	public void navigateTo(final IMarker marker) {
		if (marker == null || !marker.exists()) {
			return;
		}
		Display.getDefault().asyncExec(() -> openInEditor(marker));
	}

	private void openInEditor(IMarker marker) {
		try {
			IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			if (window == null) {
				return;
			}
			IWorkbenchPage page = window.getActivePage();
			if (page == null) {
				return;
			}
			// openEditor(page, marker) opens the right editor for the marker's
			// resource; gotoMarker then selects/reveals the LINE_NUMBER region.
			IDE.openEditor(page, marker, true);
			IDE.gotoMarker(page.getActiveEditor(), marker);
		} catch (PartInitException e) {
			CxLogger.error(String.format(PluginConstants.ERROR_OPENING_FILE, e.getMessage()), e);
		}
	}
}
