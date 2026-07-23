package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.checkmarx.eclipse.views.UISynchronizeImpl;

class UISynchronizeImplTest {

	private UISynchronizeImpl uiSync;
	@Mock
	private Display mockDisplay;
	@Mock
	private Runnable mockRunnable;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		uiSync = new UISynchronizeImpl(mockDisplay);
	}

	// ─── Constructor Tests ───────────────────────────────────────────

	@Test
	void testConstructor_storesDisplayReference() {
		assertNotNull(uiSync);
	}

	@Test
	void testConstructor_withMockDisplay() {
		UISynchronizeImpl sync = new UISynchronizeImpl(mockDisplay);
		assertNotNull(sync);
	}

	@Test
	void testConstructor_withNullDisplay_acceptsNull() {
		UISynchronizeImpl sync = new UISynchronizeImpl(null);
		assertNotNull(sync);
	}

	// ─── SyncExec Tests ─────────────────────────────────────────────

	@Test
	void testSyncExec_callsDisplaySyncExec() {
		uiSync.syncExec(mockRunnable);

		verify(mockDisplay).syncExec(mockRunnable);
		verify(mockDisplay, times(1)).syncExec(any(Runnable.class));
	}

	@Test
	void testSyncExec_withValidRunnable() {
		Runnable runnable = () -> {};
		uiSync.syncExec(runnable);

		verify(mockDisplay).syncExec(runnable);
	}

	@Test
	void testSyncExec_withNullRunnable() {
		uiSync.syncExec(null);

		verify(mockDisplay).syncExec(null);
	}

	@Test
	void testSyncExec_multipleInvocations() {
		Runnable runnable1 = () -> {};
		Runnable runnable2 = () -> {};

		uiSync.syncExec(runnable1);
		uiSync.syncExec(runnable2);

		verify(mockDisplay, times(2)).syncExec(any(Runnable.class));
		verify(mockDisplay).syncExec(runnable1);
		verify(mockDisplay).syncExec(runnable2);
	}

	@Test
	void testSyncExec_runnableThrowsException() {
		Runnable throwingRunnable = () -> {
			throw new RuntimeException("Test exception");
		};

		uiSync.syncExec(throwingRunnable);
		verify(mockDisplay).syncExec(throwingRunnable);
	}

	// ─── AsyncExec Tests ────────────────────────────────────────────

	@Test
	void testAsyncExec_callsDisplayAsyncExec() {
		uiSync.asyncExec(mockRunnable);

		verify(mockDisplay).asyncExec(mockRunnable);
		verify(mockDisplay, times(1)).asyncExec(any(Runnable.class));
	}

	@Test
	void testAsyncExec_withValidRunnable() {
		Runnable runnable = () -> {};
		uiSync.asyncExec(runnable);

		verify(mockDisplay).asyncExec(runnable);
	}

	@Test
	void testAsyncExec_withNullRunnable() {
		uiSync.asyncExec(null);

		verify(mockDisplay).asyncExec(null);
	}

	@Test
	void testAsyncExec_multipleInvocations() {
		Runnable runnable1 = () -> {};
		Runnable runnable2 = () -> {};

		uiSync.asyncExec(runnable1);
		uiSync.asyncExec(runnable2);

		verify(mockDisplay, times(2)).asyncExec(any(Runnable.class));
		verify(mockDisplay).asyncExec(runnable1);
		verify(mockDisplay).asyncExec(runnable2);
	}

	@Test
	void testAsyncExec_runnableThrowsException() {
		Runnable throwingRunnable = () -> {
			throw new RuntimeException("Test exception");
		};

		uiSync.asyncExec(throwingRunnable);
		verify(mockDisplay).asyncExec(throwingRunnable);
	}

	// ─── Mixed Operations Tests ─────────────────────────────────────

	@Test
	void testSyncExecFollowedByAsyncExec() {
		Runnable syncRunnable = () -> {};
		Runnable asyncRunnable = () -> {};

		uiSync.syncExec(syncRunnable);
		uiSync.asyncExec(asyncRunnable);

		verify(mockDisplay).syncExec(syncRunnable);
		verify(mockDisplay).asyncExec(asyncRunnable);
	}

	@Test
	void testAsyncExecFollowedBySyncExec() {
		Runnable asyncRunnable = () -> {};
		Runnable syncRunnable = () -> {};

		uiSync.asyncExec(asyncRunnable);
		uiSync.syncExec(syncRunnable);

		verify(mockDisplay).asyncExec(asyncRunnable);
		verify(mockDisplay).syncExec(syncRunnable);
	}

	@Test
	void testMultipleSyncAndAsyncExecCalls() {
		uiSync.syncExec(() -> {});
		uiSync.asyncExec(() -> {});
		uiSync.syncExec(() -> {});
		uiSync.asyncExec(() -> {});

		verify(mockDisplay, times(2)).syncExec(any(Runnable.class));
		verify(mockDisplay, times(2)).asyncExec(any(Runnable.class));
	}

	@Test
	void testSyncExec_verifyNoOtherInteractions() {
		uiSync.syncExec(mockRunnable);

		verify(mockDisplay, times(1)).syncExec(mockRunnable);
		verify(mockDisplay, never()).asyncExec(any(Runnable.class));
	}

	@Test
	void testAsyncExec_verifyNoOtherInteractions() {
		uiSync.asyncExec(mockRunnable);

		verify(mockDisplay, times(1)).asyncExec(mockRunnable);
		verify(mockDisplay, never()).syncExec(any(Runnable.class));
	}
}
