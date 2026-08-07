package com.checkmarx.eclipse.common.properties;

import org.eclipse.core.runtime.Platform;

/**
 * Read-only access to the Checkmarx preference store, for bundles that only
 * need to read values (never store them). The store itself is owned by the
 * main plugin's Activator; reads go through the platform preferences
 * service, which is not bundle-specific.
 */
public class SharedPreferences {

	public static final String QUALIFIER = "com.checkmarx.eclipse";
	public static final String API_KEY = "apiKey";
	public static final String ADDITIONAL_OPTIONS = "additionalOptions";

	private SharedPreferences() {
	}

	public static String getPref(String key) {
		return Platform.getPreferencesService().getString(QUALIFIER, key, null, null);
	}

	public static String getApiKey() {
		return getPref(API_KEY);
	}

	public static String getAdditionalOptions() {
		return getPref(ADDITIONAL_OPTIONS);
	}
}
