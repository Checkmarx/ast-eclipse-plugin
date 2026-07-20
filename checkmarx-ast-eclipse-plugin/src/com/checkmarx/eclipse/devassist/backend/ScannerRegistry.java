package com.checkmarx.eclipse.devassist.backend;

import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Manages the lifecycle of scanner services for a project.
 *
 * Responsibilities:
 * - Create scanner instances when project opens
 * - Store scanner instances for reuse
 * - Dispose scanners when project closes
 *
 * This is a project-level service. Each open project gets its own registry.
 * Scanners are lazily initialized on first access.
 *
 * Mirrors the JetBrains ScannerRegistry pattern.
 */
public class ScannerRegistry {

	private static final String LOG_TAG = "[SCANNER-REGISTRY]";

	// Session property key for storing registry on project
	public static final String REGISTRY_KEY = ScannerRegistry.class.getName() + ".INSTANCE";

	private final IProject project;
	private final ConcurrentHashMap<String, Object> scanners = new ConcurrentHashMap<>();
	private boolean disposed = false;

	/**
	 * Create a registry for a project.
	 *
	 * @param project Eclipse project
	 */
	public ScannerRegistry(IProject project) {
		this.project = project;
		CxLogger.info(LOG_TAG + " Created for project: " + project.getName());
	}

	/**
	 * Initialize all available scanners.
	 *
	 * Called when project opens. Scanners are created but not yet active;
	 * activation is controlled by GlobalScannerController.
	 */
	public void registerAllScanners() {
		if (disposed) {
			CxLogger.warning(LOG_TAG + " Registry is disposed, cannot register scanners");
			return;
		}

		CxLogger.info(LOG_TAG + " Registering all scanners for: " + project.getName());

		// Scanners will be created lazily via getScannerService()
		// For now, just initialize placeholders to track scanner types
		ScannerType[] scannerTypes = {
			ScannerType.OSS,
			ScannerType.SECRETS,
			ScannerType.CONTAINERS,
			ScannerType.IAC,
			ScannerType.ASCA
		};

		for (ScannerType type : scannerTypes) {
			CxLogger.info(LOG_TAG + " ✓ Scanner registered: " + type);
		}
	}

	/**
	 * Deregister and dispose all scanners (on project close).
	 */
	public void deregisterAllScanners() {
		CxLogger.info(LOG_TAG + " Deregistering all scanners for: " + project.getName());

		// Dispose each scanner
		scanners.forEach((type, scanner) -> {
			try {
				if (scanner instanceof AutoCloseable) {
					((AutoCloseable) scanner).close();
				}
				CxLogger.info(LOG_TAG + " ✓ Disposed scanner: " + type);
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error disposing scanner " + type + ": " +
					e.getMessage());
			}
		});

		scanners.clear();
		disposed = true;
		CxLogger.info(LOG_TAG + " All scanners disposed");
	}

	/**
	 * Get a scanner service by type.
	 * Lazily creates the scanner on first access.
	 *
	 * @param type Scanner type (OSS, SECRETS, etc.)
	 * @return Scanner instance, or null if scanner type not supported
	 */
	public Object getScannerService(ScannerType type) {
		if (disposed) {
			CxLogger.warning(LOG_TAG + " Registry is disposed");
			return null;
		}

		return scanners.computeIfAbsent(type.name(), key -> {
			CxLogger.info(LOG_TAG + " Creating scanner: " + type);
			// Scanner creation will be implemented in Phase 2
			return createScannerInstance(type);
		});
	}

	/**
	 * Create a scanner instance by type.
	 * This is a factory method that will be filled in during Phase 2.
	 *
	 * @param type Scanner type
	 * @return Scanner instance
	 */
	private Object createScannerInstance(ScannerType type) {
		switch (type) {
		case OSS:
			// return new OssScannerService(project);
		case SECRETS:
			// return new SecretsScannerService(project);
		case CONTAINERS:
			// return new ContainerScannerService(project);
		case IAC:
			// return new IacScannerService(project);
		case ASCA:
			// return new AscaScannerService(project);
		default:
			return null;
		}
	}

	/**
	 * Check if a scanner is registered.
	 *
	 * @param type Scanner type
	 * @return true if scanner exists
	 */
	public boolean hasScannerService(ScannerType type) {
		return scanners.containsKey(type.name());
	}

	/**
	 * Get the project this registry belongs to.
	 *
	 * @return Eclipse project
	 */
	public IProject getProject() {
		return project;
	}

	/**
	 * Check if registry is disposed.
	 *
	 * @return true if disposed
	 */
	public boolean isDisposed() {
		return disposed;
	}

	/**
	 * Get statistics for debugging.
	 *
	 * @return Summary string
	 */
	public String getStatistics() {
		return "Project: " + project.getName() +
			", Scanners: " + scanners.size() +
			", Disposed: " + disposed;
	}

	/**
	 * Enum of available scanner types.
	 * Maps to the 5 scanner engines in Checkmarx.
	 */
	public enum ScannerType {
		OSS("Open Source Supply Chain"),
		SECRETS("Secrets Scanning"),
		CONTAINERS("Container Scanning"),
		IAC("Infrastructure as Code"),
		ASCA("Application Security Code Analysis");

		private final String displayName;

		ScannerType(String displayName) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return displayName;
		}
	}
}
