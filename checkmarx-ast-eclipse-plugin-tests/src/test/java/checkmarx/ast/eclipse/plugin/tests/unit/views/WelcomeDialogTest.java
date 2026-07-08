package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.Assert.*;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.checkmarx.eclipse.views.ui.WelcomeDialog;

public class WelcomeDialogTest {

	private Display display;
	private Shell shell;

	static class FakeSettings implements WelcomeDialog.RealTimeSettingsManager {
		boolean all;
		boolean any;

		public FakeSettings() {
			this.all = false;
			this.any = false;
		}

		public FakeSettings(boolean all, boolean any) {
			this.all = all;
			this.any = any;
		}

		@Override
		public boolean areAllEnabled() {
			return all;
		}

		@Override
		public boolean areAnyEnabled() {
			return any;
		}

		@Override
		public void setAll(boolean enable) {
			this.all = enable;
			this.any = enable;
		}
	}

	@Before
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display);
	}

	@After
	public void tearDown() {
		if (shell != null && !shell.isDisposed()) {
			shell.dispose();
		}
	}

	@Test
	public void testWelcomeDialogCreation_McpDisabled() {
		WelcomeDialog dialog = new WelcomeDialog(shell, false, new FakeSettings());
		assertNotNull(dialog);
		Button checkbox = dialog.getRealTimeScannersCheckbox();
		// Checkbox should not be enabled when MCP is disabled
		if (checkbox != null) {
			assertFalse(checkbox.getEnabled());
		}
	}

	@Test
	public void testWelcomeDialogCreation_McpEnabled() {
		WelcomeDialog dialog = new WelcomeDialog(shell, true, new FakeSettings());
		assertNotNull(dialog);
	}

	@Test
	public void testRealTimeSettingsManager_DefaultImplementation() {
		WelcomeDialog dialog = new WelcomeDialog(shell, false);
		assertNotNull(dialog);
	}

	@Test
	public void testFakeSettings_AllEnabled() {
		FakeSettings settings = new FakeSettings(true, true);
		assertTrue(settings.areAllEnabled());
		assertTrue(settings.areAnyEnabled());
	}

	@Test
	public void testFakeSettings_AnyEnabled() {
		FakeSettings settings = new FakeSettings(false, true);
		assertFalse(settings.areAllEnabled());
		assertTrue(settings.areAnyEnabled());
	}

	@Test
	public void testFakeSettings_SetAll() {
		FakeSettings settings = new FakeSettings(false, false);
		assertFalse(settings.areAnyEnabled());
		settings.setAll(true);
		assertTrue(settings.areAllEnabled());
		assertTrue(settings.areAnyEnabled());
	}
}
