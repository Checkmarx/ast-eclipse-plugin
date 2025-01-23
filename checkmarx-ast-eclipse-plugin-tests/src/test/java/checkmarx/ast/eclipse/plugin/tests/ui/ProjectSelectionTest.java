package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.views.ViewConstants;
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
            
            // Get combo boxes
            SWTBotCombo projectCombo = _bot.comboBox(0);
            SWTBotCombo branchCombo = _bot.comboBox(1);
            SWTBotCombo scanIdCombo = _bot.comboBox(2);
            
            // Verify initial state - all empty
            assertTrue("Project combo should start empty", 
                projectCombo.getText().isEmpty() || 
                projectCombo.getText().equals("Select a project"));
            
            assertTrue("Branch combo should start empty",
                branchCombo.getText().isEmpty());
            
            assertTrue("Scan ID combo should start empty",
                scanIdCombo.getText().isEmpty() || 
                scanIdCombo.getText().equals(PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER));
            
            // Select project
            projectCombo.setText(TEST_PROJECT);
            sleep(2000);
            
            // Verify branch combo is enabled and select branch
            assertTrue("Branch combo should be enabled", branchCombo.isEnabled());
            branchCombo.setText(TEST_BRANCH);
            sleep(2000);
            
            // Verify scan ID combo is enabled and select scan
            assertTrue("Scan ID combo should be enabled", scanIdCombo.isEnabled());
            scanIdCombo.setText(Environment.SCAN_ID);  // CX_TEST_SCAN
            sleep(2000);
            
            // Cleanup
            _bot.viewByTitle(ViewConstants.VIEW_CHECKMARX_AST_SCAN).close();
            
        } catch (Exception e) {
            System.out.println("\n=== Test Failed ===");
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
} 