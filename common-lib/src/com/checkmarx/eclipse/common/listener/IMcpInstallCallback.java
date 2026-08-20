package com.checkmarx.eclipse.common.listener;

/**
 * Receives the outcome of an MCP install triggered from the UI (e.g. the "Install
 * MCP" link on CheckmarxPreferencePage). Unlike the silent, best-effort auto-install
 * run at startup, a user-initiated install needs to report back whether it actually
 * succeeded so the UI can show a result message.
 *
 * <p>May be invoked from a background thread - implementations that touch SWT
 * widgets must marshal onto the display thread themselves.
 */
public interface IMcpInstallCallback {

	/**
	 * Called when the MCP configuration was installed/updated successfully.
	 */
	void onSuccess();

	/**
	 * Called when the install ran successfully but there was nothing to change - the
	 * server entry already matches the current API key/URL exactly.
	 */
	void onAlreadyUpToDate();

	/**
	 * Called when the install could not be completed.
	 *
	 * @param errorMessage a user-presentable reason for the failure
	 */
	void onFailure(String errorMessage);
}
