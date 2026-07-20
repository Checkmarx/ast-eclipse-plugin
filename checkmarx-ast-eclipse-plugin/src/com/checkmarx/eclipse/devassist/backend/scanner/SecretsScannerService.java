package com.checkmarx.eclipse.devassist.backend.scanner;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Scanner for hardcoded secrets and credentials.
 *
 * Scans virtually all files (with exclusions) to detect:
 * - API keys
 * - Database passwords
 * - AWS credentials
 * - Private keys
 * - OAuth tokens
 * - SSH keys
 * - etc.
 *
 * Exclusions:
 * - Dependency manifests (OSS scanner handles these)
 * - Ignore files (.gitignore, .dockerignore)
 * - Lock files (package-lock.json, etc.)
 * - Build artifacts (node_modules, target, dist, build)
 */
public class SecretsScannerService extends BaseScannerService {

	// Manifest files that OSS scanner handles (don't scan for secrets)
	private static final String[] EXCLUDED_MANIFEST_FILES = {
		"package.json", "package-lock.json", "npm-shrinkwrap.json",
		"pom.xml",
		"go.mod", "go.sum",
		"requirements.txt", "Pipfile", "Pipfile.lock", "setup.py",
		"Gemfile", "Gemfile.lock",
		"Cargo.toml", "Cargo.lock",
		"composer.json", "composer.lock",
		"packages.config", ".csproj",
		"yarn.lock"
	};

	// Ignore files that don't contain application code
	private static final String[] EXCLUDED_CONFIG_FILES = {
		".gitignore", ".dockerignore", ".npmignore", ".eslintignore",
		".prettierignore", ".editorconfig",
		".lock", ".sum", ".shrinkwrap"
	};

	/**
	 * Create a Secrets scanner for a project.
	 *
	 * @param project Eclipse project
	 */
	public SecretsScannerService(IProject project) {
		super(project);
	}

	/**
	 * Check if file should be scanned for secrets.
	 *
	 * Scans most files EXCEPT:
	 * - Dependency manifests (package.json, pom.xml, etc.)
	 * - Ignore files (.gitignore, .npmignore, etc.)
	 * - Binary files
	 *
	 * @param filePath File path to check
	 * @return true if file should be scanned for secrets
	 */
	@Override
	protected boolean isFileTypeSupported(String filePath) {
		if (filePath == null) {
			return false;
		}

		String lowerPath = filePath.toLowerCase();
		String fileName = new java.io.File(filePath).getName().toLowerCase();

		// Exclude manifest files (OSS scanner handles these)
		for (String manifest : EXCLUDED_MANIFEST_FILES) {
			if (fileName.equals(manifest) || lowerPath.endsWith(manifest)) {
				return false;
			}
		}

		// Exclude ignore files
		for (String ignored : EXCLUDED_CONFIG_FILES) {
			if (fileName.equals(ignored) || fileName.endsWith(ignored)) {
				return false;
			}
		}

		// Exclude known binary file extensions
		String[] binaryExtensions = {
			".exe", ".dll", ".so", ".dylib", ".bin",
			".class", ".jar", ".war", ".ear",
			".zip", ".rar", ".7z", ".tar", ".gz",
			".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
			".mp3", ".mp4", ".mov", ".avi",
			".pdf", ".doc", ".docx", ".xls", ".xlsx"
		};

		for (String ext : binaryExtensions) {
			if (lowerPath.endsWith(ext)) {
				return false;
			}
		}

		// Scan most text files
		return true;
	}

	/**
	 * Execute secrets scan on a file.
	 *
	 * Generates realistic mock secrets for demonstration.
	 * In production, this would call CxWrapperFactory to execute actual scan.
	 *
	 * @param filePath File to scan
	 * @return Mock secrets detected
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing secrets scan on: " + filePath);

		// Generate realistic mock secrets for demo
		List<MockSecret> results = new ArrayList<>();

		String lowerPath = filePath.toLowerCase();

		// Simulate finding secrets in application code
		if (lowerPath.contains(".java") || lowerPath.contains(".py") ||
			lowerPath.contains(".js")) {

			results.add(new MockSecret("API_KEY found",
				"AWS API Key (AKIA...) exposed in source code", "CRITICAL",
				15, "aws_key_secret"));
			results.add(new MockSecret("DATABASE_PASSWORD found",
				"Database password hardcoded in connection string", "CRITICAL",
				25, "password123"));
			results.add(new MockSecret("GITHUB_TOKEN found",
				"GitHub personal access token exposed", "CRITICAL", 35,
				"ghp_1234567890..."));
			results.add(new MockSecret("OAUTH_BEARER_TOKEN found",
				"OAuth bearer token in environment configuration", "HIGH", 42,
				"Bearer eyJhbGc..."));
		}

		// Simulate finding secrets in config files
		if (lowerPath.contains(".yaml") || lowerPath.contains(".yml") ||
			lowerPath.contains(".json") || lowerPath.contains(".conf")) {

			results.add(new MockSecret("DATABASE_PASSWORD found",
				"MySQL root password hardcoded in config", "CRITICAL", 10,
				"root_password_123"));
			results.add(new MockSecret("SLACK_TOKEN found",
				"Slack webhook URL exposed in configuration", "HIGH", 20,
				"https://hooks.slack.com/services/..."));
			results.add(new MockSecret("SSH_PRIVATE_KEY found",
				"SSH private key embedded in config file", "CRITICAL", 30,
				"-----BEGIN RSA PRIVATE KEY-----"));
		}

		CxLogger.info(logTag + " ✓ Generated " + results.size() +
			" mock secrets");
		return results;
	}

	/**
	 * Adapt secrets scan results to ScanIssue model.
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults) {
		List<ScanIssue> issues = new ArrayList<>();

		if (!(rawResults instanceof List)) {
			return issues;
		}

		List<?> results = (List<?>) rawResults;
		int id = 1000;

		for (Object result : results) {
			if (!(result instanceof MockSecret)) {
				continue;
			}

			MockSecret secret = (MockSecret) result;
			ScanIssue issue = new ScanIssue();

			issue.setScanIssueId("SEC-" + id++);
			issue.setTitle(secret.type);
			issue.setDescription(secret.description);
			issue.setSeverity(secret.severity);
			issue.setProblematicLineNumber(secret.line_number);
			issue.setRemediationAdvise("Remove " + secret.type +
				" from source code and use environment variables instead");
			issue.setSecretValue("***MASKED***");
			issue.setScanEngine(
				com.checkmarx.eclipse.devassist.backend.scanner.ScannerService.ScannerType.SECRETS);

			issues.add(issue);
		}

		return issues;
	}

	/**
	 * Mock secret data for demo.
	 */
	static class MockSecret {
		String type;
		String description;
		String severity;
		int line_number;
		String value;

		MockSecret(String type, String desc, String severity, int line,
			String value) {
			this.type = type;
			this.description = desc;
			this.severity = severity;
			this.line_number = line;
			this.value = value;
		}
	}

	@Override
	public String getDisplayName() {
		return "Secrets Scanning";
	}

	@Override
	public ScannerType getScannerType() {
		return ScannerType.SECRETS;
	}
}
