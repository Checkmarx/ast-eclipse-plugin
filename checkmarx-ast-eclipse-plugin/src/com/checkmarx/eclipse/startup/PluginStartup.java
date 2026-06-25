package com.checkmarx.eclipse.startup;

import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.utils.CxLogger;

public class PluginStartup implements IStartup {

	private static final String VIEW_ID = "com.checkmarx.eclipse.views.CheckmarxView";

	@Override
	public void earlyStartup() {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			try {
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (window != null) {
					IWorkbenchPage page = window.getActivePage();
					if (page != null && page.findView(VIEW_ID) == null) {
						page.showView(VIEW_ID);
					}
				}
			} catch (PartInitException e) {
				CxLogger.error("Failed to open Checkmarx One view on startup: " + e.getMessage(), e);
			}
		});
	}
}
