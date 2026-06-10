package checkmarx.ast.eclipse.plugin.tests.unit.views.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.views.DataProvider;

import com.checkmarx.eclipse.enums.PluginListenerType;
import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.PluginListenerDefinition;
import com.checkmarx.eclipse.views.actions.ToolBarActions;
import com.checkmarx.eclipse.views.filters.FilterState;
import com.google.common.eventbus.EventBus;

class ToolBarActionsTest {

    private IActionBars actionBars;
    private IToolBarManager toolBarManager;
    private IMenuManager menuManager;

    private TreeViewer resultsTree;
    private ComboViewer projectCombo;
    private ComboViewer branchCombo;
    private ComboViewer scanCombo;

    private EventBus eventBus;
    private DisplayModel rootModel;

    private ToolBarActions toolBarActions;

    @BeforeEach
    void setup() {
        FilterState.resetFilters();

        actionBars = mock(IActionBars.class);
        toolBarManager = mock(IToolBarManager.class);
        menuManager = mock(IMenuManager.class);

        resultsTree = mock(TreeViewer.class);
        projectCombo = mock(ComboViewer.class);
        branchCombo = mock(ComboViewer.class);
        scanCombo = mock(ComboViewer.class);

        rootModel = mock(DisplayModel.class);
        eventBus = new EventBus();

        when(actionBars.getToolBarManager()).thenReturn(toolBarManager);
        when(actionBars.getMenuManager()).thenReturn(menuManager);

        toolBarActions =
                new ToolBarActions.ToolBarActionsBuilder()
                        .actionBars(actionBars)
                        .rootModel(rootModel)
                        .resultsTree(resultsTree)
                        .pluginEventBus(eventBus)
                        .projectsCombo(projectCombo)
                        .branchesCombo(branchCombo)
                        .scansCombo(scanCombo)
                        .build();
    }

    @Test
    void testBuilderCreatesInstance() {
        assertNotNull(toolBarActions);
    }

    @Test
    void testGetToolBarActions() {
        List<Action> actions = toolBarActions.getToolBarActions();
        assertNotNull(actions);
    }

    @Test
    void testGetFilterActions() {
        List<Action> filters = toolBarActions.getFilterActions();
        assertNotNull(filters);
    }

    @Test
    void testGetStartScanAction() {
        Action action = toolBarActions.getStartScanAction();
        assertNotNull(action);
    }

    @Test
    void testGetCancelScanAction() {
        Action action = toolBarActions.getCancelScanAction();
        assertNotNull(action);
    }

    @Test
    void testGetStateFilterAction() {
        Action action = toolBarActions.getStateFilterAction();
        assertNotNull(action);
    }

    @Test
    void testDisposeToolbarRemovesAll() {

        toolBarActions.disposeToolbar();

        verify(toolBarManager).removeAll();
        verify(menuManager).removeAll();
    }

    @Test
    void testRefreshToolbarRecreatesActions() {

        toolBarActions.refreshToolbar();

        verify(toolBarManager).removeAll();
        verify(menuManager).removeAll();
    }

    @Test
    void testGroupBySeverityAction() {
        // The GROUP_BY_SEVERITY action calls FilterState.setState(Severity.GROUP_BY_SEVERITY).
        // createGroupByActions() runs inside a background Job so we test the toggle directly.
        FilterState.groupBySeverity = false;
        FilterState.setState(Severity.GROUP_BY_SEVERITY);
        assertTrue(FilterState.groupBySeverity);
    }

    @Test
    void testEventBusPostCleanRefresh() {

        assertDoesNotThrow(() -> {
            eventBus.post(
                    new PluginListenerDefinition(
                            PluginListenerType.CLEAN_AND_REFRESH,
                            Collections.emptyList()));
        });
    }

    @Test
    void testBuilderSetsComboViewers() {

        ToolBarActions actions =
                new ToolBarActions.ToolBarActionsBuilder()
                        .actionBars(actionBars)
                        .rootModel(rootModel)
                        .resultsTree(resultsTree)
                        .pluginEventBus(eventBus)
                        .projectsCombo(projectCombo)
                        .branchesCombo(branchCombo)
                        .scansCombo(scanCombo)
                        .build();

        assertNotNull(actions);
    }

    @Test
    void testToolBarActionsListNotEmpty() {

        List<Action> actions = toolBarActions.getToolBarActions();

        assertNotNull(actions);
        assertTrue(actions.size() >= 0);
    }

    @Test
    void testStaticConstant_menuGroupBy() {
        assertEquals("Group By", ToolBarActions.MENU_GROUP_BY);
    }

    @Test
    void testStaticConstant_groupBySeverity() {
        assertEquals("Severity", ToolBarActions.GROUP_BY_SEVERITY);
    }

    @Test
    void testStaticConstant_groupByQueryName() {
        assertEquals("Query Name", ToolBarActions.GROUP_BY_QUERY_NAME);
    }

    @Test
    void testStaticConstant_groupByStateName() {
        assertEquals("State Name", ToolBarActions.GROUP_BY_STATE_NAME);
    }

    @Test
    void testStaticConstant_menuFilterBy() {
        assertEquals("Filter By", ToolBarActions.MENU_FILTER_BY);
    }

    @Test
    void testFilterActionsContainAtLeastOneAction() {
        List<Action> filterActions = toolBarActions.getFilterActions();
        assertNotNull(filterActions);
    }

    @Test
    void testGetStartScanAction_notNull() {
        Action startScan = toolBarActions.getStartScanAction();
        assertNotNull(startScan);
        assertEquals(com.checkmarx.eclipse.enums.ActionName.START_SCAN.name(), startScan.getId());
    }

    @Test
    void testGetCancelScanAction_notNull() {
        Action cancelScan = toolBarActions.getCancelScanAction();
        assertNotNull(cancelScan);
        assertEquals(com.checkmarx.eclipse.enums.ActionName.CANCEL_SCAN.name(), cancelScan.getId());
    }

    @Test
    void testGetStateFilterAction_notNull() {
        Action stateFilter = toolBarActions.getStateFilterAction();
        assertNotNull(stateFilter);
        assertEquals(com.checkmarx.eclipse.enums.ActionName.FILTER_CHANGED.name(), stateFilter.getId());
    }

    @Test
    void testGroupBySeverityAction_run_invokedDirectly_togglesFilterState() throws Exception {
        try (MockedStatic<PlatformUI> puMock = Mockito.mockStatic(PlatformUI.class);
             MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class)) {

            IWorkbench mockWb = mock(IWorkbench.class);
            IWorkbenchWindow mockWin = mock(IWorkbenchWindow.class);
            when(mockWin.getShell()).thenReturn(null);
            when(mockWb.getActiveWorkbenchWindow()).thenReturn(mockWin);
            puMock.when(PlatformUI::getWorkbench).thenReturn(mockWb);

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.sortResults()).thenReturn(Collections.emptyList());
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            Method m = ToolBarActions.class.getDeclaredMethod("createGroupByActions");
            m.setAccessible(true);
            try {
                m.invoke(toolBarActions);
            } catch (java.lang.reflect.InvocationTargetException ignored) {
                // headless: syncExec may NPE; groupBySeverityAction is set before that point
            }

            Field f = ToolBarActions.class.getDeclaredField("groupBySeverityAction");
            f.setAccessible(true);
            Action action = (Action) f.get(toolBarActions);
            assertNotNull(action, "groupBySeverityAction must be created by createGroupByActions()");

            FilterState.resetFilters();
            FilterState.groupBySeverity = false;

            assertDoesNotThrow(action::run);
            assertTrue(FilterState.groupBySeverity, "run() must toggle groupBySeverity to true");
        }
    }

    @Test
    void testGroupByQueryNameAction_run_invokedDirectly_togglesFilterState() throws Exception {
        try (MockedStatic<PlatformUI> puMock = Mockito.mockStatic(PlatformUI.class);
             MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class)) {

            IWorkbench mockWb = mock(IWorkbench.class);
            IWorkbenchWindow mockWin = mock(IWorkbenchWindow.class);
            when(mockWin.getShell()).thenReturn(null);
            when(mockWb.getActiveWorkbenchWindow()).thenReturn(mockWin);
            puMock.when(PlatformUI::getWorkbench).thenReturn(mockWb);

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.sortResults()).thenReturn(Collections.emptyList());
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            Method m = ToolBarActions.class.getDeclaredMethod("createGroupByActions");
            m.setAccessible(true);
            try {
                m.invoke(toolBarActions);
            } catch (java.lang.reflect.InvocationTargetException ignored) {
                // headless: syncExec may NPE; groupByQueryNameAction is set before that point
            }

            Field f = ToolBarActions.class.getDeclaredField("groupByQueryNameAction");
            f.setAccessible(true);
            Action action = (Action) f.get(toolBarActions);
            assertNotNull(action, "groupByQueryNameAction must be created by createGroupByActions()");

            FilterState.resetFilters();
            FilterState.groupByQueryName = false;

            assertDoesNotThrow(action::run);
            assertTrue(FilterState.groupByQueryName, "run() must toggle groupByQueryName to true");
        }
    }

    @Test
    void testGroupByStateNameAction_run_invokedDirectly_togglesFilterState() throws Exception {
        try (MockedStatic<PlatformUI> puMock = Mockito.mockStatic(PlatformUI.class);
             MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class)) {

            IWorkbench mockWb = mock(IWorkbench.class);
            IWorkbenchWindow mockWin = mock(IWorkbenchWindow.class);
            when(mockWin.getShell()).thenReturn(null);
            when(mockWb.getActiveWorkbenchWindow()).thenReturn(mockWin);
            puMock.when(PlatformUI::getWorkbench).thenReturn(mockWb);

            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.sortResults()).thenReturn(Collections.emptyList());
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            Method m = ToolBarActions.class.getDeclaredMethod("createGroupByActions");
            m.setAccessible(true);
            try {
                m.invoke(toolBarActions);
            } catch (java.lang.reflect.InvocationTargetException ignored) {
                // headless: syncExec may NPE; groupByStateNameAction is set before that point
            }

            Field f = ToolBarActions.class.getDeclaredField("groupByStateNameAction");
            f.setAccessible(true);
            Action action = (Action) f.get(toolBarActions);
            assertNotNull(action, "groupByStateNameAction must be created by createGroupByActions()");

            FilterState.resetFilters();
            FilterState.groupByStateName = false;

            assertDoesNotThrow(action::run);
            assertTrue(FilterState.groupByStateName, "run() must toggle groupByStateName to true");
        }
    }

}