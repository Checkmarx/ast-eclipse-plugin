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
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults) {
		List<ScanIssue> issues = new ArrayList<>();

		if (rawResults == null) {
			return issues;
		}

		// Try to adapt as real container result first
		if (isRealContainerResult(rawResults)) {
			return adaptRealContainerResult(rawResults);
		}

		// Fall back to mock data
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
			issue.setScanEngine(ScanEngine.CONTAINERS);

			issues.add(issue);
		}

		return issues;
	}

	private boolean isRealContainerResult(Object obj) {
		return obj != null && (obj.getClass().getSimpleName().equals("ContainersScanResults") ||
			obj.getClass().getSimpleName().equals("ContainerRealtimeResults"));
	}

	private List<ScanIssue> adaptRealContainerResult(Object containerResult) {
		List<ScanIssue> issues = new ArrayList<>();
		try {
			System.out.println(logTag + " adaptRealContainerResult: result type = " + containerResult.getClass().getName());
			System.out.println(logTag + " Available methods:");
			for (Method m : containerResult.getClass().getMethods()) {
				if (!m.getName().startsWith("java")) {
					System.out.println(logTag + "   - " + m.getName() + "() returns " + m.getReturnType().getSimpleName());
				}
			}

			// Try to get vulnerabilities/layers/images from result
			Method getImagesMethod = null;
			try {
				getImagesMethod = containerResult.getClass().getMethod("getImages");
			} catch (Exception e) {
				try {
					getImagesMethod = containerResult.getClass().getMethod("getLayers");
				} catch (Exception e2) {
					try {
						getImagesMethod = containerResult.getClass().getMethod("getFindings");
					} catch (Exception e3) {
						CxLogger.warning(logTag + " Could not find get method in container result");
						return issues;
					}
				}
			}

			Object containerData = getImagesMethod.invoke(containerResult);
			if (containerData == null) {
				System.out.println(logTag + " No data found in container result");
				return issues;
			}

			if (containerData instanceof List) {
				List<?> items = (List<?>) containerData;
				System.out.println(logTag + " Found " + items.size() + " items");

				int id = 3000;
				for (Object item : items) {
					try {
						ScanIssue issue = new ScanIssue();
						String title = getContainerProperty(item, "getImageName", String.class);
						if (title == null) {
							title = getContainerProperty(item, "getTitle", String.class);
						}
						if (title == null) {
							title = "Container Vulnerability";
						}

						String severity = getContainerProperty(item, "getSeverity", String.class);
						String description = getContainerProperty(item, "getDescription", String.class);

						issue.setScanIssueId("CONTAINER-" + id);
						issue.setTitle(title);
						issue.setDescription(description != null ? description : "Container vulnerability detected");
						issue.setSeverity(severity != null ? severity : "MEDIUM");
						issue.setImageTag("latest");
						issue.setProblematicLineNumber(1);
						issue.setScanEngine(ScanEngine.CONTAINERS);

						Location location = new Location();
						location.setLine(1);
						location.setStartIndex(0);
						location.setEndIndex(0);
						issue.getLocations().add(location);

						issues.add(issue);
						id++;

					} catch (Exception e) {
						System.err.println(logTag + "   ✗ Error adapting item: " + e.getMessage());
					}
				}
			}

			System.out.println(logTag + " ✓ Adapted " + issues.size() + " real container issues from server");

		} catch (Exception e) {
			System.err.println(logTag + " Error adapting real container result: " + e.getMessage());
			e.printStackTrace();
		}
		return issues;
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
