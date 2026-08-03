package com.checkmarx.eclipse.properties;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.checkmarx.eclipse.Activator;

public class Preferences {

	public static final String QUALIFIER = "com.checkmarx.eclipse";
	public static final String API_KEY = "apiKey";
	public static final String ADDITIONAL_OPTIONS = "additionalOptions";

	// Tracks whether the currently-stored API_KEY has actually been confirmed against
	// the server (Authenticator.doAuthentication succeeded), as opposed to merely being
	// present - typing a key into the Checkmarx One preference field and clicking
	// Apply/OK persists API_KEY without ever validating it, since FieldEditorPreferencePage
	// stores bound fields unconditionally. Anything that gates "is the user logged in" on
	// API_KEY alone would then treat an untested (possibly wrong) key as a successful login.
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
		IPreferenceStore prefStore = Activator.getDefault().getPreferenceStore();
		prefStore.setValue(key, value);
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
