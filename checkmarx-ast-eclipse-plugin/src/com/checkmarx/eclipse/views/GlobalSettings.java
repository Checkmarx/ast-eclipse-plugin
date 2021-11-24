package com.checkmarx.eclipse.views;

import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import com.checkmarx.eclipse.views.filters.FilterState;

public class GlobalSettings {

	private static final String GLOBAL_SETTINGS_ID = "settings.ast";
	private static final String PLUGIN_SETTINGS_ID = "plugin.settings";
	
	
	public static final String PARAM_PROJECT_ID = "project-id";
	public static final String PARAM_SCAN_ID = "scan-id";
	
	private String projectId;
	private String scanId;

	private static final Preferences preferences = ConfigurationScope.INSTANCE.getNode(GLOBAL_SETTINGS_ID);
	
	public GlobalSettings() {}
	
	
	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getScanId() {
		return scanId;
	}

	public void setScanId(String scanId) {
		this.scanId = scanId;
	}

	/**
	 * Load current settings
	 */
	public void loadSettings() {
		setProjectId(getFromPreferences(PARAM_PROJECT_ID, ""));
		setScanId(getFromPreferences(PARAM_SCAN_ID, ""));
		FilterState.loadFiltersFromSettings();
	}

	/**
	 * Store a setting in preferences
	 * 
	 * @param key
	 * @param value
	 */
	public static void storeInPreferences(String key, String value) {
		Preferences storage = preferences.node(PLUGIN_SETTINGS_ID);
		storage.put(key, value);

		try {
			// forces the application to save the preferences
			preferences.flush();
		} catch (BackingStoreException e2) {
			e2.printStackTrace();
		}
	}
	
	/**
	 * Get a setting value from the preferences
	 * 
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	public static String getFromPreferences(String key, String defaultValue) {
		return preferences.node(PLUGIN_SETTINGS_ID).get(key, defaultValue);
	}
}
