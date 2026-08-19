package com.checkmarx.eclipse.devassist.ignore;
import org.eclipse.core.resources.IProject;
import com.checkmarx.eclipse.common.utils.CxLogger;



import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;

import static com.checkmarx.eclipse.devassist.utils.DevAssistConstants.QUICK_FIX;
import static java.lang.String.format;

/**
 * Manages the ignore file (.checkmarxIgnored) within the project's workspace.
 * Handles reading, writing, and updating ignore entries.
 * Monitors the ignore file for changes and updates internal state accordingly.
 * Provides methods to ignore issues and update temporary ignore lists.
 */
public final class IgnoreManager {
    private static IgnoreManager instance;
    private final org.eclipse.core.resources.IProject project;
    private final ProblemHolderService problemHolder;
    private final IgnoreFileManager ignoreFileManager;

    public IgnoreManager(org.eclipse.core.resources.IProject project) {
        this.project = project;
        this.problemHolder = ProblemHolderService.getInstance(project);
        this.ignoreFileManager = IgnoreFileManager.getInstance(project);
    }

    public static synchronized IgnoreManager getInstance(org.eclipse.core.resources.IProject project) {
        if (instance == null || instance.project != project) {
            instance = new IgnoreManager(project);
        }
        return instance;
    }

    /**
     * Adds an entry to the ignore file.
     * After clicking on the ignore this vulnerability butten
     * Removes the corresponding scan issue from the problem holder.
     *
     * @param issueToIgnore The scan issue to ignore
     * @param clickId       The ID of the clicked action or vulnerability, used to retrieve additional details
     */
    public void addIgnoredEntry(ScanIssue issueToIgnore, String clickId) {
        CxLogger.info(String.format("RTS-Ignore: Adding ignore entry for issue: %s", issueToIgnore.getTitle()));

        String vulnerabilityKey = createJsonKeyForIgnoreEntry(issueToIgnore, clickId);
        if (vulnerabilityKey.isEmpty()) {
            CxLogger.info("RTS-Ignore: Ignoring vulnerability failed. Vulnerability key is empty.");
            return;
        }
        // Convert ScanIssue → IgnoreEntry
        IgnoreEntry ignoreEntry = buildIgnoreEntry(issueToIgnore, clickId);
        if (Objects.isNull(ignoreEntry)) {
            // Notification removed: use Eclipse MessageDialog instead
            return;
        }
        CxLogger.info(String.format("RTS-Ignore: Ignoring %s", vulnerabilityKey));
        ignoreFileManager.updateIgnoreData(vulnerabilityKey, ignoreEntry);
//         scanFileAndUpdateResults(issueToIgnore.getFilePath(), issueToIgnore.getScanEngine());
//         showIgnoreSuccessNotification(project, issueToIgnore, vulnerabilityKey);
        CxLogger.info(String.format("RTS-Ignore: Successfully added ignore entry for issue: %s", issueToIgnore.getTitle()));
    }


    /**
     * Adds an entry to the ignore file for all occurrences of the specified issue.
     * This method performs the following steps:
     * 1. Creates a vulnerability key for the given issue
     * 2. Gets all issues from the problem holder and creates a deep copy
     * 3. Creates an ignore entry for the issue
     * 4. Iterates through all issues and adds matching ones to the ignore list
     * 5. Updates the ignore file and removes the issues from the problem holder
     *
     * @param issueToIgnore The scan issue to ignore across all files
     * @param clickId       The ID that was clicked to trigger the ignore action
     */
    public void addAllIgnoredEntry(ScanIssue issueToIgnore, String clickId) {
        CxLogger.info(String.format("RTS-Ignore: Adding ignore entry for issue: %s", issueToIgnore.getTitle()));
        String vulnerabilityKey = createJsonKeyForIgnoreEntry(issueToIgnore, clickId);
        CxLogger.info("RTS-Ignore: Ignoring all vulnerabilities for: " + vulnerabilityKey);
        Map<String, List<ScanIssue>> allIssues = new HashMap<>();
        for (Map.Entry<String, List<ScanIssue>> entry : problemHolder.getAllScanIssues().entrySet()) {
            allIssues.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        if (allIssues.isEmpty()) return;
        IgnoreEntry ignoreEntry = buildIgnoreEntry(issueToIgnore, clickId);
        if (Objects.isNull(ignoreEntry)) {
            // Notification removed: use Eclipse MessageDialog instead
            return;
        }
        List<IgnoreEntry.FileReference> fileRefs = new ArrayList<>();
        for (List<ScanIssue> issues : allIssues.values()) {  // Safe: allIssues never mutates
            issues.removeIf(issue -> {
                if (!createJsonKeyForIgnoreEntry(issue, clickId).equals(vulnerabilityKey)) return false;
                // Mutate LIVE problemHolder (async-safe)
                fileRefs.add(new IgnoreEntry.FileReference(
                        ignoreFileManager.normalizePath(issue.getFilePath()),
                        true,
                        issue.getLocations().get(0).getLine(), ""));
//                 scanFileAndUpdateResults(issue.getFilePath(), issue.getScanEngine());
                return true;
            });
        }
        ignoreEntry.files = fileRefs;
        ignoreFileManager.updateIgnoreData(vulnerabilityKey, ignoreEntry);
        CxLogger.info(String.format("RTS-Ignore: Successfully added ignore entry for issue: %s", issueToIgnore.getTitle()));
//         showIgnoreSuccessNotification(project, issueToIgnore, vulnerabilityKey);
    }


    /**
     * Revives a single ignored vulnerability.
     * Shows a notification with an "Undo" option that allows the user to restore the ignored state.
     * The revive operation is performed first, then the user can undo it if desired.
     * This follows the same pattern as the VS Code extension's revivePackage method.
     *
     * @param entryToRevive The ignore entry to revive
     */
    public void reviveSingleEntry(IgnoreEntry entryToRevive) {
        CxLogger.info(format("RTS-Ignore: Reviving entry: %s", entryToRevive.getPackageName()));
        Map<String, IgnoreEntry> ignoredEntries = new HashMap<>(ignoreFileManager.getIgnoreData());

        // Count active files before reviving
        int fileCount = (int) entryToRevive.files.stream()
                .filter(f -> f.active)
                .count();
        // Perform the revive operation (sets all file references to inactive)
        boolean success = ignoreFileManager.reviveEntry(entryToRevive);
        if (!success) {
            // Notification removed: use Eclipse MessageDialog instead
            CxLogger.warning(format("RTS-Ignore: Failed to revive entry: %s", entryToRevive.getPackageName()));
            return;
        }
        // Trigger rescan for affected files
//         triggerRescanForEntry(entryToRevive);
        // Show notification with undo option
//         showReviveUndoNotification(entryToRevive, fileCount, ignoredEntries);
        CxLogger.info(format("RTS-Ignore: Successfully revived entry: %s", entryToRevive.getPackageName()));
    }

    /**
     * Revives multiple ignored vulnerabilities in bulk.
     * Shows a summary notification to the user and triggers rescans for all affected files.
     *
     * @param entriesToRevive List of package keys to revive
     */
    public void reviveMultipleEntries(List<IgnoreEntry> entriesToRevive) {
        if (entriesToRevive == null || entriesToRevive.isEmpty()) {
            CxLogger.warning("RTS-Ignore: No package keys provided for bulk revive");
            return;
        }
        int successCount = 0;
        int totalFileCount = 0;
        List<IgnoreEntry> failedIgnoreEntry = new ArrayList<>();

        for (IgnoreEntry entryToRevive : entriesToRevive) {
            int fileCount = (int) entryToRevive.files.stream()
                    .filter(f -> f.active)
                    .count();
            boolean success = ignoreFileManager.reviveEntry(entryToRevive);
            if (success) {
                successCount++;
                totalFileCount += fileCount;
                // Trigger rescan for affected files
//                 triggerRescanForEntry(entryToRevive);
                CxLogger.info(String.format("RTS-Ignore: Successfully revived entry: %s", entryToRevive.getTitle()));
            } else {
                failedIgnoreEntry.add(entryToRevive);
                CxLogger.warning(String.format("RTS-Ignore: Failed to revive entry: %s", entryToRevive.getTitle()));
            }
        }
        // Show summary notification
        if (successCount > 0) {
            String message;
            if (successCount == 1) {
                message = String.format("Revived 1 vulnerability in %d file%s",
                        totalFileCount, totalFileCount == 1 ? "" : "s");
            } else {
                message = String.format("Revived %d vulnerabilities in %d file%s",
                        successCount, totalFileCount, totalFileCount == 1 ? "" : "s");
            }
            if (!failedIgnoreEntry.isEmpty()) {
                message += String.format(" (%d failed)", failedIgnoreEntry.size());
            }
            // Notification removed: use Eclipse MessageDialog instead
        } else {
            // Notification removed: use Eclipse MessageDialog instead
        }
    }

    private IgnoreEntry buildIgnoreEntry(ScanIssue issue, String clickId) {
        IgnoreEntry entry = new IgnoreEntry();
        if (issue != null) {
            // Convert model.ScanEngine to utils.ScanEngine
            com.checkmarx.eclipse.devassist.model.ScanEngine modelEngine = issue.getScanEngine();
            if (modelEngine != null) {
                entry.type = com.checkmarx.eclipse.devassist.utils.ScanEngine.valueOf(modelEngine.toString());
            }
            entry.title = issue.getTitle();
            entry.severity = issue.getSeverity();
            entry.description = issue.getDescription();
            entry.ruleId = issue.getRuleId();
            if (!issue.getLocations().isEmpty()) {
                IgnoreEntry.FileReference ref = new IgnoreEntry.FileReference(
                    issue.getFilePath(),
                    true,
                    issue.getLocations().get(0).getLine(),
                    ""
                );
                entry.files.add(ref);
            }
        }
        return entry;
    }

    private String createJsonKeyForIgnoreEntry(ScanIssue issue, String clickId) {
        if (issue == null) return "";
        return issue.getSimilarityId() != null ? issue.getSimilarityId() : "";
    }
}
