package checkmarx.ast.eclipse.plugin.tests.unit.wrapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.common.wrapper.CxWrapperFactory;

class CxWrapperFactoryTest {

    @Test
    void testBuildWithNoArgs_usesSavedPreferencesAndStampsAgentName() throws Exception {
        AtomicReference<CxConfig> capturedConfig = new AtomicReference<>();

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class,
                (mock, context) -> capturedConfig.set((CxConfig) context.arguments().get(0)));
             var mockedPreferences = mockStatic(Preferences.class)) {

            mockedPreferences.when(Preferences::getApiKey).thenReturn("saved-api-key");
            mockedPreferences.when(Preferences::getAdditionalOptions).thenReturn("--saved-param");

            CxWrapperFactory.build();

            assertEquals(1, mocked.constructed().size());
            CxConfig config = capturedConfig.get();
            assertNotNull(config);
            assertEquals("saved-api-key", config.getApiKey());
            assertEquals("--saved-param", String.join(" ", config.getAdditionalParameters()));
            assertNotNull(config.getAgentName());
            assertTrue(config.getAgentName().startsWith("Eclipse_"),
                    "Agent name should be stamped as Eclipse_<version>, was: " + config.getAgentName());
        }
    }

    @Test
    void testBuildWithExplicitCredentials_doesNotUseSavedPreferences() throws Exception {
        AtomicReference<CxConfig> capturedConfig = new AtomicReference<>();

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class,
                (mock, context) -> capturedConfig.set((CxConfig) context.arguments().get(0)));
             var mockedPreferences = mockStatic(Preferences.class)) {

            CxWrapperFactory.build("typed-api-key", "--typed-param");

            assertEquals(1, mocked.constructed().size());
            CxConfig config = capturedConfig.get();
            assertNotNull(config);
            assertEquals("typed-api-key", config.getApiKey());
            assertEquals("--typed-param", String.join(" ", config.getAdditionalParameters()));
            assertTrue(config.getAgentName().startsWith("Eclipse_"));

            mockedPreferences.verifyNoInteractions();
        }
    }
}
