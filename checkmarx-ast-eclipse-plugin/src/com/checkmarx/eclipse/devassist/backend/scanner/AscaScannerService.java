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
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults) {
		List<ScanIssue> issues = new ArrayList<>();

		if (rawResults == null) {
			return issues;
		}

		// Try to adapt as real ScanResult first
		if (isRealScanResult(rawResults)) {
			return adaptRealScanResult(rawResults);
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

	private List<ScanIssue> adaptRealScanResult(Object scanResult) {
		List<ScanIssue> issues = new ArrayList<>();
		try {
			System.out.println(logTag + " adaptRealScanResult: scanResult type = " + scanResult.getClass().getName());
			System.out.println(logTag + " Available methods:");
			for (java.lang.reflect.Method m : scanResult.getClass().getMethods()) {
				if (!m.getName().startsWith("java")) {
					System.out.println(logTag + "   - " + m.getName() + "() returns " + m.getReturnType().getSimpleName());
				}
			}

			Method getScanDetails = scanResult.getClass().getMethod("getScanDetails");
			List<?> scanDetails = (List<?>) getScanDetails.invoke(scanResult);

			if (scanDetails == null || scanDetails.isEmpty()) {
				System.out.println(logTag + " No scan details in real result");
				return issues;
			}

			System.out.println(logTag + " Found " + scanDetails.size() + " scan details");

			int id = 2000;
			for (Object detail : scanDetails) {
				try {
					System.out.println(logTag + "   Adapting detail " + id + "...");

					ScanIssue issue = new ScanIssue();

					// Extract properties using correct method names from ScanDetail
					String ruleName = getDetailProperty(detail, "getRuleName", String.class);
					String description = getDetailProperty(detail, "getDescription", String.class);
					String severity = getDetailProperty(detail, "getSeverity", String.class);
					Integer lineNumber = getDetailProperty(detail, "getLine", Integer.class);
					Integer ruleID = getDetailProperty(detail, "getRuleID", Integer.class);
					String remediationAdvise = getDetailProperty(detail, "getRemediationAdvise", String.class);
					Integer columnLength = getDetailProperty(detail, "getLength", Integer.class);
					String fileName = getDetailProperty(detail, "getFileName", String.class);

					System.out.println(logTag + "     Rule: " + ruleName + " (severity: " + severity + ") at line " + lineNumber);

					issue.setScanIssueId("ASCA-" + id);
					issue.setTitle(ruleName != null ? ruleName : "Unknown ASCA Issue");
					issue.setDescription(description);
					issue.setSeverity(severity != null ? severity : "MEDIUM");
					issue.setRuleId(ruleID != null ? ruleID : id);
					issue.setProblematicLineNumber(lineNumber != null ? lineNumber : 0);
					issue.setRemediationAdvise(remediationAdvise);
					// NOTE: Don't set filePath here - it will be set to the original file path by BaseScannerService
					// issue.setFilePath(fileName);  // This may be a temp file path, so skip it
					issue.setScanEngine(ScanEngine.ASCA);

					// Extract precise location data from RealtimeLocation objects (similar to Secrets scanner)
					List<?> locations = getDetailProperty(detail, "getLocations", List.class);
					CxLogger.info(logTag + "     Locations available: " + (locations != null ? locations.size() : 0));

					if (locations != null && !locations.isEmpty()) {
						int locIdx = 0;
						for (Object locObj : locations) {
							try {
								Integer locLine = getLocationProperty(locObj, "getLine", Integer.class);
								Integer locStart = getLocationProperty(locObj, "getStartIndex", Integer.class);
								Integer locEnd = getLocationProperty(locObj, "getEndIndex", Integer.class);

								CxLogger.info(logTag + "       Location " + locIdx + ": line=" + locLine +
									" [" + locStart + "-" + locEnd + "] (" + (locEnd - locStart) + " chars)");

								Location location = new Location(
									locLine != null ? locLine : (lineNumber != null ? lineNumber : 0),
									locStart != null ? locStart : 0,
									locEnd != null ? locEnd : (columnLength != null ? columnLength : 50)
								);
								issue.getLocations().add(location);
								locIdx++;

							} catch (Exception e) {
								CxLogger.warning(logTag + "       Error extracting location: " + e.getMessage());
							}
						}
					} else {
						// Fallback: Use line-based positioning
						CxLogger.warning(logTag + "     ⚠ No locations from API, using fallback");
						Location location = new Location();
						location.setLine(lineNumber != null ? lineNumber : 0);
						location.setStartIndex(0);
						location.setEndIndex(columnLength != null ? columnLength : 50);
						issue.getLocations().add(location);
					}

					issues.add(issue);
					id++;

					System.out.println(logTag + "     ✓ Added issue: " + ruleName);

				} catch (Exception e) {
					System.err.println(logTag + "   ✗ Error adapting ASCA detail: " + e.getMessage());
					e.printStackTrace();
				}
			}

			System.out.println(logTag + " ✓ Adapted " + issues.size() + " real ASCA issues from server");

		} catch (Exception e) {
			System.err.println(logTag + " Error adapting real scan result: " + e.getMessage());
			e.printStackTrace();
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
