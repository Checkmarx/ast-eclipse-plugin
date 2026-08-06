package com.checkmarx.eclipse.devassist.ignore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.Platform;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.utils.CxLogger;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads/writes the ignore data file (.checkmarxIgnored.json) and notifies listeners
 * when it changes.
 *
 * Workspace-scoped (one file for the whole Eclipse workspace), matching the existing
 * IgnoredProblemsStore precedent (com.checkmarx.ast.eclipse instance-scope preferences)
 * rather than JetBrains' per-project storage - Eclipse's ScanManager/ProblemDecorator
 * pipeline operates on absolute file paths without a consistently threaded IProject,
 * so a single workspace-wide store is the natural fit here.
 *
 * Mirrors JetBrains IgnoreFileManager.
 */
public final class IgnoreFileManager {

	private static final String LOG_TAG = "[IGNORE-FILE-MGR]";
	private static final String IGNORE_FILE_NAME = ".checkmarxIgnored.json";

	private static final IgnoreFileManager INSTANCE = new IgnoreFileManager();
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final Map<String, IgnoreEntry> ignoreData = new HashMap<>();
	private final List<IgnoreListener> listeners = new ArrayList<>();

	private IgnoreFileManager() {
		loadIgnoreData();
	}

	public static IgnoreFileManager getInstance() {
		return INSTANCE;
	}

	public interface IgnoreListener {
		void onIgnoreUpdated();
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
		for (IgnoreListener listener : listeners) {
			try {
				listener.onIgnoreUpdated();
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Listener threw: " + e.getMessage());
			}
		}
	}

	/**
	 * Live, mutable ignore data map. Callers (IgnoreManager) may mutate entries directly
	 * and must call saveIgnoreDataToDisk() afterwards.
	 */
	public Map<String, IgnoreEntry> getIgnoreData() {
		return ignoreData;
	}

	public List<IgnoreEntry> getAllIgnoreEntries() {
		return new ArrayList<>(ignoreData.values());
	}

	public void updateIgnoreData(String key, IgnoreEntry entry) {
		if (key == null || entry == null) {
			return;
		}
		ignoreData.put(key, entry);
		saveIgnoreDataToDisk();
	}

	private Path getIgnoreFilePath() {
		return Platform.getStateLocation(Activator.getDefault().getBundle()).append(IGNORE_FILE_NAME).toFile()
				.toPath();
	}

	public void loadIgnoreData() {
		Path path = getIgnoreFilePath();
		if (!Files.exists(path)) {
			return;
		}
		try (InputStream in = Files.newInputStream(path)) {
			Map<String, IgnoreEntry> data = MAPPER.readValue(in, new TypeReference<Map<String, IgnoreEntry>>() {
			});
			ignoreData.clear();
			if (data != null) {
				ignoreData.putAll(data);
			}
			CxLogger.info(LOG_TAG + " Loaded " + ignoreData.size() + " ignore entries from " + path);
		} catch (IOException e) {
			CxLogger.warning(LOG_TAG + " Failed to load ignore file: " + e.getMessage());
			ignoreData.clear();
		}
	}

	/**
	 * Saves the current ignore data to disk and notifies listeners.
	 * Public because reconciliation (line-number updates) mutates entries directly
	 * via getIgnoreData() and must explicitly persist afterwards.
	 */
	public void saveIgnoreDataToDisk() {
		try {
			Path path = getIgnoreFilePath();
			Files.createDirectories(path.getParent());
			String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ignoreData);
			Files.writeString(path, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);
			notifyListeners();
		} catch (IOException e) {
			CxLogger.warning(LOG_TAG + " Failed to save ignore file: " + e.getMessage());
		}
	}

	/**
	 * Marks every file reference in the entry matching entryToRevive's identity as inactive
	 * (revive = stop ignoring, but keep history instead of deleting).
	 *
	 * @return true if a matching entry was found and revived
	 */
	public boolean reviveEntry(String key) {
		IgnoreEntry entry = ignoreData.get(key);
		if (entry == null) {
			return false;
		}
		for (IgnoreEntry.FileReference ref : entry.getFiles()) {
			ref.setActive(false);
		}
		saveIgnoreDataToDisk();
		return true;
	}

	public void removeEntry(String key) {
		if (ignoreData.remove(key) != null) {
			saveIgnoreDataToDisk();
		}
	}

	public void clearAll() {
		ignoreData.clear();
		saveIgnoreDataToDisk();
	}

	static boolean pathsEqual(String a, String b) {
		return Objects.equals(normalizeForCompare(a), normalizeForCompare(b));
	}

	private static String normalizeForCompare(String path) {
		return path == null ? null : path.replace('\\', '/');
	}
}
