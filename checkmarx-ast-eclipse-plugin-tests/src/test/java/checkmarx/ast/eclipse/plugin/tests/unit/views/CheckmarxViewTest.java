package checkmarx.ast.eclipse.plugin.tests.unit.views;

import com.checkmarx.eclipse.views.CheckmarxView;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.actions.ToolBarActions;
import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.eclipse.Activator;

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
}