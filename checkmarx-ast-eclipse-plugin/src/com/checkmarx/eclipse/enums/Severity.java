package com.checkmarx.eclipse.enums;

public enum Severity {
	
	CRITICAL,
	HIGH,
	MEDIUM,
	LOW,
	INFO,
	GROUP_BY_SEVERITY,
	GROUP_BY_QUERY_NAME;
	
	public static Severity getSeverity(String severity) {
		return Severity.valueOf(severity);
	}
}
