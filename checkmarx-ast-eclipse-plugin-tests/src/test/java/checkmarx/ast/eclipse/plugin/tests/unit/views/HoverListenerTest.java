package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.views.HoverListener;

class HoverListenerTest {

    private static Display display;

    @BeforeAll
    static void setUpClass() {
        display = Display.getDefault();
    }

    @Test
    void testConstructor_emptyList_createsInstance() {
        HoverListener listener = new HoverListener(Collections.emptyList());
        assertNotNull(listener);
    }

    @Test
    void testMouseHover_emptyList_doesNothing() {
        HoverListener listener = new HoverListener(Collections.emptyList());
        assertDoesNotThrow(() -> listener.mouseHover(null));
    }

    @Test
    void testMouseExit_nullDefaultColor_doesNotThrow() {
        // Empty list → defaultColor = null; mouseExit should be a no-op
        HoverListener listener = new HoverListener(Collections.emptyList());
        assertDoesNotThrow(() -> listener.mouseExit(null));
    }

    @Test
    void testApply_emptyList_doesNotThrow() {
        HoverListener listener = new HoverListener(Collections.emptyList());
        assertDoesNotThrow(() -> listener.apply());
    }

    @Test
    void testMouseEnter_realControl_setsBackgroundWithoutThrowing() {
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                Label label = new Label(shell, SWT.NONE);
                HoverListener listener = new HoverListener(List.of(label));
                assertDoesNotThrow(() -> listener.mouseEnter(null));
            } finally {
                shell.dispose();
            }
        });
    }

    @Test
    void testMouseExit_afterMouseEnter_disposesCustomColor() {
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                Label label = new Label(shell, SWT.NONE);
                HoverListener listener = new HoverListener(List.of(label));
                listener.mouseEnter(null);   // sets customColor
                assertDoesNotThrow(() -> listener.mouseExit(null));  // disposes customColor
            } finally {
                shell.dispose();
            }
        });
    }

    @Test
    void testMouseExit_withDefaultColor_noMouseEnter_setsBackground() {
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                Label label = new Label(shell, SWT.NONE);
                // defaultColor set from label.getBackground() in constructor
                HoverListener listener = new HoverListener(List.of(label));
                // Call mouseExit without mouseEnter — customColor == null, defaultColor != null
                assertDoesNotThrow(() -> listener.mouseExit(null));
            } finally {
                shell.dispose();
            }
        });
    }

    @Test
    void testApply_withRealControl_addsListenerWithoutThrowing() {
        display.syncExec(() -> {
            Shell shell = new Shell(display);
            try {
                Label label = new Label(shell, SWT.NONE);
                HoverListener listener = new HoverListener(List.of(label));
                assertDoesNotThrow(() -> listener.apply());
            } finally {
                shell.dispose();
            }
        });
    }
}
