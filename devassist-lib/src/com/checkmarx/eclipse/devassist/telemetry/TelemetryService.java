package com.checkmarx.eclipse.devassist.telemetry;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.Platform;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.wrapper.CxWrapperFactory;
import com.checkmarx.eclipse.devassist.backend.Constants;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;

import static java.lang.String.format;

/**
 * Telemetry service for logging user interactions with remediation actions
 * (Fix with CxOne Assist, View Details, Ignore, Ignore All).
 * Mirrors the JetBrains plugin's TelemetryService: every event is sent
 * asynchronously via the Checkmarx CLI wrapper's telemetryAIEvent, and a
 * failure to send is only logged - it never surfaces to the user or blocks
 * the action being reported on.
 */
public final class TelemetryService {

    // AI Provider constant
    private static final String AI_PROVIDER = "Copilot";

    // Agent name base, matching JetBrains' "Jetbrains <IDE name>" pattern
    private static final String ECLIPSE_AGENT_NAME = "Eclipse";

    // Event Types
    private static final String EVENT_TYPE_CLICK = "click";

    // Sub Types
    private static final String SUB_TYPE_FIX_WITH_AI_CHAT = "fixWithAIChat";
    private static final String SUB_TYPE_VIEW_DETAILS = "viewDetails";
    private static final String SUB_TYPE_IGNORE_PACKAGE = "ignorePackage";
    private static final String SUB_TYPE_IGNORE_ALL = "ignoreAll";

    // Engine Names
    private static final String ENGINE_OSS = "Oss";
    private static final String ENGINE_SECRETS = "Secrets";
    private static final String ENGINE_IAC = "IaC";
    private static final String ENGINE_ASCA = "Asca";
    private static final String ENGINE_CONTAINERS = "Containers";

    private TelemetryService() {
    }

    /**
     * Sends a user-action ("click") telemetry event.
     *
     * @param eventType       the type of event (e.g., "click")
     * @param subType         the specific action (e.g., "fixWithAIChat", "viewDetails")
     * @param engine          the scan engine type
     * @param problemSeverity the severity of the issue
     */
    public static void setUserEventDataForLogs(String eventType, String subType, String engine, String problemSeverity) {
        CompletableFuture.runAsync(() -> {
            try {
                CxWrapperFactory.build().telemetryAIEvent(
                        AI_PROVIDER,      // aiProvider
                        getAgentName(),   // agent
                        eventType,        // eventType
                        subType,          // subType
                        engine,           // engine
                        problemSeverity,  // problemSeverity
                        "",               // scanType
                        "",               // status
                        0                 // totalCount
                );
            } catch (Exception e) {
                CxLogger.warning(format("Telemetry: Failed to log user event telemetry for %s - %s", subType, e.getMessage()));
            }
        });
    }

    /**
     * Logs a user action for a given scan issue.
     *
     * @param scanIssue     the scan issue being acted upon
     * @param actionSubType the specific action sub-type for telemetry
     * @param actionName    the action name, used only for logging
     */
    private static void logUserAction(ScanIssue scanIssue, String actionSubType, String actionName) {
        if (Objects.isNull(scanIssue)) {
            CxLogger.warning("Telemetry: Cannot log " + actionName + " action - scan issue is null");
            return;
        }

        String engine = mapScanEngineToTelemetryEngine(scanIssue.getScanEngine());
        String severity = normalizeSeverity(scanIssue.getSeverity());

        setUserEventDataForLogs(EVENT_TYPE_CLICK, actionSubType, engine, severity);
    }

    /**
     * Logs user action for "Fix with CxOne Assist".
     *
     * @param scanIssue the scan issue being acted upon
     */
    public static void logFixWithCxOneAssistAction(ScanIssue scanIssue) {
        logUserAction(scanIssue, SUB_TYPE_FIX_WITH_AI_CHAT, "Fix with CxOne Assist");
    }

    /**
     * Logs user action for "View Details".
     *
     * @param scanIssue the scan issue being acted upon
     */
    public static void logViewDetailsAction(ScanIssue scanIssue) {
        logUserAction(scanIssue, SUB_TYPE_VIEW_DETAILS, "View Details");
    }

    /**
     * Logs user action for "Ignore this vulnerability".
     *
     * @param scanIssue the scan issue being acted upon
     */
    public static void logIgnorePackageAction(ScanIssue scanIssue) {
        logUserAction(scanIssue, SUB_TYPE_IGNORE_PACKAGE, "Ignore Package");
    }

    /**
     * Logs user action for "Ignore all of this type".
     *
     * @param scanIssue the scan issue being acted upon
     */
    public static void logIgnoreAllAction(ScanIssue scanIssue) {
        logUserAction(scanIssue, SUB_TYPE_IGNORE_ALL, "Ignore All");
    }

    /**
     * Maps the model's ScanEngine enum to the telemetry engine string expected
     * by the backend (matching JetBrains' naming exactly).
     */
    private static String mapScanEngineToTelemetryEngine(ScanEngine scanEngine) {
        if (Objects.isNull(scanEngine)) {
            return ENGINE_OSS; // default fallback
        }

        switch (scanEngine) {
            case OSS:
                return ENGINE_OSS;
            case SECRETS:
                return ENGINE_SECRETS;
            case IAC:
                return ENGINE_IAC;
            case ASCA:
                return ENGINE_ASCA;
            case CONTAINERS:
                return ENGINE_CONTAINERS;
            default:
                return ENGINE_OSS; // default fallback
        }
    }

    /**
     * Normalizes severity strings to match the existing Constants format.
     */
    private static String normalizeSeverity(String severity) {
        if (Objects.isNull(severity) || severity.trim().isEmpty()) {
            return Constants.UNKNOWN;
        }

        switch (severity.toLowerCase().trim()) {
            case "critical":
                return Constants.CRITICAL_SEVERITY;
            case "high":
                return Constants.HIGH_SEVERITY;
            case "medium":
                return Constants.MEDIUM_SEVERITY;
            case "low":
                return Constants.LOW_SEVERITY;
            case "malicious":
                return Constants.MALICIOUS_SEVERITY;
            default:
                return Constants.UNKNOWN;
        }
    }

    /**
     * Gets the agent name based on the running Eclipse platform version.
     */
    private static String getAgentName() {
        try {
            String version = Platform.getBundle("org.eclipse.platform") != null
                    ? Platform.getBundle("org.eclipse.platform").getVersion().toString()
                    : null;
            if (version != null) {
                return ECLIPSE_AGENT_NAME + " " + version;
            }
        } catch (Exception e) {
            CxLogger.warning("Telemetry: Could not determine Eclipse platform version, using default - " + e.getMessage());
        }

        return ECLIPSE_AGENT_NAME;
    }
}
