package com.checkmarx.eclipse.devassist.basescanner;

import com.checkmarx.eclipse.devassist.common.ScannerConfig;
import com.checkmarx.eclipse.devassist.utils.ScanEngine;
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.resources.IProject;

/**
 * BaseScannerCommand is an abstract implementation of the ScannerCommand interface
 * that provides foundational functionality for registering, deregistering, and
 * managing a scanner's lifecycle for a given project. This class serves as a
 * base implementation for custom scanner commands.
 */
public abstract class BaseScannerCommand implements ScannerCommand {

	private static final String LOG_TAG = "[SCANNER-COMMAND]";
	public ScannerConfig config;
	protected IProject project;
	private boolean isRegistered = false;

	/**
	 * Create a scanner command with configuration.
	 *
	 * @param project Eclipse project
	 * @param config Scanner configuration
	 */
	protected BaseScannerCommand(IProject project, ScannerConfig config) {
		this.project = project;
		this.config = config;
	}

	/**
	 * Registers the project for the scanner which is invoked
	 *
	 * @param project - the project for the registration
	 */
	@Override
	public void register(IProject project) {
		boolean isActive = getScannerActivationStatus();
		if (!isActive) {
			return;
		}
		if (isScannerRegisteredAlready(project)) {
			return;
		}
		CxLogger.info(config.getEnabledMessage() + ":" + project.getName());
		initializeScanner();
		isRegistered = true;
	}

	/**
	 * De-registers the project for the scanner.
	 * This method is called in two cases: either project is closed by the user, or scanner is disabled
	 *
	 * @param project - the project that is registered
	 */
	@Override
	public void deregister(IProject project) {
		if (!isScannerRegisteredAlready(project)) {
			return;
		}
		CxLogger.info(config.getDisabledMessage() + ":" + project.getName());
		isRegistered = false;
	}

	/**
	 * Returns the scanner activation status of the scanner engine
	 */
	private boolean getScannerActivationStatus() {
		return config != null && config.getEngineName() != null;
	}

	/**
	 * Checks if the scanner is registered already for the project
	 *
	 * @param project is required
	 */
	private boolean isScannerRegisteredAlready(IProject project) {
		return isRegistered;
	}

	/**
	 * This method returns the ScanEngine Type
	 *
	 * @return ScanEngine
	 */
	protected ScanEngine getScannerType() {
		return ScanEngine.valueOf(config.getEngineName().toUpperCase());
	}

	/**
	 * Get the configuration.
	 *
	 * @return Scanner config
	 */
	public ScannerConfig getConfig() {
		return config;
	}

	/**
	 * Abstract method to initialize the scanner
	 * This method is invoked when the scanner is registered for the project
	 */
	@Override
	public abstract void initializeScanner();

	/**
	 * Dispose the scanner.
	 */
	@Override
	public void dispose() {
		CxLogger.info(LOG_TAG + " Disposed");
	}
}
