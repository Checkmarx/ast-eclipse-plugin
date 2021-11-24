package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.swtbot.eclipse.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarButton;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.views.actions.ActionName;
import com.checkmarx.eclipse.views.actions.ToolBarActions;
import com.checkmarx.eclipse.views.filters.Severity;

import checkmarx.ast.eclipse.plugin.tests.common.Environment;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestUI extends BaseUITest {
	
	private static final String ERROR_INCORRECT_SCAN_ID_FORMAT = "Incorrect scanId format.";
	private static final String ERROR_SERVER_URL_NOT_SET = "Error: Checkmarx server URL is not set";

	private static final String INFO_SCAN_RETRIVING_RESULTS = "Retrieving the results for the scan id: " + Environment.SCAN_ID + " .";

	private static final String INFO_SUCCESSFUL_CONNECTION = "Connection successfull !";
	
	private static final String ASSERT_FILTER_ACTIONS_IN_TOOLBAR = "All filter actions must be in the tool bar";
	private static final String ASSERT_GROUP_BY_ACTIONS_IN_TOOLBAR = "All group by actions must be in the tool bar";
	private static final String ASSERT_TREE_CONSTAIN_HIGH_MEDIUM = "Results must contain results grouped by High and Medium";
	private static final String ASSERT_TREE_CONSTAIN_HIGH_MEDIUM_LOW = "Results must contain results grouped by High, Medium and Low";
	private static final String ASSERT_TREE_CONSTAIN_HIGH_MEDIUM_LOW_INFO = "Results must contain results grouped by High, Medium, Low and Info";
	private static final String ASSERT_TREE_WITH_NO_ISSUES = "The tree mustn't have results once we are grouping by severity and no severity is selected";
	private static final String ASSERT_GROUP_BY_QUERY_NAME = "Parent name must be equals to child name once it is grouped by query name";
	private static final String ASSERT_NO_CHINDREN = "One group by severity and group by query name are not selected, this node shouldn't have children";
	private static final String ASSERT_GROUP_BY_SEVERITY_NOT_SELECTED = "Engine child should not be HIGH, MEDIUM, LOW or INFO once the group by severity is not enabled";

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

		// Set credentials, test connection and add checkmarx plugin
		setUpCheckmarxPlugin();
				
		String firstNodeName = _bot.tree().cell(0, COLUMN_TITLE);
		String secondNodeName = _bot.tree().getTreeItem(firstNodeName).expand().getNode(0).getText();
		String thirdNodeName = _bot.tree().getTreeItem(firstNodeName).expand().getNode(0).expand().getNode(0).getText();
		
		// Expand nodes until the first vulnerability
		_bot.tree().expandNode(firstNodeName).expandNode(secondNodeName).expandNode(thirdNodeName).getNode(0).select();
		
		sleep(1000);
		
		// Close Checkmarx AST Scan view
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
	}
	
	@Test
	public void testFilterButtonsAndGroupByActionsInToolBar() throws TimeoutException {
				
		// Add Checkmarx AST Plugin
		addCheckmarxPlugin();
		
		List<SWTBotToolbarButton> toolbarButtons = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons();
		List<String> toolBarButtonsNames = toolbarButtons.stream().map(btn -> btn.getToolTipText().toUpperCase()).collect(Collectors.toList());
		List<String> filterActions = Arrays.asList(ActionName.HIGH.name(), ActionName.MEDIUM.name(), ActionName.LOW.name(), ActionName.INFO.name());
		
		// Assert all filter actions are present in the tool bar
		assertTrue(ASSERT_FILTER_ACTIONS_IN_TOOLBAR, toolBarButtonsNames.containsAll(filterActions));	
		
		List<String> groupByActions = Arrays.asList(ToolBarActions.GROUP_BY_SEVERITY, ToolBarActions.GROUP_BY_QUERY_NAME);
		List<String> toolBarGroupByActions = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(ToolBarActions.MENU_GROUP_BY).menuItems();
		
		// Assert all group by actions are present in the tool bar
		assertTrue(ASSERT_GROUP_BY_ACTIONS_IN_TOOLBAR, toolBarGroupByActions.containsAll(groupByActions));	
		
		// Close Checkmarx AST Scan view
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
	}
	
	@Test
	public void testFilteringAndGroupingResults() throws TimeoutException {
		
		// Set credentials, test connection and add checkmarx plugin
		setUpCheckmarxPlugin();
		
		ArrayList<String> currentActiveFilters = new ArrayList<>(Arrays.asList(Severity.HIGH.name(), Severity.MEDIUM.name()));	
				
		// Checks that tree contains High and Medium results
		assertTrue(ASSERT_TREE_CONSTAIN_HIGH_MEDIUM, expandTreeUntilFirstEngineAndGetCurrentSeverities().containsAll(currentActiveFilters));			
		
		// Click to include Low severity
		clickSeverityFilter(ActionName.LOW.name());
		currentActiveFilters.add(Severity.LOW.name());
		
		// Checks that tree contains High, Medium and Low results
		assertTrue(ASSERT_TREE_CONSTAIN_HIGH_MEDIUM_LOW, expandTreeUntilFirstEngineAndGetCurrentSeverities().containsAll(currentActiveFilters));	
		
		// Click to include Info severity
		clickSeverityFilter(ActionName.INFO.name());
		currentActiveFilters.add(Severity.INFO.name());
		
		// Checks that tree contains High, Medium, Low and Info results
		assertTrue(ASSERT_TREE_CONSTAIN_HIGH_MEDIUM_LOW_INFO, expandTreeUntilFirstEngineAndGetCurrentSeverities().containsAll(currentActiveFilters));	
		
		// Get all filter buttons individually
		SWTBotToolbarButton filterHighBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().toUpperCase().equals(ActionName.HIGH.name())).findFirst().get();
		SWTBotToolbarButton filterMediumBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().toUpperCase().equals(ActionName.MEDIUM.name())).findFirst().get();
		SWTBotToolbarButton filterLowBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().toUpperCase().equals(ActionName.LOW.name())).findFirst().get();
		SWTBotToolbarButton filterInfoBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().toUpperCase().equals(ActionName.INFO.name())).findFirst().get();
		
		// Click to remove all filters
		filterHighBtn.click();
		filterMediumBtn.click();
		filterLowBtn.click();
		filterInfoBtn.click();
		
		// Asserts that no issues are visible in the tree once we are grouping by Severity and no severity is selected
		assertEquals(ASSERT_TREE_WITH_NO_ISSUES, _bot.tree().cell(0, COLUMN_TITLE), Environment.SCAN_ID + " (0 Issues)");
		
		// Click to include High severity
		clickSeverityFilter(ActionName.HIGH.name());
		currentActiveFilters.add(Severity.HIGH.name());
				
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(ToolBarActions.MENU_GROUP_BY).menu(ToolBarActions.GROUP_BY_QUERY_NAME).click();
		
		sleep(1000);		
		
		String firstNodeName = _bot.tree().cell(0, COLUMN_TITLE);
		String secondNodeName = _bot.tree().getTreeItem(firstNodeName).expand().getNode(0).getText();
		String thirdNodeName = _bot.tree().getTreeItem(firstNodeName).expand().getNode(0).expand().getNode(0).getText();
		
		// Expand nodes until the first vulnerability
		String groupByQueryNameParent = _bot.tree().expandNode(firstNodeName).expandNode(secondNodeName).expandNode(thirdNodeName).getNode(0).getText();
		String groupByQueryNameChild = _bot.tree().expandNode(firstNodeName).expandNode(secondNodeName).expandNode(thirdNodeName).getNode(0).expand().getNode(0).getText();
		
		// Select the first vulnerability
		_bot.tree().expandNode(firstNodeName).expandNode(secondNodeName).expandNode(thirdNodeName).getNode(0).expand().getNode(0).select();
		
		// Asserts that the vulnerability has the same name as the parent node which means it is grouped by query name
		assertTrue(ASSERT_GROUP_BY_QUERY_NAME, groupByQueryNameParent.split("\\(")[0].trim().equals(groupByQueryNameChild));
		
		// Remove either group by severity and query name
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(ToolBarActions.MENU_GROUP_BY).menu(ToolBarActions.GROUP_BY_QUERY_NAME).click();
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(ToolBarActions.MENU_GROUP_BY).menu(ToolBarActions.GROUP_BY_SEVERITY).click();
		
		sleep(1000);
		
		firstNodeName = _bot.tree().cell(0, COLUMN_TITLE);
		secondNodeName = _bot.tree().getTreeItem(firstNodeName).expand().getNode(0).getText();
		_bot.tree().expandNode(firstNodeName).expandNode(secondNodeName);
		
		sleep(1000);
		
		// Get's the first engine child
		String firstEngineChild = _bot.tree().expandNode(firstNodeName).expandNode(secondNodeName).getNode(0).getText();
		
		// Checks if it starts by HIGH, MEDIUM, LOW or INFO
		boolean engineChildDontStartWithHIGH = !firstEngineChild.startsWith(ActionName.HIGH.name());
		boolean engineChildDontStartWithMEDIUM = !firstEngineChild.startsWith(ActionName.MEDIUM.name());
		boolean engineChildDontStartWithLOW = !firstEngineChild.startsWith(ActionName.LOW.name());
		boolean engineChildDontStartWithINFO = !firstEngineChild.startsWith(ActionName.INFO.name());
		
		// Asserts group by options are not enabled
		assertTrue(ASSERT_NO_CHINDREN, _bot.tree().expandNode(firstNodeName).expandNode(secondNodeName).getNode(0).getNodes().isEmpty());
		assertTrue(ASSERT_GROUP_BY_SEVERITY_NOT_SELECTED, engineChildDontStartWithHIGH && engineChildDontStartWithMEDIUM && engineChildDontStartWithLOW && engineChildDontStartWithINFO);
		
		// Close Checkmarx AST Scan view
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
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
	private void setUpCheckmarxPlugin() throws TimeoutException{
		// Test Connection
		testSuccessfulConnection();

		// Add Checkmarx AST Plugin
		addCheckmarxPlugin();
		
		preventWidgetWasNullInCIEnvironment();

		sleep(1000);
		
		// Test incorrect Scan ID format
		_bot.textWithLabel(LABEL_SCAN_ID).setText("invalid-scan-id");
		_bot.textWithLabel(LABEL_SCAN_ID).pressShortcut(Keystrokes.LF);

		sleep(1000);

		assertEquals("The tree must contain one row with an error message", _bot.tree().rowCount(), 1);
		assertEquals("An incorrect scanId format message must be displayed", ERROR_INCORRECT_SCAN_ID_FORMAT, _bot.tree().cell(0, COLUMN_TITLE));

		// type a valid and existing Scan ID
		typeValidScanID();

		assertEquals("The tree must contain one row", _bot.tree().rowCount(), 1);		
		assertEquals("The plugin should be retrieving results", INFO_SCAN_RETRIVING_RESULTS, _bot.tree().cell(0, COLUMN_TITLE));

		waitWhileTreeNodeEqualsTo(INFO_SCAN_RETRIVING_RESULTS);
		
		assertTrue("The plugin should retrieve results", _bot.tree().cell(0, COLUMN_TITLE).startsWith(Environment.SCAN_ID));
	}
	
	/**
	 * Click on a severity filter
	 * 
	 * @param actionName
	 */
	private void clickSeverityFilter(String actionName) {
		SWTBotToolbarButton filterLowBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().toUpperCase().equals(actionName)).findFirst().get();
		filterLowBtn.click();
	}
	
	/**
	 * Expands the tree until the first engine and picks the list of available severities
	 * 
	 * @return
	 */
	private List<String> expandTreeUntilFirstEngineAndGetCurrentSeverities() {
		String firstNodeName = _bot.tree().cell(0, COLUMN_TITLE);
		String secondNodeName = _bot.tree().getTreeItem(firstNodeName).expand().getNode(0).getText();
		
		_bot.tree().expandNode(firstNodeName).expandNode(secondNodeName);
		sleep(1000);
		
		return _bot.tree().getTreeItem(_bot.tree().cell(0, COLUMN_TITLE)).expand().getNode(0).getNodes().stream().map(node -> node.split("\\(")[0].trim()).collect(Collectors.toList());
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
