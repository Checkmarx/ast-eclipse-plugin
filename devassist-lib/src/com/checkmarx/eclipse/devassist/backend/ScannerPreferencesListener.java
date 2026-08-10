package com.checkmarx.eclipse.devassist.backend;

import com.checkmarx.eclipse.common.listener.ISettingsChangeNotifier;
import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType;

/**
 * Listens for preference changes and syncs them to GlobalScannerController.
 *
 * This bridges the gap between CheckmarxPreferencePage (in common-lib)
 * and GlobalScannerController (in devassist-lib) using the listener pattern
 * to avoid circular module dependencies.
 *
 * Pattern from JetBrains: When preferences change, listeners update the
 * runtime controller state so that scanner execution is gated by the
 * latest preferences.
 *
 * Lifecycle:
 * 1. User changes scanner checkboxes in CheckmarxPreferencePage
 * 2. CheckmarxPreferencePage.performOk() saves to preferences
 * 3. CheckmarxPreferencePage notifies ISettingsChangeNotifier
 * 4. This listener's onSettingsApplied() is called
 * 5. GlobalScannerController is synced with new preferences
 * 6. Future scans respect the new preferences
 */
public class ScannerPreferencesListener implements ISettingsChangeNotifier {

    private static final String LOG_TAG = "[SCANNER-PREFS-LISTENER]";

    /**
     * Called when preferences are applied (from CheckmarxPreferencePage.performOk()).
     * Syncs the preference store with GlobalScannerController so execution guards use latest state.
     */
    @Override
    public void notifySettingsApplied() {
        try {
            CxLogger.info(LOG_TAG + " Syncing preferences to GlobalScannerController");

            GlobalScannerController controller = GlobalScannerController.getInstance();

            // Load current preferences and sync with controller
            boolean ascaEnabled = Preferences.STORE.getBoolean(Preferences.PREF_ASCA_ENABLED);
            boolean ossEnabled = Preferences.STORE.getBoolean(Preferences.PREF_OSS_ENABLED);
            boolean secretsEnabled = Preferences.STORE.getBoolean(Preferences.PREF_SECRETS_ENABLED);
            boolean containersEnabled = Preferences.STORE.getBoolean(Preferences.PREF_CONTAINERS_ENABLED);
            boolean iacEnabled = Preferences.STORE.getBoolean(Preferences.PREF_IAC_ENABLED);

            // Log what we're reading
            CxLogger.info(LOG_TAG + " Read from STORE: ASCA=" + ascaEnabled + ", OSS=" + ossEnabled +
                         ", SECRETS=" + secretsEnabled + ", CONTAINERS=" + containersEnabled + ", IAC=" + iacEnabled);

            // Update controller (mirrors JetBrains GlobalScannerController.updateScannerState())
            if (ascaEnabled) {
                controller.enableScanner(ScannerType.ASCA);
            } else {
                controller.disableScanner(ScannerType.ASCA);
            }

            if (ossEnabled) {
                controller.enableScanner(ScannerType.OSS);
            } else {
                controller.disableScanner(ScannerType.OSS);
            }

            if (secretsEnabled) {
                controller.enableScanner(ScannerType.SECRETS);
            } else {
                controller.disableScanner(ScannerType.SECRETS);
            }

            if (containersEnabled) {
                controller.enableScanner(ScannerType.CONTAINERS);
            } else {
                controller.disableScanner(ScannerType.CONTAINERS);
            }

            if (iacEnabled) {
                controller.enableScanner(ScannerType.IAC);
            } else {
                controller.disableScanner(ScannerType.IAC);
            }

            // Verify controller state after sync
            CxLogger.info(LOG_TAG + " Preference sync complete. " + controller.getStateReport());

        } catch (Exception e) {
            CxLogger.error(LOG_TAG + " Failed to sync preferences: " + e.getMessage(), e);
        }
    }
}
