package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.utils.PluginConstants;

public class BestFixLocationTest extends BaseUITest{

	//@Test
	public void testBestFixLocation() throws TimeoutException {
//		setUpCheckmarxPlugin(true);
//		SWTBotTreeItem firstNode = getFirstResultNode();
//		firstNode.select();
//		sleep(3000);
//		String BFLText = _bot.textWithId(PluginConstants.BEST_FIX_LOCATION).getText();
//		assertTrue(BFLText.equals(PluginConstants.BFL_FOUND) || BFLText.equals(PluginConstants.BFL_NOT_FOUND));	
//		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
	}
	

	private SWTBotTreeItem getFirstResultNode() {
		String firstNodeName = _bot.tree().cell(0, 0);
		SWTBotTreeItem node = _bot.tree().getTreeItem(firstNodeName);
		while(!node.getNodes().isEmpty()) {
			node = node.expand().getNode(0);
		}
		return node;
	}
}
