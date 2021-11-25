package com.checkmarx.eclipse.views.filters;

public enum Severity {
	
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
