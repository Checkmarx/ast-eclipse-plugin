package com.checkmarx.eclipse.devassist.backend;

/**
 * Constants for DevAssist backend operations.
 * Mirrors JetBrains Constants pattern.
 */
public class Constants {
	// Main plugin bundle id, used to load icons/resources that live in the main plugin bundle
	public static final String MAIN_PLUGIN_ID = "com.checkmarx.eclipse.plugin";

	// UI strings
	public static final String BTN_OPEN_SETTINGS = "Open Settings";
	public static final String FINDINGS_PROMO_DESCRIPTION = "Checkmarx Developer Assist stops vulnerabilities where your code is written, with fixes you can actually trust.";

	// Log messages
	public static final String ERROR_BUILDING_CX_WRAPPER = "An error occurred while instantiating a CxWrapper: %s";

	// Severity level string constants
	public static final String MALICIOUS_SEVERITY = "Malicious";
	public static final String CRITICAL_SEVERITY = "Critical";
	public static final String HIGH_SEVERITY = "High";
	public static final String MEDIUM_SEVERITY = "Medium";
	public static final String LOW_SEVERITY = "Low";
	public static final String OK = "OK";
	public static final String UNKNOWN = "Unknown";
	public static final String IGNORE_LABEL = "Ignored";

	private Constants() {
		// Private constructor to prevent instantiation
	}
}
