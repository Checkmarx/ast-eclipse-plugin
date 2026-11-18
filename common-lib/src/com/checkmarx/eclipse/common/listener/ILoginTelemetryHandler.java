package com.checkmarx.eclipse.common.listener;

/**
 * Handler for login telemetry events (AST-184122).
 *
 * Allows devassist-lib to report an authentication result from PreferencesPage
 * without creating a reverse dependency from common-lib to devassist-lib.
 */
public interface ILoginTelemetryHandler {

	/**
	 * Called with the result of an API key authentication attempt.
	 *
	 * @param success       true if authentication succeeded
	 * @param failureReason reason for a failed attempt (may be null on success)
	 */
	void onLogin(boolean success, String failureReason);
}
