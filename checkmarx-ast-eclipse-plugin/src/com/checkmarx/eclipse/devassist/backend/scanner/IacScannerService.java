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
	 * In a real implementation, this would:
	 * 1. Parse the IaC file (YAML, JSON, HCL, etc.)
	 * 2. Build resource topology
	 * 3. Check against security policies
	 * 4. Validate configuration against best practices
	 * 5. Return misconfigurations
	 *
	 * For now, returns empty list (Phase 3 will integrate with CxWrapperFactory).
	 *
	 * @param filePath IaC configuration file to scan
	 * @return Raw scanner results
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing IaC scan on: " + filePath);

		// TODO: Phase 3 - Call CxWrapperFactory to execute actual scan
		// For now, return placeholder
		return new ArrayList<Object>();
	}

	/**
	 * Adapt IaC scan results to ScanIssue model.
	 *
	 * Converts IaC misconfigurations to standardized ScanIssue objects.
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults) {
		List<ScanIssue> issues = new ArrayList<>();

		// TODO: Phase 3 - Parse raw results and create ScanIssue objects
		// Example:
		// for (IacMisconfiguration config : (List<IacMisconfiguration>) rawResults) {
		//     ScanIssue issue = new ScanIssue();
		//     issue.setScanIssueId(config.getId());
		//     issue.setTitle(config.getRuleTitle());
		//     issue.setSeverity(config.getSeverity());
		//     issue.setDescription(config.getDescription());
		//     issue.setProblematicLineNumber(config.getLineNumber());
		//     issue.setRemediationAdvise(config.getRemediationAdvice());
		//
		//     // Add resource context
		//     issue.setRuleId(config.getRuleId());
		//
		//     issues.add(issue);
		// }

		return issues;
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
