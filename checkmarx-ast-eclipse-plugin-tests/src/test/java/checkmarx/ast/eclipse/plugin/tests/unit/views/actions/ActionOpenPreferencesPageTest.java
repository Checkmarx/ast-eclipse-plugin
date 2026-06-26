package checkmarx.ast.eclipse.plugin.tests.unit.views.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.actions.ActionOpenPreferencesPage;

class ActionOpenPreferencesPageTest {

    private static Display display;

    @BeforeAll
    static void setUpClass() {
        display = Display.getDefault();
    }

    @Test
    void testCreateAction_returnsNonNullAction() {
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                DisplayModel rootModel = new DisplayModel.DisplayModelBuilder(PluginConstants.EMPTY_STRING).build();
                TreeViewer mockTree = mock(TreeViewer.class);

                ActionOpenPreferencesPage action = new ActionOpenPreferencesPage(rootModel, mockTree, shell);
                Action result = action.createAction();

                assertNotNull(result);
            } finally {
                shell.dispose();
            }
        });
    }

    @Test
    void testCreateAction_hasCorrectId() {
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                DisplayModel rootModel = new DisplayModel.DisplayModelBuilder(PluginConstants.EMPTY_STRING).build();
                TreeViewer mockTree = mock(TreeViewer.class);

                ActionOpenPreferencesPage action = new ActionOpenPreferencesPage(rootModel, mockTree, shell);
                Action result = action.createAction();

                assertEquals(ActionName.PREFERENCES.name(), result.getId());
            } finally {
                shell.dispose();
            }
        });
    }

    @Test
    void testCreateAction_hasNonEmptyText() {
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                DisplayModel rootModel = new DisplayModel.DisplayModelBuilder(PluginConstants.EMPTY_STRING).build();
                TreeViewer mockTree = mock(TreeViewer.class);

                ActionOpenPreferencesPage action = new ActionOpenPreferencesPage(rootModel, mockTree, shell);
                Action result = action.createAction();

                assertNotNull(result.getText());
                assertFalse(result.getText().isEmpty());
            } finally {
                shell.dispose();
            }
        });
    }

    @Test
    void testCreateAction_runMethod_prefDialogNull_doesNotThrow() {
        // MockedStatic is thread-local — run action on test thread (not syncExec) so the mock is visible
        try (MockedStatic<PreferencesUtil> prefUtilMock = Mockito.mockStatic(PreferencesUtil.class)) {
            prefUtilMock.when(() -> PreferencesUtil.createPreferenceDialogOn(any(), anyString(), any(), any()))
                    .thenReturn(null);

            DisplayModel rootModel = new DisplayModel.DisplayModelBuilder(PluginConstants.EMPTY_STRING).build();
            TreeViewer mockTree = mock(TreeViewer.class);
            ActionOpenPreferencesPage actionPage = new ActionOpenPreferencesPage(rootModel, mockTree, null);
            Action result = actionPage.createAction();
            assertDoesNotThrow(result::run);
        }
    }

    @Test
    void testCreateAction_runMethod_prefDialogNonNull_callsOpen() {
        // MockedStatic is thread-local — run action on test thread (not syncExec) so the mock is visible
        try (MockedStatic<PreferencesUtil> prefUtilMock = Mockito.mockStatic(PreferencesUtil.class)) {
            PreferenceDialog mockDialog = mock(PreferenceDialog.class);
            when(mockDialog.open()).thenReturn(0);
            prefUtilMock.when(() -> PreferencesUtil.createPreferenceDialogOn(any(), anyString(), any(), any()))
                    .thenReturn(mockDialog);

            DisplayModel rootModel = new DisplayModel.DisplayModelBuilder(PluginConstants.EMPTY_STRING).build();
            TreeViewer mockTree = mock(TreeViewer.class);
            ActionOpenPreferencesPage actionPage = new ActionOpenPreferencesPage(rootModel, mockTree, null);
            Action result = actionPage.createAction();
            assertDoesNotThrow(result::run);
            verify(mockDialog).open();
        }
    }
}
