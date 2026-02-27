package checkmarx.ast.eclipse.plugin.tests.integration;


import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.checkmarx.eclipse.runner.Authenticator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

public class AuthenticatorIntegrationTest extends BaseIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthenticatorIntegrationTest.class);
    
    @Mock
    private Authenticator authenticator; 

    @Test
    public void testSuccessfulAuthentication() {
    	 authenticator = new Authenticator(LoggerFactory.getLogger(Authenticator.class)); 
        System.out.println("\n=== Starting Authentication Test ===");
        System.out.println("Current directory: " + new File(".").getAbsolutePath());
        System.out.println("API Key available: " + (VALID_API_KEY != null));
		String result = authenticator.doAuthentication(VALID_API_KEY, "");
		System.out.println("Authentication result: " + result);
		assertNotNull("Authentication result should not be null", result);
		assertFalse(result.toLowerCase().contains("error"), "Authentication result should not contain error");
		System.out.println("=== Authentication Test Completed ===\n");
	}

    @Test
	public void testInvalidApiKeyAuthentication() {
    	 authenticator = new Authenticator(LoggerFactory.getLogger(Authenticator.class)); 
		System.out.println("\n=== Starting Invalid API Key Test ===");
		String invalidApiKey = "invalid-api-key";
		String result = authenticator.doAuthentication(invalidApiKey, "");
		System.out.println("Authentication result with invalid API key: " + result);
		assertNotNull("Result should not be null for invalid API key", result);
		assertTrue(result.toLowerCase().contains("error"), "Result should contain error for invalid API key");
		System.out.println("=== Invalid API Key Test Completed ===\n");
    }
}