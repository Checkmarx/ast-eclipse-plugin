package checkmarx.ast.eclipse.plugin.tests.unit.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.common.enums.Severity;

class SeverityTest {

    @Test
    void testGetSeverity_critical() {
        assertEquals(Severity.CRITICAL, Severity.getSeverity("CRITICAL"));
    }

    @Test
    void testGetSeverity_high() {
        assertEquals(Severity.HIGH, Severity.getSeverity("HIGH"));
    }

    @Test
    void testGetSeverity_medium() {
        assertEquals(Severity.MEDIUM, Severity.getSeverity("MEDIUM"));
    }

    @Test
    void testGetSeverity_low() {
        assertEquals(Severity.LOW, Severity.getSeverity("LOW"));
    }

    @Test
    void testGetSeverity_info() {
        assertEquals(Severity.INFO, Severity.getSeverity("INFO"));
    }

    @Test
    void testGetSeverity_unknownValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Severity.getSeverity("UNKNOWN_SEVERITY_XYZ"));
    }

    @Test
    void testGetSeverity_roundTrip_allValues() {
        for (Severity severity : Severity.values()) {
            assertEquals(severity, Severity.getSeverity(severity.name()));
        }
    }

    @Test
    void testEnumValues_count() {
        assertEquals(8, Severity.values().length);
    }
}
