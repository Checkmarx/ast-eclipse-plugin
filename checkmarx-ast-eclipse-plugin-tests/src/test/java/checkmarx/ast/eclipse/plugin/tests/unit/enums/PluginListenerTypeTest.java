package checkmarx.ast.eclipse.plugin.tests.unit.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.enums.PluginListenerType;

class PluginListenerTypeTest {

	@Test
	void testEnumValues_count() {
		assertEquals(4, PluginListenerType.values().length);
	}

	@Test
	void testEnumConstant_GET_RESULTS() {
		assertNotNull(PluginListenerType.GET_RESULTS);
		assertEquals("GET_RESULTS", PluginListenerType.GET_RESULTS.name());
	}

	@Test
	void testEnumConstant_FILTER_CHANGED() {
		assertNotNull(PluginListenerType.FILTER_CHANGED);
		assertEquals("FILTER_CHANGED", PluginListenerType.FILTER_CHANGED.name());
	}

	@Test
	void testEnumConstant_CLEAN_AND_REFRESH() {
		assertNotNull(PluginListenerType.CLEAN_AND_REFRESH);
		assertEquals("CLEAN_AND_REFRESH", PluginListenerType.CLEAN_AND_REFRESH.name());
	}

	@Test
	void testEnumConstant_LOAD_RESULTS_FOR_SCAN() {
		assertNotNull(PluginListenerType.LOAD_RESULTS_FOR_SCAN);
		assertEquals("LOAD_RESULTS_FOR_SCAN", PluginListenerType.LOAD_RESULTS_FOR_SCAN.name());
	}

	@Test
	void testValueOf_allConstants() {
		for (PluginListenerType type : PluginListenerType.values()) {
			assertEquals(type, PluginListenerType.valueOf(type.name()));
		}
	}

	@Test
	void testValueOf_invalidName_throwsIllegalArgumentException() {
		assertThrows(IllegalArgumentException.class, () -> PluginListenerType.valueOf("INVALID_TYPE"));
	}

	@Test
	void testValueOf_nullName_throwsNullPointerException() {
		assertThrows(NullPointerException.class, () -> PluginListenerType.valueOf(null));
	}
}
