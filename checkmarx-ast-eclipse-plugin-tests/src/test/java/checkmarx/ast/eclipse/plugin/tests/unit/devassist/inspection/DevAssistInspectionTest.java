package checkmarx.ast.eclipse.plugin.tests.unit.devassist.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DevAssistInspection}. This is a plain metadata
 * holder (Eclipse has no {@code LocalInspectionTool} equivalent to extend),
 * so coverage is a straightforward smoke test of its fixed identity values.
 */
class DevAssistInspectionTest {

	private final DevAssistInspection inspection = new DevAssistInspection();

	@Test
	@DisplayName("getInspectionId returns the fixed, non-blank inspection id")
	void getInspectionIdReturnsFixedValue() {
		assertEquals("com.checkmarx.eclipse.devassist.inspection", inspection.getInspectionId());
		assertFalse(inspection.getInspectionId().isBlank());
	}

	@Test
	@DisplayName("getInspectionName returns the fixed display name")
	void getInspectionNameReturnsFixedValue() {
		assertEquals("Checkmarx Developer Assist", inspection.getInspectionName());
	}

	@Test
	@DisplayName("getInspectionGroup returns the fixed group/category")
	void getInspectionGroupReturnsFixedValue() {
		assertEquals("Checkmarx", inspection.getInspectionGroup());
	}

	@Test
	@DisplayName("Every metadata getter is stable across multiple instances")
	void metadataIsStableAcrossInstances() {
		DevAssistInspection other = new DevAssistInspection();

		assertEquals(inspection.getInspectionId(), other.getInspectionId());
		assertEquals(inspection.getInspectionName(), other.getInspectionName());
		assertEquals(inspection.getInspectionGroup(), other.getInspectionGroup());
		assertNotNull(inspection.getInspectionId());
	}
}
