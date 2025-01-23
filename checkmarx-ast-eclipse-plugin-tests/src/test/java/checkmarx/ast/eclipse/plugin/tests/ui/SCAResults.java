package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SWTBotJunit4ClassRunner.class)
public class SCAResults extends BaseUITest {
    
    @Test
    public void testSCAResultsExist() throws TimeoutException {
        try {
            // Setup and initialize plugin
            setUpCheckmarxPlugin(true);
            preventWidgetWasNullInCIEnvironment();
            
            System.out.println("\n=== Starting SCA Results Test ===");
            
            // Wait for scan results to load
            sleep(2000);
            
            // Get root node
            String scanId = _bot.tree(1).cell(0, 0);
            System.out.println("Scan ID: " + scanId);
            
            SWTBotTreeItem rootNode = _bot.tree(1).getTreeItem(scanId);
            rootNode.expand();
            sleep(1000);
            
            // Find SCA node
            List<String> scannerNodes = rootNode.getNodes();
            System.out.println("Available scanners: " + scannerNodes);
            
            // Verify SCA exists
            assertTrue(
                "SCA scanner should exist in results",
                scannerNodes.stream().anyMatch(node -> node.startsWith("SCA"))
            );
            
            // Get SCA node
            SWTBotTreeItem scaNode = null;
            for (String nodeName : scannerNodes) {
                if (nodeName.startsWith("SCA")) {
                    scaNode = rootNode.getNode(nodeName);
                    break;
                }
            }
            
            // Check severity nodes
            scaNode.expand();
            sleep(1000);
            
            List<String> severityNodes = scaNode.getNodes();
            System.out.println("SCA severity nodes: " + severityNodes);
            
            // Verify HIGH and MEDIUM exist
            assertTrue(
                "SCA should have HIGH severity findings",
                severityNodes.stream().anyMatch(node -> node.startsWith("HIGH"))
            );
            
            assertTrue(
                "SCA should have MEDIUM severity findings",
                severityNodes.stream().anyMatch(node -> node.startsWith("MEDIUM"))
            );
            
            // Cleanup
            _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
            
        } catch (Exception e) {
            System.out.println("\n=== Test Failed ===");
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
} 