package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.utils.PluginConstants;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestResultDetails extends BaseUITest {

    private static final String ASSERT_DETAILS_VISIBLE = "Result details should be visible when result is selected";
    private static final String ASSERT_DETAILS_HIDDEN = "Result details should be hidden when no result is selected";
    private static final String ASSERT_DESCRIPTION_NOT_EMPTY = "Result description should not be empty";
    private static final String ASSERT_SEVERITY_NOT_EMPTY = "Severity field should not be empty";
    private static final String ASSERT_STATE_NOT_EMPTY = "State field should not be empty";

    @Test
    public void testResultDetailsVisibility() throws TimeoutException {
        setUpCheckmarxPlugin(true);

        // Initially details should be hidden
        assertFalse(ASSERT_DETAILS_HIDDEN, isDetailsVisible());

        // Select first result
        SWTBotTreeItem resultNode = getFirstResultNode();
        resultNode.select();
        sleep(1000);

        // Verify details are shown
        assertTrue(ASSERT_DETAILS_VISIBLE, isDetailsVisible());

        // Verify details content
        assertTrue(ASSERT_DESCRIPTION_NOT_EMPTY, 
            !_bot.textWithId(PluginConstants.DESCRIPTION_TEXT_ID).getText().isEmpty());
        assertTrue(ASSERT_SEVERITY_NOT_EMPTY,
            !_bot.comboBoxWithId(PluginConstants.TRIAGE_SEVERITY_COMBO_ID).getText().isEmpty());
        assertTrue(ASSERT_STATE_NOT_EMPTY,
            !_bot.comboBoxWithId(PluginConstants.TRIAGE_STATE_COMBO_ID).getText().isEmpty());

        // Deselect result
        _bot.tree(1).select();
        sleep(1000);

        // Verify details are hidden again
        assertFalse(ASSERT_DETAILS_HIDDEN, isDetailsVisible());

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }

    private boolean isDetailsVisible() {
        try {
            _bot.textWithId(PluginConstants.DESCRIPTION_TEXT_ID);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private SWTBotTreeItem getFirstResultNode() {
        String firstNodeName = _bot.tree(1).cell(0, 0);
        SWTBotTreeItem node = _bot.tree(1).getTreeItem(firstNodeName);
        while(!node.getNodes().isEmpty()) {
            node = node.expand().getNode(0);
        }
        return node;
    }
} 