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
	 * Generates realistic mock OSS vulnerabilities for demonstration.
	 * In production, this would call CxWrapperFactory to execute actual scan.
	 *
	 * @param filePath Manifest file to scan
	 * @return Mock OSS vulnerabilities
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing OSS scan on: " + filePath);

		// Generate realistic mock OSS findings for demo
		List<MockOssVulnerability> results = new ArrayList<>();

		String fileName = new java.io.File(filePath).getName().toLowerCase();

		if (fileName.contains("package")) {
			// npm packages
			results.add(new MockOssVulnerability("log4j-core:2.14.1",
				"Apache Log4j2 RCE Vulnerability", "CRITICAL", "CVE-2021-44228",
				"2.17.0", "Upgrade to 2.17.0 or later"));
			results.add(new MockOssVulnerability("express:4.16.1",
				"Express DoS vulnerability", "HIGH", "CVE-2022-1111",
				"4.18.0", "Upgrade express to version 4.18.0 or later"));
		} else if (fileName.contains("pom")) {
			// Maven packages
			results.add(new MockOssVulnerability("commons-collections:3.2.1",
				"Apache Commons Collections serialization RCE", "CRITICAL",
				"CVE-2015-6420", "3.2.2", "Upgrade to 3.2.2 or 4.0"));
			results.add(new MockOssVulnerability(
				"org.springframework:spring-core:4.3.19",
				"Spring Core RCE via ClassPathXmlApplicationContext", "HIGH",
				"CVE-2016-6652", "4.3.21", "Upgrade to 4.3.21 or 5.0.8"));
		} else if (fileName.contains("requirements")) {
			// Python packages
			results.add(new MockOssVulnerability("Django:1.11.0",
				"Django SQL Injection in lookup", "HIGH", "CVE-2021-35042",
				"1.11.29", "Upgrade Django to 1.11.29 or later"));
			results.add(new MockOssVulnerability("requests:2.6.0",
				"HTTPS requests with SSL verification bypass", "CRITICAL",
				"CVE-2014-1829", "2.18.0", "Upgrade requests to 2.18.0 or later"));
		}

		CxLogger.info(logTag + " ✓ Generated " + results.size() +
			" mock OSS vulnerabilities");
		return results;
	}

	/**
	 * Adapt OSS scan results to ScanIssue model.
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
		int id = 1;

		for (Object result : results) {
			if (!(result instanceof MockOssVulnerability)) {
				continue;
			}

			MockOssVulnerability vuln = (MockOssVulnerability) result;
			ScanIssue issue = new ScanIssue();

			issue.setScanIssueId("OSS-" + id++);
			issue.setTitle(vuln.package_name + ": " + vuln.title);
			issue.setDescription(vuln.title);
			issue.setSeverity(vuln.severity.toUpperCase());
			issue.setPackageVersion(vuln.vulnerable_version);
			issue.setPackageManager(detectPackageManager(vuln.package_name));
			issue.setCve(vuln.cve);
			issue.setRemediationAdvise("Update to version " + vuln.fixed_version +
				" or later");
			issue.setProblematicLineNumber(1);
			issue.setScanEngine(
				com.checkmarx.eclipse.devassist.backend.scanner.ScannerService.ScannerType.OSS);

			issues.add(issue);
		}

		return issues;
	}

	/**
	 * Mock OSS vulnerability data for demo.
	 */
	static class MockOssVulnerability {
		String package_name;
		String title;
		String severity;
		String cve;
		String fixed_version;
		String remediation;

		MockOssVulnerability(String pkg, String title, String severity,
			String cve, String fixed, String remediation) {
			this.package_name = pkg;
			this.title = title;
			this.severity = severity;
			this.cve = cve;
			this.fixed_version = fixed;
			this.remediation = remediation;
		}
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
