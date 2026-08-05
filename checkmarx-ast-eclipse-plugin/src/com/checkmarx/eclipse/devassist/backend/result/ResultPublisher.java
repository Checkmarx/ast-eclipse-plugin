package com.checkmarx.eclipse.devassist.backend.result;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry;
import com.checkmarx.eclipse.devassist.inspection.DevAssistInspectionMgr;
import com.checkmarx.eclipse.devassist.problems.ProblemDecorator;
import com.checkmarx.eclipse.devassist.problems.ProblemHelper;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;
import java.util.List;

/**
 * Publishes scan results to Checkmarx Findings Window and editor decorations.
 *
 * Responsibilities:
 * - Update custom Findings View with scan results
 * - Render editor decorations (gutter icons, underlines) for Findings Window issues
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
	 * @param file File that was scanned
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
	 * @param file File that was scanned
	 * @param scanIssues Issues to display
	 */
	private static void updateFindingsView(IFile file, List<ScanIssue> scanIssues) {
		try {
			if (scanIssues.isEmpty()) {
				return;
			}

			// Must run on UI thread
			org.eclipse.swt.widgets.Display display = PlatformUI.getWorkbench().getDisplay();
			if (display == null || display.isDisposed()) {
				return;
			}

			// JetBrains Pattern: Remove old engine results, then merge new results
			// This triggers the message bus pattern:
			// 1. removeScanIssuesByFileAndScanner() removes old results for THIS engine
			// 2. mergeScanIssues() stores new results in cache
			// 3. notifyListenersOfUpdate() publishes to all listeners
			// 4. CxFindingsView listener receives callback with getAllIssues()
			// 5. Listener calls refreshTree(allCachedResults)
			// 6. Tree shows merged results (no duplicates, no stale issues)
			// FIX: Use getLocation() (absolute path) to match cache key format used in RealTimeScanJob
			// ProblemHolderService cache is keyed with absolute paths from RealTimeScanJob.scanFile()
			// Must use same path format for cache lookups or removal will fail - causing duplicates
			String filePath = file.getLocation().toOSString();

			org.eclipse.core.resources.IProject project = file.getProject();
			if (project != null) {
				ProblemHolderService problemHolder =
					(ProblemHolderService) project.getSessionProperty(
						new org.eclipse.core.runtime.QualifiedName("com.checkmarx.eclipse.plugin", "problem-holder"));

				if (problemHolder != null) {
					// Get engine type from scan issues (all issues from same scan have same engine)
					String engineType = scanIssues.isEmpty() ? null :
						scanIssues.get(0).getScanEngine() != null ?
						scanIssues.get(0).getScanEngine().name() : null;

					// Step 1: Remove old results from THIS scanner engine
					if (engineType != null) {
						problemHolder.removeScanIssuesByFileAndScanner(engineType, filePath);
						
					}

					// Step 2: Add new results from THIS scanner engine
					problemHolder.mergeScanIssues(filePath, scanIssues);
					
				} else {
					
				}
			}

		} catch (Exception e) {
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
	 * @param file File that was scanned
	 * @param scanIssues Issues to process
	 */
	private static void createAndRenderDecorations(IFile file, List<ScanIssue> scanIssues) {
		try {
			if (scanIssues.isEmpty()) {
				return;
			}

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
						CxLogger.warning(LOG_TAG + " Required services not initialized (registry=" + (registry != null) +
							", stateHolder=" + (stateHolder != null) + ", problemHolder=" + (problemHolder != null) + ")");
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

					CxLogger.info(LOG_TAG + " Problem descriptors created via DevAssistInspectionMgr for " + scanIssues.size() + " issues");

				} catch (Exception e) {
					CxLogger.warning(LOG_TAG + " Error creating problem descriptors: " + e.getMessage());
					// Fallback to direct decoration
					try {
						ProblemDecorator.decorateEditor(file, scanIssues);
					} catch (Exception fallbackError) {
						CxLogger.error(LOG_TAG + " Fallback decoration also failed: " + fallbackError.getMessage(), fallbackError);
					}
				}
			});

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error: " + e.getMessage());
		}
	}

	/**
	 * Get the IDocument for a file, preferring the live editor's document (so unsaved
	 * edits are reflected) and falling back to reading the file's on-disk content.
	 *
	 * ScanIssueProcessor requires a non-null document to validate that an issue's line
	 * number is within range (getNumberOfLines()); without it every issue is rejected.
	 *
	 * @param file File to get the document for
	 * @return IDocument, or null if it could not be obtained
	 */
	private static org.eclipse.jface.text.IDocument getDocumentForFile(IFile file) {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			if (page != null) {
				org.eclipse.ui.IEditorPart editor = page.findEditor(new org.eclipse.ui.part.FileEditorInput(file));
				if (editor instanceof org.eclipse.ui.texteditor.ITextEditor) {
					org.eclipse.ui.texteditor.ITextEditor textEditor = (org.eclipse.ui.texteditor.ITextEditor) editor;
					org.eclipse.jface.text.IDocument doc = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
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
					if (page != null) break;
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
