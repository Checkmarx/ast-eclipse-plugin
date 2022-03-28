package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeoutException;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.SWTBotWidget;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.utils.PluginConstants;

@RunWith(SWTBotJunit4ClassRunner.class)
public class BestFixLocationTest extends BaseUITest{

	@Test
	public void testBestFixLocation() throws TimeoutException {
		setUpCheckmarxPlugin(true);
		SWTBotTreeItem firstNode = getFirstResultNode();
		firstNode.select();
		sleep(3000);
		String BFLText = _bot.textWithId(PluginConstants.BEST_FIX_LOCATION).getText();
		assertTrue(BFLText.equals(PluginConstants.BFL_FOUND) || BFLText.equals(PluginConstants.BFL_NOT_FOUND));		
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
