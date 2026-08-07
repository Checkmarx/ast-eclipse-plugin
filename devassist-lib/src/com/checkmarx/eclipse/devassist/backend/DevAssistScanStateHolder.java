package com.checkmarx.eclipse.devassist.backend;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.security.MessageDigest;

import com.checkmarx.eclipse.common.utils.CxLogger;

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
	// Atomic in-flight marker to prevent concurrent scans of the same file
	// putIfAbsent() detects if another thread is already scanning this file
	private final ConcurrentHashMap<String, Boolean> inFlightScans = new ConcurrentHashMap<>();

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
	 * Check if a file has changed since last scan AND mark it as in-flight.
	 * CRITICAL: Uses atomic putIfAbsent() to prevent concurrent scans of the same file.
	 * If another thread is already scanning this file, returns false to skip duplicate work.
	 *
	 * @param filePath Absolute file path
	 * @param currentStateHash Current state of the file
	 * @return true if file changed AND no other scan is in-flight, false otherwise
	 */
	public boolean hasChanged(String filePath, long currentStateHash) {
		if (filePath == null) {
			return true;
		}

		Long cachedHash = fileStateHash.get(filePath);

		// Never scanned before
		if (cachedHash == null) {
			CxLogger.info(LOG_TAG + " File never scanned: " + filePath);
			// Atomic check: if another thread beat us here, skip to avoid duplicate work
			if (inFlightScans.putIfAbsent(filePath, true) != null) {
				CxLogger.info(LOG_TAG + " BLOCKED: Another scan already in-flight for: " + filePath);
				return false;
			}
			return true;
		}

		// Compare hashes
		boolean changed = !cachedHash.equals(currentStateHash);
		if (!changed) {
			CxLogger.info(LOG_TAG + " File unchanged (cached): " + filePath);
			return false;
		}

		// File changed - atomically mark as in-flight to prevent duplicate concurrent scans
		if (inFlightScans.putIfAbsent(filePath, true) != null) {
			CxLogger.info(LOG_TAG + " BLOCKED: Another scan already in-flight for: " + filePath);
			return false;
		}

		return true;
	}

	/**
	 * Mark a file scan as complete (remove in-flight marker).
	 * MUST be called after scan completes to unblock other threads.
	 *
	 * @param filePath Absolute file path
	 */
	public void markScanComplete(String filePath) {
		if (filePath == null) {
			return;
		}
		inFlightScans.remove(filePath);
	}

	/**
	 * Clear state for a specific file (e.g., when file is deleted).
	 * Also clears any in-flight scan marker.
	 *
	 * @param filePath Absolute file path
	 */
	public void clearFileState(String filePath) {
		if (filePath == null) {
			return;
		}

		fileStateHash.remove(filePath);
		inFlightScans.remove(filePath);
		CxLogger.info(LOG_TAG + " Cleared state for: " + filePath);
	}

	/**
	 * Clear all state (on project close).
	 * Also clears all in-flight scan markers.
	 */
	public void clearAll() {
		fileStateHash.clear();
		inFlightScans.clear();
		CxLogger.info(LOG_TAG + " All state cleared");
	}

	/**
	 * Compute a state hash for a file based on:
	 * - File system last modified time
	 * - Document content hash (if open in editor with unsaved changes)
	 *
	 * CRITICAL FIX: When file is dirty (unsaved), hash actual document content instead of
	 * using System.nanoTime(). Previous implementation returned different hash on every call,
	 * causing unnecessary rescans even when content didn't change.
	 *
	 * @param filePath File to hash
	 * @return Composite state hash
	 */
	public static long computeFileStateHash(String filePath) {
		try {
			java.nio.file.Path path = java.nio.file.Paths.get(filePath);
			long fileModified = java.nio.file.Files.getLastModifiedTime(path).toMillis();

			// Check if file is open in editor with unsaved changes
			// If dirty (unsaved), hash actual document content to detect real changes
			String dirtyDocumentContent = null;
			try {
				org.eclipse.ui.IWorkbench workbench = org.eclipse.ui.PlatformUI.getWorkbench();
				if (workbench != null && !workbench.isClosing()) {
					for (org.eclipse.ui.IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
						for (org.eclipse.ui.IWorkbenchPage page : window.getPages()) {
							for (org.eclipse.ui.IEditorReference ref : page.getEditorReferences()) {
								org.eclipse.ui.IEditorPart editor = ref.getEditor(false);
								if (editor != null && editor.isDirty()) {
									try {
										String editorPath = editor.getEditorInput().getAdapter(org.eclipse.core.resources.IFile.class)
											.getLocation().toOSString();
										if (editorPath.equals(filePath)) {
											// Get document content from editor
											if (editor instanceof org.eclipse.ui.texteditor.ITextEditor) {
												org.eclipse.ui.texteditor.ITextEditor textEditor = (org.eclipse.ui.texteditor.ITextEditor) editor;
												org.eclipse.jface.text.IDocument doc = textEditor.getDocumentProvider().getDocument(editor.getEditorInput());
												if (doc != null) {
													dirtyDocumentContent = doc.get();
													break;
												}
											}
										}
									} catch (Exception e2) {
										// Skip if we can't get editor or document
									}
								}
							}
							if (dirtyDocumentContent != null) break;
						}
						if (dirtyDocumentContent != null) break;
					}
				}
			} catch (Exception e) {
				// If workbench check fails, just use file timestamp
				dirtyDocumentContent = null;
			}

			// If file has unsaved changes, hash actual document content
			// This ensures same content hashes to same value (no unnecessary rescans)
			if (dirtyDocumentContent != null) {
				return hashDocumentContent(dirtyDocumentContent);
			}

			return fileModified;
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error computing state hash: " + e.getMessage());
			return System.currentTimeMillis();
		}
	}

	/**
	 * Compute SHA-256 hash of document content.
	 * CRITICAL: Enables stable hashing of dirty files - same content always produces same hash.
	 *
	 * @param content Document text content
	 * @return Long hash value (first 8 bytes of SHA-256)
	 */
	private static long hashDocumentContent(String content) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(content.getBytes("UTF-8"));
			// Convert first 8 bytes to long
			long result = 0;
			for (int i = 0; i < 8; i++) {
				result = (result << 8) | (hash[i] & 0xFF);
			}
			return result;
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error hashing document content: " + e.getMessage());
			// Fallback to content length + hash code
			return ((long) content.length() << 32) | (content.hashCode() & 0xFFFFFFFFL);
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
