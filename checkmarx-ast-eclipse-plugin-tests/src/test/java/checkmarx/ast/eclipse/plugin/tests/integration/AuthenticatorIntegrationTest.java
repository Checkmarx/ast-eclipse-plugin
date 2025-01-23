package checkmarx.ast.eclipse.plugin.tests.integration;

import static org.junit.Assert.*;
import org.junit.Test;


import com.checkmarx.eclipse.runner.Authenticator;
import com.checkmarx.eclipse.utils.PluginConstants;

public class AuthenticatorIntegrationTest extends BaseIntegrationTest {

    @Test
    public void testSuccessfulAuthentication() {
        String result = Authenticator.INSTANCE.doAuthentication(VALID_API_KEY, "");
        assertNotNull("Authentication result should not be null", result);
        assertFalse("Authentication result should not contain error", result.toLowerCase().contains("error"));
    }
//
//    @Test
//    public void testFailedAuthentication() {
//        String result = Authenticator.INSTANCE.doAuthentication("invalid_api_key", "");
//        assertTrue("Authentication with invalid key should fail", result.toLowerCase().contains("error"));
//    }
//
//    @Test
//    public void testAdditionalParameters() {
//        String result = Authenticator.INSTANCE.doAuthentication(VALID_API_KEY, "--additional-param value");
//        assertNotNull("Authentication with additional parameters should work", result);
//        assertFalse("Authentication result should not contain error", result.toLowerCase().contains("error"));
//    }
//
//    @Test
//    public void testEmptyApiKey() {
//        String result = Authenticator.INSTANCE.doAuthentication("", "");
//        assertTrue("Empty API key should fail", result.toLowerCase().contains("error"));
//    }
//
//    @Test
//    public void testNullApiKey() {
//        String result = Authenticator.INSTANCE.doAuthentication(null, null);
//        assertTrue("Null API key should fail", result.toLowerCase().contains("error"));
//    }
}