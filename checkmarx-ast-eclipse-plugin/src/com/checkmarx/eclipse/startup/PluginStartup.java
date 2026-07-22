package com.checkmarx.eclipse.startup;

import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.devassist.ui.findings.realtime.CheckmarxEditorListener;
import com.checkmarx.eclipse.devassist.ui.findings.realtime.FindingsEditorHoverListener;
import com.checkmarx.eclipse.devassist.configuration.McpInstallService;
import com.checkmarx.eclipse.devassist.backend.GlobalScannerController;
import com.checkmarx.eclipse.devassist.backend.listener.ProjectLifecycleListener;
import com.checkmarx.eclipse.views.ui.WelcomeDialog;
import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.runner.TenantSettingsProvider;
import org.eclipse.swt.widgets.Display;
import java.util.concurrent.CompletableFuture;

/**
 * Plugin startup activity that runs when Eclipse starts.
 *
 * Responsibilities:
 * - Open Checkmarx views (Scan, Findings, Ignored Problems)
 * - Register the JavaEditorHoverListener to install hover handlers on editors
 * - Register the CheckmarxEditorListener for real-time scanning with debounce
 * - Install hovers/real-time scanning on any already-open editors
 * - Load mock problems for demonstration
 */
public class PluginStartup implements IStartup {

	private static final String VIEW_ID = "com.checkmarx.eclipse.views.CheckmarxView";
	private static final String FINDINGS_VIEW_ID = "com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView";
	private static FindingsEditorHoverListener hoverListener; // Keep strong reference to prevent GC
	private static CheckmarxEditorListener realtimeScanListener; // Keep strong reference to prevent GC
	private static ProjectLifecycleListener projectListener; // Keep strong reference to prevent GC

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

					// Register listener for custom hover on findings annotations
					System.out.println("[STARTUP] Registering findings hover listener...");
					hoverListener = new FindingsEditorHoverListener();
					window.getPartService().addPartListener(hoverListener);
					System.out.println("[STARTUP] ✓ Findings hover listener registered");

					// Register listener for real-time scanning with 1-second debounce
					// (Equivalent to JetBrains' LocalInspectionTool)
					System.out.println("[STARTUP] Registering real-time scanning listener...");
					realtimeScanListener = new CheckmarxEditorListener();
					window.getPartService().addPartListener(realtimeScanListener);
					System.out.println("[STARTUP] ✓ Real-time scanning listener registered");

					// Show welcome dialog if user is authenticated
					// Future: Add preference tracking to show only on first login
					if (isAuthenticated()) {
						// Commented out for now - can be enabled when preference tracking is added
						// Display.getDefault().asyncExec(() -> showWelcomeDialog(window));
					}

					// REMOVED: Problems View integration disabled
					// CxProblemsServices.publisher().publish();  // ← No longer publishing to Problems View

					// Attempt MCP installation if user is authenticated
					// This happens asynchronously in the background
					CxLogger.info("[STARTUP] Triggering MCP auto-install...");
					McpInstallService.attemptAutoInstall();

					// Initialize backend scanner infrastructure (Phase 3)
					CxLogger.info("[STARTUP] Initializing backend scanner infrastructure...");
					initializeBackendScanners();
					CxLogger.info("[STARTUP] ✓ Backend scanner infrastructure initialized");
				}
			} catch (PartInitException e) {
				CxLogger.error("Failed to open Checkmarx views on startup: " + e.getMessage(), e);
			} catch (Exception e) {
				CxLogger.error("Error during plugin startup: " + e.getMessage(), e);
			}
		});
	}

	/**
	 * Check if user is authenticated (has API key configured)
	 */
	private boolean isAuthenticated() {
		String apiKey = Preferences.getApiKey();
		return apiKey != null && !apiKey.trim().isEmpty();
	}

	/**
	 * Show the welcome dialog to the user with MCP status fetched from server
	 */
	private void showWelcomeDialog(IWorkbenchWindow window) {
		String apiKey = Preferences.getApiKey();
		String additionalParams = Preferences.getAdditionalOptions();

		// Fetch MCP status asynchronously
		CompletableFuture.supplyAsync(() -> {
			try {
				return TenantSettingsProvider.INSTANCE.isAiMcpServerEnabled(apiKey, additionalParams);
			} catch (Exception ex) {
				CxLogger.error("Failed to fetch MCP status during startup", ex);
				return false;
			}
		}).thenAccept((mcpEnabled) -> {
			Display.getDefault().asyncExec(() -> {
				try {
					WelcomeDialog dlg = new WelcomeDialog(window.getShell(), mcpEnabled);
					dlg.open();
				} catch (Exception ex) {
					CxLogger.error("Failed to show welcome dialog", ex);
				}
			});
		});
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
			// Initialize global scanner controller
			GlobalScannerController controller = GlobalScannerController.getInstance();
			CxLogger.info("[STARTUP] ✓ GlobalScannerController initialized");
			CxLogger.info(controller.getStateReport());

			// Register project lifecycle listener
			projectListener = new ProjectLifecycleListener();
			projectListener.register();
			CxLogger.info("[STARTUP] ✓ ProjectLifecycleListener registered");

		} catch (Exception e) {
			CxLogger.error("[STARTUP] Error initializing backend scanners: " +
				e.getMessage(), e);
		}
	}
}
