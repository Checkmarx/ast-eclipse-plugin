package com.checkmarx.eclipse.devassist.backend.scanner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.devassist.ui.findings.model.Location;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanEngine;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Scanner for Open Source Supply Chain (OSS) vulnerabilities.
 *
 * Scans dependency/manifest files:
 * - package.json, package-lock.json (npm)
 * - pom.xml (Maven)
 * - go.mod, go.sum (Go)
 * - requirements.txt (Python)
 * - Gemfile (Ruby)
 * - Cargo.toml (Rust)
 * - Pipfile (Pipenv)
 * - etc.
 *
 * Detects vulnerable open source packages and their CVEs.
 */
public class OssScannerService extends BaseScannerService {

	private static final String[] MANIFEST_FILE_PATTERNS = {
		// npm
		"package.json", "package-lock.json", "npm-shrinkwrap.json",
		// Maven
		"pom.xml",
		// Go
		"go.mod", "go.sum",
		// Python
		"requirements.txt", "Pipfile", "Pipfile.lock", "setup.py",
		// Ruby
		"Gemfile", "Gemfile.lock",
		// Rust
		"Cargo.toml", "Cargo.lock",
		// PHP
		"composer.json", "composer.lock",
		// .NET
		"packages.config", ".csproj",
		// Node.js
		"yarn.lock", ".npm"
	};

	/**
	 * Create an OSS scanner for a project.
	 *
	 * @param project Eclipse project
	 */
	public OssScannerService(IProject project) {
		super(project);
	}

	/**
	 * Check if file is a manifest file that OSS scanner can scan.
	 *
	 * @param filePath File path to check
	 * @return true if file is a manifest file
	 */
	@Override
	protected boolean isFileTypeSupported(String filePath) {
		if (filePath == null) {
			return false;
		}

		String lowerPath = filePath.toLowerCase();
		String fileName = new java.io.File(filePath).getName().toLowerCase();

		// Check file name patterns
		for (String pattern : MANIFEST_FILE_PATTERNS) {
			if (fileName.equals(pattern) || lowerPath.endsWith(pattern)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Execute OSS scan using real Checkmarx server API via reflection.
	 *
	 * Uses reflection to call CxWrapperFactory at runtime (available in classpath
	 * even though not available at compile-time due to Tycho limitations).
	 *
	 * @param filePath Manifest file to scan
	 * @return Real OssRealtimeResults from Checkmarx server, or null if API unavailable
	 * @throws Exception if scan fails
	 */
	@Override
	protected Object executeNativeScanner(String filePath) throws Exception {
		System.out.println(logTag + " [OSS-SCAN] ╔════════════════════════════════════════════╗");
		System.out.println(logTag + " [OSS-SCAN] ║ OSS SCANNER: EXECUTING SCAN                 ║");
		System.out.println(logTag + " [OSS-SCAN] ╚════════════════════════════════════════════╝");
		System.out.println(logTag + " [OSS-SCAN] File: " + filePath);
		CxLogger.info(logTag + " Executing OSS scan on: " + filePath);

		try {
			System.out.println(logTag + " [OSS-SCAN] Calling real OSS API via reflection...");
			CxLogger.info(logTag + " Calling real OSS API via reflection (using original file path)...");

			// Call real Checkmarx API with ORIGINAL file path (not temp file)
			// Some APIs (like OSS) require the original manifest file, not a copy
			Object result = callOssApiViaReflection(filePath);
			if (result == null) {
				System.out.println(logTag + " [OSS-SCAN] ✗ OSS API returned NULL!");
				CxLogger.warning(logTag + " OSS API returned null");
				return null;
			}

			System.out.println(logTag + " [OSS-SCAN] ✓ Got results from server (type: " + result.getClass().getSimpleName() + ")");
			CxLogger.info(logTag + " ✓ Got REAL results from server");
			System.out.println(logTag + " [OSS-SCAN] ════════════════════════════════════════════");
			return result;

		} catch (Exception e) {
			System.err.println(logTag + " [OSS-SCAN] ✗ ERROR: " + e.getMessage());
			e.printStackTrace();
			CxLogger.error(logTag + " Error: " + e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Call OSS scan via reflection on CxWrapper from ast-cli-java-wrapper JAR.
	 * Works around Tycho's compile-time dependency issues.
	 *
	 * Method signature: ossRealtimeScan(String filePath, String ignorePath)
	 */
	private Object callOssApiViaReflection(String filePath) {
		try {
			// Load CxWrapper class
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

			// Call ossRealtimeScan(filePath, ignorePath)
			Method scanMethod = wrapperClass.getMethod("ossRealtimeScan", String.class, String.class);
			Object scanResult = scanMethod.invoke(wrapper, filePath, "");

			CxLogger.info(logTag + " ✓ Called real OSS API successfully");
			return scanResult;

		} catch (ClassNotFoundException e) {
			CxLogger.warning(logTag + " CxWrapper not available in classpath: " + e.getMessage());
			return null;
		} catch (Exception e) {
			CxLogger.error(logTag + " Reflection error calling OSS API: " + e.getMessage(), e);
			return null;
		}
	}


	/**
	 * Adapt OSS scan results to ScanIssue model.
	 *
	 * Handles both real OssRealtimeResults from API and legacy mock data.
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

		// Try to adapt as real OssRealtimeResults first
		if (isRealOssResult(rawResults)) {
			return adaptRealOssResult(rawResults);
		}

		// Fall back to mock data if available
		if (!(rawResults instanceof List)) {
			return issues;
		}

		List<?> results = (List<?>) rawResults;
		int id = 1;

		for (Object result : results) {
			if (!(result instanceof MockOssVulnerability)) {
				continue;
			}

			MockOssVulnerability vuln = (MockOssVulnerability) result;
			ScanIssue issue = new ScanIssue();

			issue.setScanIssueId("OSS-" + id++);
			issue.setTitle(vuln.package_name + ": " + vuln.title);
			issue.setDescription(vuln.title);
			issue.setSeverity(vuln.severity.toUpperCase());
			issue.setPackageVersion(vuln.vulnerable_version);
			issue.setPackageManager(detectPackageManager(vuln.package_name));
			issue.setCve(vuln.cve);
			issue.setRemediationAdvise("Update to version " + vuln.fixed_version +
				" or later");
			issue.setProblematicLineNumber(1);
			issue.setScanEngine(ScanEngine.OSS);

			issues.add(issue);
		}

		return issues;
	}

	private boolean isRealOssResult(Object obj) {
		return obj != null && obj.getClass().getSimpleName().equals("OssRealtimeResults");
	}

	private List<ScanIssue> adaptRealOssResult(Object ossResult) {
		List<ScanIssue> issues = new ArrayList<>();
		try {
			System.out.println(logTag + " [OSS-ADAPT] ╔════════════════════════════════════════════╗");
			System.out.println(logTag + " [OSS-ADAPT] ║ ADAPTING OSS RESULTS                       ║");
			System.out.println(logTag + " [OSS-ADAPT] ╚════════════════════════════════════════════╝");
			System.out.println(logTag + " [OSS-ADAPT] Result type: " + ossResult.getClass().getName());
			System.out.println(logTag + " [OSS-ADAPT] Available methods on result object:");
			for (java.lang.reflect.Method m : ossResult.getClass().getMethods()) {
				if (!m.getName().startsWith("java")) {
					System.out.println(logTag + " [OSS-ADAPT]   - " + m.getName() + "() returns " + m.getReturnType().getSimpleName());
				}
			}

			// Try to get the results list from OssRealtimeResults
			Method getPackagesMethod = ossResult.getClass().getMethod("getPackages");
			List<?> packages = (List<?>) getPackagesMethod.invoke(ossResult);

			if (packages == null || packages.isEmpty()) {
				System.out.println(logTag + " [OSS-ADAPT] ✗ No packages found in OSS result!");
				return issues;
			}

			System.out.println(logTag + " [OSS-ADAPT] ✓ Found " + packages.size() + " packages with vulnerabilities");

			int id = 1;
			for (Object pkg : packages) {
				try {
					System.out.println(logTag + " [OSS-ADAPT] [PACKAGE " + id + "] Adapting package...");

					// Dump available methods on first package
					if (id == 1) {
						System.out.println(logTag + " [OSS-ADAPT] [PACKAGE 1] Available methods on package object:");
						for (Method m : pkg.getClass().getMethods()) {
							if (!m.getName().startsWith("java")) {
								System.out.println(logTag + " [OSS-ADAPT] [PACKAGE 1]   - " + m.getName() + "() returns " + m.getReturnType().getSimpleName());
							}
						}
					}

					ScanIssue issue = new ScanIssue();

					// Extract package properties using reflection - try different method names
					String packageName = getOssProperty(pkg, "getPackageName", String.class);
					if (packageName == null) {
						packageName = getOssProperty(pkg, "getName", String.class);
					}

					String version = getOssProperty(pkg, "getVersion", String.class);
					if (version == null) {
						version = getOssProperty(pkg, "getCurrentVersion", String.class);
					}

					String severity = getOssProperty(pkg, "getSeverity", String.class);
					if (severity == null) {
						severity = getOssProperty(pkg, "getHighestSeverity", String.class);
					}

					String cve = getOssProperty(pkg, "getCVE", String.class);
					if (cve == null) {
						cve = getOssProperty(pkg, "getCves", String.class);
					}

					String fixedVersion = getOssProperty(pkg, "getFixedVersions", String.class);
					if (fixedVersion == null) {
						fixedVersion = getOssProperty(pkg, "getRecommendedVersion", String.class);
					}

					String description = getOssProperty(pkg, "getDescription", String.class);

					System.out.println(logTag + "     Package: " + packageName + " v" + version + " (severity: " + severity + ")");

					issue.setScanIssueId("OSS-" + id);
					issue.setTitle(packageName + ": Vulnerable Package Detected");
					issue.setDescription(description != null ? description : "Vulnerable version detected");
					issue.setSeverity(severity != null ? severity : "MEDIUM");
					issue.setPackageVersion(version);
					issue.setPackageManager(detectPackageManager(packageName));
					issue.setCve(cve);
					issue.setRemediationAdvise("Update to version " + (fixedVersion != null ? fixedVersion : "latest"));
					issue.setProblematicLineNumber(1);
					issue.setScanEngine(ScanEngine.OSS);

					Location location = new Location();
					location.setLine(1);
					location.setStartIndex(0);
					location.setEndIndex(0);
					issue.getLocations().add(location);

					issues.add(issue);
					id++;

					System.out.println(logTag + " [OSS-ADAPT] [PACKAGE " + id + "] ✓ Added issue: " + packageName);

				} catch (Exception e) {
					System.err.println(logTag + " [OSS-ADAPT] [PACKAGE " + id + "] ✗ Error adapting OSS package: " + e.getMessage());
					e.printStackTrace();
				}
			}

			System.out.println(logTag + " [OSS-ADAPT] ════════════════════════════════════════════");
			System.out.println(logTag + " [OSS-ADAPT] ✓ Adapted " + issues.size() + " real OSS issues from server");
			System.out.println(logTag + " [OSS-ADAPT] ════════════════════════════════════════════");

		} catch (Exception e) {
			System.err.println(logTag + " [OSS-ADAPT] ✗ Error adapting real OSS result: " + e.getMessage());
			e.printStackTrace();
		}
		return issues;
	}

	@SuppressWarnings("unchecked")
	private <T> T getOssProperty(Object pkg, String methodName, Class<T> returnType) {
		try {
			Method method = pkg.getClass().getMethod(methodName);
			return (T) method.invoke(pkg);
		} catch (Exception e) {
			CxLogger.warning(logTag + " Could not get property " + methodName + ": " + e.getMessage());
			return null;
		}
	}

	/**
	 * Mock OSS vulnerability data for demo.
	 */
	static class MockOssVulnerability {
		String package_name;
		String title;
		String severity;
		String cve;
		String vulnerable_version;
		String fixed_version;
		String remediation;

		MockOssVulnerability(String pkg, String title, String severity,
			String cve, String fixed, String remediation) {
			this.package_name = pkg;
			this.title = title;
			this.severity = severity;
			this.cve = cve;
			this.vulnerable_version = extractVersion(pkg);
			this.fixed_version = fixed;
			this.remediation = remediation;
		}

		private static String extractVersion(String pkg) {
			int colonIdx = pkg.lastIndexOf(':');
			if (colonIdx > 0 && colonIdx < pkg.length() - 1) {
				return pkg.substring(colonIdx + 1);
			}
			return "unknown";
		}
	}

	@Override
	public String getDisplayName() {
		return "Open Source Supply Chain";
	}

	@Override
	public ScannerType getScannerType() {
		return ScannerType.OSS;
	}

	/**
	 * Detect package manager from manifest file name.
	 *
	 * @param filePath Manifest file path
	 * @return Package manager name
	 */
	private String detectPackageManager(String filePath) {
		String fileName = new java.io.File(filePath).getName().toLowerCase();

		if (fileName.contains("package")) return "npm";
		if (fileName.contains("pom")) return "maven";
		if (fileName.contains("go.")) return "go";
		if (fileName.contains("requirements") || fileName.contains("pipfile"))
			return "python";
		if (fileName.contains("gemfile")) return "ruby";
		if (fileName.contains("cargo")) return "rust";
		if (fileName.contains("composer")) return "php";
		if (fileName.contains("csproj") || fileName.contains("packages"))
			return "dotnet";

		return "unknown";
	}
}
