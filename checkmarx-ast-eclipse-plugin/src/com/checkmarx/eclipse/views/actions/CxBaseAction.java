package com.checkmarx.eclipse.views.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;

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
}
