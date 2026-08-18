package com.checkmarx.eclipse.devassist.backend;

import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.QualifiedName;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType;

/**
 * Clears file state cache for scanners that are being re-enabled.
 *
 * When a workspace scanner (OSS, IaC, Container) is disabled, findings are purged.
 * When re-enabled, the state cache (in DevAssistScanStateHolder) still holds old
 * file hashes, preventing fresh scans. This clears the cache for those scanners
 * so manifest files get re-scanned immediately.
 *
 * Mirrors ScannerMarkerPurger pattern for disabled scanners.
 */
public class ScanStateCacheClearer {

	private static final String LOG_TAG = "[SCAN-STATE-CACHE-CLEARER]";
	private static final String PLUGIN_ID = "com.checkmarx.eclipse.plugin";
	private static final QualifiedName STATE_HOLDER_KEY =
		new QualifiedName(PLUGIN_ID, "state-holder");

	private ScanStateCacheClearer() {
	}

	/**
	 * Clear state cache for scanners that are being re-enabled.
	 * Allows manifest files to be re-scanned even if content hasn't changed.
	 *
	 * @param newlyEnabledScanners Scanners that just transitioned from disabled to enabled
	 */
	public static void clearForScanners(Set<ScannerType> newlyEnabledScanners) {
		if (newlyEnabledScanners == null || newlyEnabledScanners.isEmpty()) {
			return;
		}

		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (!project.isOpen()) {
				continue;
			}
			try {
				DevAssistScanStateHolder stateHolder =
					(DevAssistScanStateHolder) project.getSessionProperty(STATE_HOLDER_KEY);
				if (stateHolder == null) {
					continue;
				}

				// Clear ALL state cache entries to force fresh scans
				// This ensures manifest files are re-scanned regardless of whether
				// their content changed, since scanner enablement counts as "state changed"
				stateHolder.clearAll();

				CxLogger.info(LOG_TAG + " Cleared state cache for project: " + project.getName());
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error clearing state cache for project " +
					project.getName() + ": " + e.getMessage());
			}
		}
	}
}
