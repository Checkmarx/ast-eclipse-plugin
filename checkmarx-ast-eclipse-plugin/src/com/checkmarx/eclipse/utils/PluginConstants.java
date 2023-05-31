package com.checkmarx.eclipse.utils;

public class PluginConstants {
	public static final String EMPTY_STRING = "";
	public static final String SAST = "sast";
	public static final String SCA_DEPENDENCY = "sca";
	public static final String KICS_INFRASTRUCTURE = "kics";
	public static final String RETRIEVING_RESULTS_FOR_SCAN = "Retrieving results for scan id %s...";
	public static final String COMBOBOX_SCAND_ID_PLACEHOLDER = "Select a project or paste a Scan Id here and hit Enter.";
	public static final String COMBOBOX_SCAND_ID_GETTING_SCANS = "Getting scans for the project...";
	public static final String COMBOBOX_SCAND_ID_NO_SCANS_AVAILABLE = "No scans available. Select a project or paste a Scan Id here and hit Enter.";
	public static final String COMBOBOX_BRANCH_CHANGING = "Changing branch...";
	public static final String BTN_OPEN_SETTINGS = "Open Settings";
	public static final String LOADING_CHANGES = "Loading changes...";
	public static final String NO_CHANGES = "No changes...";
	public static final String BTN_UPDATE = "Update";
	public static final String BTN_LOADING = "Loading";
	public static final String DEFAULT_COMMENT_TXT = "Enter comment";
	public static final String LOADING_BFL = "Loading BFL";
	public static final String BFL_FOUND = "Indicates the Best Fix Location. Speed up your remediation by fixing multiple vulnerabilities at once";
	public static final String BFL_NOT_FOUND = "Best fix Location not available for given results";
	
	/******************************** LOG VIEW: ERRORS ********************************/
	public static final String ERROR_AUTHENTICATING_AST = "An error occurred while trying to authenticate to Checkmarx One: %s";
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
	public static final String ERROR_GETTING_CODEBASHING_DETAILS = "An error occurred while getting codebashing details: %s";
	public static final String ERROR_GETTING_BEST_FIX_LOCATION = "An error occurred while getting the best fix location: %s";
	
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
	public static final String PREFERENCES_API_KEY = "API key:";
	public static final String PREFERENCES_ADDITIONAL_OPTIONS = "Additional Params:";
	public static final String PREFERENCES_TEST_CONNECTION = "Test Connection";
	public static final String PREFERENCES_VALIDATING_STATE = "Validating...";
	
	/******************************** TOPICS ********************************/
	public static final String TOPIC_APPLY_SETTINGS = "ApplySettings";
	
	/******************************** PROBLEMS VIEW ********************************/
	public static final String PROBLEM_SOURCE_ID = "CheckmarxEclipsePlugin";
	
	/******************************** WIDGET IDS ********************************/
	public static final String DATA_ID_KEY = "org.eclipse.swtbot.widget.key";
	public static final String TRIAGE_SEVERITY_COMBO_ID = "cx.triageSeverityCombo";
	public static final String TRIAGE_STATE_COMBO_ID = "cx.triageStateCombo";
	public static final String TRIAGE_BUTTON_ID = "cx.triageButton";
	public static final String CHANGES_TAB_ID = "cx.changesTab";
	public static final String CODEBASHING_LINK_ID = "cx.codebashingLink";
	public static final String BEST_FIX_LOCATION = "cx.bestFixLocationLabel";
	
	/******************************** Plugin metric settings ********************************/
	public static final int TITLE_LABEL_WIDTH = 23;
	public static final int TITLE_LABEL_HEIGHT = 20;
	public static final int TITLE_TEXT_COMPOSITE_MAX_WIDTH = 553;
	public static final int BFL_TEXT_MAX_WIDTH = 564;
	public static final int BFL_LABEL_HEIGHT = 38;
	
	/********************************** Exit codes ************************************/ //TODO: Move to wrapper
	public static final int EXIT_CODE_LICENSE_NOT_FOUND = 3;
	public static final int EXIT_CODE_LESSON_NOT_FOUND = 4;
	
	/********************************** Codebashing ************************************/
	public static final String CODEBASHING = "Codebashing";
	public static final String CODEBASHING_NO_LESSON = "Currently, this vulnerability has no lesson.";
	public static final String CODEBASHING_NO_LICENSE = "You don't have a license for Codebashing. Please Contact your Admin for the full version implementation. Meanwhile, you can use <a href=\"https://free.codebashing.com\">Codebashing</a>.";

	/********************************** Attack Vector ************************************/
	public static final String ATTACK_VECTOR = "Attack Vector";
	public static final String LEARN_MORE = "Learn More";
	public static final String REMEDIATION_EXAMPLES = "Remediation Examples";
	public static final String LOCATION = "Location";
	public static final String PACKAGE_DATA = "Package Data";
	public static final String LEARN_MORE_RISK = "Risk";
	public static final String LEARN_MORE_CAUSE = "Cause";
	public static final String LEARN_MORE_GENERAL_RECOMMENDATIONS = "General Recommendations";
	public static final String LEARN_MORE_LOADING = "Loading...";
	public static final String ERROR_GETTING_LEARN_MORE = "An error occurred while getting learn more information: %s";
	public static final String GETTING_LEARN_MORE_JOB = "Getting Learn More information";
}
