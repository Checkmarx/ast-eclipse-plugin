package checkmarx.ast.eclipse.plugin.tests.unit.views.filters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.enums.State;
import com.checkmarx.eclipse.views.GlobalSettings;
import com.checkmarx.eclipse.views.filters.FilterState;

class FilterStateTest {

    @BeforeEach
    void setUp() {
        FilterState.resetFilters();
    }

    // ─── isSeverityEnabled ───────────────────────────────────────────────────

    @Test
    void testIsSeverityEnabled_critical() {
        FilterState.critical = true;
        assertTrue(FilterState.isSeverityEnabled("CRITICAL"));
        FilterState.critical = false;
        assertFalse(FilterState.isSeverityEnabled("CRITICAL"));
    }

    @Test
    void testIsSeverityEnabled_high() {
        FilterState.high = false;
        assertFalse(FilterState.isSeverityEnabled("HIGH"));
        FilterState.high = true;
        assertTrue(FilterState.isSeverityEnabled("HIGH"));
    }

    @Test
    void testIsSeverityEnabled_medium() {
        FilterState.medium = true;
        assertTrue(FilterState.isSeverityEnabled("MEDIUM"));
        FilterState.medium = false;
        assertFalse(FilterState.isSeverityEnabled("MEDIUM"));
    }

    @Test
    void testIsSeverityEnabled_low() {
        FilterState.low = false;
        assertFalse(FilterState.isSeverityEnabled("LOW"));
        FilterState.low = true;
        assertTrue(FilterState.isSeverityEnabled("LOW"));
    }

    @Test
    void testIsSeverityEnabled_info() {
        FilterState.info = false;
        assertFalse(FilterState.isSeverityEnabled("INFO"));
        FilterState.info = true;
        assertTrue(FilterState.isSeverityEnabled("INFO"));
    }

    @Test
    void testIsSeverityEnabled_groupBySeverity() {
        FilterState.groupBySeverity = true;
        assertTrue(FilterState.isSeverityEnabled("GROUP_BY_SEVERITY"));
        FilterState.groupBySeverity = false;
        assertFalse(FilterState.isSeverityEnabled("GROUP_BY_SEVERITY"));
    }

    @Test
    void testIsSeverityEnabled_groupByQueryName() {
        FilterState.groupByQueryName = false;
        assertFalse(FilterState.isSeverityEnabled("GROUP_BY_QUERY_NAME"));
        FilterState.groupByQueryName = true;
        assertTrue(FilterState.isSeverityEnabled("GROUP_BY_QUERY_NAME"));
    }

    @Test
    void testIsSeverityEnabled_groupByStateName() {
        FilterState.groupByStateName = false;
        assertFalse(FilterState.isSeverityEnabled("GROUP_BY_STATE_NAME"));
        FilterState.groupByStateName = true;
        assertTrue(FilterState.isSeverityEnabled("GROUP_BY_STATE_NAME"));
    }

    // ─── setState ────────────────────────────────────────────────────────────

    @Test
    void testSetState_critical_togglesAndPersists() {
        FilterState.critical = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setState(Severity.CRITICAL);
            gs.verify(() -> GlobalSettings.storeInPreferences("CRITICAL", "false"));
        }
        assertFalse(FilterState.critical);
    }

    @Test
    void testSetState_high_toggles() {
        FilterState.high = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setState(Severity.HIGH);
        }
        assertFalse(FilterState.high);
    }

    @Test
    void testSetState_medium_toggles() {
        FilterState.medium = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setState(Severity.MEDIUM);
        }
        assertFalse(FilterState.medium);
    }

    @Test
    void testSetState_low_togglesFromFalse() {
        FilterState.low = false;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setState(Severity.LOW);
        }
        assertTrue(FilterState.low);
    }

    @Test
    void testSetState_info_togglesFromFalse() {
        FilterState.info = false;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setState(Severity.INFO);
        }
        assertTrue(FilterState.info);
    }

    @Test
    void testSetState_groupBySeverity_toggles() {
        FilterState.groupBySeverity = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setState(Severity.GROUP_BY_SEVERITY);
        }
        assertFalse(FilterState.groupBySeverity);
    }

    @Test
    void testSetState_groupByQueryName_toggles() {
        FilterState.groupByQueryName = false;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setState(Severity.GROUP_BY_QUERY_NAME);
        }
        assertTrue(FilterState.groupByQueryName);
    }

    @Test
    void testSetState_groupByStateName_toggles() {
        FilterState.groupByStateName = false;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setState(Severity.GROUP_BY_STATE_NAME);
        }
        assertTrue(FilterState.groupByStateName);
    }

    // ─── setFilterState ───────────────────────────────────────────────────────

    @Test
    void testSetFilterState_notExploitable_toggles() {
        FilterState.notExploitable = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setFilterState(State.NOT_EXPLOITABLE);
        }
        assertFalse(FilterState.notExploitable);
    }

    @Test
    void testSetFilterState_proposedNotExploitable_toggles() {
        FilterState.proposedNotExploitable = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setFilterState(State.PROPOSED_NOT_EXPLOITABLE);
        }
        assertFalse(FilterState.proposedNotExploitable);
    }

    @Test
    void testSetFilterState_urgent_toggles() {
        FilterState.urgent = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setFilterState(State.URGENT);
        }
        assertFalse(FilterState.urgent);
    }

    @Test
    void testSetFilterState_ignored_toggles() {
        FilterState.ignored = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setFilterState(State.IGNORED);
        }
        assertFalse(FilterState.ignored);
    }

    @Test
    void testSetFilterState_confirmed_toggles() {
        FilterState.confirmed = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setFilterState(State.CONFIRMED);
        }
        assertFalse(FilterState.confirmed);
    }

    @Test
    void testSetFilterState_notIgnored_toggles() {
        FilterState.not_ignored = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setFilterState(State.NOT_IGNORED);
        }
        assertFalse(FilterState.not_ignored);
    }

    @Test
    void testSetFilterState_toVerify_toggles() {
        FilterState.to_verify = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setFilterState(State.TO_VERIFY);
        }
        assertFalse(FilterState.to_verify);
    }

    @Test
    void testSetFilterState_customState_togglesCustomStateFlag() {
        FilterState.customState = true;
        State custom = State.of("CUSTOM_SET_FILTER_TEST_UNIQUE_A1");
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setFilterState(custom);
        }
        assertFalse(FilterState.customState);
    }

    // ─── isFilterStateEnabled ─────────────────────────────────────────────────

    @Test
    void testIsFilterStateEnabled_null_returnsFalse() {
        assertFalse(FilterState.isFilterStateEnabled(null));
    }

    @Test
    void testIsFilterStateEnabled_notExploitable() {
        FilterState.notExploitable = true;
        assertTrue(FilterState.isFilterStateEnabled("NOT_EXPLOITABLE"));
        FilterState.notExploitable = false;
        assertFalse(FilterState.isFilterStateEnabled("NOT_EXPLOITABLE"));
    }

    @Test
    void testIsFilterStateEnabled_proposedNotExploitable() {
        FilterState.proposedNotExploitable = false;
        assertFalse(FilterState.isFilterStateEnabled("PROPOSED_NOT_EXPLOITABLE"));
        FilterState.proposedNotExploitable = true;
        assertTrue(FilterState.isFilterStateEnabled("PROPOSED_NOT_EXPLOITABLE"));
    }

    @Test
    void testIsFilterStateEnabled_toVerify() {
        FilterState.to_verify = true;
        assertTrue(FilterState.isFilterStateEnabled("TO_VERIFY"));
        FilterState.to_verify = false;
        assertFalse(FilterState.isFilterStateEnabled("TO_VERIFY"));
    }

    @Test
    void testIsFilterStateEnabled_confirmed() {
        FilterState.confirmed = false;
        assertFalse(FilterState.isFilterStateEnabled("CONFIRMED"));
        FilterState.confirmed = true;
        assertTrue(FilterState.isFilterStateEnabled("CONFIRMED"));
    }

    @Test
    void testIsFilterStateEnabled_urgent() {
        FilterState.urgent = true;
        assertTrue(FilterState.isFilterStateEnabled("URGENT"));
        FilterState.urgent = false;
        assertFalse(FilterState.isFilterStateEnabled("URGENT"));
    }

    @Test
    void testIsFilterStateEnabled_notIgnored() {
        FilterState.not_ignored = true;
        assertTrue(FilterState.isFilterStateEnabled("NOT_IGNORED"));
        FilterState.not_ignored = false;
        assertFalse(FilterState.isFilterStateEnabled("NOT_IGNORED"));
    }

    @Test
    void testIsFilterStateEnabled_ignored() {
        FilterState.ignored = false;
        assertFalse(FilterState.isFilterStateEnabled("IGNORED"));
        FilterState.ignored = true;
        assertTrue(FilterState.isFilterStateEnabled("IGNORED"));
    }

    @Test
    void testIsFilterStateEnabled_lowercaseInput_normalizedCorrectly() {
        FilterState.notExploitable = true;
        assertTrue(FilterState.isFilterStateEnabled("not_exploitable"));
    }

    @Test
    void testIsFilterStateEnabled_unknownCustomState_returnsFalse() {
        assertFalse(FilterState.isFilterStateEnabled("TOTALLY_UNKNOWN_STATE_XYZ_999"));
    }

    @Test
    void testIsFilterStateEnabled_customStateAfterToggle_returnsTrue() {
        String stateName = "TOGGLED_CUSTOM_STATE_B2";
        FilterState.toggleCustomState(stateName);
        assertTrue(FilterState.isFilterStateEnabled(stateName));
        FilterState.toggleCustomState(stateName);
    }

    // ─── toggleCustomState & isCustomStateSelected ────────────────────────────

    @Test
    void testToggleCustomState_addsThenRemoves() {
        String state = "TOGGLE_TEST_STATE_C3";
        assertFalse(FilterState.isCustomStateSelected(state));
        FilterState.toggleCustomState(state);
        assertTrue(FilterState.isCustomStateSelected(state));
        FilterState.toggleCustomState(state);
        assertFalse(FilterState.isCustomStateSelected(state));
    }

    @Test
    void testIsCustomStateSelected_caseInsensitive() {
        String state = "lowercase_state_d4";
        FilterState.toggleCustomState(state);
        assertTrue(FilterState.isCustomStateSelected("LOWERCASE_STATE_D4"));
        FilterState.toggleCustomState("LOWERCASE_STATE_D4");
    }

    // ─── setCustomStateFilter ─────────────────────────────────────────────────

    @Test
    void testSetCustomStateFilter_togglesFromTrue() {
        FilterState.customState = true;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setCustomStateFilter();
        }
        assertFalse(FilterState.customState);
    }

    @Test
    void testSetCustomStateFilter_togglesFromFalse() {
        FilterState.customState = false;
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            FilterState.setCustomStateFilter();
        }
        assertTrue(FilterState.customState);
    }

    // ─── resetFilters ─────────────────────────────────────────────────────────

    @Test
    void testResetFilters_severityDefaults() {
        FilterState.critical = false;
        FilterState.high = false;
        FilterState.medium = false;
        FilterState.low = true;
        FilterState.info = true;
        FilterState.resetFilters();
        assertTrue(FilterState.critical);
        assertTrue(FilterState.high);
        assertTrue(FilterState.medium);
        assertFalse(FilterState.low);
        assertFalse(FilterState.info);
    }

    @Test
    void testResetFilters_groupByDefaults() {
        FilterState.groupBySeverity = false;
        FilterState.groupByQueryName = false;
        FilterState.groupByStateName = false;
        FilterState.resetFilters();
        assertTrue(FilterState.groupBySeverity);
        assertTrue(FilterState.groupByQueryName);
        assertTrue(FilterState.groupByStateName);
    }

    @Test
    void testResetFilters_stateDefaults() {
        FilterState.notExploitable = false;
        FilterState.confirmed = false;
        FilterState.to_verify = false;
        FilterState.ignored = false;
        FilterState.resetFilters();
        assertTrue(FilterState.notExploitable);
        assertTrue(FilterState.confirmed);
        assertTrue(FilterState.to_verify);
        assertTrue(FilterState.ignored);
        assertTrue(FilterState.not_ignored);
        assertTrue(FilterState.urgent);
        assertTrue(FilterState.proposedNotExploitable);
        assertTrue(FilterState.customState);
    }

    // ─── getFilterStateListForPanel ───────────────────────────────────────────

    // ─── loadFiltersFromSettings ──────────────────────────────────────────────

    @Test
    void testLoadFiltersFromSettings_doesNotThrow() {
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            gs.when(() -> GlobalSettings.getFromPreferences(anyString(), anyString())).thenReturn("true");
            assertDoesNotThrow(FilterState::loadFiltersFromSettings);
            assertTrue(FilterState.critical);
            assertTrue(FilterState.high);
        }
    }

    @Test
    void testLoadFiltersFromSettings_falseValues_setsCorrectBooleans() {
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            gs.when(() -> GlobalSettings.getFromPreferences(anyString(), anyString())).thenReturn("false");
            assertDoesNotThrow(FilterState::loadFiltersFromSettings);
            assertFalse(FilterState.critical);
            assertFalse(FilterState.high);
        }
    }

    @Test
    void testGetFilterStateListForPanel_returnsNonNullNonEmptyList() {
        List<String> result = FilterState.getFilterStateListForPanel(Arrays.asList("TO_VERIFY"));
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetFilterStateListForPanel_isSortedAlphabetically() {
        List<String> result = FilterState.getFilterStateListForPanel(null);
        assertNotNull(result);
        for (int i = 0; i < result.size() - 1; i++) {
            assertTrue(result.get(i).compareToIgnoreCase(result.get(i + 1)) <= 0,
                    "List must be sorted at index " + i);
        }
    }

    @Test
    void testGetFilterStateListForPanel_containsPredefinedStates() {
        List<String> result = FilterState.getFilterStateListForPanel(null);
        assertTrue(result.contains("TO_VERIFY"));
        assertTrue(result.contains("CONFIRMED"));
        assertTrue(result.contains("NOT_EXPLOITABLE"));
    }

    // ─── loadFiltersFromSettings ──────────────────────────────────────────────

    @Test
    void testLoadFiltersFromSettings_setsFieldsFromPreferences() {
        try (MockedStatic<GlobalSettings> gs = Mockito.mockStatic(GlobalSettings.class)) {
            gs.when(() -> GlobalSettings.getFromPreferences("CRITICAL", "true")).thenReturn("false");
            gs.when(() -> GlobalSettings.getFromPreferences("HIGH", "true")).thenReturn("true");
            gs.when(() -> GlobalSettings.getFromPreferences("MEDIUM", "true")).thenReturn("true");
            gs.when(() -> GlobalSettings.getFromPreferences("LOW", "false")).thenReturn("true");
            gs.when(() -> GlobalSettings.getFromPreferences("INFO", "false")).thenReturn("false");
            gs.when(() -> GlobalSettings.getFromPreferences("GROUP_BY_SEVERITY", "true")).thenReturn("true");
            gs.when(() -> GlobalSettings.getFromPreferences("GROUP_BY_QUERY_NAME", "false")).thenReturn("false");
            gs.when(() -> GlobalSettings.getFromPreferences("GROUP_BY_STATE_NAME", "false")).thenReturn("false");
            gs.when(() -> GlobalSettings.getFromPreferences("NOT_EXPLOITABLE", "false")).thenReturn("true");
            gs.when(() -> GlobalSettings.getFromPreferences("CONFIRMED", "true")).thenReturn("true");
            gs.when(() -> GlobalSettings.getFromPreferences("TO_VERIFY", "true")).thenReturn("true");
            gs.when(() -> GlobalSettings.getFromPreferences("URGENT", "true")).thenReturn("true");
            gs.when(() -> GlobalSettings.getFromPreferences("IGNORED", "true")).thenReturn("false");
            gs.when(() -> GlobalSettings.getFromPreferences("NOT_IGNORED", "true")).thenReturn("true");
            gs.when(() -> GlobalSettings.getFromPreferences("PROPOSED_NOT_EXPLOITABLE", "false")).thenReturn("false");
            gs.when(() -> GlobalSettings.getFromPreferences("CUSTOM_STATE", "true")).thenReturn("true");

            FilterState.loadFiltersFromSettings();
        }

        assertFalse(FilterState.critical);
        assertTrue(FilterState.high);
        assertTrue(FilterState.low);
        assertTrue(FilterState.notExploitable);
        assertFalse(FilterState.ignored);
        assertTrue(FilterState.customState);
    }
}
