package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import com.checkmarx.eclipse.utils.PluginConstants;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestPreferences extends BaseUITest {
    
    @Test
    public void testPreferencesPageOpen() throws TimeoutException {
        // Open preferences window
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);
        
        // Verify preferences page opens with correct fields
        assertTrue("API Key field should be visible", 
            _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).isVisible());
        assertTrue("Test Connection button should be visible",
            _bot.button(BTN_TEST_CONNECTION).isVisible());
    }

    @Test
    public void testInvalidAPIKey() throws TimeoutException {
        // Open preferences window
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);
        
        // Try to connect with invalid API key
        _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText("invalid-key");
        _bot.button(BTN_TEST_CONNECTION).click();
        
        // Check for appropriate error message
        waitForConnectionResponse();
        assertFalse("Should show error message for invalid API key",
            _bot.text(3).getText().equals(INFO_SUCCESSFUL_CONNECTION));
    }

    @Test
    public void testPreferencesSave() throws TimeoutException {
        // Open preferences window
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);
        
        // Save preferences and verify they persist
        String testApiKey = "test-api-key";
        _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText(testApiKey);
        _bot.button(BTN_APPLY).click();
        
        // Reopen preferences to verify
        _bot.shell(ITEM_PREFERENCES).close();
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);
        
        assertEquals("API Key should persist after saving",
            testApiKey,
            _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).getText());
    }
}