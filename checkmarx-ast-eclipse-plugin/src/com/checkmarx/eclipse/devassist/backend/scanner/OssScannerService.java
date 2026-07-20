package com.checkmarx.eclipse.devassist.backend.scanner;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Scanner for Open Source Supply Chain (OSS) vulnerabilities.
 *
 * Scans dependency/manifest files:
 * - package.json, package-lock.json (npm)
 * - pom.xml (Maven)
 * - go.mod, go.sum (Go)
 * - requirements.txt (Python)
 * - Gemfile (Ruby)
 * - Cargo.toml (Rust)
 * - Pipfile (Pipenv)
 * - etc.
 *
 * Detects vulnerable open source packages and their CVEs.
 */
public class OssScannerService extends BaseScannerService {

	private static final String[] MANIFEST_FILE_PATTERNS = {
		// npm
		"package.json", "package-lock.json", "npm-shrinkwrap.json",
		// Maven
		"pom.xml",
		// Go
		"go.mod", "go.sum",
		// Python
		"requirements.txt", "Pipfile", "Pipfile.lock", "setup.py",
		// Ruby
		"Gemfile", "Gemfile.lock",
		// Rust
		"Cargo.toml", "Cargo.lock",
		// PHP
		"composer.json", "composer.lock",
		// .NET
		"packages.config", ".csproj",
		// Node.js
		"yarn.lock", ".npm"
	};

	/**
	 * Create an OSS scanner for a project.
	 *
	 * @param project Eclipse project
	 */
	public OssScannerService(IProject project) {
		super(project);
	}

	/**
	 * Check if file is a manifest file that OSS scanner can scan.
	 *
	 * @param filePath File path to check
	 * @return true if file is a manifest file
	 */
	@Override
	protected boolean isFileTypeSupported(String filePath) {
		if (filePath == null) {
			return false;
		}

		String lowerPath = filePath.toLowerCase();
		String fileName = new java.io.File(filePath).getName().toLowerCase();

		// Check file name patterns
		for (String pattern : MANIFEST_FILE_PATTERNS) {
			if (fileName.equals(pattern) || lowerPath.endsWith(pattern)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Execute OSS scan on a manifest file.
	 *
	 * In a real implementation, this would:
	 * 1. Read the manifest file
	 * 2. Parse dependencies
	 * 3. Query Checkmarx OSS database
	 * 4. Return vulnerability data
	 *
	 * For now, returns empty list (Phase 3 will integrate with CxWrapperFactory).
	 *
	 * @param filePath Manifest file to scan
	 * @return Raw scanner results
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing OSS scan on: " + filePath);

		// TODO: Phase 3 - Call CxWrapperFactory to execute actual scan
		// For now, return placeholder
		return new ArrayList<Object>();
	}

	/**
	 * Adapt OSS scan results to ScanIssue model.
	 *
	 * Converts raw OSS vulnerabilities to standardized ScanIssue objects
	 * with CVE information, severity, remediation advice, etc.
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults) {
		List<ScanIssue> issues = new ArrayList<>();

		// TODO: Phase 3 - Parse raw results and create ScanIssue objects
		// Example:
		// for (OssVulnerability vuln : (List<OssVulnerability>) rawResults) {
		//     ScanIssue issue = new ScanIssue();
		//     issue.setScanIssueId(vuln.getCveId());
		//     issue.setTitle(vuln.getPackageName() + ": " + vuln.getVulnerabilityTitle());
		//     issue.setSeverity(mapSeverity(vuln.getCvssScore()));
		//     issue.setPackageVersion(vuln.getVulnerableVersion());
		//     issue.setPackageManager(detectPackageManager(filePath));
		//     issue.setCve(vuln.getCveId());
		//     issue.setRemediationAdvise("Update to version " + vuln.getFixedVersion());
		//     issues.add(issue);
		// }

		return issues;
	}

	@Override
	public String getDisplayName() {
		return "Open Source Supply Chain";
	}

	@Override
	public ScannerType getScannerType() {
		return ScannerType.OSS;
	}

	/**
	 * Detect package manager from manifest file name.
	 *
	 * @param filePath Manifest file path
	 * @return Package manager name
	 */
	private String detectPackageManager(String filePath) {
		String fileName = new java.io.File(filePath).getName().toLowerCase();

		if (fileName.contains("package")) return "npm";
		if (fileName.contains("pom")) return "maven";
		if (fileName.contains("go.")) return "go";
		if (fileName.contains("requirements") || fileName.contains("pipfile"))
			return "python";
		if (fileName.contains("gemfile")) return "ruby";
		if (fileName.contains("cargo")) return "rust";
		if (fileName.contains("composer")) return "php";
		if (fileName.contains("csproj") || fileName.contains("packages"))
			return "dotnet";

		return "unknown";
	}
}
