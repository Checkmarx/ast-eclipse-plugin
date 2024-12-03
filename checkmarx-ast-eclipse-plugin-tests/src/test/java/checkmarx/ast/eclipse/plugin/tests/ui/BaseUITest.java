package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.eclipse.swtbot.swt.finder.results.VoidResult;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.ui.PlatformUI;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.checkmarx.eclipse.utils.PluginConstants;

import checkmarx.ast.eclipse.plugin.tests.common.Environment;

public abstract class BaseUITest {

	private static final String INFO_SUCCESSFUL_CONNECTION = "Successfully authenticated to Checkmarx One server!";
	protected static final String ASSERT_FILTER_ACTIONS_IN_TOOLBAR = "All filter actions must be in the toolbar";
	protected static final String TAB_WINDOW = "Window";
	protected static final String ITEM_SHOW_VIEW = "Show View";
	protected static final String ITEM_PREFERENCES = "Preferences";
	protected static final String ITEM_OTHER = "Other...";
	protected static final String ITEM_CHECKMARX = "Checkmarx";
	protected static final String ITEM_CHECKMARX_AST = "Checkmarx One";
	protected static final String ITEM_CHECKMARX_AST_SCAN = "Checkmarx One Scan";

	protected static final String BTN_OPEN = "Open";
	protected static final String BTN_APPLY = "Apply";
	protected static final String BTN_TEST_CONNECTION = "Test Connection";
	protected static final String BTN_APPLY_AND_CLOSE = "Apply and Close";

	protected static final String VIEW_CHECKMARX_AST_SCAN = "Checkmarx One Scan";

	protected static SWTWorkbenchBot _bot;
	private static boolean eclipseProjectExist = false;

	protected static boolean _cxSettingsDefined = false;

	@BeforeClass
	public static void beforeClass() throws Exception {
		// Set preferences for the test environment
		SWTBotPreferences.KEYBOARD_LAYOUT = "EN_US";
		SWTBotPreferences.PLAYBACK_DELAY = 100;
		SWTBotPreferences.TIMEOUT = 20000; // Timeout increased to 20 seconds

		_bot = new SWTWorkbenchBot();

		if (!eclipseProjectExist) {
			createEclipseProject();
			eclipseProjectExist = true;
		}
	}

	@After
	public void tearDown() throws Exception {
		// Optional cleanup logic if required
	}

	@AfterClass
	public static void afterClass() {
		_bot.sleep(2000); // Stabilize teardown
	}

	protected static void sleep(long millis) {
		_bot.sleep(millis);
	}

	/**
	 * Ensures workbench is focused to avoid null widget errors in CI.
	 */
	protected static void preventWidgetWasNullInCIEnvironment() {
		UIThreadRunnable.syncExec(new VoidResult() {
			public void run() {
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().forceActive();
			}
		});
	}

	/**
	 * Waits until the Branch ComboBox is enabled.
	 */
	protected static void waitUntilBranchComboIsEnabled() throws TimeoutException {
		preventWidgetWasNullInCIEnvironment();

		int retryIdx = 0;

		while (!_bot.comboBox(1).isEnabled()) {
			System.out.println("Retry #" + retryIdx + ": ComboBox is not enabled yet.");
			if (retryIdx++ >= 10) {
				throw new TimeoutException("Branch ComboBox not enabled within 20000ms. Check environment or plugin state.");
			}
			_bot.sleep(2000); // Adjusted sleep duration
		}

		System.out.println("Branch ComboBox is enabled.");
	}

	/**
	 * Waits for a successful connection response from the Checkmarx server.
	 */
	protected static void waitForConnectionResponse() throws TimeoutException {
		int retryIdx = 0;

		while (!_bot.text(3).getText().equals(INFO_SUCCESSFUL_CONNECTION)) {
			System.out.println("Retry #" + retryIdx + ": Waiting for successful connection...");
			if (retryIdx++ >= 10) {
				throw new TimeoutException("Connection validation timeout after 20000ms.");
			}
			_bot.sleep(2000); // Adjusted sleep for better debugging
		}

		System.out.println("Connection successful!");
	}

	/**
	 * Waits while tree node equals a specific message. Fails after 10 retries.
	 */
	protected static void waitWhileTreeNodeEqualsTo(String nodeText) throws TimeoutException {
		int retryIdx = 0;

		while (_bot.tree().getAllItems()[0].getText().equals(nodeText)) {
			System.out.println("Retry #" + retryIdx + ": Tree node still equals to '" + nodeText + "'.");
			if (retryIdx++ >= 10) {
				throw new TimeoutException("Timeout after 20000ms. Expected tree node to change, but it didn't.");
			}
			_bot.sleep(2000);
		}

		System.out.println("Tree node updated successfully.");
	}

	/**
	 * Sets up the Checkmarx plugin: sets credentials, tests connection, and adds the plugin.
	 */
	protected void setUpCheckmarxPlugin(boolean ignoreWrongScanValidation) throws TimeoutException {
		testSuccessfulConnection(false);
		addCheckmarxPlugin(true);

		if (!ignoreWrongScanValidation) {
			validateInvalidScanID();
		}

		clearResultsView();
		typeValidScanID();

		assertTreeContainsScanID();
	}

	/**
	 * Validates connection with Checkmarx credentials.
	 */
	protected void testSuccessfulConnection(boolean openFromInitialPanel) throws TimeoutException {
		if (_cxSettingsDefined) return;

		if (!openFromInitialPanel) {
			openPreferences();
		}

		configureCredentials();
		waitForConnectionResponse();

		closePreferences();
		_cxSettingsDefined = true;
	}

	private void openPreferences() {
		_bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
		_bot.shell(ITEM_PREFERENCES).activate();
		_bot.tree().select(ITEM_CHECKMARX_AST);
	}

	private void configureCredentials() {
		_bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText(Environment.API_KEY);
		_bot.button(BTN_APPLY).click();
		_bot.button(BTN_TEST_CONNECTION).click();
	}

	private void closePreferences() {
		_bot.shell(ITEM_PREFERENCES).setFocus();
		_bot.button(BTN_APPLY_AND_CLOSE).click();
	}

	private void validateInvalidScanID() {
		_bot.comboBox(2).setText("invalid-scan-id");
		_bot.comboBox(2).pressShortcut(Keystrokes.LF);

		sleep(1000);
		assertEquals("The tree must contain one row with an error message", 1, _bot.tree(1).rowCount());
		assertEquals("Invalid Scan ID format message must be displayed",
				PluginConstants.TREE_INVALID_SCAN_ID_FORMAT, _bot.tree(1).cell(0, 0));
	}

	private void clearResultsView() {
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(PluginConstants.TOOLBAR_ACTION_CLEAR_RESULTS).click();
		sleep(1000);
	}

	private void typeValidScanID() throws TimeoutException {
		preventWidgetWasNullInCIEnvironment();

		_bot.comboBox(2).setText(Environment.SCAN_ID);
		_bot.comboBox(2).pressShortcut(Keystrokes.LF);

		waitUntilBranchComboIsEnabled();
	}

	private void assertTreeContainsScanID() {
		assertEquals("The tree must contain one row", 1, _bot.tree(1).rowCount());
		assertTrue("The plugin should have or should be retrieving results",
				_bot.tree(1).cell(0, 0).contains(Environment.SCAN_ID));
	}

	private static void createEclipseProject() {
		_bot.menu("File").menu("New").menu("Project...").click();
		SWTBotShell shell = _bot.shell("New Project");
		shell.activate();
		_bot.tree().select("Project");
		_bot.button("Next >").click();

		_bot.textWithLabel("Project name:").setText("MyFirstProject");
		_bot.button("Finish").click();
	}
}
