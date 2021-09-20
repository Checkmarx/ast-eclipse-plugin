package com.checkmarx.eclipse.views.provider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;

import com.checkmarx.ast.results.structure.CxResult;
import com.checkmarx.eclipse.views.DisplayModel;

public class TreeContentProvider implements ITreeContentProvider {

	@Override
	public Object[] getElements(Object inputElement) {
		 return ((DisplayModel) inputElement).children.toArray(); 
		 
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		  return ((DisplayModel) parentElement).children.toArray(); 
	}

	@Override
	public Object getParent(Object element) {
		if (element == null) {
			return null;
		}

		return ((DisplayModel) element).parent;
	}

	@Override
	public boolean hasChildren(Object element) {
		List<DisplayModel> children = ((DisplayModel) element).children;
		return children != null && children.size() > 0;
	}

}
