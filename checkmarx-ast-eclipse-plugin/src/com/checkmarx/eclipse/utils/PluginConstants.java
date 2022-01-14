package com.checkmarx.eclipse.utils;

public class PluginConstants {
	public static final String EMPTY_STRING = "";
	public static final String SAST = "sast";
	public static final String SCA_DEPENDENCY = "dependency";
	public static final String KICS_INFRASTRUCTURE = "infrastructure";
	public static final String RETRIEVING_RESULTS_FOR_SCAN = "Retrieving results for scan id %s...";
	public static final String COMBOBOX_SCAND_ID_PLACEHOLDER = "Select a project or paste a Scan Id here and hit Enter.";
	public static final String COMBOBOX_SCAND_ID_GETTING_SCANS = "Getting scans for the project...";
	public static final String COMBOBOX_SCAND_ID_NO_SCANS_AVAILABLE = "No scans available. Select a project or paste a Scan Id here and hit Enter.";
	public static final String COMBOBOX_BRANCH_CHANGING = "Changing branch...";
	public static final String BTN_OPEN_SETTINGS = "Open Settings";
	public static final String LOADING_CHANGES = "Loading changes...";
	
	/******************************** LOG VIEW: ERRORS ********************************/
	public static final String ERROR_AUTHENTICATING_AST = "An error occurred while trying to authenticate to AST: %s";
	public static final String ERROR_GETTING_SCAN_INFO = "An error occurred while getting scan information: %s";
	public static final String ERROR_GETTING_PROJECTS = "An error occurred while getting projects: %s";
	public static final String ERROR_GETTING_BRANCHES = "An error occurred while getting branches for project id %s: %s";
	public static final String ERROR_GETTING_SCANS = "An error occurred while getting scans for project id %s in branch %s: %s";
	public static final String ERROR_GETTING_RESULTS = "An error occurred while getting results for scan id %s: %s";
	public static final String ERROR_OPENING_FILE = "An error occurred while opening file: %s";
	public static final String ERROR_FINDING_FILE = "An error occurred while finding file in workspace: %s";
	public static final String ERROR_GETTING_GIT_BRANCH = "An error occurred while getting git branch: %s";
	public static final String ERROR_BUILDING_CX_WRAPPER = "An error occurred while instantiating a CxWrapper: %s";
	public static final String ERROR_FINDING_OR_DELETING_MARKER = "An error occurred while finding or deleting a marker from Problems View: %s";
	public static final String ERROR_UPDATING_TRIAGE = "An error occurred while updating triage similarity id: %s";
	public static final String ERROR_GETTING_TRIAGE_DETAILS = "An error occurred while getting triage details: %s";
	
	/******************************** LOG VIEW: INFO ********************************/
	public static final String INFO_AUTHENTICATION_STATUS = "Authentication Status: %s";
	public static final String INFO_FETCHING_RESULTS = "Fetching results for scan id %s...";
	public static final String INFO_SCAN_RESULTS_COUNT = "Scan results: %d";
	public static final String INFO_GETTING_SCAN_INFO = "Getting scan info for scan id %s...";
	public static final String INFO_RESULTS_ALREADY_RETRIEVED = "Reverse selection not triggered. Results for scan id %s already retrieved.";
	public static final String INFO_CHANGE_SCAN_EVENT_NOT_TRIGGERED = "Change scan id event not triggered. Request already running: %s. Scan id results already retrieved: %s";
	public static final String INFO_CHANGE_BRANCH_EVENT_NOT_TRIGGERED = "Change branch event not triggered. Branch already selected";
	public static final String INFO_CHANGE_PROJECT_EVENT_NOT_TRIGGERED = "Change project event not triggered. Project already selected";
	
	/******************************** TREE MESSAGES ********************************/
	public static final String TREE_INVALID_SCAN_ID_FORMAT = "Invalid scan id format.";
	public static final String TREE_NO_RESULTS = "No results.";
	
	/******************************** PREFERENCES ********************************/
	public static final String PREFERENCES_SERVER_URL = "Server Url:";
	public static final String PREFERENCES_AUTH_URL = "Authentication Url:";
	public static final String PREFERENCES_TENANT = "Tenant:";
	public static final String PREFERENCES_API_KEY = "API key:";
	public static final String PREFERENCES_ADDITIONAL_OPTIONS = "Additional Options:";
	public static final String PREFERENCES_TEST_CONNECTION = "Test Connection";
	public static final String PREFERENCES_VALIDATING_STATE = "Validating...";
	
	/******************************** TOPICS ********************************/
	public static final String TOPIC_APPLY_SETTINGS = "ApplySettings";
	
	/******************************** PROBLEMS VIEW ********************************/
	public static final String PROBLEM_SOURCE_ID = "CheckmarxEclipsePlugin";

}
