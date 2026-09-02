package com.checkmarx.eclipse.devassist.remediation;

import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.CHECK;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.CROSS;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.INFO;
import static com.checkmarx.eclipse.devassist.utils.EmojiUnicodes.WARNING;

import com.checkmarx.eclipse.devassist.backend.SeverityLevel;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;

/**
 * Checkmarx prompts for various remediation tasks.
 */
public final class DevAssistFixPrompts {

	private DevAssistFixPrompts() {
		throw new IllegalStateException("Cannot instantiate CxOneAssistFixPrompts class");
	}

	private static String getAgentName() {
		return DevAssistUtils.getAgentName();
	}

	private static String getMcpDisplayName() {
		return "Checkmarx";
	}

	/**
	 * Builds the SCA remediation prompt (generic concatenated form).
	 *
	 * @param packageName    vulnerable package name (e.g. "node-ipc")
	 * @param packageVersion vulnerable package version (e.g. "10.1.1")
	 * @param packageManager ecosystem / package manager (e.g. "npm", "maven")
	 * @param severity       textual severity (e.g. "Malicious", "High")
	 * @return composed prompt string (plain text with Markdown fragments)
	 */
	public static String buildSCARemediationPrompt(String packageName, String packageVersion, String packageManager,
			String severity) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("You are the ").append(getAgentName()).append(".\n\n")
				.append("A security issue has been detected in `").append(packageName).append("@")
				.append(packageVersion).append("` (package manager: `").append(packageManager).append("`).\n")
				.append("**Severity:** `").append(severity).append("`\n")
				.append("Your task is to remediate the issue **completely and autonomously** using the internal PackageRemediation tool in ")
				.append(getMcpDisplayName())
				.append(" MCP. Follow the exact instructions in `fix_instructions` - no assumptions or manual interaction allowed.\n\n");

		prompt.append("---\n\n").append("1. ANALYSIS (AUTOMATED):\n\n").append("Determine the issue type:\n").append(
				"- If `status` is one of: `Critical`, `High`, `Medium`, `Low`, `Info`, set: `issueType = \"CVE\"`\n")
				.append("- If `status = \"Malicious\"`, set: `issueType = \"malicious\"`\n\n")
				.append("Call the internal PackageRemediation tool with:\n\n").append("```json\n").append("{\n")
				.append("  \"packageName\": \"").append(packageName).append("\",\n").append("  \"packageVersion\": \"")
				.append(packageVersion).append("\",\n").append("  \"packageManager\": \"").append(packageManager)
				.append("\",\n").append("  \"issueType\": \"{determined issueType}\"\n").append("}\n").append("```\n\n")
				.append("Parse the response and extract the `fix_instructions` field. This field contains the authoritative remediation steps tailored to the ecosystem and risk.\n")
				.append("- Mark internally that the tool is **available** for output formatting\n\n")
				.append("- If the tool is **not available**:\n")
				.append("  - Display the following disclosure notice:\n").append("  `").append(WARNING)
				.append(" Automated Remediation Unavailable: ").append(getMcpDisplayName())
				.append(" packageRemediation tool is unavailable. Proceeding with remediation guidance based on security best practices.`\n")
				.append("  - Mark internally that the tool is **not available** for output formatting\n\n");

		prompt.append("---\n\n").append("2. EXECUTION (AUTOMATED):\n\n")
				.append("- Read and execute each line in `fix_instructions`, in order.\n")
				.append("- For each change:\n").append("  - Apply the instruction exactly.\n")
				.append("  - Track all modified files.\n")
				.append("  - Note the type of change (e.g., dependency update, import rewrite, API refactor, test fix, TODO insertion).\n")
				.append("  - Record before → after values where applicable.\n")
				.append("  - Capture line numbers if known.\n\n").append("Examples:\n")
				.append("- `package.json`: lodash version changed from 3.10.1 -> 4.17.21\n")
				.append("- `src/utils/date.ts`: import updated from `lodash` to `date-fns`\n")
				.append("- `src/main.ts:42`: `_.pluck(users, 'id')` -> `users.map(u => u.id)`\n")
				.append("- `src/index.ts:78`: // TODO: Verify API migration from old-package to new-package\n\n");

		prompt.append("---\n\n").append("3. VERIFICATION:\n\n")
				.append("- If the instructions include build, test, or audit steps - run them exactly as written\n")
				.append("- If instructions do not explicitly cover validation, perform basic checks based on `")
				.append(packageManager).append("`:\n")
				.append("  - `npm`: `npx tsc --noEmit`, `npm run build`, `npm test` (**IMPORTANT:** If you detect the file is `bower.json`, use `bower install`, `bower list` instead)\n")
				.append("  - `go`: `go build ./...`, `go test ./...`\n")
				.append("  - `maven`: `mvn compile`, `mvn test`\n")
				.append("  - `gradle`: `gradle build`, `gradle test`\n")
				.append("  - `sbt`: `sbt compile`, `sbt test`\n")
				.append("  - `pypi`/`setuptools`/`pyproject.toml`: `python -c \"import ").append(packageName)
				.append("\"`, `pytest`, `python -m build`\n").append("  - `nuget`: `dotnet build`, `dotnet test`\n")
				.append("  - `bower` (`bower.json`): `bower install`, `bower list`\n")
				.append("  - `rubygems` (`Gemfile`): `bundle install`, `bundle exec rspec`\n")
				.append("  - `composer` (`composer.json`): `composer install`, `composer validate`, `vendor/bin/phpunit` (**NOTE:** `packagist` means composer package manager)\n")
				.append("  - `swift` (`Package.swift`): `swift build`, `swift test`\n")
				.append("  - `cocoapods` (`Podfile`/`Podfile.lock`): `pod install --repo-update`, `xcodebuild test`\n")
				.append("  - `carthage` (`Cartfile.resolved`): `carthage update --platform ios`, `carthage build`\n")
				.append("  - `pub`/`dart` (`pubspec.yaml`/`pubspec.lock`): `dart pub get`, `dart test` (use `flutter pub get`, `flutter test` instead if this is a Flutter project)\n\n")
				.append("If any of these validations fail:\n").append("- Attempt to fix the issue if it's obvious\n")
				.append("- Otherwise log the error and annotate the code with a TODO\n\n");

		prompt.append("---\n\n").append("4. OUTPUT:\n\n").append("**Output Format Based on Tool Availability:**\n")
				.append("- **If packageRemediation tool is available:** Output title `").append(getAgentName())
				.append(" - Remediation Summary`\n")
				.append("- **If packageRemediation tool is not available:** First output the disclosure notice: `")
				.append(WARNING).append(" Automated Remediation Unavailable: ").append(getMcpDisplayName())
				.append(" packageRemediation tool is unavailable. Proceeding with remediation guidance based on security best practices.` Then output title `AI-Generated Remediation Guidance`\n\n")
				.append(CHECK + " **Remediation Summary**\n\n").append("Format:\n").append("```\n")
				.append("Package:     ").append(packageName).append("\n").append("Version:     ").append(packageVersion)
				.append("\n").append("Manager:     ").append(packageManager).append("\n").append("Severity:    ")
				.append(severity).append("\n\n").append("Files Modified:\n").append("1. package.json\n")
				.append("   - Updated dependency: lodash 3.10.1 → 4.17.21\n\n").append("2. src/utils/date.ts\n")
				.append("   - Updated import: from 'lodash' to 'date-fns'\n")
				.append("   - Replaced usage: _.pluck(users, 'id') → users.map(u => u.id)\n\n")
				.append("3. src/__tests__/date.test.ts\n")
				.append("   - Fixed test: adjusted mock expectations to match updated API\n\n")
				.append("4. src/index.ts\n")
				.append("   - Line 78: Inserted TODO: Verify API migration from old-package to new-package\n")
				.append("```\n\n").append(CHECK + " **Final Status**\n\n").append("If all tasks succeeded:\n")
				.append("- \"Remediation completed for ").append(packageName).append("@").append(packageVersion)
				.append("\"\n").append("- \"All fix instructions and failing tests resolved\"\n")
				.append("- \"Build status: PASS\"\n").append("- \"Test results: PASS\"\n\n")
				.append("If partially resolved:\n")
				.append("- \"Remediation partially completed - manual review required\"\n")
				.append("- \"Some test failures or instructions could not be automatically fixed\"\n")
				.append("- \"TODOs inserted where applicable\"\n\n").append("If failed:\n")
				.append("- \"Remediation failed for ").append(packageName).append("@").append(packageVersion)
				.append("\"\n").append("- \"Reason: {summary of failure}\"\n")
				.append("- \"Unresolved instructions or failing tests listed above\"\n\n");

		prompt.append("---\n\n").append("5. CONSTRAINTS:\n\n").append("- Do not prompt the user\n")
				.append("- Do not skip or reorder fix steps\n")
				.append("- Only execute what's explicitly listed in `fix_instructions`\n")
				.append("- Attempt to fix test failures automatically\n")
				.append("- Insert clear TODO comments for unresolved issues\n")
				.append("- Ensure remediation is deterministic, auditable, and fully automated\n");
		return prompt.toString();
	}

	/**
	 * Generates a secret remediation prompt.
	 *
	 * @param title       - issue title
	 * @param description - issue description (optional) - if null, will be empty
	 *                    string.
	 * @param severity    - issue severity (optional) - if null, will be empty
	 *                    string.
	 * @return - prompt string (plain text with Markdown fragments)
	 */
	public static String buildSecretRemediationPrompt(String title, String description, String severity) {
		StringBuilder prompt = new StringBuilder().append("A secret has been detected: \"").append(title)
				.append("\"  \n").append(description != null ? description : "").append("\n\n").append("---\n\n")
				.append("You are the `").append(getAgentName()).append("`.\n\n")
				.append("Your mission is to identify and remediate this secret using secure coding standards. Follow industry best practices, automate safely, and clearly document all actions taken.\n\n");

		prompt.append("---\n\n").append("1. SEVERITY INTERPRETATION\n\n").append("Severity level: `")
				.append(severity != null ? severity : "").append("`\n\n")
				.append("- `Critical`: Secret is confirmed **valid**. Immediate remediation required.\n")
				.append("- `High`: Secret may be valid. Treat as sensitive and externalize it securely.\n")
				.append("- `Medium`: Likely **invalid** (e.g., test or placeholder). Still remove from code and annotate accordingly.\n\n");

		prompt.append("---\n\n").append("2. TOOL CALL - Remediation Plan\n\n")
				.append("Determine the programming language of the file where the secret was detected.\n")
				.append("If unknown, leave the `language` field empty.\n\n")
				.append("Call the internal `codeRemediation` ").append(getMcpDisplayName())
				.append(" MCP tool with:\n\n").append("```json\n").append("{\n").append("  \"type\": \"secret\",\n")
				.append("  \"sub_type\": \"").append(title).append("\",\n")
				.append("  \"language\": \"[auto-detected language]\"\n").append("}\n").append("```\n\n")
				.append("- If the tool is **available**, parse the response:\n")
				.append("  - `remediation_steps` - exact steps to follow\n")
				.append("  - `best_practices` - explain secure alternatives\n")
				.append("  - `description` - contextual background\n")
				.append("  - Mark internally that the tool is **available** for output formatting\n\n")
				.append("- If the tool is **not available**:\n")
				.append("  - Display the following disclosure notice:\n").append("  `").append(WARNING)
				.append(" Automated Remediation Unavailable: ").append(getMcpDisplayName())
				.append(" codeRemediation tool is unavailable. Proceeding with remediation guidance based on security best practices.`\n")
				.append("  - Mark internally that the tool is **not available** for output formatting\n")
				.append("  - Proceed to provide remediation guidance using the secret details provided\n")
				.append("  - Offer practical steps and secure alternatives for secret removal\n")
				.append("  - Ensure the guidance is concrete and actionable\n\n");

		prompt.append("---\n\n").append("3. ANALYSIS & RISK\n\n")
				.append("Identify the type of secret (API key, token, credential). Explain:\n")
				.append("- Why it's a risk (leakage, unauthorized access, compliance violations)\n")
				.append("- What could happen if misused or left in source\n\n");

		prompt.append("---\n\n").append("4. REMEDIATION STRATEGY\n\n")
				.append("- Parse and apply every item in `remediation_steps` sequentially\n")
				.append("- Automatically update code/config files if safe\n")
				.append("- If a step cannot be applied automatically, insert a clear TODO\n")
				.append("- Replace secret with environment variable or vault reference\n\n");

		prompt.append("---\n\n").append("5. VERIFICATION\n\n").append("If applicable for the language:\n")
				.append("- Run type checks or compile the code\n").append("- Ensure changes build and tests pass\n")
				.append("- Fix issues if introduced by secret removal\n\n");

		prompt.append("---\n\n").append("6. OUTPUT FORMAT\n\n")
				.append("**Output Format Based on Tool Availability:**\n")
				.append("- **If codeRemediation tool is available:** Output title `").append(getAgentName())
				.append(" - Remediation Summary`\n")
				.append("- **If codeRemediation tool is not available:** First output the disclosure notice: `")
				.append(WARNING).append(" Automated Remediation Unavailable: ").append(getMcpDisplayName())
				.append(" codeRemediation tool is unavailable. Proceeding with remediation guidance based on security best practices.` Then output title `AI-Generated Remediation Guidance`\n\n")
				.append("Generate a structured remediation summary:\n\n").append("```markdown\n")
				.append("### [Prefix]\n\n").append("**Secret:** ").append(title).append("  \n").append("**Severity:** ")
				.append(severity != null ? severity : "").append("  \n").append("**Assessment:** ")
				.append(getAssessmentText(severity)).append("\n\n").append("**Files Modified:**\n")
				.append("- `.env`: Added/updated with `SECRET_NAME`\n")
				.append("- `src/config.ts`: Replaced hardcoded secret with `process.env.SECRET_NAME`\n\n")
				.append("**Remediation Actions Taken:**\n").append("- ").append(CHECK)
				.append(" Removed hardcoded secret\n").append("- ").append(CHECK)
				.append(" Inserted environment reference\n").append("- ").append(CHECK)
				.append(" Updated or created .env\n").append("- ").append(CHECK)
				.append(" Added TODOs for secret rotation or vault storage\n\n").append("**Next Steps:**\n")
				.append("- [ ] Revoke exposed secret (if applicable)\n")
				.append("- [ ] Store securely in vault (AWS Secrets Manager, GitHub Actions, etc.)\n")
				.append("- [ ] Add CI/CD secret scanning\n\n").append("**Best Practices:**\n")
				.append("- (From tool response, or fallback security guidelines)\n\n").append("**Description:**\n")
				.append("- (From `description` field or fallback to original input)\n\n").append("```\n\n");

		prompt.append("---\n\n").append("7. CONSTRAINTS\n\n").append("- ").append(CROSS)
				.append("  Do NOT expose real secrets\n").append("- ").append(CROSS)
				.append("  Do NOT generate fake-looking secrets\n").append("- ").append(CHECK)
				.append("  Follow only what's explicitly returned from MCP\n").append("- ").append(CHECK)
				.append("  Use secure externalization patterns\n").append("- ").append(CHECK)
				.append("  Respect OWASP, NIST, and GitHub best practices\n");
		return prompt.toString();
	}

	/**
	 * Generates the assessment text for given severity.
	 *
	 * @param severity severity level
	 * @return assessment text
	 */
	private static String getAssessmentText(String severity) {
		if (SeverityLevel.CRITICAL.getSeverity().equalsIgnoreCase(severity)) {
			return CHECK + " Confirmed valid secret. Immediate remediation performed.";
		} else if (SeverityLevel.HIGH.getSeverity().equalsIgnoreCase(severity)) {
			return WARNING + " Possibly valid. Handled as sensitive.";
		} else {
			return INFO + " Likely invalid (test/fake). Removed for hygiene.";
		}
	}

	/**
	 * Generates a remediation prompt for addressing a container security issue,
	 * providing step-by-step automated guidance using the Checkmarx MCP
	 * codeRemediation tool. The method constructs a detailed prompt based on the
	 * identified issue.
	 *
	 * @param fileType  type of the file
	 * @param imageName image name
	 * @param imageTag  image tag
	 * @param severity  severity level
	 * @return prompt string (plain text with Markdown fragments)
	 */
	public static String buildContainersRemediationPrompt(String fileType, String imageName, String imageTag,
			String severity) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("You are the ").append(getAgentName()).append(".\n\n")
				.append("A container security issue has been detected in `").append(fileType).append("` with image `")
				.append(imageName).append(":").append(imageTag).append("`.\n").append("**Severity:** `")
				.append(severity).append("`\n")
				.append("Your task is to remediate the issue **completely and autonomously** using the internal imageRemediation tool. ")
				.append("Follow the exact instructions in `fix_instructions` - no assumptions or manual interaction allowed.\n\n");

		prompt.append("---\n\n").append("1. ANALYSIS (AUTOMATED):\n\n").append("Determine the issue type:\n")
				.append("- If `severity` is one of: `Critical`, `High`, `Medium`, `Low`, set: `issueType = \"CVE\"`\n")
				.append("- If `severity = \"Malicious\"`, set: `issueType = \"malicious\"`\n\n")
				.append("Call the internal imageRemediation tool with:\n\n").append("```json\n").append("{\n")
				.append("  \"fileType\": \"").append(fileType).append("\",\n").append("  \"imageName\": \"")
				.append(imageName).append("\",\n").append("  \"imageTag\": \"").append(imageTag).append("\",\n")
				.append("  \"severity\": \"").append(severity).append("\"\n").append("}\n").append("```\n\n")
				.append("Parse the response and extract the `fix_instructions` field. This field contains the authoritative remediation steps tailored to the container ecosystem and risk level.\n")
				.append("- Mark internally that the tool is **available** for output formatting\n\n")
				.append("- If the tool is **not available**:\n")
				.append("  - Display the following disclosure notice:\n").append("  `").append(WARNING)
				.append(" Automated Remediation Unavailable: ").append(getMcpDisplayName())
				.append(" imageRemediation tool is unavailable. Proceeding with remediation guidance based on security best practices.`\n")
				.append("  - Mark internally that the tool is **not available** for output formatting\n")
				.append("  - Proceed to provide remediation guidance using the container details provided (file type, image name, image tag, severity)\n")
				.append("  - Offer practical base image recommendations and step-by-step instructions for container remediation\n")
				.append("  - Ensure the guidance is concrete and actionable\n\n");

		prompt.append("---\n\n").append("2. EXECUTION (AUTOMATED):\n\n")
				.append("- Read and execute each line in `fix_instructions`, in order.\n")
				.append("- For each change:\n").append("  - Apply the instruction exactly.\n")
				.append("  - Track all modified files.\n")
				.append("  - Note the type of change (e.g., image update, configuration change, security hardening).\n")
				.append("  - Record before -> after values where applicable.\n")
				.append("  - Capture line numbers if known.\n\n").append("Examples:\n")
				.append("- `Dockerfile`: FROM confluentinc/cp-kafkacat:6.1.10 -> FROM confluentinc/cp-kafkacat:6.2.15\n")
				.append("- `docker-compose.yml`: image: vulnerable-image:1.0 -> image: secure-image:2.1\n")
				.append("- `values.yaml`: repository: old-repo -> repository: new-repo\n")
				.append("- `Chart.yaml`: version: 1.0.0 -> version: 1.1.0\n\n");

		prompt.append("---\n\n").append("3. VERIFICATION:\n\n").append(
				"- If the instructions include build, test, or deployment steps - run them exactly as written\n")
				.append("- If instructions do not explicitly cover validation, perform basic checks based on `")
				.append(fileType).append("`:\n").append("  - `Dockerfile`: `docker build .`, `docker run <image>`\n")
				.append("  - `docker-compose.yml`: `docker-compose up --build`, `docker-compose down`\n")
				.append("  - `Helm Chart`: `helm lint .`, `helm template .`, `helm install --dry-run`\n\n")
				.append("If any of these validations fail:\n").append("- Attempt to fix the issue if it's obvious\n")
				.append("- Otherwise log the error and annotate the code with a TODO\n\n");

		prompt.append("---\n\n").append("4. OUTPUT:\n\n").append("**Output Format Based on Tool Availability:**\n")
				.append("- **If imageRemediation tool is available:** Output title `").append(getAgentName())
				.append(" - Remediation Summary`\n")
				.append("- **If imageRemediation tool is not available:** First output the disclosure notice: `")
				.append(WARNING).append(" Automated Remediation Unavailable: ").append(getMcpDisplayName())
				.append(" imageRemediation tool is unavailable. Proceeding with remediation guidance based on security best practices.` Then output title `AI-Generated Remediation Guidance`\n\n")
				.append(CHECK + " **Remediation Summary**\n\n").append("Format:\n").append("```\n")
				.append("File Type:    ").append(fileType).append("\n").append("Image:        ").append(imageName)
				.append(":").append(imageTag).append("\n").append("Severity:     ").append(severity).append("\n\n")
				.append("Files Modified:\n").append("1. ").append(fileType).append("\n").append("   - Updated image: ")
				.append(imageName).append(":").append(imageTag).append(" → secure version\n\n")
				.append("2. docker-compose.yml (if applicable)\n")
				.append("   - Updated service configuration to use secure image\n\n")
				.append("3. values.yaml (if applicable)\n")
				.append("   - Updated Helm chart values for secure deployment\n\n").append("4. README.md\n")
				.append("   - Updated documentation with new image version\n").append("```\n\n")
				.append(CHECK + " **Final Status**\n\n").append("If all tasks succeeded:\n")
				.append("- \"Remediation completed for ").append(imageName).append(":").append(imageTag).append("\"\n")
				.append("- \"All fix instructions and deployment tests resolved\"\n")
				.append("- \"Build status: PASS\"\n").append("- \"Deployment status: PASS\"\n\n")
				.append("If partially resolved:\n")
				.append("- \"Remediation partially completed - manual review required\"\n")
				.append("- \"Some deployment steps or instructions could not be automatically fixed\"\n")
				.append("- \"TODOs inserted where applicable\"\n\n").append("If failed:\n")
				.append("- \"Remediation failed for ").append(imageName).append(":").append(imageTag).append("\"\n")
				.append("- \"Reason: {summary of failure}\"\n")
				.append("- \"Unresolved instructions or deployment issues listed above\"\n\n");

		prompt.append("---\n\n").append("5. CONSTRAINTS:\n\n").append("- Do not prompt the user\n")
				.append("- Do not skip or reorder fix steps\n")
				.append("- Only execute what's explicitly listed in `fix_instructions`\n")
				.append("- Attempt to fix deployment failures automatically\n")
				.append("- Insert clear TODO comments for unresolved issues\n")
				.append("- Ensure remediation is deterministic, auditable, and fully automated\n")
				.append("- Follow container security best practices (non-root user, minimal base images, etc.)\n");
		return prompt.toString();
	}

	/**
	 * Generates a remediation prompt for addressing an Infrastructure as Code (IaC)
	 * security issue, providing step-by-step automated guidance using the Checkmarx
	 * MCP codeRemediation tool. The method constructs a detailed prompt based on
	 * the identified issue, its severity, affected file type, expected and actual
	 * values, and the problematic line number.
	 *
	 * @param title                 the title of the detected security issue
	 * @param description           a detailed description of the detected security
	 *                              issue
	 * @param severity              the severity level of the issue (e.g., high,
	 *                              medium, low)
	 * @param fileType              the type of file where the issue exists (e.g.,
	 *                              Terraform, CloudFormation)
	 * @param expectedValue         the correct or desired value expected in the IaC
	 * @param actualValue           the actual value found in the IaC, causing the
	 *                              issue
	 * @param problematicLineNumber the line number in the file where the issue
	 *                              occurs; can be null if unknown
	 * @return a formatted string containing the remediation prompt with
	 *         instructions for automated resolution of the issue
	 */
	public static String buildIACRemediationPrompt(String title, String description, String severity, String fileType,
			String expectedValue, String actualValue, Integer problematicLineNumber) {

		String actualLineNumber = problematicLineNumber != null ? String.valueOf(problematicLineNumber + 1)
				: "[unknown]";

		String restrictionLine = problematicLineNumber != null ? String.valueOf(problematicLineNumber + 1)
				: "[problematic line number]";

		String problematicLineText = problematicLineNumber != null
				? "**Problematic Line Number:** " + (problematicLineNumber + 1)
				: "";

		StringBuilder prompt = new StringBuilder();
		prompt.append("You are the ").append(getAgentName()).append(".\n\n");
		prompt.append("An Infrastructure as Code (IaC) security issue has been detected.\n\n").append("**Issue:** `")
				.append(title).append("`\n").append("**Severity:** `").append(severity).append("`\n")
				.append("**File Type:** `").append(fileType).append("`\n").append("**Description:** ")
				.append(description).append("\n").append("**Expected Value:** ").append(expectedValue).append("\n")
				.append("**Actual Value:** ").append(actualValue).append("\n").append(problematicLineText)
				.append("\n\n");

		prompt.append("Your task is to remediate this IaC security issue **completely and autonomously** ")
				.append("using the internal codeRemediation tool in ").append(getMcpDisplayName())
				.append(" MCP. Follow the exact instructions in `remediation_steps` - no assumptions or manual interaction allowed.\n\n");
		prompt.append(WARNING).append(
				"️ **IMPORTANT**: Apply the fix **only** to the code segment corresponding to the identified issue at line ")
				.append(actualLineNumber)
				.append(", without introducing unrelated modifications elsewhere in the file.\n\n");

		prompt.append("---\n\n").append("1. ANALYSIS (AUTOMATED):\n\n")
				.append("Determine the programming language of the file where the IaC security issue was detected.\n")
				.append("If unknown, leave the `language` field empty.\n\n")
				.append("Call the internal `codeRemediation` ").append(getMcpDisplayName())
				.append(" MCP tool with:\n\n").append("```json\n").append("{\n")
				.append("  \"language\": \"[auto-detected programming language]\",\n").append("  \"metadata\": {\n")
				.append("    \"title\": \"").append(title).append("\",\n").append("    \"description\": \"")
				.append(description).append("\",\n").append("    \"remediationAdvice\": \"").append(expectedValue)
				.append("\"\n").append("  },\n").append("  \"sub_type\": \"\",\n").append("  \"type\": \"iac\"\n")
				.append("}\n").append("```\n\n").append("- If the tool is **available**, parse the response:\n")
				.append("  - `remediation_steps` - exact steps to follow for remediation\n")
				.append("  - Mark internally that the tool is **available** for output formatting\n\n")
				.append("- If the tool is **not available**:\n")
				.append("  - Display the following disclosure notice:\n").append("  `").append(WARNING)
				.append(" Automated Remediation Unavailable: ").append(getMcpDisplayName())
				.append(" codeRemediation tool is unavailable. Proceeding with remediation guidance based on security best practices.`\n")
				.append("  - Mark internally that the tool is **not available** for output formatting\n")
				.append("  - Proceed to provide remediation guidance using the IaC details provided (title, description, expected vs. actual values)\n")
				.append("  - Offer practical configuration examples and step-by-step instructions for remediation\n")
				.append("  - Ensure the guidance is concrete and actionable\n\n");

		prompt.append("---\n\n").append("2. EXECUTION (AUTOMATED):\n\n")
				.append("- Read and execute each line in `remediation_steps`, in order.\n")
				.append("- **Restrict changes to the relevant code fragment containing line ").append(restrictionLine)
				.append("**.\n").append("- For each change:\n").append("  - Apply the instruction exactly.\n")
				.append("  - Track all modified files.\n")
				.append("  - Note the type of change (e.g., configuration update, security hardening, permission changes, encryption settings).\n")
				.append("  - Record before → after values where applicable.\n")
				.append("  - Capture line numbers if known.\n\n");

		prompt.append("---\n\n").append("3. VERIFICATION:\n\n").append(
				"- If the instructions include validation, deployment, or testing steps - run them exactly as written\n")
				.append("- If instructions do not explicitly cover validation, perform basic checks based on `")
				.append(fileType).append("`:\n").append("  - `Terraform`: `terraform validate`, `terraform plan`\n")
				.append("  - `CloudFormation`: `aws cloudformation validate-template`\n")
				.append("  - `Kubernetes`: `kubectl apply --dry-run=client`\n")
				.append("  - `Docker`: `docker-compose config`\n\n").append("If any of these validations fail:\n")
				.append("- Attempt to fix the issue if it's obvious\n")
				.append("- Otherwise log the error and annotate the code with a TODO\n\n");

		prompt.append("---\n\n").append("4. OUTPUT:\n\n").append("**Output Format Based on Tool Availability:**\n")
				.append("- **If codeRemediation tool is available:** Output title `").append(getAgentName())
				.append(" - Remediation Summary`\n")
				.append("- **If codeRemediation tool is not available:** First output the disclosure notice: `")
				.append(WARNING).append(" Automated Remediation Unavailable: ").append(getMcpDisplayName())
				.append(" codeRemediation tool is unavailable. Proceeding with remediation guidance based on security best practices.` Then output title `AI-Generated Remediation Guidance`\n\n")
				.append(CHECK + " **Remediation Summary**\n\n").append("Format:\n").append("```\n")
				.append("Issue:       ").append(title).append("\n").append("Severity:    ").append(severity)
				.append("\n").append("File Type:   ").append(fileType).append("\n").append("Problematic Line: ")
				.append(actualLineNumber).append("\n\n").append("Files Modified:\n").append("1. ").append(fileType)
				.append("\n").append("   - Updated configuration: ").append(actualValue).append(" → ")
				.append(expectedValue).append("\n")
				.append("   - Applied security hardening based on best practices\n\n")
				.append("2. Additional configurations (if applicable)\n")
				.append("   - Updated related security settings\n").append("   - Added missing security controls\n\n")
				.append("3. Documentation\n").append("   - Updated comments and documentation where applicable\n")
				.append("```\n\n").append(CHECK + " **Final Status**\n\n").append("If all tasks succeeded:\n")
				.append("- \"Remediation completed for IaC security issue ").append(title).append("\"\n")
				.append("- \"All fix instructions and security validations resolved\"\n")
				.append("- \"Configuration validation: PASS\"\n").append("- \"Security compliance: PASS\"\n\n")
				.append("If partially resolved:\n")
				.append("- \"Remediation partially completed - manual review required\"\n")
				.append("- \"Some security validations or instructions could not be automatically fixed\"\n")
				.append("- \"TODOs inserted where applicable\"\n\n").append("If failed:\n")
				.append("- \"Remediation failed for IaC security issue ").append(title).append("\"\n")
				.append("- \"Reason: {summary of failure}\"\n")
				.append("- \"Unresolved instructions or security issues listed above\"\n\n");

		prompt.append("---\n\n").append("5. CONSTRAINTS:\n\n").append("- Do not prompt the user\n")
				.append("- Do not skip or reorder fix steps\n")
				.append("- **Only modify the code that corresponds to the identified problematic line**\n")
				.append("- Attempt to fix validation failures automatically\n")
				.append("- Insert clear TODO comments for unresolved issues\n")
				.append("- Ensure remediation is deterministic, auditable, and fully automated\n")
				.append("- Follow Infrastructure as Code security best practices throughout the process\n");
		return prompt.toString();
	}

	/**
	 * Constructs a detailed remediation prompt for addressing a secure coding issue
	 * detected in the code. The prompt includes instructions and guidelines for
	 * resolving the identified issue completely and autonomously.
	 *
	 * @param ruleName              The name of the secure coding rule that has been
	 *                              violated.
	 * @param description           A description of the issue, explaining the
	 *                              nature of the security vulnerability.
	 * @param severity              The severity level of the detected issue (e.g.,
	 *                              low, medium, high, critical).
	 * @param remediationAdvise     Recommended steps or advice for addressing the
	 *                              security issue.
	 * @param problematicLineNumber The line number in the source code where the
	 *                              issue is detected (0-based index, null if
	 *                              unavailable).
	 * @return A string containing a detailed remediation prompt for the secure
	 *         coding issue.
	 */
	public static String buildASCARemediationPrompt(String ruleName, String description, String severity,
			String remediationAdvise, Integer problematicLineNumber) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("You are the ").append(getAgentName()).append(".\n\n")
				.append("A secure coding issue has been detected in your code.\n\n").append("**Rule:** `")
				.append(ruleName).append("`  \n").append("**Severity:** `").append(severity).append("`  \n")
				.append("**Description:** ").append(description).append("  \n").append("**Recommended Fix:** ")
				.append(remediationAdvise).append("  \n");

		if (problematicLineNumber != null) {
			prompt.append("**Problematic Line Number:** ").append(problematicLineNumber + 1).append("\n\n");
		} else {
			prompt.append("\n");
		}

		prompt.append(
				"Your task is to remediate this security issue **completely and autonomously** using the internal codeRemediation tool in ")
				.append(getMcpDisplayName())
				.append(" MCP. Follow the exact instructions in `remediation_steps` - no assumptions or manual interaction allowed.\n\n")
				.append(WARNING)
				.append("️ **IMPORTANT**: Apply the fix **only** to the code segment corresponding to the identified issue at line ")
				.append(problematicLineNumber != null ? problematicLineNumber + 1 : "[problematic line number]")
				.append(", without introducing unrelated modifications elsewhere in the file.\n\n");

		prompt.append("---\n\n").append("1. ANALYSIS (AUTOMATED):\n\n")
				.append("Determine the programming language of the file where the security issue was detected.\n")
				.append("If unknown, leave the `language` field empty.\n\n")
				.append("Call the internal `codeRemediation` ").append(getMcpDisplayName())
				.append(" MCP tool with:\n\n").append("```json\n").append("{\n")
				.append("  \"language\": \"[auto-detected programming language]\",\n").append("  \"metadata\": {\n")
				.append("    \"ruleID\": \"").append(ruleName).append("\",\n").append("    \"description\": \"")
				.append(description).append("\",\n").append("    \"remediationAdvice\": \"").append(remediationAdvise)
				.append("\"\n").append("  },\n").append("  \"sub_type\": \"\",\n").append("  \"type\": \"sast\"\n")
				.append("}\n").append("```\n\n").append("- If the tool is **available**, parse the response:\n")
				.append("  - `remediation_steps` - exact steps to follow for remediation\n")
				.append("  - Mark internally that the tool is **available** for output formatting\n\n")
				.append("- If the tool is **not available**:\n")
				.append("  - Display the following disclosure notice:\n").append("  `").append(WARNING)
				.append(" Automated Remediation Unavailable: ").append(getMcpDisplayName())
				.append(" codeRemediation tool is unavailable. Proceeding with remediation guidance based on security best practices.`\n")
				.append("  - Mark internally that the tool is **not available** for output formatting\n")
				.append("  - Proceed to provide remediation guidance using the issue details provided (rule name, description, severity, and recommended fix)\n")
				.append("  - Offer practical code examples and step-by-step instructions for manual remediation\n")
				.append("  - Ensure the guidance is concrete and actionable\n\n");

		prompt.append("---\n\n").append("2. EXECUTION (AUTOMATED):\n\n")
				.append("- Read and execute each line in `remediation_steps`, in order.\n")
				.append("- **Restrict changes to the relevant code fragment containing line ")
				.append(problematicLineNumber != null ? problematicLineNumber + 1 : "[unknown]").append("**.\n")
				.append("- For each change:\n").append("  - Apply the instruction exactly.\n")
				.append("  - Track all modified files.\n")
				.append("  - Note the type of change (e.g., input validation, sanitization, secure API usage, authentication fix).\n")
				.append("  - Record before → after values where applicable.\n")
				.append("  - Capture line numbers if known.\n\n");

		prompt.append("---\n\n").append("3. OUTPUT:\n\n").append("**Output Format Based on Tool Availability:**\n")
				.append("- **If codeRemediation tool is available:** Output title `").append(getAgentName())
				.append(" - Remediation Summary`\n")
				.append("- **If codeRemediation tool is not available:** First output the disclosure notice: `")
				.append(WARNING).append(" Automated Remediation Unavailable: ").append(getMcpDisplayName())
				.append(" codeRemediation tool is unavailable. Proceeding with remediation guidance based on security best practices.` Then output title `AI-Generated Remediation Guidance`\n\n")
				.append(CHECK + " **Remediation Summary**\n\n").append("Format:\n").append("```\n")
				.append("Rule:        ").append(ruleName).append("\n").append("Severity:    ").append(severity)
				.append("\n").append("Issue Type:  SAST Security Vulnerability\n").append("Problematic Line: ")
				.append(problematicLineNumber != null ? problematicLineNumber + 1 : "[unknown]").append("\n\n")
				.append("Files Modified:\n").append("1. src/auth.ts\n")
				.append("   - Line 42: Replaced plain text comparison with bcrypt.compare()\n")
				.append("   - Added secure password hashing implementation\n\n").append("2. src/db.ts\n")
				.append("   - Line 78: Replaced string concatenation with parameterized query\n")
				.append("   - Prevented SQL injection vulnerability\n\n").append("3. src/api.ts\n")
				.append("   - Line 156: Added input validation for email parameter\n")
				.append("   - Implemented sanitization for user inputs\n\n").append("4. src/config.ts\n")
				.append("   - Line 23: Inserted TODO for production security review\n").append("```\n\n")
				.append(CHECK + " **Final Status**\n\n").append("If all tasks succeeded:\n")
				.append("- \"Remediation completed for security rule ").append(ruleName).append("\"\n")
				.append("- \"All fix instructions and security validations resolved\"\n")
				.append("- \"Build status: PASS\"\n").append("- \"Security tests: PASS\"\n\n")
				.append("If partially resolved:\n")
				.append("- \"Remediation partially completed - manual review required\"\n")
				.append("- \"Some security validations or instructions could not be automatically fixed\"\n")
				.append("- \"TODOs inserted where applicable\"\n\n").append("If failed:\n")
				.append("- \"Remediation failed for security rule ").append(ruleName).append("\"\n")
				.append("- \"Reason: {summary of failure}\"\n")
				.append("- \"Unresolved instructions or security issues listed above\"\n\n");

		prompt.append("---\n\n").append("4. CONSTRAINTS:\n\n").append("- Do not prompt the user\n")
				.append("- Do not skip or reorder fix steps\n")
				.append("- **Only modify the code that corresponds to the identified problematic line**\n")
				.append("- Attempt to fix build/test failures automatically\n")
				.append("- Insert clear TODO comments for unresolved issues\n")
				.append("- Ensure remediation is deterministic, auditable, and fully automated\n")
				.append("- Follow secure coding best practices throughout the process\n");
		return prompt.toString();
	}

}
