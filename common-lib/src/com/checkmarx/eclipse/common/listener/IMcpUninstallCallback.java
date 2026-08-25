package com.checkmarx.eclipse.common.listener;

/**
 * Receives the outcome of an MCP uninstall triggered after logout.
 *
 * <p>May be invoked from a background thread - implementations that touch SWT
 * widgets must marshal onto the display thread themselves.
 */
public interface IMcpUninstallCallback {

	/**
	 * Called when the MCP configuration was uninstalled/removed successfully.
	 */
	void onSuccess();

	/**
	 * Called when no MCP configuration entry was found to uninstall - the uninstall
	 * operation succeeded, but there was nothing to remove (already uninstalled,
	 * or never installed in the first place).
	 */
	void onNotFound();

	/**
	 * Called when the uninstall could not be completed.
	 *
	 * @param errorMessage a user-presentable reason for the failure
	 */
	void onFailure(String errorMessage);
}
