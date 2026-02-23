package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.eclipse.swt.widgets.Tree;
import org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.Test;

public class SCAResults extends BaseUITest {

    private static final int VIEW_OPEN_TIMEOUT     = 30000;  // 30 seconds
    private static final int TREE_LOAD_TIMEOUT     = 120000; // 2 minutes
    private static final int CHILDREN_LOAD_TIMEOUT = 60000;  // 1 minute

    @Test
    public void testSCAResultsExist() throws TimeoutException {
        try {
            // Step 1: Setup plugin
            setUpCheckmarxPlugin(true);
            preventWidgetWasNullInCIEnvironment();

            System.out.println("\n=== Starting SCA Results Test ===");

            // Step 2: Explicitly open the Checkmarx view
            openCheckmarxView();

            // Step 3: Debug — print all trees currently in the UI
            printAllTreesInUI();

            // Step 4: Wait for at least 2 trees
            waitForAtLeastTwoTrees();

            // Step 5: Wait for results tree to have rows
            waitForResultsTreeToLoad();

            // Step 6: Get root scan node
            String scanId = _bot.tree(1).cell(0, 0);
            System.out.println("Scan ID: " + scanId);

            SWTBotTreeItem rootNode = _bot.tree(1).getTreeItem(scanId);
            rootNode.expand();

            // Step 7: Wait for scanner children
            waitForChildren(rootNode, "scanner nodes");

            List<String> scannerNodes = rootNode.getNodes();
            System.out.println("Available scanners: " + scannerNodes);

            assertTrue(
                scannerNodes.stream().anyMatch(node -> node.startsWith("SCA")),
                "SCA scanner should exist in results"
            );

            // Step 8: Find SCA node
            SWTBotTreeItem scaNode = null;
            for (String nodeName : scannerNodes) {
                if (nodeName.startsWith("SCA")) {
                    scaNode = rootNode.getNode(nodeName);
                    break;
                }
            }

            assertNotNull(scaNode, "SCA node should not be null");
            final SWTBotTreeItem scaNodeFinal = scaNode;
            scaNode.expand();

            // Step 9: Wait for severity children
            waitForChildren(scaNodeFinal, "SCA severity nodes");

            List<String> severityNodes = scaNode.getNodes();
            System.out.println("SCA severity nodes: " + severityNodes);

            assertTrue(
                severityNodes.stream().anyMatch(node -> node.startsWith("HIGH")),
                "SCA should have HIGH severity findings"
            );
            assertTrue(
                severityNodes.stream().anyMatch(node -> node.startsWith("MEDIUM")),
                "SCA should have MEDIUM severity findings"
            );

            System.out.println("\n=== SCA Results Test Passed ===");

            // Cleanup
            _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();

        } catch (Exception e) {
            System.out.println("\n=== Test Failed ===");
            System.out.println("Exception type: " + e.getClass().getName());
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Explicitly opens the Checkmarx view via the workbench.
     * Tries the view by title first (safest), then falls back to showView by ID.
     */
    private void openCheckmarxView() {
        System.out.println("Attempting to open Checkmarx view...");

        // First attempt: open by title using SWTBot
        try {
            _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).show();
            System.out.println("Opened view by title: " + VIEW_CHECKMARX_AST_SCAN);
            sleep(2000);
            return;
        } catch (Exception e) {
            System.out.println("Could not open by title '" + VIEW_CHECKMARX_AST_SCAN 
                + "': " + e.getMessage());
        }

        // Second attempt: open via PlatformUI showView using known view IDs
        String[] viewIds = {
            "com.checkmarx.eclipse.views.CheckmarxView",
            "com.checkmarx.eclipse.views.CxView",
            "com.checkmarx.eclipse.view.CheckmarxView",
            "com.checkmarx.ast.eclipse.views.CheckmarxView"
        };

        for (String viewId : viewIds) {
            try {
                _bot.getDisplay().syncExec(() -> {
                    try {
                        PlatformUI.getWorkbench()
                            .getActiveWorkbenchWindow()
                            .getActivePage()
                            .showView(viewId);
                        System.out.println("Opened view by ID: " + viewId);
                    } catch (Exception ex) {
                        System.out.println("Failed to open view ID '" 
                            + viewId + "': " + ex.getMessage());
                    }
                });
                sleep(2000);

                // Check if view appeared
                try {
                    _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).show();
                    System.out.println("View confirmed open after ID: " + viewId);
                    return;
                } catch (Exception ignored) {
                    // Try next ID
                }
            } catch (Exception e) {
                System.out.println("Error trying view ID '" + viewId + "': " + e.getMessage());
            }
        }

        System.out.println("WARNING: Could not open Checkmarx view by any known ID. "
            + "Check plugin.xml for the correct view ID.");
    }

    /**
     * Prints all currently visible tree widgets and their row counts.
     * Pure debug — does not block or fail.
     */
    private void printAllTreesInUI() {
        try {
            List<?> trees = _bot.widgets(
                WidgetMatcherFactory.widgetOfType(Tree.class)
            );
            System.out.println("Total tree widgets currently in UI: " + trees.size());
            for (int i = 0; i < trees.size(); i++) {
                try {
                    int rows = _bot.tree(i).rowCount();
                    System.out.println("  tree(" + i + ") rowCount = " + rows);
                } catch (Exception e) {
                    System.out.println("  tree(" + i + ") could not read rowCount: " 
                        + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Could not enumerate trees: " + e.getMessage());
        }
    }

    /**
     * Waits until at least 2 tree widgets are present in the UI.
     */
    private void waitForAtLeastTwoTrees() {
        System.out.println("Waiting for at least 2 tree widgets...");
        _bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() throws Exception {
                try {
                    int count = _bot.widgets(
                        WidgetMatcherFactory.widgetOfType(Tree.class)
                    ).size();
                    System.out.println("Trees in UI: " + count);
                    return count >= 2;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage() {
                return "Less than 2 tree widgets found. "
                     + "Checkmarx results view did not open. "
                     + "Check plugin.xml for the correct view ID.";
            }
        }, TREE_LOAD_TIMEOUT);
    }

    /**
     * Waits until tree(1) has at least one row loaded.
     */
    private void waitForResultsTreeToLoad() {
        System.out.println("Waiting for results tree (index 1) to load rows...");
        _bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() throws Exception {
                try {
                    int rows = _bot.tree(1).rowCount();
                    System.out.println("Results tree row count: " + rows);
                    return rows > 0;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage() {
                return "Results tree (index 1) did not load any rows within timeout.";
            }
        }, TREE_LOAD_TIMEOUT);
    }

    /**
     * Waits until the given tree item has at least one child node.
     */
    private void waitForChildren(final SWTBotTreeItem item, final String label) {
        System.out.println("Waiting for children of: " + label);
        _bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() throws Exception {
                try {
                    int count = item.getNodes().size();
                    System.out.println(label + " child count: " + count);
                    return count > 0;
                } catch (Exception e) {
                    System.out.println("Error reading " + label + ": " + e.getMessage());
                    return false;
                }
            }

            @Override
            public String getFailureMessage() {
                return "No children appeared under '" + label + "' within timeout.";
            }
        }, CHILDREN_LOAD_TIMEOUT);
    }
}