package com.checkmarx.eclipse.devassist.backend.scanner;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.devassist.ui.findings.model.Location;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanEngine;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Scanner for Infrastructure as Code (IaC) misconfigurations.
 *
 * Scans infrastructure configuration files:
 * - Terraform (.tf)
 * - CloudFormation (*.yaml, *.json, *.yaml.template)
 * - Kubernetes (*.yaml, *.yml)
 * - AWS CDK (*.ts, *.js)
 * - Ansible (*.yaml, *.yml)
 * - Helm (*.yaml, *.yml)
 * - HCL (*.hcl)
 * - OpenTofu (*.tf)
 *
 * Detects:
 * - Overly permissive security groups
 * - Unencrypted storage
 * - Missing authentication
 * - Exposed credentials in configurations
 * - Network misconfiguration
 * - Insecure default settings
 * - etc.
 */
public class IacScannerService extends BaseScannerService {

	private static final String[] IaC_FILE_PATTERNS = {
		// Terraform
		".tf", ".tfvars",
		// CloudFormation
		".template", ".yaml", ".yml", ".json",
		// Kubernetes
		"-deployment.yaml", "-service.yaml", "-config.yaml",
		// Ansible
		"-playbook.yaml", "-playbook.yml",
		// Helm
		"values.yaml", "values.yml", "Chart.yaml",
		// HCL
		".hcl"
	};

	/**
	 * Create an IaC scanner for a project.
	 *
	 * @param project Eclipse project
	 */
	public IacScannerService(IProject project) {
		super(project);
	}

	/**
	 * Check if file is an IaC configuration file.
	 *
	 * Uses relaxed detection to match JetBrains pattern:
	 * - Accepts all .tf, .hcl, .template files
	 * - Accepts all .yaml, .yml, .json files (widely used for IaC)
	 * - Excludes only known non-IaC files (manifests, build configs)
	 *
	 * @param filePath File path to check
	 * @return true if file is an IaC file
	 */
	@Override
	protected boolean isFileTypeSupported(String filePath) {
		if (filePath == null) {
			return false;
		}

		String lowerPath = filePath.toLowerCase();
		String fileName = new java.io.File(filePath).getName().toLowerCase();

		// Exclude known non-IaC files
		if (fileName.equals("package.json") || fileName.equals("pom.xml") ||
			fileName.equals("go.mod") || fileName.equals("requirements.txt") ||
			fileName.equals("gemfile") || fileName.equals("cargo.toml") ||
			fileName.equals("pipfile") || fileName.equals("build.gradle") ||
			fileName.equals("settings.gradle")) {
			return false;
		}

		// Accept IaC-specific extensions
		if (lowerPath.endsWith(".tf") || lowerPath.endsWith(".tfvars") ||
			lowerPath.endsWith(".hcl") || lowerPath.endsWith(".template")) {
			return true;
		}

		// Accept YAML and JSON (widely used for IaC: CloudFormation, Kubernetes, Ansible, Helm, Docker Compose)
		if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml") ||
			lowerPath.endsWith(".json")) {
			return true;
		}

		return false;
	}

	/**
	 * Execute IaC scan using real Checkmarx server API via reflection.
	 *
	 * @param filePath IaC configuration file to scan
	 * @return Real IaC scan results from Checkmarx server
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing IaC scan on: " + filePath);

		String tempFilePath = null;
		try {
			// Read actual file content
			String fileContent = readFileContent(filePath);
			if (fileContent == null) {
				CxLogger.warning(logTag + " Could not read file: " + filePath);
				return null;
			}

			// Create temp file for IaC scanner
			tempFilePath = createTempFile(filePath, fileContent);
			if (tempFilePath == null) {
				CxLogger.warning(logTag + " Failed to create temp file");
				return null;
			}

			CxLogger.info(logTag + " Calling real IaC API via reflection...");

			// Call real Checkmarx API via reflection
			Object result = callIacApiViaReflection(tempFilePath);
			if (result == null) {
				CxLogger.warning(logTag + " IaC API returned null");
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
	 * Call IaC scan via reflection on CxWrapper.
	 */
	private Object callIacApiViaReflection(String filePath) {
		try {
			Class<?> wrapperClass = Class.forName("com.checkmarx.ast.wrapper.CxWrapper");
			Class<?> configClass = Class.forName("com.checkmarx.ast.wrapper.CxConfig");
			Class<?> configBuilderClass = Class.forName("com.checkmarx.ast.wrapper.CxConfig$CxConfigBuilder");

			Method builderMethod = configClass.getMethod("builder");
			Object configBuilder = builderMethod.invoke(null);

			Method agentMethod = configBuilderClass.getMethod("agentName", String.class);
			agentMethod.invoke(configBuilder, "Eclipse");

			Method buildMethod = configBuilderClass.getMethod("build");
			Object config = buildMethod.invoke(configBuilder);

			Object wrapper = wrapperClass.getConstructor(configClass).newInstance(config);

			// Call iacRealtimeScan(sourcePath, containerTool, ignoredFilePath)
			Method scanMethod = wrapperClass.getMethod("iacRealtimeScan", String.class, String.class, String.class);
			Object scanResult = scanMethod.invoke(wrapper, filePath, "", "");

			CxLogger.info(logTag + " ✓ Called real IaC API successfully");
			return scanResult;

		} catch (ClassNotFoundException e) {
			CxLogger.warning(logTag + " CxWrapper not available in classpath: " + e.getMessage());
			return null;
		} catch (Exception e) {
			CxLogger.error(logTag + " Reflection error calling IaC API: " + e.getMessage(), e);
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
			Path tempFilePath = tempDir.resolve("iac_" + System.nanoTime() + "_" + fileName);
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
	 * Adapt IaC scan results to ScanIssue model.
	 *
	 * Handles both real results from API and legacy mock data.
	 * Implements JetBrains pattern: group by line, generate stable IDs.
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @param filePath Original file path being scanned (for stable ID generation)
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults, String filePath) {
		List<ScanIssue> issues = new ArrayList<>();

		if (rawResults == null) {
			return issues;
		}

		// Try to adapt as real IaC result first
		if (isRealIacResult(rawResults)) {
			return adaptRealIacResult(rawResults, filePath);
		}

		// Fall back to mock data
		if (!(rawResults instanceof List)) {
			return issues;
		}

		List<?> results = (List<?>) rawResults;

		// Extract REAL filename from filePath
		String actualFileName = "Unknown";
		if (filePath != null && !filePath.isEmpty()) {
			actualFileName = new java.io.File(filePath).getName();
		}

		// JetBrains Pattern: Group mock data by line number
		Map<Integer, List<Object>> groupedByLine = new HashMap<>();

		for (Object result : results) {
			if (!(result instanceof MockIacMisconfiguration)) {
				continue;
			}

			MockIacMisconfiguration config = (MockIacMisconfiguration) result;
			int line = config.line_number;

			groupedByLine.computeIfAbsent(line, k -> new ArrayList<>()).add(config);
		}

		// Create ONE ScanIssue per line group
		for (Map.Entry<Integer, List<Object>> entry : groupedByLine.entrySet()) {
			Integer line = entry.getKey();
			List<Object> configsOnLine = entry.getValue();

			if (configsOnLine.isEmpty()) {
				continue;
			}

			try {
				// Create base ScanIssue from first config on this line
				Object firstConfig = configsOnLine.get(0);
				MockIacMisconfiguration firstMock = (MockIacMisconfiguration) firstConfig;

				// Generate content-based ID using ACTUAL filename (JetBrains pattern)
				String scanIssueId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
					line,
					firstMock.title,
					actualFileName
				);

				ScanIssue issue = new ScanIssue();
				issue.setScanIssueId(scanIssueId);
				issue.setProblematicLineNumber(line);
				issue.setScanEngine(ScanEngine.IAC);

				// Add ALL misconfigurations on this line to the SAME ScanIssue
				for (int i = 0; i < configsOnLine.size(); i++) {
					Object config = configsOnLine.get(i);
					MockIacMisconfiguration mockConfig = (MockIacMisconfiguration) config;

					com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability vulnerability =
						new com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability();

					// First vulnerability gets scanIssueId, others get unique IDs
					String vulnerabilityId;
					if (i == 0) {
						vulnerabilityId = scanIssueId;
					} else {
						vulnerabilityId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
							line,
							mockConfig.title,
							actualFileName
						);
					}

					vulnerability.setVulnerabilityId(vulnerabilityId);
					vulnerability.setTitle(mockConfig.title);
					vulnerability.setDescription(mockConfig.description + " - " + mockConfig.details);
					vulnerability.setSeverity(com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(mockConfig.severity));

					issue.getVulnerabilities().add(vulnerability);
				}

				// Dynamic title based on vulnerability count (JetBrains pattern)
				if (issue.getVulnerabilities().size() == 1) {
					issue.setTitle(issue.getVulnerabilities().get(0).getTitle());
				} else if (issue.getVulnerabilities().size() > 1) {
					issue.setTitle(issue.getVulnerabilities().size() + " IaC issues");
				}

				// Set remediation from first config
				issue.setRemediationAdvise(firstMock.remediation);
				issue.setSeverity(firstMock.severity);

				Location location = new Location();
				location.setLine(line);
				location.setStartIndex(0);
				location.setEndIndex(0);
				issue.getLocations().add(location);

				issues.add(issue);

			} catch (Exception e) {
				CxLogger.error(logTag + " Error adapting IaC mock group: " + e.getMessage(), e);
			}
		}

		return issues;
	}

	private boolean isRealIacResult(Object obj) {
		return obj != null && (obj.getClass().getSimpleName().equals("IacRealtimeResults") ||
			obj.getClass().getSimpleName().equals("IacScanResults"));
	}

	private List<ScanIssue> adaptRealIacResult(Object iacResult, String filePath) {
		List<ScanIssue> issues = new ArrayList<>();
		try {
			CxLogger.info(logTag + " adaptRealIacResult: result type = " + iacResult.getClass().getName());

			// Get issues list from result (JetBrains: getResults())
			Method getIssues = null;
			try {
				getIssues = iacResult.getClass().getMethod("getResults");
			} catch (Exception e) {
				try {
					getIssues = iacResult.getClass().getMethod("getMisconfigurations");
				} catch (Exception e2) {
					try {
						getIssues = iacResult.getClass().getMethod("getFindings");
					} catch (Exception e3) {
						CxLogger.warning(logTag + " Could not find getResults/getMisconfigurations/getFindings method in IaC result");
						return issues;
					}
				}
			}

			Object iacData = getIssues.invoke(iacResult);
			if (iacData == null) {
				CxLogger.info(logTag + " No issues found in IaC result");
				return issues;
			}

			if (!(iacData instanceof List)) {
				CxLogger.warning(logTag + " IaC data is not a List, it's: " + iacData.getClass().getName());
				return issues;
			}

			List<?> iacIssuesList = (List<?>) iacData;
			CxLogger.info(logTag + " Found " + iacIssuesList.size() + " IaC issues - grouping by location");

			// JetBrains Pattern: Flatten issues with their locations, then group
			// Step 1: Create IssueLocationEntry pairs (issue + each location)
			List<IssueLocationEntry> allEntries = new ArrayList<>();

			for (Object issue : iacIssuesList) {
				if (issue == null) {
					continue;
				}

				// Get locations from issue
				List<?> locations = getIacProperty(issue, "getLocations", List.class);
				if (locations == null || locations.isEmpty()) {
					CxLogger.warning(logTag + " Issue has no locations, skipping");
					continue;
				}

				// Create entry for each location
				for (Object location : locations) {
					if (location != null) {
						allEntries.add(new IssueLocationEntry(issue, location));
					}
				}
			}

			if (allEntries.isEmpty()) {
				CxLogger.info(logTag + " No valid issue-location entries found after flattening");
				return issues;
			}

			// Step 2: Sort by severity (highest first)
			allEntries.sort((e1, e2) -> {
				String sev1 = getIacProperty(e1.issue, "getSeverity", String.class);
				String sev2 = getIacProperty(e2.issue, "getSeverity", String.class);
				// Simple severity comparison: CRITICAL > HIGH > MEDIUM > LOW > INFO
				return severityToInt(sev2) - severityToInt(sev1);
			});

			// Step 3: Group by location key (filePath + line + startIndex + endIndex)
			Map<String, List<IssueLocationEntry>> groupedByLocation = new LinkedHashMap<>();

			for (IssueLocationEntry entry : allEntries) {
				String groupKey = getGroupingKey(entry);
				groupedByLocation.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(entry);
			}

			CxLogger.info(logTag + " Grouped " + allEntries.size() + " issue-location entries into " + groupedByLocation.size() + " location groups");

			// Step 4: Create ONE ScanIssue per group
			for (List<IssueLocationEntry> groupEntries : groupedByLocation.values()) {
				if (groupEntries.isEmpty()) {
					continue;
				}

				try {
					ScanIssue scanIssue = createScanIssueFromGroup(groupEntries);
					issues.add(scanIssue);

					CxLogger.info(logTag + " ✓ Created grouped ScanIssue with " + scanIssue.getVulnerabilities().size() +
						" vulnerabilities, ID=" + scanIssue.getScanIssueId());

				} catch (Exception e) {
					CxLogger.error(logTag + " Error creating ScanIssue from group: " + e.getMessage(), e);
				}
			}

			CxLogger.info(logTag + " ✓ Adapted " + issues.size() + " grouped IaC issues from " + allEntries.size() + " issue-location entries");

		} catch (Exception e) {
			CxLogger.error(logTag + " Error adapting real IaC result: " + e.getMessage(), e);
		}
		return issues;
	}

	/**
	 * Internal class to pair an issue with its location (JetBrains pattern)
	 */
	private static class IssueLocationEntry {
		final Object issue;
		final Object location;

		IssueLocationEntry(Object issue, Object location) {
			this.issue = issue;
			this.location = location;
		}
	}

	/**
	 * Generate grouping key: filePath + line + startIndex + endIndex (JetBrains pattern)
	 */
	private String getGroupingKey(IssueLocationEntry entry) {
		String issueFilePath = getIacProperty(entry.issue, "getFilePath", String.class);
		Integer line = getIacProperty(entry.location, "getLine", Integer.class);
		Integer startIndex = getIacProperty(entry.location, "getStartIndex", Integer.class);
		Integer endIndex = getIacProperty(entry.location, "getEndIndex", Integer.class);

		return (issueFilePath != null ? issueFilePath : "unknown") + ":" +
			(line != null ? line : 0) + ":" +
			(startIndex != null ? startIndex : 0) + ":" +
			(endIndex != null ? endIndex : 0);
	}

	/**
	 * Create one ScanIssue from a group of IssueLocationEntry (JetBrains pattern)
	 */
	private ScanIssue createScanIssueFromGroup(List<IssueLocationEntry> groupEntries) {
		ScanIssue scanIssue = new ScanIssue();

		// Get properties from first entry (highest severity)
		IssueLocationEntry firstEntry = groupEntries.get(0);
		Object firstIssue = firstEntry.issue;

		String title = getIacProperty(firstIssue, "getTitle", String.class);
		if (title == null) {
			title = getIacProperty(firstIssue, "getRuleName", String.class);
		}
		if (title == null) {
			title = "IaC Misconfiguration";
		}

		String description = getIacProperty(firstIssue, "getDescription", String.class);
		String severity = getIacProperty(firstIssue, "getSeverity", String.class);
		String similarityId = getIacProperty(firstIssue, "getSimilarityId", String.class);
		Integer line = getIacProperty(firstEntry.location, "getLine", Integer.class);
		Integer startIndex = getIacProperty(firstEntry.location, "getStartIndex", Integer.class);
		Integer endIndex = getIacProperty(firstEntry.location, "getEndIndex", Integer.class);

		// Set dynamic title based on group size
		if (groupEntries.size() > 1) {
			scanIssue.setTitle(groupEntries.size() + " IaC issues");
		} else {
			scanIssue.setTitle(title);
		}

		// Generate ID using: line + title + similarityId (JetBrains pattern)
		String scanIssueId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
			line != null ? line : 0,
			title,
			similarityId != null ? similarityId : title
		);

		scanIssue.setScanIssueId(scanIssueId);
		scanIssue.setDescription(description != null ? description : "Infrastructure misconfiguration detected");
		String normalizedSeverity = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(severity != null ? severity : "Medium");
		scanIssue.setSeverity(normalizedSeverity);
		scanIssue.setProblematicLineNumber(line != null ? line : 0);
		scanIssue.setScanEngine(ScanEngine.IAC);

		// Add all vulnerabilities in this group
		for (int i = 0; i < groupEntries.size(); i++) {
			IssueLocationEntry entry = groupEntries.get(i);
			Object issue = entry.issue;

			String vulnTitle = getIacProperty(issue, "getTitle", String.class);
			if (vulnTitle == null) {
				vulnTitle = getIacProperty(issue, "getRuleName", String.class);
			}
			if (vulnTitle == null) {
				vulnTitle = "IaC Misconfiguration";
			}

			String vulnDescription = getIacProperty(issue, "getDescription", String.class);
			String vulnSeverity = getIacProperty(issue, "getSeverity", String.class);
			String vulnSimilarityId = getIacProperty(issue, "getSimilarityId", String.class);

			com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability vulnerability =
				new com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability();

			// First vulnerability gets scanIssueId, others get unique IDs
			String vulnerabilityId;
			if (i == 0) {
				vulnerabilityId = scanIssueId;
			} else {
				vulnerabilityId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
					line != null ? line : 0,
					vulnTitle,
					vulnSimilarityId != null ? vulnSimilarityId : vulnTitle
				);
			}

			vulnerability.setVulnerabilityId(vulnerabilityId);
			vulnerability.setTitle(vulnTitle);
			vulnerability.setDescription(vulnDescription != null ? vulnDescription : "Infrastructure misconfiguration detected");
			String normalizedVulnSeverity = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(vulnSeverity != null ? vulnSeverity : "Medium");
			vulnerability.setSeverity(normalizedVulnSeverity);

			scanIssue.getVulnerabilities().add(vulnerability);

			CxLogger.info(logTag + " Added vulnerability: " + vulnTitle + " (id: " + vulnerabilityId + ")");
		}

		// Add location from first entry
		Location location = new Location();
		location.setLine((line != null ? line : 0) + 1); // JetBrains adds 1 to line
		location.setStartIndex(startIndex != null ? startIndex : 0);
		location.setEndIndex(endIndex != null ? endIndex : 0);
		scanIssue.getLocations().add(location);

		return scanIssue;
	}

	/**
	 * Convert severity string to int for comparison (higher int = higher severity)
	 */
	private int severityToInt(String severity) {
		if (severity == null) return 0;
		switch (severity.toUpperCase()) {
			case "CRITICAL": return 5;
			case "HIGH": return 4;
			case "MEDIUM": return 3;
			case "LOW": return 2;
			case "INFO": return 1;
			default: return 0;
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T getIacProperty(Object misconfig, String methodName, Class<T> returnType) {
		try {
			Method method = misconfig.getClass().getMethod(methodName);
			return (T) method.invoke(misconfig);
		} catch (Exception e) {
			CxLogger.warning(logTag + " Could not get property " + methodName + ": " + e.getMessage());
			return null;
		}
	}

	/**
	 * Mock IaC misconfiguration data for demo.
	 */
	static class MockIacMisconfiguration {
		String title;
		String description;
		String details;
		String severity;
		int line_number;
		String remediation;

		MockIacMisconfiguration(String title, String desc, String details,
			String severity, int line, String remediation) {
			this.title = title;
			this.description = desc;
			this.details = details;
			this.severity = severity;
			this.line_number = line;
			this.remediation = remediation;
		}
	}

	@Override
	public String getDisplayName() {
		return "Infrastructure as Code";
	}

	@Override
	public ScannerType getScannerType() {
		return ScannerType.IAC;
	}

	/**
	 * Detect IaC framework from file path.
	 *
	 * @param filePath Configuration file path
	 * @return Framework name (Terraform, CloudFormation, Kubernetes, etc.)
	 */
	public String detectFramework(String filePath) {
		String lower = filePath.toLowerCase();

		if (lower.endsWith(".tf") || lower.endsWith(".tfvars")) return "Terraform";
		if (lower.contains("cloudformation")) return "CloudFormation";
		if (lower.contains("kubernetes") || lower.contains("k8s")) return "Kubernetes";
		if (lower.contains("ansible")) return "Ansible";
		if (lower.contains("helm")) return "Helm";
		if (lower.endsWith(".hcl")) return "HCL";

		return "Unknown";
	}
}
