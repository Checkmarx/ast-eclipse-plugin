package com.checkmarx.eclipse.views.filters;


import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.enums.State;
import com.checkmarx.eclipse.views.GlobalSettings;

public class FilterState {

	public static boolean high = true;
	public static boolean medium = true;
	public static boolean low = false;
	public static boolean info = false;
	public static boolean groupBySeverity = true;
	public static boolean groupByQueryName = false;
	public static boolean groupByStateName = false;
	
	/*FILTER STATE FLAGS
	 * */
	
	public static boolean notExploitable = true;
	public static boolean confirmed = true;
	public static boolean to_verify = true;
	public static boolean ignored = true;
	public static boolean not_ignored = true;
	public static boolean urgent = true;
	public static boolean proposedNotExploitable = true;
	

	
	
	public static void loadFiltersFromSettings() {
		high = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.HIGH.name(), "true"));
		medium = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.MEDIUM.name(), "true"));
		low = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.LOW.name(), "false"));
		info = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.INFO.name(), "false"));
		groupBySeverity = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.GROUP_BY_SEVERITY.name(), "true"));
		groupByQueryName = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.GROUP_BY_QUERY_NAME.name(), "false"));
		groupByStateName = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.GROUP_BY_STATE_NAME.name(), "false"));
		
		notExploitable = Boolean.parseBoolean(GlobalSettings.getFromPreferences(State.NOT_EXPLOITABLE.name(), "true"));
		confirmed = Boolean.parseBoolean(GlobalSettings.getFromPreferences(State.CONFIRMED.name(), "true"));
		to_verify = Boolean.parseBoolean(GlobalSettings.getFromPreferences(State.TO_VERIFY.name(), "true"));
		urgent = Boolean.parseBoolean(GlobalSettings.getFromPreferences(State.URGENT.name(), "true"));
		ignored = Boolean.parseBoolean(GlobalSettings.getFromPreferences(State.IGNORED.name(), "true"));
		not_ignored = Boolean.parseBoolean(GlobalSettings.getFromPreferences(State.NOT_IGNORED.name(), "true"));
		proposedNotExploitable = Boolean.parseBoolean(GlobalSettings.getFromPreferences(State.PROPOSED_NOT_EXPLOITABLE.name(), "true"));
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
			case GROUP_BY_STATE_NAME:
				groupByStateName = !groupByStateName;
				GlobalSettings.storeInPreferences(Severity.GROUP_BY_STATE_NAME.name(), String.valueOf(groupByStateName));	
				break;	
		default:
			break;
		}
	}
	
	
	public static void setFilterState(State state) {
		switch(state) {
			case NOT_EXPLOITABLE:
				notExploitable = !notExploitable;
				GlobalSettings.storeInPreferences(State.NOT_EXPLOITABLE.name(), String.valueOf(notExploitable));
				break;
			case PROPOSED_NOT_EXPLOITABLE:
				proposedNotExploitable = !proposedNotExploitable;
				GlobalSettings.storeInPreferences(State.PROPOSED_NOT_EXPLOITABLE.name(), String.valueOf(proposedNotExploitable));
				break;
			case URGENT:
				urgent = !urgent;
				GlobalSettings.storeInPreferences(State.URGENT.name(), String.valueOf(urgent));
				break;
			case IGNORED:
				ignored = !ignored;
				GlobalSettings.storeInPreferences(State.IGNORED.name(), String.valueOf(ignored));
				break;
			case CONFIRMED:
				confirmed = !confirmed;
				GlobalSettings.storeInPreferences(State.CONFIRMED.name(), String.valueOf(confirmed));
				break;
			case NOT_IGNORED:
				not_ignored = !not_ignored;
				GlobalSettings.storeInPreferences(State.NOT_IGNORED.name(), String.valueOf(not_ignored));
				break;
			case TO_VERIFY:
				to_verify = !to_verify;
				GlobalSettings.storeInPreferences(State.TO_VERIFY.name(), String.valueOf(to_verify));
				break;	
		default:
			break;
		}
	}
	
	public static boolean isFilterStateEnabled(String state) {
		switch(State.getState(state)) {
			case NOT_EXPLOITABLE: return notExploitable;
			case PROPOSED_NOT_EXPLOITABLE: return proposedNotExploitable;
			case TO_VERIFY: return to_verify;
			case CONFIRMED: return confirmed;
			case URGENT: return urgent;
			case NOT_IGNORED: return not_ignored;
			case IGNORED: return ignored;
		default:
			break;
		}
		
		return false;
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
			case GROUP_BY_STATE_NAME: return groupByStateName;
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
		groupByStateName = true;
	}
	
}
