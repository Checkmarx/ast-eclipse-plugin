package com.checkmarx.eclipse.views.actions;

import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;

import com.checkmarx.ast.project.Project;
import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;

public class ActionClearSelection extends CxBaseAction {
	
	private static final String ACTION_CLEAR_SELECTION_TOOLTIP = "Clear the selected scanId and the results view.";
	private static final String ACTION_CLEAR_SELECTION_ICON_PATH = "platform:/plugin/org.eclipse.ui/icons/full/etool16/delete.png";
	
	private static final String PLACEHOLDER_SCAN_COMBO_VIEWER_TEXT = "Select scan id";
	private static final String PLACEHOLDER_PROJECT_COMBO_VIEWER_TEXT = "Select project";
	
	private Composite resultInfoCompositePanel;
	private Composite attackVectorCompositePanel;
	private Composite leftCompositePanel;
	
	private ComboViewer scanIdComboViewer;
	private ComboViewer projectComboViewer;
	
	public ActionClearSelection(DisplayModel rootModel, TreeViewer resultsTree, Composite resultInfoCompositePanel, Composite attackVectorCompositePanel, Composite leftCompositePanel, ComboViewer scanIdComboViewer, ComboViewer projectComboViewer) {
		
		super(rootModel, resultsTree);
		
		this.resultInfoCompositePanel = resultInfoCompositePanel;
		this.attackVectorCompositePanel = attackVectorCompositePanel;
		this.leftCompositePanel = leftCompositePanel;
		this.scanIdComboViewer = scanIdComboViewer;
		this.projectComboViewer = projectComboViewer;
	}

	/**
	 * Creates a JFace action to clear selection
	 */
	public Action createAction() {
		Action clearSelectionAction = new Action() {
			@Override
			public void run() {
				resultInfoCompositePanel.setVisible(false);
				attackVectorCompositePanel.setVisible(false);
			
				clearResultsTreeViewer();
				leftCompositePanel.layout();
				
				clearScanIdComboViewer();
				clearProjectComboViewer();
				reloadProjectComboViewer();
			}
		};

		clearSelectionAction.setToolTipText(ACTION_CLEAR_SELECTION_TOOLTIP);
		clearSelectionAction.setImageDescriptor(Activator.getImageDescriptor(ACTION_CLEAR_SELECTION_ICON_PATH));
		
		return clearSelectionAction;
	}
	
	/**
	 * Clears Results' tree
	 */
	private void clearResultsTreeViewer() {
		rootModel.children.clear();
		resultsTree.refresh();
	}
	
	/**
	 * Clears Scans' combobox
	 */
	private void clearScanIdComboViewer() {
		PluginUtils.enableComboViewer(scanIdComboViewer, false);
		scanIdComboViewer.refresh();
		scanIdComboViewer.setInput(Collections.EMPTY_LIST);
		PluginUtils.setTextForComboViewer(scanIdComboViewer, PLACEHOLDER_SCAN_COMBO_VIEWER_TEXT);
		scanIdComboViewer.getCombo().update();
	}
	
	/**
	 * Clears Projects' combobox
	 */
	private void clearProjectComboViewer() {
		projectComboViewer.setInput(Collections.EMPTY_LIST);
		PluginUtils.setTextForComboViewer(projectComboViewer, PLACEHOLDER_PROJECT_COMBO_VIEWER_TEXT);
	}
	
	/**
	 * Reloads Projects' combobox
	 */
	private void reloadProjectComboViewer() {
		PluginUtils.enableComboViewer(projectComboViewer, false);
		PluginUtils.setTextForComboViewer(projectComboViewer, "Getting the projects from AST server...");
		projectComboViewer.getCombo().update();
		List<Project> projectList = DataProvider.INSTANCE.getProjectList();
		projectComboViewer.setInput(projectList);
		projectComboViewer.refresh();
		PluginUtils.setTextForComboViewer(projectComboViewer ,PLACEHOLDER_PROJECT_COMBO_VIEWER_TEXT);
		PluginUtils.enableComboViewer(projectComboViewer, true);
	}
	
}
