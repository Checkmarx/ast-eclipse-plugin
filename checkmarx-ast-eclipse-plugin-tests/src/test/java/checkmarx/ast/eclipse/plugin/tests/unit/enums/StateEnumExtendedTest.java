package checkmarx.ast.eclipse.plugin.tests.unit.enums;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.enums.State;

class StateEnumExtendedTest {

	@BeforeEach
	void setUp() {
		// Reset state registry before each test to avoid cross-test contamination
	}

	@Test
	void testPredefinedState_TO_VERIFY() {
		assertNotNull(State.TO_VERIFY);
		assertEquals("TO_VERIFY", State.TO_VERIFY.getName());
	}

	@Test
	void testPredefinedState_NOT_EXPLOITABLE() {
		assertNotNull(State.NOT_EXPLOITABLE);
		assertEquals("NOT_EXPLOITABLE", State.NOT_EXPLOITABLE.getName());
	}

	@Test
	void testPredefinedState_PROPOSED_NOT_EXPLOITABLE() {
		assertNotNull(State.PROPOSED_NOT_EXPLOITABLE);
		assertEquals("PROPOSED_NOT_EXPLOITABLE", State.PROPOSED_NOT_EXPLOITABLE.getName());
	}

	@Test
	void testPredefinedState_CONFIRMED() {
		assertNotNull(State.CONFIRMED);
		assertEquals("CONFIRMED", State.CONFIRMED.getName());
	}

	@Test
	void testPredefinedState_NOT_IGNORED() {
		assertNotNull(State.NOT_IGNORED);
		assertEquals("NOT_IGNORED", State.NOT_IGNORED.getName());
	}

	@Test
	void testPredefinedState_IGNORED() {
		assertNotNull(State.IGNORED);
		assertEquals("IGNORED", State.IGNORED.getName());
	}

	@Test
	void testPredefinedState_URGENT() {
		assertNotNull(State.URGENT);
		assertEquals("URGENT", State.URGENT.getName());
	}

	@Test
	void testGetState_returnsExistingPredefinedState() {
		State state = State.getState("TO_VERIFY");
		assertNotNull(state);
		assertEquals("TO_VERIFY", state.getName());
	}

	@Test
	void testGetState_withConfirmed() {
		State state = State.getState("CONFIRMED");
		assertNotNull(state);
		assertEquals("CONFIRMED", state.getName());
	}

	@Test
	void testGetState_withUnknownState_returnsNull() {
		State state = State.getState("UNKNOWN_STATE_XYZ");
		assertNull(state);
	}

	@Test
	void testGetState_withNull_returnsNull() {
		State state = State.getState(null);
		assertNull(state);
	}

	@Test
	void testOf_withExistingPredefinedState_returnsExisting() {
		State state = State.of("TO_VERIFY");
		assertNotNull(state);
		assertEquals("TO_VERIFY", state.getName());
	}

	@Test
	void testOf_withNewCustomState_createsAndRegisters() {
		String customStateName = "CUSTOM_STATE_" + System.currentTimeMillis();
		State state = State.of(customStateName);
		assertNotNull(state);
		assertEquals(customStateName, state.getName());

		// Verify it's now in the registry
		State retrieved = State.getState(customStateName);
		assertNotNull(retrieved);
		assertEquals(customStateName, retrieved.getName());
	}

	@Test
	void testOf_calledTwice_returnsSameInstance() {
		String customStateName = "CUSTOM_DUPLICATE_" + System.currentTimeMillis();
		State state1 = State.of(customStateName);
		State state2 = State.of(customStateName);
		assertSame(state1, state2);
	}

	@Test
	void testValues_returnsUnmodifiableMap() {
		Map<String, State> stateMap = State.values();
		assertNotNull(stateMap);
		assertFalse(stateMap.isEmpty());

		// Verify it contains predefined states
		assertTrue(stateMap.containsKey("TO_VERIFY"));
		assertTrue(stateMap.containsKey("CONFIRMED"));
		assertTrue(stateMap.containsKey("IGNORED"));
	}

	@Test
	void testValues_isUnmodifiable() {
		Map<String, State> stateMap = State.values();
		assertThrows(UnsupportedOperationException.class, () -> {
			stateMap.put("NEW_STATE", State.of("NEW_STATE"));
		});
	}

	@Test
	void testToString_returnStateName() {
		assertEquals("CONFIRMED", State.CONFIRMED.toString());
		assertEquals("NOT_EXPLOITABLE", State.NOT_EXPLOITABLE.toString());
		assertEquals("TO_VERIFY", State.TO_VERIFY.toString());
	}

	@Test
	void testMultipleCustomStates_allRegistered() {
		String state1Name = "CUSTOM1_" + System.nanoTime();
		String state2Name = "CUSTOM2_" + System.nanoTime();
		String state3Name = "CUSTOM3_" + System.nanoTime();

		State.of(state1Name);
		State.of(state2Name);
		State.of(state3Name);

		Map<String, State> allStates = State.values();
		assertTrue(allStates.containsKey(state1Name));
		assertTrue(allStates.containsKey(state2Name));
		assertTrue(allStates.containsKey(state3Name));
	}

	@Test
	void testStateEquality_sameInstanceAreEqual() {
		State state1 = State.TO_VERIFY;
		State state2 = State.TO_VERIFY;
		assertSame(state1, state2);
	}

	@Test
	void testStateEquality_getStateReturnsEqualInstance() {
		State predefined = State.CONFIRMED;
		State retrieved = State.getState("CONFIRMED");
		assertSame(predefined, retrieved);
	}

	@Test
	void testGetName_returnsExactStateName() {
		assertEquals("PROPOSED_NOT_EXPLOITABLE", State.PROPOSED_NOT_EXPLOITABLE.getName());
		assertEquals("NOT_IGNORED", State.NOT_IGNORED.getName());
		assertEquals("URGENT", State.URGENT.getName());
	}

	@Test
	void testOf_withEmptyString() {
		State state = State.of("");
		assertNotNull(state);
		assertEquals("", state.getName());
	}

	@Test
	void testOf_withWhitespace() {
		State state = State.of("  WHITESPACE  ");
		assertNotNull(state);
		assertEquals("  WHITESPACE  ", state.getName());
	}

	@Test
	void testGetState_caseSensitive() {
		State state1 = State.getState("CONFIRMED");
		State state2 = State.getState("confirmed");
		assertNotNull(state1);
		assertNull(state2); // Case-sensitive lookup
	}

	@Test
	void testMultipleOfCalls_singleCustomState() {
		String customName = "SINGLE_CUSTOM_" + System.currentTimeMillis();
		for (int i = 0; i < 5; i++) {
			State state = State.of(customName);
			assertNotNull(state);
			assertEquals(customName, state.getName());
		}

		// Should only exist once in the map
		Map<String, State> allStates = State.values();
		assertEquals(1, allStates.values().stream()
			.filter(s -> s.getName().equals(customName))
			.count());
	}

	@Test
	void testStateWithSpecialCharacters() {
		String specialName = "STATE_WITH-SPECIAL_CHARS-123!@#";
		State state = State.of(specialName);
		assertNotNull(state);
		assertEquals(specialName, state.getName());

		State retrieved = State.getState(specialName);
		assertSame(state, retrieved);
	}

	@Test
	void testAllPredefinedStatesAccessible() {
		State[] predefinedStates = {
			State.TO_VERIFY,
			State.NOT_EXPLOITABLE,
			State.PROPOSED_NOT_EXPLOITABLE,
			State.CONFIRMED,
			State.NOT_IGNORED,
			State.IGNORED,
			State.URGENT
		};

		for (State state : predefinedStates) {
			assertNotNull(state);
			assertNotNull(state.getName());
			State retrieved = State.getState(state.getName());
			assertSame(state, retrieved);
		}
	}
}
