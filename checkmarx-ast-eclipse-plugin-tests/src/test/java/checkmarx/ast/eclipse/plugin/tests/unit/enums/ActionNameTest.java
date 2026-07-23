package checkmarx.ast.eclipse.plugin.tests.unit.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.enums.ActionName;

class ActionNameTest {

	@Test
	void testEnumValues_count() {
		assertEquals(13, ActionName.values().length);
	}

	@Test
	void testEnumConstant_CRITICAL() {
		assertNotNull(ActionName.CRITICAL);
		assertEquals("CRITICAL", ActionName.CRITICAL.name());
	}

	@Test
	void testEnumConstant_HIGH() {
		assertNotNull(ActionName.HIGH);
		assertEquals("HIGH", ActionName.HIGH.name());
	}

	@Test
	void testEnumConstant_MEDIUM() {
		assertNotNull(ActionName.MEDIUM);
		assertEquals("MEDIUM", ActionName.MEDIUM.name());
	}

	@Test
	void testEnumConstant_LOW() {
		assertNotNull(ActionName.LOW);
		assertEquals("LOW", ActionName.LOW.name());
	}

	@Test
	void testEnumConstant_INFO() {
		assertNotNull(ActionName.INFO);
		assertEquals("INFO", ActionName.INFO.name());
	}

	@Test
	void testEnumConstant_START_SCAN() {
		assertNotNull(ActionName.START_SCAN);
		assertEquals("START_SCAN", ActionName.START_SCAN.name());
	}

	@Test
	void testEnumConstant_CANCEL_SCAN() {
		assertNotNull(ActionName.CANCEL_SCAN);
		assertEquals("CANCEL_SCAN", ActionName.CANCEL_SCAN.name());
	}

	@Test
	void testEnumConstant_CLEAN_AND_REFRESH() {
		assertNotNull(ActionName.CLEAN_AND_REFRESH);
		assertEquals("CLEAN_AND_REFRESH", ActionName.CLEAN_AND_REFRESH.name());
	}

	@Test
	void testEnumConstant_PREFERENCES() {
		assertNotNull(ActionName.PREFERENCES);
		assertEquals("PREFERENCES", ActionName.PREFERENCES.name());
	}

	@Test
	void testEnumConstant_GROUP_BY_SEVERITY() {
		assertNotNull(ActionName.GROUP_BY_SEVERITY);
		assertEquals("GROUP_BY_SEVERITY", ActionName.GROUP_BY_SEVERITY.name());
	}

	@Test
	void testEnumConstant_GROUP_BY_QUERY_NAME() {
		assertNotNull(ActionName.GROUP_BY_QUERY_NAME);
		assertEquals("GROUP_BY_QUERY_NAME", ActionName.GROUP_BY_QUERY_NAME.name());
	}

	@Test
	void testEnumConstant_GROUP_BY_STATE_NAME() {
		assertNotNull(ActionName.GROUP_BY_STATE_NAME);
		assertEquals("GROUP_BY_STATE_NAME", ActionName.GROUP_BY_STATE_NAME.name());
	}

	@Test
	void testEnumConstant_FILTER_CHANGED() {
		assertNotNull(ActionName.FILTER_CHANGED);
		assertEquals("FILTER_CHANGED", ActionName.FILTER_CHANGED.name());
	}

	@Test
	void testValueOf_allConstants() {
		for (ActionName action : ActionName.values()) {
			assertEquals(action, ActionName.valueOf(action.name()));
		}
	}

	@Test
	void testValueOf_invalidName_throwsIllegalArgumentException() {
		assertThrows(IllegalArgumentException.class, () -> ActionName.valueOf("INVALID_ACTION"));
	}

	@Test
	void testValueOf_nullName_throwsNullPointerException() {
		assertThrows(NullPointerException.class, () -> ActionName.valueOf(null));
	}
}
