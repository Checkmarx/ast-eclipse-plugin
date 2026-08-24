package com.checkmarx.eclipse.devassist.ignore;
import org.eclipse.core.resources.IProject;
import com.checkmarx.eclipse.common.utils.CxLogger;



import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder;
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
     * Adds an entry to the ignore file for this specific occurrence only (this
     * file). If the same vulnerability (by key) is already ignored elsewhere
     * (e.g. via "ignore all" or a previous "ignore this" in another file), the
     * existing entry's file list is merged into rather than replaced, so
     * ignoring in one file never clears an ignore already recorded for another.
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
        IgnoreEntry existingEntry = ignoreFileManager.getIgnoreData().get(vulnerabilityKey);
        IgnoreEntry ignoreEntry;
        if (existingEntry != null) {
            ignoreEntry = existingEntry;
            upsertFileReference(ignoreEntry, issueToIgnore);
        } else {
            // Convert ScanIssue → IgnoreEntry (includes this occurrence's file reference)
            ignoreEntry = buildIgnoreEntry(issueToIgnore, clickId);
        }
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
     * Adds (or reactivates) a single file reference on an existing ignore
     * entry for the given issue's file, without touching any other file's
     * reference already recorded on that entry.
     */
    private void upsertFileReference(IgnoreEntry entry, ScanIssue issue) {
        if (issue.getLocations() == null || issue.getLocations().isEmpty()) {
            return;
        }
        String path = ignoreFileManager.normalizePath(issue.getFilePath());
        int line = issue.getLocations().get(0).getLine();
        if (entry.files == null) {
            entry.files = new ArrayList<>();
        }
        for (IgnoreEntry.FileReference ref : entry.files) {
            if (path.equals(ref.getPath())) {
                ref.setActive(true);
                ref.setLine(line);
                return;
            }
        }
        entry.files.add(new IgnoreEntry.FileReference(path, true, line, ""));
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
        try {
            CxLogger.info(String.format("RTS-Ignore: Adding ignore entry for issue: %s", issueToIgnore.getTitle()));
            String vulnerabilityKey = createJsonKeyForIgnoreEntry(issueToIgnore, clickId);
            CxLogger.info("RTS-Ignore: Ignoring all vulnerabilities for: " + vulnerabilityKey);
            if (vulnerabilityKey.isEmpty()) {
                CxLogger.info("RTS-Ignore: Ignoring all vulnerabilities failed. Vulnerability key is empty.");
                return;
            }
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
                for (ScanIssue issue : issues) {
                    // Issues missing a location (e.g. malformed/partial entries) can't be
                    // matched by key reliably and have no line to record - skip them
                    // instead of letting a single bad issue abort ignoring every match.
                    if (issue == null || issue.getLocations() == null || issue.getLocations().isEmpty()) {
                        continue;
                    }
                    if (!createJsonKeyForIgnoreEntry(issue, clickId).equals(vulnerabilityKey)) {
                        continue;
                    }
                    fileRefs.add(new IgnoreEntry.FileReference(
                            ignoreFileManager.normalizePath(issue.getFilePath()),
                            true,
                            issue.getLocations().get(0).getLine(), ""));
                }
            }
            // Guarantee the clicked occurrence itself is covered even if it was
            // somehow absent from the problemHolder snapshot above.
            String clickedPath = ignoreFileManager.normalizePath(issueToIgnore.getFilePath());
            boolean clickedCovered = fileRefs.stream().anyMatch(ref -> clickedPath.equals(ref.getPath()));
            if (!clickedCovered && issueToIgnore.getLocations() != null && !issueToIgnore.getLocations().isEmpty()) {
                fileRefs.add(new IgnoreEntry.FileReference(
                        clickedPath, true, issueToIgnore.getLocations().get(0).getLine(), ""));
            }
            ignoreEntry.files = fileRefs;
            ignoreFileManager.updateIgnoreData(vulnerabilityKey, ignoreEntry);
            CxLogger.info(String.format("RTS-Ignore: Successfully added ignore entry for issue: %s", issueToIgnore.getTitle()));
        } catch (Exception e) {
            CxLogger.warning("RTS-Ignore: Failed to add ignore-all entry for issue: "
                    + (issueToIgnore != null ? issueToIgnore.getTitle() : "unknown") + " - " + e.getMessage());
        }
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
        triggerRescanForEntry(entryToRevive);
        // Show notification with undo option
//         showReviveUndoNotification(entryToRevive, fileCount, ignoredEntries);
        CxLogger.info(format("RTS-Ignore: Successfully revived entry: %s", entryToRevive.getPackageName()));
    }

    /**
     * Revives multiple ignored vulnerabilities in bulk.
     * Batches all the revive operations together and saves to disk once at the end,
     * then triggers rescans for all affected files.
     *
     * @param entriesToRevive List of entries to revive
     */
    public void reviveMultipleEntries(List<IgnoreEntry> entriesToRevive) {
        if (entriesToRevive == null || entriesToRevive.isEmpty()) {
            CxLogger.warning("RTS-Ignore: No package keys provided for bulk revive");
            return;
        }
        CxLogger.info(format("RTS-Ignore: Bulk reviving %d entries", entriesToRevive.size()));

        int successCount = 0;
        int totalFileCount = 0;
        List<IgnoreEntry> failedIgnoreEntry = new ArrayList<>();
        List<IgnoreEntry> revivedEntries = new ArrayList<>();

        // Revive all entries in memory first (without individual saves)
        for (IgnoreEntry entryToRevive : entriesToRevive) {
            int fileCount = (int) entryToRevive.files.stream()
                    .filter(f -> f.active)
                    .count();
            // Use internal revive that doesn't save to disk
            boolean success = reviveSingleEntryInternal(entryToRevive);
            if (success) {
                successCount++;
                totalFileCount += fileCount;
                revivedEntries.add(entryToRevive);
                CxLogger.info(String.format("RTS-Ignore: Successfully revived in memory: %s", entryToRevive.getPackageName()));
            } else {
                failedIgnoreEntry.add(entryToRevive);
                CxLogger.warning(String.format("RTS-Ignore: Failed to revive entry: %s", entryToRevive.getPackageName()));
            }
        }

        // Save to disk once after all revives
        if (successCount > 0) {
            ignoreFileManager.saveIgnoreDataToDisk();
            CxLogger.info(format("RTS-Ignore: Saved all %d revived entries to disk", successCount));
        }

        // Trigger rescans for all revived entries
        for (IgnoreEntry entry : revivedEntries) {
            triggerRescanForEntry(entry);
        }

        // Log summary
        String message;
        if (successCount == 1) {
            message = String.format("Revived 1 vulnerability in %d file%s",
                    totalFileCount, totalFileCount == 1 ? "" : "s");
        } else if (successCount > 1) {
            message = String.format("Revived %d vulnerabilities in %d file%s",
                    successCount, totalFileCount, totalFileCount == 1 ? "" : "s");
        } else {
            message = "No vulnerabilities revived";
        }
        if (!failedIgnoreEntry.isEmpty()) {
            message += String.format(" (%d failed)", failedIgnoreEntry.size());
        }
        CxLogger.info(format("RTS-Ignore: Bulk revive summary: %s", message));
    }

    /**
     * Internal method to revive a single entry without saving to disk.
     * Used by bulk operations that batch multiple revives before a single save.
     *
     * @param entryToRevive The ignore entry to revive
     * @return true if the entry was found and revived, false otherwise
     */
    private boolean reviveSingleEntryInternal(IgnoreEntry entryToRevive) {
        CxLogger.info(format("RTS-Ignore: Reviving entry (internal): %s", entryToRevive.getPackageName()));

        // Find the entry in ignoreData map by matching properties
        String entryKey = ignoreFileManager.getIgnoreData().entrySet().stream()
                .filter(e -> ignoreFileManager.matchesEntry(e.getValue(), entryToRevive))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (entryKey == null) {
            CxLogger.warning(format("RTS-Ignore: Entry not found in ignoreData: %s", entryToRevive.getPackageName()));
            return false;
        }

        IgnoreEntry actualEntry = ignoreFileManager.getIgnoreData().get(entryKey);
        // Set all file references to inactive
        for (IgnoreEntry.FileReference file : actualEntry.getFiles()) {
            file.active = false;
        }
        CxLogger.info(format("RTS-Ignore: Marked all files as inactive for: %s", entryToRevive.getPackageName()));
        return true;
    }

    /**
     * Checks whether the given scan issue is currently ignored: the composite
     * vulnerability key must match AND the entry must have an active file
     * reference for this issue's specific file. This makes "ignore this" scoped
     * to the file it was ignored from (an entry with one active file reference
     * only hides the finding in that file), while "ignore all of this type"
     * (which records an active reference per matching file) hides it everywhere,
     * matching the {@code .checkmarxIgnored} files array's path/active/line
     * per-occurrence tracking.
     *
     * @param issue The scan issue to check
     * @return true if an active ignore entry exists for this issue's file
     */
    public boolean isIgnored(ScanIssue issue) {
        if (issue == null) {
            return false;
        }
        String key = createJsonKeyForIgnoreEntry(issue, "");
        if (key.isEmpty()) {
            return false;
        }
        IgnoreEntry entry = ignoreFileManager.getIgnoreData().get(key);
        if (entry == null || entry.files == null) {
            return false;
        }
        String normalizedPath = ignoreFileManager.normalizePath(issue.getFilePath());
        return entry.files.stream()
                .anyMatch(ref -> ref.isActive() && normalizedPath.equals(ref.getPath()));
    }

    /**
     * Triggers an immediate real-time rescan for every file referenced by the
     * given (just-revived) ignore entry, scoped to just those files - matching
     * the JetBrains plugin's revive behavior of a per-file rescan rather than a
     * full project rescan. Since the entry no longer exists (or is inactive) in
     * {@code .checkmarxIgnored} by the time this runs, the scan's ignore-file
     * exclusion no longer suppresses it, so the revived finding reappears.
     *
     * @param entry The ignore entry that was just revived
     */
    private void triggerRescanForEntry(IgnoreEntry entry) {
        if (entry == null || entry.getFiles() == null || project == null) {
            return;
        }
        org.eclipse.core.runtime.IPath projectLocation = project.getLocation();
        if (projectLocation == null) {
            return;
        }
        String basePath = projectLocation.toOSString();

        for (IgnoreEntry.FileReference fileRef : entry.getFiles()) {
            if (fileRef == null || fileRef.getPath() == null) {
                continue;
            }
            try {
                String absolutePath = Paths.get(basePath, fileRef.getPath()).toString();
                org.eclipse.core.resources.IFile file = org.eclipse.core.resources.ResourcesPlugin.getWorkspace()
                        .getRoot().getFileForLocation(new org.eclipse.core.runtime.Path(absolutePath));
                if (file == null || !file.exists()) {
                    CxLogger.warning("RTS-Ignore: Cannot trigger rescan, file not found: " + fileRef.getPath());
                    continue;
                }

                // Reviving only mutates .checkmarxIgnored - the source file's content and
                // mtime are untouched, so DevAssistScanStateHolder's cached state hash for
                // it is still identical to what was last scanned. Without clearing it here,
                // ScanManager.scanFileWithOutcome() would see hasChanged()==false and skip
                // the scan entirely (no scanner ever runs), so the revived finding would
                // never reappear. Clearing just this file's entry forces exactly one real
                // scan cycle; the hash is repopulated normally once that scan succeeds, so
                // ordinary edit-based caching for this file is unaffected afterwards.
                // Use file.getLocation().toOSString() (not the manually-joined path above)
                // since that is the exact key RealTimeScanJob/ScanManager use to read/write
                // the cache - any formatting difference here would silently no-op the clear.
                DevAssistScanStateHolder stateHolder = getOrCreateStateHolder();
                if (stateHolder != null) {
                    stateHolder.clearFileState(file.getLocation().toOSString());
                }

                com.checkmarx.eclipse.devassist.backend.listener.RealTimeScanJob scanJob =
                        new com.checkmarx.eclipse.devassist.backend.listener.RealTimeScanJob(file, file.getName());
                scanJob.schedule(0);
                CxLogger.info("RTS-Ignore: Triggered rescan for revived entry file: " + fileRef.getPath());
            } catch (Exception e) {
                CxLogger.warning("RTS-Ignore: Failed to trigger rescan for file: " + fileRef.getPath()
                        + " - " + e.getMessage());
            }
        }
    }

    /**
     * Fetches (or lazily creates) the same per-project {@link DevAssistScanStateHolder}
     * instance that {@code RealTimeScanJob}/{@code ScanManager} use for edit-based
     * caching, via the identical session-property key. Sharing the exact instance
     * (rather than constructing a disconnected one) is required for
     * {@link DevAssistScanStateHolder#clearFileState(String)} to have any effect on
     * the subsequent scan cycle triggered by revive.
     */
    private DevAssistScanStateHolder getOrCreateStateHolder() {
        if (project == null) {
            return null;
        }
        try {
            org.eclipse.core.runtime.QualifiedName stateHolderKey = new org.eclipse.core.runtime.QualifiedName(
                    "com.checkmarx.eclipse.plugin", "state-holder");
            DevAssistScanStateHolder stateHolder = (DevAssistScanStateHolder) project.getSessionProperty(stateHolderKey);
            if (stateHolder == null) {
                stateHolder = new DevAssistScanStateHolder();
                project.setSessionProperty(stateHolderKey, stateHolder);
            }
            return stateHolder;
        } catch (Exception e) {
            CxLogger.warning("RTS-Ignore: Failed to access scan state holder: " + e.getMessage());
            return null;
        }
    }

    private IgnoreEntry buildIgnoreEntry(ScanIssue issue, String clickId) {
        IgnoreEntry entry = new IgnoreEntry();
        if (issue != null) {
            // Convert model.ScanEngine to utils.ScanEngine
            com.checkmarx.eclipse.devassist.model.ScanEngine modelEngine = issue.getScanEngine();
            com.checkmarx.eclipse.devassist.utils.ScanEngine engine = null;
            if (modelEngine != null) {
                engine = com.checkmarx.eclipse.devassist.utils.ScanEngine.valueOf(modelEngine.toString());
                entry.type = engine;
            }
            entry.title = issue.getTitle();
            entry.severity = issue.getSeverity();
            entry.description = issue.getDescription();
            entry.ruleId = issue.getRuleId();
            entry.packageManager = issue.getPackageManager();
            entry.packageVersion = issue.getPackageVersion();
            entry.similarityId = issue.getSimilarityId();
            entry.secretValue = issue.getSecretValue();
            if (engine == com.checkmarx.eclipse.devassist.utils.ScanEngine.CONTAINERS) {
                entry.packageName = issue.getTitle() + ":" + issue.getImageTag();
                entry.imageName = issue.getTitle();
                entry.imageTag = issue.getImageTag();
            } else {
                entry.packageName = issue.getTitle();
            }
            if (!issue.getLocations().isEmpty()) {
                IgnoreEntry.FileReference ref = new IgnoreEntry.FileReference(
                    ignoreFileManager.normalizePath(issue.getFilePath()),
                    true,
                    issue.getLocations().get(0).getLine(),
                    ""
                );
                entry.files.add(ref);
            }
        }
        return entry;
    }

    /**
     * Builds a unique key identifying the given scan issue's vulnerability, matching the
     * composite key format used by the JetBrains plugin so ignore/revive/isIgnored checks
     * are consistent across scan engines (OSS, CONTAINERS, SECRETS, IAC, ASCA).
     */
    public String createJsonKeyForIgnoreEntry(ScanIssue issue, String clickId) {
        if (issue == null || issue.getScanEngine() == null) return "";
        String relativePath = ignoreFileManager.normalizePath(issue.getFilePath());
        com.checkmarx.eclipse.devassist.utils.ScanEngine engine =
                com.checkmarx.eclipse.devassist.utils.ScanEngine.valueOf(issue.getScanEngine().toString());
        switch (engine) {
            case OSS:
                return formatJsonKeyForIgnoreEntry(engine, issue.getPackageManager(), issue.getTitle(), issue.getPackageVersion());
            case CONTAINERS:
                return formatJsonKeyForIgnoreEntry(engine, issue.getTitle(), issue.getImageTag(), "");
            case SECRETS:
                return formatJsonKeyForIgnoreEntry(engine, issue.getTitle(), issue.getSecretValue(), relativePath);
            case IAC:
                return issue.getSimilarityId() != null ?
                        formatJsonKeyForIgnoreEntry(engine, issue.getTitle(), issue.getSimilarityId(), relativePath) : "";
            case ASCA:
                return issue.getRuleId() != null ?
                        formatJsonKeyForIgnoreEntry(engine, issue.getTitle(), String.valueOf(issue.getRuleId()), relativePath) : "";
            default:
                return formatJsonKeyForIgnoreEntry(engine, "", "", issue.getTitle());
        }
    }

    private String formatJsonKeyForIgnoreEntry(com.checkmarx.eclipse.devassist.utils.ScanEngine scanEngine,
                                                String title, String secondary, String path) {
        if (scanEngine == com.checkmarx.eclipse.devassist.utils.ScanEngine.CONTAINERS) {
            return format("%s:%s:%s", scanEngine, title, secondary);
        } else {
            return format("%s:%s:%s:%s", scanEngine, title, secondary, path);
        }
    }
}
