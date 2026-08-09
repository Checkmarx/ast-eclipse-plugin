package com.checkmarx.eclipse.devassist.state;

import com.checkmarx.eclipse.devassist.model.ScanEngine;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the current state of scanner enable/disable settings.
 * Holds which scanners are enabled and scan frequency preference.
 */
public class ScannerState {

	private final Map<ScanEngine, Boolean> scannerStates = new HashMap<>();
	private ScanFrequency frequency;

	public ScannerState() {
		initializeDefaults();
	}

	private void initializeDefaults() {
		for (ScanEngine engine : ScanEngine.values()) {
			scannerStates.put(engine, true);
		}
		this.frequency = ScanFrequency.ON_DOCUMENT_CHANGE;
	}

	public boolean isEnabled(ScanEngine engine) {
		return scannerStates.getOrDefault(engine, true);
	}

	public void setEnabled(ScanEngine engine, boolean enabled) {
		scannerStates.put(engine, enabled);
	}

	public ScanFrequency getFrequency() {
		return frequency;
	}

	public void setFrequency(ScanFrequency frequency) {
		this.frequency = frequency;
	}

	public Map<ScanEngine, Boolean> getAllStates() {
		return new HashMap<>(scannerStates);
	}
}
