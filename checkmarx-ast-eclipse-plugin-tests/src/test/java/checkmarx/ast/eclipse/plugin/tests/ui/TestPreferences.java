package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import java.util.concurrent.TimeoutException;

import com.checkmarx.eclipse.utils.PluginConstants;
import checkmarx.ast.eclipse.plugin.tests.common.Environment;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestPreferences extends BaseUITest {
    
    public static final String ASSERT_API_KEY_EMPTY = "API Key field must not be empty after setting";
    public static final String ASSERT_CONNECTION_FAILED = "Connection test should fail with invalid key";

    @Test
    public void testPreferencesPageConnection() throws TimeoutException {
        // Set timeout for test
        SWTBotPreferences.TIMEOUT = 20000;
        
        preventWidgetWasNullInCIEnvironment();
        
        // Open preferences window
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);

        _bot.sleep(1000);

        // Test with valid API key
        _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText(Environment.API_KEY);
        _bot.button(BTN_APPLY).click();
        _bot.button(BTN_TEST_CONNECTION).click();
        
        waitForConnectionResponse();
        
        _bot.shell(ITEM_PREFERENCES).setFocus();
        _bot.button(BTN_APPLY_AND_CLOSE).click();

        SWTBotPreferences.TIMEOUT = 5000;
    }

    @Test
    public void testPreferencesInvalidKey() throws TimeoutException {
        preventWidgetWasNullInCIEnvironment();
        
        // Open preferences
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);
        
        // Set invalid key and test
        _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText("invalid-key");
        _bot.button(BTN_APPLY).click();
        _bot.button(BTN_TEST_CONNECTION).click();
        
        // Wait and verify failure
        _bot.sleep(5000);
        assertFalse(ASSERT_CONNECTION_FAILED, _bot.text(3).getText().equals(INFO_SUCCESSFUL_CONNECTION));
        
        _bot.button(BTN_APPLY_AND_CLOSE).click();
    }
}