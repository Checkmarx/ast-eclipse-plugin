package com.checkmarx.eclipse.devassist.ignore;
import org.eclipse.core.resources.IProject;
import com.checkmarx.eclipse.common.utils.CxLogger;



import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;

import static com.checkmarx.eclipse.devassist.utils.DevAssistConstants.QUICK_FIX;
import static java.lang.String.format;

/**
 * Manages the ignore file (.checkmarxIgnored) within the project's workspace.
 * Handles reading, writing, and updating ignore entries.
 * Monitors the ignore file for changes and updates internal state accordingly.
 * Provides methods to ignore issues and update temporary ignore lists.
 */
public final class IgnoreManager {
    // Per-project cache (mirrors IgnoreFileManager's pattern) - a single static
    // "instance" field replaced on every call with a different project used to
    // silently discard whichever project's IgnoreManager wasn't the most
    // recently requested. Nothing currently holds a reference across an async
    // boundary, but any caller that does would then act on the wrong project's
    // ignore state the moment another project's lookup swapped the field.
    private static final Map<org.eclipse.core.resources.IProject, IgnoreManager> INSTANCES = new HashMap<>();
    private final org.eclipse.core.resources.IProject project;
    private final ProblemHolderService problemHolder;
    private final IgnoreFileManager ignoreFileManager;

    public IgnoreManager(org.eclipse.core.resources.IProject project) {
        this.project = project;
        this.problemHolder = ProblemHolderService.getInstance(project);
        this.ignoreFileManager = IgnoreFileManager.getInstance(project);
    }

    public static synchronized IgnoreManager getInstance(org.eclipse.core.resources.IProject project) {
        return INSTANCES.computeIfAbsent(project, IgnoreManager::new);
    }

    /**
     * Evicts the cached IgnoreManager for a closed project, so a stale
     * per-project instance can never be resolved (or accumulate indefinitely)
     * once the project is gone. Holds no listeners/resources of its own beyond
     * the ProblemHolderService/IgnoreFileManager references it wraps - those are
     * disposed independently - so simply dropping the reference is sufficient.
     *
     * @param project the project that is closing
     */
    public static synchronized void dispose(org.eclipse.core.resources.IProject project) {
        INSTANCES.remove(project);
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
            upsertFileReference(ignoreEntry, issueToIgnore, resolveVulnerability(issueToIgnore, clickId));
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
        // For a ScanIssue grouping several vulnerabilities on one line (ASCA/IAC), the cached
        // ScanIssue in ProblemHolderService/CxFindingsView still holds every vulnerability in
        // the group - including the one just ignored - until the next scan rebuilds it via the
        // adaptor's per-vulnerability filtering. Without an immediate rescan, isIgnored(issue)
        // checks made against that stale cached issue (e.g. ProblemDecorator.decorateEditor,
        // CxFindingsView.refreshTreeWithFilter) can resolve to the just-ignored vulnerability
        // and incorrectly treat the WHOLE line as ignored, hiding any other still-active
        // vulnerability on it until something else happens to trigger a rescan.
        triggerRescanForFile(issueToIgnore.getFilePath());
//         showIgnoreSuccessNotification(project, issueToIgnore, vulnerabilityKey);
        CxLogger.info(String.format("RTS-Ignore: Successfully added ignore entry for issue: %s", issueToIgnore.getTitle()));
    }

    /**
     * Adds (or reactivates) a single file reference on an existing ignore
     * entry for the given issue's file, without touching any other file's
     * reference already recorded on that entry.
     * <p>
     * ASCA/IAC ignore keys deliberately omit the line number (see
     * {@link #createJsonKeyForIgnoreEntry}), so a single key legitimately covers every
     * occurrence of that rule in the file - matching an existing {@link IgnoreEntry.FileReference}
     * by path alone would find *any* prior occurrence recorded under this key and overwrite its
     * line/problematicLine, silently losing track of it instead of recording this (different)
     * occurrence separately. Matching by path + problematicLine identifies the SAME occurrence
     * (e.g. re-ignoring after a revive), while a different problematicLine is always treated as
     * a new occurrence to add.
     */
    private void upsertFileReference(IgnoreEntry entry, ScanIssue issue, Vulnerability vulnerability) {
        if (issue.getLocations() == null || issue.getLocations().isEmpty()) {
            return;
        }
        String path = ignoreFileManager.normalizePath(issue.getFilePath());
        int line = issue.getLocations().get(0).getLine();
        String problematicLine = vulnerability != null && vulnerability.getProblematicLine() != null
                ? vulnerability.getProblematicLine() : "";
        if (entry.files == null) {
            entry.files = new ArrayList<>();
        }
        for (IgnoreEntry.FileReference ref : entry.files) {
            boolean sameOccurrence = path.equals(ref.getPath())
                    && (problematicLine.isEmpty()
                        ? ref.getProblematicLine() == null
                        : problematicLine.equals(ref.getProblematicLine()));
            if (sameOccurrence) {
                ref.setActive(true);
                ref.setLine(line);
                return;
            }
        }
        entry.files.add(new IgnoreEntry.FileReference(path, true, line, problematicLine));
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
            IgnoreEntry ignoreEntry = buildIgnoreEntry(issueToIgnore, clickId);
            if (Objects.isNull(ignoreEntry)) {
                // Notification removed: use Eclipse MessageDialog instead
                return;
            }
            // Best-effort: pick up every other currently-known occurrence of this
            // vulnerability from the problem holder. This may legitimately be empty
            // (e.g. a file whose scan was skipped as unchanged since the holder was
            // last populated) - that must NOT stop the clicked occurrence itself from
            // being ignored, so no early-return on an empty/no-match snapshot here.
            Map<String, List<ScanIssue>> allIssues = new HashMap<>();
            for (Map.Entry<String, List<ScanIssue>> entry : problemHolder.getAllScanIssues().entrySet()) {
                allIssues.put(entry.getKey(), new ArrayList<>(entry.getValue()));
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
        if (issue.getScanEngine() == com.checkmarx.eclipse.devassist.model.ScanEngine.ASCA) {
            // ASCA's ignore key intentionally omits the line number (see
            // createJsonKeyForIgnoreEntry) so one key can cover several distinct occurrences
            // of the same rule in one file, each tracked independently by problematicLine in
            // that entry's FileReference list (see isAscaVulnerabilityIgnored). That
            // per-occurrence filtering already runs upstream in AscaScanResultAdaptor before a
            // ScanIssue ever reaches this call (from decoration/tree-filtering call sites) -
            // any ASCA ScanIssue that does reach here already has only its non-ignored
            // vulnerabilities. A coarse key+path match here (ignoring problematicLine) would
            // incorrectly treat this issue as ignored whenever ANY occurrence of the same rule
            // in this file is ignored - including ones on a completely different line.
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
     * Triggers an immediate real-time rescan for a single file right after an ignore write, so
     * the cached scan issues (and everything decorated/filtered from them) reflect the new
     * ignore state right away instead of only after the next incidental rescan. Mirrors
     * {@link #triggerRescanForEntry(IgnoreEntry)}'s resolution/cache-clear/schedule pattern.
     *
     * @param filePath absolute path of the file to rescan
     */
    private void triggerRescanForFile(String filePath) {
        if (filePath == null || filePath.isEmpty() || project == null) {
            return;
        }
        try {
            org.eclipse.core.resources.IFile file = org.eclipse.core.resources.ResourcesPlugin.getWorkspace()
                    .getRoot().getFileForLocation(new org.eclipse.core.runtime.Path(filePath));
            if (file == null || !file.exists()) {
                CxLogger.warning("RTS-Ignore: Cannot trigger rescan, file not found: " + filePath);
                return;
            }

            // See triggerRescanForEntry for why the cached state hash must be cleared: ignoring
            // only mutates .checkmarxIgnored, not the source file, so without this the scan would
            // be skipped entirely as "unchanged".
            DevAssistScanStateHolder stateHolder = getOrCreateStateHolder();
            if (stateHolder != null) {
                stateHolder.clearFileState(file.getLocation().toOSString());
            }

            com.checkmarx.eclipse.devassist.backend.listener.RealTimeScanJob scanJob =
                    new com.checkmarx.eclipse.devassist.backend.listener.RealTimeScanJob(file, file.getName());
            scanJob.schedule(0);
            CxLogger.info("RTS-Ignore: Triggered rescan for ignored entry's file: " + filePath);
        } catch (Exception e) {
            CxLogger.warning("RTS-Ignore: Failed to trigger rescan for file: " + filePath + " - " + e.getMessage());
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
        if (issue == null) {
            return entry;
        }
        // Convert model.ScanEngine to utils.ScanEngine
        com.checkmarx.eclipse.devassist.model.ScanEngine modelEngine = issue.getScanEngine();
        com.checkmarx.eclipse.devassist.utils.ScanEngine engine = null;
        if (modelEngine != null) {
            engine = com.checkmarx.eclipse.devassist.utils.ScanEngine.valueOf(modelEngine.toString());
            entry.type = engine;
        }

        // ASCA/IAC can group several vulnerabilities under one ScanIssue (same line). The
        // ScanIssue's own title/ruleId/similarityId are the group's aggregate values (e.g.
        // "3 Checkmarx One Assist issues") and are identical for every vulnerability in the
        // group and across every line flagged by the same rule - keying/describing the ignore
        // entry off them would ignore the whole group/rule instead of just the clicked
        // occurrence. Resolve the specific Vulnerability the user clicked instead.
        Vulnerability vulnerability = (engine == com.checkmarx.eclipse.devassist.utils.ScanEngine.ASCA
                || engine == com.checkmarx.eclipse.devassist.utils.ScanEngine.IAC)
                ? resolveVulnerability(issue, clickId) : null;

        String problematicLine = "";
        if (vulnerability != null) {
            entry.title = vulnerability.getTitle();
            entry.severity = vulnerability.getSeverity();
            entry.description = vulnerability.getDescription();
            entry.ruleId = vulnerability.getRuleId();
            entry.similarityId = vulnerability.getSimilarityId();
            entry.packageName = vulnerability.getTitle();
            problematicLine = vulnerability.getProblematicLine() != null ? vulnerability.getProblematicLine() : "";
        } else {
            entry.title = issue.getTitle();
            entry.severity = issue.getSeverity();
            entry.description = issue.getDescription();
            entry.ruleId = issue.getRuleId();
            entry.similarityId = issue.getSimilarityId();
            if (engine == com.checkmarx.eclipse.devassist.utils.ScanEngine.CONTAINERS) {
                entry.packageName = issue.getTitle() + ":" + issue.getImageTag();
                entry.imageName = issue.getTitle();
                entry.imageTag = issue.getImageTag();
            } else {
                entry.packageName = issue.getTitle();
            }
        }
        entry.packageManager = issue.getPackageManager();
        entry.packageVersion = issue.getPackageVersion();
        entry.secretValue = issue.getSecretValue();
        entry.dateAdded = java.time.Instant.now().toString();

        if (issue.getLocations() != null && !issue.getLocations().isEmpty()) {
            IgnoreEntry.FileReference ref = new IgnoreEntry.FileReference(
                ignoreFileManager.normalizePath(issue.getFilePath()),
                true,
                issue.getLocations().get(0).getLine(),
                problematicLine
            );
            entry.files.add(ref);
        }
        return entry;
    }

    /**
     * Resolves the specific {@link Vulnerability} the user acted on within a (possibly
     * multi-vulnerability) ScanIssue. {@code clickId} is the vulnerability id carried on the
     * hover's per-vulnerability "Ignore this" link; when it's absent/empty/the quick-fix
     * sentinel (e.g. "Ignore This Finding" from the Findings tree's right-click menu, which
     * acts on the whole ScanIssue node rather than a specific vulnerability), falls back to
     * the issue's own id - which, by construction in the ASCA/IAC adaptors, is assigned to the
     * first (highest-severity) vulnerability of the *original* group.
     * <p>
     * That id can go stale: once that original first vulnerability is itself ignored on an
     * earlier pass, the adaptor drops it from {@code issue.getVulnerabilities()} on the next
     * rebuild, so the id no longer matches anything in the (now-filtered) list even though
     * other vulnerabilities from the same group remain. In that case, fall back to the first
     * vulnerability actually present - unambiguous when it's the only one left, and otherwise
     * consistent with the original "first in the group" intent of {@code getScanIssueId()}.
     */
    private Vulnerability resolveVulnerability(ScanIssue issue, String clickId) {
        String vulnerabilityId = (clickId == null || clickId.isEmpty() || clickId.equals(QUICK_FIX))
                ? issue.getScanIssueId() : clickId;
        Vulnerability vulnerability = DevAssistUtils.getVulnerabilityDetails(issue, vulnerabilityId);
        if (vulnerability == null && issue.getVulnerabilities() != null && !issue.getVulnerabilities().isEmpty()) {
            vulnerability = issue.getVulnerabilities().get(0);
        }
        return vulnerability;
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
            case IAC: {
                Vulnerability vulnerability = resolveVulnerability(issue, clickId);
                return vulnerability != null && vulnerability.getSimilarityId() != null ?
                        formatJsonKeyForIgnoreEntry(engine, vulnerability.getTitle(), vulnerability.getSimilarityId(), relativePath) : "";
            }
            case ASCA: {
                Vulnerability vulnerability = resolveVulnerability(issue, clickId);
                return vulnerability != null && vulnerability.getRuleId() != null ?
                        formatJsonKeyForIgnoreEntry(engine, vulnerability.getTitle(), String.valueOf(vulnerability.getRuleId()), relativePath) : "";
            }
            default:
                return formatJsonKeyForIgnoreEntry(engine, "", "", issue.getTitle());
        }
    }

    /**
     * Checks whether a specific ASCA vulnerability is ignored, based on its rule name and the
     * actual source text of the line it was flagged on ("problematic line"), rather than just
     * line number (which drifts as the file is edited) or file path alone (which would match
     * every occurrence of the same rule anywhere in the file). Used to filter individual
     * vulnerabilities out of a ScanIssue that may group several onto the same line, since ASCA's
     * CLI has no ignore-file exclusion of its own (see AscaScannerService#getIgnoreFilePath) -
     * this app-level check is the only enforcement point.
     *
     * @param vulnerability the specific vulnerability to check
     * @param ignoreEntries the current ignore entries to check against
     * @param filePath      the file path of the issue
     * @return {@code true} if this specific vulnerability is ignored; {@code false} otherwise
     */
    public boolean isAscaVulnerabilityIgnored(Vulnerability vulnerability, List<IgnoreEntry> ignoreEntries, String filePath) {
        if (vulnerability == null || ignoreEntries == null) {
            return false;
        }
        String normalizedPath = ignoreFileManager.normalizePath(filePath);
        String issueProblematicLine = vulnerability.getProblematicLine();
        String vulnTitle = vulnerability.getTitle();
        for (IgnoreEntry entry : ignoreEntries) {
            if (entry.getType() != com.checkmarx.eclipse.devassist.utils.ScanEngine.ASCA) {
                continue;
            }
            // Match by rule name: the ignore entry's packageName must match the vulnerability's title (rule name)
            boolean ruleNameMatch = (entry.getPackageName() != null && entry.getPackageName().equals(vulnTitle))
                    || (entry.getPackageName() == null && vulnTitle == null);
            if (!ruleNameMatch || entry.getFiles() == null) {
                continue;
            }
            for (IgnoreEntry.FileReference ref : entry.getFiles()) {
                boolean pathMatch = ref.isActive() && normalizedPath.equals(ref.getPath());
                boolean problematicLineMatch = (issueProblematicLine == null && ref.getProblematicLine() == null)
                        || (issueProblematicLine != null && issueProblematicLine.equals(ref.getProblematicLine()));
                if (pathMatch && problematicLineMatch) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if there are any ignored entries for the specified scan engine type.
     * Used to skip the (expensive) full re-scan / line-number reconciliation
     * done by {@link #updateLineNumbersForIgnoredEntries} when a file's scan
     * engine has no ignored entries at all.
     *
     * @param scanEngine The scan engine type to check for ignored entries
     * @return {@code true} if there are any ignored entries for the specified scan engine,
     * {@code false} otherwise
     */
    public boolean hasIgnoredEntries(com.checkmarx.eclipse.devassist.utils.ScanEngine scanEngine) {
        return ignoreFileManager.getIgnoreData().values().stream()
                .anyMatch(entry -> entry.getType() == scanEngine);
    }

    /**
     * Creates a list of ignore entry keys for a given scan issue.
     * For IAC and ASCA scan engines, it generates keys for each vulnerability found in the scan issue.
     * For other scan engines (OSS, SECRETS, CONTAINERS), it generates a single key using the quick fix ID.
     *
     * @param scanIssue The scan issue to create ignore keys for
     * @return A list of unique keys that can be used to identify ignore entries for this scan issue
     */
    private List<String> createIgnoreKeysForScanIssue(ScanIssue scanIssue) {
        List<String> keys = new ArrayList<>();
        if (scanIssue.getScanEngine() == com.checkmarx.eclipse.devassist.model.ScanEngine.IAC
                || scanIssue.getScanEngine() == com.checkmarx.eclipse.devassist.model.ScanEngine.ASCA) {
            // IAC / ASCA - build key for EACH vulnerability
            if (scanIssue.getVulnerabilities() == null || scanIssue.getVulnerabilities().isEmpty()) {
                return keys;
            }
            for (Vulnerability vulnerability : scanIssue.getVulnerabilities()) {
                String vulnerabilityId = vulnerability.getVulnerabilityId();
                if (vulnerabilityId == null || vulnerabilityId.isEmpty()) {
                    continue;
                }
                String key = createJsonKeyForIgnoreEntry(scanIssue, vulnerabilityId);
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            }
        } else {
            String key = createJsonKeyForIgnoreEntry(scanIssue, QUICK_FIX);
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * Updates line numbers for ignored entries (OSS/SECRETS/CONTAINERS/IAC) based on a fresh,
     * UNFILTERED scan of the file (i.e. run without passing the ignore file to the scan engine,
     * so already-ignored issues are still present in the results). If a user edits a file above an
     * ignored finding, the finding's line shifts - without this reconciliation the gutter
     * icon/marker for that ignored finding would render at its stale line. Also removes ignore
     * entries whose finding is no longer present in the scan results (e.g. the vulnerable code was
     * deleted).
     *
     * @param fullScanResults The UNFILTERED scan results for the file
     * @param filePath        The path of the file that was scanned and needs line number updates
     */
    public void updateLineNumbersForIgnoredEntries(ScanResult<?> fullScanResults, String filePath) {
        List<ScanIssue> allIssuesForFile = fullScanResults.getIssues();
        if (allIssuesForFile == null || allIssuesForFile.isEmpty()) {
            CxLogger.info(String.format("RTS-Ignore: No issues found in scan results for file: %s", filePath));
            return;
        }
        com.checkmarx.eclipse.devassist.model.ScanEngine modelScanEngine = allIssuesForFile.get(0).getScanEngine();
        if (modelScanEngine == null) {
            CxLogger.info(String.format("RTS-Ignore: Scan engine type is null for file: %s", filePath));
            return;
        }
        com.checkmarx.eclipse.devassist.utils.ScanEngine scanEngineType =
                com.checkmarx.eclipse.devassist.utils.ScanEngine.valueOf(modelScanEngine.toString());

        boolean hasChanges = false;
        List<String> keysToRemove = new ArrayList<>();
        Map<String, ScanIssue> scanIssueKeyMap = new HashMap<>();
        for (ScanIssue scanIssue : allIssuesForFile) {
            for (String key : createIgnoreKeysForScanIssue(scanIssue)) {
                scanIssueKeyMap.put(key, scanIssue);
            }
        }
        String relativePath = ignoreFileManager.normalizePath(filePath);
        for (Map.Entry<String, IgnoreEntry> mapEntry : ignoreFileManager.getIgnoreData().entrySet()) {
            IgnoreEntry ignoreEntry = mapEntry.getValue();
            if (ignoreEntry.getType() != scanEngineType) {
                continue; // Skip entries from different scan engines
            }
            ScanIssue matchingScanIssue = scanIssueKeyMap.get(mapEntry.getKey());
            if (matchingScanIssue != null) {
                String matchingIssuePath = ignoreFileManager.normalizePath(matchingScanIssue.getFilePath());
                if (matchingIssuePath.equals(relativePath)) {
                    if (matchingScanIssue.getLocations() == null || matchingScanIssue.getLocations().isEmpty()) {
                        continue;
                    }
                    int newLineNumber = matchingScanIssue.getLocations().get(0).getLine();
                    for (IgnoreEntry.FileReference fileRef : ignoreEntry.getFiles()) {
                        if (fileRef.getPath().equals(relativePath) && fileRef.isActive()) {
                            Integer oldLineNumber = fileRef.getLine();
                            if (oldLineNumber == null || oldLineNumber != newLineNumber) {
                                fileRef.setLine(newLineNumber);
                                hasChanges = true;
                            }
                        }
                    }
                }
            } else {
                // Not found in scan results - remove if this entry has a reference for this file
                boolean hasFileRefForCurrentFile = ignoreEntry.getFiles().stream()
                        .anyMatch(fileRef -> fileRef.getPath().equals(relativePath) && fileRef.isActive());
                if (hasFileRefForCurrentFile) {
                    keysToRemove.add(mapEntry.getKey());
                }
            }
        }
        updateInIgnoredEntries(keysToRemove, hasChanges, relativePath);
    }

    /**
     * Removes entries marked for removal and persists any line-number changes to disk.
     *
     * @param keysToRemove List of keys to remove from ignore data
     * @param toUpdate     Flag indicating whether line numbers were updated and need to be saved
     * @param relativePath Relative path of the file being processed
     */
    private void updateInIgnoredEntries(List<String> keysToRemove, boolean toUpdate, String relativePath) {
        if (!keysToRemove.isEmpty()) {
            for (String keyToRemove : keysToRemove) {
                ignoreFileManager.getIgnoreData().remove(keyToRemove);
                toUpdate = true;
            }
        }
        if (toUpdate) {
            ignoreFileManager.saveIgnoreDataToDisk();
            CxLogger.info(String.format("RTS-Ignore: Line numbers updated and saved for file: %s", relativePath));
        } else {
            CxLogger.info(String.format("RTS-Ignore: No line number changes detected for file: %s", relativePath));
        }
    }

    /**
     * Updates line numbers for ignored ASCA entries based on new scan results, using
     * problematicLine content for matching (ASCA's ignore key intentionally omits the line
     * number - see {@link #createJsonKeyForIgnoreEntry} - so occurrences of the same rule in a
     * file are tracked by the source text of the flagged line rather than its position, since
     * that drifts as the file is edited). Removes file references/entries whose problematicLine
     * is no longer present in the scan result (e.g. the vulnerable code was deleted).
     * <p>
     * The passed-in {@code fullScanResults} must be UNFILTERED (built with an
     * {@link com.checkmarx.eclipse.devassist.scanners.asca.AscaScanResultAdaptor} that keeps
     * already-ignored vulnerabilities) so every currently-ignored occurrence can still be matched.
     *
     * @param fullScanResults The UNFILTERED scan results containing updated line numbers and issues
     * @param filePath        The path of the file that was scanned and needs line number updates
     */
    public void updateLineNumbersForIgnoredEntriesByProblematicLine(ScanResult<?> fullScanResults, String filePath) {
        List<ScanIssue> allIssuesForFile = fullScanResults.getIssues();
        if (allIssuesForFile == null || allIssuesForFile.isEmpty()) {
            CxLogger.info(String.format("ASCA-Ignore: No issues found in scan results for file: %s", filePath));
            return;
        }
        String relativePath = ignoreFileManager.normalizePath(filePath);
        boolean hasChanges = false;

        List<VulnerabilityWithLine> vulnerabilitiesWithLine = new ArrayList<>();
        Set<String> presentProblematicLines = new HashSet<>();
        for (ScanIssue scanIssue : allIssuesForFile) {
            if (scanIssue.getVulnerabilities() != null) {
                for (Vulnerability v : scanIssue.getVulnerabilities()) {
                    int line = (scanIssue.getLocations() != null && !scanIssue.getLocations().isEmpty())
                            ? scanIssue.getLocations().get(0).getLine() : 0;
                    vulnerabilitiesWithLine.add(new VulnerabilityWithLine(v.getProblematicLine(), line));
                    if (v.getProblematicLine() != null) {
                        presentProblematicLines.add(v.getProblematicLine());
                    }
                }
            }
        }

        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, IgnoreEntry> mapEntry : ignoreFileManager.getIgnoreData().entrySet()) {
            IgnoreEntry ignoreEntry = mapEntry.getValue();
            if (ignoreEntry.getType() != com.checkmarx.eclipse.devassist.utils.ScanEngine.ASCA) {
                continue; // Only process ASCA entries
            }
            List<IgnoreEntry.FileReference> fileRefs = ignoreEntry.getFiles();
            List<IgnoreEntry.FileReference> fileRefsToRemove = new ArrayList<>();
            for (IgnoreEntry.FileReference fileRef : fileRefs) {
                if (fileRef.getPath().equals(relativePath) && fileRef.isActive()) {
                    String ignoredProblematicLine = fileRef.getProblematicLine();
                    VulnerabilityWithLine match = vulnerabilitiesWithLine.stream()
                            .filter(vwl -> Objects.equals(vwl.problematicLine, ignoredProblematicLine))
                            .findFirst().orElse(null);
                    if (match != null && match.line > 0
                            && (fileRef.getLine() == null || fileRef.getLine() != match.line)) {
                        fileRef.setLine(match.line);
                        hasChanges = true;
                    }
                    if (ignoredProblematicLine == null || !presentProblematicLines.contains(ignoredProblematicLine)) {
                        fileRefsToRemove.add(fileRef);
                        hasChanges = true;
                    }
                }
            }
            if (!fileRefsToRemove.isEmpty()) {
                fileRefs.removeAll(fileRefsToRemove);
            }
            if (ignoreEntry.getFiles().isEmpty()) {
                keysToRemove.add(mapEntry.getKey());
            }
        }
        for (String keyToRemove : keysToRemove) {
            ignoreFileManager.getIgnoreData().remove(keyToRemove);
            hasChanges = true;
        }
        if (hasChanges) {
            ignoreFileManager.saveIgnoreDataToDisk();
            CxLogger.info(String.format(
                    "ASCA-Ignore: Line numbers and obsolete entries updated by problematicLine and saved for file: %s",
                    relativePath));
        } else {
            CxLogger.info(String.format(
                    "ASCA-Ignore: No line number or entry changes detected by problematicLine for file: %s",
                    relativePath));
        }
    }

    /** Helper for matching a problematicLine to its current line number. */
    private static class VulnerabilityWithLine {
        final String problematicLine;
        final int line;

        VulnerabilityWithLine(String problematicLine, int line) {
            this.problematicLine = problematicLine;
            this.line = line;
        }
    }

    /**
     * Removes all ASCA ignore entries/file references for a file when a full re-scan finds no
     * issues at all in it (e.g. every flagged line was deleted).
     *
     * @param filePath The path of the file for which ignore entries should be removed
     */
    public void removeIgnoreEntriesForFileIfEmpty(String filePath) {
        String relativePath = ignoreFileManager.normalizePath(filePath);
        List<String> keysToRemove = new ArrayList<>();
        boolean removed = false;
        for (Map.Entry<String, IgnoreEntry> mapEntry : ignoreFileManager.getIgnoreData().entrySet()) {
            IgnoreEntry ignoreEntry = mapEntry.getValue();
            if (ignoreEntry.getType() != com.checkmarx.eclipse.devassist.utils.ScanEngine.ASCA) {
                continue;
            }
            List<IgnoreEntry.FileReference> fileRefs = ignoreEntry.getFiles();
            fileRefs.removeIf(fileRef -> fileRef.getPath().equals(relativePath));
            if (ignoreEntry.getFiles().isEmpty()) {
                keysToRemove.add(mapEntry.getKey());
            }
        }
        for (String keyToRemove : keysToRemove) {
            ignoreFileManager.getIgnoreData().remove(keyToRemove);
            removed = true;
        }
        if (removed) {
            ignoreFileManager.saveIgnoreDataToDisk();
            CxLogger.info(String.format("ASCA-Ignore: Removed ignore entries for file with no issues: %s", relativePath));
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
