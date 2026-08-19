package com.checkmarx.eclipse.devassist.backend;

import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.basescanner.ScannerService;

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
	private volatile boolean disposed = false;
	private final Object lock = new Object();

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
	 * Deregister and dispose all scanners (on project close).
	 * Synchronized to prevent race with getScannerService() lazy creation.
	 */
	public void deregisterAllScanners() {
		synchronized (lock) {
			CxLogger.info(LOG_TAG + " Deregistering all scanners for: " + project.getName());

			// Dispose each scanner
			scanners.forEach((type, scanner) -> {
				try {
					if (scanner instanceof AutoCloseable) {
						((AutoCloseable) scanner).close();
					}
					CxLogger.info(LOG_TAG + "Disposed scanner: " + type);
				} catch (Exception e) {
					CxLogger.warning(LOG_TAG + " Error disposing scanner " + type + ": " +
							e.getMessage());
				}
			});

			scanners.clear();
			disposed = true;
			CxLogger.info(LOG_TAG + " All scanners disposed");
		}
	}

	/**
	 * Get a scanner service by type.
	 * Lazily creates the scanner on first access.
	 *
	 * Synchronized with deregisterAllScanners() to prevent race:
	 * if project closes while scanner is being created, the new instance
	 * will be disposed immediately and not leak.
	 *
	 * @param type Scanner type (OSS, SECRETS, etc.)
	 * @return Scanner instance, or null if scanner type not supported
	 */
	public Object getScannerService(ScannerType type) {
		synchronized (lock) {
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
	}

	/**
	 * Create a scanner instance by type.
	 * Creates implementations of ScannerService that delegate to the new scanner
	 * commands.
	 *
	 * @param type Scanner type
	 * @return Scanner instance
	 */
	private Object createScannerInstance(ScannerType type) {
		try {
			CxLogger.info(LOG_TAG + " Creating scanner instance for: " + type.getDisplayName());
			Object scanner = null;

			switch (type) {
				case OSS:
					scanner = new OssScannerServiceImpl(project);
					break;
				case SECRETS:
					scanner = new SecretsScannerServiceImpl(project);
					break;
				case CONTAINERS:
					scanner = new ContainerScannerServiceImpl(project);
					break;
				case IAC:
					scanner = new IacScannerServiceImpl(project);
					break;
				case ASCA:
					scanner = new AscaScannerServiceImpl(project);
					break;
				default:
					return null;
			}

			if (scanner != null) {
				CxLogger.info(LOG_TAG + "Successfully created scanner: " + type.getDisplayName());
			} else {
				CxLogger.warning(LOG_TAG + "Scanner returned null: " + type.getDisplayName());
			}
			return scanner;
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + "Error creating scanner " + type.getDisplayName() + ": " + e.getMessage(), e);
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Inner class implementations of ScannerService that bridge to new scanner
	 * commands.
	 * These are minimal adapters that delegate to the proper scanner packages.
	 */

	private static class OssScannerServiceImpl implements ScannerService<Object> {
		private final com.checkmarx.eclipse.devassist.scanners.oss.OssScannerCommand command;
		private final com.checkmarx.eclipse.devassist.common.ScannerConfig config;

		OssScannerServiceImpl(IProject project) {
			this.command = new com.checkmarx.eclipse.devassist.scanners.oss.OssScannerCommand(project);
			this.config = com.checkmarx.eclipse.devassist.common.ScannerConfig.builder()
					.engineName("OSS")
					.build();
		}

		@Override
		public boolean shouldScanFile(String filePath) {
			return filePath != null && !filePath.isEmpty();
		}

		@Override
		public com.checkmarx.eclipse.devassist.common.ScanResult<Object> scan(String filePath) {
			try {
				// ✅ CRITICAL: Use the LIVE (possibly unsaved) editor buffer, not a
				// brand-new empty Document. A new Document() has no content, so
				// getFileContent() falls back to reading the file from DISK -
				// meaning unsaved edits (e.g. deleting a vulnerable line) would
				// never be seen by the scanner until the file is saved.
				org.eclipse.jface.text.IDocument liveDocument = com.checkmarx.eclipse.devassist.utils.DevAssistUtils
						.getLiveDocumentForFile(filePath);
				var result = command.scan(filePath,
						liveDocument != null ? liveDocument : new org.eclipse.jface.text.Document());
				return (com.checkmarx.eclipse.devassist.common.ScanResult<Object>) (Object) result;
			} catch (Exception e) {
				CxLogger.error("[OSS-SERVICE] Scan error: " + e.getMessage(), e);
				return null;
			}
		}

		@Override
		public com.checkmarx.eclipse.devassist.common.ScannerConfig getConfig() {
			return config;
		}

		@Override
		public void close() throws Exception {
			command.dispose();
		}
	}

	private static class SecretsScannerServiceImpl implements ScannerService<Object> {
		private final com.checkmarx.eclipse.devassist.scanners.secrets.SecretsScannerCommand command;
		private final com.checkmarx.eclipse.devassist.common.ScannerConfig config;

		SecretsScannerServiceImpl(IProject project) {
			this.command = new com.checkmarx.eclipse.devassist.scanners.secrets.SecretsScannerCommand(project);
			this.config = com.checkmarx.eclipse.devassist.common.ScannerConfig.builder()
					.engineName("SECRETS")
					.build();
		}

		@Override
		public boolean shouldScanFile(String filePath) {
			return filePath != null && !filePath.isEmpty();
		}

		@Override
		public com.checkmarx.eclipse.devassist.common.ScanResult<Object> scan(String filePath) {
			try {
				// ✅ CRITICAL: Use the LIVE (possibly unsaved) editor buffer - see
				// the identical fix/comment in OssScannerServiceImpl.scan() above.
				org.eclipse.jface.text.IDocument liveDocument = com.checkmarx.eclipse.devassist.utils.DevAssistUtils
						.getLiveDocumentForFile(filePath);
				var result = command.scan(filePath,
						liveDocument != null ? liveDocument : new org.eclipse.jface.text.Document());
				return (com.checkmarx.eclipse.devassist.common.ScanResult<Object>) (Object) result;
			} catch (Exception e) {
				CxLogger.error("[SECRETS-SERVICE] Scan error: " + e.getMessage(), e);
				return null;
			}
		}

		@Override
		public com.checkmarx.eclipse.devassist.common.ScannerConfig getConfig() {
			return config;
		}

		@Override
		public void close() throws Exception {
			command.dispose();
		}
	}

	private static class IacScannerServiceImpl implements ScannerService<Object> {
		private final com.checkmarx.eclipse.devassist.scanners.iac.IacScannerCommand command;
		private final com.checkmarx.eclipse.devassist.common.ScannerConfig config;

		IacScannerServiceImpl(IProject project) {
			this.command = new com.checkmarx.eclipse.devassist.scanners.iac.IacScannerCommand(project);
			this.config = com.checkmarx.eclipse.devassist.common.ScannerConfig.builder()
					.engineName("IAC")
					.build();
		}

		@Override
		public boolean shouldScanFile(String filePath) {
			return filePath != null && !filePath.isEmpty();
		}

		@Override
		public com.checkmarx.eclipse.devassist.common.ScanResult<Object> scan(String filePath) {
			try {
				// ✅ CRITICAL: Use the LIVE (possibly unsaved) editor buffer - see
				// the identical fix/comment in OssScannerServiceImpl.scan() above.
				org.eclipse.jface.text.IDocument liveDocument = com.checkmarx.eclipse.devassist.utils.DevAssistUtils
						.getLiveDocumentForFile(filePath);
				var result = command.scan(filePath,
						liveDocument != null ? liveDocument : new org.eclipse.jface.text.Document());
				return (com.checkmarx.eclipse.devassist.common.ScanResult<Object>) (Object) result;
			} catch (Exception e) {
				CxLogger.error("[IAC-SERVICE] Scan error: " + e.getMessage(), e);
				return null;
			}
		}

		@Override
		public com.checkmarx.eclipse.devassist.common.ScannerConfig getConfig() {
			return config;
		}

		@Override
		public void close() throws Exception {
			command.dispose();
		}
	}

	private static class AscaScannerServiceImpl implements ScannerService<Object> {
		private final com.checkmarx.eclipse.devassist.scanners.asca.AscaScannerCommand command;
		private final com.checkmarx.eclipse.devassist.common.ScannerConfig config;

		AscaScannerServiceImpl(IProject project) {
			this.command = new com.checkmarx.eclipse.devassist.scanners.asca.AscaScannerCommand(project);
			this.config = com.checkmarx.eclipse.devassist.common.ScannerConfig.builder()
					.engineName("ASCA")
					.build();
		}

		@Override
		public boolean shouldScanFile(String filePath) {
			return filePath != null && !filePath.isEmpty();
		}

		@Override
		public com.checkmarx.eclipse.devassist.common.ScanResult<Object> scan(String filePath) {
			try {
				// ✅ CRITICAL: Use the LIVE (possibly unsaved) editor buffer - see
				// the identical fix/comment in OssScannerServiceImpl.scan() above.
				org.eclipse.jface.text.IDocument liveDocument = com.checkmarx.eclipse.devassist.utils.DevAssistUtils
						.getLiveDocumentForFile(filePath);
				var result = command.scan(filePath,
						liveDocument != null ? liveDocument : new org.eclipse.jface.text.Document());
				return (com.checkmarx.eclipse.devassist.common.ScanResult<Object>) (Object) result;
			} catch (Exception e) {
				CxLogger.error("[ASCA-SERVICE] Scan error: " + e.getMessage(), e);
				return null;
			}
		}

		@Override
		public com.checkmarx.eclipse.devassist.common.ScannerConfig getConfig() {
			return config;
		}

		@Override
		public void close() throws Exception {
			command.dispose();
		}
	}

	private static class ContainerScannerServiceImpl implements ScannerService<Object> {
		private final com.checkmarx.eclipse.devassist.scanners.containers.ContainerScannerCommand command;
		private final com.checkmarx.eclipse.devassist.common.ScannerConfig config;

		ContainerScannerServiceImpl(IProject project) {
			this.command = new com.checkmarx.eclipse.devassist.scanners.containers.ContainerScannerCommand(project);
			this.config = com.checkmarx.eclipse.devassist.common.ScannerConfig.builder()
					.engineName("CONTAINERS")
					.build();
		}

		@Override
		public boolean shouldScanFile(String filePath) {
			return filePath != null && !filePath.isEmpty();
		}

		@Override
		public com.checkmarx.eclipse.devassist.common.ScanResult<Object> scan(String filePath) {
			try {
				// ✅ CRITICAL: Use the LIVE (possibly unsaved) editor buffer - see
				// the identical fix/comment in OssScannerServiceImpl.scan() above.
				org.eclipse.jface.text.IDocument liveDocument = com.checkmarx.eclipse.devassist.utils.DevAssistUtils
						.getLiveDocumentForFile(filePath);
				var result = command.scan(filePath,
						liveDocument != null ? liveDocument : new org.eclipse.jface.text.Document());
				return (com.checkmarx.eclipse.devassist.common.ScanResult<Object>) (Object) result;
			} catch (Exception e) {
				CxLogger.error("[CONTAINER-SERVICE] Scan error: " + e.getMessage(), e);
				return null;
			}
		}

		@Override
		public com.checkmarx.eclipse.devassist.common.ScannerConfig getConfig() {
			return config;
		}

		@Override
		public void close() throws Exception {
			command.dispose();
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
