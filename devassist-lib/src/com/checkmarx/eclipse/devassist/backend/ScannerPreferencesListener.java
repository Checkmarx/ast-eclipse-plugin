package com.checkmarx.eclipse.devassist.backend;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.checkmarx.eclipse.common.listener.ISettingsChangeNotifier;
import com.checkmarx.eclipse.common.listener.IWorkspaceScanService;
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
     * Syncs the preference store with GlobalScannerController so execution guards use latest state,
     * then reacts to whatever changed:
     * - Scanners that just got disabled have their existing findings purged immediately.
     * - Scanners that just got enabled are combined into a single consolidated scan trigger,
     *   even if several scanners were toggled on at once in the same Apply/OK click.
     */
    @Override
    public void notifySettingsApplied() {
        try {
            CxLogger.info(LOG_TAG + " Syncing preferences to GlobalScannerController");

            GlobalScannerController controller = GlobalScannerController.getInstance();

            Map<ScannerType, Boolean> desiredState = new EnumMap<>(ScannerType.class);
            desiredState.put(ScannerType.ASCA, Preferences.STORE.getBoolean(Preferences.PREF_ASCA_ENABLED));
            desiredState.put(ScannerType.OSS, Preferences.STORE.getBoolean(Preferences.PREF_OSS_ENABLED));
            desiredState.put(ScannerType.SECRETS, Preferences.STORE.getBoolean(Preferences.PREF_SECRETS_ENABLED));
            desiredState.put(ScannerType.CONTAINERS, Preferences.STORE.getBoolean(Preferences.PREF_CONTAINERS_ENABLED));
            desiredState.put(ScannerType.IAC, Preferences.STORE.getBoolean(Preferences.PREF_IAC_ENABLED));

            CxLogger.info(LOG_TAG + " Read from STORE: " + desiredState);

            Set<ScannerType> newlyEnabled = EnumSet.noneOf(ScannerType.class);
            Set<ScannerType> newlyDisabled = EnumSet.noneOf(ScannerType.class);

            for (Map.Entry<ScannerType, Boolean> entry : desiredState.entrySet()) {
                ScannerType type = entry.getKey();
                boolean shouldBeEnabled = entry.getValue();
                boolean wasEnabled = controller.isScannerEnabled(type);

                if (shouldBeEnabled) {
                    controller.enableScanner(type);
                } else {
                    controller.disableScanner(type);
                }

                if (shouldBeEnabled && !wasEnabled) {
                    newlyEnabled.add(type);
                } else if (!shouldBeEnabled && wasEnabled) {
                    newlyDisabled.add(type);
                }
            }

            CxLogger.info(LOG_TAG + " Preference sync complete. " + controller.getStateReport());

            // Disable: purge findings for scanners that just got turned off.
            for (ScannerType type : newlyDisabled) {
                CxLogger.info(LOG_TAG + " Purging findings for disabled scanner: " + type);
                ScannerMarkerPurger.purgeScanner(type);
            }

            // Enable (single or multiple at once): clear state cache and trigger one consolidated scan.
            if (!newlyEnabled.isEmpty()) {
                CxLogger.info(LOG_TAG + " Clearing state cache for newly enabled scanners: " + newlyEnabled);
                ScanStateCacheClearer.clearForScanners(newlyEnabled);

                CxLogger.info(LOG_TAG + " Triggering consolidated scan for newly enabled scanners: " + newlyEnabled);
                IWorkspaceScanService scanService = Preferences.getWorkspaceScanService();
                if (scanService != null) {
                    scanService.scanWorkspace();
                } else {
                    CxLogger.warning(LOG_TAG + " No workspace scan service registered; cannot trigger scan");
                }
            }

        } catch (Exception e) {
            CxLogger.error(LOG_TAG + " Failed to sync preferences: " + e.getMessage(), e);
        }
    }
}
