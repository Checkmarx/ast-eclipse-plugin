package checkmarx.ast.eclipse.plugin.tests.integration;

import static org.junit.Assert.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.checkmarx.eclipse.runner.Authenticator;
import java.io.File;

public class AuthenticatorIntegrationTest extends BaseIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthenticatorIntegrationTest.class);

    @Test
    public void testSuccessfulAuthentication() {
        System.out.println("\n=== Starting Authentication Test ===");
        System.out.println("Current directory: " + new File(".").getAbsolutePath());
        System.out.println("API Key available: " + (VALID_API_KEY != null));
        String result = authenticator.doAuthentication(VALID_API_KEY, "");
        System.out.println("Authentication result: " + result);
        assertNotNull("Authentication result should not be null", result);
        assertFalse("Authentication result should not contain error", result.toLowerCase().contains("error"));
        System.out.println("=== Authentication Test Completed ===\n");
    }

    @Test
    public void testInvalidApiKeyAuthentication() {
        System.out.println("\n=== Starting Invalid API Key Test ===");
        String invalidApiKey = "invalid-api-key";
        String result = authenticator.doAuthentication(invalidApiKey, "");
        System.out.println("Authentication result with invalid API key: " + result);
        assertNotNull("Result should not be null for invalid API key", result);
        assertTrue("Result should contain error for invalid API key", result.toLowerCase().contains("error"));
        System.out.println("=== Invalid API Key Test Completed ===\n");
    }
}