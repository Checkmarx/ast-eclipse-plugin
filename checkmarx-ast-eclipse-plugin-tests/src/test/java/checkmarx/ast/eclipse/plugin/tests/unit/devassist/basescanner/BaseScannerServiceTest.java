package checkmarx.ast.eclipse.plugin.tests.unit.devassist.basescanner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.common.ScannerConfig;

/**
 * Unit tests for {@link BaseScannerService} using a minimal concrete
 * subclass, exercising {@code shouldScanFile}'s node_modules exclusion and
 * the temp-folder helpers directly.
 */
class BaseScannerServiceTest {

	private static class TestScannerService extends BaseScannerService<Object> {
		boolean supported;

		TestScannerService(IProject project, ScannerConfig config) {
			super(project, config);
		}

		@Override
		protected boolean isFileTypeSupported(String filePath) {
			return supported;
		}

		@Override
		public ScanResult<Object> scan(String filePath) {
			return null;
		}

		String callGetTempSubFolderPath(String baseDir) {
			return getTempSubFolderPath(baseDir);
		}

		void callCreateTempFolder(Path path) {
			createTempFolder(path);
		}

		void callDeleteTempFolder(Path path) {
			deleteTempFolder(path);
		}
	}

	private IProject project;
	private TestScannerService service;
	private Path tempDir;

	@BeforeEach
	void setUp() {
		project = mock(IProject.class);
		when(project.getName()).thenReturn("TestProject");
		service = new TestScannerService(project, ScannerConfig.builder().engineName("TEST").build());
	}

	@AfterEach
	void cleanUp() throws IOException {
		if (tempDir == null || !Files.exists(tempDir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(tempDir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ignored) {
					// best-effort cleanup
				}
			});
		}
	}

	@Test
	@DisplayName("shouldScanFile rejects null or empty path without consulting isFileTypeSupported")
	void shouldScanFileRejectsNullOrEmpty() {
		service.supported = true;
		assertFalse(service.shouldScanFile(null));
		assertFalse(service.shouldScanFile(""));
	}

	@Test
	@DisplayName("shouldScanFile rejects any path under a node_modules directory, forward or back slash")
	void shouldScanFileRejectsNodeModules() {
		service.supported = true;
		assertFalse(service.shouldScanFile("/repo/node_modules/lodash/index.js"));
		assertFalse(service.shouldScanFile("C:\\repo\\node_modules\\lodash\\index.js"));
	}

	@Test
	@DisplayName("shouldScanFile delegates to isFileTypeSupported for non-excluded paths")
	void shouldScanFileDelegatesToSubclass() {
		service.supported = false;
		assertFalse(service.shouldScanFile("/repo/Main.java"));

		service.supported = true;
		assertTrue(service.shouldScanFile("/repo/Main.java"));
	}

	@Test
	@DisplayName("getConfig returns the same config instance passed to the constructor")
	void getConfigReturnsSameInstance() {
		ScannerConfig config = ScannerConfig.builder().engineName("TEST").build();
		TestScannerService withConfig = new TestScannerService(project, config);

		assertSame(config, withConfig.getConfig());
	}

	@Test
	@DisplayName("getTempSubFolderPath builds a path under the system temp directory")
	void getTempSubFolderPathBuildsUnderSystemTemp() {
		String path = service.callGetTempSubFolderPath("CxTestScanner");

		assertTrue(path.endsWith("CxTestScanner"));
		assertTrue(path.startsWith(System.getProperty("java.io.tmpdir")));
	}

	@Test
	@DisplayName("createTempFolder creates a missing directory, deleteTempFolder removes it")
	void createAndDeleteTempFolderRoundTrip() {
		tempDir = Path.of(System.getProperty("java.io.tmpdir"), "CxBaseScannerServiceTest-" + System.nanoTime());
		assertFalse(Files.exists(tempDir));

		service.callCreateTempFolder(tempDir);
		assertTrue(Files.exists(tempDir));
		assertTrue(Files.isDirectory(tempDir));

		service.callDeleteTempFolder(tempDir);
		assertFalse(Files.exists(tempDir));
	}

	@Test
	@DisplayName("deleteTempFolder on a path that doesn't exist is a safe no-op")
	void deleteTempFolderOnMissingPathIsNoOp() {
		Path missing = Path.of(System.getProperty("java.io.tmpdir"), "CxDoesNotExist-" + System.nanoTime());

		assertDoesNotThrow(() -> service.callDeleteTempFolder(missing));
	}

	@Test
	@DisplayName("close() (base implementation) completes without throwing")
	void closeDoesNotThrow() {
		assertDoesNotThrow(service::close);
	}
}
