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

    public interface IgnoreListener {
        void onIgnoreUpdated();
    }

    public static synchronized IgnoreFileManager getInstance(IProject project) {
        if (!INSTANCES.containsKey(project)) {
            INSTANCES.put(project, new IgnoreFileManager(project));
        }
        return INSTANCES.get(project);
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
        ResourcesPlugin.getWorkspace().addResourceChangeListener((IResourceChangeListener) event -> {
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
        }, IResourceChangeEvent.POST_CHANGE);
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
        Path ignoreFile = getIgnoreFilePath();
        if (!Files.exists(ignoreFile)) {
            CxLogger.info(String.format("RTS-Ignore: Ignore file doesn't exist: %s", ignoreFile));
            ignoreData = new HashMap<>();
            return;
        }
        try (InputStream inputStream = Files.newInputStream(ignoreFile)) {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, IgnoreEntry> data = mapper.readValue(inputStream,
                    new TypeReference<Map<String, IgnoreEntry>>() {
                    });
            ignoreData.clear();
            ignoreData.putAll(data);
        } catch (IOException e) {
            CxLogger.error("Failed to read ignore file: " + ignoreFile, e);
            ignoreData = new HashMap<>();
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
            Files.writeString(getIgnoreFilePath(), json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            notifyListeners();
        } catch (IOException e) {
            CxLogger.warning("RTS-Ignore: Exception occurred while adding ignore entry into file: " + e.getMessage());
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
        CxLogger.info(String.format("RTS-Ignore: Updating temp list with %d ignore entries", ignoreData.size()));
        for (IgnoreEntry entry : ignoreData.values()) {
            boolean hasActive = entry.files.stream().anyMatch(f -> f.active);
            if (!hasActive) continue;
            switch (entry.type) {
                case OSS:
                    tempList.add(TempItem.forOss(entry.packageManager, entry.packageName, entry.packageVersion));
                    break;
                case SECRETS:
                    tempList.add(TempItem.forSecret(entry.packageName, entry.secretValue));
                    break;
                case IAC:
                    tempList.add(TempItem.forIac(entry.packageName, entry.similarityId));
                    break;
                case CONTAINERS:
                    tempList.add(TempItem.forContainer(entry.imageName, entry.imageTag));
                    break;
                case ASCA:
                    for (IgnoreEntry.FileReference file : entry.files) {
                        if (!file.active) continue;
                        String originalPath = Paths.get(workspaceRootPath, file.path).toAbsolutePath().toString();
                        String scannedTempPath = scannedFileMap.getOrDefault(originalPath, originalPath);
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
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(tempList);
            Files.writeString(getTempListPath(), json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            CxLogger.error("RTS-Ignore: Failed to update temp list: " + e.getMessage(), e);
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
        saveIgnoreFile();
        updateIgnoreTempList();
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
        return Path.of(workspaceRootPath)
                .relativize(Paths.get(filePath))
                .toString()
                .replace("\\", "/");
    }

    private void handleFileChange() {
        loadIgnoreData();
        detectAndHandleActiveChanges();
        previousIgnoreData = copyIgnoreData(ignoreData);
        notifyListeners();
    }


    private void detectAndHandleActiveChanges() {
        List<ActiveFile> previousActiveFiles = getActiveFilesList(previousIgnoreData);
        List<ActiveFile> currentActiveFiles = getActiveFilesList(ignoreData);

        List<ActiveFile> deactivatedFiles = previousActiveFiles.stream()
                .filter(prev -> currentActiveFiles.stream()
                        .noneMatch(cur -> cur.packageKey.equals(prev.packageKey) && cur.path.equals(prev.path)))
                .collect(Collectors.toList());
        if (!deactivatedFiles.isEmpty()) {
            for (ActiveFile f : deactivatedFiles) {
                removeIgnoredEntryWithoutTempUpdate(f.packageKey, f.path);
            }
            updateIgnoreTempList();
        }
        // Remove entries where all files are inactive
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, IgnoreEntry> entry : ignoreData.entrySet()) {
            boolean hasActive = entry.getValue().files.stream().anyMatch(f -> f.active);
            if (!hasActive) {
                keysToRemove.add(entry.getKey());
            }
        }
        if (!keysToRemove.isEmpty()) {
            for (String key : keysToRemove) {
                ignoreData.remove(key);
            }
            saveIgnoreFile();
        }

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
