package com.checkmarx.eclipse.diagnostics;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.core.commands.Command;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.core.commands.IHandler;

/**
 * Diagnostic tool to discover available Copilot commands in Eclipse.
 *
 * HOW TO USE:
 * 1. Copy this class into your Eclipse project
 * 2. Call CopilotCommandDiscovery.discoverCopilotCommands() from anywhere in the plugin
 * 3. Check the console output for discovered commands
 * 4. Report findings
 *
 * Example usage in a handler or view:
 * <pre>
 * CopilotCommandDiscovery.discoverCopilotCommands();
 * CopilotCommandDiscovery.discoverCopilotHandlers();
 * </pre>
 */
public class CopilotCommandDiscovery {

    /**
     * Discovers all Copilot-related commands in Eclipse
     */
    public static void discoverCopilotCommands() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("COPILOT COMMAND DISCOVERY");
        System.out.println("=".repeat(80) + "\n");

        try {
            ICommandService commandService =
                PlatformUI.getWorkbench().getService(ICommandService.class);

            if (commandService == null) {
                System.out.println("❌ ICommandService not available");
                return;
            }

            Command[] allCommands = commandService.getDefinedCommands();
            System.out.println("Total commands in Eclipse: " + allCommands.length);
            System.out.println("\nSearching for Copilot-related commands...\n");

            int found = 0;

            // Search for Copilot commands
            for (Command cmd : allCommands) {
                String cmdId = cmd.getId();

                if (isCopilotCommand(cmdId)) {
                    found++;
                    printCommand(cmd);
                }
            }

            if (found == 0) {
                System.out.println("❌ NO COPILOT COMMANDS FOUND");
                System.out.println("\nSearched for patterns:");
                System.out.println("  - *copilot*");
                System.out.println("  - *github.copilot*");
                System.out.println("  - *GitHub.Copilot*");
                System.out.println("  - *chat*");
                System.out.println("  - *chat.open*");
            } else {
                System.out.println("\n✅ Found " + found + " Copilot-related command(s)");
            }

        } catch (Exception e) {
            System.out.println("❌ Error during discovery: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    /**
     * Discovers all Copilot-related handlers
     */
    public static void discoverCopilotHandlers() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("COPILOT HANDLER DISCOVERY");
        System.out.println("=".repeat(80) + "\n");

        try {
            IHandlerService handlerService =
                PlatformUI.getWorkbench().getService(IHandlerService.class);

            if (handlerService == null) {
                System.out.println("❌ IHandlerService not available");
                return;
            }

            System.out.println("Checking for Copilot handlers...\n");

            String[] knownCopilotCommands = {
                "com.github.copilot.chat.open",
                "com.github.copilot.chat.sendMessage",
                "com.github.copilot.chat.show",
                "github.copilot.openChat",
                "GitHub.Copilot.Chat.Show",
                "copilot.chat.show",
                "copilot.chat.open"
            };

            int found = 0;
            for (String cmdId : knownCopilotCommands) {
                try {
                    IHandler handler = handlerService.getHandler(cmdId);
                    if (handler != null && handler.isHandled()) {
                        found++;
                        System.out.println("✅ Handler found: " + cmdId);
                        System.out.println("   Handler class: " + handler.getClass().getName());
                    }
                } catch (Exception e) {
                    // Handler not found or error, continue
                }
            }

            if (found == 0) {
                System.out.println("❌ No known Copilot handlers found");
            } else {
                System.out.println("\n✅ Found " + found + " handler(s)");
            }

        } catch (Exception e) {
            System.out.println("❌ Error during handler discovery: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    /**
     * Discovers all chat-related commands (might include Copilot)
     */
    public static void discoverChatCommands() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("CHAT COMMAND DISCOVERY");
        System.out.println("=".repeat(80) + "\n");

        try {
            ICommandService commandService =
                PlatformUI.getWorkbench().getService(ICommandService.class);

            if (commandService == null) {
                System.out.println("❌ ICommandService not available");
                return;
            }

            Command[] allCommands = commandService.getDefinedCommands();
            System.out.println("Searching for chat-related commands...\n");

            int found = 0;

            // Search for chat commands
            for (Command cmd : allCommands) {
                String cmdId = cmd.getId().toLowerCase();

                if (cmdId.contains("chat") ||
                    cmdId.contains("ai") ||
                    cmdId.contains("assist") ||
                    cmdId.contains("suggestion")) {
                    found++;
                    System.out.println("Found: " + cmd.getId());
                    if (cmd.getDescription() != null && !cmd.getDescription().isEmpty()) {
                        System.out.println("  Description: " + cmd.getDescription());
                    }
                }
            }

            if (found == 0) {
                System.out.println("❌ No chat-related commands found");
            } else {
                System.out.println("\n✅ Found " + found + " chat-related command(s)");
            }

        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    /**
     * Checks if a command ID matches Copilot patterns
     */
    private static boolean isCopilotCommand(String cmdId) {
        String lower = cmdId.toLowerCase();
        return lower.contains("copilot") ||
               lower.contains("github.copilot") ||
               (lower.contains("chat") && lower.contains("github")) ||
               lower.startsWith("GitHub.Copilot");
    }

    /**
     * Prints command details
     */
    private static void printCommand(Command cmd) {
        System.out.println("━".repeat(70));
        System.out.println("✅ Command ID: " + cmd.getId());
        System.out.println("   Name: " + cmd.getName());

        if (cmd.getDescription() != null && !cmd.getDescription().isEmpty()) {
            System.out.println("   Description: " + cmd.getDescription());
        }

        // Check for parameters
        try {
            if (cmd.getParameters() != null && cmd.getParameters().length > 0) {
                System.out.println("   Parameters:");
                for (Object param : cmd.getParameters()) {
                    System.out.println("     - " + param);
                }
            }
        } catch (Exception e) {
            // Ignore parameter errors
        }

        System.out.println();
    }

    /**
     * Main entry point for standalone testing
     */
    public static void main(String[] args) {
        System.out.println("\n⚠️  This class must be run from within Eclipse as a plugin");
        System.out.println("It requires access to PlatformUI which is only available in Eclipse context");
    }

    /**
     * Quick summary of what to do
     */
    public static void printInstructions() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("COPILOT COMMAND DISCOVERY INSTRUCTIONS");
        System.out.println("=".repeat(80));
        System.out.println("\n1. Add this class to your Eclipse plugin project:");
        System.out.println("   Location: src/com/checkmarx/eclipse/diagnostics/");
        System.out.println("\n2. Call from a handler or view to execute discovery:");
        System.out.println("   CopilotCommandDiscovery.discoverCopilotCommands();");
        System.out.println("   CopilotCommandDiscovery.discoverCopilotHandlers();");
        System.out.println("   CopilotCommandDiscovery.discoverChatCommands();");
        System.out.println("\n3. Check the Eclipse Console view for output");
        System.out.println("\n4. Report findings:");
        System.out.println("   - Any Copilot commands found?");
        System.out.println("   - Command IDs and descriptions");
        System.out.println("   - Available parameters");
        System.out.println("\n5. Use findings to determine implementation approach:");
        System.out.println("   - If commands found → Use Option A (simple)");
        System.out.println("   - If no commands → Use Option B (JetBrains approach)");
        System.out.println("\n" + "=".repeat(80) + "\n");
    }
}
