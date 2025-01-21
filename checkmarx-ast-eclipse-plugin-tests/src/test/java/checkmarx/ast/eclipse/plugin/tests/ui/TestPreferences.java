package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertFalse;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import com.checkmarx.eclipse.utils.PluginConstants;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestPreferences extends BaseUITest {
    
    private static final String ASSERT_API_KEY_EMPTY = "API Key field must not be empty after setting";
    
    @Test
    public void testPreferencesConnection() throws TimeoutException {
        // Test Connection using parent's method
        testSuccessfulConnection(false);        
    }

    @Test
    public void testInvalidKey() throws TimeoutException {
        preventWidgetWasNullInCIEnvironment();
        
        // Open preferences
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);
        
        // Set invalid key and test
        _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText("invalid-key");
        _bot.button(BTN_APPLY).click();
        _bot.button(BTN_TEST_CONNECTION).click();
        
        // Use parent's method to check connection
        waitForConnectionResponse();
        
        _bot.button(BTN_APPLY_AND_CLOSE).click();
    }
}