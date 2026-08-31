package com.checkmarx.eclipse.devassist.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.backend.GlobalScannerController;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType;
import com.checkmarx.eclipse.devassist.basescanner.ScannerService;

/**
 * Unit tests for {@link ScannerFactory}. {@link ScannerRegistry} is mocked
 * (its own lifecycle/lazy-creation behavior is covered by
 * {@code ScannerRegistryTest}) so this focuses purely on the
 * global-enabled + file-type-support filtering logic. Resets the
 * {@link GlobalScannerController} JVM-wide singleton before each test - see
 * {@code GlobalScannerControllerTest} for why.
 */
class ScannerFactoryTest {

	private ScannerRegistry registry;
	private ScannerFactory factory;

	private static class FakeScannerService implements ScannerService<Object> {
		private final boolean shouldScan;

		FakeScannerService(boolean shouldScan) {
			this.shouldScan = shouldScan;
		}

		@Override
		public boolean shouldScanFile(String filePath) {
			return shouldScan;
		}

		@Override
		public ScanResult<Object> scan(String filePath) {
			return null;
		}

		@Override
		public ScannerConfig getConfig() {
			return null;
		}

		@Override
		public void close() throws Exception {
			// no-op
		}
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws Exception {
		GlobalScannerController controller = GlobalScannerController.getInstance();
		Field stateField = GlobalScannerController.class.getDeclaredField("scannerState");
		stateField.setAccessible(true);
		((Map<ScannerType, Boolean>) stateField.get(controller)).clear();

		registry = mock(ScannerRegistry.class);
		factory = new ScannerFactory(registry);
	}

	@Test
	@DisplayName("getAllSupportedScanners returns only scanners that are enabled and support the file")
	void getAllSupportedScannersFiltersByEnabledAndSupport() {
		FakeScannerService ossScanner = new FakeScannerService(true);
		when(registry.getScannerService(ScannerType.OSS)).thenReturn(ossScanner);
		// Every other type: registry has nothing registered (returns null)

		List<ScannerService<?>> supported = factory.getAllSupportedScanners("/repo/pom.xml");

		assertEquals(1, supported.size());
		assertSame(ossScanner, supported.get(0));
	}

	@Test
	@DisplayName("getAllSupportedScanners excludes a scanner disabled globally, even if it supports the file")
	void getAllSupportedScannersExcludesGloballyDisabled() {
		FakeScannerService ossScanner = new FakeScannerService(true);
		when(registry.getScannerService(ScannerType.OSS)).thenReturn(ossScanner);
		GlobalScannerController.getInstance().disableScanner(ScannerType.OSS);

		List<ScannerService<?>> supported = factory.getAllSupportedScanners("/repo/pom.xml");

		assertTrue(supported.isEmpty());
	}

	@Test
	@DisplayName("getAllSupportedScanners excludes a scanner that does not support the file type")
	void getAllSupportedScannersExcludesUnsupportedFileType() {
		FakeScannerService ossScanner = new FakeScannerService(false);
		when(registry.getScannerService(ScannerType.OSS)).thenReturn(ossScanner);

		List<ScannerService<?>> supported = factory.getAllSupportedScanners("/repo/Main.java");

		assertTrue(supported.isEmpty());
	}

	@Test
	@DisplayName("getAllSupportedScanners skips a registry entry that is not a ScannerService instance")
	void getAllSupportedScannersSkipsNonScannerServiceObjects() {
		when(registry.getScannerService(ScannerType.OSS)).thenReturn(new Object());

		List<ScannerService<?>> supported = factory.getAllSupportedScanners("/repo/pom.xml");

		assertTrue(supported.isEmpty());
	}

	@Test
	@DisplayName("getAllSupportedScanners tolerates the registry throwing for a given type")
	void getAllSupportedScannersTolerantOfRegistryException() {
		when(registry.getScannerService(ScannerType.OSS)).thenThrow(new RuntimeException("boom"));
		FakeScannerService secretsScanner = new FakeScannerService(true);
		when(registry.getScannerService(ScannerType.SECRETS)).thenReturn(secretsScanner);

		List<ScannerService<?>> supported = factory.getAllSupportedScanners("/repo/config.properties");

		assertEquals(1, supported.size());
		assertSame(secretsScanner, supported.get(0));
	}

	@Test
	@DisplayName("getScannerForFile returns null for a null file path or scanner type")
	void getScannerForFileHandlesNullInputs() {
		assertNull(factory.getScannerForFile(null, ScannerType.OSS));
		assertNull(factory.getScannerForFile("/repo/pom.xml", null));
	}

	@Test
	@DisplayName("getScannerForFile returns null when the scanner type is disabled globally")
	void getScannerForFileReturnsNullWhenDisabled() {
		GlobalScannerController.getInstance().disableScanner(ScannerType.SECRETS);
		when(registry.getScannerService(ScannerType.SECRETS)).thenReturn(new FakeScannerService(true));

		assertNull(factory.getScannerForFile("/repo/config.properties", ScannerType.SECRETS));
	}

	@Test
	@DisplayName("getScannerForFile returns null when the scanner does not support the file")
	void getScannerForFileReturnsNullWhenUnsupported() {
		when(registry.getScannerService(ScannerType.SECRETS)).thenReturn(new FakeScannerService(false));

		assertNull(factory.getScannerForFile("/repo/config.properties", ScannerType.SECRETS));
	}

	@Test
	@DisplayName("getScannerForFile returns the scanner when enabled, registered and supporting the file")
	void getScannerForFileReturnsScannerWhenEligible() {
		FakeScannerService secretsScanner = new FakeScannerService(true);
		when(registry.getScannerService(ScannerType.SECRETS)).thenReturn(secretsScanner);

		assertSame(secretsScanner, factory.getScannerForFile("/repo/config.properties", ScannerType.SECRETS));
	}

	@Test
	@DisplayName("getStatistics reports the enabled scanner count out of the total scanner type count")
	void getStatisticsReportsEnabledCount() {
		GlobalScannerController.getInstance().disableScanner(ScannerType.IAC);

		String stats = factory.getStatistics();

		assertNotNull(stats);
		assertTrue(stats.contains((ScannerType.values().length - 1) + "/" + ScannerType.values().length));
	}
}
