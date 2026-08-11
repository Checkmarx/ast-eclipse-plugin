package com.checkmarx.eclipse.startup;

import com.checkmarx.eclipse.common.listener.IWorkspaceScanService;
import com.checkmarx.eclipse.common.listener.IProjectLifecycleListener;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.backend.listener.CheckmarxEditorListener;

/**
 * Triggers workspace scans after authentication.
 *
 * Encapsulates the ProjectLifecycleListener interaction so devassist-lib
 * doesn't need to import from the main plugin.
 */
public class WorkspaceScanService implements IWorkspaceScanService {

	private static final String LOG_TAG = "[WORKSPACE-SCAN]";

	@Override
	public void scanWorkspace() {
		try {
			IProjectLifecycleListener projectListener = PluginStartup.getProjectListener();
			if (projectListener != null) {
				CxLogger.info(LOG_TAG + " Triggering workspace OSS/IaC/container scan...");
				projectListener.rescanAllOpenProjects();
			} else {
				CxLogger.warning(LOG_TAG + " Project lifecycle listener not initialized");
			}

			CheckmarxEditorListener editorListener = PluginStartup.getRealtimeScanListener();
			if (editorListener != null) {
				CxLogger.info(LOG_TAG + " Triggering rescan of open editors for real-time scanners...");
				editorListener.rescanOpenEditors();
			}
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Failed to trigger workspace scan: " + e.getMessage(), e);
		}
	}
}
