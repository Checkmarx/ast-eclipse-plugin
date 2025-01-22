package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.views.actions.ToolBarActions;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestResultsSorting extends BaseUITest {

    private static final String ASSERT_SORTED_BY_SEVERITY = "Results should be sorted by severity";
    private static final String ASSERT_SORTED_BY_STATE = "Results should be sorted by state";

    @Test
    public void testSortBySeverity() throws TimeoutException {
        setUpCheckmarxPlugin(true);

        // Enable severity grouping
        enableGrouping(ToolBarActions.GROUP_BY_SEVERITY);

        // Get all severity groups
        List<String> severityGroups = getGroups();

        // Verify order: High -> Medium -> Low -> Info
        assertTrue(ASSERT_SORTED_BY_SEVERITY, isSortedBySeverity(severityGroups));

        closeCheckmarxView();
    }

    @Test
    public void testSortByState() throws TimeoutException {
        setUpCheckmarxPlugin(true);

        // Enable state grouping
        enableGrouping(ToolBarActions.GROUP_BY_STATE_NAME);

        // Get all state groups
        List<String> stateGroups = getGroups();

        // Verify order is alphabetical
        assertTrue(ASSERT_SORTED_BY_STATE, isAlphabeticallySorted(stateGroups));

        closeCheckmarxView();
    }

    private void enableGrouping(String groupingOption) {
        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu()
            .menu(ToolBarActions.MENU_GROUP_BY).menu(groupingOption).click();
        sleep(2000); // Allow time for the grouping to take effect
    }

    private List<String> getGroups() {
        List<String> groups = new ArrayList<>();
        SWTBotTreeItem root = _bot.tree(1).getAllItems()[0];
        for (SWTBotTreeItem item : root.getItems()) {
            groups.add(item.getText().split("\\(")[0].trim());
        }
        return groups;
    }

    private boolean isSortedBySeverity(List<String> groups) {
        List<String> expectedOrder = List.of("HIGH", "MEDIUM", "LOW", "INFO");
        return isInExpectedOrder(groups, expectedOrder);
    }

    private boolean isAlphabeticallySorted(List<String> groups) {
        for (int i = 0; i < groups.size() - 1; i++) {
            if (groups.get(i).compareToIgnoreCase(groups.get(i + 1)) > 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isInExpectedOrder(List<String> actual, List<String> expected) {
        int previousIndex = -1;
        for (String group : actual) {
            int currentIndex = expected.indexOf(group);
            if (currentIndex == -1 || currentIndex < previousIndex) {
                return false;
            }
            previousIndex = currentIndex;
        }
        return true;
    }

    private void closeCheckmarxView() {
        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }
}
