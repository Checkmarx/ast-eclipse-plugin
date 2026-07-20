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
	 * In a real implementation, this would:
	 * 1. Read the file content
	 * 2. Run regex patterns to detect secrets (API keys, passwords, tokens)
	 * 3. Query Checkmarx secrets database
	 * 4. Return detected secrets
	 *
	 * For now, returns empty list (Phase 3 will integrate with CxWrapperFactory).
	 *
	 * @param filePath File to scan
	 * @return Raw scanner results
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing secrets scan on: " + filePath);

		// TODO: Phase 3 - Call CxWrapperFactory to execute actual scan
		// For now, return placeholder
		return new ArrayList<Object>();
	}

	/**
	 * Adapt secrets scan results to ScanIssue model.
	 *
	 * Converts detected secrets to standardized ScanIssue objects
	 * with severity, remediation advice, etc.
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults) {
		List<ScanIssue> issues = new ArrayList<>();

		// TODO: Phase 3 - Parse raw results and create ScanIssue objects
		// Example:
		// for (SecretFinding secret : (List<SecretFinding>) rawResults) {
		//     ScanIssue issue = new ScanIssue();
		//     issue.setScanIssueId(secret.getId());
		//     issue.setTitle("Hardcoded " + secret.getType());
		//     issue.setSeverity(secret.getType().equals("api_key") ? "CRITICAL" : "HIGH");
		//     issue.setDescription(secret.getPattern().getDescription());
		//     issue.setRemediationAdvise("Remove " + secret.getType() + " from source code");
		//     issue.setProblematicLineNumber(secret.getLineNumber());
		//     issues.add(issue);
		// }

		return issues;
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
