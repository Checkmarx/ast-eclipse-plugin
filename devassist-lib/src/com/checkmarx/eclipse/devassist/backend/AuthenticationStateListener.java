package com.checkmarx.eclipse.devassist.backend;

import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.common.listener.IWorkspaceScanService;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType;
import java.util.EnumSet;

/**
 * Listens for authentication state changes (CREDENTIALS_VALIDATED flag) and triggers
 * workspace scan when user logs in.
 *
 * Problem it solves:
 * - When user logs in, no preferences change, so ScannerPreferencesListener doesn't trigger scan
 * - But we still need to scan projects that were opened before authentication
 *
 * Solution:
 * - Listen to CREDENTIALS_VALIDATED changes
 * - When it becomes true (login), trigger workspace scan immediately
 * - ScannerPreferencesListener handles preference changes separately
 */
public class AuthenticationStateListener implements IPropertyChangeListener {

	private static final String LOG_TAG = "[AUTH-STATE-LISTENER]";

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		if (event == null || event.getProperty() == null) {
			return;
		}

		// Only respond to authentication state changes
		if (!Preferences.CREDENTIALS_VALIDATED.equals(event.getProperty())) {
			return;
		}

		Object newValue = event.getNewValue();
		boolean nowAuthenticated = newValue instanceof Boolean && (Boolean) newValue;

		// Only trigger scan on login (true), not on logout (false)
		if (nowAuthenticated) {
			CxLogger.info(LOG_TAG + " User authenticated - clearing scan cache and triggering workspace scan...");

			// CRITICAL: Clear scan state cache so files that were never scanned (before authentication)
			// are not treated as "unchanged" and skipped. Without this, files show as "cached/unchanged"
			// and the scan is skipped even though they were never actually scanned before.
			try {
				CxLogger.info(LOG_TAG + " Clearing scan state cache for all scanners...");
				EnumSet<ScannerType> allScanners = EnumSet.allOf(ScannerType.class);
				ScanStateCacheClearer.clearForScanners(allScanners);
				CxLogger.info(LOG_TAG + " ✓ Scan cache cleared");
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error clearing scan cache: " + e.getMessage());
			}

			// Now trigger workspace scan with cleared cache
			IWorkspaceScanService scanService = Preferences.getWorkspaceScanService();
			if (scanService != null) {
				try {
					scanService.scanWorkspace();
					CxLogger.info(LOG_TAG + " ✓ Workspace scan triggered on login");
				} catch (Exception e) {
					CxLogger.error(LOG_TAG + " Error triggering workspace scan: " + e.getMessage(), e);
				}
			} else {
				CxLogger.warning(LOG_TAG + " Workspace scan service not available");
			}
		}
	}
}