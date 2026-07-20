package com.checkmarx.eclipse.devassist.backend.scanner;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Scanner for Application Security Code Analysis (ASCA).
 *
 * Scans source code files to detect:
 * - SQL Injection
 * - Cross-Site Scripting (XSS)
 * - Path Traversal
 * - Command Injection
 * - Insecure Deserialization
 * - Weak Cryptography
 * - Authentication Issues
 * - etc.
 *
 * Supports languages:
 * - Java (.java)
 * - Python (.py)
 * - JavaScript (.js)
 * - TypeScript (.ts)
 * - C++ (.cpp)
 * - C# (.cs)
 * - Go (.go)
 * - PHP (.php)
 * - Ruby (.rb)
 * - Swift (.swift)
 */
public class AscaScannerService extends BaseScannerService {

	private static final String[] SUPPORTED_EXTENSIONS = {
		".java",    // Java
		".py",      // Python
		".js",      // JavaScript
		".ts",      // TypeScript
		".jsx",     // JSX
		".tsx",     // TSX
		".cpp",     // C++
		".cc",      // C++
		".cxx",     // C++
		".c",       // C
		".h",       // C/C++ header
		".hpp",     // C++ header
		".cs",      // C#
		".go",      // Go
		".php",     // PHP
		".rb",      // Ruby
		".swift",   // Swift
		".kt",      // Kotlin
		".scala",   // Scala
		".groovy"   // Groovy
	};

	/**
	 * Create an ASCA scanner for a project.
	 *
	 * @param project Eclipse project
	 */
	public AscaScannerService(IProject project) {
		super(project);
	}

	/**
	 * Check if file is source code that ASCA can scan.
	 *
	 * @param filePath File path to check
	 * @return true if file is a supported source code file
	 */
	@Override
	protected boolean isFileTypeSupported(String filePath) {
		if (filePath == null) {
			return false;
		}

		String lowerPath = filePath.toLowerCase();

		// Check file extensions
		for (String ext : SUPPORTED_EXTENSIONS) {
			if (lowerPath.endsWith(ext)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Execute ASCA scan on a source file.
	 *
	 * Generates realistic mock ASCA findings for demonstration.
	 * In production, this would call CxWrapperFactory to execute actual scan.
	 *
	 * @param filePath Source file to scan
	 * @return Mock ASCA vulnerabilities
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing ASCA scan on: " + filePath);

		// Generate realistic mock ASCA findings for demo
		List<MockAscaVulnerability> results = new ArrayList<>();

		String lowerPath = filePath.toLowerCase();

		// SQL Injection vulnerability
		results.add(new MockAscaVulnerability("SQL_INJECTION",
			"SQL Injection via unsanitized user input",
			"User-supplied input is directly concatenated into SQL query",
			"CRITICAL", 45, 15, "Use parameterized queries or prepared statements"));

		// XSS vulnerability
		results.add(new MockAscaVulnerability("REFLECTED_XSS",
			"Reflected XSS in HTML output",
			"User input is echoed back to HTML without encoding",
			"HIGH", 67, 28,
			"Use HTML encoding or templating engine with auto-escaping"));

		// Path traversal
		results.add(new MockAscaVulnerability("PATH_TRAVERSAL",
			"Path Traversal via unsanitized file path",
			"File path constructed from user input without validation",
			"HIGH", 82, 12,
			"Validate and sanitize file paths, use allowlist of valid paths"));

		// Insecure deserialization
		if (lowerPath.contains(".java")) {
			results.add(new MockAscaVulnerability("INSECURE_DESERIALIZATION",
				"Insecure Object Deserialization",
				"Untrusted data is deserialized without validation",
				"CRITICAL", 95, 20,
				"Never deserialize untrusted data; use JSON instead of Java serialization"));
		}

		// Hardcoded credentials
		results.add(new MockAscaVulnerability("HARDCODED_SECRET",
			"Hardcoded Credentials Found",
			"API key or password is hardcoded in source code",
			"CRITICAL", 120, 45,
			"Move credentials to configuration files or environment variables"));

		CxLogger.info(logTag + " ✓ Generated " + results.size() +
			" mock ASCA vulnerabilities");
		return results;
	}

	/**
	 * Adapt ASCA scan results to ScanIssue model.
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
		int id = 2000;

		for (Object result : results) {
			if (!(result instanceof MockAscaVulnerability)) {
				continue;
			}

			MockAscaVulnerability vuln = (MockAscaVulnerability) result;
			ScanIssue issue = new ScanIssue();

			issue.setScanIssueId("ASCA-" + id++);
			issue.setTitle(vuln.rule_name);
			issue.setDescription(vuln.description + " " + vuln.detailed_description);
			issue.setSeverity(vuln.severity);
			issue.setRuleId(Integer.parseInt(vuln.rule_id.replaceAll("[^0-9]", "")));
			issue.setProblematicLineNumber(vuln.line_number);
			issue.setRemediationAdvise(vuln.remediation);
			issue.setScanEngine(
				com.checkmarx.eclipse.devassist.backend.scanner.ScannerService.ScannerType.ASCA);

			// Add location information
			com.checkmarx.eclipse.devassist.ui.findings.model.Location location =
				new com.checkmarx.eclipse.devassist.ui.findings.model.Location();
			location.setLine(vuln.line_number);
			location.setStartColumn(1);
			location.setEndColumn(vuln.column_number);
			issue.getLocations().add(location);

			issues.add(issue);
		}

		return issues;
	}

	/**
	 * Mock ASCA vulnerability data for demo.
	 */
	static class MockAscaVulnerability {
		String rule_id;
		String rule_name;
		String description;
		String detailed_description;
		String severity;
		int line_number;
		int column_number;
		String remediation;

		MockAscaVulnerability(String rule_id, String rule_name, String desc,
			String severity, int line, int col, String remediation) {
			this.rule_id = rule_id;
			this.rule_name = rule_name;
			this.description = rule_name;
			this.detailed_description = desc;
			this.severity = severity;
			this.line_number = line;
			this.column_number = col;
			this.remediation = remediation;
		}
	}

	@Override
	public String getDisplayName() {
		return "Application Security Code Analysis";
	}

	@Override
	public ScannerType getScannerType() {
		return ScannerType.ASCA;
	}

	/**
	 * Get the language name for a source file.
	 *
	 * @param filePath Source file path
	 * @return Language name
	 */
	public String getLanguageName(String filePath) {
		String lower = filePath.toLowerCase();

		if (lower.endsWith(".java")) return "Java";
		if (lower.endsWith(".py")) return "Python";
		if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "JavaScript";
		if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "TypeScript";
		if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx"))
			return "C++";
		if (lower.endsWith(".c")) return "C";
		if (lower.endsWith(".h") || lower.endsWith(".hpp")) return "Header";
		if (lower.endsWith(".cs")) return "C#";
		if (lower.endsWith(".go")) return "Go";
		if (lower.endsWith(".php")) return "PHP";
		if (lower.endsWith(".rb")) return "Ruby";
		if (lower.endsWith(".swift")) return "Swift";
		if (lower.endsWith(".kt")) return "Kotlin";
		if (lower.endsWith(".scala")) return "Scala";
		if (lower.endsWith(".groovy")) return "Groovy";

		return "Unknown";
	}
}
