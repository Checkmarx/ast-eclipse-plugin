package com.checkmarx.eclipse.devassist.telemetry;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.Platform;

import com.checkmarx.ast.telemetry.TelemetryEvent;
import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.common.runner.Authenticator;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.utils.PluginConstants;
import com.checkmarx.eclipse.common.wrapper.CxWrapperFactory;
import com.checkmarx.eclipse.devassist.backend.Constants;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;

import static java.lang.String.format;

/**
 * Telemetry service for the Dev Assist analytics events (AST-184122): login,
 * scan and remediation/click events.
 * Every event is sent asynchronously via the Checkmarx CLI wrapper's
 * telemetryAIEvent, and a failure to send is only logged - it never surfaces to
 * the user or blocks the action being reported on.
 */
public final class TelemetryService {

    // AI Provider constant
    private static final String AI_PROVIDER = "Copilot";

    // Sent as the per-event agent (version goes in agentVersion). The agent name used
    // for every other CLI call (CxConfig.agentName) is left unchanged.
    private static final String ECLIPSE_AGENT_NAME = "Eclipse";

    // Event Types
    private static final String EVENT_TYPE_CLICK = "Click";
    private static final String EVENT_TYPE_AUTH = "authentication";
    private static final String EVENT_TYPE_SCAN = "Scan";

    // Common values
    private static final String PLUGIN_TYPE = "IDE";
    private static final String API_VERSION = "v2";
    private static final String AUTH_METHOD_API_KEY = "API_KEY";
    private static final String REMEDIATION_MODE = "DevAssist";
    // Sent on every event so Analytics can tell the Checkmarx One and Dev Assist plugins apart
    private static final String PLUGIN_NAME_DEV_ASSIST = "DevAssist";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILURE = "failure";
    private static final int MAX_STATUS_RESULT_LENGTH = 500;

    // Event attribute keys
    private static final String ATTR_PLUGIN_NAME = "pluginName";
    private static final String ATTR_API_VERSION = "apiVersion";
    private static final String ATTR_AUTH_METHOD = "authMethod";
    private static final String ATTR_VULNERABILITY_ID = "vulnerabilityId";
    private static final String ATTR_REMEDIATION_MODE = "remediationMode";

    // Sub Types
    private static final String SUB_TYPE_AUTH = "authentication";
    // Spelling taken from the design's routing table (AST-184122, open question 3)
    private static final String SUB_TYPE_SCAN = "scantriggerd";
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

    private static final String[] SEVERITY_LABELS = { "Critical", "High", "Medium", "Low", "Info" };

    /**
     * All events go through one single-thread executor so they reach the CLI in the
     * order they were enqueued (the CLI creates the session id on the login event,
     * which must come before any scan/remediation event). Daemon thread: it never
     * blocks IDE shutdown.
     */
    private static final ExecutorService SENDER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cx-telemetry-sender");
        t.setDaemon(true);
        return t;
    });

    private TelemetryService() {
    }

    /**
     * Sends a user-action ("Click") telemetry event with success status.
     *
     * @param eventType       the type of event (e.g., "Click")
     * @param subType         the specific action (e.g., "fixWithAIChat", "viewDetails")
     * @param engine          the scan engine type
     * @param problemSeverity the severity of the issue
     */
    public static void setUserEventDataForLogs(String eventType, String subType, String engine, String problemSeverity) {
        send(buildClickEvent(eventType, subType, engine, problemSeverity, null, STATUS_SUCCESS, ""));
    }

    /**
     * Enqueues the event; sending is asynchronous and a failure is only logged.
     */
    private static void send(TelemetryEvent event) {
        try {
            SENDER.execute(() -> sendNow(event));
        } catch (Exception e) {
            CxLogger.warning(format("Telemetry: Could not enqueue event - %s", e.getMessage()));
        }
    }

    /** Sends the event on the calling (sender) thread; a failure is only logged. */
    private static void sendNow(TelemetryEvent event) {
        try {
            CxWrapperFactory.build().telemetryAIEvent(event);
        } catch (Exception e) {
            CxLogger.warning(format("Telemetry: Failed to send %s/%s event - %s", event.getEventType(),
                    event.getSubType(), e.getMessage()));
        }
    }

    private static final AtomicBoolean STARTUP_LOGIN_SENT = new AtomicBoolean(false);

    /**
     * Sends one login event per Eclipse launch for a user who is already logged in
     * (the login flag is persisted and never re-validated at startup). The stored
     * credentials are validated on the sender thread - so no UI blocking, and every
     * later scan/remediation event is queued behind this login - and the event reports
     * the real result. The stored {@code credentialsValidated} flag is not changed.
     */
    public static void logStartupLoginEvent() {
        if (!Preferences.isAuthenticated() || !STARTUP_LOGIN_SENT.compareAndSet(false, true)) {
            return;
        }
        try {
            SENDER.execute(() -> {
                String result = Authenticator.INSTANCE.doAuthentication(Preferences.getApiKey(),
                        Preferences.getAdditionalOptions());
                boolean success = result != null && result.contains(PluginConstants.AUTH_SUCCESS_PATTERN);
                sendNow(buildLoginEvent(success, success ? null : result));
            });
        } catch (Exception e) {
            CxLogger.warning(format("Telemetry: Could not enqueue startup login event - %s", e.getMessage()));
        }
    }

    private static TelemetryEvent.TelemetryEventBuilder baseEvent(String eventType, String subType, String status,
            String statusResult) {
        return TelemetryEvent.builder()
                .eventType(eventType)
                .subType(subType)
                .status(status)
                .statusResult(statusResult)
                .agent(ECLIPSE_AGENT_NAME)
                .agentVersion(getAgentVersion())
                .cxPluginVersion(CxWrapperFactory.getPluginVersion())
                .pluginType(PLUGIN_TYPE)
                .eventAttribute(ATTR_PLUGIN_NAME, PLUGIN_NAME_DEV_ASSIST);
    }

    /**
     * Builds a click/remediation event. For "fixWithAIChat" the remediation mode and
     * (when not blank) the vulnerability id are added as event attributes.
     */
    public static TelemetryEvent buildClickEvent(String eventType, String subType, String engine,
            String problemSeverity, String vulnerabilityId, String status, String statusResult) {
        TelemetryEvent.TelemetryEventBuilder builder = baseEvent(eventType, subType, status, statusResult)
                .aiProvider(AI_PROVIDER)
                .engine(engine)
                .problemSeverity(problemSeverity);
        if (SUB_TYPE_FIX_WITH_AI_CHAT.equals(subType)) {
            builder.eventAttribute(ATTR_REMEDIATION_MODE, REMEDIATION_MODE);
            if (vulnerabilityId != null && !vulnerabilityId.trim().isEmpty()) {
                builder.eventAttribute(ATTR_VULNERABILITY_ID, vulnerabilityId.trim());
            }
        }
        return builder.build();
    }

    /** Builds the login event. {@code failureReason} is used only when {@code success} is false. */
    public static TelemetryEvent buildLoginEvent(boolean success, String failureReason) {
        return baseEvent(EVENT_TYPE_AUTH, SUB_TYPE_AUTH, success ? STATUS_SUCCESS : STATUS_FAILURE,
                success ? "" : failureStatusResult("Authentication failed", failureReason))
                .eventAttribute(ATTR_AUTH_METHOD, AUTH_METHOD_API_KEY)
                .build();
    }

    /**
     * Builds a successful scan event for one engine: severity counts in
     * {@code statusResult}, highest severity in {@code problemSeverity}, total in
     * {@code totalCount}. A scan without findings is reported too.
     *
     * @param engine     telemetry engine name (Oss, Secrets, IaC, Asca, Containers)
     * @param severities raw severity of every finding of this engine
     */
    public static TelemetryEvent buildScanEvent(String engine, List<String> severities) {
        int[] counts = new int[SEVERITY_LABELS.length];
        if (severities != null) {
            for (String severity : severities) {
                int index = severityIndex(severity);
                if (index >= 0) {
                    counts[index]++;
                }
            }
        }
        int total = 0;
        String highest = "";
        StringBuilder statusResult = new StringBuilder();
        for (int i = 0; i < SEVERITY_LABELS.length; i++) {
            total += counts[i];
            if (highest.isEmpty() && counts[i] > 0) {
                highest = SEVERITY_LABELS[i];
            }
            if (i > 0) {
                statusResult.append(", ");
            }
            statusResult.append(SEVERITY_LABELS[i]).append('=').append(counts[i]);
        }
        return baseEvent(EVENT_TYPE_SCAN, SUB_TYPE_SCAN, STATUS_SUCCESS, statusResult.toString())
                .engine(engine)
                .problemSeverity(highest)
                .totalCount(total)
                .apiVersion(API_VERSION)
                .eventAttribute(ATTR_API_VERSION, API_VERSION)
                .build();
    }

    /** Builds a failed scan event for one engine. */
    public static TelemetryEvent buildScanFailureEvent(String engine, String failureReason) {
        return baseEvent(EVENT_TYPE_SCAN, SUB_TYPE_SCAN, STATUS_FAILURE,
                failureStatusResult("Scan failed", failureReason))
                .engine(engine)
                .apiVersion(API_VERSION)
                .eventAttribute(ATTR_API_VERSION, API_VERSION)
                .build();
    }

    /**
     * Maps a finding severity to an index in {@link #SEVERITY_LABELS}. Malicious
     * counts as Critical, anything unrecognised as Info, and "OK" is not a finding.
     */
    private static int severityIndex(String severity) {
        String raw = severity == null ? "unknown" : severity.trim().toLowerCase();
        switch (raw) {
            case "critical":
            case "malicious":
                return 0;
            case "high":
                return 1;
            case "medium":
                return 2;
            case "low":
                return 3;
            case "ok":
                return -1;
            default:
                return 4;
        }
    }

    private static String failureStatusResult(String prefix, String reason) {
        return prefix + ". Reason: " + sanitize(reason == null || reason.trim().isEmpty() ? "unknown" : reason)
                + ", Error Code: N/A";
    }

    /**
     * Removes anything that looks like a credential from free text, collapses
     * whitespace and truncates it.
     */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String result = text
                .replaceAll("(?i)([a-z][a-z0-9+.-]*://)[^\\s/@]+@", "$1***@")
                .replaceAll("(?i)\\b(api[-_]?key|apikey|token|secret|password)\\s*[:=]\\s*[^\\s,;]+", "$1=***")
                .replaceAll("(?i)\\b(bearer|basic)\\s+[^\\s,;]+", "$1 ***")
                .replaceAll("\\beyJ[\\w-]+\\.[\\w-]+\\.[\\w-]+", "***")
                .replaceAll("[A-Za-z0-9_\\-+/]{32,}={0,2}", "***")
                .replaceAll("\\s+", " ")
                .trim();
        return result.length() > MAX_STATUS_RESULT_LENGTH ? result.substring(0, MAX_STATUS_RESULT_LENGTH) : result;
    }

    /**
     * Logs a login attempt (API key). Must be called before anything that can start a
     * scan, so the CLI session id exists before the first scan event.
     *
     * @param success       whether authentication succeeded
     * @param failureReason reason shown in {@code statusResult} on failure
     */
    public static void logLoginEvent(boolean success, String failureReason) {
        send(buildLoginEvent(success, failureReason));
    }

    /**
     * Logs one scan event for an engine whose scan completed.
     *
     * @param scanEngine the engine that was scanned
     * @param severities severity of every finding the engine returned
     */
    public static void logScanEvent(ScanEngine scanEngine, List<String> severities) {
        send(buildScanEvent(mapScanEngineToTelemetryEngine(scanEngine), severities));
    }

    /**
     * Logs a failed scan for an engine.
     */
    public static void logScanFailureEvent(ScanEngine scanEngine, String failureReason) {
        send(buildScanFailureEvent(mapScanEngineToTelemetryEngine(scanEngine), failureReason));
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

        send(buildClickEvent(EVENT_TYPE_CLICK, actionSubType, engine, severity, scanIssue.getCve(), STATUS_SUCCESS, ""));
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
     * Logs a "Fix with CxOne Assist" action that failed.
     *
     * @param scanIssue     the scan issue being acted upon
     * @param failureReason why the fix could not be dispatched
     */
    public static void logFixWithCxOneAssistFailure(ScanIssue scanIssue, String failureReason) {
        if (Objects.isNull(scanIssue)) {
            CxLogger.warning("Telemetry: Cannot log Fix with CxOne Assist failure - scan issue is null");
            return;
        }
        send(buildClickEvent(EVENT_TYPE_CLICK, SUB_TYPE_FIX_WITH_AI_CHAT,
                mapScanEngineToTelemetryEngine(scanIssue.getScanEngine()), normalizeSeverity(scanIssue.getSeverity()),
                scanIssue.getCve(), STATUS_FAILURE, failureStatusResult("Remediation failed", failureReason)));
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
    public static String mapScanEngineToTelemetryEngine(ScanEngine scanEngine) {
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
     * Gets the Eclipse platform version, sent as {@code agentVersion}.
     */
    private static String getAgentVersion() {
        try {
            if (Platform.getBundle("org.eclipse.platform") != null) {
                return Platform.getBundle("org.eclipse.platform").getVersion().toString();
            }
        } catch (Exception e) {
            CxLogger.warning("Telemetry: Could not determine Eclipse platform version - " + e.getMessage());
        }
        return "";
    }
}
