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
	
}
