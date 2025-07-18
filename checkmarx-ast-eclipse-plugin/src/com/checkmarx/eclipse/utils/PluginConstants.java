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
	public static final String DEFAULT_COMMENT_TXT = "Notes";
	public static final String LOADING_BFL = "Loading BFL";
	public static final String BFL_FOUND = "Indicates the Best Fix Location. Speed up your remediation by fixing multiple vulnerabilities at once";
	public static final String BFL_NOT_FOUND = "Best fix Location not available for given results";
	public static final String TOOLBAR_ACTION_PREFERENCES = "Preferences";
	public static final String TOOLBAR_ACTION_CLEAR_RESULTS = "Clear results section";

	
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
	public static final String NO_SCAN_ID_PROVIDED = "No scan id provided.";
	
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
	public static final String GETTING_LEARN_MORE_JOB = "Checkmarx: Getting Learn More information...";
	public static final String NO_REMEDIATION_EXAMPLES = "No remediation examples available.";
	public static final String REMEDIATION_EXAMPLE_TITLE_FORMAT = "%s using %s";
	
	/********************************** Run scan ************************************/
	public static final String CX_CREATING_SCAN = "Checkmarx: Creating scan...";
	public static final String CX_CANCELING_SCAN = "Checkmarx: Canceling scan...";
	public static final String CX_SCAN_TITLE = "Checkmarx scan";
	public static final String CX_SCAN_CANCELED_TITLE = "Scan canceled";
	public static final String CX_SCAN_CANCELED_DESCRIPTION = "Checkmarx scan canceled successfully.";
	public static final String CX_START_SCAN = "Start a scan";
	public static final String CX_CANCEL_RUNNING_SCAN = "Cancel running scan";
	public static final String CX_RUNNING_SCAN = "Checkmarx: Scan running with id %s";
	public static final String NO_FILES_IN_WORKSPACE = "No files in workspace to scan.";
	public static final String CX_SCAN_COMPLETED_STATUS = "completed";
	public static final String CX_SCAN_RUNNING_STATUS = "running";
	public static final String CX_SCAN_FINISHED_TITLE = "Scan finished";
	public static final String CX_SCAN_FINISHED_DESCRIPTION = "Checkmarx scan completed successfully.";
	public static final String CX_SCAN_FINISHED_WITH_STATUS = "Checkmarx scan finished with status %s";
	public static final String CX_LOAD_SCAN_RESULTS = "Load scan results";
	public static final String CX_ERROR_CREATING_SCAN = "An error occurred while creating a scan: %s";
	public static final String CX_ERROR_CANCELING_SCAN = "An error occurred while canceling a scan: %s";
	public static final String CX_ERROR_GETTING_SCAN_INFO = "An error occurred while getting scan information: %s";
	public static final String CX_ERROR_CHECKING_IDE_SCAN_ENABLED = "An error occurred while checking if scanning from IDE is allowed for current tenant: %s";
	public static final String CX_PROJECT_AND_BRANCH_MISMATCH = "Project and branch mismatch";	
	public static final String CX_PROJECT_AND_BRANCH_MISMATCH_QUESTION = "The Git branch and files open in your workspace don't match the branch and project that were previously scanned in this Checkmarx project. Do you want to scan anyway?";	
	public static final String CX_BRANCH_MISMATCH = "Branch mismatch";	
	public static final String CX_BRANCH_MISMATCH_QUESTION = "The Git branch open in your workspace isn't the same as the branch that was previously scanned in this Checkmarx project. Do you want to scan anyway?";	
	public static final String CX_PROJECT_MISMATCH = "Project mismatch";	
	public static final String CX_PROJECT_MISMATCH_QUESTION = "The files open in your workspace don't match the files previously scanned in this Checkmarx project. Do you want to scan anyway?";	
	public static final String CX_REFRESHING_TOOLBAR = "Checkmarx: Refreshing toolbar...";	
}