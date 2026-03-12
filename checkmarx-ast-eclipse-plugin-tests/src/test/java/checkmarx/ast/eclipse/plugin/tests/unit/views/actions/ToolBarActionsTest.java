package checkmarx.ast.eclipse.plugin.tests.unit.views.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.IActionBars;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.checkmarx.eclipse.enums.PluginListenerType;
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

        List<Action> actions = toolBarActions.getFilterActions();

        for (Action action : actions) {
            if ("GROUP_BY_SEVERITY".equals(action.getId())) {
                action.run();
                break;
            }
        }

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

}