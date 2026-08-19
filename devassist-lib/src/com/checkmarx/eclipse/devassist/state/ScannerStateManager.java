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
	private static final String KEY_PREFIX = "pref_";
	private static final String KEY_ENABLED_SUFFIX = "_enabled";
	private static final String KEY_FREQUENCY = "scan.frequency";

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

	private String getEnabledKey(ScanEngine engine) {
		return KEY_PREFIX + engine.name().toLowerCase() + KEY_ENABLED_SUFFIX;
	}
}