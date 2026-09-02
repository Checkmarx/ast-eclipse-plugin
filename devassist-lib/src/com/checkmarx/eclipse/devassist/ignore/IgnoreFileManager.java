package com.checkmarx.eclipse.devassist.ignore;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.widgets.Display;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the ignore file (.checkmarxIgnored) within the project's workspace.
 * Handles reading, writing, and updating ignore entries.
 * Provides methods to ignore issues and update temporary ignore lists.
 */
public final class IgnoreFileManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static boolean skipFileWatcherForTests = false;
    private static final Map<IProject, IgnoreFileManager> INSTANCES = new HashMap<>();

    private final IProject project;
    private String workspacePath = "";
    private String workspaceRootPath = "";
    private Map<String, IgnoreEntry> ignoreData = new HashMap<>();
    private final Map<String, String> scannedFileMap = new HashMap<>();
    private Map<String, IgnoreEntry> previousIgnoreData = new HashMap<>();
    private final List<IgnoreListener> listeners = Collections.synchronizedList(new ArrayList<>());
    // Captured so dispose() can unregister it - startFileWatcher() previously
    // passed an anonymous listener straight to addResourceChangeListener() with
    // no reference kept anywhere, so a closed project's manager (and its
    // workspace-level listener, which keeps firing on every future resource
    // change regardless of which project changed) could never be unregistered.
    private IResourceChangeListener resourceChangeListener;

    public interface IgnoreListener {
        void onIgnoreUpdated();
    }

    public static synchronized IgnoreFileManager getInstance(IProject project) {
        if (!INSTANCES.containsKey(project)) {
            INSTANCES.put(project, new IgnoreFileManager(project));
        }
        return INSTANCES.get(project);
    }

    /**
     * Evicts and disposes the cached IgnoreFileManager for a closed project -
     * unregisters its workspace-level resource-change listener and drops it
     * from INSTANCES. Without this, every project ever opened in a session
     * stays in INSTANCES forever with its listener still firing on every future
     * workspace resource change, even for projects that no longer exist.
     *
     * @param project the project that is closing
     */
    public static synchronized void dispose(IProject project) {
        IgnoreFileManager manager = INSTANCES.remove(project);
        if (manager != null) {
            manager.disposeInternal();
        }
    }

    private void disposeInternal() {
        if (resourceChangeListener != null) {
            ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceChangeListener);
            resourceChangeListener = null;
        }
        listeners.clear();
    }

    public IgnoreFileManager(IProject project) {
        this.project = project;
        String basePath = project.getLocation().toOSString();
        if (basePath != null && !basePath.isEmpty()) {
            this.workspaceRootPath = basePath;
            this.workspacePath = Paths.get(basePath, ".checkmarx").toString();
            ensureIgnoreFileExists();
            loadIgnoreData();
            this.previousIgnoreData = copyIgnoreData(ignoreData);
        }
        if (!skipFileWatcherForTests) {
            startFileWatcher();
        }
    }

    private void startFileWatcher() {
        org.eclipse.core.resources.IFile ignoreIFile = ResourcesPlugin.getWorkspace().getRoot()
                .getFileForLocation(new org.eclipse.core.runtime.Path(getIgnoreFilePath().toString()));
        if (ignoreIFile == null) {
            return;
        }
        resourceChangeListener = (IResourceChangeListener) event -> {
            IResourceDelta delta = event.getDelta();
            if (delta == null) {
                return;
            }
            IResourceDelta ignoreDelta = delta.findMember(ignoreIFile.getFullPath());
            if (ignoreDelta != null && (ignoreDelta.getFlags() & IResourceDelta.CONTENT) != 0) {
                // Marshal onto the UI thread: ignoreData is a plain HashMap and every
                // existing reader/writer (isIgnored, addIgnoredEntry, reviveEntry) already
                // only ever runs on the SWT UI thread; this keeps that invariant intact
                // instead of introducing a background-thread race on the map.
                Display display = Display.getDefault();
                if (display != null && !display.isDisposed()) {
                    display.asyncExec(this::handleFileChange);
                }
            }
        };
        ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceChangeListener, IResourceChangeEvent.POST_CHANGE);
    }

    public void updateIgnoreData(String vulnerabilityKey, IgnoreEntry newData) {
        if (newData == null) return;
        ignoreData.put(vulnerabilityKey, newData);
        saveIgnoreFile();
        updateIgnoreTempList();
    }

    /**
     * Ensures the ignored file exists;
     * Creates it if missing.
     * Logs a warning if creation fails.
     */

    private void ensureIgnoreFileExists() {
        try {
            Path dir = Paths.get(workspacePath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path ignoreFile = getIgnoreFilePath();
            if (!Files.exists(ignoreFile)) {
                Files.write(ignoreFile, "{}\n".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            CxLogger.error("Failed to ensure ignore file exists", e);
        }
    }

    public void loadIgnoreData() {
        loadIgnoreDataInternal();
    }

    /**
     * Loads ignore data from disk into {@link #ignoreData}.
     *
     * @return true if the file was read and parsed successfully (or genuinely
     *         doesn't exist yet, which is a legitimate empty state), false if a
     *         read/parse error occurred. On false, {@link #ignoreData} is left
     *         untouched - a transient read error (e.g. the file watcher observing
     *         our own write mid-flight) must never be treated as "the file is now
     *         empty", or every previously-ignored entry appears to vanish from
     *         the Ignored Findings window and reappears as an active finding in
     *         the editor until the next successful reload.
     */
    private boolean loadIgnoreDataInternal() {
        Path ignoreFile = getIgnoreFilePath();
        if (!Files.exists(ignoreFile)) {
            CxLogger.info(String.format("RTS-Ignore: Ignore file doesn't exist: %s", ignoreFile));
            ignoreData = new HashMap<>();
            return true;
        }
        try (InputStream inputStream = Files.newInputStream(ignoreFile)) {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, IgnoreEntry> data = mapper.readValue(inputStream,
                    new TypeReference<Map<String, IgnoreEntry>>() {
                    });
            ignoreData.clear();
            ignoreData.putAll(data);
            return true;
        } catch (IOException e) {
            CxLogger.warning("RTS-Ignore: Failed to read ignore file (keeping previous in-memory state): "
                    + ignoreFile + " - " + e.getMessage());
            return false;
        }
    }


    /**
     * Returns all ignore entries.
     *
     * @return list of ignore entries.
     */
    public List<IgnoreEntry> getAllIgnoreEntries() {
        return new ArrayList<>(ignoreData.values());
    }

    /**
     * Returns the ignore data map for this project.
     * This is an instance method to ensure project-level isolation.
     *
     * @return the ignore data map
     */
    public Map<String, IgnoreEntry> getIgnoreData() {
        return ignoreData;
    }

    /**
     * Checks if a ScanIssue is ignored based on similarity ID.
     *
     * @param similarityId The similarity ID of the issue
     * @return true if the issue is ignored, false otherwise
     */
    public boolean isIgnored(String similarityId) {
        if (similarityId == null || similarityId.isEmpty()) {
            return false;
        }
        return ignoreData.containsKey(similarityId);
    }

    /**
     * Saves the current ignore data to the ignore file.
     * Writes the ignore data as formatted JSON to the file specified by {@link #getIgnoreFilePath()}.
     * Creates a new file if it doesn't exist, or truncates the existing file.
     * Notifies all subscribers about the update.
     * Logs a warning if saving fails.
     */
    private void saveIgnoreFile() {
        try {
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ignoreData);
            Path ignoreFilePath = getIgnoreFilePath();
            writeAtomically(ignoreFilePath, json);
            refreshFileInWorkspace(ignoreFilePath);
            notifyListeners();
        } catch (IOException e) {
            CxLogger.warning("RTS-Ignore: Exception occurred while adding ignore entry into file: " + e.getMessage());
        }
    }

    /**
     * Forces Eclipse's resource model to pick up a file we just wrote directly
     * via java.nio (writeAtomically bypasses IFile/IResource entirely). Without
     * this, the Ignored Findings view (or any other resource-change listener)
     * only learns about the change once native/polling workspace refresh gets
     * around to it - which can be disabled or delayed in managed/enterprise
     * Eclipse installs - leaving a window where the on-disk file and Eclipse's
     * view of it disagree.
     */
    private void refreshFileInWorkspace(Path filePath) {
        try {
            org.eclipse.core.resources.IFile file = ResourcesPlugin.getWorkspace().getRoot()
                    .getFileForLocation(new org.eclipse.core.runtime.Path(filePath.toString()));
            if (file != null) {
                file.refreshLocal(org.eclipse.core.resources.IResource.DEPTH_ZERO, null);
            }
        } catch (org.eclipse.core.runtime.CoreException e) {
            CxLogger.warning("RTS-Ignore: Failed to refresh workspace resource for " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Writes content to the target path atomically: write to a sibling temp
     * file, then move it into place with ATOMIC_MOVE.
     *
     * Files.writeString(..., TRUNCATE_EXISTING) truncates the file to zero
     * length before writing the new content, which is observable as two
     * separate filesystem events (truncate, then write). Eclipse's resource
     * watcher (native hooks or polling refresh) can fire on that intermediate,
     * momentarily-empty state, causing a concurrent loadIgnoreData() call to
     * throw MismatchedInputException on a file that was never actually
     * corrupted on disk - just read mid-write. An atomic rename never exposes
     * that intermediate state: a reader always sees either the complete old
     * content or the complete new content.
     */
    private void writeAtomically(Path target, String content) throws IOException {
        Path tempFile = target.resolveSibling(target.getFileName().toString() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tempFile, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // Some filesystems (e.g. certain network drives) don't support atomic
                // moves across the temp/target pair - fall back to a plain replace.
                Files.move(tempFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public void addListener(IgnoreListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(IgnoreListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (IgnoreListener listener : new ArrayList<>(listeners)) {
            try {
                listener.onIgnoreUpdated();
            } catch (Exception e) {
                CxLogger.warning("Error notifying ignore listener: " + e.getMessage());
            }
        }
    }


    /**
     * Updates the temporary ignore list file based on active ignore entries.
     * Creates a list of temporary items from active ignore entries, categorized by their type (OSS, Secrets, IAC, Containers, ASCA).
     * For each entry type:
     * - OSS: adds package manager, name and version
     * - Secrets: adds package name and secret value
     * - IAC: adds package name and similarity ID
     * - Containers: adds image name and image tag
     * - ASCA: adds file name, line number and rule ID for each active file
     * The temporary list is then saved to a JSON file at the path specified by {@link #getTempListPath()}.
     */
    public void updateIgnoreTempList() {
        List<TempItem> tempList = new ArrayList<>();
        CxLogger.info(String.format("RTS-Ignore: [TEMP_LIST_UPDATE] Updating temp list with %d ignore entries", ignoreData.size()));

        for (IgnoreEntry entry : ignoreData.values()) {
            boolean hasActive = entry.files.stream().anyMatch(f -> f.active);
            if (!hasActive) {
                CxLogger.info("RTS-Ignore: [TEMP_LIST_UPDATE] Skipping entry with no active files: " + entry.getPackageName());
                continue;
            }
            switch (entry.type) {
                case OSS:
                    CxLogger.info("RTS-Ignore: [TEMP_LIST_UPDATE] Adding OSS entry: " + entry.getPackageName());
                    tempList.add(TempItem.forOss(entry.packageManager, entry.packageName, entry.packageVersion));
                    break;
                case SECRETS:
                    CxLogger.info("RTS-Ignore: [TEMP_LIST_UPDATE] Adding SECRETS entry: " + entry.getPackageName());
                    tempList.add(TempItem.forSecret(entry.packageName, entry.secretValue));
                    break;
                case IAC:
                    CxLogger.info("RTS-Ignore: [TEMP_LIST_UPDATE] Adding IAC entry: " + entry.getPackageName());
                    tempList.add(TempItem.forIac(entry.packageName, entry.similarityId));
                    break;
                case CONTAINERS:
                    CxLogger.info("RTS-Ignore: [TEMP_LIST_UPDATE] Adding CONTAINERS entry: " + entry.getImageName());
                    tempList.add(TempItem.forContainer(entry.imageName, entry.imageTag));
                    break;
                case ASCA:
                    for (IgnoreEntry.FileReference file : entry.files) {
                        if (!file.active) continue;
                        String originalPath = Paths.get(workspaceRootPath, file.path).toAbsolutePath().toString();
                        String scannedTempPath = scannedFileMap.getOrDefault(originalPath, originalPath);
                        CxLogger.info("RTS-Ignore: [TEMP_LIST_UPDATE] Adding ASCA entry: " + entry.getPackageName() + " at line " + file.line);
                        tempList.add(TempItem.forAsca(
                                Paths.get(scannedTempPath).getFileName().toString(),
                                file.line,
                                entry.ruleId
                        ));
                    }
                    break;
                default:
                    break;
            }
        }

        try {
            CxLogger.info("RTS-Ignore: [TEMP_LIST_UPDATE] Writing " + tempList.size() + " items to temp list file");
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(tempList);
            Path tempListPath = getTempListPath();
            writeAtomically(tempListPath, json);
            refreshFileInWorkspace(tempListPath);
            CxLogger.info("RTS-Ignore: [TEMP_LIST_UPDATE_SUCCESS] Temp list updated successfully with " + tempList.size() + " items");

            // If tempList is empty, verify the file is actually empty
            if (tempList.isEmpty()) {
                CxLogger.info("RTS-Ignore: [TEMP_LIST_UPDATE] Temp list is empty - file should contain empty array []");
            }
        } catch (IOException e) {
            CxLogger.error("RTS-Ignore: [TEMP_LIST_UPDATE_ERROR] Failed to update temp list: " + e.getMessage(), e);
        }
    }

    /**
     * Revives a previously ignored package by setting all its file references to inactive.
     * This makes the vulnerability visible again in future scans.
     *
     * @param entryToRevive The unique key identifying the ignored package
     * @return true if the package was found and revived, false otherwise
     */
    public boolean reviveEntry(IgnoreEntry entryToRevive) {
        boolean success = reviveEntryInternal(entryToRevive);
        if (success) {
            saveIgnoreFile();
            updateIgnoreTempList();
        }
        return success;
    }

    /**
     * Internal method to revive an entry without saving to disk.
     * Used by batch operations that need to revive multiple entries before saving once.
     *
     * @param entryToRevive The unique key identifying the ignored package
     * @return true if the package was found and revived, false otherwise
     */
    private boolean reviveEntryInternal(IgnoreEntry entryToRevive) {
        String entryKey = ignoreData.entrySet().stream()
                .filter(e -> matchesEntry(e.getValue(), entryToRevive))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (entryKey == null) {
            CxLogger.warning("RTS-Ignore: Entry not found in ignoreData map");
            return false;
        }
        IgnoreEntry actualEntry = ignoreData.get(entryKey);
        String packageName = entryToRevive.getPackageName();
        for (IgnoreEntry.FileReference file : actualEntry.getFiles()) {
            file.active = false;
        }
        CxLogger.info("RTS-Ignore: Revived package: " + packageName);
        return true;
    }


    public Path getIgnoreFilePath() {
        return Paths.get(workspacePath, ".checkmarxIgnored");
    }

    /**
     * Deletes the ignore file (.checkmarxIgnored) and the temporary ignore list file.
     * Called when the user no longer has a valid license (platform-only license).
     * This ensures that ignored findings are cleared when the feature is not available.
     */
    public void deleteIgnoreFiles() {
        try {
            Path ignoreFilePath = getIgnoreFilePath();
            if (Files.exists(ignoreFilePath)) {
                Files.delete(ignoreFilePath);
                CxLogger.info("RTS-Ignore: Deleted ignore file at " + ignoreFilePath);
            }

            Path tempListPath = Paths.get(workspacePath, ".checkmarxIgnoredTempList.json");
            if (Files.exists(tempListPath)) {
                Files.delete(tempListPath);
                CxLogger.info("RTS-Ignore: Deleted temp list file at " + tempListPath);
            }

            // Clear in-memory data
            ignoreData.clear();
            previousIgnoreData.clear();

            // Notify listeners that ignore data has changed
            notifyListeners();
        } catch (IOException e) {
            CxLogger.error("RTS-Ignore: Failed to delete ignore files", e);
        }
    }

    /**
     * Returns the path to the temporary ignore list.
     * Creates the file if it doesn't exist.
     *
     * @return path to the temporary ignore list.
     *
     */
    public Path getTempListPath() {
        Path tempListPath = Paths.get(workspacePath, ".checkmarxIgnoredTempList.json");
        if (Files.exists(tempListPath)) {
            try {
                // Validate it's a valid JSON array
                if (Files.readString(tempListPath).trim().isEmpty()) {
                    Files.writeString(tempListPath, "[]", StandardCharsets.UTF_8,
                            StandardOpenOption.TRUNCATE_EXISTING);
                }
                return tempListPath;
            } catch (IOException e) {
                CxLogger.error("Failed to validate temp list: " + tempListPath, e);
                createEmptyTempList(tempListPath);
            }
        } else {
            createEmptyTempList(tempListPath);
        }
        return tempListPath;  // Guaranteed to exist and contain []
    }

    private void createEmptyTempList(Path tempListPath) {
        try {
            Files.createDirectories(tempListPath.getParent());
            Files.writeString(tempListPath, "[]", StandardCharsets.UTF_8);
            CxLogger.info(String.format("RTS-Ignore: Created empty temp list at %s", tempListPath));
        } catch (IOException e) {
            CxLogger.error("Failed to create empty temp list", e);
        }
    }

    /**
     * normalizes the given file path to be relative to the project's workspace root.
     *
     * @param filePath
     * @return
     */
    public String normalizePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        try {
            return Path.of(workspaceRootPath)
                    .relativize(Paths.get(filePath))
                    .toString()
                    .replace("\\", "/");
        } catch (Exception e) {
            // Malformed/foreign path (e.g. a different filesystem root) - fall back to
            // the raw path rather than throwing, so callers that iterate over many
            // issues (e.g. "ignore all of this type") don't abort partway through.
            return filePath.replace("\\", "/");
        }
    }

    private void handleFileChange() {
        CxLogger.info("RTS-Ignore: [FILE_WATCHER] File change detected in .checkmarxIgnored");
        CxLogger.info("RTS-Ignore: [FILE_WATCHER] Current ignoreData size: " + ignoreData.size());
        refreshFromDisk();
        CxLogger.info("RTS-Ignore: [FILE_WATCHER] After refresh, ignoreData size: " + ignoreData.size());
    }

    /**
     * Re-reads .checkmarxIgnored from disk and reconciles derived state
     * (.checkmarxIgnoredTempList.json, listeners) against it.
     *
     * This is the same reconciliation the file watcher triggers on a detected
     * change, exposed so callers that can't rely on Eclipse's resource-change
     * notification firing reliably for every edit (e.g. a genuinely external
     * edit made while native/polling refresh is disabled, or before a
     * time-sensitive read like kicking off a scan) can force it explicitly.
     *
     * @return true if the file was read successfully (or doesn't exist),
     *         false if a transient read/parse error occurred - in which case
     *         in-memory state was left untouched and no reconciliation ran.
     */
    public boolean refreshFromDisk() {
        Map<String, IgnoreEntry> beforeIgnoreData = copyIgnoreData(ignoreData);
        if (!loadIgnoreDataInternal()) {
            // Transient read failure (e.g. watcher observed a mid-write state) -
            // ignoreData was left untouched, so there is nothing real to react to
            // this cycle. Bail out without cascading a false "everything
            // deactivated" into detectAndHandleActiveChanges()/updateIgnoreTempList().
            return false;
        }
        detectAndHandleActiveChanges();
        previousIgnoreData = copyIgnoreData(ignoreData);

        // Only notify listeners if the ignore data actually changed
        if (!ignoreDataEquals(beforeIgnoreData, ignoreData)) {
            notifyListeners();
        }
        return true;
    }

    private boolean ignoreDataEquals(Map<String, IgnoreEntry> map1, Map<String, IgnoreEntry> map2) {
        if (map1.size() != map2.size()) {
            return false;
        }
        for (String key : map1.keySet()) {
            if (!map2.containsKey(key)) {
                return false;
            }
            // Compare JSON serialization for deep equality
            try {
                String json1 = MAPPER.writeValueAsString(map1.get(key));
                String json2 = MAPPER.writeValueAsString(map2.get(key));
                if (!json1.equals(json2)) {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }


    private void detectAndHandleActiveChanges() {
        CxLogger.info("RTS-Ignore: [DETECT_CHANGES] Detecting active changes in ignore data");
        List<ActiveFile> previousActiveFiles = getActiveFilesList(previousIgnoreData);
        List<ActiveFile> currentActiveFiles = getActiveFilesList(ignoreData);

        CxLogger.info("RTS-Ignore: [DETECT_CHANGES] Previous active files: " + previousActiveFiles.size());
        CxLogger.info("RTS-Ignore: [DETECT_CHANGES] Current active files: " + currentActiveFiles.size());

        List<ActiveFile> deactivatedFiles = previousActiveFiles.stream()
                .filter(prev -> currentActiveFiles.stream()
                        .noneMatch(cur -> cur.packageKey.equals(prev.packageKey) && cur.path.equals(prev.path)))
                .collect(Collectors.toList());

        if (!deactivatedFiles.isEmpty()) {
            CxLogger.info("RTS-Ignore: [DETECT_CHANGES] Found " + deactivatedFiles.size() + " deactivated files");
            for (ActiveFile f : deactivatedFiles) {
                CxLogger.info("RTS-Ignore: [DETECT_CHANGES] Removing deactivated entry: " + f.packageKey + " at " + f.path);
                removeIgnoredEntryWithoutTempUpdate(f.packageKey, f.path);
            }
            updateIgnoreTempList();
        }

        // Remove entries where all files are inactive
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, IgnoreEntry> entry : ignoreData.entrySet()) {
            boolean hasActive = entry.getValue().files.stream().anyMatch(f -> f.active);
            if (!hasActive) {
                CxLogger.info("RTS-Ignore: [DETECT_CHANGES] Entry has no active files, marking for removal: " + entry.getKey());
                keysToRemove.add(entry.getKey());
            }
        }
        if (!keysToRemove.isEmpty()) {
            CxLogger.info("RTS-Ignore: [DETECT_CHANGES] Removing " + keysToRemove.size() + " entries with no active files");
            for (String key : keysToRemove) {
                CxLogger.info("RTS-Ignore: [DETECT_CHANGES] Removing key: " + key);
                ignoreData.remove(key);
            }
            saveIgnoreFile();
        }

        CxLogger.info("RTS-Ignore: [DETECT_CHANGES_COMPLETE] After changes, ignoreData size: " + ignoreData.size());
    }

    private static final class ActiveFile {
        final String packageKey;
        final String path;

        ActiveFile(String packageKey, String path) {
            this.packageKey = packageKey;
            this.path = path;
        }
    }

    private List<ActiveFile> getActiveFilesList(Map<String, IgnoreEntry> data) {
        List<ActiveFile> result = new ArrayList<>();
        for (Map.Entry<String, IgnoreEntry> e : data.entrySet()) {
            for (IgnoreEntry.FileReference fileRef : e.getValue().files) {
                if (fileRef.active) {
                    result.add(new ActiveFile(e.getKey(), fileRef.path));
                }
            }
        }
        return result;
    }

    private void removeIgnoredEntryWithoutTempUpdate(String packageKey, String filePath) {
        IgnoreEntry entry = ignoreData.get(packageKey);
        if (entry == null) return;
        entry.files.removeIf(fileRef -> fileRef.path.equals(filePath));
        if (entry.files.isEmpty()) {
            ignoreData.remove(packageKey);
        }
        saveIgnoreFile();

    }

    private Map<String, IgnoreEntry> copyIgnoreData(Map<String, IgnoreEntry> src) {
        // Deep copy via JSON round-trip
        try {
            String json = MAPPER.writeValueAsString(src);
            return MAPPER.readValue(json, new TypeReference<Map<String, IgnoreEntry>>() {
            });
        } catch (IOException e) {
            CxLogger.error("Failed to deep copy ignoreData, falling back to shallow copy", e);
            return new HashMap<>(src);
        }
    }

    // Helper method to match entries by properties
    public boolean matchesEntry(IgnoreEntry entry1, IgnoreEntry entry2) {
        if (entry1.getType() != entry2.getType()) return false;
        // Match based on type-specific unique identifiers
        switch (entry1.getType()) {
            case OSS:
                return Objects.equals(entry1.getPackageName(), entry2.getPackageName()) &&
                        Objects.equals(entry1.getPackageVersion(), entry2.getPackageVersion()) &&
                        Objects.equals(entry1.getPackageManager(), entry2.getPackageManager());
            case CONTAINERS:
                return Objects.equals(entry1.getImageName(), entry2.getImageName()) &&
                        Objects.equals(entry1.getImageTag(), entry2.getImageTag());
            case SECRETS:
                return Objects.equals(entry1.getPackageName(), entry2.getPackageName()) &&
                        Objects.equals(entry1.getSecretValue(), entry2.getSecretValue());
            case IAC:
                return Objects.equals(entry1.getPackageName(), entry2.getPackageName()) &&
                        Objects.equals(entry1.getSimilarityId(), entry2.getSimilarityId());
            case ASCA:
                return Objects.equals(entry1.getPackageName(), entry2.getPackageName()) &&
                        Objects.equals(entry1.getRuleId(), entry2.getRuleId());
            default:
                return false;
        }
    }

    /**
     * Saves the current ignore data to disk.
     * This is a public wrapper for the private saveIgnoreFile method.
     * Used when ignore data is modified directly (e.g., line number updates).
     */
    public void saveIgnoreDataToDisk() {
        saveIgnoreFile();
        updateIgnoreTempList();
    }
}
