package com.checkmarx.eclipse.devassist.problems.commands;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.navigator.CommonActionProvider;
import org.eclipse.ui.navigator.ICommonActionExtensionSite;
import org.eclipse.ui.navigator.ICommonViewerWorkbenchSite;

/**
 * Provides context menu actions for problems in the native Problems View.
 * Allows marking problems as ignored from the right-click menu.
 */
@SuppressWarnings("restriction")
public class ProblemContextMenuProvider extends CommonActionProvider {

	private IgnoreProblemContextAction ignoreProblemAction;

	@Override
	public void init(ICommonActionExtensionSite site) {
		super.init(site);
		ICommonViewerWorkbenchSite workbenchSite = (ICommonViewerWorkbenchSite) site.getViewSite();
		ignoreProblemAction = new IgnoreProblemContextAction(workbenchSite.getPage());
	}

	@Override
	public void fillContextMenu(IMenuManager menu) {
		ISelection selection = getContext().getSelection();
		if (!selection.isEmpty() && selection instanceof IStructuredSelection) {
			IStructuredSelection ssel = (IStructuredSelection) selection;
			if (ssel.size() == 1) {
				Object obj = ssel.getFirstElement();
				if (obj instanceof IMarker) {
					IMarker marker = (IMarker) obj;
					ignoreProblemAction.setMarker(marker);
					if (ignoreProblemAction.isCheckmarxMarker(marker)) {
						menu.appendToGroup(IWorkbenchActionConstants.MB_ADDITIONS, ignoreProblemAction);
					}
				}
			}
		}
	}

	@Override
	public void fillActionBars(IActionBars actionBars) {
		// Add toolbar or other action bar actions if needed
	}

	@Override
	public void dispose() {
		if (ignoreProblemAction != null) {
			ignoreProblemAction.dispose();
		}
		super.dispose();
	}
}
