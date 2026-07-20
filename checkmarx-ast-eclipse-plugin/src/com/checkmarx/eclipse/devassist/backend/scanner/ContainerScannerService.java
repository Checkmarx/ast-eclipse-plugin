package com.checkmarx.eclipse.devassist.backend.scanner;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Scanner for container image vulnerabilities.
 *
 * Scans container configuration files:
 * - Dockerfile
 * - docker-compose.yaml/yml
 * - Container registries (Docker Hub, ECR, etc.)
 *
 * Detects:
 * - Vulnerable base images
 * - Insecure configuration (running as root, exposed ports)
 * - Vulnerable OS packages in container layers
 * - CVEs in application dependencies
 */
public class ContainerScannerService extends BaseScannerService {

	private static final String[] CONTAINER_FILE_PATTERNS = {
		"Dockerfile", "dockerfile",
		"docker-compose.yaml", "docker-compose.yml",
		"docker-compose.override.yaml", "docker-compose.override.yml",
		".dockerignore"
	};

	/**
	 * Create a Container scanner for a project.
	 *
	 * @param project Eclipse project
	 */
	public ContainerScannerService(IProject project) {
		super(project);
	}

	/**
	 * Check if file is a container configuration file.
	 *
	 * @param filePath File path to check
	 * @return true if file is a container file
	 */
	@Override
	protected boolean isFileTypeSupported(String filePath) {
		if (filePath == null) {
			return false;
		}

		String fileName = new java.io.File(filePath).getName();
		String lowerPath = filePath.toLowerCase();

		// Check container file names
		for (String pattern : CONTAINER_FILE_PATTERNS) {
			if (fileName.equalsIgnoreCase(pattern) || lowerPath.endsWith(pattern)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Execute container scan on a Dockerfile or docker-compose file.
	 *
	 * In a real implementation, this would:
	 * 1. Parse the Dockerfile/docker-compose.yaml
	 * 2. Extract base image information
	 * 3. Query container registry for vulnerabilities
	 * 4. Analyze configuration for insecure settings
	 * 5. Scan OS packages and dependencies in layers
	 * 6. Return findings
	 *
	 * For now, returns empty list (Phase 3 will integrate with CxWrapperFactory).
	 *
	 * @param filePath Container file to scan
	 * @return Raw scanner results
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing container scan on: " + filePath);

		// TODO: Phase 3 - Call CxWrapperFactory to execute actual scan
		// For now, return placeholder
		return new ArrayList<Object>();
	}

	/**
	 * Adapt container scan results to ScanIssue model.
	 *
	 * Converts container vulnerabilities to standardized ScanIssue objects.
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults) {
		List<ScanIssue> issues = new ArrayList<>();

		// TODO: Phase 3 - Parse raw results and create ScanIssue objects
		// Example:
		// for (ContainerVulnerability vuln : (List<ContainerVulnerability>) rawResults) {
		//     ScanIssue issue = new ScanIssue();
		//     issue.setScanIssueId(vuln.getId());
		//     issue.setTitle(vuln.getTitle());
		//     issue.setSeverity(vuln.getSeverity());
		//     issue.setDescription(vuln.getDescription());
		//
		//     // For base image vulnerabilities
		//     if (vuln.isBaseImageIssue()) {
		//         issue.setTitle("Vulnerable base image: " + vuln.getImageName());
		//         issue.setRemediationAdvise("Update to " + vuln.getFixedImageName());
		//     }
		//     // For configuration issues
		//     else if (vuln.isConfigurationIssue()) {
		//         issue.setTitle("Insecure configuration: " + vuln.getConfigName());
		//         issue.setRemediationAdvise(vuln.getRemediationAdvice());
		//     }
		//     // For OS package vulnerabilities
		//     else {
		//         issue.setTitle("Vulnerable OS package: " + vuln.getPackageName());
		//         issue.setPackageVersion(vuln.getVulnerableVersion());
		//         issue.setCve(vuln.getCveId());
		//         issue.setRemediationAdvise("Update to " + vuln.getFixedVersion());
		//     }
		//
		//     issues.add(issue);
		// }

		return issues;
	}

	@Override
	public String getDisplayName() {
		return "Container Scanning";
	}

	@Override
	public ScannerType getScannerType() {
		return ScannerType.CONTAINERS;
	}

	/**
	 * Extract base image name from Dockerfile line.
	 *
	 * @param dockerfileLine Line from Dockerfile (e.g., "FROM ubuntu:20.04")
	 * @return Base image name
	 */
	public String extractBaseImage(String dockerfileLine) {
		if (dockerfileLine == null || !dockerfileLine.trim().toLowerCase().startsWith("from")) {
			return null;
		}

		String[] parts = dockerfileLine.split("\\s+");
		if (parts.length >= 2) {
			return parts[1];
		}

		return null;
	}
}
