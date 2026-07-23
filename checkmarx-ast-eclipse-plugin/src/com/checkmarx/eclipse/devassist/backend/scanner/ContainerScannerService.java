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
	 * Execute container scan using real Checkmarx server API via reflection.
	 *
	 * @param filePath Container file to scan
	 * @return Real container scan results from Checkmarx server
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing container scan on: " + filePath);

		String tempFilePath = null;
		try {
			// Read actual file content
			String fileContent = readFileContent(filePath);
			if (fileContent == null) {
				CxLogger.warning(logTag + " Could not read file: " + filePath);
				return null;
			}

			// Create temp file for container scanner
			tempFilePath = createTempFile(filePath, fileContent);
			if (tempFilePath == null) {
				CxLogger.warning(logTag + " Failed to create temp file");
				return null;
			}

			CxLogger.info(logTag + " Calling real Containers API via reflection...");

			// Call real Checkmarx API via reflection
			Object result = callContainersApiViaReflection(tempFilePath);
			if (result == null) {
				CxLogger.warning(logTag + " Containers API returned null");
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
	 * Call Containers scan via reflection on CxWrapper.
	 */
	private Object callContainersApiViaReflection(String filePath) {
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

			// Call containersRealtimeScan(filePath, imageName)
			Method scanMethod = wrapperClass.getMethod("containersRealtimeScan", String.class, String.class);
			Object scanResult = scanMethod.invoke(wrapper, filePath, "");

			CxLogger.info(logTag + " ✓ Called real Containers API successfully");
			return scanResult;

		} catch (ClassNotFoundException e) {
			CxLogger.warning(logTag + " CxWrapper not available in classpath: " + e.getMessage());
			return null;
		} catch (Exception e) {
			CxLogger.error(logTag + " Reflection error calling Containers API: " + e.getMessage(), e);
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
			Path tempFilePath = tempDir.resolve("containers_" + System.nanoTime() + "_" + fileName);
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
	 * Adapt container scan results to ScanIssue model.
	 *
	 * Handles both real results from API and legacy mock data.
	 * Implements JetBrains pattern: 1 Image = 1 ScanIssue with multiple Vulnerabilities.
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @param filePath Original file path being scanned
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults, String filePath) {
		List<ScanIssue> issues = new ArrayList<>();

		// CRITICAL DEBUG: This log will show if adaptResults is called at all
		System.out.println("════════════════════════════════════════════");
		System.out.println("⚠️  CONTAINER ADAPTER CALLED - rawResults: " + (rawResults != null ? rawResults.getClass().getSimpleName() : "NULL"));
		System.out.println("⚠️  filePath: " + filePath);
		System.out.println("════════════════════════════════════════════");
		CxLogger.info(logTag + " ✓✓✓ CONTAINER adaptResults() CALLED with rawResults type: " +
			(rawResults != null ? rawResults.getClass().getSimpleName() : "NULL"));

		if (rawResults == null) {
			CxLogger.warning(logTag + " rawResults is NULL, returning empty list");
			return issues;
		}

		// Try to adapt as real container result first
		if (isRealContainerResult(rawResults)) {
			return adaptRealContainerResult(rawResults, filePath);
		}

		// Fall back to mock data
		if (!(rawResults instanceof List)) {
			return issues;
		}

		List<?> results = (List<?>) rawResults;

		for (Object result : results) {
			if (!(result instanceof MockContainerVulnerability)) {
				continue;
			}

			MockContainerVulnerability mockVuln = (MockContainerVulnerability) result;
			ScanIssue issue = new ScanIssue();

			// JetBrains Pattern: Generate unique ID based on content (line + title + imageTag)
			String scanIssueId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
				1,
				mockVuln.title,
				"latest"
			);

			issue.setScanIssueId(scanIssueId);
			issue.setTitle(mockVuln.title);
			issue.setDescription(mockVuln.description);
			issue.setSeverity(com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(mockVuln.severity));
			issue.setRemediationAdvise(mockVuln.remediation);
			issue.setImageTag("latest");
			issue.setProblematicLineNumber(1);
			issue.setScanEngine(ScanEngine.CONTAINERS);

			// JetBrains Pattern: Multiple Vulnerabilities per ScanIssue
			com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability vulnerability =
				new com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability();
			vulnerability.setTitle(mockVuln.title);
			vulnerability.setDescription(mockVuln.description);
			vulnerability.setSeverity(com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(mockVuln.severity));
			// JetBrains: Do NOT set vulnerability ID for Container
			issue.getVulnerabilities().add(vulnerability);

			issues.add(issue);
		}

		return issues;
	}

	private boolean isRealContainerResult(Object obj) {
		return obj != null && (obj.getClass().getSimpleName().equals("ContainersScanResults") ||
			obj.getClass().getSimpleName().equals("ContainersRealtimeResults"));  // Fixed: was "ContainerRealtimeResults"
	}

	private List<ScanIssue> adaptRealContainerResult(Object containerResult, String filePath) {
		List<ScanIssue> issues = new ArrayList<>();
		try {
			CxLogger.info(logTag + " Adapting real container result from API");
			CxLogger.info(logTag + " Result type: " + containerResult.getClass().getName());
			CxLogger.info(logTag + " Result class name: " + containerResult.getClass().getSimpleName());

			// DEBUG: Print all available methods
			CxLogger.info(logTag + " Available methods on result object:");
			for (Method m : containerResult.getClass().getMethods()) {
				if (!m.getName().startsWith("java") && m.getParameterCount() == 0) {
					CxLogger.info(logTag + "   - " + m.getName() + "() returns " + m.getReturnType().getSimpleName());
				}
			}

			// Get images from result (JetBrains: getImages())
			Method getImagesMethod = null;
			try {
				getImagesMethod = containerResult.getClass().getMethod("getImages");
				CxLogger.info(logTag + " Found getImages() method");
			} catch (Exception e) {
				try {
					getImagesMethod = containerResult.getClass().getMethod("getLayers");
					CxLogger.info(logTag + " Found getLayers() method");
				} catch (Exception e2) {
					try {
						getImagesMethod = containerResult.getClass().getMethod("getFindings");
						CxLogger.info(logTag + " Found getFindings() method");
					} catch (Exception e3) {
						CxLogger.warning(logTag + " Could not find getImages/getLayers/getFindings method");
						CxLogger.warning(logTag + " DEBUG - tried: getImages() - " + e.getMessage());
						CxLogger.warning(logTag + " DEBUG - tried: getLayers() - " + e2.getMessage());
						CxLogger.warning(logTag + " DEBUG - tried: getFindings() - " + e3.getMessage());
						return issues;
					}
				}
			}

			Object containerData = getImagesMethod.invoke(containerResult);
			CxLogger.info(logTag + " getImages/getLayers/getFindings() returned: " + (containerData != null ? containerData.getClass().getSimpleName() : "null"));

			if (containerData == null) {
				CxLogger.warning(logTag + " No data found in container result (returned null)");
				return issues;
			}

			if (!(containerData instanceof List)) {
				CxLogger.warning(logTag + " Container data is not a List, it's: " + containerData.getClass().getSimpleName());
				return issues;
			}

			List<?> images = (List<?>) containerData;
			CxLogger.info(logTag + " Found " + images.size() + " images - creating ScanIssues");

			if (images.isEmpty()) {
				CxLogger.info(logTag + " Images list is empty, returning empty issues");
				return issues;
			}

			// JetBrains Pattern: 1 Image → 1 ScanIssue
			for (Object image : images) {
				if (image == null) {
					continue;
				}

				try {
					ScanIssue issue = new ScanIssue();

					// Extract image properties
					String imageName = getContainerProperty(image, "getImageName", String.class);
					if (imageName == null) {
						imageName = getContainerProperty(image, "getTitle", String.class);
					}
					if (imageName == null) {
						imageName = "Unknown Container";
					}

					String imageTag = getContainerProperty(image, "getImageTag", String.class);
					if (imageTag == null) {
						imageTag = "latest";
					}

					String severity = getContainerProperty(image, "getStatus", String.class);
					if (severity == null) {
						severity = getContainerProperty(image, "getSeverity", String.class);
					}
					if (severity == null) {
						severity = "Medium";
					}
					severity = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(severity);

					// JetBrains Pattern: Generate unique ID using line + imageName + imageTag
					String scanIssueId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
						1,
						imageName,
						imageTag
					);

					issue.setScanIssueId(scanIssueId);
					issue.setTitle(imageName);
					issue.setImageTag(imageTag);
					issue.setSeverity(severity);
					issue.setFilePath(filePath);
					issue.setProblematicLineNumber(1);
					issue.setScanEngine(ScanEngine.CONTAINERS);
					issue.setFileType(getFileType(filePath));

					// JetBrains Pattern: Add ALL vulnerabilities from this image
					List<?> vulnerabilities = getContainerProperty(image, "getVulnerabilities", List.class);
					if (vulnerabilities != null && !vulnerabilities.isEmpty()) {
						for (Object vulnObj : vulnerabilities) {
							try {
								com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability vulnerability =
									new com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability();

								String cve = getContainerProperty(vulnObj, "getCve", String.class);
								String vulnDescription = getContainerProperty(vulnObj, "getDescription", String.class);
								String vulnSeverity = getContainerProperty(vulnObj, "getSeverity", String.class);

								if (vulnSeverity == null) {
									vulnSeverity = "Medium";
								}
								vulnSeverity = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(vulnSeverity);

								vulnerability.setCve(cve);
								vulnerability.setTitle(cve != null ? cve : "Container Vulnerability");
								vulnerability.setDescription(vulnDescription != null ? vulnDescription : "");
								vulnerability.setSeverity(vulnSeverity);
								// JetBrains: Do NOT set vulnerability ID for Container
								issue.getVulnerabilities().add(vulnerability);

								CxLogger.info(logTag + " Added vulnerability: " + (cve != null ? cve : "Unknown"));

							} catch (Exception e) {
								CxLogger.warning(logTag + " Error processing vulnerability: " + e.getMessage());
							}
						}
					}

					// JetBrains Pattern: Add ALL locations from this image
					List<?> locations = getContainerProperty(image, "getLocations", List.class);
					if (locations != null && !locations.isEmpty()) {
						for (Object locObj : locations) {
							try {
								Integer line = getLocationProperty(locObj, "getLine", Integer.class);
								Integer startIndex = getLocationProperty(locObj, "getStartIndex", Integer.class);
								Integer endIndex = getLocationProperty(locObj, "getEndIndex", Integer.class);

								// JetBrains pattern: Add 1 to line (0-based → 1-based)
								Location location = new Location(
									(line != null ? line : 0) + 1,
									startIndex != null ? startIndex : 0,
									endIndex != null ? endIndex : 0
								);
								issue.getLocations().add(location);

								CxLogger.info(logTag + " Added location - Line: " + ((line != null ? line : 0) + 1));

							} catch (Exception e) {
								CxLogger.warning(logTag + " Error extracting location: " + e.getMessage());
							}
						}
					}

					// If no locations were added, create a default one
					if (issue.getLocations().isEmpty()) {
						Location location = new Location();
						location.setLine(1);
						location.setStartIndex(0);
						location.setEndIndex(0);
						issue.getLocations().add(location);
					}

					issues.add(issue);
					CxLogger.info(logTag + " ✓ Created ScanIssue: " + imageName + " with " +
						issue.getVulnerabilities().size() + " vulnerabilities (ID: " + scanIssueId + ")");

				} catch (Exception e) {
					CxLogger.warning(logTag + " Error adapting image: " + e.getMessage());
				}
			}

			CxLogger.info(logTag + " ✓ Adapted " + issues.size() + " images from server");

		} catch (Exception e) {
			CxLogger.error(logTag + " Error adapting real container result: " + e.getMessage(), e);
		}
		return issues;
	}

	/**
	 * Get file type from file path (e.g., "Dockerfile", "docker-compose.yaml")
	 */
	private String getFileType(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return "dockerfile";
		}
		String fileName = new java.io.File(filePath).getName().toLowerCase();
		if (fileName.contains("docker-compose")) {
			return "docker-compose";
		}
		return "dockerfile";
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

	@SuppressWarnings("unchecked")
	private <T> T getContainerProperty(Object item, String methodName, Class<T> returnType) {
		try {
			Method method = item.getClass().getMethod(methodName);
			return (T) method.invoke(item);
		} catch (Exception e) {
			CxLogger.warning(logTag + " Could not get property " + methodName + ": " + e.getMessage());
			return null;
		}
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
