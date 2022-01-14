package com.checkmarx.eclipse.views.filters;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.enums.PluginListenerType;
import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.PluginListenerDefinition;
import com.google.common.eventbus.EventBus;

public class ActionFilters {
	
	private static final String ACTION_FILTER_HIGH_TOOLTIP = "High";
	private static final String ACTION_FILTER_HIGH_ICON_PATH = "/icons/high_untoggle.png";
	
	private static final String ACTION_FILTER_MEDIUM_TOOLTIP = "Medium";
	private static final String ACTION_FILTER_MEDIUM_ICON_PATH = "/icons/medium_untoggle.png";
	
	private static final String ACTION_FILTER_LOW_TOOLTIP = "Low";
	private static final String ACTION_FILTER_LOW_ICON_PATH = "/icons/low_untoggle.png";
	
	private static final String ACTION_FILTER_INFO_TOOLTIP = "Info";
	private static final String ACTION_FILTER_INFO_ICON_PATH = "/icons/info_untoggle.png";
	
	private EventBus pluginEventBus;
	
	public ActionFilters(EventBus pluginEventBus) {		
		this.pluginEventBus = pluginEventBus;
	}
	
	/**
	 * Creates a JFace actions to severity filters
	 * 
	 * @return
	 */
	public List<Action> createFilterActions(){
		List<Action> filters = new ArrayList<>();
		
		Action filterHighAction = createFilterAction(ACTION_FILTER_HIGH_TOOLTIP, ACTION_FILTER_HIGH_ICON_PATH, Severity.HIGH, ActionName.HIGH);
		Action filterMediumAction = createFilterAction(ACTION_FILTER_MEDIUM_TOOLTIP, ACTION_FILTER_MEDIUM_ICON_PATH, Severity.MEDIUM, ActionName.MEDIUM);
		Action filterLowAction = createFilterAction(ACTION_FILTER_LOW_TOOLTIP, ACTION_FILTER_LOW_ICON_PATH, Severity.LOW, ActionName.LOW);
		Action filterInfoAction = createFilterAction(ACTION_FILTER_INFO_TOOLTIP, ACTION_FILTER_INFO_ICON_PATH, Severity.INFO, ActionName.INFO);
		
		filters.add(filterHighAction);
		filters.add(filterMediumAction);
		filters.add(filterLowAction);
		filters.add(filterInfoAction);
		
		return filters;
	}
	
	/**
	 * Creates a filter action
	 * 
	 * @param tooltip
	 * @param imagePath
	 * @param severity
	 * @param actionName
	 * @return
	 */
	private Action createFilterAction(String tooltip, String imagePath, Severity severity, ActionName actionName) {
		Action filterAction = new Action() {
			@Override
			public void run() {
				FilterState.setState(severity);
				pluginEventBus.post(new PluginListenerDefinition(PluginListenerType.FILTER_CHANGED, DataProvider.getInstance().sortResults()));
			}
		};

		filterAction.setId(actionName.name());
		filterAction.setToolTipText(tooltip);
		filterAction.setImageDescriptor(Activator.getImageDescriptor(imagePath));
		filterAction.setEnabled(DataProvider.getInstance().containsResults());
		filterAction.setChecked(FilterState.isSeverityEnabled(severity.name()));
		
		return filterAction;
	}	
}
