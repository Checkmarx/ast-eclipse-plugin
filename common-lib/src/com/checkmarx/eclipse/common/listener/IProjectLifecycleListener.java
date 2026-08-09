package com.checkmarx.eclipse.common.listener;

/**
 * Interface for handling project lifecycle and post-authentication scanning.
 *
 * Implemented by DevAssist module to trigger workspace scans after successful authentication.
 */
public interface IProjectLifecycleListener {

	/**
	 * Register this listener with Eclipse workspace.
	 * Must be called during plugin initialization to activate project lifecycle monitoring.
	 */
	void register();

	/**
	 * Initiates scans for all projects already open in the workspace.
	 * Called after successful user authentication to ensure all open projects
	 * are scanned with the newly authenticated credentials.
	 */
	void scanAlreadyOpenProjects();
}
