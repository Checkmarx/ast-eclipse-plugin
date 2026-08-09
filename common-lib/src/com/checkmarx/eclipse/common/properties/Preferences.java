package com.checkmarx.eclipse.common.properties;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

public class Preferences {

    public static final String QUALIFIER = "com.checkmarx.eclipse";
    public static final String API_KEY = "apiKey";
    public static final String ADDITIONAL_OPTIONS = "additionalOptions";

    // Tracks whether the currently-stored API_KEY has actually been confirmed against
    // the server (Authenticator.doAuthentication succeeded)...
    public static final String CREDENTIALS_VALIDATED = "credentialsValidated";

    public static final ScopedPreferenceStore STORE = new ScopedPreferenceStore(InstanceScope.INSTANCE, QUALIFIER);

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
}