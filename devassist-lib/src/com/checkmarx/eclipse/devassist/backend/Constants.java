package com.checkmarx.eclipse.devassist.backend;

/**
 * Constants for DevAssist backend operations.
 * Mirrors JetBrains Constants pattern.
 */
public class Constants {
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
