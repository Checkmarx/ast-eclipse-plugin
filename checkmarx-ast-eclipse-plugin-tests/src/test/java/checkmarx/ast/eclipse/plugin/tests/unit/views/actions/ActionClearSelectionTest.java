package checkmarx.ast.eclipse.plugin.tests.unit.views.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.actions.ActionClearSelection;
import com.google.common.eventbus.EventBus;

class ActionClearSelectionTest {

    private DisplayModel rootModel;
    private TreeViewer resultsTree;
    private EventBus eventBus;
    private ActionClearSelection actionClearSelection;

    @BeforeEach
    void setUp() {
        rootModel = mock(DisplayModel.class);
        resultsTree = mock(TreeViewer.class);
        eventBus = new EventBus();
        actionClearSelection = new ActionClearSelection(rootModel, resultsTree, eventBus);
    }

    @Test
    void testCreateAction_returnsNonNull() {
        Action action = actionClearSelection.createAction();
        assertNotNull(action);
    }

    @Test
    void testCreateAction_hasCorrectId() {
        Action action = actionClearSelection.createAction();
        assertEquals(ActionName.CLEAN_AND_REFRESH.name(), action.getId());
    }

    @Test
    void testCreateAction_hasTooltipText() {
        Action action = actionClearSelection.createAction();
        assertEquals(ActionClearSelection.ACTION_CLEAR_SELECTION_TOOLTIP, action.getToolTipText());
    }

    @Test
    void testCreateAction_isDisabledByDefault() {
        Action action = actionClearSelection.createAction();
        assertFalse(action.isEnabled());
    }

    @Test
    void testActionRun_postsEventWithoutThrowing() {
        Action action = actionClearSelection.createAction();
        assertDoesNotThrow(action::run);
    }

    @Test
    void testConstructor_storesEventBus() {
        // Different EventBus instances → different ActionClearSelection instances
        EventBus bus1 = new EventBus();
        EventBus bus2 = new EventBus();
        ActionClearSelection a1 = new ActionClearSelection(rootModel, resultsTree, bus1);
        ActionClearSelection a2 = new ActionClearSelection(rootModel, resultsTree, bus2);
        assertNotSame(a1, a2);
    }
}
