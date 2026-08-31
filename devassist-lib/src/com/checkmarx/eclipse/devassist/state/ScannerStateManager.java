package com.checkmarx.eclipse.devassist.state;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.checkmarx.eclipse.devassist.model.ScanEngine;

/**
 * Manages scanner state persistence using Eclipse preferences.
 * Loads and saves which scanners are enabled/disabled and scan frequency
 * preference.
 */
public class ScannerStateManager {

	// Aligned to match the canonical plugin qualifier used across the plugin
	private static final String PLUGIN_ID = "com.checkmarx.eclipse";
	// IMPORTANT: Must match the keys used in CheckmarxPreferencePage
	private static final String KEY_PREFIX = "scanner.";
	private static final String KEY_ENABLED_SUFFIX = ".enabled";
	private static final String KEY_FREQUENCY = "scan.frequency";
	private static final String KEY_USER_PREFERENCES_SET = "user.preferences.set";

	private final IPreferenceStore prefs;

	public ScannerStateManager() {
		this.prefs = new ScopedPreferenceStore(InstanceScope.INSTANCE, PLUGIN_ID);
	}

	public ScannerStateManager(IPreferenceStore prefs) {
		this.prefs = prefs;
	}

	public ScannerState loadState() {
		ScannerState state = new ScannerState();

		for (ScanEngine engine : ScanEngine.values()) {
			String key = getEnabledKey(engine);
			boolean enabled = prefs.getBoolean(key);
			state.setEnabled(engine, enabled);
		}

		String freqKey = prefs.getString(KEY_FREQUENCY);
		state.setFrequency(ScanFrequency.fromKey(freqKey));

		return state;
	}

	public void saveState(ScannerState state) {
		for (ScanEngine engine : ScanEngine.values()) {
			String key = getEnabledKey(engine);
			boolean enabled = state.isEnabled(engine);
			prefs.setValue(key, enabled);
		}

		prefs.setValue(KEY_FREQUENCY, state.getFrequency().getKey());
	}

	public boolean isScannerEnabled(ScanEngine engine) {
		return prefs.getBoolean(getEnabledKey(engine));
	}

	public void setScannerEnabled(ScanEngine engine, boolean enabled) {
		prefs.setValue(getEnabledKey(engine), enabled);
	}

	public ScanFrequency getScanFrequency() {
		String key = prefs.getString(KEY_FREQUENCY);
		return ScanFrequency.fromKey(key);
	}

	public void setScanFrequency(ScanFrequency frequency) {
		prefs.setValue(KEY_FREQUENCY, frequency.getKey());
	}

	/**
	 * Checks if user has explicitly configured scanner preferences.
	 * Returns true if ANY scanner preference has been explicitly set (stored in preferences).
	 * This is more reliable than checking a separate flag, as it detects actual customization.
	 */
	public boolean isUserPreferencesSet() {
		// Check if the explicit flag is set (legacy behavior)
		if (prefs.getBoolean(KEY_USER_PREFERENCES_SET)) {
			return true;
		}

		// Also check if ANY scanner preference has been explicitly set in the store
		// This handles cases where user went directly to preferences page and configured scanners
		for (ScanEngine engine : ScanEngine.values()) {
			String key = getEnabledKey(engine);
			// If this key exists in the preference store (has been explicitly set), preferences are set
			if (prefs.contains(key)) {
				return true;
			}
		}

		return false;
	}

	public void setUserPreferencesSet(boolean set) {
		prefs.setValue(KEY_USER_PREFERENCES_SET, set);
	}

	private String getEnabledKey(ScanEngine engine) {
		return KEY_PREFIX + engine.name().toLowerCase() + KEY_ENABLED_SUFFIX;
	}
}