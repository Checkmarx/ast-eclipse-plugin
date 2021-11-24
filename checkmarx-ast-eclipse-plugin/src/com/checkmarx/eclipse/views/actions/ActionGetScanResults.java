package com.checkmarx.eclipse.views.actions;

import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.viewers.TreeViewer;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.PluginListenerType;
import com.checkmarx.eclipse.views.PluginListenerDefinition;
import com.google.common.eventbus.EventBus;

public class ActionGetScanResults extends CxBaseAction {
	
	private static final String ACTION_SCAN_RESULTS_TOOLTIP = "Get results for the scan id.";
	private static final String ACTION_SCAN_RESULTS_ICON_PATH = "platform:/plugin/org.eclipse.ui.browser/icons/clcl16/nav_go.png";
	
	private static final String MSG_RETRIEVING_RESULTS = "Retrieving the results for the scan id: %s .";
	
	private Action abortScanResultsAction;
	
	private boolean alreadyRunning = false;
	private StringFieldEditor scanIdField;
	private EventBus pluginEventBus;

	public ActionGetScanResults(DisplayModel rootModel, TreeViewer resultsTree, boolean alreadyRunning, StringFieldEditor scanIdField, Action abortScanResultsAction, EventBus pluginEventBus) {
		
		super(rootModel, resultsTree);
		
		this.abortScanResultsAction = abortScanResultsAction;
		this.alreadyRunning = alreadyRunning;
		this.scanIdField = scanIdField;
		this.pluginEventBus = pluginEventBus;
	}

	/**
	 * Creates a JFace action to get scan results
	 */
	public Action createAction() {
		Action getScanResultsAction = new Action() {
			@Override
			public void run() {
				if (alreadyRunning)
					return;
				String scanId = scanIdField.getStringValue();
				if (!PluginUtils.validateScanIdFormat(scanId)) {
					showMessage("Incorrect scanId format.");
					return;
				}

				showMessage(String.format(MSG_RETRIEVING_RESULTS, scanId));

				this.setEnabled(false);
				abortScanResultsAction.setEnabled(true);

				CompletableFuture.runAsync(() -> {
					alreadyRunning = true;
					pluginEventBus.post(new PluginListenerDefinition(PluginListenerType.GET_RESULTS, DataProvider.getInstance().getResultsForScanId(scanId)));
				});
			}
		};

		getScanResultsAction.setId(ActionName.GET_RESULTS.name());
		getScanResultsAction.setToolTipText(ACTION_SCAN_RESULTS_TOOLTIP);
		getScanResultsAction.setImageDescriptor(Activator.getImageDescriptor(ACTION_SCAN_RESULTS_ICON_PATH));
		
		return getScanResultsAction;
	}

}
