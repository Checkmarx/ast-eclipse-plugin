package com.checkmarx.eclipse.startup;

import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.devassist.problems.CxProblemsServices;
import com.checkmarx.eclipse.devassist.problems.hover.JavaEditorHoverListener;
import com.checkmarx.eclipse.devassist.problems.commands.ProblemsViewFilterManager;
import com.checkmarx.eclipse.devassist.ui.findings.realtime.CheckmarxEditorListener;
import com.checkmarx.eclipse.devassist.configuration.McpInstallService;
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
	private static JavaEditorHoverListener hoverListener; // Keep strong reference to prevent GC
	private static CheckmarxEditorListener realtimeScanListener; // Keep strong reference to prevent GC

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

					// Register listener for hover installation on new/opened editors
					hoverListener = new JavaEditorHoverListener();
					window.getPartService().addPartListener(hoverListener);

					// Register listener for real-time scanning with 1-second debounce
					// (Equivalent to JetBrains' LocalInspectionTool)
					System.out.println("[STARTUP] Registering real-time scanning listener...");
					realtimeScanListener = new CheckmarxEditorListener();
					window.getPartService().addPartListener(realtimeScanListener);
					System.out.println("[STARTUP] ✓ Real-time scanning listener registered");

					// Install hover and real-time scanning on any already-open editors
					if (page != null) {
						org.eclipse.ui.IEditorReference[] editors = page.getEditorReferences();
						for (org.eclipse.ui.IEditorReference editorRef : editors) {
							org.eclipse.ui.IEditorPart editor = editorRef.getEditor(false);
							if (editor != null) {
								hoverListener.installHoverOnEditor(editor);
								// Real-time scanning is also installed via the part listener
								// (called automatically via partOpened/partActivated)
							}
						}
					}

					// Initialize Problems View filter manager
					System.out.println("[STARTUP] Initializing Problems View filter manager...");
					try {
						ProblemsViewFilterManager filterManager = ProblemsViewFilterManager.getInstance();
						filterManager.register();
						System.out.println("[STARTUP] ✓ Problems View filter manager initialized");
					} catch (Exception e) {
						System.err.println("[STARTUP] Could not initialize filter manager: " + e.getMessage());
					}

					// Show welcome dialog if user is authenticated
					// Future: Add preference tracking to show only on first login
					if (isAuthenticated()) {
						// Commented out for now - can be enabled when preference tracking is added
						// Display.getDefault().asyncExec(() -> showWelcomeDialog(window));
					}

					// Load mock problems for demonstration/testing
					CxProblemsServices.publisher().publish();

					// Attempt MCP installation if user is authenticated
					// This happens asynchronously in the background
					CxLogger.info("[STARTUP] Triggering MCP auto-install...");
					McpInstallService.attemptAutoInstall();
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
}
