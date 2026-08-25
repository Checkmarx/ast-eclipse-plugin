package com.checkmarx.eclipse.devassist.utils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * Utility class for formatting dates as relative time strings.
 * Formats dates as "Today", "Yesterday", "X days ago", "X weeks ago", etc.
 */
public final class DateFormatUtil {

	private DateFormatUtil() {
		// Private constructor to prevent instantiation
	}

	/**
	 * Formats an ISO 8601 Instant string as a relative date string.
	 * Examples: "Today", "1 day ago", "2 weeks ago", "3 months ago"
	 *
	 * @param isoDateString ISO 8601 Instant format string (e.g., "2026-06-24T17:46:28.459238500Z")
	 * @return Formatted relative date string, or "Unknown" if parsing fails
	 */
	public static String formatRelativeDate(String isoDateString) {
		if (isoDateString == null || isoDateString.isEmpty()) {
			return "Unknown";
		}

		try {
			java.time.Instant instant = java.time.Instant.parse(isoDateString);
			long days = ChronoUnit.DAYS.between(instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
					java.time.ZonedDateTime.now().toLocalDate());

			if (days == 0) {
				return "Today";
			} else if (days == 1) {
				return "Yesterday";
			} else if (days < 7) {
				return days + " days ago";
			} else if (days < 30) {
				long weeks = days / 7;
				return weeks + (weeks == 1 ? " week ago" : " weeks ago");
			} else if (days < 365) {
				long months = days / 30;
				return months + (months == 1 ? " month ago" : " months ago");
			} else {
				long years = days / 365;
				return years + (years == 1 ? " year ago" : " years ago");
			}
		} catch (Exception e) {
			CxLogger.warning("DateFormatUtil: Failed to parse date: " + isoDateString + " - " + e.getMessage());
			return isoDateString;
		}
	}
}
