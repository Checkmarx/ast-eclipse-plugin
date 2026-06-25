package checkmarx.ast.eclipse.plugin.tests.unit.runner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.runner.Authenticator;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;

class AuthenticatorTest {

    @Test
    void testDoAuthenticationSuccess() throws Exception {

        Logger mockLogger = mock(Logger.class);

        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate()).thenReturn("SUCCESS"));
             MockedStatic<CxLogger> mockedCxLogger = Mockito.mockStatic(CxLogger.class)) {

            Authenticator authenticator = new Authenticator(mockLogger);

            String result = authenticator.doAuthentication("dummyKey", "--param");

            assertEquals("SUCCESS", result);
            mockedCxLogger.verify(() -> CxLogger.info(String.format(PluginConstants.INFO_AUTHENTICATION_STATUS, "SUCCESS")));
        }
    }

    @Test
    void testDoAuthenticationIOException() throws Exception {

        Logger mockLogger = mock(Logger.class);

        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate())
                                     .thenThrow(new IOException("IO error")));
             MockedStatic<CxLogger> mockedCxLogger = Mockito.mockStatic(CxLogger.class)) {

            Authenticator authenticator = new Authenticator(mockLogger);

            String result = authenticator.doAuthentication("dummyKey", "--param");

            assertEquals("IO error", result);
            mockedCxLogger.verify(() -> CxLogger.error(
                    eq(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, "IO error")),
                    any(IOException.class)
            ));
        }
    }

    @Test
    void testDoAuthenticationInterruptedException() throws Exception {

        Logger mockLogger = mock(Logger.class);

        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate())
                                     .thenThrow(new InterruptedException("Interrupted")));
             MockedStatic<CxLogger> mockedCxLogger = Mockito.mockStatic(CxLogger.class)) {

            Authenticator authenticator = new Authenticator(mockLogger);

            String result = authenticator.doAuthentication("dummyKey", "--param");

            assertEquals("Interrupted", result);
            mockedCxLogger.verify(() -> CxLogger.error(
                    eq(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, "Interrupted")),
                    any(InterruptedException.class)
            ));
        }
    }

    @Test
    void testDoAuthenticationCxException() throws Exception {

        Logger mockLogger = mock(Logger.class);

        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate())
                                     .thenThrow(new CxException(1, "Cx error")));
             MockedStatic<CxLogger> mockedCxLogger = Mockito.mockStatic(CxLogger.class)) {

            Authenticator authenticator = new Authenticator(mockLogger);

            String result = authenticator.doAuthentication("dummyKey", "--param");

            assertEquals("Cx error", result);
            mockedCxLogger.verify(() -> CxLogger.error(
                    eq(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, "Cx error")),
                    any(CxException.class)
            ));
        }
    }

    @Test
    void testSingletonInstanceNotNull() {
        assertNotNull(Authenticator.INSTANCE);
    }
}
