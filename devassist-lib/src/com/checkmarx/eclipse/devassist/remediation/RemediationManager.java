package com.checkmarx.eclipse.devassist.remediation;

import static com.checkmarx.eclipse.devassist.utils.DevAssistConstants.QUICK_FIX;
import static java.lang.String.format;

import java.util.Objects;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.swt.widgets.Display;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;
import com.checkmarx.eclipse.devassist.utils.PackageManager;

/**
 * RemediationManager provides remediation options for issues identified during
 * a real-time scan.
 * <p>
 * This class supports applying fixes, viewing details etc. for scan issues
 * detected by different scan engines, such as OSS, ASCA, etc.
 * <p>
 * Main responsibilities:
 * <ul>
 * <li>Apply remediation for different scan engine issues</li>
 * <li>Generate and copy remediation prompts to the clipboard</li>
 * <li>Log remediation actions</li>
 * </ul>
 */
public final class RemediationManager {

	// private static final Logger LOGGER =
	// PluginUtils.getLogger(RemediationManager.class);

	private static final String DEV_ASSIST_COPY_FIX_PROMPT = "Fix prompt copied to clipboard! Paste the prompt into Copilot chat (Agent Mode)";

	private static final String DEV_ASSIST_COPY_VIEW_DETAILS_PROMPT = "Prompt asking AI to provide more details was copied to your clipboard! Paste the prompt into Copilot chat.";

	/**
	 * Apply remediation for a given scan issue.
	 *
	 * @param project   the project where the fix is to be applied
	 * @param scanIssue the scan issue to fix
	 * @param actionId  the action ID for vulnerability-specific fixes
	 */
	public void fixWithCxOneAssist(@NonNull ScanIssue scanIssue, String actionId) {
		String prompt = buildRemediationPrompt(scanIssue, actionId);
		applyFix(scanIssue, prompt);
	}

	/**
	 * Builds the remediation prompt based on scan engine type.
	 *
	 * @param scanIssue the scan issue to build prompt for
	 * @param actionId  the action ID for vulnerability-specific fixes
	 * @return the remediation prompt, or null if not applicable
	 */
	@Nullable
	private String buildRemediationPrompt(@NonNull ScanIssue scanIssue, String actionId) {
		switch (scanIssue.getScanEngine()) {
			case OSS:
				return buildOSSRemediationPrompt(scanIssue);
			case SECRETS:
				return buildSecretRemediationPrompt(scanIssue);
			case CONTAINERS:
				return buildContainerRemediationPrompt(scanIssue);
			case IAC:
				return buildIACRemediationPrompt(scanIssue, actionId);
			case ASCA:
				return buildASCARemediationPrompt(scanIssue, actionId);
			default:
				return null;
		}
	}

	/**
	 * Applies the fix by attempting to send to Copilot AI first, with clipboard
	 * fallback.
	 *
	 * @param project   the project context
	 * @param scanIssue the scan issue being fixed
	 * @param prompt    the remediation prompt to apply
	 */
	private void applyFix(@NonNull ScanIssue scanIssue, @Nullable String prompt) {
		if (prompt == null || prompt.isEmpty()) {
			CxLogger.warning(format("RTS-Fix: Remediation failed. Prompt is empty for issue: %s, for file: %s",
					scanIssue.getTitle(), scanIssue.getFilePath()));
			return;
		}
		CxLogger.info(format("RTS-Fix: %s remediation started for issue: %s, for file: %s",
				scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
		String notificationTitle = getNotificationTitle(scanIssue.getScanEngine());

		// Try to fix with Copilot AI first (no notifications shown by fixWithAI)
		boolean aiSuccess = fixWithAI(prompt);
		if (aiSuccess) {
			CxLogger.info(format("RTS-Fix: %s remediation sent to Copilot for issue: %s, for file: %s",
					scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
		} else {
			// Fallback: Copy to clipboard with notification when Copilot is not available
			if (copyToClipboardAndNotify(prompt, notificationTitle, DEV_ASSIST_COPY_FIX_PROMPT)) {
				CxLogger.info(format("RTS-Fix: %s remediation completed (clipboard) for issue: %s, for file: %s",
						scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
			}
		}
	}

	/**
	 * Sends a fix prompt to GitHub Copilot for automated remediation.
	 * <p>
	 * This method attempts to:
	 * <ol>
	 * <li>Open GitHub Copilot Chat</li>
	 * <li>Switch to Agent mode</li>
	 * <li>Paste and send the prompt automatically</li>
	 * </ol>
	 * <p>
	 * This method does NOT show any notifications - the caller is responsible for
	 * handling success/failure notifications.
	 *
	 * @param prompt  the fix prompt to send to Copilot
	 * @param project the project context
	 * @return true if Copilot was successfully opened and prompt initiated, false
	 *         otherwise
	 */
	private boolean fixWithAI(@NonNull String prompt) {
		try {
			return CopilotIntegration.sendPromptToCopilot(prompt);
		} catch (Exception exception) {
			CxLogger.error("RTS-Fix: Failed to fix with AI: ", exception);
			return false;
		}
	}

	/**
	 * View details for a given scan issue.
	 *
	 * @param project   the project where the fix is to be applied
	 * @param scanIssue the scan issue to view details for
	 * @param actionId  the action ID for vulnerability-specific details
	 */
	public void viewDetails(@NonNull ScanIssue scanIssue, String actionId) {
		String prompt = buildExplanationPrompt(scanIssue, actionId);
		applyViewDetails(scanIssue, prompt);
	}

	/**
	 * Builds the explanation prompt based on scan engine type.
	 *
	 * @param scanIssue the scan issue to build prompt for
	 * @param actionId  the action ID for vulnerability-specific details
	 * @return the explanation prompt, or null if not applicable
	 */
	@Nullable
	private String buildExplanationPrompt(@NonNull ScanIssue scanIssue, String actionId) {
		switch (scanIssue.getScanEngine()) {
			case OSS:
				return buildOSSExplanationPrompt(scanIssue);
			case SECRETS:
				return buildSecretExplanationPrompt(scanIssue);
			case CONTAINERS:
				return buildContainerExplanationPrompt(scanIssue);
			case IAC:
				return buildIACExplanationPrompt(scanIssue, actionId);
			case ASCA:
				return buildASCAExplanationPrompt(scanIssue, actionId);
			default:
				return null;
		}
	}

	/**
	 * Applies the view details by attempting to send to Copilot AI first, with
	 * clipboard fallback.
	 *
	 * @param project   the project context
	 * @param scanIssue the scan issue being explained
	 * @param prompt    the explanation prompt to apply
	 */
	private void applyViewDetails(@NonNull ScanIssue scanIssue, @Nullable String prompt) {
		if (prompt == null || prompt.isEmpty()) {
			CxLogger.warning(format("RTS-ViewDetails: Explanation failed. Prompt is empty for issue: %s, for file: %s",
					scanIssue.getTitle(), scanIssue.getFilePath()));
			return;
		}
		CxLogger.info(format("RTS-ViewDetails: %s explanation started for issue: %s, for file: %s",
				scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
		String notificationTitle = getNotificationTitle(scanIssue.getScanEngine());

		// Try to send to Copilot AI first (no notifications shown by fixWithAI)
		boolean aiSuccess = fixWithAI(prompt);
		if (aiSuccess) {
			CxLogger.info(format("RTS-ViewDetails: %s explanation sent to Copilot for issue: %s, for file: %s",
					scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
		} else {
			// Fallback: Copy to clipboard with notification when Copilot is not available
			if (copyToClipboardAndNotify(prompt, notificationTitle, DEV_ASSIST_COPY_VIEW_DETAILS_PROMPT)) {
				CxLogger.info(
						format("RTS-ViewDetails: %s explanation completed (clipboard) for issue: %s, for file: %s",
								scanIssue.getScanEngine().name(), scanIssue.getTitle(), scanIssue.getFilePath()));
			}
		}
	}

	/**
	 * Builds remediation prompt for an OSS issue.
	 */
	private String buildOSSRemediationPrompt(ScanIssue scanIssue) {

		return DevAssistFixPrompts.buildSCARemediationPrompt(scanIssue.getTitle(), scanIssue.getPackageVersion(),
				PackageManager.mapToRemediationFormat(scanIssue.getPackageManager()), scanIssue.getSeverity());
	}

	/**
	 * Builds remediation prompt for a Secret issue.
	 */
	private String buildSecretRemediationPrompt(ScanIssue scanIssue) {
		return DevAssistFixPrompts.buildSecretRemediationPrompt(scanIssue.getTitle(), scanIssue.getDescription(),
				scanIssue.getSeverity());
	}

	/**
	 * Builds remediation prompt for a container issue.
	 */
	private String buildContainerRemediationPrompt(ScanIssue scanIssue) {
		return DevAssistFixPrompts.buildContainersRemediationPrompt(scanIssue.getFileType(), scanIssue.getTitle(),
				scanIssue.getImageTag(), scanIssue.getSeverity());
	}

	/**
	 * Builds remediation prompt for a IAC issue.
	 */
	private String buildIACRemediationPrompt(ScanIssue scanIssue, String actionId) {
		if (Objects.isNull(actionId) || actionId.isEmpty()) {
			CxLogger.warning(format("RTS-Fix: Remediation failed. Action id is not found for IAC issue: %s.",
					scanIssue.getTitle()));
			return null;
		}
		Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(scanIssue,
				actionId.equals(QUICK_FIX) ? scanIssue.getScanIssueId() : actionId);

		if (Objects.isNull(vulnerability)) {
			CxLogger.warning(format("RTS-Fix: Remediation failed. Vulnerability details not found for IAC issue: %s.",
					actionId));
			return null;
		}

		return DevAssistFixPrompts.buildIACRemediationPrompt(
				actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle(),
				actionId.equals(QUICK_FIX) ? scanIssue.getDescription() : vulnerability.getDescription(),
				actionId.equals(QUICK_FIX) ? scanIssue.getSeverity() : vulnerability.getSeverity(),
				scanIssue.getFileType(), vulnerability.getExpectedValue(), vulnerability.getActualValue(),
				scanIssue.getProblematicLineNumber());
	}

	/**
	 * Builds remediation prompt for an ASCA issue.
	 *
	 * @param scanIssue the scan issue to fix
	 * @param actionId  the specific vulnerability ID to fix, or QUICK_FIX for
	 *                  general remediation
	 */
	private String buildASCARemediationPrompt(ScanIssue scanIssue, String actionId) {
		if (Objects.isNull(actionId) || actionId.isEmpty()) {
			CxLogger.warning(format("RTS-Fix: Remediation failed. Action id is not found for ASCA issue: %s.",
					scanIssue.getTitle()));
			return null;
		}
		Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(scanIssue,
				actionId.equals(QUICK_FIX) ? scanIssue.getScanIssueId() : actionId);

		if (Objects.isNull(vulnerability)) {
			CxLogger.warning(format("RTS-Fix: Remediation failed. Vulnerability details not found for ASCA issue: %s.",
					actionId));
			return null;
		}

		return DevAssistFixPrompts.buildASCARemediationPrompt(
				actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle(),
				actionId.equals(QUICK_FIX) ? scanIssue.getDescription() : vulnerability.getDescription(),
				actionId.equals(QUICK_FIX) ? scanIssue.getSeverity() : vulnerability.getSeverity(),
				actionId.equals(QUICK_FIX) ? scanIssue.getRemediationAdvise() : vulnerability.getRemediationAdvise(),
				scanIssue.getProblematicLineNumber());
	}

	/**
	 * Builds explanation prompt for an OSS issue.
	 */
	private String buildOSSExplanationPrompt(ScanIssue scanIssue) {
		return ViewDetailsPrompts.buildSCAExplanationPrompt(scanIssue.getTitle(), scanIssue.getPackageVersion(),
				scanIssue.getSeverity(), scanIssue.getVulnerabilities());
	}

	/**
	 * Builds explanation prompt for a Secret issue.
	 */
	private String buildSecretExplanationPrompt(ScanIssue scanIssue) {
		return ViewDetailsPrompts.buildSecretsExplanationPrompt(scanIssue.getTitle(), scanIssue.getDescription(),
				scanIssue.getSeverity());
	}

	/**
	 * Builds explanation prompt for a container issue.
	 */
	private String buildContainerExplanationPrompt(ScanIssue scanIssue) {
		return ViewDetailsPrompts.buildContainersExplanationPrompt(scanIssue.getFileType(), scanIssue.getTitle(),
				scanIssue.getImageTag(), scanIssue.getSeverity());
	}

	/**
	 * Builds explanation prompt for an IAC issue.
	 */
	private String buildIACExplanationPrompt(ScanIssue scanIssue, String actionId) {
		if (Objects.isNull(actionId) || actionId.isEmpty()) {
			CxLogger.warning(format("RTS-ViewDetails: Explanation failed. Action id is not found for IAC issue: %s.",
					scanIssue.getTitle()));
			return null;
		}
		Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(scanIssue,
				actionId.equals(QUICK_FIX) ? scanIssue.getScanIssueId() : actionId);

		if (Objects.isNull(vulnerability)) {
			CxLogger.warning(
					format("RTS-ViewDetails: Explanation failed. Vulnerability details not found for IAC issue: %s.",
							actionId));
			return null;
		}

		return ViewDetailsPrompts.buildIACExplanationPrompt(
				actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle(),
				actionId.equals(QUICK_FIX) ? scanIssue.getDescription() : vulnerability.getDescription(),
				actionId.equals(QUICK_FIX) ? scanIssue.getSeverity() : vulnerability.getSeverity(),
				scanIssue.getFileType(), vulnerability.getExpectedValue(), vulnerability.getActualValue());
	}

	/**
	 * Builds explanation prompt for an ASCA issue.
	 *
	 * @param scanIssue the scan issue to explain
	 * @param actionId  the specific vulnerability ID to explain, or QUICK_FIX for
	 *                  general explanation
	 */
	private String buildASCAExplanationPrompt(ScanIssue scanIssue, String actionId) {
		if (Objects.isNull(actionId) || actionId.isEmpty()) {
			CxLogger.warning(format("RTS-ViewDetails: Explanation failed. Action id is not found for ASCA issue: %s.",
					scanIssue.getTitle()));
			return null;
		}
		Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(scanIssue,
				actionId.equals(QUICK_FIX) ? scanIssue.getScanIssueId() : actionId);

		if (Objects.isNull(vulnerability)) {
			CxLogger.warning(
					format("RTS-ViewDetails: Explanation failed. Vulnerability details not found for ASCA issue: %s.",
							actionId));
			return null;
		}

		return ViewDetailsPrompts.buildASCAExplanationPrompt(
				actionId.equals(QUICK_FIX) ? scanIssue.getTitle() : vulnerability.getTitle(),
				actionId.equals(QUICK_FIX) ? scanIssue.getDescription() : vulnerability.getDescription(),
				actionId.equals(QUICK_FIX) ? scanIssue.getSeverity() : vulnerability.getSeverity());
	}

	/**
	 * Get the notification title for the given scan engine.
	 */
	private String getNotificationTitle(ScanEngine scanEngine) {
		return DevAssistUtils.getAgentName() + " - " + scanEngine.name();
	}

	/**
	 * Copies the prompt to the clipboard and shows a balloon notification
	 * confirming it.
	 *
	 * @param prompt the prompt to copy
	 * @return true if the prompt was successfully copied
	 */
	private static boolean copyToClipboardAndNotify(String prompt, String notifyTitle, String notifyMessage) {
		boolean copied = DevAssistUtils.copyToClipboard(prompt);
		if (copied) {
			Display.getDefault().asyncExec(() -> {
				Display display = Display.getDefault();
				NotificationPopup popup = new NotificationPopup(display, notifyTitle, notifyMessage);
				popup.open();
			});
		} else {
			CxLogger.error("RTS-Fix: Failed to copy prompt to clipboard",
					new Exception("RTS-Fix: Failed to copy prompt to clipboard"));
		}
		return copied;
	}
}
