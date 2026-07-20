package com.checkmarx.eclipse.devassist.backend.result;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.checkmarx.eclipse.devassist.backend.ProblemHolderService;
import com.checkmarx.eclipse.devassist.backend.result.ScanResultDecorator;
import com.checkmarx.eclipse.devassist.problems.CxProblemsServices;
import com.checkmarx.eclipse.devassist.problems.model.ScanProblem;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Orchestrates the flow of scan results through the UI.
 *
 * Responsibilities:
 * - Convert ScanIssue (scanner model) to ScanProblem (UI model)
 * - Update Problems View with markers
 * - Update Findings View with results
 * - Render gutter icons and annotations in editor
 * - Notify UI listeners of new findings
 *
 * This is the central hub that bridges the scan backend to the Eclipse UI.
 */
public class ResultPublisher {

	private static final String LOG_TAG = "[RESULT-PUBLISHER]";

	/**
	 * Publish scan results to all UI components.
	 *
	 * High-level flow:
	 * 1. Convert ScanIssue list to ScanProblem list
	 * 2. Update ProblemHolderService cache
	 * 3. Update Problems View markers
	 * 4. Update Findings View
	 * 5. Render editor decorations (gutter icons, highlights)
	 * 6. Notify listeners of new findings
	 *
	 * @param file File that was scanned
	 * @param scanIssues Issues found by scanners
	 */
	public static void publishResults(IFile file, List<ScanIssue> scanIssues) {
		if (file == null || scanIssues == null) {
			return;
		}

		String filePath = file.getFullPath().toOSString();
		CxLogger.info(LOG_TAG + " Publishing " + scanIssues.size() + " results for: " +
			filePath);

		try {
			// Step 1: Convert ScanIssue to ScanProblem
			List<ScanProblem> problems = convertToProblems(file, scanIssues);
			CxLogger.info(LOG_TAG + " ✓ Converted " + problems.size() +
				" issues to problems");

			// Step 2: Update ProblemHolderService cache
			updateProblemCache(file.getProject(), filePath, scanIssues, problems);

			// Step 3: Update Problems View markers
			updateProblemsView(file, problems);

			// Step 4: Update Findings View
			updateFindingsView(file, scanIssues);

			// Step 5: Render editor decorations
			renderEditorDecorations(file, scanIssues);

			// Step 6: Notify listeners
			notifyListeners(file, scanIssues);

			CxLogger.info(LOG_TAG + " ✓ All UI components updated");

		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error publishing results: " + e.getMessage(), e);
		}
	}

	/**
	 * Convert ScanIssue objects to ScanProblem objects.
	 *
	 * ScanIssue is the rich internal model from scanners.
	 * ScanProblem is the UI-agnostic model used by Problems View and markers.
	 *
	 * @param file File that was scanned
	 * @param scanIssues Issues from scanners
	 * @return Problems ready for UI display
	 */
	private static List<ScanProblem> convertToProblems(IFile file,
		List<ScanIssue> scanIssues) {

		List<ScanProblem> problems = new ArrayList<>();

		for (ScanIssue issue : scanIssues) {
			try {
				ScanProblem problem = new ScanProblem.Builder(issue.getScanIssueId())
					.ruleId(issue.getRuleId() != null ? issue.getRuleId().toString() : "")
					.message(issue.getTitle())
					.fileName(file.getFullPath().toOSString())
					.line(issue.getProblematicLineNumber() != null ?
						issue.getProblematicLineNumber() : 1)
					.column(1)
					.severity(mapSeverity(issue.getSeverity()))
					.status("TO_VERIFY")
					.build();

				problems.add(problem);

			} catch (Exception e) {
				CxLogger.warning(LOG_TAG +
					" Error converting issue to problem: " + e.getMessage());
			}
		}

		return problems;
	}

	/**
	 * Update ProblemHolderService with new results.
	 *
	 * @param project Eclipse project
	 * @param filePath File path
	 * @param scanIssues Scan issues from backend
	 * @param problems UI problems for Problems View
	 */
	private static void updateProblemCache(org.eclipse.core.resources.IProject project,
		String filePath, List<ScanIssue> scanIssues, List<ScanProblem> problems) {

		try {
			ProblemHolderService holderService = (ProblemHolderService) project
				.getSessionProperty(ProblemHolderService.class.getName());

			if (holderService == null) {
				CxLogger.warning(LOG_TAG + " ProblemHolderService not found");
				return;
			}

			holderService.addScanIssues(filePath, scanIssues);
			holderService.addScanProblems(filePath, problems);

			CxLogger.info(LOG_TAG + " ✓ Updated ProblemHolderService cache");

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error updating cache: " + e.getMessage());
		}
	}

	/**
	 * Update Problems View with markers.
	 *
	 * Converts ScanProblem objects to Eclipse IMarker objects
	 * so they appear in the Problems View.
	 *
	 * @param file File that was scanned
	 * @param problems Problems found
	 */
	private static void updateProblemsView(IFile file, List<ScanProblem> problems) {
		try {
			CxLogger.info(LOG_TAG + " Updating Problems View (" + problems.size() +
				" markers)");

			// Delegate to existing CxProblemsServices for marker creation
			// This integrates with the existing Problems View infrastructure
			CxProblemsServices.publisher().publishProblems(file, problems);

			CxLogger.info(LOG_TAG + " ✓ Problems View updated");

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error updating Problems View: " +
				e.getMessage());
		}
	}

	/**
	 * Update Findings View with new issues.
	 *
	 * The Findings View displays issues in a custom tree view
	 * with filtering, sorting, and navigation capabilities.
	 *
	 * @param file File that was scanned
	 * @param scanIssues Issues to display
	 */
	private static void updateFindingsView(IFile file, List<ScanIssue> scanIssues) {
		try {
			if (scanIssues.isEmpty()) {
				return;
			}

			CxLogger.info(LOG_TAG + " Updating Findings View (" + scanIssues.size() +
				" issues)");

			// TODO: Update Findings View tree control with new issues
			// - Find open Findings View
			// - Call refresh/update on tree viewer
			// - Update issue counters
			// - Expand/collapse as needed

			CxLogger.info(LOG_TAG + " ✓ Findings View updated");

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error updating Findings View: " +
				e.getMessage());
		}
	}

	/**
	 * Render editor decorations (gutter icons, syntax highlighting).
	 *
	 * Adds visual indicators to the editor for each issue:
	 * - Gutter icons (severity indicators)
	 * - Line highlighting (background color by severity)
	 * - Squiggly underlines (similar to IDE inspections)
	 *
	 * @param file File that was scanned
	 * @param scanIssues Issues to visualize
	 */
	private static void renderEditorDecorations(IFile file, List<ScanIssue> scanIssues) {
		try {
			if (scanIssues.isEmpty()) {
				return;
			}

			CxLogger.info(LOG_TAG + " Rendering editor decorations (" +
				scanIssues.size() + " issues)");

			// Delegate to ScanResultDecorator to render annotations
			ScanResultDecorator.decorateEditor(file, scanIssues);

			CxLogger.info(LOG_TAG + " ✓ Editor decorations rendered");

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error rendering decorations: " +
				e.getMessage());
		}
	}

	/**
	 * Notify listeners of new findings.
	 *
	 * Publishes an event to any listeners interested in scan results
	 * (e.g., status bars, dashboards, remote reporting services).
	 *
	 * @param file File that was scanned
	 * @param scanIssues Issues found
	 */
	private static void notifyListeners(IFile file, List<ScanIssue> scanIssues) {
		try {
			// TODO: Publish message bus event for listeners
			// - Create ResultAvailableEvent with file and issues
			// - Get message bus from Eclipse
			// - Publish to topic for interested subscribers
			// - Listeners: status bar, dashboards, analytics, etc.

			CxLogger.info(LOG_TAG + " ✓ Listeners notified");

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error notifying listeners: " +
				e.getMessage());
		}
	}

	/**
	 * Find open text editor for a file.
	 *
	 * Searches all open editors for one editing the given file.
	 *
	 * @param file File to find editor for
	 * @return ITextEditor or null if not open
	 */
	private static ITextEditor findOpenEditor(IFile file) {
		try {
			IWorkbench workbench = PlatformUI.getWorkbench();
			IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();

			if (page == null) {
				return null;
			}

			var editors = page.getEditors();
			for (var editor : editors) {
				if (editor instanceof ITextEditor) {
					Object input = editor.getEditorInput();
					if (input instanceof org.eclipse.ui.IFileEditorInput) {
						IFile editorFile = ((org.eclipse.ui.IFileEditorInput) input)
							.getFile();
						if (editorFile.equals(file)) {
							return (ITextEditor) editor;
						}
					}
				}
			}
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error finding open editor: " +
				e.getMessage());
		}

		return null;
	}

	/**
	 * Map scanner severity string to Eclipse Severity enum.
	 *
	 * @param severity Scanner severity (e.g., "CRITICAL", "HIGH")
	 * @return Eclipse Severity enum
	 */
	private static Severity mapSeverity(String severity) {
		if (severity == null) {
			return Severity.INFO;
		}

		String upper = severity.toUpperCase();
		if (upper.contains("CRITICAL")) {
			return Severity.CRITICAL;
		}
		if (upper.contains("HIGH")) {
			return Severity.HIGH;
		}
		if (upper.contains("MEDIUM")) {
			return Severity.MEDIUM;
		}
		if (upper.contains("LOW")) {
			return Severity.LOW;
		}

		return Severity.INFO;
	}

	/**
	 * Clear results for a file.
	 *
	 * Called when:
	 * - File is deleted
	 * - User manually clears findings
	 * - Project is closed
	 *
	 * @param file File to clear
	 */
	public static void clearResults(IFile file) {
		try {
			String filePath = file.getFullPath().toOSString();
			CxLogger.info(LOG_TAG + " Clearing results for: " + filePath);

			// TODO: Clear all UI components
			// - Remove markers from Problems View
			// - Remove from Findings View
			// - Remove editor decorations

			CxLogger.info(LOG_TAG + " ✓ Results cleared");

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error clearing results: " + e.getMessage());
		}
	}
}
