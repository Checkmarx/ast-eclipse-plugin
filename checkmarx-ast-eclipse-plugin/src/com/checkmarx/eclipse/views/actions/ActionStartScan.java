package com.checkmarx.eclipse.views.actions;

import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.mylyn.commons.ui.dialogs.AbstractNotificationPopup;

import com.checkmarx.ast.scan.Scan;
import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.enums.PluginListenerType;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.GlobalSettings;
import com.checkmarx.eclipse.views.PluginListenerDefinition;
import com.google.common.eventbus.EventBus;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.NotificationPopUpUI;
import com.checkmarx.eclipse.utils.PluginConstants;

public class ActionStartScan extends CxBaseAction {
	
	private static final String ACTION_START_SCAN_ICON_PATH = "platform:/plugin/org.eclipse.ui.browser/icons/clcl16/nav_go.png";
			
	private EventBus pluginEventBus;
	private ComboViewer projectsCombo;
	private ComboViewer branchesCombo;
	private ComboViewer scansCombo;
	private Action startScanAction;
	private Action cancelScanAction;
	private ScheduledExecutorService pollScanExecutor;
	private boolean creatingScanCanceled = false;
	
	public ActionStartScan(DisplayModel rootModel, TreeViewer resultsTree, EventBus pluginEventBus, ComboViewer projectsCombo, ComboViewer branchesCombo, ComboViewer scansCombo, Action cancelScanAction) {
		super(rootModel, resultsTree);
		
		this.pluginEventBus = pluginEventBus;
		this.projectsCombo = projectsCombo;
		this.branchesCombo = branchesCombo;
		this.scansCombo = scansCombo;
		this.cancelScanAction = cancelScanAction;
	}

	/**
	 * Creates a JFace action to start a scan
	 */
	public Action createAction() {
		startScanAction = new Action() {
			@Override
			public void run(){
				this.setEnabled(false);
			    		
				String project = projectsCombo.getCombo().getText();
				String branch = branchesCombo.getCombo().getText();
				
				Job job = new Job(PluginConstants.CX_CREATING_SCAN) {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						try {
							if(ResourcesPlugin.getWorkspace().getRoot().getProjects().length > 0) {
								String projectInWorkspacePath = ResourcesPlugin.getWorkspace().getRoot().getProjects()[0].getLocation().toString();
								Scan scan = DataProvider.getInstance().createScan(projectInWorkspacePath, project, branch);
								
								if(creatingScanCanceled) {
									creatingScanCanceled = false;
									cancelScan(scan.getId());
									
									return Status.CANCEL_STATUS;
								}
								
								pollScan(scan.getId());
								cancelScanAction.setEnabled(true);
							} else {
								Display.getDefault().execute(new Runnable() {
									@Override
									public void run() {
										new NotificationPopUpUI(Display.getDefault(), PluginConstants.CX_SCAN_TITLE, PluginConstants.NO_FILES_IN_WORKSPACE, null, null, null).open();
										setEnabled(true);
									}
								});
							}
						} catch (Exception e) {
							CxLogger.error(String.format(PluginConstants.CX_ERROR_CREATING_SCAN, e.getMessage()), e);
						}
						
						return Status.OK_STATUS;
					}
					
					@Override
					protected void canceling() {
						super.canceling();
						creatingScanCanceled = true;
						this.setName(PluginConstants.CX_CANCELING_SCAN);
					}
				};
				job.schedule();
			}
		};
		
		startScanAction.setId(ActionName.START_SCAN.name());
		startScanAction.setToolTipText(PluginConstants.CX_START_SCAN);
		startScanAction.setImageDescriptor(Activator.getImageDescriptor(ACTION_START_SCAN_ICON_PATH));
		startScanAction.setEnabled(false);
		
		String runningScanId = GlobalSettings.getFromPreferences(GlobalSettings.PARAM_RUNNING_SCAN_ID, PluginConstants.EMPTY_STRING);
		boolean  isScanRunning = !StringUtils.isEmptyOrNull(runningScanId);
		
		if(isScanRunning) {
			pollScan(runningScanId);
		}
		
		return startScanAction;
	}
	
	private void pollScan(String scanId) {
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_RUNNING_SCAN_ID, scanId);
		
		Job job = new Job(String.format(PluginConstants.CX_RUNNING_SCAN, scanId)) {
			@Override
			protected IStatus run(IProgressMonitor arg0) {
				try {			
					pollScanExecutor = Executors.newScheduledThreadPool(1);					
					pollScanExecutor.scheduleAtFixedRate(pollingScan(scanId), 0, 15, TimeUnit.SECONDS);
					
					do {
	                } while (!pollScanExecutor.isTerminated());
					
				} catch (Exception e) {
					CxLogger.error(String.format(PluginConstants.CX_ERROR_GETTING_SCAN_INFO, e.getMessage()), e);
				}
				
				return Status.OK_STATUS;
			}
			
			@Override
			protected void canceling() {
				super.canceling();
				pollScanExecutor.shutdown();
				cancelScan(scanId);
			}
		};
		job.schedule();
	}
	
	private void cancelScan(String scanId) {
		try {
			DataProvider.getInstance().cancelScan(scanId);
			GlobalSettings.storeInPreferences(GlobalSettings.PARAM_RUNNING_SCAN_ID, PluginConstants.EMPTY_STRING);
			Display.getDefault().execute(new Runnable() {
				@Override
				public void run() {
					AbstractNotificationPopup notification = new NotificationPopUpUI(Display.getCurrent(), PluginConstants.CX_SCAN_CANCELED_TITLE, PluginConstants.CX_SCAN_CANCELED_DESCRIPTION, null, null, null);
					notification.setDelayClose(5000);
					notification.open();
				}
			});
			startScanAction.setEnabled(true);
			cancelScanAction.setEnabled(false);
		} catch (Exception e) {
			CxLogger.error(String.format(PluginConstants.CX_ERROR_CANCELING_SCAN, e.getMessage()), e);
		}
	}
	
	private Runnable pollingScan(String scanId) {
        return () -> {
        	try {
				Scan scan = DataProvider.getInstance().getScanInformation(scanId);
				boolean isScanRunning = scan.getStatus().toLowerCase(Locale.ROOT).equals(PluginConstants.CX_SCAN_RUNNING_STATUS);
				
				if(isScanRunning) {
					CxLogger.info(String.format(PluginConstants.CX_RUNNING_SCAN, scanId));
				} else {
					CxLogger.info(String.format(PluginConstants.CX_SCAN_FINISHED_WITH_STATUS, scan.getStatus()));
					GlobalSettings.storeInPreferences(GlobalSettings.PARAM_RUNNING_SCAN_ID, PluginConstants.EMPTY_STRING);
					pollScanExecutor.shutdown();
					cancelScanAction.setEnabled(false);
					startScanAction.setEnabled(true);
					
					if(scan.getStatus().toLowerCase(Locale.ROOT).equals(PluginConstants.CX_SCAN_COMPLETED_STATUS)) {
						Display.getDefault().execute(new Runnable() {
							@Override
							public void run() {
								AbstractNotificationPopup notification = new NotificationPopUpUI(
										Display.getCurrent(), 
										PluginConstants.CX_SCAN_FINISHED_TITLE, 
										PluginConstants.CX_SCAN_FINISHED_DESCRIPTION,
										null,
										PluginConstants.CX_LOAD_SCAN_RESULTS, 
										new SelectionAdapter() {
											@Override
											public void widgetSelected(SelectionEvent e) {
												scansCombo.getCombo().setText(scanId);
												pluginEventBus.post(new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, Collections.emptyList()));
											}
										});
								notification.setDelayClose(100000000);
								notification.open();
							}
						});
					}
				}
			} catch (Exception e) {
				CxLogger.error(String.format(PluginConstants.CX_ERROR_GETTING_SCAN_INFO, e.getMessage()), e);
			}
        };
	}
}
