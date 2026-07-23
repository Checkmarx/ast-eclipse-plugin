package com.checkmarx.eclipse.devassist.backend.scanner;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.devassist.ui.findings.model.ScanEngine;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Scanner for hardcoded secrets and credentials.
 *
 * Scans virtually all files (with exclusions) to detect:
 * - API keys
 * - Database passwords
 * - AWS credentials
 * - Private keys
 * - OAuth tokens
 * - SSH keys
 * - etc.
 *
 * Exclusions:
 * - Dependency manifests (OSS scanner handles these)
 * - Ignore files (.gitignore, .dockerignore)
 * - Lock files (package-lock.json, etc.)
 * - Build artifacts (node_modules, target, dist, build)
 */
public class SecretsScannerService extends BaseScannerService {

	// Manifest files that OSS scanner handles (don't scan for secrets)
	private static final String[] EXCLUDED_MANIFEST_FILES = {
		"package.json", "package-lock.json", "npm-shrinkwrap.json",
		"pom.xml",
		"go.mod", "go.sum",
		"requirements.txt", "Pipfile", "Pipfile.lock", "setup.py",
		"Gemfile", "Gemfile.lock",
		"Cargo.toml", "Cargo.lock",
		"composer.json", "composer.lock",
		"packages.config", ".csproj",
		"yarn.lock"
	};

	// Ignore files that don't contain application code
	private static final String[] EXCLUDED_CONFIG_FILES = {
		".gitignore", ".dockerignore", ".npmignore", ".eslintignore",
		".prettierignore", ".editorconfig",
		".lock", ".sum", ".shrinkwrap"
	};

	/**
	 * Create a Secrets scanner for a project.
	 *
	 * @param project Eclipse project
	 */
	public SecretsScannerService(IProject project) {
		super(project);
	}

	/**
	 * Check if file should be scanned for secrets.
	 *
	 * Scans most files EXCEPT:
	 * - Dependency manifests (package.json, pom.xml, etc.)
	 * - Ignore files (.gitignore, .npmignore, etc.)
	 * - Binary files
	 *
	 * @param filePath File path to check
	 * @return true if file should be scanned for secrets
	 */
	@Override
	protected boolean isFileTypeSupported(String filePath) {
		if (filePath == null) {
			return false;
		}

		String lowerPath = filePath.toLowerCase();
		String fileName = new java.io.File(filePath).getName().toLowerCase();

		// Exclude manifest files (OSS scanner handles these)
		for (String manifest : EXCLUDED_MANIFEST_FILES) {
			if (fileName.equals(manifest) || lowerPath.endsWith(manifest)) {
				return false;
			}
		}

		// Exclude ignore files
		for (String ignored : EXCLUDED_CONFIG_FILES) {
			if (fileName.equals(ignored) || fileName.endsWith(ignored)) {
				return false;
			}
		}

		// Exclude known binary file extensions
		String[] binaryExtensions = {
			".exe", ".dll", ".so", ".dylib", ".bin",
			".class", ".jar", ".war", ".ear",
			".zip", ".rar", ".7z", ".tar", ".gz",
			".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
			".mp3", ".mp4", ".mov", ".avi",
			".pdf", ".doc", ".docx", ".xls", ".xlsx"
		};

		for (String ext : binaryExtensions) {
			if (lowerPath.endsWith(ext)) {
				return false;
			}
		}

		// Scan most text files
		return true;
	}

	/**
	 * Execute secrets scan using real Checkmarx server API via reflection.
	 *
	 * Uses reflection to call CxWrapperFactory at runtime.
	 *
	 * @param filePath File to scan
	 * @return Real SecretsRealtimeResults from Checkmarx server, or null if API unavailable
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		CxLogger.info(logTag + " Executing secrets scan on: " + filePath);

		String tempFilePath = null;
		try {
			// Read actual file content
			String fileContent = readFileContent(filePath);
			if (fileContent == null) {
				CxLogger.warning(logTag + " Could not read file: " + filePath);
				return null;
			}

			// Create temp file for secrets scanner
			tempFilePath = createTempFile(filePath, fileContent);
			if (tempFilePath == null) {
				CxLogger.warning(logTag + " Failed to create temp file");
				return null;
			}

			CxLogger.info(logTag + " Calling real Secrets API via reflection...");

			// Call real Checkmarx API via reflection
			Object result = callSecretsApiViaReflection(tempFilePath);
			if (result == null) {
				CxLogger.warning(logTag + " Secrets API returned null");
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
	 * Call Secrets scan via reflection on CxWrapper from ast-cli-java-wrapper JAR.
	 * Works around Tycho's compile-time dependency issues.
	 *
	 * Method signature: secretsRealtimeScan(String filePath, String ignorePath)
	 */
	private Object callSecretsApiViaReflection(String filePath) {
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

			// Call secretsRealtimeScan(filePath, ignorePath)
			// Note: Method is secretsRealtimeScan (lowercase s), not ScanSecretsRealtime
			Method scanMethod = wrapperClass.getMethod("secretsRealtimeScan", String.class, String.class);
			Object scanResult = scanMethod.invoke(wrapper, filePath, "");

			CxLogger.info(logTag + " ✓ Called real Secrets API successfully");
			return scanResult;

		} catch (ClassNotFoundException e) {
			CxLogger.warning(logTag + " CxWrapper not available in classpath: " + e.getMessage());
			return null;
		} catch (Exception e) {
			CxLogger.error(logTag + " Reflection error calling Secrets API: " + e.getMessage(), e);
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
			Path tempFilePath = tempDir.resolve("secrets_" + System.nanoTime() + "_" + fileName);
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
	 * Adapt secrets scan results to ScanIssue model.
	 *
	 * Handles both real SecretsRealtimeResults from API and legacy mock data.
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @param filePath Original file path being scanned
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults, String filePath) {
		List<ScanIssue> issues = new ArrayList<>();

		if (rawResults == null) {
			return issues;
		}

		// Try to adapt as real SecretsRealtimeResults first
		if (isRealSecretsResult(rawResults)) {
			return adaptRealSecretsResult(rawResults, filePath);
		}

		// Fall back to mock data if available
		if (!(rawResults instanceof List)) {
			return issues;
		}

		List<?> results = (List<?>) rawResults;

		for (Object result : results) {
			if (!(result instanceof MockSecret)) {
				continue;
			}

			MockSecret secret = (MockSecret) result;
			ScanIssue issue = new ScanIssue();

			// JetBrains Pattern: Generate unique ID based on content (line + title + description)
			String scanIssueId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
				secret.line_number,
				secret.type,
				secret.description
			);

			issue.setScanIssueId(scanIssueId);
			issue.setTitle(secret.type);
			issue.setDescription(secret.description);
			issue.setSeverity(secret.severity);
			issue.setProblematicLineNumber(secret.line_number);
			issue.setRemediationAdvise("Remove " + secret.type +
				" from source code and use environment variables instead");
			issue.setSecretValue("***MASKED***");
			issue.setScanEngine(ScanEngine.SECRETS);

			// JetBrains Pattern: ONE Vulnerability per ScanIssue
			com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability vulnerability =
				new com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability();
			vulnerability.setVulnerabilityId(scanIssueId);
			vulnerability.setTitle(secret.type);
			vulnerability.setDescription(secret.description);
			vulnerability.setSeverity(com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(secret.severity));
			issue.getVulnerabilities().add(vulnerability);

			issues.add(issue);
		}

		return issues;
	}

	private boolean isRealSecretsResult(Object obj) {
		return obj != null && obj.getClass().getSimpleName().equals("SecretsRealtimeResults");
	}

	private List<ScanIssue> adaptRealSecretsResult(Object secretsResult, String filePath) {
		List<ScanIssue> issues = new ArrayList<>();
		try {
			CxLogger.info(logTag + " Adapting real secrets result from API");

			Method getSecrets = secretsResult.getClass().getMethod("getSecrets");
			List<?> secrets = (List<?>) getSecrets.invoke(secretsResult);

			if (secrets == null || secrets.isEmpty()) {
				CxLogger.info(logTag + " No secrets found in real result");
				return issues;
			}

			CxLogger.info(logTag + " Found " + secrets.size() + " secrets - creating ScanIssues");

			// JetBrains Pattern: 1 Secret → 1 ScanIssue with 1 Vulnerability
			for (Object secret : secrets) {
				try {
					ScanIssue issue = new ScanIssue();

					String title = getSecretProperty(secret, "getTitle", String.class);
					String description = getSecretProperty(secret, "getDescription", String.class);
					String severity = getSecretProperty(secret, "getSeverity", String.class);
					String secretValue = getSecretProperty(secret, "getSecretValue", String.class);

					// Get first location for ID generation (JetBrains pattern)
					List<?> locations = getSecretProperty(secret, "getLocations", List.class);
					Integer firstLine = 0;
					if (locations != null && !locations.isEmpty()) {
						firstLine = getLocationProperty(locations.get(0), "getLine", Integer.class);
						if (firstLine == null) {
							firstLine = 0;
						}
					}

					// JetBrains Pattern: Generate unique ID using line + title + description
					String scanIssueId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
						firstLine,
						title != null ? title : "Unknown Secret",
						description != null ? description : ""
					);

					issue.setScanIssueId(scanIssueId);
					issue.setTitle(title != null ? title : "Unknown Secret");
					issue.setDescription(description != null ? description : "");
					String normalizedSeverity = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(severity != null ? severity : "Medium");
					issue.setSeverity(normalizedSeverity);
					issue.setFilePath(filePath);
					issue.setSecretValue(secretValue != null ? secretValue : "***MASKED***");
					issue.setScanEngine(ScanEngine.SECRETS);

					// JetBrains Pattern: ONE Vulnerability per ScanIssue
					com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability vulnerability =
						new com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability();
					vulnerability.setVulnerabilityId(scanIssueId);
					vulnerability.setTitle(title != null ? title : "Unknown Secret");
					vulnerability.setDescription(description != null ? description : "");
					vulnerability.setSeverity(normalizedSeverity);
					issue.getVulnerabilities().add(vulnerability);

					// Add ALL locations to this ScanIssue
					if (locations != null && !locations.isEmpty()) {
						for (Object locObj : locations) {
							try {
								Integer line = getLocationProperty(locObj, "getLine", Integer.class);
								Integer startIndex = getLocationProperty(locObj, "getStartIndex", Integer.class);
								Integer endIndex = getLocationProperty(locObj, "getEndIndex", Integer.class);

								// JetBrains pattern: Add 1 to line (0-based → 1-based)
								com.checkmarx.eclipse.devassist.ui.findings.model.Location location =
									new com.checkmarx.eclipse.devassist.ui.findings.model.Location(
										(line != null ? line : 0) + 1,
										startIndex != null ? startIndex : 0,
										endIndex != null ? endIndex : 0
									);
								issue.getLocations().add(location);

								CxLogger.info(logTag + " Added location - Line: " + ((line != null ? line : 0) + 1) +
									", StartIdx: " + (startIndex != null ? startIndex : 0) +
									", EndIdx: " + (endIndex != null ? endIndex : 0));

							} catch (Exception e) {
								CxLogger.warning(logTag + " Error extracting location: " + e.getMessage());
							}
						}
					}

					// Set problematic line from first location
					if (issue.getProblematicLineNumber() == 0 && !issue.getLocations().isEmpty()) {
						issue.setProblematicLineNumber(issue.getLocations().get(0).getLine());
					}

					issues.add(issue);
					CxLogger.info(logTag + " ✓ Created ScanIssue: " + title + " (ID: " + scanIssueId + ")");

				} catch (Exception e) {
					CxLogger.warning(logTag + " Error adapting secret: " + e.getMessage());
				}
			}

			CxLogger.info(logTag + " ✓ Adapted " + issues.size() + " real secrets from server");

		} catch (Exception e) {
			CxLogger.error(logTag + " Error adapting real secrets result: " + e.getMessage(), e);
		}
		return issues;
	}

	@SuppressWarnings("unchecked")
	private <T> T getSecretProperty(Object secret, String methodName, Class<T> returnType) {
		try {
			Method method = secret.getClass().getMethod(methodName);
			return (T) method.invoke(secret);
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

	private String mapSecretSeverity(String secretType) {
		if (secretType == null) return com.checkmarx.eclipse.devassist.backend.SeverityLevel.MEDIUM.getSeverity();
		String lower = secretType.toLowerCase();
		if (lower.contains("private") || lower.contains("password") ||
			lower.contains("api_key") || lower.contains("token")) {
			return com.checkmarx.eclipse.devassist.backend.SeverityLevel.CRITICAL.getSeverity();
		}
		if (lower.contains("bearer") || lower.contains("webhook")) {
			return com.checkmarx.eclipse.devassist.backend.SeverityLevel.HIGH.getSeverity();
		}
		return com.checkmarx.eclipse.devassist.backend.SeverityLevel.MEDIUM.getSeverity();
	}

	/**
	 * Mock secret data for demo.
	 */
	static class MockSecret {
		String type;
		String description;
		String severity;
		int line_number;
		String value;

		MockSecret(String type, String desc, String severity, int line,
			String value) {
			this.type = type;
			this.description = desc;
			this.severity = severity;
			this.line_number = line;
			this.value = value;
		}
	}

	@Override
	public String getDisplayName() {
		return "Secrets Scanning";
	}

	@Override
	public ScannerType getScannerType() {
		return ScannerType.SECRETS;
	}
}
