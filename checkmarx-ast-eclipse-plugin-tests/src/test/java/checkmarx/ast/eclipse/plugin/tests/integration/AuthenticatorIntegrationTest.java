package checkmarx.ast.eclipse.plugin.tests.integration;

import static org.junit.Assert.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.eclipse.runner.Authenticator;

public class AuthenticatorIntegrationTest extends BaseIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthenticatorIntegrationTest.class);

    @Test
    public void testSuccessfulAuthentication() {
        logger.info("Starting successful authentication test");
        String result = Authenticator.INSTANCE.doAuthentication(VALID_API_KEY, "");
        logger.info("Authentication result: {}", result);
        assertNotNull("Authentication result should not be null", result);
        assertFalse("Authentication result should not contain error", result.toLowerCase().contains("error"));
        logger.info("Authentication test completed successfully");
    }
}