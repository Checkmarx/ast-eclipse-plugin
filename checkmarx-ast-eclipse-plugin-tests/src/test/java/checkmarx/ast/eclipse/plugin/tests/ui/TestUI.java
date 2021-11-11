package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.eclipse.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.junit.Test;
import org.junit.runner.RunWith;

import checkmarx.ast.eclipse.plugin.tests.common.Environment;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestUI extends BaseUITest {

	private static final String ERROR_INCORRECT_SCAN_ID_FORMAT = "Incorrect scanId format.";
	private static final String ERROR_SERVER_URL_NOT_SET = "Error: Checkmarx server URL is not set";

	private static final String INFO_SCAN_RETRIVING_RESULTS = "Retrieving the results for the scan id: " + Environment.SCAN_ID + " .";
	private static final String INFO_TYPE_SCAN_TO_GET_RESULTS = "Paste a scanId and hit play to fetch the results.";

	private static final String INFO_SUCCESSFUL_CONNECTION = "Connection successfull !";

	private static boolean _cxSettingsDefined = false;

	@Test
	public void testSuccessfulConnetion() {
		testSuccessfulConnection();
	}

	@Test
	public void testAddCheckmarxASTPlugin() {
				
		// Add Checkmarx plugin to the eclipse view
		addCheckmarxPlugin();

		// Assert that active view is the Checkmarx AST Scan
		assertTrue("Active view must be the Checkmarx AST Scan", _bot.activeView().getTitle().equals(VIEW_CHECKMARX_AST_SCAN));
		
		// Close Checkmarx AST Scan view
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
	}

	@Test
	public void testMissingSetCheckmarxServerUrl() {

		// Clear Checkmarx credentials to expect missing Server Url
		clearCheckmarxCredentials();

		// Add Checkmarx plugin to the eclipse perspective view
		addCheckmarxPlugin();

		// Type a valid and existing scan id
		typeValidScanID();

		assertEquals("The tree must contain a single row", _bot.tree().rowCount(), 1);

		String firstTreeCell = _bot.tree().cell(0, COLUMN_TITLE);

		// The first row must have a message saying that AST is getting results or failing due the missing Server Url
		boolean expectedResult = firstTreeCell.equals(INFO_SCAN_RETRIVING_RESULTS) || firstTreeCell.equals(ERROR_SERVER_URL_NOT_SET);
		assertTrue("Plugin should be retrieving results or failed due Server Url not set", expectedResult);

		sleep();

		// After a sleep the missing Server Url message must be displayed
		assertEquals("", ERROR_SERVER_URL_NOT_SET, _bot.tree().cell(0, COLUMN_TITLE));
		
		// Close Checkmarx AST Scan view
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
	}

	/**
	 * Test UI End-to-End
	 * 
	 * 		-> Set Checkmarx credentials and test connection 
	 * 		-> Add Checkmarx plugin to the show view perspective 
	 * 		-> Type and assert invalid scan id 
	 * 		-> Type and assert valid scan id 
	 * 		-> Expand scan results
	 * 
	 * @throws TimeoutException
	 */
	@Test
	public void testEnd2End() throws TimeoutException {

		// Test Connection
		testSuccessfulConnection();

		// Add Checkmarx AST Plugin
		addCheckmarxPlugin();
		
		preventWidgetWasNullInCIEnvironment();

		assertEquals("The tree must contain one row with an info message", _bot.tree().rowCount(), 1);
		assertEquals("", INFO_TYPE_SCAN_TO_GET_RESULTS, _bot.tree().cell(0, COLUMN_TITLE));

		// Test incorrect Scan ID format
		_bot.textWithLabel(LABEL_SCAN_ID).setText("invalid-scan-id");
		_bot.textWithLabel(LABEL_SCAN_ID).pressShortcut(Keystrokes.LF);

		sleep(1000);

		assertEquals("The tree must contain one row with an error message", _bot.tree().rowCount(), 1);
		assertEquals("An incorrect scanId format message must be displayed", ERROR_INCORRECT_SCAN_ID_FORMAT, _bot.tree().cell(0, COLUMN_TITLE));

		sleep(1000);

		// type a valid and existing Scan ID
		typeValidScanID();

		assertEquals("The tree must contain one row", _bot.tree().rowCount(), 1);		
		assertEquals("The plugin should be retrieving results", INFO_SCAN_RETRIVING_RESULTS, _bot.tree().cell(0, COLUMN_TITLE));

		waitWhileTreeNodeEqualsTo(INFO_SCAN_RETRIVING_RESULTS);
		
		assertTrue("The plugin should retrieve results", _bot.tree().cell(0, COLUMN_TITLE).startsWith(Environment.SCAN_ID));
				
		String firstNodeName = _bot.tree().cell(0, COLUMN_TITLE);
		String secondNodeName = _bot.tree().getTreeItem(firstNodeName).expand().getNode(0).getText();
		String thirdNodeName = _bot.tree().getTreeItem(firstNodeName).expand().getNode(0).expand().getNode(0).getText();
		
		// Expand nodes until the first vulnerability
		_bot.tree().expandNode(firstNodeName).expandNode(secondNodeName).expandNode(thirdNodeName).getNode(0).select();
		
		sleep(1000);
		
		// Close Checkmarx AST Scan view
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
	}


	/**
	 * Test successful connection
	 */
	private void testSuccessfulConnection() {
		
		preventWidgetWasNullInCIEnvironment();
		
		_bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
		_bot.shell(ITEM_PREFERENCES).activate();
		_bot.tree().select(ITEM_CHECKMARX_AST);

		_bot.sleep(1000);

		_bot.textWithLabel(LABEL_SERVER_URL).setText(Environment.BASE_URL);
		_bot.textWithLabel(LABEL_TENANT).setText(Environment.TENANT);
		_bot.textWithLabel(LABEL_AST_API_KEY).setText(Environment.API_KEY);

		_bot.button(BTN_APPLY).click();
		_bot.button(BTN_TEST_CONNECTION).click();
		_bot.waitUntil(Conditions.shellIsActive(SHELL_AUTHENTICATION));
		
		assertEquals(INFO_SUCCESSFUL_CONNECTION, _bot.label(INFO_SUCCESSFUL_CONNECTION).getText());
		
		_bot.button(BTN_OK).click();
		
		_bot.shell(ITEM_PREFERENCES).setFocus(); // Need to set focus to avoid failing in CI environment
		_bot.button(BTN_APPLY_AND_CLOSE).click();

		_cxSettingsDefined = true;
	}

	/**
	 * Add Checkmarx plugin in the show view perspective
	 */
	private void addCheckmarxPlugin() {
		
		preventWidgetWasNullInCIEnvironment();
		
		_bot.menu(TAB_WINDOW).menu(ITEM_SHOW_VIEW).menu(ITEM_OTHER).click();
		_bot.shell(ITEM_SHOW_VIEW).activate();
		_bot.tree().expandNode(ITEM_CHECKMARX).select(ITEM_CHECKMARX_AST_SCAN);
		_bot.button(BTN_OPEN).click();
	}

	/**
	 * Type a valid Scan ID to get results
	 */
	private void typeValidScanID() {
		
		preventWidgetWasNullInCIEnvironment();
		
		_bot.textWithLabel(LABEL_SCAN_ID).setText(Environment.SCAN_ID);
		_bot.textWithLabel(LABEL_SCAN_ID).pressShortcut(Keystrokes.LF);
	}

	/**
	 * Clear all Checkmarx credentials
	 */
	private void clearCheckmarxCredentials() {

		if (!_cxSettingsDefined) {
			return;
		}
		
		preventWidgetWasNullInCIEnvironment();

		_bot.menu(TAB_WINDOW).menu(ITEM_PREFERENCES).click();
		_bot.shell(ITEM_PREFERENCES).activate();
		_bot.tree().select(ITEM_CHECKMARX_AST);

		_bot.textWithLabel(LABEL_SERVER_URL).setText("");
		_bot.textWithLabel(LABEL_TENANT).setText("");
		_bot.textWithLabel(LABEL_AST_API_KEY).setText("");

		_bot.button(BTN_APPLY).click();
		_bot.button(BTN_APPLY_AND_CLOSE).click();

		sleep();
		
		if (_bot.getFocusedWidget() != null) {
			// Check if an Authorizing Eclipse Window pops up and closes it
			if (_bot.activeShell().getText().equals("Authorizing with Eclipse.org")) {
				_bot.button("Cancel").click();
			}

			sleep();

			// Check if an Authorizing Eclipse Window pops up and closes it
			if (_bot.activeShell().getText().equals("Preference Recorder")) {
				_bot.button("Cancel").click();
			}
		}

		_cxSettingsDefined = false;
	}
	
	/**
	 * Wait while tree node equals to a a specific message. Fails after 10 retries
	 * 
	 * @param nodeText
	 * @throws TimeoutException
	 */
	private static void waitWhileTreeNodeEqualsTo(String nodeText) throws TimeoutException {

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
}
