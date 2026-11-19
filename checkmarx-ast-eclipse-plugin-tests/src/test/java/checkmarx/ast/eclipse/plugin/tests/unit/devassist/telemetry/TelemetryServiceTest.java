package checkmarx.ast.eclipse.plugin.tests.unit.devassist.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals("DevAssist", event.getEventAttributes().get("pluginName"));
    }

    @Test
    void loginSuccess() {
        TelemetryEvent event = TelemetryService.buildLoginEvent(true, null);

        assertCommonFields(event);
        assertEquals("authentication", event.getEventType());
        assertEquals("authentication", event.getSubType());
        assertEquals("success", event.getStatus());
        assertEquals("Authentication successful.", event.getStatusResult());
        assertEquals("APIKey", event.getEventAttributes().get("authMethod"));
        assertFalse(event.getEventAttributes().get("loginTimestamp").isEmpty());
        java.time.Instant.parse(event.getEventAttributes().get("loginTimestamp"));
    }

    @Test
    void installEventCarriesIdAndTimestamp() {
        TelemetryEvent event = TelemetryService.buildInstallEvent("eclipse-1234", "2026-11-19T08:00:00Z");

        assertCommonFields(event);
        assertEquals("install", event.getEventType());
        assertEquals("installation", event.getSubType());
        assertEquals("success", event.getStatus());
        assertEquals("Plugin installed successfully.", event.getStatusResult());
        assertEquals("eclipse-1234", event.getEventAttributes().get("installationId"));
        assertEquals("2026-11-19T08:00:00Z", event.getEventAttributes().get("installationTimestamp"));
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
        assertEquals("v2", event.getEventAttributes().get("apiVersion"));
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
        assertEquals("Remediation completed successfully.", event.getStatusResult());
    }

    @Test
    void clickEventAndScanEventCarryRepoUrl() {
        assertEquals("https://github.com/org/repo", TelemetryService.buildClickEvent("click", "fixWithAIChat", "Oss",
                "High", "x@1", "success", "", "https://github.com/org/repo").getRepoUrl());
        assertEquals("https://github.com/org/repo", TelemetryService.buildScanEvent("Oss", Collections.emptyList(),
                "https://github.com/org/repo").getRepoUrl());
    }

    @Test
    void gitRemoteResolverReadsOriginAndStripsCredentials(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws java.io.IOException {
        java.nio.file.Path git = java.nio.file.Files.createDirectories(dir.resolve(".git"));
        java.nio.file.Files.write(git.resolve("config"), Arrays.asList("[core]", "	bare = false",
                "[remote \"upstream\"]", "	url = https://github.com/other/repo.git",
                "[remote \"origin\"]", "	url = https://user:tok@github.com/org/repo.git"));
        java.nio.file.Path file = java.nio.file.Files.createDirectories(dir.resolve("src")).resolve("a.java");

        assertEquals("https://github.com/org/repo.git",
                com.checkmarx.eclipse.devassist.telemetry.GitRemoteResolver.resolve(file.toString()));
        assertNull(com.checkmarx.eclipse.devassist.telemetry.GitRemoteResolver.resolve(null));
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

        assertFalse(event.getEventAttributes().containsKey("remediationMode"));
        assertFalse(event.getEventAttributes().containsKey("vulnerabilityId"));
        assertNull(event.getApiVersion());
    }

    @Test
    void sanitizeRemovesCredentialsAndTruncates() {
        assertEquals("https://***@host/x", TelemetryService.sanitize("https://user:pw@host/x"));
        assertEquals("Bearer ***", TelemetryService.sanitize("Bearer abc.def"));
        assertEquals(256, TelemetryService.sanitize("a ".repeat(400)).length());
    }
}
