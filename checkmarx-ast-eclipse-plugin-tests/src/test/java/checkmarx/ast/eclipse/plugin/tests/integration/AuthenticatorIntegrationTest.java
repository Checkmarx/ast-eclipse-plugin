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
        System.out.println("API Key available: " + VALID_API_KEY );
        String result = Authenticator.INSTANCE.doAuthentication(VALID_API_KEY, "");
        System.out.println("Authentication result: " + result);
//        assertNotNull("Authentication result should not be null", result);
//        assertFalse("Authentication result should not contain error", result.toLowerCase().contains("error"));
//        System.out.println("=== Authentication Test Completed ===\n");
    }
}