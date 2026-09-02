package com.checkmarx.eclipse.devassist.remediation;

import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.BOOKS;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.BRAIN;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.CHECK;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.CLIPBOARD;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.CONSTRUCTION;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.CROSS;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.EXCLAMATION;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.FIRECRACKER;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.LOCK;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.OPEN_BOOK;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.PENCIL;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.POINT_RIGHT;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.POLICE_LIGHT;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.REPEAT;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.SEARCH;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.SHIELD;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.TOOLS;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.WARNING;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.WHALE;

import java.util.List;

import com.checkmarx.eclipse.devassist.backend.SeverityLevel;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;

/**
 * Prompt builder for generating prompts for viewing vulnerability details.
 */
public final class ViewDetailsPrompts {

    private ViewDetailsPrompts() {
        throw new IllegalStateException("Cannot instantiate ViewDetailsPrompts class");
    }

    private static final String AGENT_NAME =  DevAssistUtils.getAgentName();

    /**
     * Generates a detailed prompt for explaining the security issue of an SCA/OSS.
     *
     * @param packageName     The name of the software package.
     * @param version         The version of the software package.
     * @param status          The security status of the package (e.g., "malicious", "vulnerable").
     * @param vulnerabilities A list of vulnerabilities affecting the package.
     * @return A formatted prompt string for explaining the security issue.
     */
    public static String buildSCAExplanationPrompt(String packageName, String version,
                                                   String status, List<Vulnerability> vulnerabilities) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the `").append(AGENT_NAME).append("`.\n\n")
                .append("Your task is to **analyze and explain** the security issue affecting the package `")
                .append(packageName).append("@").append(version).append("` with status: `").append(status).append("`.\n\n");

        prompt.append("### " + EXCLAMATION + " Important Instructions:\n")
                .append("- " + POINT_RIGHT + " **Do not change anything in the code. Just explain the risks and remediation steps.**\n")
                .append("- " + CROSS + " **Never include references from Checkmarx competitors.**\n\n");

        prompt.append("### " + SEARCH + " Package Overview\n\n")
                .append("- **Package:** `").append(packageName).append("`\n")
                .append("- **Version:** `").append(version).append("`\n")
                .append("- **Status:** `").append(status).append("`\n");

        if (status.equalsIgnoreCase(SeverityLevel.MALICIOUS.getSeverity())) {
            buildMaliciousContentForSCAPrompt(version, prompt);
        } else {
            buildVulnerabilityContentForSCAPrompt(status, vulnerabilities, prompt);
        }
        prompt.append("### " + TOOLS + " Remediation Guidance\n\n")
                .append("Offer actionable advice:\n")
                .append("- Whether to remove, upgrade, or replace the package\n")
                .append("- If malicious: clearly emphasize permanent removal\n")
                .append("- Recommend safer, verified alternatives if available\n")
                .append("- Suggest preventative measures:\n")
                .append("  - Use SCA in CI/CD\n")
                .append("  - Prefer signed packages\n")
                .append("  - Pin versions to prevent shadow updates\n\n");

        prompt.append("### " + CHECK + " Summary Section\n\n")
                .append("Conclude with:\n")
                .append("- Overall risk explanation\n")
                .append("- Immediate remediation steps\n")
                .append("- Whether this specific version is linked to online reports\n")
                .append("- If not, reference Checkmarx attribution (per above rules)\n")
                .append("- Never mention competitor vendors or tools\n\n");

        prompt.append("### " + PENCIL + " Output Formatting\n\n")
                .append("- Use Markdown: `##`, `- `, `**bold**`, `code`\n")
                .append("- Developer-friendly tone, informative, concise\n")
                .append("- No speculation - use only trusted, verified sources\n");

        return prompt.toString();
    }

    /**
     * Builds a prompt for explaining malicious packages.
     *
     * @param version the version of the package
     * @param prompt  the prompt builder
     */
    private static void buildMaliciousContentForSCAPrompt(String version, StringBuilder prompt) {
        prompt.append("### " + FIRECRACKER + " Malicious Package Detected\n\n")
                .append("This package has been flagged as **malicious**.\n\n")
                .append("** " + WARNING + " Never install or use this package under any circumstances.**\n\n")
                .append("#### " + SEARCH + " Web Investigation:\n\n")
                .append("- Search the web for trusted community or vendor reports about malicious activity involving this package.\n")
                .append("- If information exists about other versions but **not** version `").append(version).append("`, explicitly say:\n\n")
                .append("> _“This specific version (`").append(version).append("`) was identified as malicious by Checkmarx Security researchers.”_\n\n")
                .append("- If **no credible external information is found at all**, state:\n\n")
                .append("> _“This package was identified as malicious by Checkmarx Security researchers based on internal threat intelligence and behavioral analysis.”_\n\n")
                .append("Then explain:\n")
                .append("- What types of malicious behavior these packages typically include (e.g., data exfiltration, postinstall backdoors)\n")
                .append("- Indicators of compromise developers should look for (e.g., suspicious scripts, obfuscation, DNS calls)\n\n")
                .append("**Recommended Actions:**\n")
                .append("- " + CHECK + " Immediately remove from all codebases and pipelines\n")
                .append("- " + CROSS + " Never reinstall or trust any version of this package\n")
                .append("- " + REPEAT + " Replace with a well-known, secure alternative\n")
                .append("- " + LOCK + " Consider running a retrospective security scan if this was installed\n\n");
    }

    /**
     * Builds a prompt for explaining known vulnerabilities.
     *
     * @param status          the severity status of the package
     * @param vulnerabilities the list of vulnerabilities affecting the package
     * @param prompt          the prompt builder
     */
    private static void buildVulnerabilityContentForSCAPrompt(String status, List<Vulnerability> vulnerabilities, StringBuilder prompt) {
        prompt.append("### " + POLICE_LIGHT + " Known Vulnerabilities\n\n")
                .append("Explain each known CVE affecting this package:\n");

        if (vulnerabilities != null && !vulnerabilities.isEmpty()) {
            for (int i = 0; i < vulnerabilities.size(); i++) {
                Vulnerability vuln = vulnerabilities.get(i);
                prompt.append("\n#### ").append(i + 1).append(". ").append(vuln.getCve()).append("\n")
                        .append("- **Severity:** ").append(vuln.getSeverity()).append("\n")
                        .append("- **Description:** ").append(vuln.getDescription()).append("\n");
            }
            prompt.append("\n");
        } else {
            prompt.append("\n " + WARNING + " No CVEs were provided. Please verify if this is expected for status `").append(status).append("`.\n\n");
        }
    }

    /**
     * Generates a detailed prompt for explaining a detected secret.
     *
     * @param title       the title of the secret
     * @param description the description of the secret
     * @param severity    the severity level of the secret vulnerability
     * @return the formatted Markdown prompt
     */
    public static String buildSecretsExplanationPrompt(String title, String description, String severity) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the `").append(AGENT_NAME).append("`.\n\n")
                .append("A potential secret has been detected: **\"").append(title).append("\"**  \n")
                .append("Severity: **").append(severity).append("**\n\n");
        prompt.append("### " + EXCLAMATION + " Important Instruction:\n")
                .append(POINT_RIGHT + " **Do not change any code. Just explain the risk, validation level, and recommended actions.**\n\n");
        prompt.append("### " + SEARCH + " Secret Overview\n\n")
                .append("- **Secret Name:** `").append(title).append("`\n")
                .append("- **Severity Level:** `").append(severity).append("`\n")
                .append("- **Details:** ").append(description).append("\n\n");
        prompt.append("### " + BRAIN + " Risk Understanding Based on Severity\n\n")
                .append("- **Critical**:  \n")
                .append("  The secret was **validated as active**. It is likely in use and can be exploited immediately if exposed.\n\n")
                .append("- **High**:  \n")
                .append("  The validation status is **unknown**. The secret may or may not be valid. Proceed with caution and treat it as potentially live.\n\n")
                .append("- **Medium**:  \n")
                .append("  The secret was identified as **invalid** or **mock/test value**. While not active, it may confuse developers or be reused insecurely.\n\n");
        prompt.append("### " + LOCK + " Why This Matters\n\n")
                .append("Hardcoded secrets pose a serious risk:\n")
                .append("- **Leakage** through public repositories or logs\n")
                .append("- **Unauthorized access** to APIs, cloud providers, or infrastructure\n")
                .append("- **Exploitation** via replay attacks, privilege escalation, or lateral movement\n\n");
        prompt.append("### " + CHECK + " Recommended Remediation Steps (for developer action)\n\n")
                .append("- Rotate the secret if it's live (Critical/High)\n")
                .append("- Move secrets to environment variables or secret managers\n")
                .append("- Audit the commit history to ensure it hasn't leaked publicly\n")
                .append("- Implement secret scanning in your CI/CD pipelines\n")
                .append("- Document safe handling procedures in your repo\n\n");
        prompt.append("### " + CLIPBOARD + " Next Steps Checklist (Markdown)\n\n")
                .append("```markdown\n")
                .append("### Next Steps:\n")
                .append("- [ ] Rotate the exposed secret if valid\n")
                .append("- [ ] Move secret to secure storage (.env or secret manager)\n")
                .append("- [ ] Clean secret from commit history if leaked\n")
                .append("- [ ] Annotate clearly if it's a fake or mock value\n")
                .append("- [ ] Implement CI/CD secret scanning and policies\n")
                .append("```\n\n");
        prompt.append("### " + PENCIL + " Output Format Guidelines\n\n")
                .append("- Use Markdown with clear sections\n")
                .append("- Do not attempt to edit or redact the code\n")
                .append("- Be factual, concise, and helpful\n")
                .append("- Assume this is shown to a developer unfamiliar with security tooling\n");
        return prompt.toString();
    }

    /**
     * Generates a detailed prompt for explaining a detected container issue.
     *
     * @param fileType  the file type of the container vulnerability
     * @param imageName the name of the image
     * @param imageTag  the tag of the image
     * @param severity  the severity level of the container vulnerability
     * @return the formatted Markdown prompt
     */
    public static String buildContainersExplanationPrompt(String fileType, String imageName,
                                                          String imageTag, String severity) {
        boolean isMalicious = severity.equalsIgnoreCase(SeverityLevel.MALICIOUS.getSeverity());
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the `").append(AGENT_NAME).append("`.\n\n")
                .append("Your task is to **analyze and explain** the container security issue affecting `")
                .append(fileType).append("` with image `").append(imageName).append(":").append(imageTag)
                .append("` and severity: `").append(severity).append("`.\n\n");
        prompt.append("###  Important Instructions:\n")
                .append("-  **Do not change anything in the code. Just explain the risks and remediation steps.**\n")
                .append("-  **Never include references from Checkmarx competitors.**\n\n");
        prompt.append("### " + SEARCH + " Container Overview\n\n")
                .append("- **File Type:** `").append(fileType).append("`\n")
                .append("- **Image:** `").append(imageName).append(":").append(imageTag).append("`\n")
                .append("- **Severity:** `").append(severity).append("`\n\n");
        prompt.append("### " + WHALE + " Container Security Issue Analysis\n\n")
                .append("**Issue Type:** ")
                .append(isMalicious ? "Malicious Container Image" : "Vulnerable Container Image")
                .append("\n\n");

        if (isMalicious) {
            // Malicious content
            buildMaliciousContentForContainerPrompt(prompt, imageTag);
        } else {
            // Vulnerable content
            buildVulnerabilityContentForContainerPrompt(prompt);
        }
        prompt.append("### " + TOOLS + " Remediation Guidance\n\n")
                .append("Offer actionable advice:\n")
                .append("- Whether to update, replace, or rebuild the container\n")
                .append("- If malicious: clearly emphasize permanent removal\n")
                .append("- Recommend secure base images and best practices\n")
                .append("- Suggest preventative measures:\n")
                .append("  - Use container scanning in CI/CD\n")
                .append("  - Prefer minimal base images (Alpine, distroless)\n")
                .append("  - Implement image signing and verification\n")
                .append("  - Regular security updates and patching\n")
                .append("  - Run containers as non-root users\n")
                .append("  - Use multi-stage builds to reduce attack surface\n\n");
        prompt.append("### " + CHECK + " Summary Section\n\n")
                .append("Conclude with:\n")
                .append("- Overall risk explanation for container deployments\n")
                .append("- Immediate remediation steps\n")
                .append("- Whether this specific image/tag is linked to online reports\n")
                .append("- If not, reference Checkmarx attribution (per above rules)\n")
                .append("- Never mention competitor vendors or tools\n\n");
        prompt.append("### Output Formatting\n\n")
                .append("- Use Markdown: `##`, `- `, `**bold**`, `code`\n")
                .append("- Developer-friendly tone, informative, concise\n")
                .append("- No speculation - use only trusted, verified sources\n")
                .append("- Include container-specific terminology and best practices\n");
        return prompt.toString();
    }

    /**
     * builds the malicious content for a container prompt.
     *
     * @param prompt   the prompt builder
     * @param imageTag the image tag
     */
    private static void buildMaliciousContentForContainerPrompt(StringBuilder prompt, String imageTag) {
        prompt.append("### " + FIRECRACKER + " Malicious Container Detected\n\n")
                .append("This container image has been flagged as **malicious**.\n\n")
                .append("** " + WARNING + " Never deploy or use this container under any circumstances.**\n\n")
                .append("#### " + SEARCH + " Investigation Guidelines:\n\n")
                .append("- Search for trusted community or vendor reports about malicious activity involving this image\n")
                .append("- If information exists about other tags but **not** tag `").append(imageTag).append("`, explicitly state:\n\n")
                .append("> _\"This specific tag (`").append(imageTag).append("`) was identified as malicious by Checkmarx Security researchers.\"_\n\n")
                .append("- If **no credible external information is found**, state:\n\n")
                .append("> _\"This container image was identified as malicious by Checkmarx Security researchers based on internal threat intelligence and behavioral analysis.\"_\n\n")
                .append("**Common Malicious Container Behaviors:**\n")
                .append("- Data exfiltration to external servers\n")
                .append("- Cryptocurrency mining operations\n")
                .append("- Backdoor access establishment\n")
                .append("- Credential harvesting\n")
                .append("- Lateral movement within infrastructure\n\n")
                .append("**Recommended Actions:**\n")
                .append("- " + CHECK + " Immediately remove from all deployment pipelines\n")
                .append("- " + CROSS + " Never redeploy or trust any version of this image\n")
                .append("- " + REPEAT + " Replace with a well-known, secure alternative\n")
                .append("- " + LOCK + " Audit all systems that may have run this container\n\n");

    }

    /**
     * Builds the vulnerability content for a container prompt.
     *
     * @param prompt the prompt builder
     */
    private static void buildVulnerabilityContentForContainerPrompt(StringBuilder prompt) {
        prompt.append("### " + POLICE_LIGHT + " Container Vulnerabilities\n\n")
                .append("This container image contains known security vulnerabilities.\n\n")
                .append("**Risk Assessment:**\n")
                .append("- **Critical/High:** Immediate action required - vulnerable to active exploitation\n")
                .append("- **Medium:** Should be addressed soon - potential for exploitation\n")
                .append("- **Low:** Address when convenient - limited immediate risk\n\n")
                .append("**Common Container Security Issues:**\n")
                .append("- Outdated base images with known CVEs\n")
                .append("- Unnecessary packages and services\n")
                .append("- Running as root user\n")
                .append("- Missing security patches\n")
                .append("- Insecure default configurations\n\n");
    }

    /**
     * Generates a detailed prompt for explaining an Infrastructure as Code (IaC) security issue.
     *
     * @param title         The title of the IaC security issue.
     * @param description   A detailed description of the security issue.
     * @param severity      The severity level of the issue (e.g., High, Medium, Low).
     * @param fileType      The type of IaC file where the issue is detected (e.g., Terraform, YAML).
     * @param expectedValue The expected secure value for the configuration.
     * @param actualValue   The actual insecure value in the configuration.
     * @return A formatted Markdown prompt explaining the security issue, risks, and remediation steps.
     */
    public static String buildIACExplanationPrompt(String title, String description, String severity,
                                                   String fileType, String expectedValue, String actualValue) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the `").append(AGENT_NAME).append("`.\n\n");
        prompt.append("Your task is to **analyze and explain** the Infrastructure as Code (IaC) security issue: **")
                .append(title).append("** with severity: `").append(severity).append("`.\n\n");

        prompt.append("### " + EXCLAMATION + " Important Instructions:\n")
                .append("- " + POINT_RIGHT + " **Do not change anything in the configuration. Just explain the risks and remediation steps.**\n")
                .append("- " + CROSS + " **Never include references from Checkmarx competitors.**\n\n");

        prompt.append("### " + SEARCH + " IaC Security Issue Overview\n\n")
                .append("- **Issue:** `").append(title).append("`\n")
                .append("- **File Type:** `").append(fileType).append("`\n")
                .append("- **Severity:** `").append(severity).append("`\n")
                .append("- **Description:** ").append(description).append("\n")
                .append("- **Expected Value:** `").append(expectedValue).append("`\n")
                .append("- **Actual Value:** `").append(actualValue).append("`\n\n");

        prompt.append("### " + CONSTRUCTION + " Infrastructure Security Issue Analysis\n\n")
                .append("**Issue Type:** Infrastructure Configuration Vulnerability\n\n");

        prompt.append("### " + POLICE_LIGHT + " Security Risks\n\n")
                .append("This configuration issue can lead to:\n")
                .append("- **Critical/High:** Immediate security exposure - vulnerable to active exploitation\n")
                .append("- **Medium:** Potential security risk - should be addressed soon\n")
                .append("- **Low:** Security hygiene - address when convenient\n\n");

        prompt.append("**Common IaC Security Issues:**\n")
                .append("- Overly permissive access controls\n")
                .append("- Exposed sensitive data or credentials\n")
                .append("- Insecure network configurations\n")
                .append("- Missing encryption settings\n")
                .append("- Unrestricted public access\n")
                .append("- Insecure service configurations\n\n");

        prompt.append("### " + TOOLS + " Remediation Guidance\n\n")
                .append("Offer actionable advice based on the file type:\n\n")
                .append("**For ").append(fileType).append(" configurations:**\n")
                .append("- Specific configuration changes needed\n")
                .append("- Security best practices to follow\n")
                .append("- Compliance considerations\n")
                .append("- Testing and validation steps\n\n");

        prompt.append("**Preventative Measures:**\n")
                .append("- Use IaC security scanning in CI/CD pipelines\n")
                .append("- Implement infrastructure policy as code\n")
                .append("- Regular security audits of infrastructure\n")
                .append("- Follow cloud provider security guidelines\n")
                .append("- Use secure configuration templates\n\n");

        prompt.append("### " + CHECK + " Summary Section\n\n")
                .append("Conclude with:\n")
                .append("- Overall risk explanation for infrastructure security\n")
                .append("- Immediate remediation steps\n")
                .append("- Impact on system security posture\n")
                .append("- Long-term security considerations\n\n");

        prompt.append("### " + PENCIL + " Output Formatting\n\n")
                .append("- Use Markdown: `##`, `- `, `**bold**`, `code`\n")
                .append("- Infrastructure-focused tone, informative, concise\n")
                .append("- No speculation - use only trusted, verified sources\n")
                .append("- Include infrastructure-specific terminology and best practices\n");
        return prompt.toString();
    }

    /**
     * Builds a detailed prompt for explaining a security rule, including its description,
     * severity, implications, and best practices for mitigation.
     *
     * @param ruleName    The name of the security rule to explain.
     * @param description A detailed description of the security issue.
     * @param severity    The severity level of the issue (e.g., High, Medium, Low).
     * @return A formatted prompt string with the explanation, best practices, and additional resources related to the security rule.
     */
    public static String buildASCAExplanationPrompt(String ruleName, String description, String severity) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the ").append(AGENT_NAME).append(" providing detailed security explanations.\n\n")
                .append("**Rule:** `").append(ruleName).append("`  \n")
                .append("**Severity:** `").append(severity).append("`  \n")
                .append("**Description:** ").append(description).append("\n\n")
                .append("Please provide a comprehensive explanation of this security issue.\n\n");

        prompt.append("### " + SEARCH + " Security Issue Overview\n\n")
                .append("**Rule Name:** ").append(ruleName).append("\n")
                .append("**Risk Level:** ").append(severity).append("\n\n")
                .append("### " + OPEN_BOOK + " Detailed Explanation\n\n")
                .append(description).append("\n\n")
                .append("### " + WARNING + " Why This Matters\n\n")
                .append("Explain the potential security implications:\n")
                .append("- What attacks could exploit this vulnerability?\n")
                .append("- What data or systems could be compromised?\n")
                .append("- What is the potential business impact?\n\n")
                .append("### " + SHIELD + " Security Best Practices\n\n")
                .append("Provide general guidance on:\n")
                .append("- How to prevent this type of issue\n")
                .append("- Coding patterns to avoid\n")
                .append("- Secure alternatives to recommend\n")
                .append("- Tools and techniques for detection\n\n")
                .append("### " + BOOKS + " Additional Resources\n\n")
                .append("Suggest relevant:\n")
                .append("- Security frameworks and standards\n")
                .append("- Documentation and guides\n")
                .append("- Tools for static analysis\n")
                .append("- Training materials\n\n");

        prompt.append("### " + PENCIL + " Output Format Guidelines\n\n")
                .append("- Use clear, educational language\n")
                .append("- Provide context for non-security experts\n")
                .append("- Include practical examples where helpful\n")
                .append("- Focus on actionable advice\n")
                .append("- Be thorough but concise\n");
        return prompt.toString();
    }
}
