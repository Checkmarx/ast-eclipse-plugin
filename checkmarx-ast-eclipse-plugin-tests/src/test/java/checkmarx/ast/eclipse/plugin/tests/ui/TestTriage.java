package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.utils.PluginConstants;

//@RunWith(SWTBotJunit4ClassRunner.class)
public class TestTriage  extends BaseUITest {

	private static final ICondition triageButtonEnabled = new ICondition() {
		private SWTBot bot;
		
		@Override
		public boolean test() throws Exception {
			return bot.buttonWithId(PluginConstants.TRIAGE_BUTTON_ID).isEnabled();
		}
		
		@Override
		public void init(SWTBot bot) {
			this.bot = bot;
		}
		
		@Override
		public String getFailureMessage() {
			return "triage button not enabled";
		}
	};
	
	//@Test
	public void testTriage() throws TimeoutException {
		setUpCheckmarxPlugin(true);
		
		SWTBotTreeItem resultNode = getFirstResultNode();
		String resultName = resultNode.getText();
		
		// Select the first vulnerability
		resultNode.select();
		
		SWTBotCombo severityCombo = _bot.comboBoxWithId(PluginConstants.TRIAGE_SEVERITY_COMBO_ID);
		SWTBotCombo stateCombo = _bot.comboBoxWithId(PluginConstants.TRIAGE_STATE_COMBO_ID);
		
		// set to MEDIUM and CONFIRMED
		String commentUUID = UUID.randomUUID().toString();
		severityCombo.setSelection(Severity.MEDIUM.toString());
		stateCombo.setSelection("CONFIRMED");
		
		// add comment with unique UUID
		SWTBotText commentText = _bot.text(PluginConstants.DEFAULT_COMMENT_TXT);
		commentText.setText(commentUUID);
		
		// perform triage
		SWTBotButton triageButton = _bot.buttonWithId(PluginConstants.TRIAGE_BUTTON_ID);
		triageButton.click();
		
		sleep(1000);
		
		// wait for button to be enabled
		_bot.waitUntil(triageButtonEnabled);
		
		// open changes tab
		SWTBotTabItem changesTab = _bot.tabItemWithId(PluginConstants.CHANGES_TAB_ID);
		changesTab.activate();
		
		// find the unique comment UUID
		_bot.clabel(commentUUID);
		
		// revert severity and state
		severityCombo.setSelection(Severity.HIGH.toString());
		stateCombo.setSelection("TO_VERIFY");
		triageButton.click();

		// wait for button to be enabled
		_bot.waitUntil(triageButtonEnabled);

		// since the order of the list changes, we need to make sure that the changed result is in HIGH -> TO_VERIFY nodes
		// split(" ")[0] provides the initial part of the name, which is the query id, both in the group and in resultName
		List<String> stateResults = getStateResultNodes("TO_VERIFY").stream().map(element -> (element.split(" ")[0]).trim()).collect(Collectors.toList());		
		assertTrue(String.format("%s - %s", stateResults.toString(), resultName), stateResults.contains(resultName.split(" ")[0]));
		
		// Close Checkmarx One Scan view
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
	}

	private List<String> getStateResultNodes(String state) throws TimeoutException {
		String firstNodeName = _bot.tree(1).cell(0, 0);
		SWTBotTreeItem node = _bot.tree(1).getTreeItem(firstNodeName);
		List<String> sastHigh = node.expand().getNode(0).expand().getNode(0).expand().getNodes();
		List<String> result = null;
		for(int toVerifyIndex=0;toVerifyIndex < sastHigh.size();toVerifyIndex++) {
			if(sastHigh.get(toVerifyIndex).startsWith(state)) {
			result = node.expand().getNode(0).expand().getNode(0).expand().getNode(toVerifyIndex).expand().getNodes();
			}
		}
		return result;
	}


	private SWTBotTreeItem getFirstResultNode() {
		String firstNodeName = _bot.tree(1).cell(0, 0);
		SWTBotTreeItem node = _bot.tree(1).getTreeItem(firstNodeName);
		while(!node.getNodes().isEmpty()) {
			node = node.expand().getNode(0);
		}
		return node;
	}
}
