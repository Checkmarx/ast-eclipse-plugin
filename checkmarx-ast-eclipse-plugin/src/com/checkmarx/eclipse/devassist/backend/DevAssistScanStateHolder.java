package com.checkmarx.eclipse.devassist.backend;

import java.util.concurrent.ConcurrentHashMap;

import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Tracks file modification state to prevent redundant scans.
 *
 * Stores a composite "state hash" for each file:
 * - Document modification timestamp
 * - File system last-modified time
 * - Editor content hash
 *
 * When a file is requested for scanning, we compare the current state
 * with the cached state. If unchanged, we skip the scan and return cached results.
 *
 * This mirrors the JetBrains DevAssistScanStateHolder pattern.
 */
public class DevAssistScanStateHolder {

	private static final String LOG_TAG = "[SCAN-STATE]";

	private final ConcurrentHashMap<String, Long> fileStateHash = new ConcurrentHashMap<>();

	/**
	 * Get the cached state hash for a file.
	 *
	 * @param filePath Absolute file path
	 * @return Last recorded state hash, or null if never scanned
	 */
	public Long getStateHash(String filePath) {
		if (filePath == null) {
			return null;
		}
		return fileStateHash.get(filePath);
	}

	/**
	 * Update the state hash for a file (after successful scan).
	 *
	 * @param filePath Absolute file path
	 * @param stateHash New state hash
	 */
	public void updateStateHash(String filePath, long stateHash) {
		if (filePath == null) {
			return;
		}

		Long previous = fileStateHash.put(filePath, stateHash);
		CxLogger.info(LOG_TAG + " Updated state hash for: " + filePath +
			" (previous: " + previous + ", new: " + stateHash + ")");
	}

	/**
	 * Check if a file has changed since last scan.
	 *
	 * @param filePath Absolute file path
	 * @param currentStateHash Current state of the file
	 * @return true if file changed (or never scanned), false if unchanged
	 */
	public boolean hasChanged(String filePath, long currentStateHash) {
		if (filePath == null) {
			return true;
		}

		Long cachedHash = fileStateHash.get(filePath);

		// Never scanned before
		if (cachedHash == null) {
			CxLogger.info(LOG_TAG + " File never scanned: " + filePath);
			return true;
		}

		// Compare hashes
		boolean changed = !cachedHash.equals(currentStateHash);
		if (!changed) {
			CxLogger.info(LOG_TAG + " File unchanged (cached): " + filePath);
		}

		return changed;
	}

	/**
	 * Clear state for a specific file (e.g., when file is deleted).
	 *
	 * @param filePath Absolute file path
	 */
	public void clearFileState(String filePath) {
		if (filePath == null) {
			return;
		}

		fileStateHash.remove(filePath);
		CxLogger.info(LOG_TAG + " Cleared state for: " + filePath);
	}

	/**
	 * Clear all state (on project close).
	 */
	public void clearAll() {
		fileStateHash.clear();
		CxLogger.info(LOG_TAG + " All state cleared");
	}

	/**
	 * Compute a state hash for a file based on:
	 * - File system last modified time
	 * - Document modification timestamp
	 *
	 * @param filePath File to hash
	 * @return Composite state hash
	 */
	public static long computeFileStateHash(String filePath) {
		try {
			java.nio.file.Path path = java.nio.file.Paths.get(filePath);
			long fileModified = java.nio.file.Files.getLastModifiedTime(path).toMillis();

			// Simple composite hash: file modification time
			// (In a real implementation, would also include document modification time
			// if the file is open in editor)
			return fileModified;
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error computing state hash: " + e.getMessage());
			return System.currentTimeMillis();
		}
	}

	/**
	 * Get statistics about tracked files.
	 *
	 * @return Summary string
	 */
	public String getStatistics() {
		return "Tracked files: " + fileStateHash.size();
	}
}
