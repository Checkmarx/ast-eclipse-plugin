package com.checkmarx.eclipse.views.actions;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jgit.api.Git;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.mylyn.commons.ui.dialogs.AbstractNotificationPopup;

import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Result;
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
import com.checkmarx.eclipse.utils.PluginUtils;

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
	private static Job pollJob;

	public ActionStartScan(DisplayModel rootModel, TreeViewer resultsTree, EventBus pluginEventBus,
			ComboViewer projectsCombo, ComboViewer branchesCombo, ComboViewer scansCombo, Action cancelScanAction) {
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
			public void run() {
				setEnabled(false);

				String branch = branchesCombo.getCombo().getText();
				String currentGitBranch = getCurrentGitBranch();
				boolean matchProject = cxProjectMatchesWorkspaceProject();
				boolean matchBranch = StringUtils.isEmpty(currentGitBranch) || currentGitBranch.equals(branch);

				if (!matchProject && !matchBranch) {
					displayMismatchNotification(PluginConstants.CX_PROJECT_AND_BRANCH_MISMATCH,
							PluginConstants.CX_PROJECT_AND_BRANCH_MISMATCH_QUESTION);
					return;
				}

				if (!matchBranch) {
					displayMismatchNotification(PluginConstants.CX_BRANCH_MISMATCH,
							PluginConstants.CX_BRANCH_MISMATCH_QUESTION);
					return;
				}

				if (!matchProject) {
					displayMismatchNotification(PluginConstants.CX_PROJECT_MISMATCH,
							PluginConstants.CX_PROJECT_MISMATCH_QUESTION);
					return;
				}

				createScan();
			}
		};

		startScanAction.setId(ActionName.START_SCAN.name());
		startScanAction.setToolTipText(PluginConstants.CX_START_SCAN);
		startScanAction.setImageDescriptor(Activator.getImageDescriptor(ACTION_START_SCAN_ICON_PATH));

		String branch = GlobalSettings.getFromPreferences(GlobalSettings.PARAM_BRANCH, PluginConstants.EMPTY_STRING);

		startScanAction.setEnabled(StringUtils.isNotBlank(branch));

		String runningScanId = GlobalSettings.getFromPreferences(GlobalSettings.PARAM_RUNNING_SCAN_ID,
				PluginConstants.EMPTY_STRING);
		boolean isScanRunning = StringUtils.isNotEmpty(runningScanId);

		if (isScanRunning) {
			pollScan(runningScanId);
		}

		return startScanAction;
	}

	/**
	 * Display notification about project/branch mismatch
	 * 
	 * @param title
	 * @param question
	 */
	private void displayMismatchNotification(String title, String question) {
		boolean loadResults = MessageDialog.openQuestion(Display.getDefault().getActiveShell(), title, question);

		if (loadResults) {
			createScan();
		}

		startScanAction.setEnabled(!loadResults);
	}

	/**
	 * Create a new scan
	 */
	private void createScan() {
		String project = projectsCombo.getCombo().getText();
		String branch = branchesCombo.getCombo().getText();

		Job job = new Job(PluginConstants.CX_CREATING_SCAN) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					if (ResourcesPlugin.getWorkspace().getRoot().getProjects().length > 0) {
						String projectInWorkspacePath = ResourcesPlugin.getWorkspace().getRoot().getProjects()[0]
								.getLocation().toString();
						Scan scan = DataProvider.getInstance().createScan(projectInWorkspacePath, project, branch);

						if (creatingScanCanceled) {
							creatingScanCanceled = false;
							cancelScan(scan.getId());

							return Status.CANCEL_STATUS;
						}

						pollScan(scan.getId());
					} else {
						Display.getDefault().syncExec(new Runnable() {
							@Override
							public void run() {
								new NotificationPopUpUI(Display.getDefault(), PluginConstants.CX_SCAN_TITLE,
										PluginConstants.NO_FILES_IN_WORKSPACE, null, null, null).open();
								startScanAction.setEnabled(true);
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

	/**
	 * Get current git branch in scm
	 * 
	 * @return
	 */
	private String getCurrentGitBranch() {
		if (ResourcesPlugin.getWorkspace().getRoot().getProjects().length > 0) {
			try {
				String projectInWorkspacePath = ResourcesPlugin.getWorkspace().getRoot().getProjects()[0].getLocation()
						.toString();
				Git git = Git.open(new File(projectInWorkspacePath));

				return git.getRepository().getBranch();
			} catch (IOException e) {
				return PluginConstants.EMPTY_STRING;
			}
		}

		return PluginConstants.EMPTY_STRING;
	}

	/**
	 * Check if checkmarx project matches workspace project
	 * 
	 * @return
	 */
	private boolean cxProjectMatchesWorkspaceProject() {
		Results results = DataProvider.getInstance().getCurrentResults();
		boolean noResultsInScan = results == null || results.getResults().isEmpty();
		boolean noFilesInWorkspace = ResourcesPlugin.getWorkspace().getRoot().getProjects().length == 0;

		if (noResultsInScan || noFilesInWorkspace) {
			return true;
		}

		List<String> resultsFileNames = new ArrayList<String>();

		for (Result result : results.getResults()) {
			if (!Optional.ofNullable(result.getData().getNodes()).orElse(Collections.emptyList()).isEmpty()) {
				// Add SAST file name
				resultsFileNames.add(result.getData().getNodes().get(0).getFileName());
			} else if (StringUtils.isNotEmpty(result.getData().getFileName())) {
				// Add KICS file name
				resultsFileNames.add(result.getData().getFileName());
			}
		}

		for (String fileName : resultsFileNames) {
			Path filePath = new Path(fileName);
			List<IFile> filesFound = PluginUtils.findFileInWorkspace(filePath.lastSegment());

			if (filesFound.size() > 0) {
				return true;
			}
		}

		return false;
	}

	private void pollScan(String scanId) {
		cancelScanAction.setEnabled(true);
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_RUNNING_SCAN_ID, scanId);

		pollJob = new Job(String.format(PluginConstants.CX_RUNNING_SCAN, scanId)) {
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
				this.setName(PluginConstants.CX_CANCELING_SCAN);
				super.canceling();
				pollScanExecutor.shutdown();
				cancelScan(scanId);
			}
		};
		pollJob.schedule();
	}

	public static void onCancel() {
		pollJob.cancel();
	}

	private void cancelScan(String scanId) {
		Job job = new Job(PluginConstants.CX_CANCELING_SCAN) {
			@Override
			protected IStatus run(IProgressMonitor arg0) {
				try {
					DataProvider.getInstance().cancelScan(scanId);
					GlobalSettings.storeInPreferences(GlobalSettings.PARAM_RUNNING_SCAN_ID,
							PluginConstants.EMPTY_STRING);
					Display.getDefault().syncExec(new Runnable() {
						@Override
						public void run() {
							AbstractNotificationPopup notification = new NotificationPopUpUI(Display.getCurrent(),
									PluginConstants.CX_SCAN_CANCELED_TITLE,
									PluginConstants.CX_SCAN_CANCELED_DESCRIPTION, null, null, null);
							notification.setDelayClose(8000);
							notification.open();
						}
					});
					startScanAction.setEnabled(true);
					cancelScanAction.setEnabled(false);
				} catch (Exception e) {
					CxLogger.error(String.format(PluginConstants.CX_ERROR_CANCELING_SCAN, e.getMessage()), e);
				}

				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	private Runnable pollingScan(String scanId) {
		return () -> {
			try {
				Scan scan = DataProvider.getInstance().getScanInformation(scanId);
				boolean isScanRunning = scan.getStatus().toLowerCase(Locale.ROOT)
						.equals(PluginConstants.CX_SCAN_RUNNING_STATUS);

				if (isScanRunning) {
					CxLogger.info(String.format(PluginConstants.CX_RUNNING_SCAN, scanId));
				} else {
					CxLogger.info(String.format(PluginConstants.CX_SCAN_FINISHED_WITH_STATUS, scan.getStatus()));
					GlobalSettings.storeInPreferences(GlobalSettings.PARAM_RUNNING_SCAN_ID,
							PluginConstants.EMPTY_STRING);
					pollScanExecutor.shutdown();
					cancelScanAction.setEnabled(false);
					startScanAction.setEnabled(true);

					if (scan.getStatus().toLowerCase(Locale.ROOT).equals(PluginConstants.CX_SCAN_COMPLETED_STATUS)) {
						Display.getDefault().syncExec(new Runnable() {
							AbstractNotificationPopup notification;

							@Override
							public void run() {
								notification = new NotificationPopUpUI(Display.getCurrent(),
										PluginConstants.CX_SCAN_FINISHED_TITLE,
										PluginConstants.CX_SCAN_FINISHED_DESCRIPTION, null,
										PluginConstants.CX_LOAD_SCAN_RESULTS, new SelectionAdapter() {
											@Override
											public void widgetSelected(SelectionEvent e) {
												scansCombo.getCombo().setText(scanId);
												pluginEventBus.post(new PluginListenerDefinition(
														PluginListenerType.LOAD_RESULTS_FOR_SCAN,
														Collections.emptyList()));
												notification.close();
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
