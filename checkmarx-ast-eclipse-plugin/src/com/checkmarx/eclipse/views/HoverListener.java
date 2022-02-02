package com.checkmarx.eclipse.views;

import java.util.List;

import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.themes.ITheme;

public class HoverListener implements MouseTrackListener {

	private static final String HOVER_COLOR_KEY = "org.eclipse.ui.workbench.HOVER_BACKGROUND";
	private final List<Control> controls;
	private final Color defaultColor;

	public HoverListener(List<Control> controls) {
		this.controls = controls;
		this.defaultColor = controls.size() > 0 ? controls.get(0).getBackground() : null;
	}

	@Override
	public void mouseEnter(MouseEvent arg0) {
		ITheme currentTheme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
		Color c = currentTheme.getColorRegistry().get(HOVER_COLOR_KEY);
		controls.forEach(control -> control.setBackground(c));		
	}

	@Override
	public void mouseExit(MouseEvent arg0) {
		if (defaultColor != null) {
			controls.forEach(control -> control.setBackground(defaultColor));
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
