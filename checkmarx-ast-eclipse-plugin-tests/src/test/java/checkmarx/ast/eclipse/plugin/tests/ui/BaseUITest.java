package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.eclipse.swtbot.swt.finder.results.VoidResult;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swt.widgets.Decorations;
import org.eclipse.swt.widgets.Tree;

import com.checkmarx.eclipse.utils.PluginConstants;

import checkmarx.ast.eclipse.plugin.tests.common.Environment;

public abstract class BaseUITest {

	private static final String INFO_SUCCESSFUL_CONNECTION = "Successfully authenticated to Checkmarx One server!";

	protected static final String ASSERT_FILTER_ACTIONS_IN_TOOLBAR = "All filter actions must be in the tool bar";

	protected static final String TAB_WINDOW = "Window";

	protected static final String ITEM_SHOW_VIEW = "Show View";
	protected static final String ITEM_PREFERENCES = "Preferences";
	protected static final String ITEM_OTHER = "Other...";
	protected static final String ITEM_CHECKMARX = "Checkmarx";
	protected static final String ITEM_CHECKMARX_AST = "Checkmarx One";
	protected static final String ITEM_CHECKMARX_AST_SCAN = "Checkmarx One Scan";

	protected static final String LABEL_SCAN_ID = "Scan Id:";

	protected static final String BTN_OPEN = "Open";
	protected static final String BTN_APPLY = "Apply";
	protected static final String BTN_TEST_CONNECTION = "Test Connection";
	protected static final String BTN_OK = "OK";
	protected static final String BTN_APPLY_AND_CLOSE = "Apply and Close";

	protected static final String SHELL_AUTHENTICATION = "Authentication";

	protected static final String VIEW_CHECKMARX_AST_SCAN = "Checkmarx One Scan";

	protected static SWTWorkbenchBot _bot;
	private static boolean eclipseProjectExist = false;

	protected static boolean _cxSettingsDefined = false;

	@BeforeAll
	public static void beforeClass() throws Exception {
		System.out.println("CX_SCAN_ID = '" + System.getenv("CX_SCAN_ID") + "'");
		System.out.println("CX_API_KEY = '" + System.getenv("CX_API_KEY") + "'");
		System.out.println("Environment.SCAN_ID = '" + Environment.SCAN_ID + "'");
		System.out.println("Environment.API_KEY = '" + Environment.API_KEY + "'");

		SWTBotPreferences.KEYBOARD_LAYOUT = "EN_US";
		SWTBotPreferences.PLAYBACK_DELAY = 500;
		SWTBotPreferences.TIMEOUT = 120000;

		_bot = new SWTWorkbenchBot();

		// Tycho headless stabilization
		for (int i = 0; i < 5; i++) {
			preventWidgetWasNullInCIEnvironment();
			_bot.sleep(1000);
		}

		closeIntroScreens();

		// Enable deterministic plugin behavior for SWTBot UI tests
		System.setProperty("com.checkmarx.eclipse.testmode", "true");

		// Create eclipse project if needed (only on non-CI local runs)
		if (!eclipseProjectExist && !isCIEnvironment()) {
			createEclipseProject();
			eclipseProjectExist = true;
		} else {
			eclipseProjectExist = true;
		}
	}

	private static void closeIntroScreens() {
		try { _bot.viewByTitle("Welcome").close(); } catch (Exception ignored) {}
		try { _bot.shell("Error").close(); } catch (Exception ignored) {}
	}

	protected static boolean isCIEnvironment() {
		return System.getProperty("CI") != null ||
			   System.getenv("GITHUB_ACTIONS") != null;
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@AfterAll
	public static void sleep() {
		_bot.sleep(2000);
	}

	protected static void sleep(long millis) {
		_bot.sleep(millis);
	}

	/**
	 * Used to get the workbench focus back and avoid "widget was null" error in CI
	 */
	protected static void preventWidgetWasNullInCIEnvironment() {
		UIThreadRunnable.syncExec(new VoidResult() {
			public void run() {
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().forceActive();
			}
		});
	}

	/**
	 * Set up checkmarx plugin:
	 *   -> Set credentials
	 *   -> Test connection
	 *   -> Add checkmarx plugin
	 *
	 * NOTE: On CI we still run the full setup — the early-return was removed because
	 * it prevented the Checkmarx view from ever opening, causing tree(1) to not exist.
	 */
	protected void setUpCheckmarxPlugin(boolean ignoreWrongScanValidation) throws TimeoutException {
		// Test Connection
		testSuccessfulConnection(false);

		// Add Checkmarx One Plugin (opens the view via Window > Show View menu)
		addCheckmarxPlugin(true);

		preventWidgetWasNullInCIEnvironment();

		if (!ignoreWrongScanValidation) {
			// Test incorrect Scan ID format
			_bot.comboBox(2).setText("invalid-scan-id");
			_bot.comboBox(2).pressShortcut(Keystrokes.LF);

			sleep(1000);

			assertEquals(1, getResultsTree().rowCount(), "The tree must contain one row with an error message");
			assertEquals(PluginConstants.TREE_INVALID_SCAN_ID_FORMAT, getResultsTree().cell(0, 0));
		}

		// Clear the view before getting the scan id
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(PluginConstants.TOOLBAR_ACTION_CLEAR_RESULTS).click();

		sleep(1000);

		// Type a valid and existing Scan ID
		typeValidScanID();

		// Wait for tree to show scan ID (retry for up to 30 seconds)
		int retryIdx = 0;
		boolean retrievingOrRetrievedResults = false;
		while (retryIdx < 20) {
			if (getResultsTree().rowCount() >= 1 && getResultsTree().cell(0, 0).contains(Environment.SCAN_ID)) {
				retrievingOrRetrievedResults = true;
				break;
			}
			sleep(1500);
			retryIdx++;
		}

		assertEquals(1, getResultsTree().rowCount(), "The tree must contain one row");
		assertTrue(retrievingOrRetrievedResults, "The plugin should have or should be retrieving results");

		waitWhileTreeNodeEqualsTo(String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, Environment.SCAN_ID));

		assertTrue(getResultsTree().cell(0, 0).startsWith(Environment.SCAN_ID), "The plugin should retrieve results");
	}

	/**
	 * Test successful connection
	 */
	protected void testSuccessfulConnection(boolean openFromInitialPanel) throws TimeoutException {
		preventWidgetWasNullInCIEnvironment();

		if (_cxSettingsDefined) return;

		if (!openFromInitialPanel) {
			_bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
			_bot.shell(ITEM_PREFERENCES).activate();
			_bot.tree().select(ITEM_CHECKMARX_AST);
		}

		_bot.sleep(1000);

		_bot.shell(ITEM_PREFERENCES).setFocus();

		_bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText(Environment.API_KEY);

		_bot.button(BTN_APPLY).click();
		_bot.button(BTN_TEST_CONNECTION).click();

		waitForConnectionResponse();

		_bot.shell(ITEM_PREFERENCES).setFocus();
		_bot.button(BTN_APPLY_AND_CLOSE).click();

		_cxSettingsDefined = true;
	}

	/**
	 * Add Checkmarx plugin via Window > Show View menu.
	 * This is what causes tree(1) to appear in the UI.
	 */
	protected void addCheckmarxPlugin(boolean waitUntilPluginEnable) throws TimeoutException {
		stabilizeBase();
		preventWidgetWasNullInCIEnvironment();

		_bot.menu(TAB_WINDOW).menu(ITEM_SHOW_VIEW).menu(ITEM_OTHER).click();
		sleep(2000);
		_bot.shell(ITEM_SHOW_VIEW).activate();
		sleep(1000);
		_bot.tree().expandNode(ITEM_CHECKMARX).select(ITEM_CHECKMARX_AST_SCAN);
		_bot.button(BTN_OPEN).click();

		if (waitUntilPluginEnable) {
			waitUntilBranchComboIsEnabled();
		}
	}

	/**
	 * Wait while tree node equals a specific message. Fails after 20 retries.
	 */
	protected void waitWhileTreeNodeEqualsTo(String nodeText) throws TimeoutException {
	    int retryIdx = 0;

	    while (true) {
	        SWTBotTree tree = getResultsTree();

	        if (tree.rowCount() == 0) {
	            break;
	        }

	        String currentText = tree.getAllItems()[0].getText();

	        if (!currentText.equals(nodeText)) {
	            break;
	        }

	        if (retryIdx == 20) {
	            throw new TimeoutException("Timeout after waiting. Scan results should be retrieved");
	        }

	        sleep(1500);
	        retryIdx++;
	    }
	}

	/**
	 * Wait until branch combobox is enabled
	 */
	protected static void waitUntilBranchComboIsEnabled() throws TimeoutException {
		stabilizeBase();
		preventWidgetWasNullInCIEnvironment();

		boolean emptyScanId = _bot.comboBox(2).getText().isEmpty()
				|| _bot.comboBox(2).getText().equals(PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
		boolean projectNotSelected = _bot.comboBox(0).getText().isEmpty()
				|| _bot.comboBox(0).getText().equals("Select a project");

		if (emptyScanId || projectNotSelected) {
			return;
		}

		int retryIdx = 0;

		while (!_bot.comboBox(1).isEnabled()) {
			if (retryIdx == 15) {
				break;
			}
			_bot.sleep(8000);
			retryIdx++;
		}

		if (retryIdx == 15) {
			emptyScanId = _bot.comboBox(2).getText().isEmpty()
					|| _bot.comboBox(2).getText().equals(PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
			projectNotSelected = _bot.comboBox(0).getText().isEmpty()
					|| _bot.comboBox(0).getText().equals("Select a project");

			if (emptyScanId || projectNotSelected) {
				return;
			}

			throw new TimeoutException("Timeout after waiting. Branches combobox must be enabled");
		}
	}

	/**
	 * Wait for connection response
	 */
	protected static void waitForConnectionResponse() throws TimeoutException {
		boolean found = false;
		int retryIdx = 0;
		//check for the success message in the shell's decorations (where info messages are shown in Eclipse)
		while (!found) {			
			for (var text : _bot.text()) {
	            if (((Decorations) text).getText().contains(INFO_SUCCESSFUL_CONNECTION)) {
	            	found=true;
	            }
	        }
		    if (found) break;
		    if (retryIdx == 10) break;
		    _bot.sleep(1000);
		    retryIdx++;
		}
		if (!found) {
		    throw new TimeoutException("Connection validation timeout after 10000ms.");
		}
	}

	/**
	 * Type a valid Scan ID to get results
	 */
	private void typeValidScanID() throws TimeoutException {
		preventWidgetWasNullInCIEnvironment();
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).setFocus();
		sleep(2000);

		_bot.comboBox(2).setText(Environment.SCAN_ID);
		_bot.comboBox(2).pressShortcut(Keystrokes.LF);

		waitUntilBranchComboIsEnabled();
	}

	/**
	 * Create an Eclipse project (local non-CI runs only)
	 */
	protected static void createEclipseProject() {
		try {
			waitForJobs();
			sleep(3000);

			if (_bot.menu("File").isEnabled()) {
				_bot.menu("File").menu("New").menu("Project...").click();
				SWTBotShell shell = _bot.shell("New Project");
				shell.activate();
				_bot.tree().select("Project");
				_bot.button("Next >").click();
				_bot.textWithLabel("Project name:").setText("MyFirstProject");
				_bot.button("Finish").click();

				_bot.menu("File").menu("New").menu("File").click();
				_bot.textWithLabel("File name:").setText("Dockerfile");
				_bot.tree().select(0);
				_bot.button("Finish").click();
			}
		} catch (WidgetNotFoundException e) {
			System.out.println("CI: Skipping project creation (expected): " + e.getMessage());
		}
	}

	protected static void waitForJobs() {
		_bot.sleep(3000);
	}

	protected static void stabilizeBase() {
		_bot.sleep(5000);
		try {
			_bot.activeShell().activate();
			UIThreadRunnable.syncExec(new VoidResult() {
				public void run() {
					PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().forceActive();
				}
			});
		} catch (Exception ignored) {}
		_bot.sleep(3000);
	}
	
	protected SWTBotTree getResultsTree() {
	    try {
	        return _bot.tree(1);
	    } catch (IndexOutOfBoundsException e) {
	        return _bot.tree(0);
	    }
	}
}