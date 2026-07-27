package com.checkmarx.eclipse.devassist.ui.findings.realtime;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import java.util.HashMap;
import java.util.Map;

import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.problems.ProblemDecorator;

/**
 * Real-time editor listener for Checkmarx scanning.
 *
 * Equivalent to JetBrains' LocalInspectionTool integration — listens for editor
 * open/close events and registers document listeners for real-time scanning.
 *
 * When a text editor opens:
 * 1. Create a RealTimeScanJob for that file
 * 2. Register a CheckmarxDocumentListener on the document
 * 3. Every keystroke triggers the document listener
 * 4. Document listener reschedules the job (1-second debounce)
 * 5. When debounce expires, RealTimeScanJob.run() executes the scan
 *
 * When the editor closes:
 * - Dispose of the document listener and cancel the job
 */
public class CheckmarxEditorListener implements IPartListener2 {

	/**
	 * Map of documents to their associated listeners.
	 * Key: IDocument hash code (unique identifier for the document)
	 * Value: CheckmarxDocumentListener (for cleanup on editor close)
	 */
	private final Map<Integer, CheckmarxDocumentListener> activeListeners = new HashMap<>();

	/**
	 * Map of documents to their associated scan jobs.
	 * Key: IDocument hash code
	 * Value: RealTimeScanJob (for cleanup and tracking)
	 */
	private final Map<Integer, RealTimeScanJob> activeScanJobs = new HashMap<>();

	public CheckmarxEditorListener() {
		System.out.println("[REALTIME] ✓ CheckmarxEditorListener created");
	}

	/**
	 * Get the Eclipse log for this plugin.
	 */
	private ILog getLog() {
		return Platform.getLog(getClass());
	}

	/**
	 * Called when an editor part is opened.
	 * Register real-time scanning for this editor.
	 */
	@Override
	public void partOpened(IWorkbenchPartReference partRef) {
		try {
			Object part = partRef.getPart(false);
			if (part instanceof IEditorPart) {
				setupRealtimeScanning((IEditorPart) part);
			}
		} catch (Exception e) {
			System.err.println("[REALTIME] Error in partOpened: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Called when an editor is activated.
	 * Also install scanning if not already done.
	 */
	@Override
	public void partActivated(IWorkbenchPartReference partRef) {
		try {
			Object part = partRef.getPart(false);
			if (part instanceof IEditorPart) {
				setupRealtimeScanning((IEditorPart) part);
			}
		} catch (Exception e) {
			System.err.println("[REALTIME] Error in partActivated: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Called when an editor is closed.
	 * Clean up document listeners and cancel pending scan jobs.
	 */
	@Override
	public void partClosed(IWorkbenchPartReference partRef) {
		try {
			Object part = partRef.getPart(false);
			if (part instanceof IEditorPart) {
				cleanupRealtimeScanning((IEditorPart) part);
			}
		} catch (Exception e) {
			System.err.println("[REALTIME] Error in partClosed: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Setup real-time scanning on the given editor.
	 *
	 * @param editor the editor part (should be a text editor)
	 */
	private void setupRealtimeScanning(IEditorPart editor) {
		if (editor == null) {
			return;
		}

		// Get the document from the editor
		IDocument document = getDocumentFromEditor(editor);
		if (document == null) {
			// Not a text editor or no document available
			return;
		}

		// Use document hash code as a unique identifier
		int documentId = document.hashCode();

		// Check if we've already set up scanning for this document
		if (activeListeners.containsKey(documentId)) {
			System.out.println("[REALTIME] Document listener already registered");
			return;
		}

		// Get file name for logging
		String fileName = extractFileNameFromEditor(editor);
		System.out.println("[REALTIME] Setting up real-time scanning for: " + fileName);

		// Log to Eclipse Error Log
		String message = "User opened the file: " + fileName;
		getLog().log(new Status(Status.INFO, "com.checkmarx.eclipse.plugin", message));

		// Create a scan job for this file
		// Note: We extract the IFile from the editor if possible, otherwise use null
		// (The actual file can be obtained from the editor input)
		org.eclipse.core.resources.IFile file = extractFileFromEditor(editor);
		RealTimeScanJob scanJob = new RealTimeScanJob(file, fileName);

		// Create a document listener that will reschedule the job on every keystroke
		CheckmarxDocumentListener docListener = new CheckmarxDocumentListener(fileName, scanJob);

		// Register the document listener
		try {
			document.addDocumentListener(docListener);

			// Store the listener and job for later cleanup
			activeListeners.put(documentId, docListener);
			activeScanJobs.put(documentId, scanJob);

			System.out.println("[REALTIME] ✓ Document listener registered for: " + fileName);

			// **CRITICAL FIX: Apply cached decorations if findings exist for this file**
			// JetBrains pattern: when editor opens, apply cached decorations immediately
			// This fixes the issue where decorations don't appear if editor wasn't open during scan
			applyCachedDecorationsForFile(file, document);

		} catch (Exception e) {
			System.err.println("[REALTIME] ✗ Error registering document listener: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Cleanup real-time scanning on the given editor.
	 *
	 * @param editor the editor part being closed
	 */
	private void cleanupRealtimeScanning(IEditorPart editor) {
		if (editor == null) {
			return;
		}

		// Get the document from the editor
		IDocument document = getDocumentFromEditor(editor);
		if (document == null) {
			return;
		}

		int documentId = document.hashCode();

		// Remove the document listener
		CheckmarxDocumentListener listener = activeListeners.remove(documentId);
		if (listener != null) {
			try {
				document.removeDocumentListener(listener);
				listener.dispose();
				System.out.println("[REALTIME] ✓ Document listener removed for: " + listener.getFileName());
			} catch (Exception e) {
				System.err.println("[REALTIME] Error removing document listener: " + e.getMessage());
			}
		}

		// Cancel the scan job
		RealTimeScanJob scanJob = activeScanJobs.remove(documentId);
		if (scanJob != null) {
			scanJob.cancel();
			System.out.println("[REALTIME] ✓ Scan job cancelled for: " + scanJob.getFileName());
		}
	}

	/**
	 * Extract the IDocument from an editor.
	 * Handles both standard ITextEditor and editors like MavenPomEditor.
	 *
	 * @param editor the editor part
	 * @return the document, or null if not available
	 */
	private IDocument getDocumentFromEditor(IEditorPart editor) {
		if (editor == null) {
			return null;
		}

		// Try method 1: Direct ITextEditor instance
		if (editor instanceof ITextEditor) {
			ITextEditor textEditor = (ITextEditor) editor;
			try {
				return textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
			} catch (Exception e) {
				// Fall through to try adapter pattern
			}
		}

		// Try method 2: Adapter pattern (for MavenPomEditor and other non-ITextEditor editors)
		try {
			ITextEditor textEditor = editor.getAdapter(ITextEditor.class);
			if (textEditor != null) {
				return textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
			}
		} catch (Exception e) {
			// Fall through to next method
		}

		// Try method 3: Direct IDocument adapter (some editors provide this)
		try {
			IDocument document = editor.getAdapter(IDocument.class);
			if (document != null) {
				return document;
			}
		} catch (Exception e) {
			// Fall through
		}

		return null;
	}

	/**
	 * Extract the file name from an editor for logging.
	 *
	 * @param editor the editor part
	 * @return the file name, or "unknown" if not available
	 */
	private String extractFileNameFromEditor(IEditorPart editor) {
		try {
			return editor.getEditorInput().getName();
		} catch (Exception e) {
			return "unknown";
		}
	}

	/**
	 * Extract the IFile from an editor (may return null for non-workspace files).
	 *
	 * @param editor the editor part
	 * @return the IFile, or null if not available
	 */
	private org.eclipse.core.resources.IFile extractFileFromEditor(IEditorPart editor) {
		try {
			if (editor.getEditorInput() instanceof org.eclipse.ui.part.FileEditorInput) {
				org.eclipse.ui.part.FileEditorInput fileInput =
						(org.eclipse.ui.part.FileEditorInput) editor.getEditorInput();
				return fileInput.getFile();
			}
		} catch (Exception e) {
			// Ignore exceptions; file extraction is optional
		}
		return null;
	}

	/**
	 * Apply cached decorations (gutter icons, underlines) when editor opens.
	 *
	 * JetBrains pattern: when an editor opens, check if there are cached findings
	 * and apply decorations immediately. This ensures decorations appear even if
	 * the editor wasn't open when the scan completed.
	 *
	 * @param file the Eclipse IFile being opened
	 * @param document the document for the file
	 */
	private void applyCachedDecorationsForFile(org.eclipse.core.resources.IFile file, IDocument document) {
		if (file == null || document == null) {
			return;
		}

		try {
			String filePath = file.getLocation().toOSString();
			org.eclipse.core.resources.IProject project = file.getProject();

			if (project == null) {
				return;
			}

			// Get cached findings for this file
			ProblemHolderService problemHolder =
				(ProblemHolderService) project.getSessionProperty(
					new org.eclipse.core.runtime.QualifiedName("com.checkmarx.eclipse.plugin", "problem-holder"));

			if (problemHolder == null) {
				return;
			}

			java.util.List<com.checkmarx.eclipse.devassist.model.ScanIssue> cachedIssues =
				problemHolder.getScanIssuesByFile(filePath);

			if (cachedIssues == null || cachedIssues.isEmpty()) {
				System.out.println("[REALTIME] No cached findings for: " + file.getName());
				return;
			}

			// Apply decorations for cached findings
			System.out.println("[REALTIME] ✓ Applying " + cachedIssues.size() + " cached decorations for: " + file.getName());
			ProblemDecorator.decorateEditor(file, cachedIssues);

		} catch (Exception e) {
			System.err.println("[REALTIME] Error applying cached decorations: " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Implement other IPartListener2 methods (not used for real-time scanning)

	@Override
	public void partBroughtToTop(IWorkbenchPartReference partRef) {}

	@Override
	public void partDeactivated(IWorkbenchPartReference partRef) {}

	@Override
	public void partHidden(IWorkbenchPartReference partRef) {}

	@Override
	public void partVisible(IWorkbenchPartReference partRef) {}

	@Override
	public void partInputChanged(IWorkbenchPartReference partRef) {}

	/**
	 * Get the number of active listeners (for testing/debugging).
	 */
	public int getActiveListenerCount() {
		return activeListeners.size();
	}

	/**
	 * Get the number of active scan jobs (for testing/debugging).
	 */
	public int getActiveScanJobCount() {
		return activeScanJobs.size();
	}
}
