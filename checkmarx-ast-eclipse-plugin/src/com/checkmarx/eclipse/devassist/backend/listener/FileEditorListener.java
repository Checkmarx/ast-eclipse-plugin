package com.checkmarx.eclipse.devassist.backend.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder;
import com.checkmarx.eclipse.devassist.backend.ProblemHolderService;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry;
import com.checkmarx.eclipse.devassist.backend.scanner.ScanManager;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Listens for file editor events (open, modify).
 *
 * Responsibilities:
 * - Detect when files are opened in editors
 * - Detect when files are modified in editors
 * - Schedule debounced scans on file modification
 * - Restore previous scan results from cache
 * - Render gutter icons and annotations
 *
 * Implements two listener interfaces:
 * - IPartListener2: Detects editor part open/close events
 * - IDocumentListener: Detects document content changes
 *
 * This is a project-scoped listener. Each open project gets its own
 * FileEditorListener instance.
 */
public class FileEditorListener implements IPartListener2, IDocumentListener {

	private static final String LOG_TAG = "[FILE-EDITOR]";
	private static final int DEBOUNCE_DELAY_MS = 1000; // 1 second

	private final IProject project;
	private final Map<String, TimerTask> pendingScans = new HashMap<>();
	private final Timer debounceTimer = new Timer("FileEditorDebounce-" +
		System.currentTimeMillis(), true);

	// Track registered document listeners to avoid duplicate registration
	private final Map<IDocument, IDocumentListener> registeredDocuments = new HashMap<>();

	/**
	 * Create a file editor listener for a project.
	 *
	 * @param project Project to listen to
	 */
	public FileEditorListener(IProject project) {
		this.project = project;
	}

	/**
	 * Register this listener with Eclipse workbench.
	 *
	 * Called when project opens (from ProjectLifecycleListener).
	 */
	public void register() {
		CxLogger.info(LOG_TAG + " Registering file editor listener for: " +
			project.getName());

		try {
			IWorkbench workbench = PlatformUI.getWorkbench();
			workbench.getActiveWorkbenchWindow().getPartService().addPartListener(this);
			CxLogger.info(LOG_TAG + " ✓ Part listener registered");
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error registering listener: " + e.getMessage());
		}
	}

	/**
	 * Unregister this listener from Eclipse workbench.
	 *
	 * Called when project closes (from ProjectLifecycleListener).
	 */
	public void unregister() {
		CxLogger.info(LOG_TAG + " Unregistering file editor listener for: " +
			project.getName());

		try {
			// Cancel all pending scans
			for (TimerTask task : pendingScans.values()) {
				task.cancel();
			}
			pendingScans.clear();
			debounceTimer.cancel();

			// Unregister from part service
			IWorkbench workbench = PlatformUI.getWorkbench();
			workbench.getActiveWorkbenchWindow().getPartService().removePartListener(this);

			CxLogger.info(LOG_TAG + " ✓ Unregistered");
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error unregistering listener: " +
				e.getMessage());
		}
	}

	/**
	 * Called when an editor part is opened.
	 *
	 * Restores previous scan results from cache and renders gutter icons.
	 *
	 * @param partRef The editor part reference
	 */
	@Override
	public void partOpened(IWorkbenchPartReference partRef) {
		try {
			IEditorPart editor = (IEditorPart) partRef.getPart(false);
			if (!(editor instanceof ITextEditor)) {
				return;
			}

			IFile file = extractFileFromEditor((ITextEditor) editor);
			if (file == null || !file.getProject().equals(project)) {
				return;
			}

			String filePath = file.getFullPath().toOSString();
			CxLogger.info(LOG_TAG + " ✓ File opened: " + filePath);

			// Restore previous scan results from cache
			restorePreviousScanResults(file);

			// Register document listener for this editor
			ITextEditor textEditor = (ITextEditor) editor;
			registerDocumentListener(textEditor);

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error handling partOpened: " + e.getMessage());
		}
	}

	/**
	 * Called when an editor part is closed.
	 *
	 * Unregisters the document listener.
	 *
	 * @param partRef The editor part reference
	 */
	@Override
	public void partClosed(IWorkbenchPartReference partRef) {
		try {
			IEditorPart editor = (IEditorPart) partRef.getPart(false);
			if (!(editor instanceof ITextEditor)) {
				return;
			}

			IFile file = extractFileFromEditor((ITextEditor) editor);
			if (file == null) {
				return;
			}

			String filePath = file.getFullPath().toOSString();
			CxLogger.info(LOG_TAG + " ✓ File closed: " + filePath);

			// Unregister document listener
			unregisterDocumentListener((ITextEditor) editor);

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error handling partClosed: " + e.getMessage());
		}
	}

	/**
	 * Called when document content changes.
	 *
	 * Schedules a debounced scan (delays actual scan by 1000ms).
	 * If another change comes in within 1000ms, the previous scan is cancelled
	 * and rescheduled.
	 *
	 * @param event Document event
	 */
	@Override
	public void documentChanged(DocumentEvent event) {
		try {
			IDocument document = event.getDocument();
			IFile file = getFileFromDocument(document);

			if (file == null || !file.getProject().equals(project)) {
				return;
			}

			String filePath = file.getFullPath().toOSString();
			CxLogger.info(LOG_TAG + " Document modified: " + filePath);

			// Schedule debounced scan
			scheduleDebouncedScan(file);

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error handling documentChanged: " +
				e.getMessage());
		}
	}

	/**
	 * Schedule a scan with debounce (1000ms delay).
	 *
	 * If a scan is already pending for this file, cancel it and reschedule.
	 *
	 * @param file File to scan
	 */
	private void scheduleDebouncedScan(IFile file) {
		String filePath = file.getFullPath().toOSString();

		// Cancel previous pending scan for this file
		TimerTask previousTask = pendingScans.get(filePath);
		if (previousTask != null) {
			previousTask.cancel();
			CxLogger.info(LOG_TAG + " ✓ Cancelled previous pending scan: " + filePath);
		}

		// Schedule new scan with debounce delay
		TimerTask scanTask = new TimerTask() {
			@Override
			public void run() {
				executeBackgroundScan(file);
			}
		};

		debounceTimer.schedule(scanTask, DEBOUNCE_DELAY_MS);
		pendingScans.put(filePath, scanTask);

		CxLogger.info(LOG_TAG + " Scheduled scan (debounce=" + DEBOUNCE_DELAY_MS +
			"ms): " + filePath);
	}

	/**
	 * Execute scan in background thread (after debounce delay).
	 *
	 * @param file File to scan
	 */
	private void executeBackgroundScan(IFile file) {
		String filePath = file.getFullPath().toOSString();

		new Thread(() -> {
			try {
				CxLogger.info(LOG_TAG + " Executing scan (after debounce): " + filePath);

				// Get per-project backend services
				ScannerRegistry registry = (ScannerRegistry) project.getSessionProperty(
					ScannerRegistry.REGISTRY_KEY
				);
				DevAssistScanStateHolder stateHolder =
					(DevAssistScanStateHolder) project.getSessionProperty(
						DevAssistScanStateHolder.class.getName()
					);
				ProblemHolderService problemHolder =
					(ProblemHolderService) project.getSessionProperty(
						ProblemHolderService.class.getName()
					);

				if (registry == null || stateHolder == null || problemHolder == null) {
					CxLogger.warning(LOG_TAG + " Backend services not initialized");
					return;
				}

				// Create scan manager
				ScanManager scanManager = new ScanManager(registry, stateHolder);

				// Perform scan
				java.util.List<ScanIssue> issues = scanManager.scanFile(filePath);

				// Update problem holder with results
				problemHolder.addScanIssues(filePath, issues);

				CxLogger.info(LOG_TAG + " ✓ Scan complete, found " + issues.size() +
					" issues: " + filePath);

				// TODO: Phase 4 - Update UI with findings
				// - Update Problems View markers
				// - Render gutter icons in editor
				// - Update Findings View

			} catch (Exception e) {
				CxLogger.error(LOG_TAG + " Scan failed: " + e.getMessage(), e);
			} finally {
				// Remove from pending scans map
				pendingScans.remove(filePath);
			}
		}, "CxScanner-" + System.currentTimeMillis()).start();
	}

	/**
	 * Restore previous scan results from cache.
	 *
	 * When a file is opened, check if we have cached results from a previous scan.
	 * If yes, render gutter icons to show previous issues.
	 *
	 * @param file File that was opened
	 */
	private void restorePreviousScanResults(IFile file) {
		try {
			String filePath = file.getFullPath().toOSString();

			ProblemHolderService problemHolder =
				(ProblemHolderService) project.getSessionProperty(
					ProblemHolderService.class.getName()
				);

			if (problemHolder == null) {
				return;
			}

			java.util.List<ScanIssue> cachedIssues = problemHolder.getScanIssuesByFile(
				filePath
			);

			if (!cachedIssues.isEmpty()) {
				CxLogger.info(LOG_TAG + " ✓ Restored " + cachedIssues.size() +
					" cached issues for: " + filePath);

				// TODO: Phase 4 - Render gutter icons from cached issues
				// - Get ITextEditor for file
				// - Iterate cached issues
				// - Add annotations/markers for each issue
				// - Use FindingsEditorOverlay to render

			} else {
				CxLogger.info(LOG_TAG + " No cached issues found: " + filePath);
			}
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error restoring cached results: " +
				e.getMessage());
		}
	}

	/**
	 * Register document listener for an editor.
	 *
	 * Adds this listener to the editor's document so we're notified
	 * of content changes.
	 *
	 * @param editor Text editor
	 */
	private void registerDocumentListener(ITextEditor editor) {
		try {
			IDocument document = editor.getDocumentProvider().getDocument(
				editor.getEditorInput()
			);

			if (document == null) {
				return;
			}

			// Avoid duplicate registration
			if (registeredDocuments.containsKey(document)) {
				return;
			}

			document.addDocumentListener(this);
			registeredDocuments.put(document, this);

			IFile file = extractFileFromEditor(editor);
			String filePath = file != null ? file.getFullPath().toOSString() :
				"unknown";

			CxLogger.info(LOG_TAG + " ✓ Document listener registered: " + filePath);

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error registering document listener: " +
				e.getMessage());
		}
	}

	/**
	 * Unregister document listener for an editor.
	 *
	 * @param editor Text editor
	 */
	private void unregisterDocumentListener(ITextEditor editor) {
		try {
			IDocument document = editor.getDocumentProvider().getDocument(
				editor.getEditorInput()
			);

			if (document == null) {
				return;
			}

			if (registeredDocuments.containsKey(document)) {
				document.removeDocumentListener(this);
				registeredDocuments.remove(document);

				CxLogger.info(LOG_TAG + " ✓ Document listener unregistered");
			}
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error unregistering document listener: " +
				e.getMessage());
		}
	}

	/**
	 * Extract file from text editor.
	 *
	 * @param editor Text editor
	 * @return IFile or null
	 */
	private IFile extractFileFromEditor(ITextEditor editor) {
		try {
			Object input = editor.getEditorInput();
			if (input instanceof org.eclipse.ui.IFileEditorInput) {
				return ((org.eclipse.ui.IFileEditorInput) input).getFile();
			}
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error extracting file from editor: " +
				e.getMessage());
		}
		return null;
	}

	/**
	 * Get file associated with a document.
	 *
	 * @param document Text document
	 * @return IFile or null
	 */
	private IFile getFileFromDocument(IDocument document) {
		try {
			// Try to find the file by searching open editors
			IWorkbench workbench = PlatformUI.getWorkbench();
			var editors = workbench.getActiveWorkbenchWindow().getActivePage()
				.getEditors();

			for (IEditorPart editor : editors) {
				if (editor instanceof ITextEditor) {
					ITextEditor textEditor = (ITextEditor) editor;
					if (document.equals(textEditor.getDocumentProvider()
						.getDocument(textEditor.getEditorInput()))) {
						return extractFileFromEditor(textEditor);
					}
				}
			}
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error getting file from document: " +
				e.getMessage());
		}
		return null;
	}

	// IPartListener2 stub methods (not used)
	@Override
	public void partActivated(IWorkbenchPartReference partRef) {
		// Intentionally empty
	}

	@Override
	public void partBroughtToTop(IWorkbenchPartReference partRef) {
		// Intentionally empty
	}

	@Override
	public void partDeactivated(IWorkbenchPartReference partRef) {
		// Intentionally empty
	}

	@Override
	public void partHidden(IWorkbenchPartReference partRef) {
		// Intentionally empty
	}

	@Override
	public void partVisible(IWorkbenchPartReference partRef) {
		// Intentionally empty
	}

	@Override
	public void partInputChanged(IWorkbenchPartReference partRef) {
		// Intentionally empty
	}

	// IDocumentListener stub method (not used)
	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
		// Intentionally empty
	}

	/**
	 * Get statistics for debugging.
	 *
	 * @return Summary string
	 */
	public String getStatistics() {
		return "Project: " + project.getName() +
			", Registered documents: " + registeredDocuments.size() +
			", Pending scans: " + pendingScans.size();
	}
}
