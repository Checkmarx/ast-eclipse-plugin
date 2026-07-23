package com.checkmarx.eclipse.devassist.backend.scanner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.devassist.backend.DevAssistUtils;
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
	 * Follows JetBrains pattern: one ScanIssue per package with multiple Vulnerability objects.
	 *
	 * @param rawResults Raw results from executeNativeScanner()
	 * @param filePath Original file path being scanned (for stable ID generation)
	 * @return List of ScanIssue objects
	 */
	@Override
	protected List<ScanIssue> adaptResults(Object rawResults, String filePath) {
		List<ScanIssue> issues = new ArrayList<>();

		if (rawResults == null) {
			return issues;
		}

		// Try to adapt as real OssRealtimeResults first
		if (isRealOssResult(rawResults)) {
			return adaptRealOssResult(rawResults, filePath);
		}

		// Fall back to mock data if available
		if (!(rawResults instanceof List)) {
			return issues;
		}

		List<?> results = (List<?>) rawResults;

		for (Object result : results) {
			if (!(result instanceof MockOssVulnerability)) {
				continue;
			}

			MockOssVulnerability vuln = (MockOssVulnerability) result;
			ScanIssue issue = new ScanIssue();

			// Get line number from location data (fallback to 1 if not available)
			int lineNumber = 1;

			// JetBrains pattern: generate stable ID using line, package info, and filename
			String actualFileName = "Unknown";
			if (filePath != null && !filePath.isEmpty()) {
				actualFileName = new java.io.File(filePath).getName();
			}

			String packageManager = detectPackageManager(vuln.package_name);
			String scanIssueId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
				lineNumber,
				packageManager + vuln.package_name,
				vuln.vulnerable_version
			);

			issue.setScanIssueId(scanIssueId);
			issue.setTitle(vuln.package_name);
			issue.setDescription(vuln.title);
			String normalizedSeverity = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(vuln.severity);
			issue.setSeverity(normalizedSeverity);
			issue.setPackageVersion(vuln.vulnerable_version);
			issue.setPackageManager(packageManager);
			issue.setCve(vuln.cve);
			issue.setRemediationAdvise("Update to version " + vuln.fixed_version + " or later");
			issue.setProblematicLineNumber(lineNumber);
			issue.setScanEngine(ScanEngine.OSS);

			// Add location for decoration (gutter icons, underlines)
			// For mock data, mark the first line with a reasonable range for underline
			Location mockLocation = new Location();
			mockLocation.setLine(lineNumber);
			mockLocation.setStartIndex(0);
			mockLocation.setEndIndex(100);  // Decorator will use this to underline up to 100 chars
			issue.getLocations().add(mockLocation);

			// JetBrains pattern: create Vulnerability object for each CVE/issue
			com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability vulnerability =
				new com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability();
			vulnerability.setVulnerabilityId(scanIssueId);
			vulnerability.setTitle(vuln.title);
			vulnerability.setDescription(vuln.title);
			vulnerability.setSeverity(normalizedSeverity);
			vulnerability.setCve(vuln.cve);
			issue.getVulnerabilities().add(vulnerability);

			issues.add(issue);
		}

		return issues;
	}

	private boolean isRealOssResult(Object obj) {
		return obj != null && obj.getClass().getSimpleName().equals("OssRealtimeResults");
	}

	private List<ScanIssue> adaptRealOssResult(Object ossResult, String filePath) {
		List<ScanIssue> issues = new ArrayList<>();
		try {
			System.out.println(logTag + " [OSS-ADAPT] ╔════════════════════════════════════════════╗");
			System.out.println(logTag + " [OSS-ADAPT] ║ ADAPTING OSS RESULTS                       ║");
			System.out.println(logTag + " [OSS-ADAPT] ╚════════════════════════════════════════════╝");
			System.out.println(logTag + " [OSS-ADAPT] Result type: " + ossResult.getClass().getName());

			// Try to get the results list from OssRealtimeResults
			Method getPackagesMethod = ossResult.getClass().getMethod("getPackages");
			List<?> packages = (List<?>) getPackagesMethod.invoke(ossResult);

			if (packages == null || packages.isEmpty()) {
				System.out.println(logTag + " [OSS-ADAPT] ✗ No packages found in OSS result!");
				return issues;
			}

			System.out.println(logTag + " [OSS-ADAPT] ✓ Found " + packages.size() + " packages with vulnerabilities");

			// Extract actual filename from filePath (not temp file name)
			String actualFileName = "Unknown";
			if (filePath != null && !filePath.isEmpty()) {
				actualFileName = new java.io.File(filePath).getName();
			}

			int packageCount = 0;
			for (Object pkg : packages) {
				try {
					packageCount++;
					System.out.println(logTag + " [OSS-ADAPT] [PACKAGE " + packageCount + "] Adapting package...");

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

					String severity = getOssProperty(pkg, "getStatus", String.class);
					if (severity == null) {
						severity = getOssProperty(pkg, "getStatus", String.class);
					}

					String description = getOssProperty(pkg, "getDescription", String.class);
					String packageManager = detectPackageManager(packageName);

					// JetBrains pattern: generate stable ID using line, package info, and version
					// Line number is always 1 for manifest files (no specific line for OSS)
					String scanIssueId = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.generateUniqueId(
						1,
						packageManager + packageName,
						version
					);

					issue.setScanIssueId(scanIssueId);
					issue.setTitle(packageName);
					issue.setDescription(description != null ? description : "Vulnerable version detected");
					String normalizedSeverity = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(severity != null ? severity : "Medium");
					issue.setSeverity(normalizedSeverity);
					issue.setPackageVersion(version);
					issue.setPackageManager(packageManager);
					issue.setProblematicLineNumber(1);
					issue.setScanEngine(ScanEngine.OSS);

					// **JetBrains Pattern: Extract locations from API response for proper decoration**
					// OssRealtimeResults provides RealtimeLocation objects with actual start/end indices
					// This allows the decorator to draw underlines and gutter icons correctly
					List<?> locationsFromApi = getOssProperty(pkg, "getLocations", List.class);
					if (locationsFromApi != null && !locationsFromApi.isEmpty()) {
						for (Object locObj : locationsFromApi) {
							try {
								Integer locLine = getOssProperty(locObj, "getLine", Integer.class);
								Integer locStart = getOssProperty(locObj, "getStartIndex", Integer.class);
								Integer locEnd = getOssProperty(locObj, "getEndIndex", Integer.class);

								// OSS API returns 0-based line numbers, convert to 1-based
								int lineNum = (locLine != null ? locLine : 0) + 1;

								Location location = new Location();
								location.setLine(lineNum);
								location.setStartIndex(locStart != null ? locStart : 0);
								location.setEndIndex(locEnd != null ? locEnd : 0);
								issue.getLocations().add(location);

								CxLogger.info(logTag + " Added location from API: line=" + lineNum +
									", start=" + location.getStartIndex() + ", end=" + location.getEndIndex());

							} catch (Exception e) {
								CxLogger.warning(logTag + " Error extracting location: " + e.getMessage());
							}
						}
					}

					// Fallback: if no locations from API, create a default location for the first line
					if (issue.getLocations().isEmpty()) {
						Location location = new Location();
						location.setLine(1);
						location.setStartIndex(0);
						// For manifest files without specific location data, mark the entire first line
						location.setEndIndex(100);  // Decorator will use this for underline range
						issue.getLocations().add(location);
						CxLogger.info(logTag + " Using fallback location for package: " + packageName);
					}

					// JetBrains pattern: create Vulnerability objects for each CVE/vulnerability in package
					List<?> vulnerabilities = getOssProperty(pkg, "getVulnerabilities", List.class);
					if (vulnerabilities != null && !vulnerabilities.isEmpty()) {
						for (Object vulnObj : vulnerabilities) {
							try {
								String vulnCve = getOssProperty(vulnObj, "getCve", String.class);
								String vulnDescription = getOssProperty(vulnObj, "getDescription", String.class);
								String vulnSeverity = getOssProperty(vulnObj, "getSeverity", String.class);
								String fixVersion = getOssProperty(vulnObj, "getFixVersion", String.class);

								String normalizedVulnSeverity = DevAssistUtils.normalizeSeverity(vulnSeverity != null ? vulnSeverity : "Medium");

								com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability vulnerability =
									new com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability();
								vulnerability.setVulnerabilityId(scanIssueId);
								vulnerability.setTitle(vulnCve != null ? vulnCve : "OSS Vulnerability");
								vulnerability.setDescription(vulnDescription);
								vulnerability.setSeverity(normalizedVulnSeverity);
								vulnerability.setCve(vulnCve);
								issue.getVulnerabilities().add(vulnerability);

								CxLogger.info(logTag + " Added vulnerability: " + vulnCve);

							} catch (Exception e) {
								CxLogger.warning(logTag + " Error adapting OSS vulnerability: " + e.getMessage());
							}
						}
					}

					// If no vulnerabilities found, create a default one
					if (issue.getVulnerabilities().isEmpty()) {
						com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability vulnerability =
							new com.checkmarx.eclipse.devassist.ui.findings.model.Vulnerability();
						vulnerability.setVulnerabilityId(scanIssueId);
						vulnerability.setTitle("Vulnerable Package");
						vulnerability.setDescription(description);
						vulnerability.setSeverity(normalizedSeverity);
						issue.getVulnerabilities().add(vulnerability);
					}

					// Set CVE from first vulnerability if available
					if (!issue.getVulnerabilities().isEmpty()) {
						issue.setCve(issue.getVulnerabilities().get(0).getCve());
					}

					// Set remediation advice
					String fixedVersion = getOssProperty(pkg, "getFixedVersions", String.class);
					if (fixedVersion == null) {
						fixedVersion = getOssProperty(pkg, "getRecommendedVersion", String.class);
					}
					issue.setRemediationAdvise("Update to version " + (fixedVersion != null ? fixedVersion : "latest"));

					issues.add(issue);

					System.out.println(logTag + " [OSS-ADAPT] [PACKAGE " + packageCount + "] ✓ Added issue: " + packageName +
						" with " + issue.getVulnerabilities().size() + " vulnerabilities");

				} catch (Exception e) {
					System.err.println(logTag + " [OSS-ADAPT] [PACKAGE " + packageCount + "] ✗ Error adapting OSS package: " + e.getMessage());
					e.printStackTrace();
					CxLogger.error(logTag + " Error adapting OSS package: " + e.getMessage(), e);
				}
			}		

		} catch (Exception e) {
			System.err.println(logTag + " [OSS-ADAPT] ✗ Error adapting real OSS result: " + e.getMessage());
			e.printStackTrace();
			CxLogger.error(logTag + " Error adapting real OSS result: " + e.getMessage(), e);
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
