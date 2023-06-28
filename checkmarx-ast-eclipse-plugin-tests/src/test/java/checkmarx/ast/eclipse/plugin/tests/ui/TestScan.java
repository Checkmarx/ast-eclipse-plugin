package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertFalse;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarButton;
import org.junit.BeforeClass;
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
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		// Needed to set CI environment keyboard layout
		SWTBotPreferences.KEYBOARD_LAYOUT = "EN_US"; 
		
		// Used to wait for scan to finish
		SWTBotPreferences.TIMEOUT = 300000; // 5minutes

		// Used to decrease tests velocity
		SWTBotPreferences.PLAYBACK_DELAY = 100;

		_bot = new SWTWorkbenchBot();
		createEclipseProject();
	}

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
		testSuccessfulConnection(false);

		addCheckmarxPlugin(true);
		
		preventWidgetWasNullInCIEnvironment();
		
		_bot.comboBox(2).setText(Environment.SCAN_ID_PROJECT_DOES_NOT_MATCH);
		_bot.comboBox(2).pressShortcut(Keystrokes.LF);
		
		waitUntilBranchComboIsEnabled();
		
		SWTBotToolbarButton startBtn = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons().stream().filter(btn -> btn.getToolTipText().equals(PluginConstants.CX_START_SCAN)).findFirst().get();
		startBtn.click();
		
		SWTBotShell shell = _bot.shell(PluginConstants.CX_PROJECT_MISMATCH);
		shell.activate();
		
		_bot.button(BTN_NO).click();
	}
	
	@Test
	public void testCancelScan() throws TimeoutException {
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
	}
	
	@Test
	public void testRunScan() throws TimeoutException {
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
	}
	
	private static void createEclipseProject() {
		_bot.menu("File").menu("New").menu("Project...").click();
		SWTBotShell shell = _bot.shell("New Project");
		shell.activate();
		_bot.tree().select("Project");
		_bot.button("Next >").click();
		
 
		_bot.textWithLabel("Project name:").setText("MyFirstProject");
		_bot.button("Finish").click();
		
		_bot.menu("File").menu("New").menu("File").click();
		_bot.textWithLabel("File name:").setText("Dockerfile"); 
		_bot.button("Finish").click();
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