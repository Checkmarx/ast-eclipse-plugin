package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import com.checkmarx.eclipse.views.GlobalSettings;
import com.checkmarx.eclipse.views.filters.FilterState;

class GlobalSettingsTest {

    @Test
    void testSetAndGetProjectId() {

        GlobalSettings settings = new GlobalSettings();

        settings.setProjectId("project1");

        assertEquals("project1", settings.getProjectId());
    }

    @Test
    void testSetAndGetBranch() {

        GlobalSettings settings = new GlobalSettings();

        settings.setBranch("main");

        assertEquals("main", settings.getBranch());
    }

    @Test
    void testSetAndGetScanId() {

        GlobalSettings settings = new GlobalSettings();

        settings.setScanId("scan123");

        assertEquals("scan123", settings.getScanId());
    }

    @Test
    void testStoreInPreferencesDoesNotThrow() {

        assertDoesNotThrow(() ->
                GlobalSettings.storeInPreferences("test-key", "test-value")
        );
    }

    @Test
    void testGetFromPreferencesReturnsValue() {

        String value = GlobalSettings.getFromPreferences("non-existing", "default");

        assertNotNull(value);
    }

    @Test
    void testLoadSettings() {

        GlobalSettings settings = new GlobalSettings();

        try (MockedStatic<FilterState> filterMock = Mockito.mockStatic(FilterState.class)) {

            settings.loadSettings();

            filterMock.verify(FilterState::loadFiltersFromSettings);
        }

        assertNotNull(settings.getProjectId());
        assertNotNull(settings.getBranch());
        assertNotNull(settings.getScanId());
    }
}