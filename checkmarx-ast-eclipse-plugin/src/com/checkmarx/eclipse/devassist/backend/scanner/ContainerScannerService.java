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
	 * Generates realistic mock container vulnerabilities for demonstration.
	 * In production, this would call CxWrapperFactory to execute actual scan.
	 *
	 * @param filePath Container file to scan
	 * @return Mock container vulnerabilities
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing container scan on: " + filePath);

		// Generate realistic mock container findings for demo
		List<MockContainerVulnerability> results = new ArrayList<>();

		String lowerPath = filePath.toLowerCase();

		if (lowerPath.contains("dockerfile")) {
			// Base image vulnerability
			results.add(new MockContainerVulnerability(
				"Vulnerable base image: ubuntu:18.04",
				"Base image contains known vulnerabilities",
				"CRITICAL", "Use ubuntu:22.04 LTS"));

			// Configuration issues
			results.add(new MockContainerVulnerability(
				"Container running as root",
				"Container process runs with root privileges",
				"HIGH", "Add USER directive to run as non-root"));

			results.add(new MockContainerVulnerability(
				"No health check defined",
				"Container has no healthcheck instruction",
				"MEDIUM", "Add HEALTHCHECK instruction"));

			// Vulnerable OS packages
			results.add(new MockContainerVulnerability(
				"OpenSSL 1.0.2 - CVE-2021-23839",
				"Vulnerable OpenSSL version in base image", "HIGH",
				"Update base image to patched version"));
		} else if (lowerPath.contains("docker-compose")) {
			// Service configuration issues
			results.add(new MockContainerVulnerability(
				"Privileged mode enabled",
				"Service running with --privileged flag",
				"CRITICAL", "Remove privileged flag if not needed"));

			results.add(new MockContainerVulnerability(
				"Port exposed to all interfaces",
				"Port 5432 exposed to 0.0.0.0", "HIGH",
				"Restrict to localhost or specific IP"));
		}

		CxLogger.info(logTag + " ✓ Generated " + results.size() +
			" mock container vulnerabilities");
		return results;
	}

	/**
	 * Adapt container scan results to ScanIssue model.
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
		int id = 3000;

		for (Object result : results) {
			if (!(result instanceof MockContainerVulnerability)) {
				continue;
			}

			MockContainerVulnerability vuln = (MockContainerVulnerability) result;
			ScanIssue issue = new ScanIssue();

			issue.setScanIssueId("CONTAINER-" + id++);
			issue.setTitle(vuln.title);
			issue.setDescription(vuln.description);
			issue.setSeverity(vuln.severity);
			issue.setRemediationAdvise(vuln.remediation);
			issue.setImageTag("latest");
			issue.setProblematicLineNumber(1);
			issue.setScanEngine(
				com.checkmarx.eclipse.devassist.backend.scanner.ScannerService.ScannerType.CONTAINERS);

			issues.add(issue);
		}

		return issues;
	}

	/**
	 * Mock container vulnerability data for demo.
	 */
	static class MockContainerVulnerability {
		String title;
		String description;
		String severity;
		String remediation;

		MockContainerVulnerability(String title, String desc, String severity,
			String remediation) {
			this.title = title;
			this.description = desc;
			this.severity = severity;
			this.remediation = remediation;
		}
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
