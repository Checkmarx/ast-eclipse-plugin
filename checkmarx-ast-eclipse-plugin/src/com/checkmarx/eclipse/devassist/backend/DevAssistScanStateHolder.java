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
	 * - Document modification timestamp (if open in editor with unsaved changes)
	 *
	 * When a file is edited in Eclipse but not saved to disk, the file system
	 * timestamp doesn't change. This method detects unsaved changes by checking
	 * if the editor's dirty flag is set, and includes that in the hash.
	 *
	 * @param filePath File to hash
	 * @return Composite state hash
	 */
	public static long computeFileStateHash(String filePath) {
		try {
			java.nio.file.Path path = java.nio.file.Paths.get(filePath);
			long fileModified = java.nio.file.Files.getLastModifiedTime(path).toMillis();

			// Check if file is open in editor with unsaved changes
			// If dirty (unsaved), include a dynamic component to detect changes
			boolean hasUnsavedChanges = false;
			try {
				org.eclipse.ui.IWorkbench workbench = org.eclipse.ui.PlatformUI.getWorkbench();
				if (workbench != null && !workbench.isClosing()) {
					for (org.eclipse.ui.IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
						for (org.eclipse.ui.IWorkbenchPage page : window.getPages()) {
							for (org.eclipse.ui.IEditorReference ref : page.getEditorReferences()) {
								org.eclipse.ui.IEditorPart editor = ref.getEditor(false);
								if (editor != null && editor.isDirty()) {
									// Check if this editor is for our file
									try {
										String editorPath = editor.getEditorInput().getAdapter(org.eclipse.core.resources.IFile.class)
											.getLocation().toOSString();
										if (editorPath.equals(filePath)) {
											hasUnsavedChanges = true;
											break;
										}
									} catch (Exception e2) {
										// Skip if we can't get editor path
									}
								}
							}
							if (hasUnsavedChanges) break;
						}
						if (hasUnsavedChanges) break;
					}
				}
			} catch (Exception e) {
				// If workbench check fails, just use file timestamp
				hasUnsavedChanges = false;
			}

			// If file has unsaved changes, use current time to force re-scan
			// This ensures edits are detected even if not yet saved to disk
			if (hasUnsavedChanges) {
				return System.nanoTime();  // Force different hash on every check while dirty
			}

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
