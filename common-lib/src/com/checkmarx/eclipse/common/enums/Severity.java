package com.checkmarx.eclipse.common.enums;

/**
 * Severity levels for security findings.
 *
 * Note: UI grouping modes are kept separate in the plugin module (GroupingMode enum).
 * This enum is limited to actual severity levels for the shared contract.
 */
public enum Severity {

	CRITICAL,
	HIGH,
	MEDIUM,
	LOW,
	INFO;

	public static Severity getSeverity(String severity) {
		return Severity.valueOf(severity);
	}
}
