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

	/**
	 * Re-runs the workspace file scan (manifest/IaC/container patterns) for every
	 * open project, regardless of whether it was already initialized.
	 *
	 * Unlike {@link #scanAlreadyOpenProjects()}, which only initializes projects
	 * that haven't been set up yet, this forces a fresh scan of already-initialized
	 * projects too. Used when scanner preferences change (e.g. a scanner is enabled)
	 * and previously-scanned projects need to be rescanned with the new scanner set.
	 */
	void rescanAllOpenProjects();
}
