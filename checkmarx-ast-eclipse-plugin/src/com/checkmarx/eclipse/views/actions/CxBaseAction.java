package com.checkmarx.eclipse.views.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;

public abstract class CxBaseAction {
	
	public DisplayModel rootModel;
	public TreeViewer resultsTree;
	
	public CxBaseAction(DisplayModel rootModel, TreeViewer resultsTree) {
		this.rootModel = rootModel;
		this.resultsTree = resultsTree;
	}
	
	/**
	 * Create a JFace action
	 * 
	 * @return
	 */
	public abstract Action createAction();
	
	/**
	 * Add a message to the tree
	 * 
	 * @param message
	 */
	public void showMessage(String message) {
		rootModel.children.clear();
		rootModel.children.add(DataProvider.INSTANCE.message(message));
		resultsTree.refresh();
	}
	
}
