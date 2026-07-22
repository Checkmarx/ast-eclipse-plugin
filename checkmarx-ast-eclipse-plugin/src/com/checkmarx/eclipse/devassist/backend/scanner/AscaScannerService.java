package com.checkmarx.eclipse.devassist.backend.scanner;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.devassist.ui.findings.model.Location;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanEngine;
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
	 * Execute ASCA scan using real Checkmarx server API via reflection.
	 *
	 * Uses reflection to call CxWrapperFactory at runtime (available in classpath
	 * even though not available at compile-time due to Tycho limitations).
	 *
	 * @param filePath Source file to scan
	 * @return Real ScanResult from Checkmarx server, or null if API unavailable
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing ASCA scan on: " + filePath);

		String tempFilePath = null;
		try {
			// Read actual file content
			String fileContent = readFileContent(filePath);
			if (fileContent == null) {
				CxLogger.warning(logTag + " Could not read file: " + filePath);
				return null;
			}

			// Create temp file for ASCA
			tempFilePath = createTempFile(filePath, fileContent);
			if (tempFilePath == null) {
				CxLogger.warning(logTag + " Failed to create temp file");
				return null;
			}

			CxLogger.info(logTag + " Calling real ASCA API via reflection...");

			// Call real Checkmarx API via reflection
			Object result = callAscaApiViaReflection(tempFilePath);
			if (result == null) {
				CxLogger.warning(logTag + " ASCA API returned null");
				return null;
			}

			CxLogger.info(logTag + " ✓ Got REAL results from server");
			return result;

		} catch (Exception e) {
			CxLogger.error(logTag + " Error: " + e.getMessage(), e);
			throw e;
		} finally {
			if (tempFilePath != null) {
				deleteTempFile(tempFilePath);
			}
		}
	}

	/**
	 * Call ASCA scan via reflection on CxWrapper from ast-cli-java-wrapper JAR.
	 * Works around Tycho's compile-time dependency issues.
	 */
	private Object callAscaApiViaReflection(String filePath) {
		try {
			// Load CxWrapper class (the actual wrapper in the JAR, not CxWrapperFactory)
			Class<?> wrapperClass = Class.forName("com.checkmarx.ast.wrapper.CxWrapper");
			Class<?> configClass = Class.forName("com.checkmarx.ast.wrapper.CxConfig");
			Class<?> configBuilderClass = Class.forName("com.checkmarx.ast.wrapper.CxConfig$CxConfigBuilder");

			// Build CxConfig: CxConfig.builder().agentName("Eclipse").build()
			Method builderMethod = configClass.getMethod("builder");
			Object configBuilder = builderMethod.invoke(null);

			Method agentMethod = configBuilderClass.getMethod("agentName", String.class);
			agentMethod.invoke(configBuilder, "Eclipse");

			Method buildMethod = configBuilderClass.getMethod("build");
			Object config = buildMethod.invoke(configBuilder);

			// Create CxWrapper: new CxWrapper(config)
			Object wrapper = wrapperClass.getConstructor(configClass).newInstance(config);

			// Call ScanAsca(filePath, true, "Eclipse", null)
			Method scanMethod = wrapperClass.getMethod("ScanAsca", String.class, boolean.class, String.class, String.class);
			Object scanResult = scanMethod.invoke(wrapper, filePath, true, "Eclipse", null);

			CxLogger.info(logTag + " ✓ Called real ASCA API successfully");
			return scanResult;

		} catch (ClassNotFoundException e) {
			CxLogger.warning(logTag + " CxWrapper not available in classpath: " + e.getMessage());
			return null;
		} catch (Exception e) {
			CxLogger.error(logTag + " Reflection error calling ASCA API: " + e.getMessage(), e);
			return null;
		}
	}

	private String readFileContent(String filePath) {
		try {
			return new String(Files.readAllBytes(Paths.get(filePath)));
		} catch (IOException e) {
			CxLogger.warning(logTag + " Failed to read file: " + e.getMessage());
			return null;
		}
	}

	private String createTempFile(String originalPath, String fileContent) {
		try {
			Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
			String fileName = Paths.get(originalPath).getFileName().toString();
			Path tempFilePath = tempDir.resolve("asca_" + System.nanoTime() + "_" + fileName);
			Files.write(tempFilePath, fileContent.getBytes());
			return tempFilePath.toAbsolutePath().toString();
		} catch (IOException e) {
			CxLogger.warning(logTag + " Failed to create temp file: " + e.getMessage());
			return null;
		}
	}

	private void deleteTempFile(String tempFilePath) {
		try {
			Files.deleteIfExists(Paths.get(tempFilePath));
		} catch (IOException e) {
			CxLogger.warning(logTag + " Failed to delete temp file: " + e.getMessage());
		}
	}

	/**
	 * Adapt ASCA scan results to ScanIssue model.
	 *
	 * Handles both real ScanResult from API and legacy mock data.
	 *
	 * @param rawResults Raw results from executeNativeScanner() - can be ScanResult or List<MockAscaVulnerability>
	 * @param filePath Original file path being scanned (for stable ID generation)
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults, String filePath) {
		List<ScanIssue> issues = new ArrayList<>();

		if (rawResults == null) {
			return issues;
		}

		// Try to adapt as real ScanResult first
		if (isRealScanResult(rawResults)) {
			return adaptRealScanResult(rawResults, filePath);
		}

		// Fall back to mock data if available
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

			issue.setScanIssueId("ASCA-" + id);
			issue.setTitle(vuln.rule_name);
			issue.setDescription(vuln.description + " " + vuln.detailed_description);
			issue.setSeverity(vuln.severity);
			issue.setRuleId(id);
			issue.setProblematicLineNumber(vuln.line_number);
			issue.setRemediationAdvise(vuln.remediation);
			issue.setScanEngine(ScanEngine.ASCA);
			id++;

			Location location = new Location();
			location.setLine(vuln.line_number);
			location.setStartIndex(0);
			location.setEndIndex(vuln.column_number);
			issue.getLocations().add(location);

			issues.add(issue);
		}

		return issues;
	}

	private boolean isRealScanResult(Object obj) {
		return obj != null && obj.getClass().getSimpleName().equals("ScanResult");
	}

	private List<ScanIssue> adaptRealScanResult(Object scanResult, String filePath) {
		List<ScanIssue> issues = new ArrayList<>();
		try {
			CxLogger.info(logTag + " adaptRealScanResult: scanResult type = " + scanResult.getClass().getName());

			Method getScanDetails = scanResult.getClass().getMethod("getScanDetails");
			List<?> scanDetails = (List<?>) getScanDetails.invoke(scanResult);

			if (scanDetails == null || scanDetails.isEmpty()) {
				CxLogger.info(logTag + " No scan details in real result");
				return issues;
			}

			CxLogger.info(logTag + " Found " + scanDetails.size() + " scan details - grouping by line");

			// **Extract REAL filename from filePath (not scanner's internal filename)**
			// Scanner may use temp names like "asca_123456789_HelloClass.java"
			// but we need the actual file name for stable ID generation
			String actualFileName = "Unknown";
			if (filePath != null && !filePath.isEmpty()) {
				actualFileName = new java.io.File(filePath).getName();
			}

			// **JetBrains Pattern: Group scan details by line number**
			java.util.Map<Integer, java.util.List<Object>> groupedByLine = new java.util.HashMap<>();

			for (Object detail : scanDetails) {
				Integer lineNumber = getDetailProperty(detail, "getLine", Integer.class);
				int line = lineNumber != null ? lineNumber : 0;

				groupedByLine.computeIfAbsent(line, k -> new ArrayList<>()).add(detail);
			}

			CxLogger.info(logTag + " Grouped " + scanDetails.size() + " details into " + groupedByLine.size() + " line groups");

			// **Create ONE ScanIssue per line group**
			for (java.util.List<Object> detailsOnLine : groupedByLine.values()) {
				if (detailsOnLine.isEmpty()) {
					continue;
				}

				try {
					// Create base ScanIssue from first (highest severity) detail on this line
					Object firstDetail = detailsOnLine.get(0);
					String ruleName = getDetailProperty(firstDetail, "getRuleName", String.class);
					String description = getDetailProperty(firstDetail, "getDescription", String.class);
					String severity = getDetailProperty(firstDetail, "getSeverity", String.class);
					Integer lineNumber = getDetailProperty(firstDetail, "getLine", Integer.class);
					Integer ruleID = getDetailProperty(firstDetail, "getRuleID", Integer.class);
					String remediationAdvise = getDetailProperty(firstDetail, "getRemediationAdvise", String.class);
					Integer columnLength = getDetailProperty(firstDetail, "getLength", Integer.class);

					ScanIssue issue = new ScanIssue();

					// **Generate content-based ID using ACTUAL filename (JetBrains pattern)**
					// Use actualFileName (extracted from filePath) instead of scanner's internal filename
					// This ensures IDs remain stable across re-scans
					String scanIssueId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
						lineNumber != null ? lineNumber : 0,
						(ruleID != null ? ruleID : 0) + (ruleName != null ? ruleName : ""),
						actualFileName
					);

					issue.setScanIssueId(scanIssueId);
					issue.setDescription(description);
					issue.setSeverity(severity != null ? severity : "MEDIUM");
					issue.setRuleId(ruleID != null ? ruleID : 0);
					issue.setProblematicLineNumber(lineNumber != null ? lineNumber : 0);
					issue.setRemediationAdvise(remediationAdvise);
					issue.setScanEngine(ScanEngine.ASCA);

					// Extract location from first detail
					List<?> locations = getDetailProperty(firstDetail, "getLocations", List.class);

					if (locations != null && !locations.isEmpty()) {
						Object locObj = locations.get(0);
						Integer locLine = getLocationProperty(locObj, "getLine", Integer.class);
						Integer locStart = getLocationProperty(locObj, "getStartIndex", Integer.class);
						Integer locEnd = getLocationProperty(locObj, "getEndIndex", Integer.class);

						Location location = new Location(
							locLine != null ? locLine : (lineNumber != null ? lineNumber : 0),
							locStart != null ? locStart : 0,
							locEnd != null ? locEnd : (columnLength != null ? columnLength : 50)
						);
						issue.getLocations().add(location);
					} else {
						// Fallback: Use line-based positioning
						Location location = new Location();
						location.setLine(lineNumber != null ? lineNumber : 0);
						location.setStartIndex(0);
						location.setEndIndex(columnLength != null ? columnLength : 50);
						issue.getLocations().add(location);
					}

					// **Add ALL vulnerabilities on this line to the SAME ScanIssue**
					for (int i = 0; i < detailsOnLine.size(); i++) {
						Object detail = detailsOnLine.get(i);

						String vulnRuleName = getDetailProperty(detail, "getRuleName", String.class);
						String vulnDescription = getDetailProperty(detail, "getDescription", String.class);
						String vulnSeverity = getDetailProperty(detail, "getSeverity", String.class);
						Integer vulnRuleID = getDetailProperty(detail, "getRuleID", Integer.class);
						String vulnRemediationAdvise = getDetailProperty(detail, "getRemediationAdvise", String.class);

						// Create Vulnerability object
						com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability vulnerability =
							new com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability();

						// First vulnerability gets scanIssueId, others get unique IDs
						String vulnerabilityId;
						if (i == 0) {
							vulnerabilityId = scanIssueId;
						} else {
							// Use actualFileName for stable ID generation
							vulnerabilityId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
								lineNumber != null ? lineNumber : 0,
								(vulnRuleID != null ? vulnRuleID : 0) + (vulnRuleName != null ? vulnRuleName : ""),
								actualFileName
							);
						}

						vulnerability.setVulnerabilityId(vulnerabilityId);
						vulnerability.setTitle(vulnRuleName != null ? vulnRuleName : "Unknown ASCA Issue");
						vulnerability.setDescription(vulnDescription);
						vulnerability.setSeverity(vulnSeverity != null ? vulnSeverity : "MEDIUM");
						vulnerability.setCve(vulnRuleName);

						issue.getVulnerabilities().add(vulnerability);

						CxLogger.info(logTag + " Added vulnerability: " + vulnRuleName + " (id: " + vulnerabilityId + ")");
					}

					// **Update title based on vulnerability count (JetBrains pattern)**
					if (issue.getVulnerabilities().size() == 1) {
						issue.setTitle(issue.getVulnerabilities().get(0).getTitle());
					} else if (issue.getVulnerabilities().size() > 1) {
						issue.setTitle(issue.getVulnerabilities().size() + " ASCA issues");
					} else {
						issue.setTitle(ruleName != null ? ruleName : "Unknown ASCA Issue");
					}

					issues.add(issue);
					CxLogger.info(logTag + " ✓ Created grouped ScanIssue on line " + lineNumber +
						" with " + issue.getVulnerabilities().size() + " vulnerabilities, ID=" + scanIssueId);

				} catch (Exception e) {
					CxLogger.error(logTag + " Error adapting ASCA group: " + e.getMessage(), e);
				}
			}

			CxLogger.info(logTag + " ✓ Adapted " + issues.size() + " grouped ASCA issues from " + scanDetails.size() + " details");

		} catch (Exception e) {
			CxLogger.error(logTag + " Error adapting real scan result: " + e.getMessage(), e);
		}
		return issues;
	}

	@SuppressWarnings("unchecked")
	private <T> T getDetailProperty(Object detail, String methodName, Class<T> returnType) {
		try {
			Method method = detail.getClass().getMethod(methodName);
			return (T) method.invoke(detail);
		} catch (Exception e) {
			CxLogger.warning(logTag + " Could not get property " + methodName + ": " + e.getMessage());
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T getLocationProperty(Object location, String methodName, Class<T> returnType) {
		try {
			Method method = location.getClass().getMethod(methodName);
			return (T) method.invoke(location);
		} catch (Exception e) {
			CxLogger.warning(logTag + " Could not get location property " + methodName + ": " + e.getMessage());
			return null;
		}
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
