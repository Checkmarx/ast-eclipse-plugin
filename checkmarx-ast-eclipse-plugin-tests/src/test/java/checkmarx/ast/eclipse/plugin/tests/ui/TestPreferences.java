package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.utils.PluginConstants;

import checkmarx.ast.eclipse.plugin.tests.common.Environment;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestPreferences extends BaseUITest {

    private static final String ASSERT_API_KEY_EMPTY = "API Key field must not be empty after setting";
    private static final String ASSERT_CONNECTION_FAILED = "Connection should fail with an invalid API key";
    private static final String ASSERT_CONNECTION_SUCCESS = "Connection should succeed with a valid API key";

    @Test
    public void testValidApiKeyConnection() throws TimeoutException {
        preventWidgetWasNullInCIEnvironment();

        // Open Preferences
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);

        // Set valid API key
        _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText(Environment.API_KEY);
        _bot.button(BTN_APPLY).click();
        _bot.button(BTN_TEST_CONNECTION).click();

        // Wait for successful connection message
        waitForConnectionResponse();

        // Validate success message
        assertTrue(ASSERT_CONNECTION_SUCCESS, 
            _bot.text(3).getText().contains("Successfully authenticated"));

        _bot.button(BTN_APPLY_AND_CLOSE).click();
    }

    @Test
    public void testInvalidApiKeyConnection() throws TimeoutException {
        preventWidgetWasNullInCIEnvironment();

        // Open Preferences
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);

        // Set invalid API key
        _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText("invalid-key");
        _bot.button(BTN_APPLY).click();
        _bot.button(BTN_TEST_CONNECTION).click();

        // Wait for error response
        SWTBotPreferences.TIMEOUT = 5000; // Adjusted timeout
        _bot.sleep(5000); // Simulating waiting time for the response

        // Validate error message
        assertFalse(ASSERT_CONNECTION_FAILED, 
            _bot.text(3).getText().contains("Successfully authenticated"));

        _bot.button(BTN_APPLY_AND_CLOSE).click();
    }

    @Test
    public void testApiKeyFieldNotEmpty() {
        preventWidgetWasNullInCIEnvironment();

        // Open Preferences
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);

        // Set API Key
        String apiKey = "dummy-api-key";
        _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText(apiKey);
        _bot.button(BTN_APPLY).click();

        // Validate the API Key field is not empty
        String currentKey = _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).getText();
        assertEquals(ASSERT_API_KEY_EMPTY, apiKey, currentKey);

        _bot.button(BTN_APPLY_AND_CLOSE).click();
    }

    @Test
    public void testEmptyApiKeyFieldAfterClear() {
        preventWidgetWasNullInCIEnvironment();

        // Open Preferences
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);

        // Clear API Key
        _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText("");
        _bot.button(BTN_APPLY).click();

        // Validate the API Key field is empty
        String currentKey = _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).getText();
        assertEquals("API Key field should be empty after clearing", "", currentKey);

        _bot.button(BTN_APPLY_AND_CLOSE).click();
    }

    private void waitForConnectionResponse() throws TimeoutException {
        int retryIdx = 0;
        while (!_bot.text(3).getText().contains("Successfully authenticated")) {
            if (retryIdx++ == 10) {
                throw new TimeoutException("Connection validation timeout after 10000ms.");
            }
            _bot.sleep(1000);
        }
    }
}
