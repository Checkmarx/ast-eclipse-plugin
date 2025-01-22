// package checkmarx.ast.eclipse.plugin.tests.ui;

// import static org.junit.Assert.assertTrue;

// import java.util.concurrent.TimeoutException;

// import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
// import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
// import org.junit.Test;
// import org.junit.runner.RunWith;

// @RunWith(SWTBotJunit4ClassRunner.class)
// public class TestResultNavigation extends BaseUITest {

//     private static final String ASSERT_EDITOR_OPENED = "Editor should open when double-clicking result";
//     private static final String ASSERT_LINE_SELECTED = "Correct line should be selected in editor";

//     @Test
//     public void testDoubleClickNavigation() throws TimeoutException {
//         setUpCheckmarxPlugin(true);

//         // Get first result node
//         SWTBotTreeItem resultNode = getFirstResultNode();
        
//         // Double click to open editor
//         resultNode.doubleClick();
//         sleep(2000);

//         // Verify editor opened with correct file
//         assertTrue(ASSERT_EDITOR_OPENED, 
//             _bot.activeEditor() != null);

//         // Verify line selection
//         assertTrue(ASSERT_LINE_SELECTED,
//             _bot.activeEditor().toTextEditor().getSelection().x > 0);

//         _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
//     }

//     @Test
//     public void testMultipleResultsNavigation() throws TimeoutException {
//         setUpCheckmarxPlugin(true);

//         // Navigate through multiple results
//         SWTBotTreeItem firstNode = getFirstResultNode();
//         firstNode.select();
//         sleep(1000);

//         // Get next result
//         SWTBotTreeItem nextNode = getNextResultNode(firstNode);
//         nextNode.select();
//         sleep(1000);

//         // Verify different selections
//         assertTrue("Different results should be selectable",
//             !firstNode.getText().equals(nextNode.getText()));

//         _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
//     }

//     private SWTBotTreeItem getFirstResultNode() {
//         String firstNodeName = _bot.tree(1).cell(0, 0);
//         SWTBotTreeItem node = _bot.tree(1).getTreeItem(firstNodeName);
//         while(!node.getNodes().isEmpty()) {
//             node = node.expand().getNode(0);
//         }
//         return node;
//     }

//     private SWTBotTreeItem getNextResultNode(SWTBotTreeItem currentNode) {
//         SWTBotTreeItem parent = currentNode.parent();
//         int currentIndex = parent.getNodes().indexOf(currentNode.getText());
//         if (currentIndex < parent.getNodes().size() - 1) {
//             return parent.getNode(currentIndex + 1);
//         }
//         return parent.getNode(0); // Wrap around to first if at end
//     }
// } 