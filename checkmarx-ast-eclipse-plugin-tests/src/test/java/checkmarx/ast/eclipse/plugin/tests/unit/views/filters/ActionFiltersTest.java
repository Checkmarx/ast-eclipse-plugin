package checkmarx.ast.eclipse.plugin.tests.unit.views.filters;

import com.checkmarx.eclipse.views.filters.ActionFilters;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.enums.PluginListenerType;
import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.PluginListenerDefinition;
import com.google.common.eventbus.EventBus;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionFiltersTest {
    private EventBus mockEventBus;
    private ActionFilters actionFilters;

    @BeforeEach
    void setUp() {
        mockEventBus = mock(EventBus.class);
        actionFilters = new ActionFilters(mockEventBus);
    }

    @Test
    void testCreateFilterActions_propertiesAndState() {
        try (MockedStatic<DataProvider> dp = Mockito.mockStatic(DataProvider.class);
             MockedStatic<com.checkmarx.eclipse.views.filters.FilterState> fs = Mockito.mockStatic(com.checkmarx.eclipse.views.filters.FilterState.class);
             MockedStatic<com.checkmarx.eclipse.Activator> activator = Mockito.mockStatic(com.checkmarx.eclipse.Activator.class)) {

            DataProvider provider = mock(DataProvider.class);
            dp.when(DataProvider::getInstance).thenReturn(provider);
            when(provider.containsResults()).thenReturn(true);
            fs.when(() -> com.checkmarx.eclipse.views.filters.FilterState.isSeverityEnabled(anyString())).thenReturn(true);
            activator.when(() -> com.checkmarx.eclipse.Activator.getImageDescriptor(anyString())).thenReturn(mock(ImageDescriptor.class));

            List<Action> actions = actionFilters.createFilterActions();
            assertEquals(5, actions.size());
            assertEquals(ActionName.CRITICAL.name(), actions.get(0).getId());
            assertEquals(ActionName.HIGH.name(), actions.get(1).getId());
            assertEquals(ActionName.MEDIUM.name(), actions.get(2).getId());
            assertEquals(ActionName.LOW.name(), actions.get(3).getId());
            assertEquals(ActionName.INFO.name(), actions.get(4).getId());
            for (Action action : actions) {
                assertTrue(action.isEnabled());
                assertTrue(action.isChecked());
                assertNotNull(action.getToolTipText());
                assertNotNull(action.getImageDescriptor());
            }
        }
    }

    @Test
    void testCreateFilterActions_runActionPostsEvent() {
        try (MockedStatic<DataProvider> dp = Mockito.mockStatic(DataProvider.class);
             MockedStatic<com.checkmarx.eclipse.views.filters.FilterState> fs = Mockito.mockStatic(com.checkmarx.eclipse.views.filters.FilterState.class);
             MockedStatic<com.checkmarx.eclipse.Activator> activator = Mockito.mockStatic(com.checkmarx.eclipse.Activator.class);
             MockedStatic<PluginListenerDefinition> pld = Mockito.mockStatic(PluginListenerDefinition.class, Mockito.CALLS_REAL_METHODS)) {

            DataProvider provider = mock(DataProvider.class);
            dp.when(DataProvider::getInstance).thenReturn(provider);
            when(provider.containsResults()).thenReturn(true);
            fs.when(() -> com.checkmarx.eclipse.views.filters.FilterState.isSeverityEnabled(anyString())).thenReturn(true);
            activator.when(() -> com.checkmarx.eclipse.Activator.getImageDescriptor(anyString())).thenReturn(mock(ImageDescriptor.class));
            when(provider.sortResults()).thenReturn(mock(List.class));

            List<Action> actions = actionFilters.createFilterActions();
            Action criticalAction = actions.get(0);
            criticalAction.run();
            ArgumentCaptor<PluginListenerDefinition> captor = ArgumentCaptor.forClass(PluginListenerDefinition.class);
            verify(mockEventBus, atLeastOnce()).post(captor.capture());
            PluginListenerDefinition event = captor.getValue();
            assertEquals(PluginListenerType.FILTER_CHANGED, event.getListenerType());
        }
    }

    @Test
    void testCreateFilterActions_disabledUnchecked() {
        try (MockedStatic<DataProvider> dp = Mockito.mockStatic(DataProvider.class);
             MockedStatic<com.checkmarx.eclipse.views.filters.FilterState> fs = Mockito.mockStatic(com.checkmarx.eclipse.views.filters.FilterState.class);
             MockedStatic<com.checkmarx.eclipse.Activator> activator = Mockito.mockStatic(com.checkmarx.eclipse.Activator.class)) {

            DataProvider provider = mock(DataProvider.class);
            dp.when(DataProvider::getInstance).thenReturn(provider);
            when(provider.containsResults()).thenReturn(false);
            fs.when(() -> com.checkmarx.eclipse.views.filters.FilterState.isSeverityEnabled(anyString())).thenReturn(false);
            activator.when(() -> com.checkmarx.eclipse.Activator.getImageDescriptor(anyString())).thenReturn(mock(ImageDescriptor.class));

            List<Action> actions = actionFilters.createFilterActions();
            for (Action action : actions) {
                assertFalse(action.isEnabled());
                assertFalse(action.isChecked());
            }
        }
    }
}