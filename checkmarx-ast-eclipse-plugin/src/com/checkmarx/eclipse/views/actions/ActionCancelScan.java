package com.checkmarx.eclipse.views.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.views.DisplayModel;

public class ActionCancelScan extends CxBaseAction {
	
	private static final String CANCEL_SCAN_ICON_PATH = "platform:/plugin/org.eclipse.ui.browser/icons/clcl16/nav_stop.png";
	
	public ActionCancelScan(DisplayModel rootModel, TreeViewer resultsTree) {
		super(rootModel, resultsTree);
	}

	/**
	 * Creates a JFace action to cancel a scan 
	 */
	public Action createAction() {
		Action cancelScanAction = new Action() {
			@Override
			public void run() {
				ActionStartScan.onCancel();
				
				this.setEnabled(false);
			}
		};

		cancelScanAction.setId(ActionName.CANCEL_SCAN.name());
		cancelScanAction.setToolTipText(PluginConstants.CX_CANCEL_RUNNING_SCAN);
		cancelScanAction.setImageDescriptor(Activator.getImageDescriptor(CANCEL_SCAN_ICON_PATH));
		cancelScanAction.setEnabled(false);
		
		return cancelScanAction;
	}
}
