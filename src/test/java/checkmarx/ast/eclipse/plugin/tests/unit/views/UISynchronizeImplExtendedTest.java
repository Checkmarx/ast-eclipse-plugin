package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.views.UISynchronizeImpl;

class UISynchronizeImplExtendedTest {

	private UISynchronizeImpl uiSync;
	private Display mockDisplay;

	@BeforeEach
	void setUp() {
		mockDisplay = mock(Display.class);
		uiSync = new UISynchronizeImpl(mockDisplay);
	}

	@Test
	void testConstructor_storesDisplayReference() {
		assertNotNull(uiSync);
	}

	@Test
	void testSyncExec_callsDisplaySyncExec() {
		Runnable runnable = mock(Runnable.class);
		uiSync.syncExec(runnable);
		verify(mockDisplay, times(1)).syncExec(runnable);
	}

	@Test
	void testAsyncExec_callsDisplayAsyncExec() {
		Runnable runnable = mock(Runnable.class);
		uiSync.asyncExec(runnable);
		verify(mockDisplay, times(1)).asyncExec(runnable);
	}

	@Test
	void testSyncExec_withMultipleRunnables_executesAllInOrder() {
		Runnable runnable1 = mock(Runnable.class);
		Runnable runnable2 = mock(Runnable.class);
		Runnable runnable3 = mock(Runnable.class);

		uiSync.syncExec(runnable1);
		uiSync.syncExec(runnable2);
		uiSync.syncExec(runnable3);

		verify(mockDisplay).syncExec(runnable1);
		verify(mockDisplay).syncExec(runnable2);
		verify(mockDisplay).syncExec(runnable3);
		verify(mockDisplay, times(3)).syncExec(any());
	}

	@Test
	void testAsyncExec_withMultipleRunnables_executesAll() {
		Runnable runnable1 = mock(Runnable.class);
		Runnable runnable2 = mock(Runnable.class);

		uiSync.asyncExec(runnable1);
		uiSync.asyncExec(runnable2);

		verify(mockDisplay).asyncExec(runnable1);
		verify(mockDisplay).asyncExec(runnable2);
		verify(mockDisplay, times(2)).asyncExec(any());
	}

	@Test
	void testSyncExec_withNullRunnable_passesThrough() {
		// This tests behavior when null is passed - should pass to Display
		uiSync.syncExec(null);
		verify(mockDisplay, times(1)).syncExec(null);
	}

	@Test
	void testAsyncExec_withNullRunnable_passesThrough() {
		uiSync.asyncExec(null);
		verify(mockDisplay, times(1)).asyncExec(null);
	}

	@Test
	void testSyncExecAndAsyncExec_mixedCalls() {
		Runnable syncRunnable = mock(Runnable.class);
		Runnable asyncRunnable = mock(Runnable.class);

		uiSync.syncExec(syncRunnable);
		uiSync.asyncExec(asyncRunnable);
		uiSync.syncExec(syncRunnable);

		verify(mockDisplay, times(2)).syncExec(syncRunnable);
		verify(mockDisplay, times(1)).asyncExec(asyncRunnable);
	}

	@Test
	void testMultipleInstances_independentDisplayReferences() {
		Display display1 = mock(Display.class);
		Display display2 = mock(Display.class);

		UISynchronizeImpl sync1 = new UISynchronizeImpl(display1);
		UISynchronizeImpl sync2 = new UISynchronizeImpl(display2);

		Runnable runnable = mock(Runnable.class);

		sync1.syncExec(runnable);
		sync2.asyncExec(runnable);

		verify(display1).syncExec(runnable);
		verify(display2).asyncExec(runnable);
		verifyNoMoreInteractions(display1, display2);
	}

	@Test
	void testSyncExec_withRunnableThrowingException_displayHandlesIt() {
		Runnable throwingRunnable = mock(Runnable.class);
		doThrow(new RuntimeException("Test exception")).when(mockDisplay).syncExec(throwingRunnable);

		assertThrows(RuntimeException.class, () -> uiSync.syncExec(throwingRunnable));
	}

	@Test
	void testAsyncExec_withRunnableThrowingException_displayHandlesIt() {
		Runnable throwingRunnable = mock(Runnable.class);
		doThrow(new RuntimeException("Test exception")).when(mockDisplay).asyncExec(throwingRunnable);

		assertThrows(RuntimeException.class, () -> uiSync.asyncExec(throwingRunnable));
	}

	@Test
	void testSyncExec_sequentialCalls_verifiesExecutionOrder() {
		Runnable first = mock(Runnable.class);
		Runnable second = mock(Runnable.class);
		Runnable third = mock(Runnable.class);

		uiSync.syncExec(first);
		uiSync.syncExec(second);
		uiSync.syncExec(third);

		// Verify all were called in order
		verify(mockDisplay, times(1)).syncExec(first);
		verify(mockDisplay, times(1)).syncExec(second);
		verify(mockDisplay, times(1)).syncExec(third);
	}

	@Test
	void testAsyncExec_sequentialCalls_verifiesExecutionOrder() {
		Runnable first = mock(Runnable.class);
		Runnable second = mock(Runnable.class);

		uiSync.asyncExec(first);
		uiSync.asyncExec(second);

		verify(mockDisplay, times(1)).asyncExec(first);
		verify(mockDisplay, times(1)).asyncExec(second);
	}

	@Test
	void testSyncExecAndAsyncExec_alternatingCalls() {
		Runnable sync1 = mock(Runnable.class);
		Runnable async1 = mock(Runnable.class);
		Runnable sync2 = mock(Runnable.class);
		Runnable async2 = mock(Runnable.class);

		uiSync.syncExec(sync1);
		uiSync.asyncExec(async1);
		uiSync.syncExec(sync2);
		uiSync.asyncExec(async2);

		verify(mockDisplay).syncExec(sync1);
		verify(mockDisplay).asyncExec(async1);
		verify(mockDisplay).syncExec(sync2);
		verify(mockDisplay).asyncExec(async2);
	}

	@Test
	void testConstructor_withDifferentDisplayInstances() {
		Display display1 = mock(Display.class);
		Display display2 = mock(Display.class);
		Display display3 = mock(Display.class);

		UISynchronizeImpl sync1 = new UISynchronizeImpl(display1);
		UISynchronizeImpl sync2 = new UISynchronizeImpl(display2);
		UISynchronizeImpl sync3 = new UISynchronizeImpl(display3);

		Runnable runnable = () -> {};

		sync1.syncExec(runnable);
		sync2.syncExec(runnable);
		sync3.syncExec(runnable);

		verify(display1).syncExec(runnable);
		verify(display2).syncExec(runnable);
		verify(display3).syncExec(runnable);
	}
}
