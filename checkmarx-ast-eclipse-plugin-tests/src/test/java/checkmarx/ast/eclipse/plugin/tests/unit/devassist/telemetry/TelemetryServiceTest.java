package checkmarx.ast.eclipse.plugin.tests.unit.devassist.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.checkmarx.ast.telemetry.TelemetryEvent;
import com.checkmarx.eclipse.devassist.telemetry.TelemetryService;

/**
 * Verifies the telemetry events built for login, scan and remediation (AST-184122).
 */
class TelemetryServiceTest {

    private static void assertCommonFields(TelemetryEvent event) {
        assertEquals("Eclipse", event.getAgent());
        assertEquals("IDE", event.getPluginType());
        assertFalse(event.getCxPluginVersion() == null || event.getCxPluginVersion().isEmpty());
    }

    @Test
    void loginSuccess() {
        TelemetryEvent event = TelemetryService.buildLoginEvent(true, null);

        assertCommonFields(event);
        assertEquals("authentication", event.getEventType());
        assertEquals("authentication", event.getSubType());
        assertEquals("success", event.getStatus());
        assertEquals("", event.getStatusResult());
        assertEquals("API_KEY", event.getEventAttributes().get("authMethod"));
    }

    @Test
    void loginFailureCarriesSanitizedReason() {
        TelemetryEvent event = TelemetryService.buildLoginEvent(false, "bad key apikey=abc123 expired");

        assertEquals("failure", event.getStatus());
        assertEquals("Authentication failed. Reason: bad key apikey=*** expired, Error Code: N/A",
                event.getStatusResult());
    }

    @Test
    void scanEventCountsSeveritiesAndReportsHighest() {
        TelemetryEvent event = TelemetryService.buildScanEvent("Oss",
                Arrays.asList("High", "High", "Medium", "Malicious", "Unknown", "OK", null));

        assertCommonFields(event);
        assertEquals("Scan", event.getEventType());
        assertEquals("scantriggerd", event.getSubType());
        assertEquals("success", event.getStatus());
        assertEquals("Oss", event.getEngine());
        assertEquals("Critical", event.getProblemSeverity());
        assertEquals(Integer.valueOf(6), event.getTotalCount());
        assertEquals("Critical=1, High=2, Medium=1, Low=0, Info=2", event.getStatusResult());
        assertEquals("v2", event.getApiVersion());
    }

    @Test
    void scanWithoutFindingsIsStillReported() {
        TelemetryEvent event = TelemetryService.buildScanEvent("Secrets", Collections.emptyList());

        assertEquals("success", event.getStatus());
        assertEquals("", event.getProblemSeverity());
        assertEquals(Integer.valueOf(0), event.getTotalCount());
        assertEquals("Critical=0, High=0, Medium=0, Low=0, Info=0", event.getStatusResult());
    }

    @Test
    void scanFailure() {
        TelemetryEvent event = TelemetryService.buildScanFailureEvent("IaC", "boom");

        assertEquals("Scan", event.getEventType());
        assertEquals("failure", event.getStatus());
        assertEquals("IaC", event.getEngine());
        assertEquals("Scan failed. Reason: boom, Error Code: N/A", event.getStatusResult());
    }

    @Test
    void remediationEventHasModeAndVulnerabilityId() {
        TelemetryEvent event = TelemetryService.buildClickEvent("Click", "fixWithAIChat", "Oss", "High",
                "CVE-2024-1", "success", "");

        assertCommonFields(event);
        assertEquals("Click", event.getEventType());
        assertEquals("fixWithAIChat", event.getSubType());
        assertEquals("Copilot", event.getAiProvider());
        assertEquals("Oss", event.getEngine());
        assertEquals("High", event.getProblemSeverity());
        assertEquals("DevAssist", event.getEventAttributes().get("remediationMode"));
        assertEquals("CVE-2024-1", event.getEventAttributes().get("vulnerabilityId"));
    }

    @Test
    void remediationEventOmitsBlankVulnerabilityId() {
        TelemetryEvent event = TelemetryService.buildClickEvent("Click", "fixWithAIChat", "Secrets", "High", "  ",
                "failure", "Remediation failed. Reason: x, Error Code: N/A");

        assertFalse(event.getEventAttributes().containsKey("vulnerabilityId"));
        assertEquals("failure", event.getStatus());
    }

    @Test
    void otherClickEventsHaveNoRemediationAttributes() {
        TelemetryEvent event = TelemetryService.buildClickEvent("Click", "viewDetails", "Oss", "Low", "CVE-1",
                "success", "");

        assertTrue(event.getEventAttributes() == null || event.getEventAttributes().isEmpty());
        assertNull(event.getApiVersion());
    }

    @Test
    void sanitizeRemovesCredentialsAndTruncates() {
        assertEquals("https://***@host/x", TelemetryService.sanitize("https://user:pw@host/x"));
        assertEquals("Bearer ***", TelemetryService.sanitize("Bearer abc.def"));
        assertEquals(500, TelemetryService.sanitize("a ".repeat(400)).length());
    }
}
