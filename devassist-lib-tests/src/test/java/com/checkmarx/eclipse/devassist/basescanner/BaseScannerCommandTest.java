package com.checkmarx.eclipse.devassist.basescanner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.common.ScannerConfig;
import com.checkmarx.eclipse.devassist.model.ScanEngine;

/**
 * Unit tests for {@link BaseScannerCommand} using a minimal concrete
 * subclass, exercising the register/deregister lifecycle directly rather
 * than only indirectly through each scanner's own Command subclass.
 */
class BaseScannerCommandTest {

	private static class TestScannerCommand extends BaseScannerCommand {
		int initializeCount = 0;

		TestScannerCommand(IProject project, ScannerConfig config) {
			super(project, config);
		}

		@Override
		public void initializeScanner() {
			initializeCount++;
		}

		ScanEngine callGetScannerType() {
			return getScannerType();
		}
	}

	private IProject project;

	@BeforeEach
	void setUp() {
		project = mock(IProject.class);
		when(project.getName()).thenReturn("TestProject");
	}

	private ScannerConfig configFor(String engineName) {
		return ScannerConfig.builder().engineName(engineName).enabledMessage("started")
				.disabledMessage("disabled").build();
	}

	@Test
	@DisplayName("register() initializes the scanner when the config has a valid engine name")
	void registerInitializesWhenConfigValid() {
		TestScannerCommand command = new TestScannerCommand(project, configFor("OSS"));

		command.register(project);

		assertEquals(1, command.initializeCount);
	}

	@Test
	@DisplayName("register() does nothing when the config is null (scanner considered inactive)")
	void registerDoesNothingWhenConfigNull() {
		TestScannerCommand command = new TestScannerCommand(project, null);

		command.register(project);

		assertEquals(0, command.initializeCount);
	}

	@Test
	@DisplayName("register() does nothing when the config's engine name is null")
	void registerDoesNothingWhenEngineNameNull() {
		TestScannerCommand command = new TestScannerCommand(project, ScannerConfig.builder().build());

		command.register(project);

		assertEquals(0, command.initializeCount);
	}

	@Test
	@DisplayName("Calling register() again while already registered does not re-initialize")
	void registerIsIdempotentWhileAlreadyRegistered() {
		TestScannerCommand command = new TestScannerCommand(project, configFor("OSS"));

		command.register(project);
		command.register(project);

		assertEquals(1, command.initializeCount);
	}

	@Test
	@DisplayName("deregister() then register() again re-initializes the scanner")
	void deregisterThenRegisterReinitializes() {
		TestScannerCommand command = new TestScannerCommand(project, configFor("OSS"));

		command.register(project);
		command.deregister(project);
		command.register(project);

		assertEquals(2, command.initializeCount);
	}

	@Test
	@DisplayName("deregister() on a never-registered command is a safe no-op")
	void deregisterWithoutRegisterIsNoOp() {
		TestScannerCommand command = new TestScannerCommand(project, configFor("OSS"));

		assertDoesNotThrow(() -> command.deregister(project));
		assertEquals(0, command.initializeCount);
	}

	@Test
	@DisplayName("getScannerType() resolves the ScanEngine matching the config's engine name")
	void getScannerTypeResolvesEngine() {
		TestScannerCommand command = new TestScannerCommand(project, configFor("asca"));

		assertEquals(ScanEngine.ASCA, command.callGetScannerType());
	}

	@Test
	@DisplayName("getConfig() returns the same config instance passed to the constructor")
	void getConfigReturnsSameInstance() {
		ScannerConfig config = configFor("OSS");
		TestScannerCommand command = new TestScannerCommand(project, config);

		assertSame(config, command.getConfig());
	}

	@Test
	@DisplayName("dispose() (base implementation) completes without throwing")
	void disposeDoesNotThrow() {
		TestScannerCommand command = new TestScannerCommand(project, configFor("OSS"));

		assertDoesNotThrow(command::dispose);
	}
}
