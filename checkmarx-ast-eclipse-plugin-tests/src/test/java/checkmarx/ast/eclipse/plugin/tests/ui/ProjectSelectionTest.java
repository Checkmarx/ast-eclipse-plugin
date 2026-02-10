package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import checkmarx.ast.eclipse.plugin.tests.common.Environment;

public class ProjectSelectionTest extends BaseUITest {
    
    private static final String TEST_PROJECT = "pedrompflopes/WebGoat";
    private static final String TEST_BRANCH = "develop";
    
    @Test
    public void testProjectSelectionFlow() throws TimeoutException {
        try {
            setUpCheckmarxPlugin(true);
            preventWidgetWasNullInCIEnvironment();
            
            System.out.println("\n=== Starting Project Selection Flow Test ===");
            
          
            // Select project
            _bot.comboBox(0).setText(TEST_PROJECT);
            sleep(2000);
            System.out.println("Project selected: '" + _bot.comboBox(0).getText() + "'");
            
            // Verify branch combo is enabled and select branch
            assertTrue(_bot.comboBox(1).isEnabled(), "Branch combo should be enabled");
            _bot.comboBox(1).setText(TEST_BRANCH);
            sleep(2000);
            
            // Verify scan ID combo is enabled and select scan
            assertTrue(_bot.comboBox(2).isEnabled(), "Scan ID combo should be enabled");
            _bot.comboBox(2).setText(Environment.SCAN_ID);
            sleep(2000);
            
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