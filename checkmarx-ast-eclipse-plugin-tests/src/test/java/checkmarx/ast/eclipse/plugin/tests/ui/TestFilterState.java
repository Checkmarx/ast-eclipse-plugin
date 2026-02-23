package checkmarx.ast.eclipse.plugin.tests.ui;

import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.bindings.keys.ParseException;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarDropDownButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.enums.State;
import com.checkmarx.eclipse.views.actions.ToolBarActions;

public class TestFilterState extends BaseUITest {

    List<String> groupByActions = Arrays.asList(
            ToolBarActions.GROUP_BY_QUERY_NAME,
            ToolBarActions.GROUP_BY_SEVERITY,
            ToolBarActions.GROUP_BY_STATE_NAME);

    private static final String HIGH = "HIGH";
    private static final String MEDIUM = "MEDIUM";
    private static final String LOW = "LOW";
    private static final String INFO = "INFO";

    @BeforeEach
    void init() {
        preventWidgetWasNullInCIEnvironment();
    }

    @Test
    void testGroupByActionsInToolBar() throws TimeoutException {

        int SECOND_NODE = 2;
        int THIRD_NODE = 3;
        int FOURTH_NODE = 4;

        setUpCheckmarxPlugin(true);

        disableAllGroupByActions(groupByActions);
        sleep(1000);

        SWTBotTreeItem ll = getFirstResultNode();

        ArrayList<String> severityFilters = new ArrayList<>(
                Arrays.asList(Severity.HIGH.name(),
                        Severity.MEDIUM.name(),
                        Severity.LOW.name(),
                        Severity.INFO.name()));

        ArrayList<String> stateFilters = new ArrayList<>(
                Arrays.asList(State.CONFIRMED.getName(),
                        State.IGNORED.getName(),
                        State.NOT_EXPLOITABLE.getName(),
                        State.NOT_IGNORED.getName(),
                        State.PROPOSED_NOT_EXPLOITABLE.getName(),
                        State.TO_VERIFY.getName(),
                        State.URGENT.getName()));

        assertTrue(!severityFilters.contains(ll.getText()));

        enableGroup(ToolBarActions.GROUP_BY_SEVERITY);
        sleep(1000);
        String severityFilter = getNodeLabel(SECOND_NODE);
        assertTrue(severityFilters.contains(severityFilter));

        enableGroup(ToolBarActions.GROUP_BY_STATE_NAME);
        sleep(1000);
        String stateFilter = getNodeLabel(THIRD_NODE);
        assertTrue(stateFilters.contains(stateFilter));

        enableGroup(ToolBarActions.GROUP_BY_QUERY_NAME);
        sleep(1000);
        String queryNameFilter = getNodeLabel(FOURTH_NODE);
        assertTrue(queryNameFilter.startsWith(ll.getText()));

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }

    @Test
    void testFilterStateActionsInToolBar()
            throws TimeoutException, ParseException {

        sleep(1000);
        setUpCheckmarxPlugin(true);

        disableAllGroupByActions(groupByActions);
        sleep(1000);

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN)
                .viewMenu()
                .menu(ToolBarActions.MENU_GROUP_BY)
                .menu(ToolBarActions.GROUP_BY_STATE_NAME)
                .click();

        List<String> filterStateButtons = Arrays.asList(
                "Not Exploitable", "Confirmed",
                "Proposed Not Exploitable", "Urgent",
                "Ignored", "Not Ignored", "To Verify");

        List<String> enabledFilters = _bot.tree(1)
                .getTreeItem(_bot.tree(1).cell(0, 0))
                .expand()
                .getNode(0)
                .expand()
                .getNodes()
                .stream()
                .map(node -> node.split("\\(")[0].trim())
                .collect(Collectors.toList());

        String firstGroup = enabledFilters.get(0);

        List<String> filterButton = filterStateButtons.stream()
                .filter(node -> node.equalsIgnoreCase(firstGroup.replace("_", " ")))
                .collect(Collectors.toList());

        assertTrue(filterButton.size() == 1);

        SWTBotToolbarDropDownButton stateFilter =
                _bot.toolbarDropDownButtonWithTooltip("State");

        SWTBotMenu menuItem = stateFilter.menuItem(filterButton.get(0));
        menuItem.setFocus();
        menuItem.click();
        stateFilter.pressShortcut(KeyStroke.getInstance("ESC"));

        sleep(1000);

        if (enabledFilters.size() > 0) {
            List<String> filteredGroup = _bot.tree(1)
                    .getTreeItem(_bot.tree(1).cell(0, 0))
                    .expand()
                    .getNode(0)
                    .expand()
                    .getNodes()
                    .stream()
                    .map(node -> node.split("\\(")[0].trim())
                    .collect(Collectors.toList());

            assertTrue(!filteredGroup.contains(firstGroup));
        } else {
            assertTrue(
                    _bot.tree(1)
                            .getTreeItem(_bot.tree(1).cell(0, 0))
                            .expand()
                            .getNodes()
                            .isEmpty(),
                    TestUI.ASSERT_NO_CHINDREN
            );
        }

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }

    @Test
    void testScannerTypesDisplay() throws TimeoutException {

        setUpCheckmarxPlugin(true);
        sleep(1000);

        disableAllGroupByActions(groupByActions);
        sleep(1000);

        String firstNodeName = _bot.tree(1).cell(0, 0);

        List<String> scannerTypes = _bot.tree(1)
                .getTreeItem(firstNodeName)
                .expand()
                .getNodes();

        assertTrue(
                scannerTypes.stream().anyMatch(node -> node.contains("SAST")),
                "Should contain SAST results"
        );

        assertTrue(
                scannerTypes.stream().allMatch(node -> node.matches(".*\\(\\d+\\)")),
                "Scanner types format should be correct"
        );

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }

    @Test
    void testResultsSeverityOrder() throws TimeoutException {

        setUpCheckmarxPlugin(true);
        sleep(2000);

        disableAllGroupByActions(groupByActions);

        String firstNodeName = _bot.tree(1).cell(0, 0);
        SWTBotTreeItem rootNode = _bot.tree(1).getTreeItem(firstNodeName);
        rootNode.expand();
        sleep(1000);

        List<String> rootNodes = rootNode.getNodes();
        if (rootNodes.isEmpty()) return;

        SWTBotTreeItem sastNode = null;
        for (String nodeName : rootNodes) {
            if (nodeName.toLowerCase().contains("sast")) {
                sastNode = rootNode.getNode(nodeName);
                break;
            }
        }

        if (sastNode == null) return;

        enableGroup(ToolBarActions.GROUP_BY_SEVERITY);
        sleep(2000);

        rootNode = _bot.tree(1).getTreeItem(firstNodeName);
        rootNode.expand();

        String sastNodeName = rootNode.getNodes().stream()
                .filter(n -> n.toLowerCase().contains("sast"))
                .findFirst()
                .orElse(null);

        if (sastNodeName == null) return;

        sastNode = rootNode.getNode(sastNodeName);
        sastNode.expand();
        sleep(1000);

        List<String> severityNodes = sastNode.getNodes();

        List<String> actualSeverities = severityNodes.stream()
                .map(node -> node.split("\\(")[0].trim())
                .filter(s -> getSeverityWeight(s) > 0)
                .distinct()
                .collect(Collectors.toList());

        if (actualSeverities.size() <= 1) return;

        for (int i = 0; i < actualSeverities.size() - 1; i++) {
            String current = actualSeverities.get(i);
            String next = actualSeverities.get(i + 1);

            assertTrue(
                    getSeverityWeight(current) >= getSeverityWeight(next),
                    String.format("Wrong severity order: %s before %s", current, next)
            );
        }

        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN).close();
    }

    private int getSeverityWeight(String severity) {
        switch (severity.toUpperCase()) {
            case HIGH: return 4;
            case MEDIUM: return 3;
            case LOW: return 2;
            case INFO: return 1;
            default: return 0;
        }
    }

    private SWTBotTreeItem getFirstResultNode() {
        String firstNodeName = _bot.tree(1).cell(0, 0);
        SWTBotTreeItem node = _bot.tree(1).getTreeItem(firstNodeName);
        while (!node.getNodes().isEmpty()) {
            node = node.expand().getNode(0);
        }
        return node;
    }

    private String getNodeLabel(int i) {
        SWTBotTreeItem treeNode =
                _bot.tree(1).getTreeItem(_bot.tree(1).cell(0, 0));
        while (i > 0) {
            treeNode = treeNode.expand().getNode(0);
            i--;
        }
        return treeNode.getText().split("\\(")[0].trim();
    }

    private void enableGroup(String groupBy) {
        _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN)
                .viewMenu()
                .menu(ToolBarActions.MENU_GROUP_BY)
                .menu(groupBy)
                .click();
    }

    private void disableAllGroupByActions(List<String> groupByActions) {
        for (String action : groupByActions) {
            SWTBotMenu groupMenu =
                    _bot.viewByTitle(VIEW_CHECKMARX_AST_SCAN)
                            .viewMenu()
                            .menu(ToolBarActions.MENU_GROUP_BY)
                            .menu(action);
            if (groupMenu.isChecked()) {
                groupMenu.click();
            }
        }
    }
}