package checkmarx.ast.eclipse.plugin.tests.unit.devassist.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType;
import com.checkmarx.eclipse.devassist.basescanner.ScannerService;

/**
 * Unit tests for {@link ScannerRegistry}'s lazy-creation/caching/disposal
 * lifecycle. Each {@code ScannerType} maps to a real (but network-free at
 * construction time) scanner command wrapper - constructing one only stores
 * references and logs, matching the pattern already validated for each
 * scanner's own Command test in the scanners batch.
 */
class ScannerRegistryTest {

	private IProject project;
	private ScannerRegistry registry;

	@BeforeEach
	void setUp() {
		project = mock(IProject.class);
		when(project.getName()).thenReturn("TestProject");
		registry = new ScannerRegistry(project);
	}

	@Test
	@DisplayName("getProject returns the project the registry was created for")
	void getProjectReturnsProject() {
		assertSame(project, registry.getProject());
	}

	@Test
	@DisplayName("A freshly created registry is not disposed and has no registered scanners")
	void freshRegistryIsNotDisposed() {
		assertFalse(registry.isDisposed());
		for (ScannerType type : ScannerType.values()) {
			assertFalse(registry.hasScannerService(type));
		}
	}

	@Test
	@DisplayName("getScannerService lazily creates a scanner for every supported type")
	void getScannerServiceCreatesEveryType() {
		for (ScannerType type : ScannerType.values()) {
			Object scanner = registry.getScannerService(type);
			assertNotNull(scanner, "Expected a scanner instance for type: " + type);
			assertTrue(scanner instanceof ScannerService, "Scanner should implement ScannerService for: " + type);
			assertTrue(registry.hasScannerService(type));
		}
	}

	@Test
	@DisplayName("getScannerService returns the same cached instance on repeated calls")
	void getScannerServiceCachesInstance() {
		Object first = registry.getScannerService(ScannerType.OSS);
		Object second = registry.getScannerService(ScannerType.OSS);

		assertSame(first, second);
	}

	@Test
	@DisplayName("deregisterAllScanners clears all registered scanners and marks the registry disposed")
	void deregisterAllScannersClearsAndDisposes() {
		registry.getScannerService(ScannerType.OSS);
		registry.getScannerService(ScannerType.SECRETS);
		assertTrue(registry.hasScannerService(ScannerType.OSS));

		assertDoesNotThrow(registry::deregisterAllScanners);

		assertTrue(registry.isDisposed());
		assertFalse(registry.hasScannerService(ScannerType.OSS));
		assertFalse(registry.hasScannerService(ScannerType.SECRETS));
	}

	@Test
	@DisplayName("getScannerService returns null once the registry has been disposed")
	void getScannerServiceReturnsNullAfterDispose() {
		registry.deregisterAllScanners();

		Object scanner = registry.getScannerService(ScannerType.ASCA);

		assertNotNull(registry); // sanity: registry object itself still usable
		assertFalse(registry.hasScannerService(ScannerType.ASCA));
		org.junit.jupiter.api.Assertions.assertNull(scanner);
	}

	@Test
	@DisplayName("getStatistics reports the project name, scanner count and disposed flag")
	void getStatisticsReportsSummary() {
		registry.getScannerService(ScannerType.CONTAINERS);

		String stats = registry.getStatistics();

		assertTrue(stats.contains("TestProject"));
		assertTrue(stats.contains("Scanners: 1"));
		assertTrue(stats.contains("Disposed: false"));
	}
}
