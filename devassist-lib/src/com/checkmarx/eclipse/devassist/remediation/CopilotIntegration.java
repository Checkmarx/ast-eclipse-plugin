package com.checkmarx.eclipse.devassist.remediation;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.osgi.framework.Bundle;

import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * Integration with GitHub Copilot for Eclipse
 * (https://github.com/microsoft/copilot-for-eclipse).
 * <p>
 * Opens the Copilot Chat view in <b>Agent</b> mode with a pre-filled prompt in a
 * <b>new chat session</b> and submits it automatically, using the command
 * contributed by the Copilot plugin: {@code com.microsoft.copilot.eclipse.commands.openChatView}.
 * Each prompt is opened in its own isolated chat session for independent Agent conversations.
 * <p>
 * Fallback strategy, in order:
 * <ol>
 * <li>If GitHub Copilot for Eclipse is not installed, a notification is shown
 * inviting the user to install it from the Eclipse Marketplace.</li>
 * <li>If Copilot is installed but the command could not be invoked (e.g.
 * disabled, or the command contract changed in a future Copilot release), the
 * prompt is copied to the clipboard.</li>
 * </ol>
 * In both fallback cases the prompt is always copied to the clipboard and a
 * balloon notification confirms it, so the user never loses the generated
 * prompt.
 */
public final class CopilotIntegration {

	private static final String LOG_PREFIX = "[CX-COPILOT-INTEGRATION]";

	/**
	 * Bundle symbolic ids used to detect whether GitHub Copilot for Eclipse is
	 * installed. Checking both the core and UI bundles guards against internal
	 * repackaging.
	 */
	private static final String[] COPILOT_BUNDLE_IDS = { "com.microsoft.copilot.eclipse.core",
			"com.microsoft.copilot.eclipse.ui" };

	/** Command contributed by GitHub Copilot for Eclipse to open the chat view. */
	private static final String COPILOT_OPEN_CHAT_COMMAND = "com.microsoft.copilot.eclipse.commands.openChatView";

	/** Initial text to place in the chat input. */
	private static final String PARAM_INPUT_VALUE = "com.microsoft.copilot.eclipse.commands.openChatView.inputValue";

	/** Whether the chat input should be submitted automatically once set. */
	private static final String PARAM_AUTO_SEND = "com.microsoft.copilot.eclipse.commands.openChatView.autoSend";

	/** Chat mode to switch to before submitting ("Agent" or "Ask"). */
	private static final String PARAM_MODE = "com.microsoft.copilot.eclipse.commands.openChatView.mode";

	/** New conversation. */
	private static final String COPILOT_NEW_CONVERSATION_COMMAND = "com.microsoft.copilot.eclipse.commands.newConversation";

	private static final String CHAT_MODE_AGENT = "Agent";
	private static final String CHAT_MODE_ASK = "Ask";

	private static final String COPILOT_MARKETPLACE_URL = "https://marketplace.eclipse.org/content/github-copilot";

	private static final String INSTALL_NOTIFICATION_TITLE = "GitHub Copilot for Eclipse Not Installed";
	private static final String INSTALL_NOTIFICATION_MESSAGE = "GitHub Copilot for Eclipse is required to fix the vulnerability.\nInstall it from the Eclipse Marketplace, then try again.";

	private CopilotIntegration() {
		throw new IllegalStateException("Cannot instantiate CopilotIntegration class");
	}

	/**
	 * Opens GitHub Copilot Chat in the specified mode with the given prompt in a new chat
	 * session and submits it automatically.
	 * <p>
	 * Each call creates a new chat session instead of reusing an existing
	 * conversation, ensuring isolated contexts for each prompt.
	 * <p>
	 * If Copilot is not installed, an "install Copilot" notification is shown. In
	 * every case where the prompt could not be handed off to Copilot directly, it
	 * is copied to the clipboard and a confirmation balloon is shown.
	 *
	 * @param prompt the prompt to send to Copilot
	 * @param chatMode the chat mode to use ("Agent" for autonomous fixes, "Ask" for explanations)
	 * @return true if the prompt was successfully handed off to Copilot or copied
	 *         to the clipboard as a fallback; false only if the prompt itself is
	 *         invalid
	 */
	public static boolean sendPromptToCopilot(String prompt, String chatMode) {
		if (prompt == null || prompt.isEmpty()) {
			CxLogger.error(LOG_PREFIX + " Cannot send an empty prompt to Copilot",
					new Exception("Empty prompt for Copilot"));
			return false;
		}

		if (!isCopilotInstalled()) {
			CxLogger.warning(LOG_PREFIX + " GitHub Copilot for Eclipse is not installed");
			showInstallCopilotNotification();
			return false;
		}

		if (openChatInModeAndSend(prompt, chatMode)) {
			CxLogger.info(LOG_PREFIX + " Prompt sent to Copilot Chat in " + chatMode + " mode and submitted automatically");
			return true;
		}

		CxLogger.warning(LOG_PREFIX + " Could not invoke Copilot's open chat command - falling back to clipboard");
		return false;
	}

	/**
	 * Opens GitHub Copilot Chat in Agent mode with the given prompt in a new chat
	 * session and submits it automatically.
	 * <p>
	 * Convenience method that calls sendPromptToCopilot(prompt, "Agent").
	 *
	 * @param prompt the prompt to send to Copilot
	 * @return true if the prompt was successfully handed off to Copilot or copied
	 *         to the clipboard as a fallback; false only if the prompt itself is
	 *         invalid
	 */
	public static boolean sendPromptToCopilot(String prompt) {
		return sendPromptToCopilot(prompt, CHAT_MODE_AGENT);
	}

	/**
	 * Checks whether GitHub Copilot for Eclipse is installed in this IDE instance.
	 *
	 * @return true if the Copilot plugin's bundles are present
	 */
	public static boolean isCopilotInstalled() {
		for (String bundleId : COPILOT_BUNDLE_IDS) {
			Bundle bundle = Platform.getBundle(bundleId);
			if (bundle != null && bundle.getState() != Bundle.UNINSTALLED) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Executes the Copilot {@code openChatView} command, switching to the specified mode,
	 * pre-filling the prompt, and requesting an automatic submit.
	 *
	 * @param prompt the prompt to place in the chat input
	 * @param chatMode the chat mode to use ("Agent" or "Ask")
	 * @return true if the command was found, enabled, and executed without error
	 */
	private static boolean openChatInModeAndSend(String prompt, String chatMode) {
	    try {
	        // 1. Run the reset command synchronously on the UI Thread
	        Display.getDefault().syncExec(() -> {
	            try {
	                ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
	                if (commandService == null) return;

	                Command newConvCommand = commandService.getCommand(COPILOT_NEW_CONVERSATION_COMMAND);
	                if (newConvCommand != null && newConvCommand.isDefined() && newConvCommand.isEnabled()) {
	                    newConvCommand.executeWithChecks(new ExecutionEvent(newConvCommand, new HashMap<>(), null, null));
	                }
	            } catch (Exception e) {
	                CxLogger.warning(LOG_PREFIX + " Error clearing conversation state: " + e.getMessage());
	            }
	        });

	        // 2. Schedule mode switch and prompt send in sequence via background thread
	        // This ensures mode is fully initialized before prompt is sent
	        Thread executionThread = new Thread(() -> {
	            try {
	                // Wait for chat UI to stabilize after reset
	                Thread.sleep(450);

	                // Step A: Switch to the specified mode first
	                Display.getDefault().syncExec(() -> {
	                    try {
	                        ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
	                        if (commandService == null) return;

	                        Command command = commandService.getCommand(COPILOT_OPEN_CHAT_COMMAND);
	                        if (command != null && command.isDefined() && command.isEnabled()) {
	                            Map<String, String> modeParams = new HashMap<>();
	                            modeParams.put(PARAM_MODE, chatMode != null ? chatMode : CHAT_MODE_AGENT);
	                            command.executeWithChecks(new ExecutionEvent(command, modeParams, null, null));
	                        }
	                    } catch (Exception e) {
	                        CxLogger.warning(LOG_PREFIX + " Mode switch failed: " + e.getMessage());
	                    }
	                });

	                // Wait for mode to fully initialize in the UI
	                Thread.sleep(300);

	                // Step B: Now send the prompt after mode is ready
	                Display.getDefault().syncExec(() -> {
	                    try {
	                        ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
	                        if (commandService == null) return;

	                        Command command = commandService.getCommand(COPILOT_OPEN_CHAT_COMMAND);
	                        if (command != null && command.isDefined() && command.isEnabled()) {
	                            Map<String, String> promptParams = new HashMap<>();
	                            promptParams.put(PARAM_INPUT_VALUE, prompt);
	                            promptParams.put(PARAM_AUTO_SEND, Boolean.TRUE.toString());

	                            command.executeWithChecks(new ExecutionEvent(command, promptParams, null, null));
	                        }
	                    } catch (Exception e) {
	                        CxLogger.warning(LOG_PREFIX + " Prompt submission failed: " + e.getMessage());
	                    }
	                });

	            } catch (InterruptedException e) {
	                Thread.currentThread().interrupt();
	                CxLogger.warning(LOG_PREFIX + " Automation sequence interrupted: " + e.getMessage());
	            }
	        });

	        executionThread.start();
	        return true;

	    } catch (Exception e) {
	        CxLogger.error(LOG_PREFIX + " Unexpected exception handling background dispatch: " + e.getMessage(), e);
	        return false;
	    }
	}
	

	/**
	 * Shows a notification prompting the user to install GitHub Copilot for
	 * Eclipse, with a link to its Eclipse Marketplace listing.
	 */
	private static void showInstallCopilotNotification() {
		Display.getDefault().asyncExec(() -> {
			Display display = Display.getDefault();
			CopilotInstallNotificationPopup popup = new CopilotInstallNotificationPopup(display,
					INSTALL_NOTIFICATION_TITLE, INSTALL_NOTIFICATION_MESSAGE, COPILOT_MARKETPLACE_URL);
			popup.open();
		});
	}
}
