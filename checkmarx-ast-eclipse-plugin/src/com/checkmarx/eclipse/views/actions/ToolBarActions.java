package com.checkmarx.eclipse.views.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.enums.PluginListenerType;
import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.utils.PluginConstants;
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
	public static final String GROUP_BY_STATE_NAME = "State Name";
	
	public static final String MENU_FILTER_BY = "Filter By";
	
	private List<Action> toolBarActions = new ArrayList<>();
	
	private IActionBars actionBars;

	private DisplayModel rootModel;
	private TreeViewer resultsTree;
	
	private EventBus pluginEventBus;
	
	private Action startScanAction;
	private Action cancelScanAction;
	private Action groupBySeverityAction;
	private Action groupByQueryNameAction;
	private Action groupByStateNameAction;
	private Action stateFilter;
	
	private ComboViewer projectsCombo;
	private ComboViewer branchesCombo;
	private ComboViewer scansCombo;
	
	private List<Action> filterActions;
		
	private ToolBarActions(ToolBarActionsBuilder toolBarActionsBuilder) {
		this.actionBars = toolBarActionsBuilder.actionBars;
		this.rootModel = toolBarActionsBuilder.rootModel;
		this.resultsTree = toolBarActionsBuilder.resultsTree;
		this.pluginEventBus = toolBarActionsBuilder.pluginEventBus;
		this.projectsCombo = toolBarActionsBuilder.projectsCombo;
		this.branchesCombo = toolBarActionsBuilder.branchesCombo;
		this.scansCombo = toolBarActionsBuilder.scansCombo;
		
		createActions();
	}

	/**
	 * Create all tool bar actions
	 */
	private void createActions() {
		filterActions = new ActionFilters(pluginEventBus).createFilterActions();
		
		cancelScanAction = new ActionCancelScan(rootModel, resultsTree).createAction();
		startScanAction = new ActionStartScan(rootModel, resultsTree, pluginEventBus, projectsCombo, branchesCombo, scansCombo, cancelScanAction).createAction();
		stateFilter = new ActionFilterStatePreference(pluginEventBus);
		
		toolBarActions.addAll(filterActions);
		toolBarActions.add(startScanAction);
		toolBarActions.add(cancelScanAction);
		toolBarActions.add(stateFilter);
		
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
		
		Action resetPlugin = new Action() {
			@Override
			public void run() {
				pluginEventBus.post(new PluginListenerDefinition(PluginListenerType.CLEAN_AND_REFRESH, Collections.emptyList()));
			}
		};

		resetPlugin.setId(ActionName.CLEAN_AND_REFRESH.name());
		resetPlugin.setToolTipText(PluginConstants.TOOLBAR_ACTION_CLEAR_RESULTS);
		resetPlugin.setText(PluginConstants.TOOLBAR_ACTION_CLEAR_RESULTS);
		
		Action openPreferencesPageAction = new ActionOpenPreferencesPage(rootModel, resultsTree, PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell()).createAction();

		dropDownMenu.add(resetPlugin);
		dropDownMenu.add(openPreferencesPageAction);
		
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
	 * Get scan results action
	 * 
	 * @return
	 */
	public Action getStartScanAction() {
		return startScanAction;
	}
	
	/**
	 * Get abort results action
	 * 
	 * @return
	 */
	public Action getCancelScanAction() {
		return cancelScanAction;
	}
	
	/**
	 * Get filter state action
	 * 
	 * @return
	 */
	public Action getStateFilterAction() {
		return stateFilter;
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
		
		private ComboViewer projectsCombo;
		private ComboViewer branchesCombo;
		private ComboViewer scansCombo;
		
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
		
		public ToolBarActionsBuilder projectsCombo(ComboViewer projectsCombo) {
			this.projectsCombo = projectsCombo;
			return this;
		}
		
		public ToolBarActionsBuilder branchesCombo(ComboViewer branchesCombo) {
			this.branchesCombo = branchesCombo;
			return this;
		}
		
		public ToolBarActionsBuilder scansCombo(ComboViewer scansCombo) {
			this.scansCombo = scansCombo;
			return this;
		}
		
		public ToolBarActions build() {
			return new ToolBarActions(this);
		}
	}
}
