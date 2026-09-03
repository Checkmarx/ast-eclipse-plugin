package checkmarx.ast.eclipse.plugin.tests.unit.devassist.backend.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.backend.listener.CheckmarxDocumentListener;
import com.checkmarx.eclipse.devassist.backend.listener.RealTimeScanJob;
import com.checkmarx.eclipse.devassist.inspection.DevAssistScanScheduler;

/**
 * Unit tests for {@link CheckmarxDocumentListener}, the real-time-scan
 * debounce trigger fired on every document edit. {@code documentChanged}
 * never reads its {@code DocumentEvent} argument, so {@code null} is passed
 * for it throughout - matching the actual (unused-parameter) implementation.
 */
class CheckmarxDocumentListenerTest {

	private IFile file() {
		IFile file = mock(IFile.class);
		when(file.getName()).thenReturn("Main.java");
		return file;
	}

	@Test
	@DisplayName("documentChanged reschedules the debounced scan via the scheduler when one is available")
	void documentChangedReschedulesViaScheduler() {
		DevAssistScanScheduler scheduler = mock(DevAssistScanScheduler.class);
		IFile file = file();
		CheckmarxDocumentListener listener = new CheckmarxDocumentListener("Main.java", null, file, scheduler);

		listener.documentChanged(null);

		verify(scheduler).rescheduleInspection(file, 1000);
	}

	@Test
	@DisplayName("Two rapid edits within the throttle window only reschedule once")
	void rapidEditsAreThrottled() {
		DevAssistScanScheduler scheduler = mock(DevAssistScanScheduler.class);
		CheckmarxDocumentListener listener = new CheckmarxDocumentListener("Main.java", null, file(), scheduler);

		listener.documentChanged(null);
		listener.documentChanged(null); // fires within the same test method, well under the 100ms throttle window

		verify(scheduler, times(1)).rescheduleInspection(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1000L));
	}

	@Test
	@DisplayName("setSkipNextChange(true) suppresses exactly the next reschedule, then resets")
	void skipNextChangeSuppressesOneReschedule() {
		DevAssistScanScheduler scheduler = mock(DevAssistScanScheduler.class);
		IFile file = file();
		CheckmarxDocumentListener listener = new CheckmarxDocumentListener("Main.java", null, file, scheduler);

		listener.setSkipNextChange(true);
		listener.documentChanged(null);

		verify(scheduler, never()).rescheduleInspection(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	@DisplayName("Falls back to the scanJob's own reschedule when no scheduler is provided")
	void fallsBackToScanJobWhenSchedulerNull() {
		RealTimeScanJob scanJob = mock(RealTimeScanJob.class);
		CheckmarxDocumentListener listener = new CheckmarxDocumentListener("Main.java", scanJob, null, null);

		listener.documentChanged(null);

		verify(scanJob).reschedule(1000);
	}

	@Test
	@DisplayName("An exception from the scheduler is caught and does not propagate")
	void schedulerExceptionIsCaughtSafely() {
		DevAssistScanScheduler scheduler = mock(DevAssistScanScheduler.class);
		IFile file = file();
		doThrow(new RuntimeException("boom")).when(scheduler).rescheduleInspection(file, 1000);
		CheckmarxDocumentListener listener = new CheckmarxDocumentListener("Main.java", null, file, scheduler);

		assertDoesNotThrow(() -> listener.documentChanged(null));
	}

	@Test
	@DisplayName("documentAboutToBeChanged is a safe no-op")
	void documentAboutToBeChangedIsNoOp() {
		CheckmarxDocumentListener listener = new CheckmarxDocumentListener("Main.java", null, null, null);

		assertDoesNotThrow(() -> listener.documentAboutToBeChanged(null));
	}

	@Test
	@DisplayName("dispose cancels the underlying scan job when one is present")
	void disposeCancelsScanJob() {
		RealTimeScanJob scanJob = mock(RealTimeScanJob.class);
		CheckmarxDocumentListener listener = new CheckmarxDocumentListener("Main.java", scanJob, null, null);

		listener.dispose();

		verify(scanJob).cancel();
	}

	@Test
	@DisplayName("dispose is a safe no-op when there is no scan job")
	void disposeWithoutScanJobIsNoOp() {
		CheckmarxDocumentListener listener = new CheckmarxDocumentListener("Main.java", null, null, null);

		assertDoesNotThrow(listener::dispose);
	}

	@Test
	@DisplayName("getFileName returns the file name passed to the constructor")
	void getFileNameReturnsConstructorValue() {
		CheckmarxDocumentListener listener = new CheckmarxDocumentListener("Main.java", null, null, null);

		assertEquals("Main.java", listener.getFileName());
	}
}
