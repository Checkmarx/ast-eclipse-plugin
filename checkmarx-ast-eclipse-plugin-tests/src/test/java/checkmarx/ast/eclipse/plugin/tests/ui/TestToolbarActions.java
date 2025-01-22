package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarButton;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.utils.PluginConstants;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestToolbarActions extends BaseUITest {

    private static final String ASSERT_REFRESH_ENABLED = "Refresh button should be enabled";
    private static final String ASSERT_CLEAR_ENABLED = "Clear button should be enabled";
    private static final String ASSERT_TREE_CLEARED = "Tree should be cleared after clear action";
    private static final String ASSERT_RESULTS_REFRESHED = "Results should be updated after refresh";

    @Test
    public void testRefreshAction() throws TimeoutException {
        setUpCheckmarxPlugin(true);

        // Get initial results count
        String initialResults = _bot.tree(1).cell(0, 0);
        
        // Click refresh
        SWTBotToolbarButton refreshBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN)
            .getToolbarButtons().stream()
            .filter(btn -> btn.getToolTipText().equals(PluginConstants.TOOLBAR_ACTION_REFRESH))
            .findFirst().get();
            
        assertTrue(ASSERT_REFRESH_ENABLED, refreshBtn.isEnabled());
        refreshBtn.click();
        
        sleep(2000);
        
        // Verify results are refreshed
        String refreshedResults = _bot.tree(1).cell(0, 0);
        assertTrue(ASSERT_RESULTS_REFRESHED, 
            !refreshedResults.equals(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN));

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }

    @Test
    public void testClearAction() throws TimeoutException {
        setUpCheckmarxPlugin(true);

        // Click clear results
        SWTBotToolbarButton clearBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN)
            .getToolbarButtons().stream()
            .filter(btn -> btn.getToolTipText().equals(PluginConstants.TOOLBAR_ACTION_CLEAR_RESULTS))
            .findFirst().get();
            
        assertTrue(ASSERT_CLEAR_ENABLED, clearBtn.isEnabled());
        clearBtn.click();
        
        sleep(1000);
        
        // Verify tree is cleared
        assertTrue(ASSERT_TREE_CLEARED, 
            _bot.tree(1).getAllItems().length == 0);

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }
} 