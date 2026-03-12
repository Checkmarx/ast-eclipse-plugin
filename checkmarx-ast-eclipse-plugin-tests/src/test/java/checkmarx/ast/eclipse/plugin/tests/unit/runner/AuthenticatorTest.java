package checkmarx.ast.eclipse.plugin.tests.unit.runner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.slf4j.Logger;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.runner.Authenticator;
import com.checkmarx.eclipse.utils.PluginConstants;

class AuthenticatorTest {

    @Test
    void testDoAuthenticationSuccess() throws Exception {

        Logger mockLogger = mock(Logger.class);

        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate()).thenReturn("SUCCESS"))) {

            Authenticator authenticator = new Authenticator(mockLogger);

            String result = authenticator.doAuthentication("dummyKey", "--param");

            assertEquals("SUCCESS", result);
            verify(mockLogger).info("Authentication Status: SUCCESS");
        }
    }

    @Test
    void testDoAuthenticationIOException() throws Exception {

        Logger mockLogger = mock(Logger.class);

        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate())
                                     .thenThrow(new IOException("IO error")))) {

            Authenticator authenticator = new Authenticator(mockLogger);

            String result = authenticator.doAuthentication("dummyKey", "--param");

            assertEquals("IO error", result);
            verify(mockLogger).error(
            	    eq(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, "IO error")),
            	    any(IOException.class)
            	);
        }
    }

    @Test
    void testDoAuthenticationInterruptedException() throws Exception {

        Logger mockLogger = mock(Logger.class);

        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate())
                                     .thenThrow(new InterruptedException("Interrupted")))) {

            Authenticator authenticator = new Authenticator(mockLogger);

            String result = authenticator.doAuthentication("dummyKey", "--param");

            assertEquals("Interrupted", result);
            verify(mockLogger).error(
            	    eq(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, "Interrupted")),
            	    any(InterruptedException.class)
            	);
        }
    }

    @Test
    void testDoAuthenticationCxException() throws Exception {

        Logger mockLogger = mock(Logger.class);

        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate())
                                     .thenThrow(new CxException(1, "Cx error")))) {

            Authenticator authenticator = new Authenticator(mockLogger);

            String result = authenticator.doAuthentication("dummyKey", "--param");

            assertEquals("Cx error", result);
            verify(mockLogger).error(
            	    eq(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, "Cx error")),
            	    any(CxException.class)
            	);
        }
    }

    @Test
    void testSingletonInstanceNotNull() {
        assertNotNull(Authenticator.INSTANCE);
    }
}
