package checkmarx.ast.eclipse.plugin.tests.unit.enums;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.enums.State;

class StateTest {

    // ─── predefined constants ────────────────────────────────────────────────

    @Test
    void testPredefinedConstants_notNull() {
        assertNotNull(State.TO_VERIFY);
        assertNotNull(State.NOT_EXPLOITABLE);
        assertNotNull(State.PROPOSED_NOT_EXPLOITABLE);
        assertNotNull(State.CONFIRMED);
        assertNotNull(State.NOT_IGNORED);
        assertNotNull(State.IGNORED);
        assertNotNull(State.URGENT);
    }

    @Test
    void testPredefinedConstants_names() {
        assertEquals("TO_VERIFY", State.TO_VERIFY.getName());
        assertEquals("NOT_EXPLOITABLE", State.NOT_EXPLOITABLE.getName());
        assertEquals("PROPOSED_NOT_EXPLOITABLE", State.PROPOSED_NOT_EXPLOITABLE.getName());
        assertEquals("CONFIRMED", State.CONFIRMED.getName());
        assertEquals("NOT_IGNORED", State.NOT_IGNORED.getName());
        assertEquals("IGNORED", State.IGNORED.getName());
        assertEquals("URGENT", State.URGENT.getName());
    }

    // ─── toString ────────────────────────────────────────────────────────────

    @Test
    void testToString_returnsName() {
        assertEquals("TO_VERIFY", State.TO_VERIFY.toString());
        assertEquals("CONFIRMED", State.CONFIRMED.toString());
        assertEquals("IGNORED", State.IGNORED.toString());
        assertEquals("URGENT", State.URGENT.toString());
    }

    // ─── getState ────────────────────────────────────────────────────────────

    @Test
    void testGetState_existingPredefined_returnsInstance() {
        assertNotNull(State.getState("TO_VERIFY"));
        assertEquals("TO_VERIFY", State.getState("TO_VERIFY").getName());
    }

    @Test
    void testGetState_allPredefinedStates_found() {
        assertNotNull(State.getState("NOT_EXPLOITABLE"));
        assertNotNull(State.getState("PROPOSED_NOT_EXPLOITABLE"));
        assertNotNull(State.getState("CONFIRMED"));
        assertNotNull(State.getState("NOT_IGNORED"));
        assertNotNull(State.getState("IGNORED"));
        assertNotNull(State.getState("URGENT"));
    }

    @Test
    void testGetState_nonExistent_returnsNull() {
        assertNull(State.getState("DOES_NOT_EXIST_STATE_XYZ_123"));
    }

    // ─── of ──────────────────────────────────────────────────────────────────

    @Test
    void testOf_existingPredefined_returnsSameInstance() {
        assertSame(State.TO_VERIFY, State.of("TO_VERIFY"));
    }

    @Test
    void testOf_newName_createsAndRegisters() {
        String name = "CUSTOM_OF_TEST_UNIQUE_E5";
        State result = State.of(name);
        assertNotNull(result);
        assertEquals(name, result.getName());
        assertSame(result, State.getState(name));
    }

    @Test
    void testOf_sameName_returnsSameInstance() {
        String name = "CUSTOM_OF_SAME_UNIQUE_F6";
        State first = State.of(name);
        State second = State.of(name);
        assertSame(first, second);
    }

    @Test
    void testOf_customState_toString() {
        String name = "MY_CUSTOM_STATE_G7";
        State state = State.of(name);
        assertEquals(name, state.toString());
    }

    // ─── values ──────────────────────────────────────────────────────────────

    @Test
    void testValues_isUnmodifiable() {
        Map<String, State> vals = State.values();
        assertThrows(UnsupportedOperationException.class, () -> vals.put("NEW", State.TO_VERIFY));
    }

    @Test
    void testValues_notEmpty() {
        assertFalse(State.values().isEmpty());
    }

    @Test
    void testValues_containsPredefinedKeys() {
        Map<String, State> vals = State.values();
        assertTrue(vals.containsKey("TO_VERIFY"));
        assertTrue(vals.containsKey("CONFIRMED"));
        assertTrue(vals.containsKey("IGNORED"));
        assertTrue(vals.containsKey("NOT_EXPLOITABLE"));
        assertTrue(vals.containsKey("URGENT"));
    }
}
