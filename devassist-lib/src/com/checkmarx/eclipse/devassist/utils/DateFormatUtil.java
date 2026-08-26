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
	 * Formats an ISO 8601 date string as a relative date string.
	 * Supports both Instant format (e.g., "2026-06-24T17:46:28.459238500Z")
	 * and ZonedDateTime format (e.g., "2022-08-25T12:35:24.722784400+05:30[Asia/Calcutta]")
	 * Examples: "Today", "1 day ago", "2 weeks ago", "3 months ago"
	 *
	 * @param isoDateString ISO 8601 date format string
	 */
	public static String formatRelativeDate(String isoDateString) {
		if (isoDateString == null || isoDateString.isEmpty()) {
			return "Unknown";
		}

		try {
			long days = 0;

			// Try parsing as Instant first (new format: "2026-06-24T17:46:28.459238500Z")
			if (isoDateString.endsWith("Z")) {
				java.time.Instant instant = java.time.Instant.parse(isoDateString);
				days = ChronoUnit.DAYS.between(instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
						java.time.ZonedDateTime.now().toLocalDate());
			} else {
				// Parse as ZonedDateTime (old format: "2022-08-25T12:35:24.722784400+05:30[Asia/Calcutta]")
				// Remove the zone ID part in brackets if present
				String dateString = isoDateString;
				int bracketIndex = isoDateString.indexOf('[');
				if (bracketIndex > 0) {
					dateString = isoDateString.substring(0, bracketIndex);
				}

				ZonedDateTime dateTime = ZonedDateTime.parse(dateString);
				days = ChronoUnit.DAYS.between(dateTime.toLocalDate(), ZonedDateTime.now().toLocalDate());
			}

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
