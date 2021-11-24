package com.checkmarx.eclipse.views.filters;


public class FilterState {

	public static boolean high = true;
	public static boolean medium = true;
	public static boolean low = false;
	public static boolean info = false;
	public static boolean groupBySeverity = true;
	public static boolean groupByQueryName = false;
	
	/**
	 * Change severity state
	 * 
	 * @param severity
	 */
	public static void setState(Severity severity) {
		
		switch(severity) {
			case HIGH:
				high = !high;
				break;
			case MEDIUM:
				medium = !medium;
				break;
			case LOW:
				low = !low;
				break;
			case INFO:
				info = !info;
				break;
			case GROUP_BY_SEVERITY:
				groupBySeverity = !groupBySeverity;
				break;
			case GROUP_BY_QUERY_NAME:
				groupByQueryName = !groupByQueryName;
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
		groupByQueryName = false;
	}
}
