package com.checkmarx.eclipse.views.provider;

import java.util.function.Function;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.graphics.Image;

import com.checkmarx.eclipse.views.DisplayModel;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ColumnProvider extends ColumnLabelProvider{
	Function<DisplayModel, Image> vulnFuncImg;
	Function<DisplayModel, String> vulnFuncTxt;
	

	public ColumnProvider(Object object, Object object2) {
		// TODO Auto-generated constructor stub
	}

	@Override
	public String getText(Object element) {
		DisplayModel v = (DisplayModel) element;
		return vulnFuncTxt.apply(v);
	}
	
	@Override
	public Image getImage(Object element) {
		DisplayModel v = (DisplayModel) element;
		return vulnFuncImg.apply(v);
	}
	
}
