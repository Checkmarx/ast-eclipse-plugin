package checkmarx.ast.eclipse.plugin.tests.unit.devassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.backend.GlobalScannerController;
import com.checkmarx.eclipse.devassist.backend.GlobalScannerController.ScannerStateListener;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType;

/**
 * Unit tests for {@link GlobalScannerController}. It is a JVM-wide singleton,
 * so every test resets its internal state map/listener list via reflection
 * in {@code @BeforeEach} to avoid bleeding state across tests (and across
 * other test classes in this module, e.g. {@code ScannerFactoryTest}, that
 * also go through {@code getInstance()}).
 */
class GlobalScannerControllerTest {

	private GlobalScannerController controller;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void resetSingletonState() throws Exception {
		controller = GlobalScannerController.getInstance();

		Field stateField = GlobalScannerController.class.getDeclaredField("scannerState");
		stateField.setAccessible(true);
		((Map<ScannerType, Boolean>) stateField.get(controller)).clear();

		Field listenersField = GlobalScannerController.class.getDeclaredField("stateListeners");
		listenersField.setAccessible(true);
		((List<ScannerStateListener>) listenersField.get(controller)).clear();
	}

	@Test
	@DisplayName("isScannerEnabled defaults to true for a type that was never explicitly set")
	void isScannerEnabledDefaultsToTrue() {
		assertTrue(controller.isScannerEnabled(ScannerType.OSS));
	}

	@Test
	@DisplayName("isScannerEnabled returns false for a null type")
	void isScannerEnabledHandlesNullType() {
		assertFalse(controller.isScannerEnabled(null));
	}

	@Test
	@DisplayName("disableScanner then isScannerEnabled reflects the disabled state")
	void disableScannerThenIsScannerEnabled() {
		controller.disableScanner(ScannerType.SECRETS);
		assertFalse(controller.isScannerEnabled(ScannerType.SECRETS));

		controller.enableScanner(ScannerType.SECRETS);
		assertTrue(controller.isScannerEnabled(ScannerType.SECRETS));
	}

	@Test
	@DisplayName("enableScanner/disableScanner with a null type is a safe no-op")
	void enableDisableHandleNullType() {
		controller.enableScanner(null);
		controller.disableScanner(null);
		// No exception, and no scanner type is affected.
		assertEquals(ScannerType.values().length, controller.getEnabledScannerCount());
	}

	@Test
	@DisplayName("disableAllScanners then enableAllScanners toggles every scanner type")
	void disableThenEnableAllScanners() {
		controller.disableAllScanners();
		assertEquals(0, controller.getEnabledScannerCount());
		for (ScannerType type : ScannerType.values()) {
			assertFalse(controller.isScannerEnabled(type));
		}

		controller.enableAllScanners();
		assertEquals(ScannerType.values().length, controller.getEnabledScannerCount());
	}

	@Test
	@DisplayName("Listener is notified only on an actual state transition, not on a redundant call")
	void listenerNotifiedOnlyOnRealTransition() {
		// Note: wasEnabled/wasDisabled are computed from the map's PREVIOUS explicit
		// value, not from isScannerEnabled()'s default-true fallback - so after the
		// @BeforeEach map .clear(), the type has no explicit entry yet. Prime one
		// with an explicit enableScanner() call (itself not guaranteed to notify)
		// before attaching the listener, so the subsequent disable really is a
		// transition from a known "true" state.
		controller.enableScanner(ScannerType.IAC);
		List<Boolean> notifications = new ArrayList<>();
		ScannerStateListener listener = (type, enabled) -> notifications.add(enabled);
		controller.addScannerStateListener(listener);

		controller.disableScanner(ScannerType.IAC);
		controller.disableScanner(ScannerType.IAC);
		controller.enableScanner(ScannerType.IAC);
		controller.enableScanner(ScannerType.IAC);

		assertEquals(List.of(false, true), notifications);
	}

	@Test
	@DisplayName("removeScannerStateListener stops further notifications")
	void removeScannerStateListenerStopsNotifications() {
		controller.enableScanner(ScannerType.ASCA);
		List<Boolean> notifications = new ArrayList<>();
		ScannerStateListener listener = (type, enabled) -> notifications.add(enabled);
		controller.addScannerStateListener(listener);
		controller.removeScannerStateListener(listener);

		controller.disableScanner(ScannerType.ASCA);

		assertTrue(notifications.isEmpty());
	}

	@Test
	@DisplayName("A listener that throws does not prevent other listeners from being notified")
	void listenerExceptionDoesNotBlockOtherListeners() {
		controller.enableScanner(ScannerType.CONTAINERS);
		List<Boolean> notifications = new ArrayList<>();
		controller.addScannerStateListener((type, enabled) -> {
			throw new RuntimeException("boom");
		});
		controller.addScannerStateListener((type, enabled) -> notifications.add(enabled));

		controller.disableScanner(ScannerType.CONTAINERS);

		assertEquals(List.of(false), notifications);
	}

	@Test
	@DisplayName("getStateReport lists every scanner type with its enabled/disabled state")
	void getStateReportListsAllTypes() {
		controller.disableScanner(ScannerType.OSS);

		String report = controller.getStateReport();

		assertTrue(report.contains("DISABLED"));
		assertTrue(report.contains("ENABLED"));
		for (ScannerType type : ScannerType.values()) {
			assertTrue(report.contains(type.getDisplayName()));
		}
	}
}
