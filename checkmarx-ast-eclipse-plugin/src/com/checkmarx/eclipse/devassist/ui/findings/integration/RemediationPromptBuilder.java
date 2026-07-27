package com.checkmarx.eclipse.devassist.ui.findings.integration;

import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.ScanEngine;

/**
 * Builds engine-specific remediation prompts for Copilot.
 *
 * Generates context-aware prompts based on vulnerability type:
 * - ASCA (SAST): Code analysis with line numbers and remediation advice
 * - OSS (Dependencies): Package information and upgrade paths
 * - SECRETS: Credential type and revocation steps
 * - CONTAINERS: Image information and vulnerability sources
 * - IAC: Configuration details and expected vs actual values
 *
 * Each prompt type is optimized for the scan engine context.
 */
public class RemediationPromptBuilder {

    /**
     * Build a remediation prompt for the given scan issue
     *
     * @param issue The scan issue to build prompt for
     * @return The remediation prompt, or empty string if unable to build
     */
    public static String buildRemediationPrompt(ScanIssue issue) {
        if (issue == null) {
            return "";
        }

        ScanEngine engine = issue.getScanEngine();

        switch (engine) {
            case ASCA:
                return buildASCAPrompt(issue);
            case OSS:
                return buildOSSPrompt(issue);
            case SECRETS:
                return buildSecretPrompt(issue);
            case CONTAINERS:
                return buildContainerPrompt(issue);
            case IAC:
                return buildIACPrompt(issue);
            default:
                return buildGenericPrompt(issue);
        }
    }

    /**
     * Build prompt for ASCA (SAST) vulnerabilities
     * Context: Code analysis with line numbers and severity
     */
    private static String buildASCAPrompt(ScanIssue issue) {
        return String.format(
            "You are a code security expert. Please fix the following code vulnerability:\n\n" +
            "**Issue:** %s\n" +
            "**Severity:** %s\n" +
            "**Description:** %s\n" +
            "**Line Number:** %d\n" +
            "**File:** %s\n" +
            "%s" + // Remediation advice if available
            "\n**Requirements for the fix:**\n" +
            "1. Must address the security vulnerability\n" +
            "2. Should maintain code readability\n" +
            "3. Must preserve existing functionality\n" +
            "4. Include necessary imports/dependencies\n" +
            "5. Follow Java best practices\n\n" +
            "Provide the corrected code snippet.",

            issue.getTitle(),
            formatSeverity(issue.getSeverity()),
            issue.getDescription(),
            issue.getProblematicLineNumber(),
            issue.getFilePath(),
            buildRemediationAdviceSection(issue)
        );
    }

    /**
     * Build prompt for OSS (Open Source Software) vulnerabilities
     * Context: Dependency vulnerabilities with version information
     */
    private static String buildOSSPrompt(ScanIssue issue) {
        return String.format(
            "A vulnerability was detected in an open source dependency:\n\n" +
            "**Package:** %s\n" +
            "**Severity:** %s\n" +
            "**Description:** %s\n" +
            "%s" + // Version info if available
            "\n**What needs to be done:**\n" +
            "1. Identify recommended safe version to upgrade to\n" +
            "2. Provide upgrade command (Maven/Gradle/npm/pip as applicable)\n" +
            "3. List any configuration changes needed\n" +
            "4. Note any compatibility concerns\n" +
            "5. Suggest testing approach\n\n" +
            "Please provide step-by-step remediation instructions.",

            issue.getTitle(),
            formatSeverity(issue.getSeverity()),
            issue.getDescription(),
            buildVersionInfo(issue)
        );
    }

    /**
     * Build prompt for SECRETS (credential leaks)
     * Context: Exposed credentials that need immediate action
     */
    private static String buildSecretPrompt(ScanIssue issue) {
        return String.format(
            "**SECURITY ALERT:** A secret/credential has been detected in the code:\n\n" +
            "**Secret Type:** %s\n" +
            "**Severity:** %s\n" +
            "**Description:** %s\n" +
            "**File:** %s\n\n" +
            "**Immediate Actions Required:**\n" +
            "1. If this is a real credential, immediately revoke/rotate it in the management console\n" +
            "2. Generate new credentials if necessary\n" +
            "3. Update application configuration to use new credentials\n" +
            "4. Remove the hardcoded credential from source code\n\n" +
            "**Best Practices to Prevent:**\n" +
            "- Use environment variables or secrets manager\n" +
            "- Never commit secrets to version control\n" +
            "- Use tools like GitGuardian or TruffleHog to scan commits\n" +
            "- Implement pre-commit hooks to prevent secret commits\n\n" +
            "Provide detailed steps for proper secret management.",

            issue.getTitle(),
            formatSeverity(issue.getSeverity()),
            issue.getDescription(),
            issue.getFilePath()
        );
    }

    /**
     * Build prompt for CONTAINERS (container image vulnerabilities)
     * Context: Docker/container image vulnerabilities
     */
    private static String buildContainerPrompt(ScanIssue issue) {
        return String.format(
            "A vulnerability was detected in a container image:\n\n" +
            "**Image/Library:** %s\n" +
            "**Severity:** %s\n" +
            "**Description:** %s\n" +
            "**File:** %s\n" +
            "%s" + // Version info if available
            "\n**Remediation Steps:**\n" +
            "1. Use specific version tags instead of 'latest'\n" +
            "2. Update base image to patched version\n" +
            "3. Use minimal base images (alpine, distroless)\n" +
            "4. Implement scanning in CI/CD pipeline\n" +
            "5. Regular image updates and patching\n\n" +
            "Provide updated Dockerfile snippet and best practices.",

            issue.getTitle(),
            formatSeverity(issue.getSeverity()),
            issue.getDescription(),
            issue.getFilePath(),
            buildVersionInfo(issue)
        );
    }

    /**
     * Build prompt for IAC (Infrastructure as Code) issues
     * Context: Configuration and infrastructure vulnerabilities
     */
    private static String buildIACPrompt(ScanIssue issue) {
        return String.format(
            "An infrastructure configuration vulnerability was detected:\n\n" +
            "**Issue:** %s\n" +
            "**Severity:** %s\n" +
            "**Description:** %s\n" +
            "**File:** %s\n" +
            "**Type:** Infrastructure as Code (Terraform/CloudFormation/YAML)\n" +
            "%s" + // Configuration details if available
            "\n**Security Requirements:**\n" +
            "1. Enable encryption where applicable\n" +
            "2. Enforce authentication and authorization\n" +
            "3. Use least privilege principles\n" +
            "4. Enable logging and monitoring\n" +
            "5. Follow cloud provider security best practices\n\n" +
            "Provide corrected configuration and explanation of security improvements.",

            issue.getTitle(),
            formatSeverity(issue.getSeverity()),
            issue.getDescription(),
            issue.getFilePath(),
            buildConfigInfo(issue)
        );
    }

    /**
     * Generic prompt for unknown engine types
     */
    private static String buildGenericPrompt(ScanIssue issue) {
        return String.format(
            "A security issue was detected in the code:\n\n" +
            "**Issue:** %s\n" +
            "**Severity:** %s\n" +
            "**Description:** %s\n" +
            "**File:** %s\n\n" +
            "Please provide:\n" +
            "1. Root cause analysis\n" +
            "2. Security implications\n" +
            "3. Step-by-step fix\n" +
            "4. Prevention strategies\n\n" +
            "Ensure the fix follows security best practices.",

            issue.getTitle(),
            formatSeverity(issue.getSeverity()),
            issue.getDescription(),
            issue.getFilePath()
        );
    }

    /**
     * Helper: Format severity level
     */
    private static String formatSeverity(String severity) {
        if (severity == null) return "UNKNOWN";
        switch (severity.toUpperCase()) {
            case "CRITICAL":
                return "ðŸ”´ CRITICAL - Requires immediate attention";
            case "HIGH":
                return "ðŸŸ  HIGH - Should be fixed soon";
            case "MEDIUM":
                return "ðŸŸ¡ MEDIUM - Should be addressed";
            case "LOW":
                return "ðŸŸ¢ LOW - May be fixed in next iteration";
            default:
                return severity;
        }
    }

    /**
     * Helper: Build remediation advice section if available
     */
    private static String buildRemediationAdviceSection(ScanIssue issue) {
        String advice = issue.getRemediationAdvise();
        if (advice != null && !advice.isEmpty()) {
            return "\n**Remediation Advice:** " + advice;
        }
        return "";
    }

    /**
     * Helper: Build version information section
     */
    private static String buildVersionInfo(ScanIssue issue) {
        StringBuilder sb = new StringBuilder();

        String currentVersion = issue.getPackageVersion();
        if (currentVersion != null && !currentVersion.isEmpty()) {
            sb.append("\n**Current Version:** ").append(currentVersion);
        }

        // Note: Recommended version may be available from API in future versions
        // For now, we rely on Copilot to suggest the safe version

        return sb.toString();
    }

    /**
     * Helper: Build configuration details section
     */
    private static String buildConfigInfo(ScanIssue issue) {
        String description = issue.getDescription();
        if (description != null && description.length() > 100) {
            return "\n**Details:** " + description;
        }
        return "";
    }
}

