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
        testSuccessfulConnection(false);        
    }

    @Test
    public void testInvalidKey() throws TimeoutException {
        try {
            preventWidgetWasNullInCIEnvironment();
            
            _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
            _bot.shell(ITEM_PREFERENCES).activate();
            _bot.tree().select(ITEM_CHECKMARX_AST);
            
            // Test invalid key
            _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText("invalid-key");
            _bot.button(BTN_APPLY).click();
            _bot.button(BTN_TEST_CONNECTION).click();
            
            _bot.sleep(5000);
            
            assertFalse("Connection should fail with invalid key", 
                _bot.text(3).getText().contains("Successfully authenticated"));
            
        } finally {
            // Restore valid API key for next tests
            _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText(Environment.API_KEY);
            _bot.button(BTN_APPLY).click();
            _bot.button(BTN_TEST_CONNECTION).click();
            waitForConnectionResponse();
            _bot.button(BTN_APPLY_AND_CLOSE).click();
            _cxSettingsDefined = true;  // Make sure flag is set for next tests
        }
    }
}