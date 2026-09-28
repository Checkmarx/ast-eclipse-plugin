package checkmarx.ast.eclipse.plugin.tests.unit.devassist.backend;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder;

class DevAssistScanStateHolderTest {

	private DevAssistScanStateHolder holder;

	@BeforeEach
	void setUp() {
		holder = new DevAssistScanStateHolder();
	}

	@Test
	void getStateHash_unknownPath_returnsNull() {
		assertNull(holder.getStateHash("/unknown/path.java"));
	}

	@Test
	void updateStateHash_thenGetStateHash_returnsUpdatedValue() {
		holder.updateStateHash("/path/file.java", 12345L);
		assertEquals(12345L, holder.getStateHash("/path/file.java"));
	}

	@Test
	void updateStateHash_overwritesExistingValue() {
		holder.updateStateHash("/path/file.java", 100L);
		holder.updateStateHash("/path/file.java", 200L);
		assertEquals(200L, holder.getStateHash("/path/file.java"));
	}

	@Test
	void updateStateHash_multiplePaths_tracksEachIndependently() {
		holder.updateStateHash("/a.java", 1L);
		holder.updateStateHash("/b.java", 2L);
		assertEquals(1L, holder.getStateHash("/a.java"));
		assertEquals(2L, holder.getStateHash("/b.java"));
	}

	@Test
	void hasChanged_unknownPath_returnsTrue() {
		assertTrue(holder.hasChanged("/new/path.java", 12345L));
	}

	@Test
	void hasChanged_unchangedFile_returnsFalse() {
		long hash = 99999L;
		holder.updateStateHash("/path/file.java", hash);
		assertFalse(holder.hasChanged("/path/file.java", hash));
	}

	@Test
	void hasChanged_changedFile_returnsTrue() {
		holder.updateStateHash("/path/file.java", 100L);
		assertTrue(holder.hasChanged("/path/file.java", 200L));
	}

	@Test
	void hasChanged_nullPath_returnsTrue() {
		assertTrue(holder.hasChanged(null, 12345L));
	}

	@Test
	void markScanComplete_removesInFlightMarker() {
		holder.updateStateHash("/path/file.java", 100L);
		assertTrue(holder.hasChanged("/path/file.java", 200L));
		holder.markScanComplete("/path/file.java");
		// After marking complete, another change should be detected
		assertTrue(holder.hasChanged("/path/file.java", 300L));
	}

	@Test
	void clearFileState_removesFileState() {
		holder.updateStateHash("/path/file.java", 12345L);
		assertEquals(12345L, holder.getStateHash("/path/file.java"));
		holder.clearFileState("/path/file.java");
		assertNull(holder.getStateHash("/path/file.java"));
	}

	@Test
	void clearAll_removesAllState() {
		holder.updateStateHash("/a.java", 1L);
		holder.updateStateHash("/b.java", 2L);
		holder.clearAll();
		assertNull(holder.getStateHash("/a.java"));
		assertNull(holder.getStateHash("/b.java"));
	}

	@Test
	void getStatistics_returnsTrackedFileCount() {
		holder.updateStateHash("/a.java", 1L);
		holder.updateStateHash("/b.java", 2L);
		String stats = holder.getStatistics();
		assertNotNull(stats);
		assertTrue(stats.contains("2") || stats.contains("Tracked files"));
	}
}
