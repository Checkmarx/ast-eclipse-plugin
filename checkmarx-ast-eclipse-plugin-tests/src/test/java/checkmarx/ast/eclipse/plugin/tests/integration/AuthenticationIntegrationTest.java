package checkmarx.ast.eclipse.plugin.tests.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AuthenticationIntegrationTest extends BaseIntegrationTest {
    
    @Test
    public void testAuthentication() throws Exception {
        String authResponse = wrapper.authValidate();
        
        assertNotNull("Auth response should not be null", authResponse);
        assertTrue("Auth should be successful", authResponse.toLowerCase().contains("authenticated successfully"));
    }
} 