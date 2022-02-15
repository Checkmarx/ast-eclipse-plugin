package com.checkmarx.eclipse.views;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

public class HoverListener implements MouseTrackListener {

	private final List<Control> controls;
	private final Color defaultColor;
	private Color customColor;

	public HoverListener(List<Control> controls) {
		this.controls = controls;
		this.defaultColor = controls.size() > 0 ? controls.get(0).getBackground() : null;
	}

	@Override
	public void mouseEnter(MouseEvent arg0) {
		Color themeColor = Display.getCurrent().getSystemColor(SWT.COLOR_LIST_SELECTION);
		float[] hsba = themeColor.getRGBA().getHSBA();
		customColor = new Color(new RGBA(hsba[0], 0.10f, hsba[2], hsba[3]));
		controls.forEach(control -> {
			control.setBackground(customColor);
		});		
	}

	@Override
	public void mouseExit(MouseEvent arg0) {
		if (defaultColor != null) {
			controls.forEach(control -> {
				control.setBackground(defaultColor);	
			});
			if (customColor != null) {
				customColor.dispose();
				customColor = null;
			}
		}		
	}

	@Override
	public void mouseHover(MouseEvent arg0) {
		// do nothing		
	}
	
	public void apply() {
		controls.forEach(c -> c.addMouseTrackListener(this));
	}
}
