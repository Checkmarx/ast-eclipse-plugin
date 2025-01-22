package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.utils.PluginConstants;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestProjectAndBranchSelection extends BaseUITest {

    private static final String ASSERT_PROJECT_COMBO_ENABLED = "Project combo should be enabled after authentication";
    private static final String ASSERT_BRANCH_COMBO_DISABLED = "Branch combo should be disabled before project selection";
    private static final String ASSERT_BRANCH_COMBO_ENABLED = "Branch combo should be enabled after project selection";
    private static final String ASSERT_PROJECT_SELECTED = "Selected project should match the expected value";
    private static final String ASSERT_BRANCH_SELECTED = "Selected branch should match the expected value";

    @Test
    public void testProjectAndBranchSelectionFlow() throws TimeoutException {
        // Set up plugin with authentication
        testSuccessfulConnection(false);
        addCheckmarxPlugin(true);

        // Get combo boxes
        SWTBotCombo projectCombo = _bot.comboBox(0);
        SWTBotCombo branchCombo = _bot.comboBox(1);

        // Verify initial state
        assertTrue(ASSERT_PROJECT_COMBO_ENABLED, projectCombo.isEnabled());
        assertFalse(ASSERT_BRANCH_COMBO_DISABLED, branchCombo.isEnabled());

        // Select first project
        projectCombo.setSelection(0);
        sleep(2000);

        // Verify branch combo is enabled after project selection
        assertTrue(ASSERT_BRANCH_COMBO_ENABLED, branchCombo.isEnabled());

        // Select first branch
        branchCombo.setSelection(0);
        sleep(1000);

        // Verify selections
        String selectedProject = projectCombo.getText();
        String selectedBranch = branchCombo.getText();
        
        assertFalse("Project selection should not be empty", selectedProject.isEmpty());
        assertFalse("Branch selection should not be empty", selectedBranch.isEmpty());

        // Clear project selection
        projectCombo.setText("");
        sleep(1000);

        // Verify branch combo is disabled again
        assertFalse(ASSERT_BRANCH_COMBO_DISABLED, branchCombo.isEnabled());

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }

    @Test 
    public void testProjectSearch() throws TimeoutException {
        testSuccessfulConnection(false);
        addCheckmarxPlugin(true);

        SWTBotCombo projectCombo = _bot.comboBox(0);
        
        // Type partial project name
        String partialName = "test";
        projectCombo.setText(partialName);
        sleep(2000);

        // Verify filtered results
        String[] items = projectCombo.items();
        for (String item : items) {
            assertTrue("Filtered items should contain search text", 
                item.toLowerCase().contains(partialName.toLowerCase()));
        }

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }
} 