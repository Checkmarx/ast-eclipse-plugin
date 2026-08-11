package com.checkmarx.eclipse.common.preferences;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.checkmarx.eclipse.common.listener.IAuthenticationSuccessHandler;
import com.checkmarx.eclipse.common.listener.ISettingsChangeNotifier;
import com.checkmarx.eclipse.common.listener.IWorkspaceScanService;

public class Preferences {

    public static final String QUALIFIER = "com.checkmarx.eclipse";
    public static final String API_KEY = "apiKey";
    public static final String ADDITIONAL_OPTIONS = "additionalOptions";

    // Tracks whether the currently-stored API_KEY has actually been confirmed against
    // the server (Authenticator.doAuthentication succeeded)...
    public static final String CREDENTIALS_VALIDATED = "credentialsValidated";

    // Scanner Preference Keys (from CheckmarxPreferencePage)
    public static final String PREF_ASCA_ENABLED = "scanner.asca.enabled";
    public static final String PREF_OSS_ENABLED = "scanner.oss.enabled";
    public static final String PREF_SECRETS_ENABLED = "scanner.secrets.enabled";
    public static final String PREF_CONTAINERS_ENABLED = "scanner.containers.enabled";
    public static final String PREF_IAC_ENABLED = "scanner.iac.enabled";
    public static final String PREF_CONTAINERS_TOOL = "scanner.containers.tool";

    // User Preferences (preserved when features toggle) - mirrors JetBrains pattern
    public static final String USER_PREF_ASCA_ENABLED = "userPref.scanner.asca.enabled";
    public static final String USER_PREF_OSS_ENABLED = "userPref.scanner.oss.enabled";
    public static final String USER_PREF_SECRETS_ENABLED = "userPref.scanner.secrets.enabled";
    public static final String USER_PREF_CONTAINERS_ENABLED = "userPref.scanner.containers.enabled";
    public static final String USER_PREF_IAC_ENABLED = "userPref.scanner.iac.enabled";
    public static final String USER_PREFERENCES_SET = "userPreferences.set";

    public static final ScopedPreferenceStore STORE = new ScopedPreferenceStore(InstanceScope.INSTANCE, QUALIFIER);

    // Handler for post-authentication UI setup (registered by devassist-lib)
    private static IAuthenticationSuccessHandler authSuccessHandler;

    // Notifiers for settings changes (registered by main plugin and devassist-lib).
    // A List is used because both bundles register their own notifier for different
    // purposes (UI panel refresh vs. scanner-state sync); a single-slot field would
    // let one registration silently overwrite the other.
    private static final List<ISettingsChangeNotifier> settingsChangeNotifiers = new CopyOnWriteArrayList<>();

    // Service for triggering workspace scans (registered by main plugin)
    private static IWorkspaceScanService workspaceScanService;

    private Preferences() {
    }

    public static String getPref(String key) {
        return Platform.getPreferencesService().getString(Preferences.QUALIFIER, key, null, null);
    }

    public static String getApiKey() {
        return getPref(API_KEY);
    }

    public static String getAdditionalOptions() {
        return getPref(ADDITIONAL_OPTIONS);
    }

    public static void store(String key, String value) {
        // Replaced Activator call with the ScopedPreferenceStore instance
        STORE.setValue(key, value);
    }

    public static void clearApiKey() {
        STORE.setValue(API_KEY, "");
        STORE.setValue(CREDENTIALS_VALIDATED, false);
    }

    public static boolean isCredentialsValidated() {
        return STORE.getBoolean(CREDENTIALS_VALIDATED);
    }

    public static void setCredentialsValidated(boolean validated) {
        STORE.setValue(CREDENTIALS_VALIDATED, validated);
    }

    public static void setAuthenticationSuccessHandler(IAuthenticationSuccessHandler handler) {
        authSuccessHandler = handler;
    }

    public static IAuthenticationSuccessHandler getAuthenticationSuccessHandler() {
        return authSuccessHandler;
    }

    public static void addSettingsChangeNotifier(ISettingsChangeNotifier notifier) {
        settingsChangeNotifiers.add(notifier);
    }

    public static List<ISettingsChangeNotifier> getSettingsChangeNotifiers() {
        return settingsChangeNotifiers;
    }

    public static void setWorkspaceScanService(IWorkspaceScanService service) {
        workspaceScanService = service;
    }

    public static IWorkspaceScanService getWorkspaceScanService() {
        return workspaceScanService;
    }

    // ============================================================================
    // USER PREFERENCES - Preserve user's scanner choices across feature toggles
    // Mirrors JetBrains GlobalSettingsState.setUserPreferences() pattern
    // ============================================================================

    /**
     * Save user's current scanner preferences for preservation when features toggle.
     * Called when user clicks OK/Apply on preferences page, or when a feature is about to disable.
     *
     * @param asca Enable/disable ASCA
     * @param oss Enable/disable OSS
     * @param secrets Enable/disable Secrets
     * @param containers Enable/disable Containers
     * @param iac Enable/disable IaC
     */
    public static void setUserPreferences(boolean asca, boolean oss, boolean secrets,
                                         boolean containers, boolean iac) {
        STORE.setValue(USER_PREF_ASCA_ENABLED, asca);
        STORE.setValue(USER_PREF_OSS_ENABLED, oss);
        STORE.setValue(USER_PREF_SECRETS_ENABLED, secrets);
        STORE.setValue(USER_PREF_CONTAINERS_ENABLED, containers);
        STORE.setValue(USER_PREF_IAC_ENABLED, iac);
        STORE.setValue(USER_PREFERENCES_SET, true);
    }

    /**
     * Restore user's previously saved preferences to current scanner settings.
     * Called when a feature re-enables after being disabled.
     *
     * @return true if preferences were restored, false if no preferences saved
     */
    public static boolean applyUserPreferencesToCurrentSettings() {
        if (!STORE.getBoolean(USER_PREFERENCES_SET)) {
            return false;  // No user preferences saved yet
        }

        boolean asca = STORE.getBoolean(USER_PREF_ASCA_ENABLED);
        boolean oss = STORE.getBoolean(USER_PREF_OSS_ENABLED);
        boolean secrets = STORE.getBoolean(USER_PREF_SECRETS_ENABLED);
        boolean containers = STORE.getBoolean(USER_PREF_CONTAINERS_ENABLED);
        boolean iac = STORE.getBoolean(USER_PREF_IAC_ENABLED);

        // Apply to current settings
        STORE.setValue(PREF_ASCA_ENABLED, asca);
        STORE.setValue(PREF_OSS_ENABLED, oss);
        STORE.setValue(PREF_SECRETS_ENABLED, secrets);
        STORE.setValue(PREF_CONTAINERS_ENABLED, containers);
        STORE.setValue(PREF_IAC_ENABLED, iac);

        return true;
    }

    /**
     * Check if user has any custom preferences saved.
     * Used to determine if this is first time or existing user.
     *
     * @return true if preferences have been saved, false if default state
     */
    public static boolean getUserPreferencesSet() {
        return STORE.getBoolean(USER_PREFERENCES_SET);
    }

    /**
     * Save current scanner settings as user preferences.
     * Called before disabling scanners to preserve user's choices.
     */
    public static void saveCurrentSettingsAsUserPreferences() {
        boolean asca = STORE.getBoolean(PREF_ASCA_ENABLED);
        boolean oss = STORE.getBoolean(PREF_OSS_ENABLED);
        boolean secrets = STORE.getBoolean(PREF_SECRETS_ENABLED);
        boolean containers = STORE.getBoolean(PREF_CONTAINERS_ENABLED);
        boolean iac = STORE.getBoolean(PREF_IAC_ENABLED);

        setUserPreferences(asca, oss, secrets, containers, iac);
    }
}