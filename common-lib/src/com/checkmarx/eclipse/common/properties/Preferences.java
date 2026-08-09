package com.checkmarx.eclipse.common.properties;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.checkmarx.eclipse.common.listener.IAuthenticationSuccessHandler;
import com.checkmarx.eclipse.common.listener.ISettingsChangeNotifier;

public class Preferences {

    public static final String QUALIFIER = "com.checkmarx.eclipse";
    public static final String API_KEY = "apiKey";
    public static final String ADDITIONAL_OPTIONS = "additionalOptions";

    // Tracks whether the currently-stored API_KEY has actually been confirmed against
    // the server (Authenticator.doAuthentication succeeded)...
    public static final String CREDENTIALS_VALIDATED = "credentialsValidated";

    public static final ScopedPreferenceStore STORE = new ScopedPreferenceStore(InstanceScope.INSTANCE, QUALIFIER);

    // Handler for post-authentication UI setup (registered by devassist-lib)
    private static IAuthenticationSuccessHandler authSuccessHandler;

    // Notifier for settings changes (registered by main plugin)
    private static ISettingsChangeNotifier settingsChangeNotifier;

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

    public static void setSettingsChangeNotifier(ISettingsChangeNotifier notifier) {
        settingsChangeNotifier = notifier;
    }

    public static ISettingsChangeNotifier getSettingsChangeNotifier() {
        return settingsChangeNotifier;
    }
}