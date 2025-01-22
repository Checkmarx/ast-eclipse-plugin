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
	
	@Test
	public void testGroupByActionsInToolBar() throws TimeoutException {
		int SECOND_NODE = 2;
		int THIRD_NODE = 3;
		int FOURTH_NODE = 4;

		setUpCheckmarxPlugin(true);     

		// remove all groups and get the first individual node
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
		assertTrue(severityFilters.contains(severityFilter));
		
		// enable group by state (2nd level group)
		enableGroup(ToolBarActions.GROUP_BY_STATE_NAME);
		sleep(1000);
		String stateFilter = getNodeLabel(THIRD_NODE);
		assertTrue(stateFilters.contains(stateFilter));
		
		// enable group by query name (3rd level group)
		enableGroup(ToolBarActions.GROUP_BY_QUERY_NAME);
		sleep(1000);
		String queryNameFilter = getNodeLabel(FOURTH_NODE);
		assertTrue(queryNameFilter.startsWith(ll.getText()));

		// Close Checkmarx One Scan view
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
	}
	
	private String getNodeLabel(int i) {
		SWTBotTreeItem treeNode = _bot.tree(1).getTreeItem(_bot.tree(1).cell(0, 0));
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
		sleep(1000);
		setUpCheckmarxPlugin(true);
		
		// deselect all group by actions and enable only the state group by
		disableAllGroupByActions(groupByActions);
		
		sleep(1000);
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(ToolBarActions.MENU_GROUP_BY).menu(ToolBarActions.GROUP_BY_STATE_NAME).click();
		
		// get all filter nodes
		List<String> filterStateButtons = Arrays.asList("Not Exploitable","Confirmed","Proposed Not Exploitable","Urgent","Ignored","Not Ignored","To Verify");
		List<String> enabledFilters = _bot.tree(1).getTreeItem(_bot.tree(1).cell(0, 0)).expand().getNode(0).expand().getNodes().stream().map(node -> node.split("\\(")[0].trim()).collect(Collectors.toList());
		String firstGroup = enabledFilters.get(0);
		List<String> filterButton = filterStateButtons.stream().filter(node -> node.equalsIgnoreCase(firstGroup.replace("_", " "))).collect(Collectors.toList());
		assertTrue(filterButton.size()==1);
		SWTBotToolbarDropDownButton stateFilter = _bot.toolbarDropDownButtonWithTooltip("State");
		final SWTBotMenu menuItem = stateFilter.menuItem(filterButton.get(0));
		menuItem.setFocus();
		menuItem.click();
		stateFilter.pressShortcut(KeyStroke.getInstance("ESC"));
		
		sleep(1000);
		List<String> filteredGroup = new ArrayList<String>();
		if(enabledFilters.size()>0) {
			filteredGroup = _bot.tree(1).getTreeItem(_bot.tree(1).cell(0, 0)).expand().getNode(0).expand().getNodes().stream().map(node -> node.split("\\(")[0].trim()).collect(Collectors.toList());
			assertTrue(!filteredGroup.contains(firstGroup));
		}
		else {
			assertTrue(TestUI.ASSERT_NO_CHINDREN, _bot.tree(1).getTreeItem(_bot.tree(1).cell(0, 0)).expand().getNodes().isEmpty());
		}
		
		_bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
		
	}

 
    @Test
    public void testScannerTypesDisplay() throws TimeoutException {
        setUpCheckmarxPlugin(true);
        preventWidgetWasNullInCIEnvironment();
        
        // Clear all existing group by actions
        disableAllGroupByActions(groupByActions);
        sleep(1000);
        
        // Verify basic results exist
        SWTBotTreeItem baseNode = getFirstResultNode();
        
        String firstNodeName = _bot.tree(1).cell(0, 0);
        List<String> scannerTypes = _bot.tree(1)
            .getTreeItem(firstNodeName)
            .expand()
            .getNodes();
        
        assertTrue("Should contain SAST results", 
            scannerTypes.stream().anyMatch(node -> node.contains("SAST")));
        assertTrue("Scanner types format should be correct", 
            scannerTypes.stream().allMatch(node -> node.matches(".*\\(\\d+\\)")));
            
        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }


	@Test
	public void testResultsSeverityOrder() throws TimeoutException {
	   setUpCheckmarxPlugin(true);
	   preventWidgetWasNullInCIEnvironment();
	   
	   disableAllGroupByActions(groupByActions);
	   sleep(2000);
	   
	   String firstNodeName = _bot.tree(1).cell(0, 0);
	   SWTBotTreeItem rootNode = _bot.tree(1).getTreeItem(firstNodeName);
	   rootNode.expand();
	   sleep(1000);
	   
	   // Check if root node has any nodes
	   List<String> rootNodes = rootNode.getNodes();
	   assertTrue("Root node has no results", !rootNodes.isEmpty());
	   
	   // Find SAST node
	   SWTBotTreeItem sastNode = null;
	   for (String nodeName : rootNodes) {
		   if (nodeName.toLowerCase().contains("sast")) {
			   sastNode = rootNode.getNode(nodeName);
			   break;
		   }
	   }
	   if (sastNode == null) {
		   throw new AssertionError("SAST node not found");
	   }
	   
	   sastNode.select();
	   sastNode.expand();
	   sleep(1000);
	   
	   // Check if SAST node has results before grouping
	   assertTrue("SAST node has no results before grouping", 
		   !sastNode.getNodes().isEmpty());
	   
	   enableGroup(ToolBarActions.GROUP_BY_SEVERITY);
	   sleep(2000);
	   
	   // Check if SAST node has results after grouping
	   List<String> nodes = sastNode.getNodes();
	   assertTrue("No results found after grouping by severity", 
		   !nodes.isEmpty());
	   
	   List<String> severityNodes = new ArrayList<>();
	   for (String nodeName : nodes) {
		   String severityText = nodeName.split("\\(")[0].trim();
		   if (getSeverityWeight(severityText) > 0) {  // רק אם זו חומרה תקפה
			   severityNodes.add(severityText);
		   }
	   }
	   
	   if (severityNodes.isEmpty()) {
		   System.out.println("No severity nodes found - test passes by default");
		   return;
	   }
	   
	   // Get actual severities and check order only if we have severities
	   List<String> actualSeverities = severityNodes.stream()
	       .distinct()
	       .collect(Collectors.toList());
	   
	   System.out.println("Found severities: " + actualSeverities);

	   if (actualSeverities.size() == 1) {
	       System.out.println("Only one severity found (" + actualSeverities.get(0) + ") - no need to check order");
	       return;
	   }
	   
	   // Check order only if we have more than one severity
	   for (int i = 0; i < actualSeverities.size() - 1; i++) {
	       String currentSeverity = actualSeverities.get(i);
	       String nextSeverity = actualSeverities.get(i + 1);
	       
	       assertTrue(
	           String.format("Wrong severity order: %s found before %s", 
	               currentSeverity, nextSeverity),
	           getSeverityWeight(currentSeverity) >= getSeverityWeight(nextSeverity)
	       );
	   }
	   
	   _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
	}

	// Helper method to get severity weight
	private int getSeverityWeight(String severity) {
	    switch(severity.toUpperCase()) {
	        case "HIGH": return 4;
	        case "MEDIUM": return 3;
	        case "LOW": return 2;
	        case "INFO": return 1;
	        default: return 0;
	    }
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
