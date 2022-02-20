package checkmarx.ast.eclipse.plugin.tests.ui;

import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.bindings.keys.ParseException;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarDropDownButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.enums.State;
import com.checkmarx.eclipse.views.actions.ToolBarActions;


@RunWith(SWTBotJunit4ClassRunner.class)
public class TestFilterState extends BaseUITest{
	
	List<String> groupByActions = Arrays.asList(ToolBarActions.GROUP_BY_QUERY_NAME,ToolBarActions.GROUP_BY_SEVERITY,ToolBarActions.GROUP_BY_STATE_NAME);
	
	
	@Test(timeout=100000)
	public void testGroupByActionsInToolBar() throws TimeoutException {
		
		int SECOND_NODE = 2;
		int THIRD_NODE = 3;
		int FOURTH_NODE = 4;

		
		setUpCheckmarxPlugin(true);

		// remove all groups and get the first individual node
		
		//List<String> groupByActions = Arrays.asList(ToolBarActions.GROUP_BY_QUERY_NAME,ToolBarActions.GROUP_BY_SEVERITY,ToolBarActions.GROUP_BY_STATE_NAME);
		disableAllGroupByActions(groupByActions);

		
		sleep(1000);
		
		SWTBotTreeItem ll = getFirstResultNode();
		ArrayList<String> severityFilters = new ArrayList<>(Arrays.asList(Severity.HIGH.name(), Severity.MEDIUM.name(),Severity.LOW.name(),Severity.INFO.name()));
		ArrayList<String> stateFilters = new ArrayList<>(Arrays.asList(State.CONFIRMED.name(),State.IGNORED.name(),State.NOT_EXPLOITABLE.name(),State.NOT_IGNORED.name(),State.PROPOSED_NOT_EXPLOITABLE.name(),State.TO_VERIFY.name(),State.URGENT.name()));
		assertTrue(!severityFilters.contains(ll.getText()));
		
		//enable group by severity (1st level group)
		
		enableGroup(ToolBarActions.GROUP_BY_SEVERITY);
		sleep(1000);
		String severityFilter = getNodeLabel(SECOND_NODE);
		//String severityFilter = _bot.tree().getTreeItem(_bot.tree().cell(0, 0)).expand().getNode(0).expand().getNode(0).getText().split(" ")[0];
		assertTrue(severityFilters.contains(severityFilter));
		
		
		// enable group by state (2nd level group)
		enableGroup(ToolBarActions.GROUP_BY_STATE_NAME);
		sleep(1000);
		//String stateFilter = _bot.tree().getTreeItem(_bot.tree().cell(0, 0)).expand().getNode(0).expand().getNode(0).expand().getNode(0).getText().split("\\(")[0].trim();
		String stateFilter = getNodeLabel(THIRD_NODE);
		assertTrue(stateFilters.contains(stateFilter));
		
		
		// enable group by query name (3rd level group)
		enableGroup(ToolBarActions.GROUP_BY_QUERY_NAME);
		sleep(1000);
		//String queryNameFilter= _bot.tree().getTreeItem(_bot.tree().cell(0, 0)).expand().getNode(0).expand().getNode(0).expand().getNode(0).expand().getNode(0).getText().split(" ")[0];
		String queryNameFilter = getNodeLabel(FOURTH_NODE);
		assertTrue(queryNameFilter.startsWith(ll.getText()));

		// Close Checkmarx AST Scan view
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
	}
	


	private String getNodeLabel(int i) {
		SWTBotTreeItem treeNode = _bot.tree().getTreeItem(_bot.tree().cell(0, 0));
		String value = "";
		while(i>0) {
			treeNode = treeNode.expand().getNode(0);
			i--;
		}
		value= treeNode.getText().split("\\(")[0].trim();
		return value;
	}



	private void enableGroup(String groupBy) {
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(ToolBarActions.MENU_GROUP_BY).menu(groupBy).click();
	}


	private void disableAllGroupByActions(List<String> groupByActions) {
		for(String action : groupByActions) {
			SWTBotMenu groupMenu = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(ToolBarActions.MENU_GROUP_BY).menu(action);
			if(groupMenu.isChecked())
				groupMenu.click();
		}
		
	}


	@Test
	public void testFilterStateActionsInToolBar() throws TimeoutException, ParseException{
		setUpCheckmarxPlugin(true);
		
		// deselect all group by actions and enable only the state group by
		disableAllGroupByActions(groupByActions);
		
		sleep(1000);
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(ToolBarActions.MENU_GROUP_BY).menu(ToolBarActions.GROUP_BY_STATE_NAME).click();
		
		// get all filter nodes
		
		
		List<String> filterStateButtons = Arrays.asList("Not Exploitable","Confirmed","Proposed Not Exploitable","Urgent","Ignored","Not Ignored","To Verify");
		List<String> enabledFilters = _bot.tree().getTreeItem(_bot.tree().cell(0, 0)).expand().getNode(0).expand().getNodes().stream().map(node -> node.split("\\(")[0].trim()).collect(Collectors.toList());
		String firstGroup = enabledFilters.get(0);
		List<String> filterButton = filterStateButtons.stream().filter(node -> node.equalsIgnoreCase(firstGroup.replace("_", " "))).collect(Collectors.toList());
		assertTrue(filterButton.size()==1);
		//List<SWTBotToolbarButton> toolbarButtons = _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).getToolbarButtons();
		//SWTBotToolbarButton filterStateButton = toolbarButtons.stream().filter(btn -> btn.getToolTipText().toUpperCase().equals("STATE")).findFirst().get();
		SWTBotToolbarDropDownButton stateFilter = _bot.toolbarDropDownButtonWithTooltip("State");
		final SWTBotMenu menuItem = stateFilter.menuItem(filterButton.get(0));
		menuItem.setFocus();
		menuItem.click();
		stateFilter.pressShortcut(KeyStroke.getInstance("ESC"));
		
		sleep(1000);
		List<String> filteredGroup = new ArrayList<String>();
		if(enabledFilters.size()>1) {
			filteredGroup = _bot.tree().getTreeItem(_bot.tree().cell(0, 0)).expand().getNode(0).expand().getNodes().stream().map(node -> node.split("\\(")[0].trim()).collect(Collectors.toList());
			assertTrue(!filteredGroup.contains(firstGroup));
		}
		else {
			assertTrue(TestUI.ASSERT_NO_CHINDREN, _bot.tree().getTreeItem(_bot.tree().cell(0, 0)).expand().getNodes().isEmpty());
		}
		
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
		
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
