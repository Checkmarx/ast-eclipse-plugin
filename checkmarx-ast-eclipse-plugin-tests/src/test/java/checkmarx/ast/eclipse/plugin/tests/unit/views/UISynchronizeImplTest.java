package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.views.UISynchronizeImpl;

class UISynchronizeImplTest {

    private static Display display;

    @BeforeAll
    static void setUpClass() {
        display = Display.getDefault();
    }

    @Test
    void testSyncExec_runnableExecutes() {
        UISynchronizeImpl sync = new UISynchronizeImpl(display);
        AtomicBoolean executed = new AtomicBoolean(false);

        sync.syncExec(() -> executed.set(true));

        assertTrue(executed.get());
    }

    @Test
    void testAsyncExec_runnableEventuallyExecutes() {
        UISynchronizeImpl sync = new UISynchronizeImpl(display);
        AtomicBoolean executed = new AtomicBoolean(false);

        sync.asyncExec(() -> executed.set(true));

        // Pump pending async events
        display.syncExec(() -> {});

        assertTrue(executed.get());
    }

    @Test
    void testSyncExec_constructorAcceptsDisplay() {
        assertDoesNotThrow(() -> new UISynchronizeImpl(display));
    }
}
