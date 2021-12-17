package com.checkmarx.eclipse.views.actions;


import java.util.Collections;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.enums.PluginListenerType;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.PluginListenerDefinition;
import com.google.common.eventbus.EventBus;

public class ActionClearSelection extends CxBaseAction {
	
	private static final String ACTION_CLEAR_SELECTION_TOOLTIP = "Clear the selected scanId and the results view.";
	private static final String ACTION_CLEAR_SELECTION_ICON_PATH = "platform:/plugin/org.eclipse.ui.views.log/icons/elcl16/refresh.png";
	
	private EventBus pluginEventBus;
	
	public ActionClearSelection(DisplayModel rootModel, TreeViewer resultsTree, EventBus pluginEventBus) {
		super(rootModel, resultsTree);
		
		this.pluginEventBus = pluginEventBus;
	}

	/**
	 * Creates a JFace action to clear selection
	 */
	public Action createAction() {
		Action clearSelectionAction = new Action() {
			@Override
			public void run() {
				pluginEventBus.post(new PluginListenerDefinition(PluginListenerType.CLEAN_AND_REFRESH, Collections.emptyList()));
			}
		};

		clearSelectionAction.setId(ActionName.CLEAN_AND_REFRESH.name());
		clearSelectionAction.setToolTipText(ACTION_CLEAR_SELECTION_TOOLTIP);
		clearSelectionAction.setImageDescriptor(Activator.getImageDescriptor(ACTION_CLEAR_SELECTION_ICON_PATH));
		clearSelectionAction.setEnabled(false);
		
		return clearSelectionAction;
	}
	
}
