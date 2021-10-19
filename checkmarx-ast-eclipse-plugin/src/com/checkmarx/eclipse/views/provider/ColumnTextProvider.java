package com.checkmarx.eclipse.views.provider;

import java.util.function.Function;

import org.eclipse.jface.viewers.ColumnLabelProvider;

import com.checkmarx.eclipse.views.DisplayModel;

public class ColumnTextProvider extends ColumnLabelProvider {

	Function<DisplayModel, String> vulnFunc;
	
	public ColumnTextProvider(Function<DisplayModel, String> vulnFunc) {
		this.vulnFunc = vulnFunc;
	}
	
	@Override
	public String getText(Object element) {
		DisplayModel v = (DisplayModel) element;
		return vulnFunc.apply(v);
	}
	
}
