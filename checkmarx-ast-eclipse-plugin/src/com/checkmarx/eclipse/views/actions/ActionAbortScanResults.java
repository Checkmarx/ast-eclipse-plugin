package com.checkmarx.eclipse.views.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;

public class ActionAbortScanResults extends CxBaseAction {
	
	private static final String MSG_ABORTING_RETRIEVAL_SCAN_RESULTS = "Aborting the retrieval of results...";
	private static final String ACTION_ABORT_SCAN_RESULTS_TOOLTIP = "Abort the retrieval of results";
	private static final String ACTION_ABORT_SCAN_RESULTS_ICON_PATH = "platform:/plugin/org.eclipse.ui.browser/icons/clcl16/nav_stop.png";
	
	public ActionAbortScanResults(DisplayModel rootModel, TreeViewer resultsTree) {
		super(rootModel, resultsTree);
	}

	/**
	 * Creates a JFace action to abort the retrieving of scan results 
	 */
	public Action createAction() {
		Action abortScanResultsAction = new Action() {
			@Override
			public void run() {
				showMessage(MSG_ABORTING_RETRIEVAL_SCAN_RESULTS);
				DataProvider.abort.set(true);
				this.setEnabled(false);
			}
		};

		abortScanResultsAction.setId(ActionName.ABORT_RESULTS.name());
		abortScanResultsAction.setToolTipText(ACTION_ABORT_SCAN_RESULTS_TOOLTIP);
		abortScanResultsAction.setImageDescriptor(Activator.getImageDescriptor(ACTION_ABORT_SCAN_RESULTS_ICON_PATH));
		abortScanResultsAction.setEnabled(false);
		
		return abortScanResultsAction;
	}

}
