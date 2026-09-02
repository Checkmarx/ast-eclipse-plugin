package com.checkmarx.eclipse.startup;

import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.listener.IProjectLifecycleListener;
import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.devassist.backend.listener.CheckmarxEditorListener;
import com.checkmarx.eclipse.devassist.backend.GlobalScannerController;
import com.checkmarx.eclipse.devassist.backend.listener.ProjectLifecycleListener;

public class PluginStartup implements IStartup {

	static {
		// Register services for PreferencesPage
		Preferences.addSettingsChangeNotifier(new SettingsChangeNotifier());
		Preferences.setWorkspaceScanService(new WorkspaceScanService());
	}

	private static final String VIEW_ID = "com.checkmarx.eclipse.views.CheckmarxView";
	private static final String FINDINGS_VIEW_ID = "com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView";
	private static CheckmarxEditorListener realtimeScanListener; // Keep strong reference to prevent GC
	private static IProjectLifecycleListener projectListener; // Keep strong reference to prevent GC

	@Override
	public void earlyStartup() {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			try {
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (window != null) {
					IWorkbenchPage page = window.getActivePage();

					// Show Checkmarx One view if not already visible
					if (page != null && page.findView(VIEW_ID) == null) {
						page.showView(VIEW_ID);
					}

					// Show Checkmarx Findings view if not already visible
					if (page != null && page.findView(FINDINGS_VIEW_ID) == null) {
						page.showView(FINDINGS_VIEW_ID);
					}

					// Register listener for real-time scanning with debounce
					realtimeScanListener = new CheckmarxEditorListener();
					window.getPartService().addPartListener(realtimeScanListener);

					// Initialize backend scanner infrastructure
					initializeBackendScanners();
				}
			} catch (PartInitException e) {
				CxLogger.error("Failed to open Checkmarx views on startup: " + e.getMessage(), e);
			} catch (Exception e) {
				CxLogger.error("Error during plugin startup: " + e.getMessage(), e);
			}
		});
	}

	/**
	 * Get the project lifecycle listener.
	 *
	 * @return the registered ProjectLifecycleListener, or null if not yet initialized
	 */
	public static IProjectLifecycleListener getProjectListener() {
		return projectListener;
	}

	/**
	 * Get the real-time editor listener that tracks per-file scan jobs.
	 *
	 * @return the registered CheckmarxEditorListener, or null if not yet initialized
	 */
	public static CheckmarxEditorListener getRealtimeScanListener() {
		return realtimeScanListener;
	}

	/**
	 * Initialize backend scanner infrastructure.
	 *
	 * Creates and registers:
	 * - GlobalScannerController (application-level singleton)
	 * - ProjectLifecycleListener (project open/close listener)
	 *
	 * This enables real-time scanning on file modifications.
	 */
	private void initializeBackendScanners() {
		try {
			GlobalScannerController controller = GlobalScannerController.getInstance();
			CxLogger.info(controller.getStateReport());

			projectListener = new ProjectLifecycleListener();
			projectListener.register();
		} catch (Exception e) {
			CxLogger.error("Error initializing backend scanners: " + e.getMessage(), e);
		}
	}
}
