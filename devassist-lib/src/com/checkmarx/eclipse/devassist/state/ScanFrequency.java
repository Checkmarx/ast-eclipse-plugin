package com.checkmarx.eclipse.devassist.state;

/**
 * Enumeration of scan frequency options.
 * Determines when scans are triggered automatically.
 */
public enum ScanFrequency {
	ON_FILE_SAVE("on_save", "On File Save"),
	ON_DOCUMENT_CHANGE("on_change", "On Document Change (1s debounce)"),
	MANUAL_ONLY("manual", "Manual Only");

	private final String key;
	private final String label;

	ScanFrequency(String key, String label) {
		this.key = key;
		this.label = label;
	}

	public String getKey() {
		return key;
	}

	public String getLabel() {
		return label;
	}

	public static ScanFrequency fromKey(String key) {
		for (ScanFrequency freq : ScanFrequency.values()) {
			if (freq.key.equals(key)) {
				return freq;
			}
		}
		return ON_DOCUMENT_CHANGE;
	}
}
