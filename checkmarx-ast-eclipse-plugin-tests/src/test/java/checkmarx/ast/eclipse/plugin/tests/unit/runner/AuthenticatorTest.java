package checkmarx.ast.eclipse.plugin.tests.unit.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.common.runner.Authenticator;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.utils.PluginConstants;

class AuthenticatorTest {

    @Test
    void testDoAuthenticationSuccess() throws Exception {


        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate()).thenReturn("SUCCESS"));
             MockedStatic<CxLogger> mockedCxLogger = Mockito.mockStatic(CxLogger.class)) {

            String result = Authenticator.INSTANCE.doAuthentication("dummyKey", "--param");

            assertEquals("SUCCESS", result);
            mockedCxLogger.verify(() -> CxLogger.info(String.format(PluginConstants.INFO_AUTHENTICATION_STATUS, "SUCCESS")));
        }
    }

    @Test
    void testDoAuthenticationIOException() throws Exception {


        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate())
                                     .thenThrow(new IOException("IO error")));
             MockedStatic<CxLogger> mockedCxLogger = Mockito.mockStatic(CxLogger.class)) {

            String result = Authenticator.INSTANCE.doAuthentication("dummyKey", "--param");

            assertEquals("IO error", result);
            mockedCxLogger.verify(() -> CxLogger.error(
                    eq(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, "IO error")),
                    any(IOException.class)
            ));
        }
    }

    @Test
    void testDoAuthenticationInterruptedException() throws Exception {


        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate())
                                     .thenThrow(new InterruptedException("Interrupted")));
             MockedStatic<CxLogger> mockedCxLogger = Mockito.mockStatic(CxLogger.class)) {

            String result = Authenticator.INSTANCE.doAuthentication("dummyKey", "--param");

            assertEquals("Interrupted", result);
            mockedCxLogger.verify(() -> CxLogger.error(
                    eq(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, "Interrupted")),
                    any(InterruptedException.class)
            ));
        }
    }

    @Test
    void testDoAuthenticationCxException() throws Exception {

        try (MockedConstruction<CxWrapper> mocked =
                     Mockito.mockConstruction(CxWrapper.class,
                             (mock, context) -> when(mock.authValidate())
                                     .thenThrow(new CxException(1, "Cx error")));
             MockedStatic<CxLogger> mockedCxLogger = Mockito.mockStatic(CxLogger.class)) {

            String result = Authenticator.INSTANCE.doAuthentication("dummyKey", "--param");

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
