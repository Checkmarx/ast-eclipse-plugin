package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertFalse;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarButton;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.utils.PluginConstants;

import checkmarx.ast.eclipse.plugin.tests.common.Environment;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestScan extends BaseUITest {
	
	public static final String ASSERT_START_SCAN_DISABLED = "Start scan must be disabled since there is no project or branch selected.";
	public static final String ASSERT_CANCEL_SCAN_DISABLED = "Cancel scan must be disabled since there is no project or branch selected and no running scan.";
	public static final String BTN_YES = "Yes";
	public static final String BTN_NO = "No";

	@Test
	public void testScanButtonsDisabledWhenMissingProjectOrBranch() throws TimeoutException {
		// Test Connection
		testSuccessfulConnection(false);

		// Add Checkmarx One Plugin
		addCheckmarxPlugin(true);
		
		// clear the view before getting the scan id
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(PluginConstants.TOOLBAR_ACTION_CLEAR_RESULTS).click();
		
		SWTBotToolbarButton startBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().equals(PluginConstants.CX_START_SCAN)).findFirst().get();
		assertFalse(ASSERT_START_SCAN_DISABLED, startBtn.isEnabled());
		SWTBotToolbarButton cancelBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().equals(PluginConstants.CX_CANCEL_RUNNING_SCAN)).findFirst().get();
		assertFalse(ASSERT_CANCEL_SCAN_DISABLED, cancelBtn.isEnabled());
	}
	
	@Test
	public void testScanProjectDoesNotMatch() throws TimeoutException {
	    // Set SWTBot timeout to 5 minutes to wait for slow operations
	    SWTBotPreferences.TIMEOUT = 300000; // 5 minutes

	    // Ensure successful connection to the server
	    testSuccessfulConnection(false);

	    // Add the Checkmarx plugin to the environment
	    addCheckmarxPlugin(true);

	    // Handle potential widget null issues in CI environment
	    preventWidgetWasNullInCIEnvironment();

	    // Set the project ID in the combo box
	    _bot.comboBox(2).setText("9a9c75a5-2fb1-460c-80f7-f902790422b0");
	    _bot.comboBox(2).pressShortcut(Keystrokes.LF);

	    // Wait until the branch combo box is enabled
	    waitUntilBranchComboIsEnabled();

	    // Wait until the Start Scan button is enabled
	    _bot.waitUntil(startScanButtonEnabled);

	    // Add a 30-second delay
	    try {
	        Thread.sleep(30000); // 30 seconds
	    } catch (InterruptedException e) {
	        e.printStackTrace(); // Handle interruption
	    }

	    // Find and click the Start Scan button
	    SWTBotToolbarButton startBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN)
	        .getToolbarButtons().stream()
	        .filter(btn -> btn.getToolTipText().equals(PluginConstants.CX_START_SCAN))
	        .findFirst()
	        .orElseThrow(() -> new RuntimeException("Start Scan button not found"));
	    startBtn.click();

	    // Wait for and activate the project mismatch dialog
	    SWTBotShell shell = _bot.shell(PluginConstants.CX_PROJECT_MISMATCH);
	    shell.activate();

	    // Click the "No" button in the dialog
	    _bot.button(BTN_NO).click();

	    // Reset SWTBot timeout to 5 seconds
	    SWTBotPreferences.TIMEOUT = 5000;
	}

	
	@Test
	public void testCancelScan() throws TimeoutException {
		// Used to wait for scan to finish
		SWTBotPreferences.TIMEOUT = 300000; // 5minutes
		
		testSuccessfulConnection(false);

		addCheckmarxPlugin(true);
		
		preventWidgetWasNullInCIEnvironment();
		
		_bot.comboBox(2).setText(Environment.SCAN_ID);
		_bot.comboBox(2).pressShortcut(Keystrokes.LF);
		
		waitUntilBranchComboIsEnabled();
		
		SWTBotToolbarButton startBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().equals(PluginConstants.CX_START_SCAN)).findFirst().get();
		startBtn.click();
		
		SWTBotToolbarButton cancelBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().equals(PluginConstants.CX_CANCEL_RUNNING_SCAN)).findFirst().get();
		_bot.waitUntil(cancelScanButtonEnabled);
		cancelBtn.click();
		
		_bot.waitUntil(startScanButtonEnabled);
		
		SWTBotPreferences.TIMEOUT = 5000;
	}
	
	@Test
	public void testRunScan() throws TimeoutException {
		// Used to wait for scan to finish
		SWTBotPreferences.TIMEOUT = 300000; // 5minutes
				
		testSuccessfulConnection(false);

		addCheckmarxPlugin(true);
		
		preventWidgetWasNullInCIEnvironment();
		
		_bot.comboBox(2).setText(Environment.SCAN_ID);
		_bot.comboBox(2).pressShortcut(Keystrokes.LF);
		
		waitUntilBranchComboIsEnabled();
		
		sleep(10000);
		
		SWTBotToolbarButton startBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().equals(PluginConstants.CX_START_SCAN)).findFirst().get();
		startBtn.click();
				
		_bot.waitUntil(startScanButtonEnabled);
		
		SWTBotPreferences.TIMEOUT = 5000;
	}	
	
	private static final ICondition startScanButtonEnabled = new ICondition() {		
		@Override
		public boolean test() throws Exception {
			SWTBotToolbarButton startBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().equals(PluginConstants.CX_START_SCAN)).findFirst().get();
			return startBtn.isEnabled();
		}
		
		@Override
		public String getFailureMessage() {
			return "Start scan button must be enabled";
		}

		@Override
		public void init(SWTBot bot) {
			
		}
	};
	
	private static final ICondition cancelScanButtonEnabled = new ICondition() {		
		@Override
		public boolean test() throws Exception {
			SWTBotToolbarButton cancelBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().equals(PluginConstants.CX_CANCEL_RUNNING_SCAN)).findFirst().get();
			return cancelBtn.isEnabled();
		}
		
		@Override
		public String getFailureMessage() {
			return "Cancel scan button must be enabled";
		}

		@Override
		public void init(SWTBot bot) {
			
		}
	};
}