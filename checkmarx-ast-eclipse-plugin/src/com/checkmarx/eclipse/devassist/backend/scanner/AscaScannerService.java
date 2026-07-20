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
	 * In a real implementation, this would:
	 * 1. Parse the source code
	 * 2. Build abstract syntax tree (AST)
	 * 3. Run data flow analysis
	 * 4. Check for vulnerability patterns
	 * 5. Return findings with line numbers and code locations
	 *
	 * For now, returns empty list (Phase 3 will integrate with CxWrapperFactory).
	 *
	 * @param filePath Source file to scan
	 * @return Raw scanner results
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing ASCA scan on: " + filePath);

		// TODO: Phase 3 - Call CxWrapperFactory to execute actual scan
		// For now, return placeholder
		return new ArrayList<Object>();
	}

	/**
	 * Adapt ASCA scan results to ScanIssue model.
	 *
	 * Converts source code vulnerabilities to standardized ScanIssue objects
	 * with detailed location information, code flow, remediation, etc.
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults) {
		List<ScanIssue> issues = new ArrayList<>();

		// TODO: Phase 3 - Parse raw results and create ScanIssue objects
		// Example:
		// for (AscaVulnerability vuln : (List<AscaVulnerability>) rawResults) {
		//     ScanIssue issue = new ScanIssue();
		//     issue.setScanIssueId(vuln.getId());
		//     issue.setTitle(vuln.getRuleName());
		//     issue.setSeverity(vuln.getSeverity());
		//     issue.setDescription(vuln.getDescription());
		//     issue.setRuleId(vuln.getRuleId());
		//     issue.setProblematicLineNumber(vuln.getLineNumber());
		//     issue.setRemediationAdvise(vuln.getRemediationAdvice());
		//
		//     // Add code locations with ranges
		//     for (CodeLocation loc : vuln.getLocations()) {
		//         Location location = new Location();
		//         location.setLine(loc.getLineNumber());
		//         location.setStartColumn(loc.getStartColumn());
		//         location.setEndColumn(loc.getEndColumn());
		//         location.setFileName(filePath);
		//         issue.getLocations().add(location);
		//     }
		//
		//     issues.add(issue);
		// }

		return issues;
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
