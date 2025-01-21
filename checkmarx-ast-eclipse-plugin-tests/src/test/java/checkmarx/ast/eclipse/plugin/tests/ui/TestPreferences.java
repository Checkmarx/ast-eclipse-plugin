package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.utils.PluginConstants;

@RunWith(SWTBotJunit4ClassRunner.class)
public class ApiKeyTest extends BaseUITest {

    @Test
    public void testValidApiKeyConnection() throws TimeoutException {
        // Open the Preferences window
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);

        // Enter a valid API Key
        String validApiKey = "your-valid-api-key"; // Replace with a valid API key
        _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText(validApiKey);
        _bot.button(BTN_APPLY).click();
        _bot.button(BTN_TEST_CONNECTION).click();

        // Wait for a response
        waitForConnectionResponse();

        // Validate: the connection should succeed
        assertTrue("Connection should succeed with a valid API key", 
            _bot.text(3).getText().contains("Successfully authenticated"));

        // Close the Preferences window
        _bot.button(BTN_APPLY_AND_CLOSE).click();
    }

    @Test
    public void testInvalidApiKeyConnection() throws TimeoutException {
        // Open the Preferences window
        _bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
        _bot.shell(ITEM_PREFERENCES).activate();
        _bot.tree().select(ITEM_CHECKMARX_AST);

        // Enter an invalid API Key
        String invalidApiKey = "invalid-api-key";
        _bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText(invalidApiKey);
        _bot.button(BTN_APPLY).click();
        _bot.button(BTN_TEST_CONNECTION).click();

        // Wait for a response
        waitForConnectionResponse();

        // Validate: the connection should fail
        assertFalse("Connection should fail with an invalid API key", 
            _bot.text(3).getText().contains("Successfully authenticated"));

        // Close the Preferences window
        _bot.button(BTN_APPLY_AND_CLOSE).click();
    }

    private void waitForConnectionResponse() throws TimeoutException {
        int retryIdx = 0;
        while (!_bot.text(3).getText().contains("Successfully authenticated") && 
                retryIdx++ < 10) {
            _bot.sleep(1000); // Wait for a short interval
        }
    }
}
