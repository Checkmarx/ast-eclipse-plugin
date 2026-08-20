package com.checkmarx.eclipse.devassist.backend.result;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry;
import com.checkmarx.eclipse.devassist.inspection.DevAssistInspectionMgr;
import com.checkmarx.eclipse.devassist.problems.ProblemDecorator;
import com.checkmarx.eclipse.devassist.problems.ProblemHelper;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.common.utils.CxLogger;
import java.util.List;

/**
 * Publishes scan results to Checkmarx Findings Window and editor decorations.
 *
 * Responsibilities:
 * - Update custom Findings View with scan results
 * - Render editor decorations (gutter icons, underlines) for Findings Window
 * issues
 * - NO integration with Eclipse native Problems View
 *
 * This connects scan results directly to the custom Findings Window.
 */
public class ResultPublisher {

	private static final String LOG_TAG = "[RESULT-PUBLISHER]";

	/**
	 * Publish scan results to Findings View and editor decorations.
	 *
	 * Orchestrates the complete problem descriptor creation and publication flow:
	 * 1. Update Findings View cache with scan results
	 * 2. Create problem descriptors via DevAssistInspectionMgr
	 * 3. Render editor decorations (gutter icons, underlines)
	 *
	 * Mirrors JetBrains pattern where scan results are stored in cache,
	 * which then publishes a message to notify all interested views.
	 *
	 * @param file       File that was scanned
	 * @param scanIssues Issues found by scanners
	 */
	public static void publishResults(IFile file, List<ScanIssue> scanIssues) {
		if (file == null || scanIssues == null) {
			return;
		}
		try {
			// Step 1: Update Findings View (try to display immediately if view is open)

			updateFindingsView(file, scanIssues);

			// Step 2: Create problem descriptors via DevAssistInspectionMgr

			createAndRenderDecorations(file, scanIssues);

		} catch (Exception e) {
			System.err.println(LOG_TAG + " [ERROR] " + e.getMessage());
			e.printStackTrace();
			CxLogger.error(LOG_TAG + " Error publishing results: " + e.getMessage(), e);
		}
	}

	/**
	 * Update Findings View with scan results.
	 *
	 * ✅ CRITICAL: `scanIssues` here always represents the COMPLETE, current set
	 * of issues for this file across every applicable/enabled engine - because
	 * {@link com.checkmarx.eclipse.devassist.common.ScanManager#scanFileWithOutcome}
	 * runs every applicable scanner for the file in a single pass and this method
	 * is only invoked by callers that just performed (or confirmed) such a real
	 * scan cycle (see {@link RealTimeScanJob}, which gates this call on
	 * {@code ScanOutcome.isScanned()}).
	 *
	 * Because of that, this is a full REPLACE of the file's cached issues, not a
	 * per-engine merge/remove. This correctly handles the case where a vulnerable
	 * line is deleted (scanIssues becomes empty -> cache is fully cleared for
	 * this file) without needing to infer which engine produced which result.
	 *
	 * @param file       File that was scanned
	 * @param scanIssues Complete, current issue list for this file (may be empty)
	 */
	private static void updateFindingsView(IFile file, List<ScanIssue> scanIssues) {
		try {
			// Must run on UI thread
			org.eclipse.swt.widgets.Display display = PlatformUI.getWorkbench().getDisplay();
			if (display == null || display.isDisposed()) {
				return;
			}

			// FIX: Use getLocation() (absolute path) to match cache key format used in
			// RealTimeScanJob
			// ProblemHolderService cache is keyed with absolute paths from
			// RealTimeScanJob.scanFile()
			// Must use same path format for cache lookups or removal will fail - causing
			// duplicates
			String filePath = file.getLocation().toOSString();

			org.eclipse.core.resources.IProject project = file.getProject();
			if (project != null) {
				ProblemHolderService problemHolder = (ProblemHolderService) project.getSessionProperty(
						new org.eclipse.core.runtime.QualifiedName("com.checkmarx.eclipse.plugin", "problem-holder"));

				if (problemHolder != null) {
					// Full replace: this cycle's scanIssues list IS the complete truth for
					// this file. If it's empty, every previously-cached issue for this
					// file (from any engine) is correctly dropped. This also publishes
					// the ISSUES_UPDATED_TOPIC event that CxFindingsView listens to.
					problemHolder.addScanIssues(filePath, scanIssues);
					CxLogger.info(LOG_TAG + " Updated cache for " + filePath + " with " + scanIssues.size()
							+ " issues");

				} else {
					CxLogger.warning(LOG_TAG + " ProblemHolderService not initialized for project");
				}
			}

		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error updating findings view: " + e.getMessage(), e);
			e.printStackTrace();
		}
	}

	/**
	 * Create problem descriptors and render editor decorations.
	 *
	 * Orchestrates:
	 * 1. Get registry and state holder from project session
	 * 2. Build ProblemHelper.Builder with file context and scan issues
	 * 3. Call DevAssistInspectionMgr to create problem descriptors
	 * 4. Render gutter icons and underlines using descriptors
	 *
	 * ✅ CRITICAL: Always processes results, even if empty.
	 * When scan returns 0 issues, we MUST clear old decorations/annotations.
	 *
	 * @param file       File that was scanned
	 * @param scanIssues Issues to process (may be empty)
	 */
	private static void createAndRenderDecorations(IFile file, List<ScanIssue> scanIssues) {
		try {
			org.eclipse.swt.widgets.Display display = PlatformUI.getWorkbench().getDisplay();
			if (display == null || display.isDisposed()) {
				return;
			}

			org.eclipse.core.resources.IProject project = file.getProject();
			if (project == null) {
				CxLogger.warning(LOG_TAG + " Project not available for file: " + file.getName());
				return;
			}

			display.asyncExec(() -> {
				try {
					// Get registry and state holder from session properties
					ScannerRegistry registry = (ScannerRegistry) project.getSessionProperty(
							new QualifiedName("com.checkmarx.eclipse.plugin", "scanner-registry"));
					DevAssistScanStateHolder stateHolder = (DevAssistScanStateHolder) project.getSessionProperty(
							new QualifiedName("com.checkmarx.eclipse.plugin", "state-holder"));
					ProblemHolderService problemHolder = (ProblemHolderService) project.getSessionProperty(
							new QualifiedName("com.checkmarx.eclipse.plugin", "problem-holder"));

					if (registry == null || stateHolder == null || problemHolder == null) {
						CxLogger.warning(
								LOG_TAG + " Required services not initialized (registry=" + (registry != null) +
										", stateHolder=" + (stateHolder != null) + ", problemHolder="
										+ (problemHolder != null) + ")");
						// Fallback to direct decoration if services not available
						ProblemDecorator.decorateEditor(file, scanIssues);
						return;
					}

					// Build ProblemHelper.Builder with file context and scan issues
					String filePath = file.getLocation().toOSString();
					org.eclipse.jface.text.IDocument document = getDocumentForFile(file);
					ProblemHelper.Builder builder = ProblemHelper.builder(file, project)
							.filePath(filePath)
							.document(document)
							.scanIssueList(scanIssues)
							.problemHolderService(problemHolder)
							.problemDecorator(new ProblemDecorator());

					// Create problem descriptors via DevAssistInspectionMgr
					DevAssistInspectionMgr mgr = new DevAssistInspectionMgr(registry, stateHolder);
					mgr.startScanAndCreateProblemDescriptors(builder);

					CxLogger.info(LOG_TAG + " Problem descriptors created via DevAssistInspectionMgr for "
							+ scanIssues.size() + " issues");

				} catch (Exception e) {
					CxLogger.warning(LOG_TAG + " Error creating problem descriptors: " + e.getMessage());
					// Fallback to direct decoration
					try {
						ProblemDecorator.decorateEditor(file, scanIssues);
					} catch (Exception fallbackError) {
						CxLogger.error(LOG_TAG + " Fallback decoration also failed: " + fallbackError.getMessage(),
								fallbackError);
					}
				}
			});

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error: " + e.getMessage());
		}
	}

	/**
	 * Get the IDocument for a file, preferring the live editor's document (so
	 * unsaved
	 * edits are reflected) and falling back to reading the file's on-disk content.
	 *
	 * ScanIssueProcessor requires a non-null document to validate that an issue's
	 * line
	 * number is within range (getNumberOfLines()); without it every issue is
	 * rejected.
	 *
	 * @param file File to get the document for
	 * @return IDocument, or null if it could not be obtained
	 */
	private static org.eclipse.jface.text.IDocument getDocumentForFile(IFile file) {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			if (page != null) {
				org.eclipse.ui.IEditorPart editor = page.findEditor(new org.eclipse.ui.part.FileEditorInput(file));
				if (editor instanceof ITextEditor) {
					ITextEditor textEditor = (ITextEditor) editor;
					IDocument doc = textEditor.getDocumentProvider()
							.getDocument(textEditor.getEditorInput());
					if (doc != null) {
						return doc;
					}
				}
			}
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Could not get document from editor: " + e.getMessage());
		}

		try {
			org.eclipse.jface.text.Document doc = new org.eclipse.jface.text.Document();
			doc.set(new String(file.getContents().readAllBytes()));
			return doc;
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Could not create document from file: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Find the open Findings View.
	 *
	 * @return CxFindingsView instance if open, null otherwise
	 */
	private static com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView findOpenFindingsView() {
		try {
			IWorkbench workbench = PlatformUI.getWorkbench();
			if (workbench == null) {
				return null;
			}

			IWorkbenchPage page = null;
			try {
				page = workbench.getActiveWorkbenchWindow().getActivePage();
			} catch (NullPointerException e) {
				for (var window : workbench.getWorkbenchWindows()) {
					page = window.getActivePage();
					if (page != null)
						break;
				}
			}

			if (page == null) {
				return null;
			}

			return (com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView) page
					.findView(com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView.ID);

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error finding Findings View: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Clear results for a file.
	 *
	 * @param file File to clear
	 */
	public static void clearResults(IFile file) {
		try {
			CxLogger.info(LOG_TAG + " Clearing results for: " + file.getName());
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error clearing results: " + e.getMessage());
		}
	}
}
