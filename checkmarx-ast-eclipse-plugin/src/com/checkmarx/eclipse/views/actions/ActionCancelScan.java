package com.checkmarx.eclipse.views.actions;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.mylyn.commons.ui.dialogs.AbstractNotificationPopup;
import org.eclipse.swt.widgets.Display;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.NotificationPopUpUI;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.GlobalSettings;

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
				Job job = new Job(PluginConstants.CX_CANCELING_SCAN) {
					@Override
					protected IStatus run(IProgressMonitor arg0) {
						try {
							DataProvider.getInstance().cancelScan(GlobalSettings.getFromPreferences(GlobalSettings.PARAM_RUNNING_SCAN_ID, PluginConstants.EMPTY_STRING));
							
							do {
			                } while (!StringUtils.isEmptyOrNull(GlobalSettings.getFromPreferences(GlobalSettings.PARAM_RUNNING_SCAN_ID, PluginConstants.EMPTY_STRING)));
							
							Display.getDefault().syncExec(new Runnable() {
								@Override
								public void run() {
									AbstractNotificationPopup notification = new NotificationPopUpUI(Display.getCurrent(), PluginConstants.CX_SCAN_CANCELED_TITLE, PluginConstants.CX_SCAN_CANCELED_DESCRIPTION, null, null, null);
									notification.setDelayClose(5000);
									notification.open();
								}
							});
							
						} catch (Exception e) {
							CxLogger.error(String.format(PluginConstants.CX_ERROR_CANCELING_SCAN, e.getMessage()), e);
						}
						
						return Status.OK_STATUS;
					}
				};
				job.schedule();
				
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
