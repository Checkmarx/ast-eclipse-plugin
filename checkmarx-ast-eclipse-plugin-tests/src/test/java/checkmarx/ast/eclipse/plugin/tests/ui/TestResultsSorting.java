package checkmarx.ast.eclipse.plugin.tests.ui;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.checkmarx.eclipse.utils.PluginConstants;

@RunWith(SWTBotJunit4ClassRunner.class)
public class TestResultsSorting extends BaseUITest {

    private static final String ASSERT_SORTED_BY_SEVERITY = "Results should be sorted by severity";
    private static final String ASSERT_SORTED_BY_STATE = "Results should be sorted by state";

    @Test
    public void testSortBySeverity() throws TimeoutException {
        setUpCheckmarxPlugin(true);

        // Enable severity grouping
        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(PluginConstants.MENU_GROUP_BY)
            .menu(PluginConstants.GROUP_BY_SEVERITY).click();

        sleep(2000);

        // Get all severity groups
        List<String> severityGroups = getSeverityGroups();

        // Verify order: High -> Medium -> Low -> Info
        assertTrue(ASSERT_SORTED_BY_SEVERITY, 
            isSortedBySeverity(severityGroups));

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }

    @Test
    public void testSortByState() throws TimeoutException {
        setUpCheckmarxPlugin(true);

        // Enable state grouping
        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).viewMenu().menu(PluginConstants.MENU_GROUP_BY)
            .menu(PluginConstants.GROUP_BY_STATE_NAME).click();

        sleep(2000);

        // Get all state groups
        List<String> stateGroups = getStateGroups();

        // Verify order is alphabetical
        assertTrue(ASSERT_SORTED_BY_STATE,
            isAlphabeticallySorted(stateGroups));

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }

    private List<String> getSeverityGroups() {
        List<String> groups = new ArrayList<>();
        SWTBotTreeItem root = _bot.tree(1).getAllItems()[0];
        for (SWTBotTreeItem item : root.getItems()) {
            groups.add(item.getText().split("\\(")[0].trim());
        }
        return groups;
    }

    private List<String> getStateGroups() {
        List<String> groups = new ArrayList<>();
        SWTBotTreeItem root = _bot.tree(1).getAllItems()[0];
        for (SWTBotTreeItem item : root.getItems()) {
            groups.add(item.getText().split("\\(")[0].trim());
        }
        return groups;
    }

    private boolean isSortedBySeverity(List<String> groups) {
        int highIndex = groups.indexOf("HIGH");
        int mediumIndex = groups.indexOf("MEDIUM");
        int lowIndex = groups.indexOf("LOW");
        int infoIndex = groups.indexOf("INFO");

        return highIndex < mediumIndex && 
               mediumIndex < lowIndex && 
               lowIndex < infoIndex;
    }

    private boolean isAlphabeticallySorted(List<String> groups) {
        for (int i = 0; i < groups.size() - 1; i++) {
            if (groups.get(i).compareTo(groups.get(i + 1)) > 0) {
                return false;
            }
        }
        return true;
    }
} 