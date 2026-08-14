package com.checkmarx.eclipse.devassist.utils;

import java.util.List;

/**
 * The DevAssistConstants class defines a collection of constant values
 * related to real-time scanning functionalities, including support for
 * different scanning engines and associated configurations.
 */
public final class DevAssistConstants {

	private DevAssistConstants() {
		throw new UnsupportedOperationException("Cannot instantiate DevAssistConstants class");
	}

	// Tab Name Constants
	public static final String DEVASSIST_TAB = "Checkmarx One Assist Findings";
	public static final String IGNORED_FINDINGS_TAB = "Ignored Findings";
	public static final String DEVASSIST_PLUGIN_FINDINGS_WINDOW_NAME = "Checkmarx Developer Assist Findings";

	// OSS Scanner Constants
	public static final String ACTIVATE_OSS_REALTIME_SCANNER = "Activate OSS-Realtime";
	public static final String OSS_REALTIME_SCANNER = "Checkmarx Open Source Realtime Scanner (OSS-Realtime)";
	public static final String OSS_REALTIME_SCANNER_START = "Realtime OSS Scanner Engine started";
	public static final String OSS_REALTIME_SCANNER_DISABLED = "Realtime OSS Scanner Engine disabled";
	public static final String OSS_REALTIME_SCANNER_DIRECTORY = "Cx-oss-realtime-scanner";
	public static final String ERROR_OSS_REALTIME_SCANNER = "Failed to handle OSS Realtime scan";

	// Container Scanner Constants
	public static final String ACTIVATE_CONTAINER_REALTIME_SCANNER = "Activate Containers-Realtime";
	public static final String CONTAINER_REALTIME_SCANNER = "Checkmarx Containers Realtime Scanner (Containers-Realtime)";
	public static final String CONTAINER_REALTIME_SCANNER_START = "Realtime Containers Scanner Engine started";
	public static final String CONTAINER_REALTIME_SCANNER_DISABLED = "Realtime Containers Scanner Engine disabled";
	public static final String CONTAINER_REALTIME_SCANNER_DIRECTORY = "Cx-containers-realtime-scanner";
	public static final String ERROR_CONTAINER_REALTIME_SCANNER = "Failed to handle Containers Realtime scan";

	// Secrets Scanner Constants
	public static final String ACTIVATE_SECRETS_REALTIME_SCANNER = "Activate Secrets-Realtime";
	public static final String SECRETS_REALTIME_SCANNER = "Checkmarx Secrets Realtime Scanner (Secrets-Realtime)";
	public static final String SECRETS_REALTIME_SCANNER_START = "Realtime Secrets Scanner Engine started";
	public static final String SECRETS_REALTIME_SCANNER_DISABLED = "Realtime Secrets Scanner Engine disabled";
	public static final String SECRETS_REALTIME_SCANNER_DIRECTORY = "Cx-secrets-realtime-scanner";
	public static final String ERROR_SECRETS_REALTIME_SCANNER = "Failed to handle Secrets Realtime scan";

	// IaC Scanner Constants
	public static final String ACTIVATE_IAC_REALTIME_SCANNER = "Activate IAC-Realtime";
	public static final String IAC_REALTIME_SCANNER = "Checkmarx IAC Realtime Scanner (IAC-Realtime)";
	public static final String IAC_REALTIME_SCANNER_START = "Realtime IAC Scanner Engine started";
	public static final String IAC_REALTIME_SCANNER_DISABLED = "Realtime IAC Scanner Engine disabled";
	public static final String IAC_REALTIME_SCANNER_DIRECTORY = "Cx-iac-realtime-scanner";
	public static final String ERROR_IAC_REALTIME_SCANNER = "Failed to handle IAC Realtime scan";
	public static final String IAC_PREREQUISITE = "Please refer IAC RealTime Scanner Prerequisites";
	public static final String IAC_ENGINE_VALIDATION_ERROR = "Checkmarx Containers Management Tool Error";

	// ASCA Scanner Constants
	public static final String ACTIVATE_ASCA_REALTIME_SCANNER = "Activate ASCA-Realtime";
	public static final String ASCA_REALTIME_SCANNER = "Checkmarx AI Secure Coding Assistant (ASCA)";
	public static final String ASCA_REALTIME_SCANNER_START = "AI Secure Coding Assistant Engine started.";
	public static final String ASCA_REALTIME_SCANNER_DISABLED = "AI Secure Coding Assistant Engine disabled.";
	public static final String ERROR_ASCA_REALTIME_SCANNER = "Failed to handle ASCA Realtime scan";

	// ASCA Supported File Extensions
	public static final List<String> ASCA_SUPPORTED_EXTENSIONS = List.of(
			"java", "cs", "go", "py", "js", "jsx", "ts", "tsx", "rb", "cpp"
	);

	// Dev Assist Fixes Constants
	public static final String FIX_WITH_CXONE_ASSIST = "Fix with Checkmarx One Assist";
	public static final String FIX_WITH_DEV_ASSIST = "Fix with Checkmarx Developer Assist";
	public static final String VIEW_DETAILS_FIX_NAME = "View details";
	public static final String IGNORE_THIS_VULNERABILITY_FIX_NAME = "Ignore this vulnerability";
	public static final String IGNORE_ALL_OF_THIS_TYPE_FIX_NAME = "Ignore all of this type";
	public static final String COPY_DETAILS_FIX_NAME = "Copy finding details";

	// Manifest file patterns
	public static final List<String> MANIFEST_FILE_PATTERNS = List.of(
			"**/Directory.Packages.props",
			"**/packages.config",
			"**/pom.xml",
			"**/package.json",
			"**/requirements.txt",
			"**/go.mod",
			"**/*.csproj",
			"**/build.gradle",
			"**/build.gradle.kts",
			"**/yarn.lock",
			"**/*.sbt",
			"**/Gemfile",
			"**/bower.json",
			"**/requirement-*.txt",
			"**/requirements-*.txt",
			"**/Setup.py",
			"**/Setup.cfg",
			"**/pyproject.toml",
			"**/poetry.lock",
			"**/Package.swift",
			"**/Package.resolved",
			"**/composer.json",
			"**/composer.lock",
			"**/*.podspec.json",
			"**/*.podspec",
			"**/Podfile",
			"**/Podfile.lock",
			"**/Cartfile.resolved",
			"**/Gemfile.lock",
			"**/cpanfile.snapshot",
			"**/cpanfile",
			"**/pubspec.lock"
	);

	// Container file patterns
	public static final List<String> CONTAINERS_FILE_PATTERNS = List.of(
			"**/dockerfile",
			"**/dockerfile-*",
			"**/dockerfile.*",
			"**/docker-compose.yml",
			"**/docker-compose.yaml",
			"**/docker-compose-*.yml",
			"**/docker-compose-*.yaml"
	);

	// IaC file patterns and extensions
	public static final List<String> IAC_SUPPORTED_PATTERNS = List.of(
			"**/dockerfile",
			"**/*.auto.tfvars",
			"**/*.terraform.tfvars"
	);

	public static final List<String> IAC_FILE_EXTENSIONS = List.of(
			"tf", "yaml", "yml", "json", "proto", "dockerfile"
	);

	// Multiple issues on same line
	public static final String MULTIPLE_IAC_ISSUES = " IAC issues detected on this line";
	public static final String MULTIPLE_ASCA_ISSUES = " ASCA violations detected on this line";

	// Container file types
	public static final String DOCKERFILE = "dockerfile";
	public static final String DOCKER_COMPOSE = "docker-compose";
	public static final String HELM = "helm";
	public static final List<String> CONTAINER_HELM_EXTENSION = List.of("yml", "yaml");
	public static final List<String> CONTAINER_HELM_EXCLUDED_FILES = List.of("chart.yml", "chart.yaml");

	// Container image risk descriptions
	public static final String MALICIOUS_RISK_CONTAINER = "Malicious-risk container image";
	public static final String CRITICAL_RISK_CONTAINER = "Critical-risk container image";
	public static final String HIGH_RISK_CONTAINER = "High-risk container image";
	public static final String MEDIUM_RISK_CONTAINER = "Medium-risk container image";
	public static final String LOW_RISK_CONTAINER = "Low-risk container image";

	// General constants
	public static final String SEVERITY_PACKAGE = "Severity Package";
	public static final String THEME = "THEME";
	public static final String CX_AGENT_NAME = "Checkmarx One Assist";
	public static final String CX_DEVASSIST_AGENT_NAME = "Checkmarx Developer Assist";
	public static final List<String> AI_AGENT_FILES = List.of("/Dummy.txt", "/", "/AIAssistantInput");
	public static final String SEPERATOR = ":";
	public static final String QUICK_FIX = "QUICK_FIX";
	public static final String UNDO = "Undo";
	
	/******************************** WELCOME DIALOG ********************************/
	public static final String WELCOME_TITLE = "Welcome to Checkmarx";
	public static final String WELCOME_SUBTITLE = "Checkmarx offers immediate threat detection and assists you in preventing vulnerabilities before they arise.";
	public static final String WELCOME_ASSIST_TITLE = "Code Smarter with Checkmarx One Assist";
	public static final String WELCOME_ASSIST_FEATURE_1 = "Get instant security feedback as you code.";
	public static final String WELCOME_ASSIST_FEATURE_2 = "See suggested fixes for vulnerabilities across open source, config, and code.";
	public static final String WELCOME_ASSIST_FEATURE_3 = "Fix faster with intelligent, context-aware remediation inside your IDE.";
	public static final String WELCOME_MAIN_FEATURE_1 = "Run SAST, SCA, IaC, Containers and Secrets scans.";
	public static final String WELCOME_MAIN_FEATURE_2 = "Create a new Checkmarx branch from your local workspace.";
	public static final String WELCOME_MAIN_FEATURE_3 = "Preview or rescan before committing.";
	public static final String WELCOME_MAIN_FEATURE_4 = "Triage & fix issues directly in the editor.";
	public static final String WELCOME_CLOSE_BUTTON = "Close";
	public static final String WELCOME_MCP_INSTALLED_INFO = "Checkmarx MCP Installed automatically - no need for manual integration";

	
	
	/**
     * Constant class to hold image paths.
     */
    public static final class ImagePaths {

        private ImagePaths() {
            throw new UnsupportedOperationException("Cannot instantiate ImagePaths class");
        }

        public static final String DEV_ASSIST_PNG = "/icons/tooltip/cxone_assist.png";
        public static final String CRITICAL_PNG = "/icons/tooltip/critical.png";
        public static final String HIGH_PNG = "/icons/tooltip/high.png";
        public static final String MEDIUM_PNG = "/icons/tooltip/medium.png";
        public static final String LOW_PNG = "/icons/tooltip/low.png";
        public static final String MALICIOUS_PNG = "/icons/tooltip/malicious.png";
        public static final String PACKAGE_PNG = "/icons/tooltip/package.png";
        public static final String CONTAINER_PNG = "/icons/tooltip/container.png";

        // Vulnerability Severity Count Icons
        public static final String CRITICAL_16_PNG = "/icons/tooltip/severity_count/critical.png";
        public static final String HIGH_16_PNG = "/icons/tooltip/severity_count/high.png";
        public static final String MEDIUM_16_PNG = "/icons/tooltip/severity_count/medium.png";
        public static final String LOW_16_PNG = "/icons/tooltip/severity_count/low.png";

        //DEVASSIST PLUGIN ICONS
        public static final String DEVASSIST_BADGE_PNG = "/icons/tooltip/devassist_badge.png";
    }
	
}
