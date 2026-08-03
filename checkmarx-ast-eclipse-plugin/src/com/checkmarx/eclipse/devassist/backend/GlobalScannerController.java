package com.checkmarx.eclipse.devassist.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Application-level singleton managing global scanner state.
 *
 * Responsibilities:
 * - Track which scanners are enabled/disabled globally
 * - Sync with user preferences/settings
 * - Notify all open projects when scanner state changes
 * - Provide query methods for scanner availability
 *
 * This is an application-scoped service (one instance for entire Eclipse).
 * Each project's ScannerRegistry checks this controller before executing scans.
 *
 * Mirrors the JetBrains GlobalScannerController pattern.
 */
public class GlobalScannerController {

	private static final String LOG_TAG = "[GLOBAL-SCANNER]";
	private static GlobalScannerController instance;

	// Global enable/disable state for each scanner
	private final ConcurrentHashMap<ScannerType, Boolean> scannerState =
		new ConcurrentHashMap<>();

	// Listeners notified when scanner state changes
	private final List<ScannerStateListener> stateListeners = new ArrayList<>();

	// State manager for persistence
	private final com.checkmarx.eclipse.devassist.state.ScannerStateManager stateManager =
		new com.checkmarx.eclipse.devassist.state.ScannerStateManager();

	// Preference change listener to reload state when preferences are saved
	private final org.eclipse.jface.util.IPropertyChangeListener prefChangeListener =
		event -> {
			if ("scannerPreferencesChanged".equals(event.getProperty())) {
				reloadStateFromPreferences();
			}
		};

	/**
	 * Get the global singleton instance.
	 * Lazily creates on first access.
	 *
	 * @return Global scanner controller
	 */
	public synchronized static GlobalScannerController getInstance() {
		if (instance == null) {
			instance = new GlobalScannerController();
			instance.initializeDefaults();
			instance.registerPreferenceListener();
		}
		return instance;
	}

	/**
	 * Initialize scanner state from preferences.
	 */
	private void initializeDefaults() {
		CxLogger.info(LOG_TAG + " Initializing scanner state from preferences");

		com.checkmarx.eclipse.devassist.state.ScannerState state = stateManager.loadState();

		for (ScannerType type : ScannerType.values()) {
			com.checkmarx.eclipse.devassist.model.ScanEngine engine = typeToEngine(type);
			if (engine != null) {
				boolean enabled = state.isEnabled(engine);
				scannerState.put(type, enabled);
				String status = enabled ? "enabled" : "disabled";
				CxLogger.info(LOG_TAG + " " + type.getDisplayName() + " " + status);
			}
		}
	}

	/**
	 * Register listener for preference changes.
	 * When preferences are saved, reload scanner state from preferences.
	 */
	private void registerPreferenceListener() {
		try {
			com.checkmarx.eclipse.Activator.getDefault().getPreferenceStore()
				.addPropertyChangeListener(prefChangeListener);
			CxLogger.info(LOG_TAG + " Preference listener registered");
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Failed to register preference listener: " + e.getMessage());
		}
	}

	/**
	 * Reload scanner state from preferences.
	 * Called when preferences are saved to pick up any changes.
	 */
	private void reloadStateFromPreferences() {
		CxLogger.info(LOG_TAG + " Reloading scanner state from preferences");

		com.checkmarx.eclipse.devassist.state.ScannerState state = stateManager.loadState();

		for (ScannerType type : ScannerType.values()) {
			com.checkmarx.eclipse.devassist.model.ScanEngine engine = typeToEngine(type);
			if (engine != null) {
				boolean enabled = state.isEnabled(engine);
				boolean wasEnabled = scannerState.getOrDefault(type, true);

				if (enabled != wasEnabled) {
					scannerState.put(type, enabled);
					String status = enabled ? "enabled" : "disabled";
					CxLogger.info(LOG_TAG + " Updated " + type.getDisplayName() + " to " + status);
					notifyScannerStateChanged(type, enabled);
				}
			}
		}
	}

	/**
	 * Enable a scanner globally.
	 *
	 * @param type Scanner type to enable
	 */
	public void enableScanner(ScannerType type) {
		if (type == null) {
			return;
		}

		boolean wasDisabled = Boolean.FALSE.equals(scannerState.put(type, true));

		if (wasDisabled) {
			CxLogger.info(LOG_TAG + " Enabled scanner: " + type.getDisplayName());
			com.checkmarx.eclipse.devassist.model.ScanEngine engine = typeToEngine(type);
			if (engine != null) {
				stateManager.setScannerEnabled(engine, true);
			}
			notifyScannerStateChanged(type, true);
		}
	}

	/**
	 * Disable a scanner globally.
	 *
	 * @param type Scanner type to disable
	 */
	public void disableScanner(ScannerType type) {
		if (type == null) {
			return;
		}

		boolean wasEnabled = Boolean.TRUE.equals(scannerState.put(type, false));

		if (wasEnabled) {
			CxLogger.info(LOG_TAG + " Disabled scanner: " + type.getDisplayName());
			com.checkmarx.eclipse.devassist.model.ScanEngine engine = typeToEngine(type);
			if (engine != null) {
				stateManager.setScannerEnabled(engine, false);
			}
			notifyScannerStateChanged(type, false);
		}
	}

	/**
	 * Check if a scanner is enabled globally.
	 *
	 * @param type Scanner type to check
	 * @return true if enabled, false if disabled
	 */
	public boolean isScannerEnabled(ScannerType type) {
		if (type == null) {
			return false;
		}

		// Default to enabled if not explicitly set
		return scannerState.getOrDefault(type, true);
	}

	/**
	 * Enable all scanners.
	 */
	public void enableAllScanners() {
		CxLogger.info(LOG_TAG + " Enabling all scanners");

		for (ScannerType type : ScannerType.values()) {
			enableScanner(type);
		}
	}

	/**
	 * Disable all scanners.
	 */
	public void disableAllScanners() {
		CxLogger.info(LOG_TAG + " Disabling all scanners");

		for (ScannerType type : ScannerType.values()) {
			disableScanner(type);
		}
	}

	/**
	 * Get count of enabled scanners.
	 *
	 * @return Number of enabled scanners
	 */
	public int getEnabledScannerCount() {
		int count = 0;
		for (ScannerType type : ScannerType.values()) {
			if (isScannerEnabled(type)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Register a listener to be notified of state changes.
	 *
	 * @param listener Listener callback
	 */
	public void addScannerStateListener(ScannerStateListener listener) {
		if (listener != null) {
			stateListeners.add(listener);
		}
	}

	/**
	 * Unregister a state listener.
	 *
	 * @param listener Listener to remove
	 */
	public void removeScannerStateListener(ScannerStateListener listener) {
		if (listener != null) {
			stateListeners.remove(listener);
		}
	}

	/**
	 * Notify all listeners of a scanner state change.
	 *
	 * @param type Changed scanner type
	 * @param enabled New enabled state
	 */
	private void notifyScannerStateChanged(ScannerType type, boolean enabled) {
		for (ScannerStateListener listener : stateListeners) {
			try {
				listener.onScannerStateChanged(type, enabled);
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error notifying listener: " + e.getMessage());
			}
		}
	}

	/**
	 * Get a detailed state report.
	 *
	 * @return Multi-line status string
	 */
	public String getStateReport() {
		StringBuilder sb = new StringBuilder();
		sb.append(LOG_TAG).append(" Scanner State Report:\n");

		for (ScannerType type : ScannerType.values()) {
			boolean enabled = isScannerEnabled(type);
			sb.append("  ").append(type.getDisplayName()).append(": ")
				.append(enabled ? "ENABLED" : "DISABLED").append("\n");
		}

		sb.append("  Total Enabled: ").append(getEnabledScannerCount()).append("/")
			.append(ScannerType.values().length);

		return sb.toString();
	}

	/**
	 * Convert ScannerType to ScanEngine enum.
	 * Used for bridging between global controller and state manager.
	 *
	 * @param type Scanner type
	 * @return Corresponding ScanEngine, or null if no mapping exists
	 */
	private com.checkmarx.eclipse.devassist.model.ScanEngine typeToEngine(ScannerType type) {
		if (type == null) {
			return null;
		}

		return switch (type) {
			case ASCA -> com.checkmarx.eclipse.devassist.model.ScanEngine.ASCA;
			case OSS -> com.checkmarx.eclipse.devassist.model.ScanEngine.OSS;
			case SECRETS -> com.checkmarx.eclipse.devassist.model.ScanEngine.SECRETS;
			case IAC -> com.checkmarx.eclipse.devassist.model.ScanEngine.IAC;
			case CONTAINERS -> com.checkmarx.eclipse.devassist.model.ScanEngine.CONTAINERS;
		};
	}

	/**
	 * Listener interface for scanner state changes.
	 * Implemented by project registries to react to global changes.
	 */
	public interface ScannerStateListener {
		/**
		 * Called when a scanner's enabled state changes globally.
		 *
		 * @param type Changed scanner type
		 * @param enabled New enabled state
		 */
		void onScannerStateChanged(ScannerType type, boolean enabled);
	}
}
