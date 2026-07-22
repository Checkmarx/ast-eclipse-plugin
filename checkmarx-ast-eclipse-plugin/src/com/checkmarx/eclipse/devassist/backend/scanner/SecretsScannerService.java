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
			return adaptRealSecretsResult(rawResults);
		}

		// Fall back to mock data if available
		if (!(rawResults instanceof List)) {
			return issues;
		}

		List<?> results = (List<?>) rawResults;
		int id = 1000;

		for (Object result : results) {
			if (!(result instanceof MockSecret)) {
				continue;
			}

			MockSecret secret = (MockSecret) result;
			ScanIssue issue = new ScanIssue();

			issue.setScanIssueId("SEC-" + id);
			issue.setTitle(secret.type);
			issue.setDescription(secret.description);
			issue.setSeverity(secret.severity);
			issue.setProblematicLineNumber(secret.line_number);
			issue.setRemediationAdvise("Remove " + secret.type +
				" from source code and use environment variables instead");
			issue.setSecretValue("***MASKED***");
			issue.setScanEngine(ScanEngine.SECRETS);
			id++;

			issues.add(issue);
		}

		return issues;
	}

	private boolean isRealSecretsResult(Object obj) {
		return obj != null && obj.getClass().getSimpleName().equals("SecretsRealtimeResults");
	}

	private List<ScanIssue> adaptRealSecretsResult(Object secretsResult) {
		List<ScanIssue> issues = new ArrayList<>();
		try {
			CxLogger.info(logTag + " ╔═══════════════════════════════════════════════════════╗");
			CxLogger.info(logTag + " ║ SECRETS SCANNER - ADAPTING REAL API RESULTS          ║");
			CxLogger.info(logTag + " ╚═══════════════════════════════════════════════════════╝");

			Method getSecrets = secretsResult.getClass().getMethod("getSecrets");
			List<?> secrets = (List<?>) getSecrets.invoke(secretsResult);

			if (secrets == null || secrets.isEmpty()) {
				CxLogger.info(logTag + " ℹ No secrets found in real result");
				return issues;
			}

			CxLogger.info(logTag + " Found " + secrets.size() + " secrets from API");

			int id = 1000;
			for (Object secret : secrets) {
				try {
					ScanIssue issue = new ScanIssue();

					String title = getSecretProperty(secret, "getTitle", String.class);
					String description = getSecretProperty(secret, "getDescription", String.class);

					CxLogger.info(logTag + " ─────────────────────────────────────────────────────");
					CxLogger.info(logTag + " Secret #" + id + ":");
					CxLogger.info(logTag + "   Title: " + title);
					CxLogger.info(logTag + "   Description: " + description);

					issue.setScanIssueId("SEC-" + id);
					issue.setTitle(title);
					issue.setDescription(description);
					issue.setSeverity(mapSecretSeverity(title));
					issue.setRemediationAdvise("Remove " + title +
						" from source code and use environment variables or secure vaults instead");
					issue.setSecretValue("***MASKED***");
					issue.setScanEngine(ScanEngine.SECRETS);

					// Extract precise location data from RealtimeLocation objects
					List<?> locations = getSecretProperty(secret, "getLocations", List.class);
					CxLogger.info(logTag + "   Locations: " + (locations != null ? locations.size() : 0) + " location(s)");

					if (locations != null && !locations.isEmpty()) {
						int locIndex = 0;
						for (Object locObj : locations) {
							try {
								Integer line = getLocationProperty(locObj, "getLine", Integer.class);
								Integer startIndex = getLocationProperty(locObj, "getStartIndex", Integer.class);
								Integer endIndex = getLocationProperty(locObj, "getEndIndex", Integer.class);

								CxLogger.info(logTag + "     Location " + locIndex + ":");
								CxLogger.info(logTag + "       Line: " + line);
								CxLogger.info(logTag + "       CharStart (absolute): " + startIndex);
								CxLogger.info(logTag + "       CharEnd (absolute): " + endIndex);
								CxLogger.info(logTag + "       Range length: " + (endIndex - startIndex) + " chars");

								com.checkmarx.eclipse.devassist.ui.findings.model.Location location =
									new com.checkmarx.eclipse.devassist.ui.findings.model.Location(
										line != null ? line : 0,
										startIndex != null ? startIndex : 0,
										endIndex != null ? endIndex : 0
									);
								issue.getLocations().add(location);

								CxLogger.info(logTag + "       ✓ Location added to ScanIssue");
								locIndex++;

							} catch (Exception e) {
								CxLogger.warning(logTag + "       ✗ Error extracting location: " + e.getMessage());
							}
						}
					} else {
						CxLogger.warning(logTag + "   ⚠ No locations found for secret!");
					}

					issues.add(issue);
					CxLogger.info(logTag + " ✓ Secret #" + id + " complete");
					id++;

				} catch (Exception e) {
					CxLogger.warning(logTag + " Error adapting secret: " + e.getMessage());
					e.printStackTrace();
				}
			}

			CxLogger.info(logTag + " ═══════════════════════════════════════════════════════");
			CxLogger.info(logTag + " ✓ TOTAL: Adapted " + issues.size() + " real secrets from server");
			CxLogger.info(logTag + " ═══════════════════════════════════════════════════════");

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
		if (secretType == null) return "MEDIUM";
		String lower = secretType.toLowerCase();
		if (lower.contains("private") || lower.contains("password") ||
			lower.contains("api_key") || lower.contains("token")) {
			return "CRITICAL";
		}
		if (lower.contains("bearer") || lower.contains("webhook")) {
			return "HIGH";
		}
		return "MEDIUM";
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
