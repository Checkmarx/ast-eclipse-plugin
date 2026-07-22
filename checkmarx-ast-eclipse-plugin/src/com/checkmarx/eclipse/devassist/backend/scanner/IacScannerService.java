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

		// Check for Terraform files
		if (lowerPath.endsWith(".tf") || lowerPath.endsWith(".tfvars")) {
			return true;
		}

		// Check for CloudFormation templates
		if (lowerPath.endsWith(".template") || fileName.contains("cloudformation")) {
			return true;
		}

		// Check for Kubernetes manifests
		if ((lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) &&
			(lowerPath.contains("kubernetes") || lowerPath.contains("k8s") ||
			 fileName.contains("deployment") || fileName.contains("service") ||
			 fileName.contains("config") || fileName.contains("ingress") ||
			 fileName.contains("pod"))) {
			return true;
		}

		// Check for Ansible playbooks
		if ((lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) &&
			(lowerPath.contains("ansible") || fileName.contains("playbook"))) {
			return true;
		}

		// Check for Helm charts
		if ((lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) &&
			(lowerPath.contains("helm") || fileName.contains("values") ||
			 fileName.contains("Chart"))) {
			return true;
		}

		// Check for HCL files
		if (lowerPath.endsWith(".hcl")) {
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

			// Call iacRealtimeScan(filePath)
			Method scanMethod = wrapperClass.getMethod("iacRealtimeScan", String.class);
			Object scanResult = scanMethod.invoke(wrapper, filePath);

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
	 *
	 * @param rawResults Raw results from executeNativeScanner()
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
			return adaptRealIacResult(rawResults);
		}

		// Fall back to mock data
		if (!(rawResults instanceof List)) {
			return issues;
		}

		List<?> results = (List<?>) rawResults;
		int id = 4000;

		for (Object result : results) {
			if (!(result instanceof MockIacMisconfiguration)) {
				continue;
			}

			MockIacMisconfiguration config = (MockIacMisconfiguration) result;
			ScanIssue issue = new ScanIssue();

			issue.setScanIssueId("IAC-" + id++);
			issue.setTitle(config.title);
			issue.setDescription(config.description + " - " + config.details);
			issue.setSeverity(config.severity);
			issue.setProblematicLineNumber(config.line_number);
			issue.setRemediationAdvise(config.remediation);
			issue.setRuleId(Integer.parseInt(
				config.title.replaceAll("[^0-9]", "0" + id).substring(0, 5)));
			issue.setScanEngine(ScanEngine.IAC);

			issues.add(issue);
		}

		return issues;
	}

	private boolean isRealIacResult(Object obj) {
		return obj != null && (obj.getClass().getSimpleName().equals("IacRealtimeResults") ||
			obj.getClass().getSimpleName().equals("IacScanResults"));
	}

	private List<ScanIssue> adaptRealIacResult(Object iacResult) {
		List<ScanIssue> issues = new ArrayList<>();
		try {
			System.out.println(logTag + " adaptRealIacResult: result type = " + iacResult.getClass().getName());
			System.out.println(logTag + " Available methods:");
			for (Method m : iacResult.getClass().getMethods()) {
				if (!m.getName().startsWith("java")) {
					System.out.println(logTag + "   - " + m.getName() + "() returns " + m.getReturnType().getSimpleName());
				}
			}

			// Try to get misconfigurations from result
			Method getMisconfig = null;
			try {
				getMisconfig = iacResult.getClass().getMethod("getMisconfigurations");
			} catch (Exception e) {
				try {
					getMisconfig = iacResult.getClass().getMethod("getFindings");
				} catch (Exception e2) {
					try {
						getMisconfig = iacResult.getClass().getMethod("getResults");
					} catch (Exception e3) {
						CxLogger.warning(logTag + " Could not find get method in IaC result");
						return issues;
					}
				}
			}

			Object iacData = getMisconfig.invoke(iacResult);
			if (iacData == null) {
				System.out.println(logTag + " No misconfigurations found in IaC result");
				return issues;
			}

			if (iacData instanceof List) {
				List<?> misconfigs = (List<?>) iacData;
				System.out.println(logTag + " Found " + misconfigs.size() + " misconfigurations");

				int id = 4000;
				for (Object misconfig : misconfigs) {
					try {
						ScanIssue issue = new ScanIssue();

						String title = getIacProperty(misconfig, "getTitle", String.class);
						if (title == null) {
							title = getIacProperty(misconfig, "getRuleName", String.class);
						}
						if (title == null) {
							title = "IaC Misconfiguration";
						}

						String severity = getIacProperty(misconfig, "getSeverity", String.class);
						String description = getIacProperty(misconfig, "getDescription", String.class);
						Integer lineNumber = getIacProperty(misconfig, "getLine", Integer.class);
						String remediation = getIacProperty(misconfig, "getRemediationAdvice", String.class);

						issue.setScanIssueId("IAC-" + id);
						issue.setTitle(title);
						issue.setDescription(description != null ? description : "Infrastructure misconfiguration detected");
						issue.setSeverity(severity != null ? severity : "MEDIUM");
						issue.setProblematicLineNumber(lineNumber != null ? lineNumber : 1);
						issue.setRemediationAdvise(remediation);
						issue.setScanEngine(ScanEngine.IAC);

						Location location = new Location();
						location.setLine(lineNumber != null ? lineNumber : 1);
						location.setStartIndex(0);
						location.setEndIndex(0);
						issue.getLocations().add(location);

						issues.add(issue);
						id++;

					} catch (Exception e) {
						System.err.println(logTag + "   ✗ Error adapting IaC misconfiguration: " + e.getMessage());
					}
				}
			}

			System.out.println(logTag + " ✓ Adapted " + issues.size() + " real IaC issues from server");

		} catch (Exception e) {
			System.err.println(logTag + " Error adapting real IaC result: " + e.getMessage());
			e.printStackTrace();
		}
		return issues;
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
