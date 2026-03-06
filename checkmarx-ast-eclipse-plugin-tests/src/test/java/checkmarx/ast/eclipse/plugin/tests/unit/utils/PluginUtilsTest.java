package checkmarx.ast.eclipse.plugin.tests.unit.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbench;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.resources.IWorkspaceRoot;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Combo;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.filters.FilterState;
import org.eclipse.core.runtime.IStatus;

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

    @Test
    void testGetEventBroker() {
        IEventBroker mockBroker = mock(IEventBroker.class);
        IWorkbench mockWorkbench = mock(IWorkbench.class);
        try (MockedStatic<PlatformUI> platformUI = Mockito.mockStatic(PlatformUI.class)) {
            platformUI.when(PlatformUI::getWorkbench).thenReturn(mockWorkbench);
            when(mockWorkbench.getService(IEventBroker.class)).thenReturn(mockBroker);
            IEventBroker result = PluginUtils.getEventBroker();
            assertSame(mockBroker, result);
        }
    }

    @Test
    void testFindFileInWorkspace_normal() throws Exception {
        IFile mockFile = mock(IFile.class);
        IWorkspaceRoot root = mock(IWorkspaceRoot.class);
        IWorkspace workspace = mock(IWorkspace.class);
        IResourceProxyVisitor[] visitorHolder = new IResourceProxyVisitor[1];
        try (MockedStatic<ResourcesPlugin> rp = Mockito.mockStatic(ResourcesPlugin.class)) {
            rp.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);
            when(workspace.getRoot()).thenReturn(root);
            doAnswer((Answer<Void>) invocation -> {
                visitorHolder[0] = invocation.getArgument(0);
                // Simulate visit
                return null;
            }).when(root).accept(any(IResourceProxyVisitor.class), anyInt());
            List<IFile> files = PluginUtils.findFileInWorkspace("file.java");
            assertNotNull(files);
        }
    }

    @Test
    void testFindFileInWorkspace_exception() throws Exception {
        IWorkspaceRoot root = mock(IWorkspaceRoot.class);
        IWorkspace workspace = mock(IWorkspace.class);
        try (MockedStatic<ResourcesPlugin> rp = Mockito.mockStatic(ResourcesPlugin.class)) {
            rp.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);
            when(workspace.getRoot()).thenReturn(root);
            doThrow(new RuntimeException("fail")).when(root).accept(any(IResourceProxyVisitor.class), anyInt());
            List<IFile> files = PluginUtils.findFileInWorkspace("file.java");
            assertNotNull(files);
            assertTrue(files.isEmpty());
        }
    }

    @Test
    void testClearVulnerabilitiesFromProblemsView_normal() throws Exception {
        IWorkspace workspace = mock(IWorkspace.class);
        IWorkspaceRoot resource = mock(IWorkspaceRoot.class);
        IMarker marker1 = mock(IMarker.class);
        IMarker marker2 = mock(IMarker.class);
        when(workspace.getRoot()).thenReturn(resource);
        when(resource.findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE)).thenReturn(new IMarker[]{marker1, marker2});
        when(marker1.getAttribute(IMarker.SOURCE_ID)).thenReturn(PluginConstants.PROBLEM_SOURCE_ID);
        when(marker2.getAttribute(IMarker.SOURCE_ID)).thenReturn("other");
        try (MockedStatic<ResourcesPlugin> rp = Mockito.mockStatic(ResourcesPlugin.class)) {
            rp.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);
            PluginUtils.clearVulnerabilitiesFromProblemsView();
            verify(marker1).delete();
            verify(marker2, never()).delete();
        }
    }

    @Test
    void testClearVulnerabilitiesFromProblemsView_coreException() throws Exception {
        IWorkspace workspace = mock(IWorkspace.class);
        IWorkspaceRoot resource = mock(IWorkspaceRoot.class);
        IMarker marker1 = mock(IMarker.class);
        when(workspace.getRoot()).thenReturn(resource);
        when(resource.findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE)).thenReturn(new IMarker[]{marker1});
        when(marker1.getAttribute(IMarker.SOURCE_ID)).thenReturn(PluginConstants.PROBLEM_SOURCE_ID);
        IStatus status = mock(IStatus.class);
        when(status.getMessage()).thenReturn("error");
        doThrow(new CoreException(status)).when(marker1).delete();
        try (MockedStatic<ResourcesPlugin> rp = Mockito.mockStatic(ResourcesPlugin.class)) {
            rp.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);
            PluginUtils.clearVulnerabilitiesFromProblemsView();
            // Should not throw
        }
    }
}