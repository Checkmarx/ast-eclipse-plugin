package com.checkmarx.eclipse.views.filters;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.enums.State;
import com.checkmarx.eclipse.views.GlobalSettings;

public class FilterState {

	public static boolean critical = true;
	public static boolean high = true;
	public static boolean medium = true;
	public static boolean low = false;
	public static boolean info = false;
	public static boolean groupBySeverity = true;
	public static boolean groupByQueryName = false;
	public static boolean groupByStateName = false;

	// Predefined states
	public static final List<String> PREDEFINED_STATES = Arrays.asList("NOT_EXPLOITABLE", "PROPOSED_NOT_EXPLOITABLE",
			"TO_VERIFY", "CONFIRMED", "URGENT", "NOT_IGNORED", "IGNORED");
	public static final Set<String> PREDEFINED_STATE_SET = new HashSet<>(PREDEFINED_STATES);

	// FILTER STATE FLAGS
	public static boolean notExploitable = true;
	public static boolean confirmed = true;
	public static boolean to_verify = true;
	public static boolean ignored = true;
	public static boolean not_ignored = true;
	public static boolean urgent = true;
	public static boolean proposedNotExploitable = true;
	public static boolean customState = true; // default enabled

	public static void loadFiltersFromSettings() {
		critical = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.CRITICAL.name(), "true"));
		high = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.HIGH.name(), "true"));
		medium = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.MEDIUM.name(), "true"));
		low = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.LOW.name(), "false"));
		info = Boolean.parseBoolean(GlobalSettings.getFromPreferences(Severity.INFO.name(), "false"));
		groupBySeverity = Boolean
				.parseBoolean(GlobalSettings.getFromPreferences(Severity.GROUP_BY_SEVERITY.name(), "true"));
		groupByQueryName = Boolean
				.parseBoolean(GlobalSettings.getFromPreferences(Severity.GROUP_BY_QUERY_NAME.name(), "false"));
		groupByStateName = Boolean
				.parseBoolean(GlobalSettings.getFromPreferences(Severity.GROUP_BY_STATE_NAME.name(), "false"));

		notExploitable = Boolean.parseBoolean(GlobalSettings.getFromPreferences("NOT_EXPLOITABLE", "false"));
		confirmed = Boolean.parseBoolean(GlobalSettings.getFromPreferences("CONFIRMED", "true"));
		to_verify = Boolean.parseBoolean(GlobalSettings.getFromPreferences("TO_VERIFY", "true"));
		urgent = Boolean.parseBoolean(GlobalSettings.getFromPreferences("URGENT", "true"));
		ignored = Boolean.parseBoolean(GlobalSettings.getFromPreferences("IGNORED", "true"));
		not_ignored = Boolean.parseBoolean(GlobalSettings.getFromPreferences("NOT_IGNORED", "true"));
		proposedNotExploitable = Boolean
				.parseBoolean(GlobalSettings.getFromPreferences("PROPOSED_NOT_EXPLOITABLE", "false"));
		customState = Boolean.parseBoolean(GlobalSettings.getFromPreferences("CUSTOM_STATE", "true"));
	}

	/**
	 * Change severity state
	 */
	public static void setState(Severity severity) {
		switch (severity) {
		case CRITICAL:
			critical = !critical;
			GlobalSettings.storeInPreferences(Severity.CRITICAL.name(), String.valueOf(critical));
			break;
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
		switch (state.getName()) {
		case "NOT_EXPLOITABLE":
			notExploitable = !notExploitable;
			GlobalSettings.storeInPreferences("NOT_EXPLOITABLE", String.valueOf(notExploitable));
			break;
		case "PROPOSED_NOT_EXPLOITABLE":
			proposedNotExploitable = !proposedNotExploitable;
			GlobalSettings.storeInPreferences("PROPOSED_NOT_EXPLOITABLE", String.valueOf(proposedNotExploitable));
			break;
		case "URGENT":
			urgent = !urgent;
			GlobalSettings.storeInPreferences("URGENT", String.valueOf(urgent));
			break;
		case "IGNORED":
			ignored = !ignored;
			GlobalSettings.storeInPreferences("IGNORED", String.valueOf(ignored));
			break;
		case "CONFIRMED":
			confirmed = !confirmed;
			GlobalSettings.storeInPreferences("CONFIRMED", String.valueOf(confirmed));
			break;
		case "NOT_IGNORED":
			not_ignored = !not_ignored;
			GlobalSettings.storeInPreferences("NOT_IGNORED", String.valueOf(not_ignored));
			break;
		case "TO_VERIFY":
			to_verify = !to_verify;
			GlobalSettings.storeInPreferences("TO_VERIFY", String.valueOf(to_verify));
			break;
		default:
			// For custom states, toggle the global customState flag
			setCustomStateFilter();
			break;
		}
	}

	/**
	 * Enable/disable the custom state filter.
	 */
	public static void setCustomStateFilter() {
		customState = !customState;
		GlobalSettings.storeInPreferences("CUSTOM_STATE", String.valueOf(customState));
	}

	/**
	 * Returns true if the state is enabled for filtering. If the state is not a
	 * predefined state and customState is enabled, returns true.
	 */
	public static boolean isFilterStateEnabled(String state) {
		if (state == null) {
			return false;
		}
		String normalized = state.trim().toUpperCase();
		if (PREDEFINED_STATE_SET.contains(normalized)) {
			switch (normalized) {
			case "NOT_EXPLOITABLE":
				return notExploitable;
			case "PROPOSED_NOT_EXPLOITABLE":
				return proposedNotExploitable;
			case "TO_VERIFY":
				return to_verify;
			case "CONFIRMED":
				return confirmed;
			case "URGENT":
				return urgent;
			case "NOT_IGNORED":
				return not_ignored;
			case "IGNORED":
				return ignored;
			default:
				break;
			}
		} else {
			// Not a predefined state, treat as custom
			return customState;
		}
		return false;
	}

	/**
	 * Checks whether a severity is enabled
	 */
	public static boolean isSeverityEnabled(String severity) {
		switch (Severity.getSeverity(severity)) {
		case CRITICAL:
			return critical;
		case HIGH:
			return high;
		case MEDIUM:
			return medium;
		case LOW:
			return low;
		case INFO:
			return info;
		case GROUP_BY_SEVERITY:
			return groupBySeverity;
		case GROUP_BY_QUERY_NAME:
			return groupByQueryName;
		case GROUP_BY_STATE_NAME:
			return groupByStateName;
		default:
			break;
		}
		return false;
	}

	/**
	 * Reset filters state
	 */
	public static void resetFilters() {
		critical = true;
		high = true;
		medium = true;
		low = false;
		info = false;
		groupBySeverity = true;
		groupByQueryName = true;
		groupByStateName = true;

		notExploitable = true;
		confirmed = true;
		to_verify = true;
		ignored = true;
		not_ignored = true;
		urgent = true;
		proposedNotExploitable = true;
		customState = true;
	}

	/**
	 * Returns the list of filter state names for the filter panel. Shows all
	 * predefined states and any custom states found in the results.
	 */
	public static List<String> getFilterStateListForPanel(List<String> allStatesInResults) {
		// Get all states from the global registry
		Map<String, State> allStates = State.values();

		List<String> panelList = new java.util.ArrayList<>(allStates.keySet());

		// Optional: Sort alphabetically or by custom rule
		panelList.sort(String::compareToIgnoreCase);

		return panelList;
	}

}
