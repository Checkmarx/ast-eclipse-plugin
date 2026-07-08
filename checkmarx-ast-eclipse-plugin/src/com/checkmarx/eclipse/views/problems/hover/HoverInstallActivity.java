package com.checkmarx.eclipse.views.problems.hover;

import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Startup activity to install text hover listener when Eclipse starts.
 */
public class HoverInstallActivity implements IStartup {

	@Override
	public void earlyStartup() {
		System.out.println("[HOVER-INSTALL] *** EARLY STARTUP CALLED ***");
		System.out.flush();

		try {
			System.out.println("[HOVER-INSTALL] Getting workbench...");

			// Try multiple times to get the window (it might not be ready immediately)
			for (int i = 0; i < 10; i++) {
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				System.out.println("[HOVER-INSTALL] Attempt " + (i+1) + ": window = " + (window != null ? "FOUND" : "null"));

				if (window != null) {
					System.out.println("[HOVER-INSTALL] ✓ Window found, registering listener...");
					try {
						JavaEditorHoverListener listener = new JavaEditorHoverListener();
						window.getPartService().addPartListener(listener);
						System.out.println("[HOVER-INSTALL] ✓✓✓ HOVER LISTENER REGISTERED ✓✓✓");
						System.out.flush();
						return;
					} catch (Exception e) {
						System.err.println("[HOVER-INSTALL] Error registering listener: " + e.getMessage());
						e.printStackTrace();
					}
				}

				try {
					Thread.sleep(100); // Wait a bit before retrying
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}

			System.out.println("[HOVER-INSTALL] ✗ Could not get workbench window after retries");
		} catch (Exception e) {
			System.err.println("[HOVER-INSTALL] Error in earlyStartup: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
