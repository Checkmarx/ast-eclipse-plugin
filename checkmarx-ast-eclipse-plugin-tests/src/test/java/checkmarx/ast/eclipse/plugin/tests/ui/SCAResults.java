package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.Test;

public class SCAResults extends BaseUITest {

    @Test
    void testSCAResultsExist() throws TimeoutException {

        try {
            // Setup and initialize plugin
            setUpCheckmarxPlugin(true);
            preventWidgetWasNullInCIEnvironment();

            System.out.println("\n=== Starting SCA Results Test ===");

            // Wait briefly for UI stabilization
            sleep(2000);

            // 🔥 Scope tree to the Checkmarx view (CI safe)
            SWTBotTree tree = _bot
                    .viewByTitle(VIEW_CHECKMARX_AST_SCAN)
                    .bot()
                    .tree();

            assertTrue(tree.rowCount() > 0,
                    "Results tree should contain at least one row");

            // Get root node (scan ID)
            String scanId = tree.cell(0, 0);
            System.out.println("Scan ID: " + scanId);

            SWTBotTreeItem rootNode = tree.getTreeItem(scanId);
            rootNode.expand();
            sleep(1000);

            // Get scanner nodes
            List<String> scannerNodes = rootNode.getNodes();
            System.out.println("Available scanners: " + scannerNodes);

            // Verify SCA exists
            assertTrue(
                    scannerNodes.stream().anyMatch(node -> node.startsWith("SCA")),
                    "SCA scanner should exist in results"
            );

            // Get SCA node safely
            SWTBotTreeItem scaNode = scannerNodes.stream()
                    .filter(node -> node.startsWith("SCA"))
                    .map(rootNode::getNode)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("SCA node not found"));

            scaNode.expand();
            sleep(1000);

            // Check severity nodes
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

            // Cleanup
            _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();

        } catch (Exception e) {
            System.out.println("\n=== Test Failed ===");
            System.out.println("Exception type: " + e.getClass().getName());
            System.out.println("Message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}