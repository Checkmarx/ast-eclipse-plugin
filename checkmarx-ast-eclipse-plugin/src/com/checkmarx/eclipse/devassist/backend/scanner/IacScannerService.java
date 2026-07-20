package com.checkmarx.eclipse.devassist.backend.scanner;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

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
	 * Execute IaC scan on a configuration file.
	 *
	 * Generates realistic mock IaC misconfigurations for demonstration.
	 * In production, this would call CxWrapperFactory to execute actual scan.
	 *
	 * @param filePath IaC configuration file to scan
	 * @return Mock IaC misconfigurations
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing IaC scan on: " + filePath);

		// Generate realistic mock IaC findings for demo
		List<MockIacMisconfiguration> results = new ArrayList<>();

		String lowerPath = filePath.toLowerCase();
		String framework = detectFramework(filePath);

		if (framework.equals("Terraform")) {
			results.add(new MockIacMisconfiguration("AWS_S3_PUBLIC",
				"S3 bucket publicly accessible",
				"S3 bucket has public-read or public-read-write ACL", "CRITICAL",
				8, "Set acl to private or use bucket policy"));

			results.add(new MockIacMisconfiguration("AWS_SECURITY_GROUP_WIDE_OPEN",
				"Security group allows unrestricted access",
				"Security group allows 0.0.0.0/0 on sensitive ports",
				"HIGH", 15,
				"Restrict CIDR to known IP ranges"));

			results.add(new MockIacMisconfiguration("AWS_RDS_NO_ENCRYPTION",
				"RDS database not encrypted at rest",
				"Database cluster storage is not encrypted", "HIGH", 22,
				"Enable storage_encrypted = true"));
		} else if (framework.equals("Kubernetes")) {
			results.add(new MockIacMisconfiguration("K8S_POD_RUN_AS_ROOT",
				"Pod runs with root privileges",
				"securityContext.runAsNonRoot not set to true", "HIGH",
				10,
				"Set runAsNonRoot: true and runAsUser to non-zero"));

			results.add(new MockIacMisconfiguration("K8S_NO_RESOURCE_LIMITS",
				"No resource limits defined",
				"Pod has no CPU or memory limits",
				"MEDIUM", 18,
				"Define limits for CPU and memory"));

			results.add(new MockIacMisconfiguration("K8S_NODE_PORT_EXPOSED",
				"Service exposed via NodePort",
				"Service uses NodePort which exposes to all nodes",
				"MEDIUM", 25,
				"Use ClusterIP or Ingress instead"));
		}

		CxLogger.info(logTag + " ✓ Generated " + results.size() +
			" mock IaC misconfigurations");
		return results;
	}

	/**
	 * Adapt IaC scan results to ScanIssue model.
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
			issue.setScanEngine(
				com.checkmarx.eclipse.devassist.backend.scanner.ScannerService.ScannerType.IAC);

			issues.add(issue);
		}

		return issues;
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
