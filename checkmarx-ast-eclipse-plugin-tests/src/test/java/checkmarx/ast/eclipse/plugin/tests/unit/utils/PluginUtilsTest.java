package checkmarx.ast.eclipse.plugin.tests.unit.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Combo;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.filters.FilterState;

public class PluginUtilsTest {

    @Test
    void testConvertStringTimeStampValid() {
        String input = "2024-01-01T10:00:00Z";

        String result = PluginUtils.convertStringTimeStamp(input);

        assertNotNull(result);
        assertTrue(result.contains("2024"));
    }

    @Test
    void testConvertStringTimeStampInvalid() {
        String input = "invalid-date";

        String result = PluginUtils.convertStringTimeStamp(input);

        assertEquals(input, result);
    }

    @Test
    void testValidateScanIdFormatValid() {
        String scanId = "d61aaad4-38fb-406c-bf54-b9b8475f81a5";

        boolean result = PluginUtils.validateScanIdFormat(scanId);

        assertTrue(result);
    }

    @Test
    void testValidateScanIdFormatInvalid() {
        String scanId = "invalid-scan-id";

        boolean result = PluginUtils.validateScanIdFormat(scanId);

        assertFalse(result);
    }

    @Test
    void testEnableComboViewer() {
        ComboViewer viewer = mock(ComboViewer.class);
        Combo combo = mock(Combo.class);

        when(viewer.getCombo()).thenReturn(combo);

        PluginUtils.enableComboViewer(viewer, true);

        verify(combo).setEnabled(true);
    }

    @Test
    void testSetTextForComboViewer() {
        ComboViewer viewer = mock(ComboViewer.class);
        Combo combo = mock(Combo.class);

        when(viewer.getCombo()).thenReturn(combo);

        PluginUtils.setTextForComboViewer(viewer, "TestText");

        verify(combo).setText("TestText");
        verify(combo).update();
    }

    @Test
    void testUpdateFiltersEnabledAndCheckedState() {

        Action action = mock(Action.class);
        when(action.getId()).thenReturn("LOW");

        List<Action> actions = new ArrayList<>();
        actions.add(action);

        try (MockedStatic<DataProvider> dp = Mockito.mockStatic(DataProvider.class);
             MockedStatic<FilterState> fs = Mockito.mockStatic(FilterState.class)) {

            DataProvider provider = mock(DataProvider.class);
            dp.when(DataProvider::getInstance).thenReturn(provider);
            when(provider.containsResults()).thenReturn(true);

            fs.when(() -> FilterState.isSeverityEnabled("LOW")).thenReturn(true);

            PluginUtils.updateFiltersEnabledAndCheckedState(actions);

            verify(action).setEnabled(true);
            verify(action).setChecked(true);
        }
    }

    @Test
    void testMessageCreation() {
        DisplayModel model = PluginUtils.message("Hello");

        assertNotNull(model);
    }

    @Test
    void testShowMessage() {

        DisplayModel root = new DisplayModel.DisplayModelBuilder("root").build();
        TreeViewer viewer = mock(TreeViewer.class);

        PluginUtils.showMessage(root, viewer, "Test message");

        assertEquals(1, root.children.size());
        verify(viewer).refresh();
    }

    @Test
    void testClearMessage() {

        DisplayModel root = new DisplayModel.DisplayModelBuilder("root").build();
        root.children.add(new DisplayModel.DisplayModelBuilder("child").build());

        TreeViewer viewer = mock(TreeViewer.class);

        PluginUtils.clearMessage(root, viewer);

        assertEquals(0, root.children.size());
        verify(viewer).refresh();
    }

    @Test
    void testAreCredentialsDefinedTrue() {

        try (MockedStatic<Preferences> prefs = Mockito.mockStatic(Preferences.class)) {

            prefs.when(Preferences::getApiKey).thenReturn("apikey");

            boolean result = PluginUtils.areCredentialsDefined();

            assertTrue(result);
        }
    }

    @Test
    void testAreCredentialsDefinedFalse() {

        try (MockedStatic<Preferences> prefs = Mockito.mockStatic(Preferences.class)) {

            prefs.when(Preferences::getApiKey).thenReturn("");

            boolean result = PluginUtils.areCredentialsDefined();

            assertFalse(result);
        }
    }
}