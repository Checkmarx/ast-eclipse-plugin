package com.checkmarx.eclipse.views.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.IActionBars;

import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.enums.PluginListenerType;
import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.PluginListenerDefinition;
import com.checkmarx.eclipse.views.filters.ActionFilters;
import com.checkmarx.eclipse.views.filters.FilterState;
import com.google.common.eventbus.EventBus;

public class ToolBarActions {
	
	public static final String MENU_GROUP_BY = "Group By";
	public static final String GROUP_BY_SEVERITY = "Severity";
	public static final String GROUP_BY_QUERY_NAME = "Query Name";
	public static final String GROUP_BY_STATE_NAME = "State";
	
	/*
	 * Filter state
	 * 
	 */
	
	public static final String MENU_FILTER_BY = "Filter State";
	

	private List<Action> toolBarActions = new ArrayList<>();
	
	private IActionBars actionBars;

	private DisplayModel rootModel;
	private TreeViewer resultsTree;
	
	private EventBus pluginEventBus;
	
	private Action clearAndRefreshAction;
	private Action scanResultsAction;
	private Action abortResultsAction;
	private Action groupBySeverityAction;
	private Action groupByQueryNameAction;
	private Action groupByStateNameAction;
	private List<Action> filterActions;
		
	private ToolBarActions(ToolBarActionsBuilder toolBarActionsBuilder) {
		this.actionBars = toolBarActionsBuilder.actionBars;
		this.rootModel = toolBarActionsBuilder.rootModel;
		this.resultsTree = toolBarActionsBuilder.resultsTree;
		this.pluginEventBus = toolBarActionsBuilder.pluginEventBus;
		
		createActions();
	}

	/**
	 * Create all tool bar actions
	 */
	private void createActions() {
		filterActions = new ActionFilters(pluginEventBus).createFilterActions();
		
		clearAndRefreshAction = new ActionClearSelection(rootModel, resultsTree, pluginEventBus).createAction();
		abortResultsAction = new ActionAbortScanResults(rootModel, resultsTree).createAction();
		scanResultsAction = new ActionGetScanResults(rootModel, resultsTree, pluginEventBus).createAction();
	     
		toolBarActions.addAll(filterActions);
		toolBarActions.add(clearAndRefreshAction);
		toolBarActions.add(scanResultsAction);
		toolBarActions.add(abortResultsAction);
		
		createGroupByActions();
	}
	
	/**
	 * Create Group By actions (Severity & Query Name)
	 */
	private void createGroupByActions() {
		groupBySeverityAction = new Action(GROUP_BY_SEVERITY, IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				FilterState.setState(Severity.GROUP_BY_SEVERITY);
				pluginEventBus.post(new PluginListenerDefinition(PluginListenerType.FILTER_CHANGED, DataProvider.getInstance().sortResults()));
			}
		};
		
		groupBySeverityAction.setId(ActionName.GROUP_BY_SEVERITY.name());
		groupBySeverityAction.setChecked(FilterState.groupBySeverity);

		groupByQueryNameAction = new Action(GROUP_BY_QUERY_NAME, IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				FilterState.setState(Severity.GROUP_BY_QUERY_NAME);
				pluginEventBus.post(new PluginListenerDefinition(PluginListenerType.FILTER_CHANGED, DataProvider.getInstance().sortResults()));
			}
		};
		
		groupByQueryNameAction.setId(ActionName.GROUP_BY_QUERY_NAME.name());
		groupByQueryNameAction.setChecked(FilterState.groupByQueryName);
		
		groupByStateNameAction = new Action(GROUP_BY_STATE_NAME, IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				FilterState.setState(Severity.GROUP_BY_STATE_NAME);
				pluginEventBus.post(new PluginListenerDefinition(PluginListenerType.FILTER_CHANGED, DataProvider.getInstance().sortResults()));
			}
		};
		
		groupByStateNameAction.setId(ActionName.GROUP_BY_STATE_NAME.name());
		groupByStateNameAction.setChecked(FilterState.groupByStateName);
		
		filterActions.add(groupBySeverityAction);
		filterActions.add(groupByQueryNameAction);
		filterActions.add(groupByStateNameAction);
		
		IMenuManager dropDownMenu = actionBars.getMenuManager();
		MenuManager subMenu = new MenuManager(MENU_GROUP_BY, MENU_GROUP_BY);
		subMenu.add(groupBySeverityAction);
		subMenu.add(groupByQueryNameAction);
		subMenu.add(groupByStateNameAction);
		dropDownMenu.add(subMenu);
		
		actionBars.updateActionBars();
	}
	
	/**
	 * Get all tool bar actions
	 * 
	 * @return
	 */
	public List<Action> getToolBarActions() {
		return this.toolBarActions;
	}
	
	/**
	 * Get clear and refresh action
	 * 
	 * @return
	 */
	public Action getClearAndRefreshAction() {
		return clearAndRefreshAction;
	}
	
	/**
	 * Get scan results action
	 * 
	 * @return
	 */
	public Action getScanResultsAction() {
		return scanResultsAction;
	}
	
	/**
	 * Get abort results action
	 * 
	 * @return
	 */
	public Action getAbortResultsAction() {
		return abortResultsAction;
	}
	
	/**
	 * Get all filter actions
	 * 
	 * @return
	 */
	public List<Action> getFilterActions() {
		return filterActions;
	}
	
	/**
	 * Builder Class to construct a ToolBarActions
	 * 
	 * @author HugoMa
	 *
	 */
	public static class ToolBarActionsBuilder {
		
		private IActionBars actionBars;
		
		private DisplayModel rootModel;
		private TreeViewer resultsTree;
	
		private EventBus pluginEventBus;
		
		public ToolBarActionsBuilder() {}
		
		public ToolBarActionsBuilder actionBars(IActionBars actionBars) {
			this.actionBars = actionBars;
			return this;
		}
		
		public ToolBarActionsBuilder rootModel(DisplayModel rootModel) {
			this.rootModel = rootModel;
			return this;
		}
		
		public ToolBarActionsBuilder resultsTree(TreeViewer resultsTree) {
			this.resultsTree = resultsTree;
			return this;
		}
		
		public ToolBarActionsBuilder pluginEventBus(EventBus pluginEventBus) {
			this.pluginEventBus = pluginEventBus;
			return this;
		}
		
		public ToolBarActions build() {
			return new ToolBarActions(this);
		}
	}
}
