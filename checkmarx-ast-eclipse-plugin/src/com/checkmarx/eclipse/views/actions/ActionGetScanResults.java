package com.checkmarx.eclipse.views.actions;

import java.util.Collections;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.PluginListenerType;
import com.checkmarx.eclipse.views.PluginListenerDefinition;
import com.google.common.eventbus.EventBus;

public class ActionGetScanResults extends CxBaseAction {
	
	private static final String ACTION_SCAN_RESULTS_TOOLTIP = "Get results for the scan id.";
	private static final String ACTION_SCAN_RESULTS_ICON_PATH = "platform:/plugin/org.eclipse.ui.browser/icons/clcl16/nav_go.png";
			
	private EventBus pluginEventBus;

	public ActionGetScanResults(DisplayModel rootModel, TreeViewer resultsTree, EventBus pluginEventBus) {
		super(rootModel, resultsTree);
		
		this.pluginEventBus = pluginEventBus;
	}

	/**
	 * Creates a JFace action to get scan results
	 */
	public Action createAction() {
		Action getScanResultsAction = new Action() {
			@Override
			public void run(){
				pluginEventBus.post(new PluginListenerDefinition(PluginListenerType.REVERSE_CALL, Collections.emptyList()));
			}
		};

		getScanResultsAction.setId(ActionName.GET_RESULTS.name());
		getScanResultsAction.setToolTipText(ACTION_SCAN_RESULTS_TOOLTIP);
		getScanResultsAction.setImageDescriptor(Activator.getImageDescriptor(ACTION_SCAN_RESULTS_ICON_PATH));
		
		return getScanResultsAction;
	}

}
