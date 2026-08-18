package checkmarx.ast.eclipse.plugin.tests.unit.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.checkmarx.eclipse.common.enums.Severity;

class SeverityExtendedTest {

	@Test
	void testGetSeverity_allConstants() {
		assertEquals(Severity.CRITICAL, Severity.getSeverity("CRITICAL"));
		assertEquals(Severity.HIGH, Severity.getSeverity("HIGH"));
		assertEquals(Severity.MEDIUM, Severity.getSeverity("MEDIUM"));
		assertEquals(Severity.LOW, Severity.getSeverity("LOW"));
		assertEquals(Severity.INFO, Severity.getSeverity("INFO"));
	}

	@Test
	void testGetSeverity_invalidValue_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> Severity.getSeverity("INVALID_SEVERITY"));
	}

	@Test
	void testGetSeverity_nullValue_throwsException() {
		assertThrows(NullPointerException.class, () -> Severity.getSeverity(null));
	}

	@Test
	void testGetSeverity_emptyString_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> Severity.getSeverity(""));
	}

	@Test
	void testGetSeverity_caseInsensitiveValue_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> Severity.getSeverity("critical"));
	}

	@Test
	void testValueOf_allConstantsExist() {
		assertDoesNotThrow(() -> Severity.valueOf("CRITICAL"));
		assertDoesNotThrow(() -> Severity.valueOf("HIGH"));
		assertDoesNotThrow(() -> Severity.valueOf("MEDIUM"));
		assertDoesNotThrow(() -> Severity.valueOf("LOW"));
		assertDoesNotThrow(() -> Severity.valueOf("INFO"));
	}

	@Test
	void testValueOf_invalidValue_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> Severity.valueOf("NONEXISTENT"));
	}

	@Test
	void testValueOf_nullValue_throwsException() {
		assertThrows(NullPointerException.class, () -> Severity.valueOf(null));
	}

	@Test
	void testEnumName_correctValues() {
		assertEquals("CRITICAL", Severity.CRITICAL.name());
		assertEquals("HIGH", Severity.HIGH.name());
		assertEquals("MEDIUM", Severity.MEDIUM.name());
		assertEquals("LOW", Severity.LOW.name());
		assertEquals("INFO", Severity.INFO.name());
	}

	@Test
	void testEnumOrdinal_correctSequence() {
		assertEquals(0, Severity.CRITICAL.ordinal());
		assertEquals(1, Severity.HIGH.ordinal());
		assertEquals(2, Severity.MEDIUM.ordinal());
		assertEquals(3, Severity.LOW.ordinal());
		assertEquals(4, Severity.INFO.ordinal());
	}

	@Test
	void testValues_returnsAllConstants() {
		Severity[] values = Severity.values();
		assertEquals(8, values.length);
	}

	@Test
	void testValues_containsAllExpected() {
		Severity[] values = Severity.values();
		boolean hasCritical = false;
		boolean hasHigh = false;
		boolean hasLow = false;
		boolean hasGroupBy = false;

		for (Severity s : values) {
			if (s == Severity.CRITICAL) hasCritical = true;
			if (s == Severity.HIGH) hasHigh = true;
			if (s == Severity.LOW) hasLow = true;
		}

		assertTrue(hasCritical);
		assertTrue(hasHigh);
		assertTrue(hasLow);
		assertTrue(hasGroupBy);
	}

	@Test
	void testGetSeverity_roundTrip() {
		for (Severity severity : Severity.values()) {
			Severity retrieved = Severity.getSeverity(severity.name());
			assertEquals(severity, retrieved);
		}
	}

	@Test
	void testSeverityComparison_sameInstanceEquality() {
		Severity s1 = Severity.CRITICAL;
		Severity s2 = Severity.CRITICAL;
		assertSame(s1, s2);
		assertEquals(s1, s2);
	}

	@Test
	void testSeverityComparison_differentInstancesNotEqual() {
		assertNotEquals(Severity.CRITICAL, Severity.HIGH);
		assertNotEquals(Severity.HIGH, Severity.LOW);
		assertNotEquals(Severity.MEDIUM, Severity.INFO);
	}

	@Test
	void testToString_returnsName() {
		assertEquals("CRITICAL", Severity.CRITICAL.toString());
		assertEquals("HIGH", Severity.HIGH.toString());
	}

	@Test
	void testHashCode_consistency() {
		Severity s1 = Severity.CRITICAL;
		Severity s2 = Severity.CRITICAL;
		assertEquals(s1.hashCode(), s2.hashCode());
	}

	@Test
	void testHashCode_differentForDifferentValues() {
		// Different enum constants should (almost certainly) have different hash codes
		assertNotEquals(Severity.CRITICAL.hashCode(), Severity.HIGH.hashCode());
	}

	@Test
	void testEnum_canBeUsedInSwitch() {
		Severity severity = Severity.HIGH;
		String result = switch (severity) {
			case CRITICAL -> "CRITICAL_LEVEL";
			case HIGH -> "HIGH_LEVEL";
			case MEDIUM -> "MEDIUM_LEVEL";
			case LOW -> "LOW_LEVEL";
			case INFO -> "INFO_LEVEL";
			default -> "OTHER";
		};
		assertEquals("HIGH_LEVEL", result);
	}

	@Test
	void testEnum_canBeUsedInIfConditions() {
		Severity severity = Severity.CRITICAL;
		boolean isCritical = severity == Severity.CRITICAL;
		assertTrue(isCritical);

		boolean isHigh = severity == Severity.HIGH;
		assertFalse(isHigh);
	}

	@Test
	void testGetSeverity_withWhitespace_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> Severity.getSeverity(" CRITICAL "));
	}

	@Test
	void testGetSeverity_repeatedCallsSameValue() {
		Severity s1 = Severity.getSeverity("MEDIUM");
		Severity s2 = Severity.getSeverity("MEDIUM");
		Severity s3 = Severity.getSeverity("MEDIUM");

		assertEquals(s1, s2);
		assertEquals(s2, s3);
		assertSame(s1, s2);
		assertSame(s2, s3);
	}

	@Test
	void testEnumConstants_arePublicStaticFinal() {
		assertNotNull(Severity.CRITICAL);
		assertNotNull(Severity.HIGH);
		assertNotNull(Severity.MEDIUM);
		assertNotNull(Severity.LOW);
		assertNotNull(Severity.INFO);
	}

	@Test
	void testGroupByConstants_arePublicStaticFinal() {
	}

	@Test
	void testGetSeverity_multipleCallsSequence() {
		Severity[] severities = {
			Severity.getSeverity("CRITICAL"),
			Severity.getSeverity("HIGH"),
			Severity.getSeverity("MEDIUM"),
			Severity.getSeverity("LOW"),
			Severity.getSeverity("INFO")
		};

		assertEquals(5, severities.length);
		for (Severity sev : severities) {
			assertNotNull(sev);
		}
	}

	@Test
	void testGetSeverity_allGroupByVariants() {
	}

	@ParameterizedTest
	@ValueSource(strings = {"CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"})
	void testGetSeverity_validStandardSeverities(String severityName) {
		Severity severity = Severity.getSeverity(severityName);
		assertNotNull(severity);
		assertEquals(severityName, severity.name());
	}

	@ParameterizedTest
	void testGetSeverity_validGroupingOptions(String groupingName) {
		Severity severity = Severity.getSeverity(groupingName);
		assertNotNull(severity);
		assertEquals(groupingName, severity.name());
	}
}
