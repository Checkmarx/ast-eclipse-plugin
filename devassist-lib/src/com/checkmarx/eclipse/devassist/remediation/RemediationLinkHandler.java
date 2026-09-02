package com.checkmarx.eclipse.devassist.remediation;

import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.telemetry.TelemetryService;
import com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView;

import static com.checkmarx.eclipse.devassist.utils.DevAssistConstants.SEPERATOR;
import static java.lang.String.format;

/**
 * Handler for remediation actions triggered from tooltips in the editor.
 * This class processes remediation links extracted from hover popups and executes
 * the corresponding actions such as fixing issues, viewing details, or ignoring
 * certain types of issues.
 *
 * Adapted from JetBrains IntelliJ implementation to work with Eclipse's link
 * handling mechanism via browser LocationListener.
 */
public class RemediationLinkHandler {

    private static final String FIX = "copyfixprompt";
    private static final String VIEW_DETAILS = "viewdetails";
    private static final String IGNORE_THIS_TYPE = "ignorethis";
    private static final String IGNORE_ALL_OF_THIS_TYPE = "ignoreallofthis";

    private final RemediationManager remediationManager = new RemediationManager();

    /**
     * Handles a remediation link with a provided scan issue context.
     * This is the primary entry point when a link is clicked in the hover popup.
     *
     * @param link the link string containing action and issue information,
     *             formatted as: action|issueId|engineName
     * @param scanIssue the scan issue context for the remediation action
     * @return true if the link was handled successfully, false otherwise.
     */
    public boolean handleLink(@NonNull String link, @NonNull ScanIssue scanIssue) {
        if (!link.contains(SEPERATOR)) {
            CxLogger.warning("RTS-Fix: Remediation action failed, Link is not valid: " + link);
            return false;
        }

        String[] linkData = link.split(SEPERATOR);
        String scanIssueId = extractIssueId(linkData);
        if (scanIssueId.isEmpty()) {
            CxLogger.warning("RTS-Fix: Remediation action failed, Scan issue id not found in remediation link: " + link);
            return false;
        }

        String action = extractAction(linkData);
        if (action.isEmpty()) {
            CxLogger.warning("RTS-Fix: Remediation action failed, Action not found in remediation link: " + link);
            return false;
        }

        // Note: the scanIssue is already known here, so unlike the other handleLink()
        // overload, a missing engine-name segment (e.g. links built without it, such as
        // "ignorethis"/"ignoreallofthis") must not block the action from running.
        String engineName = extractEngineName(linkData);

        CxLogger.info(format("RTS-Fix: %s Remediation action called for engine: %s with issue id: %s", action, engineName, scanIssueId));

        return handleActions(action, scanIssue, scanIssueId);
    }

    /**
     * Handles a remediation link by extracting action information and searching for the issue.
     * This is an alternative entry point when the scan issue is not readily available
     * and must be retrieved from the ProblemHolderService.
     *
     * @param link the link string containing action and issue information,
     *             formatted as: action|issueId|engineName
     * @return true if the link was handled successfully, false otherwise.
     */
    public boolean handleLink(@NonNull String link) {
        if (!link.contains(SEPERATOR)) {
            CxLogger.warning("RTS-Fix: Remediation action failed, Link is not valid: " + link);
            return false;
        }

        String[] linkData = link.split(SEPERATOR);
        String scanIssueId = extractIssueId(linkData);
        if (scanIssueId.isEmpty()) {
            CxLogger.warning("RTS-Fix: Remediation action failed, Scan issue id not found in remediation link: " + link);
            return false;
        }

        String action = extractAction(linkData);
        if (action.isEmpty()) {
            CxLogger.warning("RTS-Fix: Remediation action failed, Action not found in remediation link: " + link);
            return false;
        }

        String engineName = extractEngineName(linkData);
        if (Objects.isNull(engineName) || engineName.isEmpty()) {
            CxLogger.warning("RTS-Fix: Remediation action failed, Scan engine name not found in remediation link: " + link);
            return false;
        }

        CxLogger.info(format("RTS-Fix: %s Remediation action called for engine: %s with issue id: %s", action, engineName, scanIssueId));

        ScanIssue scanIssue = getScanIssue(scanIssueId, engineName);
        if (Objects.isNull(scanIssue)) {
            CxLogger.warning(format("RTS-Fix: %s Remediation action failed. Scan issue is not found for the given issue-id: %s", action, scanIssueId));
            return false;
        }

        return handleActions(action, scanIssue, scanIssueId);
    }

    /**
     * Handles specific remediation actions for a given scan issue.
     * Depending on the provided action link, it performs appropriate actions
     * such as applying a fix, viewing issue details, or ignoring the issue type.
     *
     * @param action    the remediation action to be performed
     * @param scanIssue the scan issue on which the action is performed
     * @param actionId  the action ID for vulnerability-specific fixes
     * @return true if the action is successfully handled, false otherwise
     */
    private boolean handleActions(@NonNull String action, @NonNull ScanIssue scanIssue, @NonNull String actionId) {
        switch (action) {
            case FIX:
                remediationManager.fixWithCxOneAssist(scanIssue, actionId);
                TelemetryService.logFixWithCxOneAssistAction(scanIssue);
                break;
            case VIEW_DETAILS:
                remediationManager.viewDetails(scanIssue, actionId);
                TelemetryService.logViewDetailsAction(scanIssue);
                break;
            case IGNORE_THIS_TYPE: {
                IProject project = getActiveProject();
                if (Objects.isNull(project)) {
                    CxLogger.warning("RTS-Fix: Remediation action failed, no active project found for IGNORE_THIS_TYPE");
                    return false;
                }
                com.checkmarx.eclipse.devassist.ignore.IgnoreManager.getInstance(project).addIgnoredEntry(scanIssue, actionId);
                TelemetryService.logIgnorePackageAction(scanIssue);
                deleteMarkerForIssue(project, scanIssue);
                refreshFindingsView();
                com.checkmarx.eclipse.devassist.ui.findings.ignore.DevAssistIgnoredFindings.refreshIfOpen();
                break;
            }
            case IGNORE_ALL_OF_THIS_TYPE: {
                IProject project = getActiveProject();
                if (Objects.isNull(project)) {
                    CxLogger.warning("RTS-Fix: Remediation action failed, no active project found for IGNORE_ALL_OF_THIS_TYPE");
                    return false;
                }
                com.checkmarx.eclipse.devassist.ignore.IgnoreManager.getInstance(project).addAllIgnoredEntry(scanIssue, actionId);
                TelemetryService.logIgnoreAllAction(scanIssue);
                deleteMarkersForAllMatches(project, scanIssue);
                refreshFindingsView();
                com.checkmarx.eclipse.devassist.ui.findings.ignore.DevAssistIgnoredFindings.refreshIfOpen();
                break;
            }
            default:
                CxLogger.warning(format("RTS-Fix: Remediation action %s is not supported", action));
                return false;
        }
        return true;
    }

    /**
     * Resolves the active project the same way {@code CxFindingsView} does
     * (single-project workspace assumption used throughout the ignore/revive feature).
     */
    @Nullable
    private org.eclipse.core.resources.IProject getActiveProject() {
        org.eclipse.core.resources.IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (org.eclipse.core.resources.IProject project : projects) {
            if (project.isOpen()) {
                return project;
            }
        }
        return null;
    }

    /**
     * Extracts the engine name from the link data array.
     *
     * @param linkData split link data array
     * @return scan engine name, or empty string if not found
     */
    private String extractEngineName(String[] linkData) {
        return Objects.nonNull(linkData) && linkData.length > 2 ? linkData[2] : "";
    }

    /**
     * Extracts the issue id from the link data array.
     *
     * @param linkData split link data array
     * @return scan issue id, or empty string if not found
     */
    private String extractIssueId(String[] linkData) {
        return Objects.nonNull(linkData) && linkData.length > 1 ? linkData[1] : "";
    }

    /**
     * Extracts the action from the link data array.
     *
     * @param linkData split link data array
     * @return remediation action, or empty string if not found
     */
    private String extractAction(String[] linkData) {
        return Objects.nonNull(linkData) && linkData.length > 0 ? linkData[0] : "";
    }

    /**
     * Retrieves a specific scan issue based on the provided issue ID and engine name.
     * Queries the ProblemHolderService to find the matching issue by either scan issue ID
     * or vulnerability ID. This method iterates through all cached scan issues to find
     * a match.
     *
     * @param issueId    the unique identifier of the scan issue to retrieve
     * @param engineName the scan engine name to match
     * @return the {@link ScanIssue} matching the given issue ID and engine, or null if not found
     */
    @Nullable
    private ScanIssue getScanIssue(@NonNull String issueId, @NonNull String engineName) {
        try {
            CxLogger.warning("RTS-Fix: Searching for scan issue with ID: " + issueId + ", engine: " + engineName);

            // Get all cached scan issues from all files
            java.util.Map<String, List<ScanIssue>> allIssuesMap = getAllCachedScanIssues();
            if (allIssuesMap == null || allIssuesMap.isEmpty()) {
                CxLogger.warning("RTS-Fix: No scan issues found in cache to handle the link");
                return null;
            }

            // Flatten the map into a single list of all issues
            List<ScanIssue> allIssues = new java.util.ArrayList<>();
            for (List<ScanIssue> issueList : allIssuesMap.values()) {
                allIssues.addAll(issueList);
            }

            ScanIssue scanIssue = getScanIssueUsingScanIssueId(allIssues, issueId, engineName);
            if (Objects.isNull(scanIssue)) {
                return getScanIssueUsingVulnerabilityId(allIssues, issueId, engineName);
            }
            return scanIssue;
        } catch (Exception exception) {
            CxLogger.warning("RTS-Fix: Exception occurred while retrieving scan issue");
            return null;
        }
    }

    /**
     * Helper method to get all cached scan issues from the workspace.
     * Since ProblemHolderService is project-scoped, we try to find a cached instance
     * or return an empty map if none are available.
     *
     * @return Map of all cached scan issues (file path → list of issues)
     */
    @Nullable
    private java.util.Map<String, List<ScanIssue>> getAllCachedScanIssues() {
        try {
            // Try to get issues from Eclipse workspace root (project-agnostic approach)
            // In a multi-project workspace, this will only get issues from the currently
            // active project's ProblemHolderService instance. For a complete solution,
            // iterate through all open projects (requires org.eclipse.core.resources.IWorkspace)
            org.eclipse.core.resources.IWorkspaceRoot root =
                org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot();
            org.eclipse.core.resources.IProject[] projects = root.getProjects();

            java.util.Map<String, List<ScanIssue>> combinedIssues = new java.util.HashMap<>();

            for (org.eclipse.core.resources.IProject project : projects) {
                try {
                    if (project.isOpen()) {
                        ProblemHolderService service = ProblemHolderService.getInstance(project);
                        if (service != null) {
                            java.util.Map<String, List<ScanIssue>> projectIssues = service.getAllScanIssues();
                            if (projectIssues != null) {
                                combinedIssues.putAll(projectIssues);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip projects with errors, continue with others
                    CxLogger.warning("RTS-Fix: Error accessing project " + project.getName());
                }
            }

            return combinedIssues;
        } catch (Exception e) {
            CxLogger.warning("RTS-Fix: Error retrieving cached scan issues from workspace");
            return null;
        }
    }

    /**
     * Retrieves the ScanIssue corresponding to the given scan issue ID from the provided list.
     * Matches both the issue ID and the scan engine name.
     *
     * @param scanIssueList list of scan issues to search
     * @param issueId       scan issue id to match
     * @param engineName    scan engine name to match
     * @return the ScanIssue matching the given issueId and engine, or null if no match is found
     */
    @Nullable
    private ScanIssue getScanIssueUsingScanIssueId(@NonNull List<ScanIssue> scanIssueList,
                                                    @NonNull String issueId,
                                                    @NonNull String engineName) {
        return scanIssueList.stream()
                .filter(issue -> Objects.nonNull(issue)
                        && issue.getScanIssueId().equals(issueId)
                        && issue.getScanEngine().name().equalsIgnoreCase(engineName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Retrieves the ScanIssue by searching through vulnerabilities of scan issues.
     * Used when a vulnerability ID is provided instead of a scan issue ID.
     * Matches both the vulnerability ID and the scan engine name.
     *
     * @param scanIssueList list of scan issues to search
     * @param issueId       vulnerability id to match
     * @param engineName    scan engine name to match
     * @return the ScanIssue containing the matching vulnerability, or null if not found
     */
    @Nullable
    private ScanIssue getScanIssueUsingVulnerabilityId(@NonNull List<ScanIssue> scanIssueList,
                                                       @NonNull String issueId,
                                                       @NonNull String engineName) {
        for (ScanIssue scanIssue : scanIssueList) {
            if (Objects.nonNull(scanIssue) && scanIssue.getScanEngine().name().equalsIgnoreCase(engineName)
                    && Objects.nonNull(scanIssue.getVulnerabilities()) && !scanIssue.getVulnerabilities().isEmpty()) {
                for (var vulnerability : scanIssue.getVulnerabilities()) {
                    if (vulnerability.getVulnerabilityId().equals(issueId)) {
                        return scanIssue;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Deletes markers for a single ignored issue from the editor.
     * Finds the file where the issue is located and removes the marker.
     *
     * @param project the active project
     * @param scanIssue the scan issue to find and delete markers for
     */
    private void deleteMarkerForIssue(@NonNull IProject project, @NonNull ScanIssue scanIssue) {
        try {
            String filePath = scanIssue.getFilePath();
            if (filePath == null || filePath.isEmpty()) {
                CxLogger.warning("RTS-Fix: Cannot delete marker, scan issue has no file path");
                return;
            }

            // scanIssue.getFilePath() is an absolute OS path (same format as
            // file.getLocation().toOSString() elsewhere in this codebase), not a
            // project-relative path - IProject.getFile(String) expects the latter and
            // would never resolve here, silently no-oping this whole method.
            IFile file = ResourcesPlugin.getWorkspace().getRoot()
                    .getFileForLocation(new org.eclipse.core.runtime.Path(filePath));
            if (file == null || !file.exists()) {
                CxLogger.warning("RTS-Fix: Cannot delete marker, file not found: " + filePath);
                return;
            }

            IMarker[] markers = file.findMarkers("com.checkmarx.eclipse.plugin.checkmarxProblemMarker", true, IResource.DEPTH_ZERO);
            for (IMarker marker : markers) {
                try {
                    // Match against the actual attribute key MarkerIssueMapper stores the
                    // issue id under ("cx.issueId") - "scanIssueId" was never a real
                    // attribute on these markers, so this comparison always failed.
                    String markerScanIssueId = com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper
                            .getIssueId(marker);
                    if (markerScanIssueId.equals(scanIssue.getScanIssueId())) {
                        marker.delete();
                        CxLogger.info("RTS-Fix: Deleted marker for issue: " + scanIssue.getTitle());
                        return;
                    }
                } catch (Exception e) {
                    CxLogger.warning("RTS-Fix: Error checking marker attribute: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            CxLogger.error("RTS-Fix: Error deleting marker for ignored issue", e);
        }
    }

    /**
     * Deletes all markers for issues matching the ignore-all criteria.
     * For OSS/CONTAINERS, finds all markers with the same package/image.
     *
     * @param project the active project
     * @param scanIssue the scan issue to match against
     */
    private void deleteMarkersForAllMatches(@NonNull IProject project, @NonNull ScanIssue scanIssue) {
        try {
            IMarker[] allMarkers = project.findMarkers("com.checkmarx.eclipse.plugin.checkmarxProblemMarker", true, IResource.DEPTH_INFINITE);
            int deletedCount = 0;

            for (IMarker marker : allMarkers) {
                try {
                    String markerTitle = marker.getAttribute(IMarker.MESSAGE, "");
                    if (markerTitle.contains(scanIssue.getTitle())) {
                        marker.delete();
                        deletedCount++;
                    }
                } catch (Exception e) {
                    CxLogger.warning("RTS-Fix: Error checking marker for deletion: " + e.getMessage());
                }
            }

            if (deletedCount > 0) {
                CxLogger.info("RTS-Fix: Deleted " + deletedCount + " markers for ignore-all action");
            }
        } catch (Exception e) {
            CxLogger.error("RTS-Fix: Error deleting markers for ignore-all", e);
        }
    }

    /**
     * Refreshes the Findings View to update the tree and remove ignored findings.
     * Finds the CxFindingsView instance and calls refreshTreeWithFilter() on it.
     */
    private void refreshFindingsView() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) {
                CxLogger.warning("RTS-Fix: Cannot refresh findings view, no active workbench window");
                return;
            }

            IWorkbenchPage page = window.getActivePage();
            if (page == null) {
                CxLogger.warning("RTS-Fix: Cannot refresh findings view, no active workbench page");
                return;
            }

            CxFindingsView view = (CxFindingsView) page.findView(CxFindingsView.ID);
            if (view == null) {
                CxLogger.warning("RTS-Fix: Cannot refresh findings view, view not found");
                return;
            }

            view.refreshTreeWithFilter();
            CxLogger.info("RTS-Fix: Refreshed Findings View");
        } catch (Exception e) {
            CxLogger.error("RTS-Fix: Error refreshing findings view", e);
        }
    }
}
