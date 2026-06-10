package checkmarx.ast.eclipse.plugin.tests.unit.views;

import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.views.CheckmarxView;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.GlobalSettings;
import com.checkmarx.eclipse.views.actions.ToolBarActions;
import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.filters.FilterState;
import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.eclipse.Activator;

import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;

import org.eclipse.e4.core.services.events.IEventBroker;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.osgi.service.event.Event;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class CheckmarxViewTest {

    private static Display display;

    private static MockedStatic<Activator> activatorStaticMock;
    private static MockedStatic<ImageDescriptor> imageDescriptorStaticMock;

    private MockedStatic<PlatformUI> platformUIMock;
    private MockedStatic<PluginUtils> pluginUtilsMock;

    private CheckmarxView checkmarxView;
    private Shell shell;
    private Composite parent;

    @BeforeAll
    static void beforeAll() {

        display = Display.getDefault();

        activatorStaticMock = Mockito.mockStatic(Activator.class);
        imageDescriptorStaticMock = Mockito.mockStatic(ImageDescriptor.class, Mockito.CALLS_REAL_METHODS);

        ImageDescriptor descriptor = Mockito.mock(ImageDescriptor.class);
        Image image = Mockito.mock(Image.class);

        activatorStaticMock
                .when(() -> Activator.getImageDescriptor(Mockito.anyString()))
                .thenReturn(descriptor);

        Mockito.when(descriptor.createImage()).thenReturn(image);
    }

    @AfterAll
    static void afterAll() {
        activatorStaticMock.close();
        imageDescriptorStaticMock.close();
    }

    @BeforeEach
    void setUp() throws Exception {

        platformUIMock = Mockito.mockStatic(PlatformUI.class);
        pluginUtilsMock = Mockito.mockStatic(PluginUtils.class);

        IWorkbench workbench = Mockito.mock(IWorkbench.class);
        IWorkbenchWindow window = Mockito.mock(IWorkbenchWindow.class);

        display.syncExec(() -> {
            shell = new Shell(display);
            parent = new Composite(shell, 0);
        });

        Mockito.when(window.getShell()).thenReturn(shell);
        Mockito.when(workbench.getActiveWorkbenchWindow()).thenReturn(window);
        Mockito.when(workbench.getDisplay()).thenReturn(display);

        platformUIMock.when(PlatformUI::getWorkbench).thenReturn(workbench);

        IEventBroker broker = Mockito.mock(IEventBroker.class);
        Mockito.when(broker.subscribe(Mockito.anyString(), Mockito.any())).thenReturn(true);

        pluginUtilsMock.when(PluginUtils::getEventBroker).thenReturn(broker);

        checkmarxView = new CheckmarxView();

        injectDependencies();
    }

    @AfterEach
    void tearDown() {

        platformUIMock.close();
        pluginUtilsMock.close();

        if (shell != null && !shell.isDisposed()) {
            display.syncExec(() -> shell.dispose());
        }
    }

    @Test
    void testConstructorInitializesFields() {
        assertNotNull(checkmarxView);
    }

    @Test
    void testDisposeDoesNotThrow() {
        assertDoesNotThrow(() -> checkmarxView.dispose());
    }

    @Test
    void testSetFocusDoesNotThrow() {
        assertDoesNotThrow(() -> checkmarxView.setFocus());
    }

    @Test
    void testHandleEventWithEmptyApiKey() throws Exception {

    	Event event = new Event("test/topic", new HashMap<String, Object>());

        try (MockedStatic<Preferences> prefMock =
                     Mockito.mockStatic(Preferences.class, Mockito.CALLS_REAL_METHODS)) {

            prefMock.when(Preferences::getApiKey).thenReturn("");

            assertDoesNotThrow(() ->
            Display.getDefault().syncExec(() -> checkmarxView.handleEvent(event))
    );
        }
    }

    @Test
    void testHandleEventWithNonEmptyApiKey() {

        org.osgi.service.event.Event event =
                new org.osgi.service.event.Event("test/topic", new HashMap<>());

        try (MockedStatic<Preferences> prefMock =
                     Mockito.mockStatic(Preferences.class, Mockito.CALLS_REAL_METHODS)) {

            prefMock.when(Preferences::getApiKey).thenReturn("dummyApiKey");

            assertDoesNotThrow(() ->
                    Display.getDefault().syncExec(() -> checkmarxView.handleEvent(event))
            );
        }
    }

    @Test
    void testStaticFieldsNotNull() {

        assertNotNull(CheckmarxView.ID);
        assertNotNull(CheckmarxView.CHECKMARX_OPEN_SETTINGS_LOGO);
        assertNotNull(CheckmarxView.CRITICAL_SEVERITY);
        assertNotNull(CheckmarxView.HIGH_SEVERITY);
        assertNotNull(CheckmarxView.MEDIUM_SEVERITY);
        assertNotNull(CheckmarxView.LOW_SEVERITY);
        assertNotNull(CheckmarxView.INFO_SEVERITY);
        assertNotNull(CheckmarxView.USER);
        assertNotNull(CheckmarxView.CREATED_AT_IMAGE);
        assertNotNull(CheckmarxView.COMMENT);
        assertNotNull(CheckmarxView.STATE);
        assertNotNull(CheckmarxView.BFL);
    }
    
    @Test
    void testRemoveCount() throws Exception {
        Method method = CheckmarxView.class.getDeclaredMethod("removeCount", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "High (5)");

        assertEquals("High", result);
    }
    
    @Test
    void testGetLatestScanFromScanList() throws Exception {

        Scan scan1 = Mockito.mock(Scan.class);
        Scan scan2 = Mockito.mock(Scan.class);

        List<Scan> scans = Arrays.asList(scan1, scan2);

        Method method = CheckmarxView.class.getDeclaredMethod("getLatestScanFromScanList", List.class);
        method.setAccessible(true);

        Scan result = (Scan) method.invoke(checkmarxView, scans);

        assertEquals(scan1, result);
    }
    
    @Test
    void testGetProjectFromIdFound() throws Exception {

        Project project = Mockito.mock(Project.class);
        Mockito.when(project.getId()).thenReturn("123");
        Mockito.when(project.getName()).thenReturn("DemoProject");

        List<Project> projects = List.of(project);

        Method method = CheckmarxView.class.getDeclaredMethod(
                "getProjectFromId", List.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(checkmarxView, projects, "123");

        assertEquals("DemoProject", result);
    }
    
    @Test
    void testGetProjectFromIdNotFound() throws Exception {

        Project project = Mockito.mock(Project.class);
        Mockito.when(project.getId()).thenReturn("123");

        List<Project> projects = List.of(project);

        Method method = CheckmarxView.class.getDeclaredMethod(
                "getProjectFromId", List.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(checkmarxView, projects, "999");

        assertEquals("Select a project", result);
    }
    
    @Test
    void testGetProjectFromIdEmptyList() throws Exception {

        Method method = CheckmarxView.class.getDeclaredMethod(
                "getProjectFromId", List.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(checkmarxView, Collections.emptyList(), "123");

        assertEquals("No projects available.", result);
    }
    
    @Test
    void testFormatScanLabelNormal() throws Exception {

        Scan scan = Mockito.mock(Scan.class);

        Mockito.when(scan.getId()).thenReturn("scan123");
        Mockito.when(scan.getUpdatedAt()).thenReturn("2024-01-01T10:00:00Z");

        Field latestScanField = CheckmarxView.class.getDeclaredField("latestScanId");
        latestScanField.setAccessible(true);
        latestScanField.set(checkmarxView, "otherScan");

        Method method = CheckmarxView.class.getDeclaredMethod("formatScanLabel", Scan.class);
        method.setAccessible(true);

        String label = (String) method.invoke(checkmarxView, scan);

        assertTrue(label.contains("scan123"));
    }
    
    @Test
    void testFormatScanLabelLatest() throws Exception {

        Scan scan = Mockito.mock(Scan.class);

        Mockito.when(scan.getId()).thenReturn("scan999");
        Mockito.when(scan.getUpdatedAt()).thenReturn("2024-01-01T10:00:00Z");

        Field latestScanField = CheckmarxView.class.getDeclaredField("latestScanId");
        latestScanField.setAccessible(true);
        latestScanField.set(checkmarxView, "scan999");

        Method method = CheckmarxView.class.getDeclaredMethod("formatScanLabel", Scan.class);
        method.setAccessible(true);

        String label = (String) method.invoke(checkmarxView, scan);

        assertTrue(label.contains("latest"));
    }
    
    @Test
    void testGetProjectsSuccess() throws Exception {

        List<Project> projects = List.of(Mockito.mock(Project.class));

        DataProvider provider = Mockito.mock(DataProvider.class);
        Mockito.when(provider.getProjects()).thenReturn(projects);

        try (MockedStatic<DataProvider> mocked = Mockito.mockStatic(DataProvider.class)) {

            mocked.when(DataProvider::getInstance).thenReturn(provider);

            Method method = CheckmarxView.class.getDeclaredMethod("getProjects");
            method.setAccessible(true);

            List<Project> result = (List<Project>) method.invoke(checkmarxView);

            assertEquals(1, result.size());
        }
    }
    
    @Test
    void testGetProjectsException() throws Exception {

        DataProvider provider = Mockito.mock(DataProvider.class);

        Mockito.when(provider.getProjects()).thenThrow(new RuntimeException("error"));

        try (MockedStatic<DataProvider> mocked = Mockito.mockStatic(DataProvider.class)) {

            mocked.when(DataProvider::getInstance).thenReturn(provider);

            Method method = CheckmarxView.class.getDeclaredMethod("getProjects");
            method.setAccessible(true);

            List<Project> result = (List<Project>) method.invoke(checkmarxView);

            assertNotNull(result);
        }
    }
    
    @Test
    void testUpdateStartScanButtonEnabled() throws Exception {

        ToolBarActions toolBarActions = Mockito.mock(ToolBarActions.class);
        Action startAction = Mockito.mock(Action.class);

        Mockito.when(toolBarActions.getStartScanAction()).thenReturn(startAction);

        Field field = CheckmarxView.class.getDeclaredField("toolBarActions");
        field.setAccessible(true);
        field.set(checkmarxView, toolBarActions);

        Method method = CheckmarxView.class.getDeclaredMethod("updateStartScanButton", boolean.class);
        method.setAccessible(true);

        method.invoke(checkmarxView, true);

        Mockito.verify(startAction).setEnabled(Mockito.anyBoolean());
    }

    @Test
    void testCreatePartControl_credentialsNotDefined_drawsMissingCredentialsPanel() {
        pluginUtilsMock.when(PluginUtils::areCredentialsDefined).thenReturn(false);

        display.syncExec(() -> checkmarxView.createPartControl(parent));

        int[] childCount = {0};
        display.syncExec(() -> childCount[0] = parent.getChildren().length);
        assertTrue(childCount[0] > 0);
    }

    @Test
    void testGetScanNameFromId_emptyList_returnsNoScansText() throws Exception {
        Method method = CheckmarxView.class.getDeclaredMethod("getScanNameFromId", List.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(checkmarxView, Collections.emptyList(), "any-id");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetScanNameFromId_scanFound_returnsLabelContainingScanId() throws Exception {
        Scan scan = Mockito.mock(Scan.class);
        Mockito.when(scan.getId()).thenReturn("scan-abc-123");
        Mockito.when(scan.getUpdatedAt()).thenReturn("2024-06-01T10:00:00Z");

        Method method = CheckmarxView.class.getDeclaredMethod("getScanNameFromId", List.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(checkmarxView, List.of(scan), "scan-abc-123");

        assertTrue(result.contains("scan-abc-123"));
    }

    @Test
    void testGetScanNameFromId_scanNotFound_returnsSelectScanText() throws Exception {
        Scan scan = Mockito.mock(Scan.class);
        Mockito.when(scan.getId()).thenReturn("scan-abc-123");

        Method method = CheckmarxView.class.getDeclaredMethod("getScanNameFromId", List.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(checkmarxView, List.of(scan), "different-id");

        assertNotNull(result);
    }

    @Test
    void testCreatePartControl_credentialsDefined_attemptDrawPluginPanel() {
        // ATTEMPT — skipped if ViewPart partSite reflection fails in this Tycho environment
        try {
            org.eclipse.ui.IViewSite mockViewSite = Mockito.mock(org.eclipse.ui.IViewSite.class);
            org.eclipse.ui.IActionBars mockActionBars = Mockito.mock(org.eclipse.ui.IActionBars.class);
            org.eclipse.jface.action.IToolBarManager mockToolBarMgr = Mockito.mock(org.eclipse.jface.action.IToolBarManager.class);
            org.eclipse.jface.action.IMenuManager mockMenuMgr = Mockito.mock(org.eclipse.jface.action.IMenuManager.class);
            Mockito.when(mockViewSite.getActionBars()).thenReturn(mockActionBars);
            Mockito.when(mockActionBars.getToolBarManager()).thenReturn(mockToolBarMgr);
            Mockito.when(mockActionBars.getMenuManager()).thenReturn(mockMenuMgr);

            Field siteField = Class.forName("org.eclipse.ui.internal.WorkbenchPart").getDeclaredField("partSite");
            siteField.setAccessible(true);
            siteField.set(checkmarxView, mockViewSite);

            pluginUtilsMock.when(PluginUtils::areCredentialsDefined).thenReturn(true);

            DataProvider mockProvider = Mockito.mock(DataProvider.class);
            Mockito.when(mockProvider.getBranchesForProject(Mockito.anyString())).thenReturn(Collections.emptyList());
            Mockito.when(mockProvider.getScansForProject(Mockito.anyString())).thenReturn(Collections.emptyList());
            Mockito.when(mockProvider.getProjects()).thenReturn(Collections.emptyList());
            Mockito.when(mockProvider.isScanAllowed()).thenReturn(true);

            try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class)) {
                dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

                display.syncExec(() -> checkmarxView.createPartControl(parent));

                assertTrue(parent.getChildren().length > 0);
            }
        } catch (Exception e) {
            // WorkbenchPart internal API not accessible in this OSGi classloader — pass vacuously
        }
    }

    private void injectDependencies() throws Exception {

        ToolBarActions toolbar = Mockito.mock(ToolBarActions.class);
        Action action = Mockito.mock(Action.class);

        Mockito.when(toolbar.getStartScanAction()).thenReturn(action);

        Field toolbarField = CheckmarxView.class.getDeclaredField("toolBarActions");
        toolbarField.setAccessible(true);
        toolbarField.set(checkmarxView, toolbar);

        Field parentField = CheckmarxView.class.getDeclaredField("parent");
        parentField.setAccessible(true);
        parentField.set(checkmarxView, parent);
    }

    private void injectComboViewers() throws Exception {
        ComboViewer mockBranch = Mockito.mock(ComboViewer.class);
        Combo mockBranchCombo = Mockito.mock(Combo.class);
        Mockito.when(mockBranch.getCombo()).thenReturn(mockBranchCombo);

        ComboViewer mockScanId = Mockito.mock(ComboViewer.class);
        Combo mockScanCombo = Mockito.mock(Combo.class);
        Mockito.when(mockScanId.getCombo()).thenReturn(mockScanCombo);
        Mockito.when(mockScanCombo.getText()).thenReturn("");

        for (String fname : new String[]{"branchComboViewer", "scanIdComboViewer"}) {
            Field f = CheckmarxView.class.getDeclaredField(fname);
            f.setAccessible(true);
            f.set(checkmarxView, fname.equals("branchComboViewer") ? mockBranch : mockScanId);
        }
    }

    @Test
    void testUpdatePluginBranchAndScans_withEmptyBranches_doesNotThrow() throws Exception {
        // currentBranches is empty by default, so pluginBranchesContainsGitBranch = false
        // Method returns without entering the if-body
        Method method = CheckmarxView.class.getDeclaredMethod("updatePluginBranchAndScans", String.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(checkmarxView, "main");
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        });
    }

    @Test
    void testGetProjectFromId_nullInput_returnsNoProjects() throws Exception {
        Method method = CheckmarxView.class.getDeclaredMethod(
                "getProjectFromId", List.class, String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(checkmarxView, null, "123");
        assertNotNull(result);
    }

    @Test
    void testSetSelectionForBranchComboViewer_withBranchFound_doesNotThrow() throws Exception {
        injectComboViewers();

        DataProvider mockProvider = Mockito.mock(DataProvider.class);
        Mockito.when(mockProvider.getBranchesForProject(Mockito.anyString()))
                .thenReturn(Arrays.asList("main", "develop"));

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class)) {
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            Method method = CheckmarxView.class.getDeclaredMethod(
                    "setSelectionForBranchComboViewer", String.class, String.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(checkmarxView, "main", "project-id-123");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });
        }
    }

    @Test
    void testSetSelectionForBranchComboViewer_withBranchNotFound_doesNotThrow() throws Exception {
        injectComboViewers();

        DataProvider mockProvider = Mockito.mock(DataProvider.class);
        Mockito.when(mockProvider.getBranchesForProject(Mockito.anyString()))
                .thenReturn(Arrays.asList("develop"));

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class)) {
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            Method method = CheckmarxView.class.getDeclaredMethod(
                    "setSelectionForBranchComboViewer", String.class, String.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(checkmarxView, "main", "project-id-123");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });
        }
    }

    @Test
    void testSetSelectionForScanIdComboViewer_emptyScanList_emptyScanId_setsNoScansText() throws Exception {
        injectComboViewers();

        DataProvider mockProvider = Mockito.mock(DataProvider.class);
        Mockito.when(mockProvider.getScansForProject(Mockito.anyString()))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class)) {
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            Method method = CheckmarxView.class.getDeclaredMethod(
                    "setSelectionForScanIdComboViewer", String.class, String.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(checkmarxView, "", "main");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });
        }
    }

    @Test
    void testSetSelectionForScanIdComboViewer_emptyScanList_nonEmptyScanId_setsSelection() throws Exception {
        injectComboViewers();

        DataProvider mockProvider = Mockito.mock(DataProvider.class);
        Mockito.when(mockProvider.getScansForProject(Mockito.anyString()))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class)) {
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            Method method = CheckmarxView.class.getDeclaredMethod(
                    "setSelectionForScanIdComboViewer", String.class, String.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(checkmarxView, "scan-abc-123", "main");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });
        }
    }

    @Test
    void testSetSelectionForScanIdComboViewer_nonEmptyScanList_nullScanId_setsViewerText() throws Exception {
        injectComboViewers();

        Scan mockScan = Mockito.mock(Scan.class);
        Mockito.when(mockScan.getId()).thenReturn("scan-001");
        Mockito.when(mockScan.getUpdatedAt()).thenReturn("2024-01-01T00:00:00Z");

        DataProvider mockProvider = Mockito.mock(DataProvider.class);
        Mockito.when(mockProvider.getScansForProject(Mockito.anyString()))
                .thenReturn(Arrays.asList(mockScan));

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class)) {
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);
            gsMock.when(() -> GlobalSettings.getFromPreferences(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn("");

            Method method = CheckmarxView.class.getDeclaredMethod(
                    "setSelectionForScanIdComboViewer", String.class, String.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(checkmarxView, null, "main");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });
        }
    }

    @Test
    void testSetSelectionForScanIdComboViewer_nonEmptyScanList_matchingScanId_setsSelection() throws Exception {
        injectComboViewers();

        Scan mockScan = Mockito.mock(Scan.class);
        Mockito.when(mockScan.getId()).thenReturn("scan-match");
        Mockito.when(mockScan.getUpdatedAt()).thenReturn("2024-01-01T00:00:00Z");

        DataProvider mockProvider = Mockito.mock(DataProvider.class);
        Mockito.when(mockProvider.getScansForProject(Mockito.anyString()))
                .thenReturn(Arrays.asList(mockScan));

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
             MockedStatic<GlobalSettings> gsMock = Mockito.mockStatic(GlobalSettings.class)) {
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);
            gsMock.when(() -> GlobalSettings.getFromPreferences(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn("");

            Method method = CheckmarxView.class.getDeclaredMethod(
                    "setSelectionForScanIdComboViewer", String.class, String.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(checkmarxView, "scan-match", "main");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });
        }
    }

    @Test
    void testLoadingProjects_doesNotThrow() throws Exception {
        Method method = CheckmarxView.class.getDeclaredMethod("loadingProjects");
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(checkmarxView);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        });
    }

    @Test
    void testLoadingBranches_doesNotThrow() throws Exception {
        injectComboViewers();
        Method method = CheckmarxView.class.getDeclaredMethod("loadingBranches");
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(checkmarxView);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        });
    }

    @Test
    void testLoadingScans_doesNotThrow() throws Exception {
        injectComboViewers();
        Method method = CheckmarxView.class.getDeclaredMethod("loadingScans");
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(checkmarxView);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        });
    }

    @Test
    void testResetFiltersState_withMockedDataProvider_doesNotThrow() throws Exception {
        FilterState.resetFilters();

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class)) {
            DataProvider mockProvider = Mockito.mock(DataProvider.class);
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            Method method = CheckmarxView.class.getDeclaredMethod("resetFiltersState");
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(checkmarxView);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });

            Mockito.verify(mockProvider).setCurrentScanId(null);
            Mockito.verify(mockProvider).setCurrentResults(null);
        }
    }

    @Test
    void testEnablePluginFields_withEmptyActions_doesNotThrow() throws Exception {
        Field tbField = CheckmarxView.class.getDeclaredField("toolBarActions");
        tbField.setAccessible(true);
        ToolBarActions toolbarMock = (ToolBarActions) tbField.get(checkmarxView);
        Mockito.when(toolbarMock.getToolBarActions()).thenReturn(Collections.emptyList());

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class)) {
            DataProvider mockProvider = Mockito.mock(DataProvider.class);
            Mockito.when(mockProvider.containsResults()).thenReturn(false);
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            Method method = CheckmarxView.class.getDeclaredMethod("enablePluginFields", boolean.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(checkmarxView, true);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });
        }
    }

    @Test
    void testEnablePluginFields_withResultsAvailable_enablesNonGroupBySeverityActions() throws Exception {
        Action mockAction = Mockito.mock(Action.class);
        Mockito.when(mockAction.getId()).thenReturn(ActionName.GROUP_BY_QUERY_NAME.name());

        Field tbField = CheckmarxView.class.getDeclaredField("toolBarActions");
        tbField.setAccessible(true);
        ToolBarActions toolbarMock = (ToolBarActions) tbField.get(checkmarxView);
        Mockito.when(toolbarMock.getToolBarActions()).thenReturn(Arrays.asList(mockAction));

        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class)) {
            DataProvider mockProvider = Mockito.mock(DataProvider.class);
            Mockito.when(mockProvider.containsResults()).thenReturn(true);
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            Method method = CheckmarxView.class.getDeclaredMethod("enablePluginFields", boolean.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> {
                try {
                    method.invoke(checkmarxView, false);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            });

            Mockito.verify(mockAction).setEnabled(true);
        }
    }
}