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
	private Action clearSelectionAction;
	private Action abortScanResultsAction;

	public ToolBarActions(DisplayModel rootModel, TreeViewer resultsTree, boolean alreadyRunning, StringFieldEditor scanIdField, 
			Composite resultInfoPanel, Composite attackVectorPanel, Composite leftPanel, 
			ComboViewer scanIdComboViewer, ComboViewer projectComboViewer) {
		
		this.rootModel = rootModel;
		this.resultsTree = resultsTree;
		this.alreadyRunning = alreadyRunning;
		this.scanIdField = scanIdField;
		this.resultInfoPanel = resultInfoPanel;
		this.attackVectorPanel = attackVectorPanel;
		this.leftPanel = leftPanel;
		this.scanIdComboViewer = scanIdComboViewer;
		this.projectComboViewer = projectComboViewer;
		
		createActions();
	}

	/**
	 * Create all tool bar actions
	 */
	private void createActions() {
		
		clearSelectionAction = new ActionClearSelection(rootModel, resultsTree, resultInfoPanel, attackVectorPanel, leftPanel, scanIdComboViewer, projectComboViewer).createAction();
		abortScanResultsAction = new ActionAbortScanResults(rootModel, resultsTree).createAction();
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
}
