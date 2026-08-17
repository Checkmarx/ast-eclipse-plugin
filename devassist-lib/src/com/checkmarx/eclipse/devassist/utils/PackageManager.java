package com.checkmarx.eclipse.devassist.utils;

import java.util.List;

import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * The PackageManager enum represents various package managers used in software development.
 * Each constant corresponds to a specific package manager, and the enum provides utility methods for mapping and checking supported package managers.
 */
public enum PackageManager {

	DOTNET("dotnet", ManifestFilePattern.DOTNET),
	GRADLE("gradle", ManifestFilePattern.GRADLE),
	MAVEN("mvn", ManifestFilePattern.MAVEN),
	SBT("sbt", ManifestFilePattern.SBT),
	NPM("npm", ManifestFilePattern.NPM),
	GO("go", ManifestFilePattern.GO),
	PYTHON("python", ManifestFilePattern.PYTHON),
	BOWER("bower", ManifestFilePattern.BOWER),
	COCOAPODS("cocoapods", ManifestFilePattern.COCOAPODS),
	CARTHAGE("carthage", ManifestFilePattern.CARTHAGE),
	SWIFT("swift", ManifestFilePattern.SWIFT),
	DART("dart", ManifestFilePattern.DART),
	RUBY("ruby", ManifestFilePattern.RUBY),
	PHP("php", ManifestFilePattern.PHP),
	UNKNOWN("unknown", null);

	private String packageManager;
	private ManifestFilePattern manifestPattern;

	PackageManager(String packageManager, ManifestFilePattern pattern) {
		this.packageManager = packageManager;
		this.manifestPattern = pattern;
	}
	
	public String getPackageManager() {
		return packageManager;
	}
	
	public ManifestFilePattern getManifestPattern() {
		return manifestPattern;
	}

	/**
	 * Maps a string representation of a package manager to its corresponding PackageManager enum constant.
	 * @param packageManager
	 * @return
	 */
	public static PackageManager fromString(String packageManager) {
		for (PackageManager pm : PackageManager.values()) {
			if (pm.packageManager.equalsIgnoreCase(packageManager)) {
				return pm;
			}
		}
		return UNKNOWN;
	}
	
	/**
	 * Checks if a given string representation of a package manager is supported by the system.
	 * @param packageManager
	 * @return
	 */
	public static boolean isSupportedPackageManager(String packageManager) {
		for (PackageManager pm : PackageManager.values()) {
			if (pm.packageManager.equalsIgnoreCase(packageManager)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Maps a given package manager to its corresponding remediation format.
	 * For example, Gradle and SBT are mapped to Maven, while CocoaPods and Carthage are mapped to Swift.
	 * If the package manager is not recognized or is null/empty, it returns the original input.
	 *
	 * @param packageManager The string representation of the package manager to be mapped.
	 * @return The corresponding remediation format for the given package manager.
	 */
	public static String mapToRemediationFormat(String packageManager) {
		if (packageManager == null || packageManager.isEmpty()) {
			CxLogger.warning("[PACKAGE-MANAGER] Package manager is null or empty, returning as is.");
			return packageManager;
		}
		PackageManager pm = fromString(packageManager.toLowerCase());

		switch (pm) {
		case GRADLE:
		case SBT:
			return MAVEN.getPackageManager();
		case COCOAPODS:
		case CARTHAGE:
			return SWIFT.getPackageManager();
		default:
			return packageManager;
		}
	}


	 /**
     * Infers companion lock file names based on the manifest file name.
     * Some manifests may have multiple companion files (e.g., package.json has both package-lock.json and yarn.lock).
     *
     * @param fileName name of the manifest file
     * @return list of companion file names; empty list if no companions are defined
     */
    public static List<String> getCompanionFileNames(String fileName) {
        // npm/Yarn - support both package-lock.json (npm) and yarn.lock (yarn)
        if (fileName.equals("package.json")) {
            return List.of(CompanionFileType.PACKAGE_LOCK_JSON.getCompFileName(), CompanionFileType.YARN_LOCK.getCompFileName());
        }

        // .NET
        if (fileName.contains(".csproj")) {
        	return getCompanionFileNamesByType(CompanionFileType.PACKAGES_LOCK_JSON);
        }

        // Swift Package Manager (AST-165765)
        if (fileName.equals("Package.swift")) {
            return getCompanionFileNamesByType(CompanionFileType.PACKAGE_RESOLVED);
        }
        if (fileName.startsWith("Package@swift-") && fileName.endsWith(".swift")) {
            return List.of(fileName.replace(".swift", ".resolved"));
        }

        // CocoaPods (AST-165761)
        if (fileName.equals("Podfile")) {
            return getCompanionFileNamesByType(CompanionFileType.PODFILE_LOCK);
        }

        // Carthage
        if (fileName.equals("Cartfile") || fileName.equals("Cartfile.private")) {
            return getCompanionFileNamesByType(CompanionFileType.CARTFILE_RESOLVED);
        }

        // Ruby Bundler
        if (fileName.equals("Gemfile")) {
            return getCompanionFileNamesByType(CompanionFileType.GEMFILE_LOCK);
        }

        // PHP Composer
        if (fileName.equals("composer.json")) {
        	return getCompanionFileNamesByType(CompanionFileType.COMPOSER_LOCK);
        }

        // Python Poetry
        if (fileName.equals("pyproject.toml")) {
        	return getCompanionFileNamesByType(CompanionFileType.POETRY_LOCK);
        }

        // Dart/Flutter Pub
        if (fileName.equals("pubspec.yaml")) {
        	return getCompanionFileNamesByType(CompanionFileType.PUBSPEC_LOCK);
        }
        return List.of();
    }
    
    /**
     * Enum representing companion lock file types for various package managers.
     */
    static enum CompanionFileType {
    	
		PACKAGE_LOCK_JSON("package-lock.json"),
		YARN_LOCK("yarn.lock"),
		PACKAGES_LOCK_JSON("packages.lock.json"),
		PACKAGE_RESOLVED("Package.resolved"),
		PODFILE_LOCK("Podfile.lock"),
		CARTFILE_RESOLVED("Cartfile.resolved"),
		GEMFILE_LOCK("Gemfile.lock"),
		COMPOSER_LOCK("composer.lock"),
		POETRY_LOCK("poetry.lock"),
		PUBSPEC_LOCK("pubspec.lock");
    	
    	private String compFileName;

		CompanionFileType(String compFileName) {
			this.compFileName = compFileName;
		}
		
		public String getCompFileName() {
			return compFileName;
		}
	}
    
    public static List<String> getCompanionFileNamesByType(CompanionFileType type) {
		if (type == null) {
			return List.of();
		}
		return List.of(type.getCompFileName());
	}
    
    /**
	 * Enum representing manifest file patterns for various package managers.
	 * Each constant corresponds to a specific package manager and holds a list of file patterns used to identify manifest files.
	 */
    static enum ManifestFilePattern {
    	
    	DOTNET(List.of("**/Directory.Packages.props", "**/packages.config","**/*.csproj")),
		GRADLE(List.of("**/*.gradle", "**/*.gradle.kts", "**/libs.versions.toml")),
    	MAVEN(List.of("**/pom.xml")),
    	SBT(List.of("**/*.sbt")),
    	NPM(List.of("**/package.json")),
    	GO(List.of("**/go.mod")),
		PYTHON(List.of("**/requirement*.txt", "**/constraints.txt", "**/constraints-*.txt", "**/pyproject.toml",
				"**/setup.cfg", "**/setup.py")),
		BOWER(List.of("**/bower.json")),
		COCOAPODS(List.of("**/Podfile", "**/*.podspec", "**/*.podspec.json")),
		CARTHAGE(List.of("**/Cartfile", "**/Cartfile.private")),
		SWIFT(List.of("**/Package.swift", "**/Package@swift-*.swift")),
    	DART(List.of("**/pubspec.yaml")),
    	RUBY(List.of("**/Gemfile")),
    	PHP(List.of("**/composer.json"));

    	private List<String> patterns;
    	
		ManifestFilePattern(List<String> filePatterns) {
			this.patterns = filePatterns;
		}
    	
		public List<String> getPatterns() {
			return patterns;
		}
    }
    
    /**
	 * Retrieves all defined manifest file patterns across all package managers.
	 * If a package manager has no defined patterns, a warning is logged.
	 *
	 * @return a list of all manifest file patterns
	 */
	public static List<String> getAllPatterns() {

		List<String> allPatterns = new java.util.ArrayList<>();

		for (ManifestFilePattern pattern : ManifestFilePattern.values()) {
			if (pattern.getPatterns() == null || pattern.getPatterns().isEmpty()) {
				CxLogger.warning("[PACKAGE-MANAGER] ManifestFilePattern " + pattern.name() + " has no defined patterns.");
			}
			allPatterns.addAll(pattern.getPatterns());
		}
		return allPatterns;
	}
		
}
