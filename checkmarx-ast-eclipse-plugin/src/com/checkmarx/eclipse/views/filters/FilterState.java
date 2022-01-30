package com.checkmarx.eclipse.views.filters;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.views.GlobalSettings;

public class FilterState {

	public static boolean high = true;
	public static boolean medium = true;
	public static boolean low = false;
	public static boolean info = false;
	public static boolean groupBySeverity = true;
	public static boolean groupByQueryName = true;

	
	
	public static void loadFiltersFromSettings() {
		high = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.HIGH.name(), "true"));
		medium = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.MEDIUM.name(), "true"));
		low = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.LOW.name(), "false"));
		info = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.INFO.name(), "false"));
		groupBySeverity = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.GROUP_BY_SEVERITY.name(), "true"));
		groupByQueryName = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.GROUP_BY_QUERY_NAME.name(), "true"));
	}
	
	/**
	 * Change severity state
	 * 
	 * @param severity
	 */
	public static void setState(Severity severity) {
		switch(severity) {
			case HIGH:
				high = !high;
				GlobalSettings.storeInPreferences(Severity.HIGH.name(), String.valueOf(high));
				break;
			case MEDIUM:
				medium = !medium;
				GlobalSettings.storeInPreferences(Severity.MEDIUM.name(), String.valueOf(medium));
				break;
			case LOW:
				low = !low;
				GlobalSettings.storeInPreferences(Severity.LOW.name(), String.valueOf(low));
				break;
			case INFO:
				info = !info;
				GlobalSettings.storeInPreferences(Severity.INFO.name(), String.valueOf(info));
				break;
			case GROUP_BY_SEVERITY:
				groupBySeverity = !groupBySeverity;
				GlobalSettings.storeInPreferences(Severity.GROUP_BY_SEVERITY.name(), String.valueOf(groupBySeverity));
				break;
			case GROUP_BY_QUERY_NAME:
				groupByQueryName = !groupByQueryName;
				GlobalSettings.storeInPreferences(Severity.GROUP_BY_QUERY_NAME.name(), String.valueOf(groupByQueryName));	
				break;	
		default:
			break;
		}
	}
	
	/**
	 * Checks whether a severity is enabled
	 * 
	 * @param severity
	 * @return
	 */
	public static boolean isSeverityEnabled(String severity) {
		switch(Severity.getSeverity(severity)) {
			case HIGH: return high;
			case MEDIUM: return medium;
			case LOW: return low;
			case INFO: return info;
			case GROUP_BY_SEVERITY: return groupBySeverity;
			case GROUP_BY_QUERY_NAME: return groupByQueryName;
		default:
			break;
		}
		
		return false;
	}
	
	/**
	 * Reset filters state
	 */
	public static void resetFilters() {
		high = true;
		medium = true;
		low = false;
		info = false;
		groupBySeverity = true;
		groupByQueryName = true;
	}
}
