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
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		// Needed to set CI environment keyboard layout
		SWTBotPreferences.KEYBOARD_LAYOUT = "EN_US"; 

		// Used to decrease tests velocity
		SWTBotPreferences.PLAYBACK_DELAY = 100;
		
		SWTBotPreferences.TIMEOUT = 5000;

		_bot = new SWTWorkbenchBot();
				
		if(!eclipseProjectExist) {
			createEclipseProject();
			eclipseProjectExist = true;
		}
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@AfterClass
	public static void sleep() {
		_bot.sleep(2000);
	}
	
	
	protected static void sleep(long millis) {
		_bot.sleep(millis);
	}
	
	/**
	 * Used to get the workbench focus back and avoid "widget was null" error message in the CI environment
	 */
	protected static void preventWidgetWasNullInCIEnvironment() {
		UIThreadRunnable.syncExec(new VoidResult() {
			public void run() {
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().forceActive();
			}
		});
	}
	
	/**
	 * Set up checkmarx plugin
	 * 
	 * 		-> Set credentials
	 * 		-> Test connection
	 * 		-> Add checkmarx plugin
	 * 
	 * @throws TimeoutException
	 */
	protected void setUpCheckmarxPlugin(boolean ignoreWrongScanValidation) throws TimeoutException {
		// Test Connection
		testSuccessfulConnection(false);

		// Add Checkmarx One Plugin
		addCheckmarxPlugin(true);
		
		preventWidgetWasNullInCIEnvironment();
		
		if(!ignoreWrongScanValidation) {
			// Test incorrect Scan ID format
			_bot.comboBox(2).setText("invalid-scan-id");
			_bot.comboBox(2).pressShortcut(Keystrokes.LF);

			sleep(1000);

			assertEquals("The tree must contain one row with an error message", _bot.tree(1).rowCount(), 1);
			assertEquals("An incorrect scanId format message must be displayed", PluginConstants.TREE_INVALID_SCAN_ID_FORMAT, _bot.tree(1).cell(0, 0));
		}
		
		// clear the view before getting the scan id
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(PluginConstants.TOOLBAR_ACTION_CLEAR_RESULTS).click();
		
		sleep(1000);
		
		// type a valid and existing Scan ID
		typeValidScanID();

		assertEquals("The tree must contain one row", _bot.tree().rowCount(), 1);		
		boolean retrievingOrRetrievedResults = _bot.tree(1).cell(0, 0).contains(Environment.SCAN_ID);
		assertTrue("The plugin should have or should be retrieving results", retrievingOrRetrievedResults);

		waitWhileTreeNodeEqualsTo(String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, Environment.SCAN_ID));
		
		assertTrue("The plugin should retrieve results", _bot.tree(1).cell(0, 0).startsWith(Environment.SCAN_ID));
	}

	/**
	 * Test successful connection
	 * 
	 * @throws TimeoutException
	 */
	protected void testSuccessfulConnection(boolean openFromInitialPanel) throws TimeoutException {
		preventWidgetWasNullInCIEnvironment();
		
		if(_cxSettingsDefined) return;
		
		if(!openFromInitialPanel) {
			_bot.menu("Eclipse").menu(ITEM_PREFERENCES).click();
			_bot.shell(ITEM_PREFERENCES).activate();
			_bot.tree().select(ITEM_CHECKMARX_AST);
		}

		_bot.sleep(1000);

		_bot.shell(ITEM_PREFERENCES).setFocus(); // Need to set focus to avoid failing in CI environment
		
		_bot.textWithLabel(PluginConstants.PREFERENCES_API_KEY).setText(Environment.API_KEY);

		_bot.button(BTN_APPLY).click();
		_bot.button(BTN_TEST_CONNECTION).click();
		
		//Do waitUntil Method to get text from text(6)
		waitForConnectionResponse();
			
		_bot.shell(ITEM_PREFERENCES).setFocus(); // Need to set focus to avoid failing in CI environment
		_bot.button(BTN_APPLY_AND_CLOSE).click();

		_cxSettingsDefined = true;
	}
	

	/**
	 * Add Checkmarx plugin in the show view perspective
	 * 
	 * @throws TimeoutException 
	 */
	protected void addCheckmarxPlugin(boolean waitUntilPluginEnable) throws TimeoutException {
		preventWidgetWasNullInCIEnvironment();
		
		_bot.menu(TAB_WINDOW).menu(ITEM_SHOW_VIEW).menu(ITEM_OTHER).click();
		_bot.shell(ITEM_SHOW_VIEW).activate();
		_bot.tree().expandNode(ITEM_CHECKMARX).select(ITEM_CHECKMARX_AST_SCAN);
		_bot.button(BTN_OPEN).click();
		
		if(waitUntilPluginEnable) {
			waitUntilBranchComboIsEnabled();	
		}
	}
	
	/**
	 * Wait while tree node equals to a a specific message. Fails after 10 retries
	 * 
	 * @param nodeText
	 * @throws TimeoutException
	 */
	protected static void waitWhileTreeNodeEqualsTo(String nodeText) throws TimeoutException {
		int retryIdx = 0;

		while (_bot.tree().getAllItems()[0].getText().equals(nodeText)) {

			if (retryIdx == 10) {
				break;
			}

			_bot.sleep(1000);

			retryIdx++;
		}

		if (retryIdx == 10) {
			throw new TimeoutException("Timeout after 5000ms. Scan results should be retrieved");
		}
	}
	
	/**
	 * Wait until branch combobox is enabled
	 * 
	 * @throws TimeoutException
	 */
	protected static void waitUntilBranchComboIsEnabled() throws TimeoutException {
		preventWidgetWasNullInCIEnvironment();
		
		boolean emptyScanId = _bot.comboBox(2).getText().isEmpty() || _bot.comboBox(2).getText().equals(PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
		boolean projectNotSelected =_bot.comboBox(0).getText().isEmpty() || _bot.comboBox(0).getText().equals("Select a project");
		
		if(emptyScanId || projectNotSelected) {
			return;
		}
		
		int retryIdx = 0;

		while (!_bot.comboBox(1).isEnabled()) {

			if (retryIdx == 10) {
				break;
			}

			_bot.sleep(5000);

			retryIdx++;
		}

		if (retryIdx == 10) {
			emptyScanId = _bot.comboBox(2).getText().isEmpty() || _bot.comboBox(2).getText().equals(PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
			projectNotSelected = _bot.comboBox(0).getText().isEmpty() || _bot.comboBox(0).getText().equals("Select a project");
			
			if(emptyScanId || projectNotSelected) {
				return;
			}
			
			throw new TimeoutException("Timeout after 5000ms. Branches' combobox must be enabled");
		}
	}
	
	/**
	 * Wait while tree node equals to a a specific message. Fails after 10 retries
	 * 
	 * @param nodeText
	 * @throws TimeoutException
	 */
	protected static void waitForConnectionResponse() throws TimeoutException {
		int retryIdx = 0;
		while (!_bot.text(3).getText().equals(INFO_SUCCESSFUL_CONNECTION)) {
			if (retryIdx == 10) {
				break;
			}

			_bot.sleep(1000);
			retryIdx++;
		}

		if (retryIdx == 10) {
			throw new TimeoutException("Connection validation timeout after 10000ms.");
		}
	}
	

	/**
	 * Type a valid Scan ID to get results
	 * 
	 * @throws TimeoutException 
	 */
	private void typeValidScanID() throws TimeoutException {
		preventWidgetWasNullInCIEnvironment();
		
		_bot.comboBox(2).setText(Environment.SCAN_ID);
		_bot.comboBox(2).pressShortcut(Keystrokes.LF);
		
		waitUntilBranchComboIsEnabled();
	}
	
	/**
	 * Create a eclipse project
	 */
	private static void createEclipseProject() {
		_bot.menu("File").menu("New").menu("Project...").click();
		SWTBotShell shell = _bot.shell("New Project");
		shell.activate();
		_bot.tree().select("Java Project");
		_bot.button("Next >").click();
		
 
		_bot.textWithLabel("Project name:").setText("MyFirstProject");
		_bot.button("Finish").click();
		
		_bot.menu("File").menu("New").menu("File").click();
		_bot.textWithLabel("File name:").setText("Dockerfile");
		_bot.tree().select(0);
		_bot.button("Finish").click();
		_bot.button("Cancel").click();
	}
}
