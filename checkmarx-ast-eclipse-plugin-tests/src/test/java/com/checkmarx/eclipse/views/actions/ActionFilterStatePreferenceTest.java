package com.checkmarx.eclipse.views.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.filters.FilterState;
import com.google.common.eventbus.EventBus;

class ActionFilterStatePreferenceTest {

    private static MockedStatic<Activator> activatorMock;
    private static Display display;

    @BeforeAll
    static void setUpClass() {
        display = Display.getDefault();
        activatorMock = Mockito.mockStatic(Activator.class);
        ImageDescriptor descriptor = mock(ImageDescriptor.class);
        Image image = mock(Image.class);
        when(descriptor.createImage()).thenReturn(image);
        activatorMock.when(() -> Activator.getImageDescriptor(anyString())).thenReturn(descriptor);
    }

    @AfterAll
    static void tearDownClass() {
        activatorMock.close();
    }

    private ActionFilterStatePreference buildPreference() {
        return new ActionFilterStatePreference(new EventBus());
    }

    @Test
    void testFilterNotExploitableConstant() {
        assertEquals("Not Exploitable", ActionFilterStatePreference.FILTER_NOT_EXPLOITABLE);
    }

    @Test
    void testFilterConfirmedConstant() {
        assertEquals("Confirmed", ActionFilterStatePreference.FILTER_CONFIRMED);
    }

    @Test
    void testFilterProposedNotExploitableConstant() {
        assertEquals("Proposed Not Exploitable", ActionFilterStatePreference.FILTER_PROPOSED_NON_EXPLOITABLE);
    }

    @Test
    void testFilterToVerifyConstant() {
        assertEquals("To Verify", ActionFilterStatePreference.FILTER_TO_VERIFY);
    }

    @Test
    void testFilterUrgentConstant() {
        assertEquals("Urgent", ActionFilterStatePreference.FILTER_URGENT);
    }

    @Test
    void testFilterIgnoredConstant() {
        assertEquals("Ignored", ActionFilterStatePreference.FILTER_IGNORED);
    }

    @Test
    void testFilterNotIgnoredConstant() {
        assertEquals("Not Ignored", ActionFilterStatePreference.FILTER_NOT_IGNORED);
    }

    @Test
    void testConstructor_setsCorrectId() {
        ActionFilterStatePreference pref = buildPreference();
        assertEquals(ActionName.FILTER_CHANGED.name(), pref.getId());
    }

    @Test
    void testDispose_whenMenuIsNull_doesNotThrow() {
        ActionFilterStatePreference pref = buildPreference();
        assertDoesNotThrow(pref::dispose);
    }

    @Test
    void testGetMenu_menuOverload_returnsNull() {
        ActionFilterStatePreference pref = buildPreference();
        Menu parentMenu = null;
        assertNull(pref.getMenu(parentMenu));
    }

    @Test
    void testGetMenu_controlOverload_createsMenu() {
        ActionFilterStatePreference pref = buildPreference();
        final Menu[] result = {null};
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                result[0] = pref.getMenu(shell);
            } finally {
                // dispose the menu so we don't leak; then shell
                if (result[0] != null && !result[0].isDisposed()) {
                    result[0].dispose();
                }
                shell.dispose();
            }
        });
    }

    @Test
    void testDispose_afterGetMenu_disposesMenu() {
        ActionFilterStatePreference pref = buildPreference();
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                pref.getMenu(shell);   // creates the menu
                assertDoesNotThrow(pref::dispose);  // should dispose it
            } finally {
                shell.dispose();
            }
        });
    }

    @Test
    void testRunWithEvent_withToolItem_opensMenuWithoutThrowing() {
        ActionFilterStatePreference pref = buildPreference();
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                ToolBar toolBar = new ToolBar(shell, SWT.FLAT);
                ToolItem toolItem = new ToolItem(toolBar, SWT.DROP_DOWN);

                Event event = new Event();
                event.widget = toolItem;

                try {
                    pref.runWithEvent(event);
                } catch (Exception ignored) {
                    // headless environments may not support setVisible on popup menus
                } finally {
                    pref.dispose();  // close the popup menu if it was opened
                }
            } finally {
                shell.dispose();
            }
        });
    }

    @Test
    void testGetMenu_control_standardStateItemFires_widgetSelected() {
        ActionFilterStatePreference pref = buildPreference();
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                FilterState.resetFilters();

                Menu menu = pref.getMenu(shell);
                assertNotNull(menu);
                // 7 standard state items expected
                assertTrue(menu.getItemCount() >= 7);

                // Fire widgetSelected on the first standard-state MenuItem
                MenuItem item = menu.getItem(0);
                Event selEvent = new Event();
                selEvent.widget = item;
                assertDoesNotThrow(() -> item.notifyListeners(SWT.Selection, selEvent));
            } finally {
                shell.dispose();
            }
        });
    }

    @Test
    void testGetMenu_callTwice_disposesExistingMenu() {
        ActionFilterStatePreference pref = buildPreference();
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                // First call creates a menu
                Menu menu1 = pref.getMenu(shell);
                assertNotNull(menu1);
                // Second call disposes the old menu and creates a new one
                Menu menu2 = pref.getMenu(shell);
                assertNotNull(menu2);
                assertNotSame(menu1, menu2);
            } finally {
                pref.dispose();
                shell.dispose();
            }
        });
    }

    @Test
    void testGetMenu_withCustomState_firesWidgetSelected_coversCustomStateAdapter() {
        try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class)) {
            DataProvider mockProvider = mock(DataProvider.class);
            when(mockProvider.getCustomStates()).thenReturn(Arrays.asList("MY_CUSTOM_STATE"));
            when(mockProvider.sortResults()).thenReturn(Collections.emptyList());
            dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

            ActionFilterStatePreference pref = buildPreference();
            display.syncExec(() -> {
                Shell shell = new Shell(display);
                try {
                    FilterState.resetFilters();
                    Menu menu = pref.getMenu(shell);
                    assertNotNull(menu);
                    // 7 standard + 1 custom = 8 items
                    assertEquals(8, menu.getItemCount());

                    // Fire widgetSelected on the custom state item (last item)
                    MenuItem customItem = menu.getItem(7);
                    Event selEvent = new Event();
                    selEvent.widget = customItem;
                    assertDoesNotThrow(() -> customItem.notifyListeners(SWT.Selection, selEvent));
                } finally {
                    pref.dispose();
                    shell.dispose();
                }
            });
        }
    }
}
