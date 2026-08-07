package com.checkmarx.eclipse.devassist.ui.findings.integration;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.statushandlers.StatusManager;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.IStatus;
import java.util.HashMap;
import java.util.Map;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Integration with GitHub Copilot for Eclipse.
 *
 * Attempts to send prompts to Copilot chat via available commands.
 * Falls back to clipboard if Copilot is unavailable.
 *
 * Implementation Strategy:
 * 1. Try to find and execute Copilot commands (70% probability they exist)
 * 2. If commands not available, copy prompt to clipboard
 * 3. User can manually paste into Copilot Chat
 *
 * This ensures users always have the prompt available, either:
 * - Sent automatically to Copilot (best case)
 * - In clipboard for manual paste (fallback)
 */
public class CopilotIntegration {

    private static final String LOG_PREFIX = "[COPILOT-INTEGRATION]";

    /**
     * Known Copilot command IDs to try (in priority order)
     * Using Microsoft Copilot for Eclipse commands
     */
    private static final String COPILOT_OPEN_COMMAND = "com.microsoft.copilot.eclipse.commands.openChatView";
    private static final String COPILOT_INPUT_PARAM = "com.microsoft.copilot.eclipse.commands.openChatView.inputValue";
    private static final String COPILOT_AUTO_SEND_PARAM = "com.microsoft.copilot.eclipse.commands.openChatView.autoSend";

    /**
     * Send a prompt to Copilot Chat.
     *
     * Smart fallback strategy:
     * 1. Try to send via Copilot command (best case)
     * 2. If command fails, copy to clipboard (fallback)
     * 3. Show appropriate notification based on what succeeded
     *
     * @param prompt The prompt to send to Copilot
     * @return true if successfully sent via command or clipboard
     */
    public static boolean sendPromptToCopilot(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            CxLogger.error("Cannot send empty prompt to Copilot", new Exception("Empty prompt"));
            return false;
        }

        CxLogger.info(LOG_PREFIX + " Attempting to send prompt to Copilot...");

        // Step 1: Try to send prompt via Copilot command
        boolean copilotSuccess = tryPasteAndSendToCopilot(prompt);

        if (copilotSuccess) {
            CxLogger.info(LOG_PREFIX + " ✓ Successfully sent prompt via Copilot command");
            return true;
        }

        // Step 2: Fallback - copy to clipboard if command failed
        CxLogger.warning(LOG_PREFIX + " Copilot command failed - falling back to clipboard");
        boolean clipboardSuccess = copyToClipboard(prompt);

        if (clipboardSuccess) {
            CxLogger.info(LOG_PREFIX + " ✓ Prompt copied to clipboard as fallback");
            showNotification(
                "Fix Prompt Copied (Copilot Unavailable)",
                "The Copilot command is not available.\n\n" +
                "✓ The fix prompt has been copied to your clipboard.\n\n" +
                "Open Microsoft Copilot Chat and paste (Ctrl+V) to get AI-powered fix suggestions.",
                IStatus.INFO
            );
            return true; // Success because clipboard worked
        }

        // Both methods failed
        CxLogger.error(LOG_PREFIX + " Failed to send prompt - both command and clipboard failed",
                new Exception("Copilot command and clipboard fallback both failed"));
        showNotification(
            "Failed to Send Prompt",
            "Could not send prompt to Copilot Chat or copy to clipboard.\n\n" +
            "Please check that Copilot is properly installed.",
            IStatus.WARNING
        );
        return false;
    }

    /**
     * Tries to paste the prompt into Copilot Chat and trigger send
     *
     * @param prompt The prompt to paste
     * @return true if successfully pasted, false otherwise
     */
    private static boolean tryPasteAndSendToCopilot(String prompt) {
        final boolean[] success = { false };

        try {
            // Step 1: Try to execute Copilot open command with prompt
            CxLogger.info(LOG_PREFIX + " Attempting to execute Copilot open command with prompt...");
            boolean commandExecuted = executeOpenCopilotCommand(prompt);            
            if (commandExecuted) {
                CxLogger.info(LOG_PREFIX + " ✓ Copilot command executed successfully - prompt sent!");
                return true; // Success - command handled it all
            } else {
                CxLogger.warning(LOG_PREFIX + " Copilot command execution failed - attempting manual paste fallback");
            }

            // Step 2: Try to find and paste into the view
            Display.getDefault().syncExec(() -> {
                try {
                    IWorkbenchWindow activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                    if (activeWindow == null) {
                        CxLogger.warning(LOG_PREFIX + " No active workbench window");
                        return;
                    }

                    IWorkbenchPage activePage = activeWindow.getActivePage();
                    if (activePage == null) {
                        CxLogger.warning(LOG_PREFIX + " No active workbench page");
                        return;
                    }

                    // Try known Copilot view IDs
                    String[] copilotViewIds = {
                        "GitHub.Copilot.Chat.View",
                        "com.github.copilot.chat.view",
                        "copilot.chatView",
                        "com.github.copilot.views.CopilotChatView"
                    };

                    IViewPart copilotView = null;
                    String foundViewId = null;
                    for (String viewId : copilotViewIds) {
                        try {
                            copilotView = activePage.findView(viewId);
                            if (copilotView != null) {
                                foundViewId = viewId;
                                CxLogger.info(LOG_PREFIX + " Found Copilot Chat view: " + viewId);
                                break;
                            }
                        } catch (Exception e) {
                            // Try next
                        }
                    }

                    if (copilotView == null) {
                        CxLogger.warning(LOG_PREFIX + " Could not find Copilot Chat view after command execution");
                        return;
                    }

                    // Activate the view and bring to front
                    activePage.activate(copilotView);
                    activeWindow.getShell().forceActive();
                    CxLogger.info(LOG_PREFIX + " Activated Copilot Chat view: " + foundViewId);

                    // Find the text input field
                    Control control = copilotView.getAdapter(Control.class);
                    if (control == null || control.isDisposed()) {
                        CxLogger.warning(LOG_PREFIX + " Could not get control from Copilot view");
                        return;
                    }

                    Text inputField = findTextInputField(control);
                    if (inputField == null || inputField.isDisposed()) {
                        CxLogger.warning(LOG_PREFIX + " Could not find text input field in Copilot Chat");
                        return;
                    }

                    // Focus and set the content
                    inputField.setFocus();
                    inputField.setText(prompt);

                    CxLogger.info(LOG_PREFIX + " ✓ Successfully pasted prompt into Copilot Chat input field");
                    success[0] = true;

                    // Note: We don't auto-send to give user a chance to review
                    // User can press Enter or click Send button manually

                } catch (Exception e) {
                    CxLogger.warning(LOG_PREFIX + " Error pasting to Copilot Chat: " + e.getMessage());
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            CxLogger.error(LOG_PREFIX + " Error in tryPasteAndSendToCopilot: " + e.getMessage(), e);
        }

        return success[0];
    }

    /**
     * Executes Microsoft Copilot for Eclipse command to open chat with prompt
     *
     * @param prompt The prompt to send to Copilot
     * @return true if command was executed successfully, false otherwise
     */
    private static boolean executeOpenCopilotCommand(String prompt) {
        final boolean[] success = { false };

        try {
            Display.getDefault().syncExec(() -> {
                try {
                    // Get the command service
                    ICommandService commandService = PlatformUI.getWorkbench()
                            .getService(ICommandService.class);

                    if (commandService == null) {
                        CxLogger.warning(LOG_PREFIX + " ICommandService not available");
                        return;
                    }

                    // Get the Copilot open command
                    Command command = commandService.getCommand(COPILOT_OPEN_COMMAND);

                    if (command == null) {
                        CxLogger.warning(LOG_PREFIX + " Copilot command not found: " + COPILOT_OPEN_COMMAND);
                        return;
                    }

                    if (!command.isEnabled()) {
                        CxLogger.warning(LOG_PREFIX + " Copilot command is not enabled");
                        return;
                    }

                    CxLogger.info(LOG_PREFIX + " Found Copilot command: " + COPILOT_OPEN_COMMAND);

                    // Create parameters map for the command
                    java.util.Map<String, String> parameters = new java.util.HashMap<>();
                    parameters.put(COPILOT_INPUT_PARAM, prompt);
                    parameters.put(COPILOT_AUTO_SEND_PARAM, "true");

                    // Execute the command with parameters
                    try {
                    	
                        command.executeWithChecks(new ExecutionEvent(
                                command,
                                parameters,
                                null,
                                null
                        ));
                        
                        


                        CxLogger.info(LOG_PREFIX + " ✓ Successfully executed Copilot command with prompt");
                        success[0] = true;
                        
                        

                    } catch (Exception e) {
                        CxLogger.warning(LOG_PREFIX + " Command execution failed: " + e.getMessage());
                        e.printStackTrace();
                    }

                } catch (Exception e) {
                    CxLogger.warning(LOG_PREFIX + " Error executing Copilot command: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            CxLogger.error(LOG_PREFIX + " Error in executeOpenCopilotCommand: " + e.getMessage(), e);
        }
        
        return success[0];
    }


    /**
     * Recursively searches for a Text widget that appears to be the chat input (fallback)
     *
     * @param control The control to search
     * @return The text widget if found, null otherwise
     */
    private static Text findTextInputField(Control control) {
        if (control == null || control.isDisposed()) {
            return null;
        }

        // If this is a Text widget, it might be the input field
        if (control instanceof Text) {
            Text text = (Text) control;
            // Look for text widget that's editable and multi-line
            if (!text.isDisposed() && (text.getStyle() & SWT.MULTI) != 0) {
                return text;
            }
        }

        // Recursively search children if this is a composite
        if (control instanceof Composite) {
            Composite composite = (Composite) control;
            Control[] children = composite.getChildren();
            for (Control child : children) {
                Text found = findTextInputField(child);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Copies text to system clipboard
     *
     * @param text The text to copy
     * @return true if successful, false otherwise
     */
    private static boolean copyToClipboard(String text) {
        try {
            Display display = Display.getDefault();

            display.syncExec(() -> {
                Clipboard clipboard = new Clipboard(display);
                TextTransfer transfer = TextTransfer.getInstance();
                clipboard.setContents(new Object[] { text }, new org.eclipse.swt.dnd.Transfer[] { transfer });
                clipboard.dispose();
            });

            CxLogger.info(LOG_PREFIX + " Text copied to clipboard");
            return true;
        } catch (Exception e) {
            CxLogger.error(LOG_PREFIX + " Failed to copy to clipboard: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Shows a notification to the user
     *
     * @param title The notification title
     * @param message The notification message
     * @param severity The severity (IStatus.INFO, IStatus.WARNING, etc)
     */
    private static void showNotification(String title, String message, int severity) {
        try {
            Display.getDefault().asyncExec(() -> {
                IStatus status = new Status(
                    severity,
                    Activator.PLUGIN_ID,
                    title + "\n" + message
                );
                StatusManager.getManager().handle(status, StatusManager.SHOW | StatusManager.LOG);
            });
        } catch (Exception e) {
            CxLogger.warning(LOG_PREFIX + " Failed to show notification: " + e.getMessage());
        }
    }

    /**
     * Checks if Copilot is available in this Eclipse instance
     * Currently returns false as we use clipboard method as primary approach
     *
     * @return true if Copilot is available
     */
    public static boolean isCopilotAvailable() {
        // Clipboard method is always available as fallback
        return false;
    }
}
