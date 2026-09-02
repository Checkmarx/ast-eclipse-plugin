package com.checkmarx.eclipse.common.listener;

/**
 * Service for triggering workspace scans after authentication.
 *
 * Allows AuthenticationSuccessHandler (devassist-lib) to trigger workspace scans
 * without importing ProjectLifecycleListener or PluginStartup from main plugin.
 */
public interface IWorkspaceScanService {

	/**
	 * Scan all open projects in the workspace.
	 * Called after successful authentication to ensure all open projects
	 * are scanned with the newly authenticated credentials.
	 */
	void scanWorkspace();
}
