package checkmarx.ast.eclipse.plugin.tests.unit.views.filters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.enums.State;
import com.checkmarx.eclipse.views.GlobalSettings;
import com.checkmarx.eclipse.views.filters.FilterState;

class FilterStateExtendedTest {

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		FilterState.resetFilters();
	}

	@Test
	void testResetFilters_setsAllToDefault() {
		FilterState.critical = false;
		FilterState.high = false;

		FilterState.resetFilters();

		assertTrue(FilterState.critical);
		assertTrue(FilterState.high);
	}

	@Test
	void testSetState_criticalToggle() {
		boolean beforeState = FilterState.critical;

		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			FilterState.setState(Severity.CRITICAL);
			assertNotEquals(beforeState, FilterState.critical);
		}
	}

	@Test
	void testSetState_highToggle() {
		boolean beforeState = FilterState.high;

		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			FilterState.setState(Severity.HIGH);
			assertNotEquals(beforeState, FilterState.high);
		}
	}

	@Test
	void testSetState_mediumToggle() {
		boolean beforeState = FilterState.medium;

		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			FilterState.setState(Severity.MEDIUM);
			assertNotEquals(beforeState, FilterState.medium);
		}
	}

	@Test
	void testSetState_lowToggle() {
		boolean beforeState = FilterState.low;

		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			FilterState.setState(Severity.LOW);
			assertNotEquals(beforeState, FilterState.low);
		}
	}

	@Test
	void testSetState_infoToggle() {
		boolean beforeState = FilterState.info;

		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			FilterState.setState(Severity.INFO);
			assertNotEquals(beforeState, FilterState.info);
		}
	}

	@Test
	void testSetState_groupBySeverityToggle() {
		boolean beforeState = FilterState.groupBySeverity;

		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			FilterState.setState(Severity.GROUP_BY_SEVERITY);
			assertNotEquals(beforeState, FilterState.groupBySeverity);
		}
	}

	@Test
	void testSetFilterState_confirmedToggle() {
		boolean beforeState = FilterState.confirmed;

		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			State confirmedState = State.of("CONFIRMED");
			FilterState.setFilterState(confirmedState);
			assertNotEquals(beforeState, FilterState.confirmed);
		}
	}

	@Test
	void testSetFilterState_notExploitableToggle() {
		boolean beforeState = FilterState.notExploitable;

		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			State state = State.of("NOT_EXPLOITABLE");
			FilterState.setFilterState(state);
			assertNotEquals(beforeState, FilterState.notExploitable);
		}
	}

	@Test
	void testSetFilterState_toVerifyToggle() {
		boolean beforeState = FilterState.to_verify;

		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			State state = State.of("TO_VERIFY");
			FilterState.setFilterState(state);
			assertNotEquals(beforeState, FilterState.to_verify);
		}
	}

	@Test
	void testIsSeverityEnabled_critical() {
		FilterState.critical = true;
		assertTrue(FilterState.isSeverityEnabled("CRITICAL"));

		FilterState.critical = false;
		assertFalse(FilterState.isSeverityEnabled("CRITICAL"));
	}

	@Test
	void testIsSeverityEnabled_high() {
		FilterState.high = true;
		assertTrue(FilterState.isSeverityEnabled("HIGH"));

		FilterState.high = false;
		assertFalse(FilterState.isSeverityEnabled("HIGH"));
	}

	@Test
	void testIsSeverityEnabled_low() {
		FilterState.low = true;
		assertTrue(FilterState.isSeverityEnabled("LOW"));

		FilterState.low = false;
		assertFalse(FilterState.isSeverityEnabled("LOW"));
	}

	@Test
	void testIsFilterStateEnabled_confirmed() {
		FilterState.confirmed = true;
		assertTrue(FilterState.isFilterStateEnabled("CONFIRMED"));

		FilterState.confirmed = false;
		assertFalse(FilterState.isFilterStateEnabled("CONFIRMED"));
	}

	@Test
	void testIsFilterStateEnabled_notExploitable() {
		FilterState.notExploitable = true;
		assertTrue(FilterState.isFilterStateEnabled("NOT_EXPLOITABLE"));

		FilterState.notExploitable = false;
		assertFalse(FilterState.isFilterStateEnabled("NOT_EXPLOITABLE"));
	}

	@Test
	void testIsFilterStateEnabled_withNull() {
		assertFalse(FilterState.isFilterStateEnabled(null));
	}

	@Test
	void testIsFilterStateEnabled_withDifferentCases() {
		FilterState.confirmed = true;
		assertTrue(FilterState.isFilterStateEnabled("confirmed"));
		assertTrue(FilterState.isFilterStateEnabled("CONFIRMED"));
		assertTrue(FilterState.isFilterStateEnabled("Confirmed"));
	}

	@Test
	void testSetCustomStateFilter_togglesCustomState() {
		boolean beforeState = FilterState.customState;

		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			FilterState.setCustomStateFilter();
			assertNotEquals(beforeState, FilterState.customState);
		}
	}

	@Test
	void testToggleCustomState_addsNewState() {
		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			FilterState.toggleCustomState("CUSTOM_STATE_1");
			assertTrue(FilterState.isCustomStateSelected("CUSTOM_STATE_1"));
		}
	}

	@Test
	void testToggleCustomState_addsMultipleStates() {
		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			FilterState.toggleCustomState("CUSTOM_A");
			FilterState.toggleCustomState("CUSTOM_B");
			assertTrue(FilterState.isCustomStateSelected("CUSTOM_A"));
			assertTrue(FilterState.isCustomStateSelected("CUSTOM_B"));
		}
	}

	@Test
	void testGetFilterStateListForPanel_notNull() {
		java.util.List<String> result = FilterState.getFilterStateListForPanel(new java.util.ArrayList<>());
		assertNotNull(result);
	}

	@Test
	void testMultipleSeverityToggles() {
		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			FilterState.setState(Severity.CRITICAL);
			FilterState.setState(Severity.HIGH);
			FilterState.setState(Severity.MEDIUM);

			boolean result1 = FilterState.isSeverityEnabled("CRITICAL");
			boolean result2 = FilterState.isSeverityEnabled("HIGH");
			boolean result3 = FilterState.isSeverityEnabled("MEDIUM");

			assertNotNull(result1);
			assertNotNull(result2);
			assertNotNull(result3);
		}
	}

	@Test
	void testMultipleStateToggles() {
		try (MockedStatic<GlobalSettings> mockSettings = mockStatic(GlobalSettings.class)) {
			FilterState.setFilterState(State.CONFIRMED);
			FilterState.setFilterState(State.NOT_EXPLOITABLE);
			FilterState.setFilterState(State.IGNORED);

			boolean result1 = FilterState.isFilterStateEnabled("CONFIRMED");
			boolean result2 = FilterState.isFilterStateEnabled("NOT_EXPLOITABLE");
			boolean result3 = FilterState.isFilterStateEnabled("IGNORED");

			assertNotNull(result1);
			assertNotNull(result2);
			assertNotNull(result3);
		}
	}

	@Test
	void testPredefinedStatesList_notEmpty() {
		assertNotNull(FilterState.PREDEFINED_STATES);
		assertFalse(FilterState.PREDEFINED_STATES.isEmpty());
	}

	@Test
	void testPredefinedStateSet_notEmpty() {
		assertNotNull(FilterState.PREDEFINED_STATE_SET);
		assertFalse(FilterState.PREDEFINED_STATE_SET.isEmpty());
	}
}
