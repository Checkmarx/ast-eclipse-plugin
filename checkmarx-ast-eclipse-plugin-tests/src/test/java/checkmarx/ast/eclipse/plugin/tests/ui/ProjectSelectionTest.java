package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.utils.PluginConstants;
import checkmarx.ast.eclipse.plugin.tests.common.Environment;

@RunWith(SWTBotJunit4ClassRunner.class)
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
            assertTrue("Branch combo should be enabled", _bot.comboBox(1).isEnabled());
            _bot.comboBox(1).setText(TEST_BRANCH);
            sleep(2000);
            
            // Verify scan ID combo is enabled and select scan
            assertTrue("Scan ID combo should be enabled", _bot.comboBox(2).isEnabled());
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