package com.checkmarx.eclipse.devassist.backend;

/**
 * Severity level enumeration matching JetBrains implementation.
 * Provides 8 severity levels with precedence values (lower = more severe).
 */
public enum SeverityLevel {
	MALICIOUS("Malicious", 1),
	CRITICAL("Critical", 2),
	HIGH("High", 3),
	MEDIUM("Medium", 4),
	LOW("Low", 5),
	UNKNOWN("Unknown", 6),
	OK("OK", 7),
	IGNORED("Ignored", 8);

	private final String severity;
	private final int precedence;

	SeverityLevel(String severity, int precedence) {
		this.severity = severity;
		this.precedence = precedence;
	}

	public String getSeverity() {
		return severity;
	}

	public int getPrecedence() {
		return precedence;
	}

	/**
	 * Convert string severity value to enum.
	 * Returns UNKNOWN if no match found.
	 *
	 * @param value Severity string (case-insensitive)
	 * @return Matching SeverityLevel or UNKNOWN
	 */
	public static SeverityLevel fromValue(String value) {
		if (value == null) {
			return UNKNOWN;
		}
		for (SeverityLevel level : values()) {
			if (level.getSeverity().equalsIgnoreCase(value)) {
				return level;
			}
		}
		return UNKNOWN;
	}
}
