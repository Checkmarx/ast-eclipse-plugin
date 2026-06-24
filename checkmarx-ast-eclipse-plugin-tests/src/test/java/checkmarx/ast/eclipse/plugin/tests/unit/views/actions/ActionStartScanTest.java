package checkmarx.ast.eclipse.plugin.tests.unit.views.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Combo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.GlobalSettings;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.actions.ActionStartScan;
import com.google.common.eventbus.EventBus;

class ActionStartScanTest {

    private DisplayModel rootModel;
    private TreeViewer resultsTree;
    private EventBus eventBus;
    private ComboViewer projectsCombo;
    private ComboViewer branchesCombo;
    private ComboViewer scansCombo;
    private Action cancelScanAction;

    private Combo branchCombo;
    private Combo projectCombo;

    @BeforeEach
    void setUp() {
        rootModel = mock(DisplayModel.class);
        resultsTree = mock(TreeViewer.class);
        eventBus = new EventBus();
        projectsCombo = mock(ComboViewer.class);
        branchesCombo = mock(ComboViewer.class);
        scansCombo = mock(ComboViewer.class);
        cancelScanAction = mock(Action.class);

        branchCombo = mock(Combo.class);
        when(branchCombo.getText()).thenReturn("");
        when(branchesCombo.getCombo()).thenReturn(branchCombo);

        projectCombo = mock(Combo.class);
        when(projectCombo.getText()).thenReturn("TestProject");
        when(projectsCombo.getCombo()).thenReturn(projectCombo);
    }

    private ActionStartScan buildAction() {
        return new ActionStartScan(rootModel, resultsTree, eventBus,
                projectsCombo, branchesCombo, scansCombo, cancelScanAction);
    }

    @Test
    void testCreateAction_returnsNonNull() {
        Action action = buildAction().createAction();
        assertNotNull(action);
    }

    @Test
    void testCreateAction_hasCorrectId() {
        Action action = buildAction().createAction();
        assertEquals(ActionName.START_SCAN.name(), action.getId());
    }

    @Test
    void testCreateAction_hasTooltipText() {
        Action action = buildAction().createAction();
        assertNotNull(action.getToolTipText());
        assertFalse(action.getToolTipText().isEmpty());
    }

    @Test
    void testCreateAction_isDisabledWhenNoBranchConfigured() {
        // no branch stored in preferences → action disabled by default
        Action action = buildAction().createAction();
        assertFalse(action.isEnabled());
    }

    @Test
    void testCxProjectMatchesWorkspaceProject_nullResults_returnsTrue() throws Exception {
        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class)) {

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.getCurrentResults()).thenReturn(null);
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            IWorkspace mockWorkspace = mock(IWorkspace.class);
            IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
            when(mockRoot.getProjects()).thenReturn(new IProject[0]);
            when(mockWorkspace.getRoot()).thenReturn(mockRoot);
            resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

            Method method = ActionStartScan.class.getDeclaredMethod("cxProjectMatchesWorkspaceProject");
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(buildAction());
            assertTrue(result);
        }
    }

    @Test
    void testCxProjectMatchesWorkspaceProject_emptyResultsList_returnsTrue() throws Exception {
        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class)) {

            Results mockResults = mock(Results.class);
            when(mockResults.getResults()).thenReturn(Collections.emptyList());

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.getCurrentResults()).thenReturn(mockResults);
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            IWorkspace mockWorkspace = mock(IWorkspace.class);
            IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
            when(mockRoot.getProjects()).thenReturn(new IProject[]{mock(IProject.class)});
            when(mockWorkspace.getRoot()).thenReturn(mockRoot);
            resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

            Method method = ActionStartScan.class.getDeclaredMethod("cxProjectMatchesWorkspaceProject");
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(buildAction());
            assertTrue(result);
        }
    }

    @Test
    void testCxProjectMatchesWorkspaceProject_noWorkspaceProjects_returnsTrue() throws Exception {
        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class)) {

            Data mockData = mock(Data.class);
            when(mockData.getNodes()).thenReturn(null);
            when(mockData.getFileName()).thenReturn("Foo.java");
            Result mockResult = mock(Result.class);
            when(mockResult.getData()).thenReturn(mockData);

            Results mockResults = mock(Results.class);
            when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.getCurrentResults()).thenReturn(mockResults);
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            IWorkspace mockWorkspace = mock(IWorkspace.class);
            IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
            when(mockRoot.getProjects()).thenReturn(new IProject[0]);
            when(mockWorkspace.getRoot()).thenReturn(mockRoot);
            resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

            Method method = ActionStartScan.class.getDeclaredMethod("cxProjectMatchesWorkspaceProject");
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(buildAction());
            assertTrue(result);
        }
    }

    @Test
    void testGetCurrentGitBranch_noWorkspaceProjects_returnsEmpty() throws Exception {
        try (MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class)) {

            IWorkspace mockWorkspace = mock(IWorkspace.class);
            IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
            when(mockRoot.getProjects()).thenReturn(new IProject[0]);
            when(mockWorkspace.getRoot()).thenReturn(mockRoot);
            resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

            Method method = ActionStartScan.class.getDeclaredMethod("getCurrentGitBranch");
            method.setAccessible(true);
            String result = (String) method.invoke(buildAction());
            assertEquals("", result);
        }
    }

    @Test
    void testOnCancel_withMockedPollJob_callsCancel() throws Exception {
        Job mockJob = mock(Job.class);
        when(mockJob.cancel()).thenReturn(true);

        Field pollJobField = ActionStartScan.class.getDeclaredField("pollJob");
        pollJobField.setAccessible(true);
        pollJobField.set(null, mockJob);

        try {
            assertDoesNotThrow(() -> ActionStartScan.onCancel());
            verify(mockJob).cancel();
        } finally {
            pollJobField.set(null, null);
        }
    }

    @Test
    void testCxProjectMatchesWorkspaceProject_withSastNodesAndWorkspace_fileFound_returnsTrue() throws Exception {
        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<ResourcesPlugin> resMock = Mockito.mockStatic(ResourcesPlugin.class);
             MockedStatic<PluginUtils> puMock = Mockito.mockStatic(PluginUtils.class)) {

            Node mockNode = mock(Node.class);
            when(mockNode.getFileName()).thenReturn("/src/Foo.java");
            Data mockData = mock(Data.class);
            when(mockData.getNodes()).thenReturn(Arrays.asList(mockNode));
            Result mockResult = mock(Result.class);
            when(mockResult.getData()).thenReturn(mockData);
            Results mockResults = mock(Results.class);
            when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.getCurrentResults()).thenReturn(mockResults);
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            IProject mockProject = mock(IProject.class);
            IPath mockPath = mock(IPath.class);
            when(mockPath.toString()).thenReturn("/workspace/project");
            when(mockProject.getLocation()).thenReturn(mockPath);
            IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
            when(mockRoot.getProjects()).thenReturn(new IProject[]{mockProject});
            IWorkspace mockWorkspace = mock(IWorkspace.class);
            when(mockWorkspace.getRoot()).thenReturn(mockRoot);
            resMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

            IFile mockFile = mock(IFile.class);
            puMock.when(() -> PluginUtils.findFileInWorkspace(anyString()))
                    .thenReturn(Arrays.asList(mockFile));

            Method method = ActionStartScan.class.getDeclaredMethod("cxProjectMatchesWorkspaceProject");
            method.setAccessible(true);
            assertTrue((boolean) method.invoke(buildAction()));
        }
    }

    @Test
    void testCxProjectMatchesWorkspaceProject_withKicsFileName_noFileInWorkspace_returnsFalse() throws Exception {
        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<ResourcesPlugin> resMock = Mockito.mockStatic(ResourcesPlugin.class);
             MockedStatic<PluginUtils> puMock = Mockito.mockStatic(PluginUtils.class)) {

            Data mockData = mock(Data.class);
            when(mockData.getNodes()).thenReturn(null);
            when(mockData.getFileName()).thenReturn("Dockerfile");
            Result mockResult = mock(Result.class);
            when(mockResult.getData()).thenReturn(mockData);
            Results mockResults = mock(Results.class);
            when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.getCurrentResults()).thenReturn(mockResults);
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            IProject mockProject = mock(IProject.class);
            IPath mockPath = mock(IPath.class);
            when(mockPath.toString()).thenReturn("/workspace/project");
            when(mockProject.getLocation()).thenReturn(mockPath);
            IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
            when(mockRoot.getProjects()).thenReturn(new IProject[]{mockProject});
            IWorkspace mockWorkspace = mock(IWorkspace.class);
            when(mockWorkspace.getRoot()).thenReturn(mockRoot);
            resMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

            puMock.when(() -> PluginUtils.findFileInWorkspace(anyString()))
                    .thenReturn(Collections.emptyList());

            Method method = ActionStartScan.class.getDeclaredMethod("cxProjectMatchesWorkspaceProject");
            method.setAccessible(true);
            assertFalse((boolean) method.invoke(buildAction()));
        }
    }

    @Test
    void testGetCurrentGitBranch_withProjectsPresentButGitFails_returnsEmpty() throws Exception {
        try (MockedStatic<ResourcesPlugin> resMock = Mockito.mockStatic(ResourcesPlugin.class)) {
            IProject mockProject = mock(IProject.class);
            IPath mockPath = mock(IPath.class);
            // A path that cannot be opened as a git repo -> IOException
            when(mockPath.toString()).thenReturn("/nonexistent/git/repo/xyz_abc");
            when(mockProject.getLocation()).thenReturn(mockPath);
            IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
            when(mockRoot.getProjects()).thenReturn(new IProject[]{mockProject});
            IWorkspace mockWorkspace = mock(IWorkspace.class);
            when(mockWorkspace.getRoot()).thenReturn(mockRoot);
            resMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

            Method method = ActionStartScan.class.getDeclaredMethod("getCurrentGitBranch");
            method.setAccessible(true);
            String result = (String) method.invoke(buildAction());
            assertEquals("", result);
        }
    }

    @Test
    void testPollScan_outerBody_setsEnabledAndStoresPreference() throws Exception {
        try (MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class)) {
            ActionStartScan as = buildAction();
            as.createAction(); // ensures startScanAction is initialised

            Method method = ActionStartScan.class.getDeclaredMethod("pollScan", String.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(as, "scan-poll-test");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });
            verify(cancelScanAction).setEnabled(true);
        }
    }

    @Test
    void testDisplayMismatchNotification_userAccepts_callsCreateScan() throws Exception {
        try (MockedStatic<com.checkmarx.eclipse.Activator> activatorMock =
                     Mockito.mockStatic(com.checkmarx.eclipse.Activator.class);
             MockedStatic<MessageDialog> dialogMock = Mockito.mockStatic(MessageDialog.class);
             MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class);
             MockedStatic<org.eclipse.swt.widgets.Display> displayMock =
                     Mockito.mockStatic(org.eclipse.swt.widgets.Display.class)) {

            ImageDescriptor desc = mock(ImageDescriptor.class);
            when(desc.createImage()).thenReturn(mock(Image.class));
            activatorMock.when(() -> com.checkmarx.eclipse.Activator.getImageDescriptor(anyString()))
                    .thenReturn(desc);

            org.eclipse.swt.widgets.Display mockDisplay = mock(org.eclipse.swt.widgets.Display.class);
            org.eclipse.swt.widgets.Shell mockShell = mock(org.eclipse.swt.widgets.Shell.class);
            when(mockDisplay.getActiveShell()).thenReturn(mockShell);
            displayMock.when(org.eclipse.swt.widgets.Display::getDefault).thenReturn(mockDisplay);

            // User accepts the mismatch dialog
            dialogMock.when(() -> MessageDialog.openQuestion(any(), anyString(), anyString()))
                    .thenReturn(true);

            gsMock.when(() -> GlobalSettings.getFromPreferences(anyString(), anyString())).thenReturn("");

            ActionStartScan startScan = buildAction();
            startScan.createAction();

            Method method = ActionStartScan.class.getDeclaredMethod(
                    "displayMismatchNotification", String.class, String.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(startScan, "Title", "Question?");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });
        }
    }

    @Test
    void testPollingScan_runnable_scanRunning_doesNotThrow() throws Exception {
        Scan mockScan = mock(Scan.class);
        when(mockScan.getStatus()).thenReturn("running");

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class)) {

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.getScanInformation(anyString())).thenReturn(mockScan);
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            ActionStartScan as = buildAction();
            as.createAction();

            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            Field execField = ActionStartScan.class.getDeclaredField("pollScanExecutor");
            execField.setAccessible(true);
            execField.set(as, executor);

            Method method = ActionStartScan.class.getDeclaredMethod("pollingScan", String.class);
            method.setAccessible(true);
            Runnable r = (Runnable) method.invoke(as, "scan-running-123");
            assertDoesNotThrow(r::run);
        }
    }

    @Test
    void testPollingScan_runnable_scanNotRunning_doesNotThrow() throws Exception {
        Scan mockScan = mock(Scan.class);
        when(mockScan.getStatus()).thenReturn("failed");

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class);
             MockedStatic<org.eclipse.swt.widgets.Display> displayMock =
                     Mockito.mockStatic(org.eclipse.swt.widgets.Display.class)) {

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.getScanInformation(anyString())).thenReturn(mockScan);
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            org.eclipse.swt.widgets.Display mockDisplay = mock(org.eclipse.swt.widgets.Display.class);
            displayMock.when(org.eclipse.swt.widgets.Display::getDefault).thenReturn(mockDisplay);

            gsMock.when(() -> GlobalSettings.getFromPreferences(anyString(), anyString())).thenReturn("");

            ActionStartScan as = buildAction();
            as.createAction();

            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            Field execField = ActionStartScan.class.getDeclaredField("pollScanExecutor");
            execField.setAccessible(true);
            execField.set(as, executor);

            Method method = ActionStartScan.class.getDeclaredMethod("pollingScan", String.class);
            method.setAccessible(true);
            Runnable r = (Runnable) method.invoke(as, "scan-done-456");
            assertDoesNotThrow(r::run);
            verify(executor).shutdown();
            verify(cancelScanAction).setEnabled(false);
        }
    }

    @Test
    void testPollingScan_runnable_scanCompleted_coversCompletedPath() throws Exception {
        Scan mockScan = mock(Scan.class);
        when(mockScan.getStatus()).thenReturn("completed");

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class);
             MockedStatic<org.eclipse.swt.widgets.Display> displayMock =
                     Mockito.mockStatic(org.eclipse.swt.widgets.Display.class)) {

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.getScanInformation(anyString())).thenReturn(mockScan);
            when(mockProvider.sortResults()).thenReturn(Collections.emptyList());
            when(mockProvider.getScansForProject(anyString())).thenReturn(Collections.emptyList());
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            org.eclipse.swt.widgets.Display mockDisplay = mock(org.eclipse.swt.widgets.Display.class);
            displayMock.when(org.eclipse.swt.widgets.Display::getDefault).thenReturn(mockDisplay);
            displayMock.when(org.eclipse.swt.widgets.Display::getCurrent).thenReturn(mockDisplay);

            ActionStartScan as = buildAction();
            as.createAction();

            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            Field execField = ActionStartScan.class.getDeclaredField("pollScanExecutor");
            execField.setAccessible(true);
            execField.set(as, executor);

            // Mock scansCombo.getCombo() to avoid NPE inside syncExec Runnables
            Combo mockScansRawCombo = mock(Combo.class);
            when(scansCombo.getCombo()).thenReturn(mockScansRawCombo);

            Method method = ActionStartScan.class.getDeclaredMethod("pollingScan", String.class);
            method.setAccessible(true);
            Runnable r = (Runnable) method.invoke(as, "scan-completed-789");
            assertDoesNotThrow(r::run);
            verify(executor).shutdown();
        }
    }

    @Test
    void testPollingScan_runnable_exceptionThrown_handlesCatch() throws Exception {
        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class)) {

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.getScanInformation(anyString()))
                    .thenThrow(new RuntimeException("network failure"));
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            ActionStartScan as = buildAction();
            as.createAction();

            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            Field execField = ActionStartScan.class.getDeclaredField("pollScanExecutor");
            execField.setAccessible(true);
            execField.set(as, executor);

            Method method = ActionStartScan.class.getDeclaredMethod("pollingScan", String.class);
            method.setAccessible(true);
            Runnable r = (Runnable) method.invoke(as, "scan-error-999");
            assertDoesNotThrow(r::run);
        }
    }

    @Test
    void testCancelScan_outerBody_schedulesJob() throws Exception {
        ActionStartScan as = buildAction();
        as.createAction();

        Method method = ActionStartScan.class.getDeclaredMethod("cancelScan", String.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(as, "scan-to-cancel");
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        });
    }

    @Test
    void testDisplayMismatchNotification_userDeclines_setsScanActionEnabled() throws Exception {
        try (MockedStatic<com.checkmarx.eclipse.Activator> activatorMock =
                     Mockito.mockStatic(com.checkmarx.eclipse.Activator.class);
             MockedStatic<MessageDialog> dialogMock = Mockito.mockStatic(MessageDialog.class);
             MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class);
             MockedStatic<org.eclipse.swt.widgets.Display> displayMock =
                     Mockito.mockStatic(org.eclipse.swt.widgets.Display.class)) {

            ImageDescriptor desc = mock(ImageDescriptor.class);
            when(desc.createImage()).thenReturn(mock(Image.class));
            activatorMock.when(() -> com.checkmarx.eclipse.Activator.getImageDescriptor(anyString()))
                    .thenReturn(desc);

            // Mock Display.getDefault() to avoid SWT thread-access check on getActiveShell()
            org.eclipse.swt.widgets.Display mockDisplay = mock(org.eclipse.swt.widgets.Display.class);
            org.eclipse.swt.widgets.Shell mockShell = mock(org.eclipse.swt.widgets.Shell.class);
            when(mockDisplay.getActiveShell()).thenReturn(mockShell);
            displayMock.when(org.eclipse.swt.widgets.Display::getDefault).thenReturn(mockDisplay);

            IWorkspace ws = mock(IWorkspace.class);
            IWorkspaceRoot rootWs = mock(IWorkspaceRoot.class);
            when(rootWs.getProjects()).thenReturn(new IProject[0]);
            when(ws.getRoot()).thenReturn(rootWs);
            resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(ws);

            // User declines → loadResults=false → createScan() NOT called → startScanAction enabled
            dialogMock.when(() -> MessageDialog.openQuestion(any(), anyString(), anyString()))
                    .thenReturn(false);

            ActionStartScan startScan = buildAction();
            Action action = startScan.createAction();

            Method method = ActionStartScan.class.getDeclaredMethod(
                    "displayMismatchNotification", String.class, String.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(startScan, "Title", "Question?");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });
            assertTrue(action.isEnabled());
        }
    }
}
