package com.checkmarx.eclipse.views.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;

import com.checkmarx.eclipse.views.DisplayModel;

public class ToolBarActions {

	private List<Action> toolBarActions = new ArrayList<Action>();

	private DisplayModel rootModel;
	private TreeViewer resultsTree;
	private StringFieldEditor scanIdField;
	private boolean alreadyRunning = false;

	private Composite resultInfoPanel;
	private Composite attackVectorPanel;
	private Composite leftPanel;

	private ComboViewer scanIdComboViewer;
	private ComboViewer projectComboViewer;
	
	private Action scanResultsAction;
		
	public ToolBarActions(ToolBarActionsBuilder toolBarActionsBuilder) {
		this.rootModel = toolBarActionsBuilder.rootModel;
		this.resultsTree = toolBarActionsBuilder.resultsTree;
		this.alreadyRunning = toolBarActionsBuilder.alreadyRunning;
		this.scanIdField = toolBarActionsBuilder.scanIdField;
		this.resultInfoPanel = toolBarActionsBuilder.resultInfoPanel;
		this.attackVectorPanel = toolBarActionsBuilder.attackVectorPanel;
		this.leftPanel = toolBarActionsBuilder.leftPanel;
		this.scanIdComboViewer = toolBarActionsBuilder.scanIdComboViewer;
		this.projectComboViewer = toolBarActionsBuilder.projectComboViewer;
		
		createActions();
	}

	/**
	 * Create all tool bar actions
	 */
	private void createActions() {
		
		Action clearSelectionAction = new ActionClearSelection(rootModel, resultsTree, resultInfoPanel, attackVectorPanel, leftPanel, scanIdComboViewer, projectComboViewer).createAction();
		Action abortScanResultsAction = new ActionAbortScanResults(rootModel, resultsTree).createAction();
		scanResultsAction = new ActionGetScanResults(rootModel, resultsTree, alreadyRunning, scanIdField, abortScanResultsAction).createAction();
		
		toolBarActions.add(clearSelectionAction);
		toolBarActions.add(scanResultsAction);
		toolBarActions.add(abortScanResultsAction);
	}
	
	/**
	 * Gets all tool bar actions
	 * 
	 * @return
	 */
	public List<Action> getToolBarActions() {
		return this.toolBarActions;
	}
	
	/**
	 * Gets scan results action
	 * 
	 * @return
	 */
	public Action getScanResultsAction() {
		return scanResultsAction;
	}
	
	/**
	 * Builder Class to construct a ToolBarActions
	 * 
	 * @author HugoMa
	 *
	 */
	public static class ToolBarActionsBuilder {
		
		private DisplayModel rootModel;
		private TreeViewer resultsTree;
		private StringFieldEditor scanIdField;
		private boolean alreadyRunning = false;
		
		private Composite resultInfoPanel;
		private Composite attackVectorPanel;
		private Composite leftPanel;

		private ComboViewer scanIdComboViewer;
		private ComboViewer projectComboViewer;
		
		public ToolBarActionsBuilder() {}
		
		public ToolBarActionsBuilder rootModel(DisplayModel rootModel) {
			this.rootModel = rootModel;
			return this;
		}
		
		public ToolBarActionsBuilder resultsTree(TreeViewer resultsTree) {
			this.resultsTree = resultsTree;
			return this;
		}
		
		public ToolBarActionsBuilder scanIdField(StringFieldEditor scanIdField) {
			this.scanIdField = scanIdField;
			return this;
		}
		
		public ToolBarActionsBuilder alreadyRunning(boolean alreadyRunning) {
			this.alreadyRunning = alreadyRunning;
			return this;
		}
		
		public ToolBarActionsBuilder resultInfoPanel(Composite resultInfoPanel) {
			this.resultInfoPanel = resultInfoPanel;
			return this;
		}
		
		public ToolBarActionsBuilder attackVectorPanel(Composite attackVectorPanel) {
			this.attackVectorPanel = attackVectorPanel;
			return this;
		}
		
		public ToolBarActionsBuilder leftPanel(Composite leftPanel) {
			this.leftPanel = leftPanel;
			return this;
		}
		
		public ToolBarActionsBuilder scanIdComboViewer(ComboViewer scanIdComboViewer) {
			this.scanIdComboViewer = scanIdComboViewer;
			return this;
		}
		
		public ToolBarActionsBuilder projectComboViewer(ComboViewer projectComboViewer) {
			this.projectComboViewer = projectComboViewer;
			return this;
		}
		
		public ToolBarActions build() {
			return new ToolBarActions(this);
		}
	}
}
