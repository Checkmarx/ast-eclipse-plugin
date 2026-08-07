package com.checkmarx.eclipse.devassist.basescanner;

import org.eclipse.core.resources.IProject;

/**
 * Interface for scanner command implementations.
 * Manages scanner lifecycle including registration and deregistration.
 */
public interface ScannerCommand {

	/**
	 * Register the scanner for a project.
	 *
	 * @param project Eclipse project
	 */
	void register(IProject project);

	/**
	 * Deregister the scanner for a project.
	 *
	 * @param project Eclipse project
	 */
	void deregister(IProject project);

	/**
	 * Initialize the scanner.
	 */
	void initializeScanner();

	/**
	 * Dispose the scanner.
	 */
	void dispose();
}
