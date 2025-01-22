// package checkmarx.ast.eclipse.plugin.tests.ui;

// import static org.junit.Assert.assertTrue;

// import java.util.concurrent.TimeoutException;

// import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
// import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
// import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
// import org.junit.Test;
// import org.junit.runner.RunWith;

// import com.checkmarx.eclipse.utils.PluginConstants;

// @RunWith(SWTBotJunit4ClassRunner.class)
// public class TestResultsFiltering extends BaseUITest {

//     private static final String ASSERT_FILTERED_RESULTS = "Results should be filtered according to search text";
//     private static final String ASSERT_NO_RESULTS = "No results should be shown for invalid search";

//     @Test
//     public void testQueryNameFiltering() throws TimeoutException {
//         setUpCheckmarxPlugin(true);

//         // Get the first query name
//         String firstNodeName = _bot.tree(1).cell(0, 0);
//         SWTBotTreeItem firstNode = _bot.tree(1).getTreeItem(firstNodeName);
//         String queryName = getFirstQueryName(firstNode);

//         // Type partial query name in filter
//         SWTBotText filterText = _bot.textWithId(PluginConstants.FILTER_TEXT_ID);
//         filterText.setText(queryName.substring(0, 5));
//         sleep(2000);

//         // Verify filtered results
//         SWTBotTreeItem[] items = _bot.tree(1).getAllItems();
//         for (SWTBotTreeItem item : items) {
//             String itemText = getAllNodeText(item);
//             assertTrue(ASSERT_FILTERED_RESULTS, 
//                 itemText.toLowerCase().contains(queryName.substring(0, 5).toLowerCase()));
//         }

//         // Test invalid search
//         filterText.setText("INVALID_QUERY_NAME_XXX");
//         sleep(2000);

//         assertTrue(ASSERT_NO_RESULTS, _bot.tree(1).getAllItems().length <= 1);

//         _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
//     }

//     private String getFirstQueryName(SWTBotTreeItem node) {
//         while(!node.getNodes().isEmpty()) {
//             node = node.expand().getNode(0);
//         }
//         return node.getText();
//     }

//     private String getAllNodeText(SWTBotTreeItem item) {
//         StringBuilder text = new StringBuilder(item.getText());
//         for (SWTBotTreeItem child : item.getItems()) {
//             text.append(" ").append(getAllNodeText(child));
//         }
//         return text.toString();
//     }
// } 