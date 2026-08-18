package com.checkmarx.eclipse.enums;

/**
 * UI grouping modes for the CheckmarxView findings tree.
 * Separate from Severity enum to keep shared severity contract clean.
 *
 * Determines how findings are organized/grouped in the findings tree view.
 */
public enum GroupingMode {
	SEVERITY("Group by Severity"),
	QUERY_NAME("Group by Query Name"),
	STATE_NAME("Group by State");

	private final String displayName;

	GroupingMode(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public static GroupingMode fromString(String value) {
		if (value == null) {
			return SEVERITY; // Default
		}
		try {
			return GroupingMode.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException e) {
			return SEVERITY; // Default for invalid values
		}
	}
}
