package com.checkmarx.eclipse.devassist.inspection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;

import com.checkmarx.eclipse.devassist.basescanner.ScannerService;
import com.checkmarx.eclipse.devassist.common.ScanManager;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.problems.ProblemBuilder;
import com.checkmarx.eclipse.devassist.problems.ProblemDecorator;
import com.checkmarx.eclipse.devassist.problems.ProblemDescriptor;
import com.checkmarx.eclipse.devassist.problems.ProblemHelper;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.problems.ScanIssueProcessor;
import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * Main orchestrator for inspection workflow.
 *
 * Coordinates the complete flow:
 * 1. Scan files using ScanManager (inherited)
 * 2. Create problem descriptors from scan issues
 * 3. Validate issues (ScanIssueProcessor)
 * 4. Cache problems (ProblemHolderService)
 * 5. Decorate editor (ProblemDecorator)
 * 6. Manage cleanup and state reset
 *
 * Extends ScanManager to inherit scanning capabilities.
 * Mirrors JetBrains DevAssistInspectionMgr.
 */
public class DevAssistInspectionMgr extends ScanManager {

	private static final String LOG_TAG = "[INSPECTION-MGR]";

	private final ProblemDecorator problemDecorator = new ProblemDecorator();

	/**
	 * Constructor accepting scanner registry and state holder.
	 *
	 * @param registry    Scanner registry for the project
	 * @param stateHolder State holder for tracking file modifications
	 */
	public DevAssistInspectionMgr(
			ScannerRegistry registry,
			com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder stateHolder) {
		super(registry, stateHolder);
	}

	/**
	 * Scan a file and create problem descriptors.
	 *
	 * Complete orchestration:
	 * 1. Build problem helper
	 * 2. Scan file → get ScanIssue list (if not already provided)
	 * 3. Cache scan issues
	 * 4. Create ScanIssueProcessor for validation
	 * 5. For each issue: validate and create ProblemDescriptor
	 * 6. Cache problem descriptors
	 * 7. Return array of problem descriptors
	 *
	 * @param problemHelperBuilder Builder with pre-configured context
	 * @return Array of problem descriptors (empty if none)
	 */
	public ProblemDescriptor[] startScanAndCreateProblemDescriptors(
			ProblemHelper.Builder problemHelperBuilder) {

		ProblemHelper problemHelper = problemHelperBuilder.build();

		CxLogger.info(LOG_TAG + " Starting scan for file: " + problemHelper.getFile().getName());

		try {
			// Use pre-scanned issues if available, otherwise scan file.
			// ✅ Only fall back to a fresh scan when the caller genuinely has no
			// issue list yet (null). Callers such as ResultPublisher always pass a
			// real (possibly empty) list here after a completed scan cycle - an
			// empty list is a legitimate "file has zero issues" result, not a
			// signal to re-scan. Treating isEmpty() as "need a fresh scan" caused a
			// redundant, wasted re-scan on every zero-issue publish (harmless
			// since ScanManager's state-hash dedup made it a no-op, but noisy and
			// misleading in logs).
			List<ScanIssue> allScanIssues = problemHelper.getScanIssueList();
			if (allScanIssues == null) {
				allScanIssues = scanFile(problemHelper.getFilePath());
				CxLogger.info(LOG_TAG + " Performed fresh scan for file: " + problemHelper.getFile().getName());
			} else {
				CxLogger.info(LOG_TAG + " Using pre-scanned issues for file: " + problemHelper.getFile().getName());
			}

			if (allScanIssues.isEmpty()) {
				CxLogger.info(LOG_TAG + " No scan issues found for: " +
						problemHelper.getFile().getName());
				// Clear stale cached problem descriptors for this file - otherwise a
				// later getExistingProblems() lookup could resurrect descriptors for
				// issues that no longer exist.
				problemHelper.getProblemHolderService().removeProblemDescriptorsForFile(problemHelper.getFilePath());
				decorateUIForIgnoreVulnerability(problemHelper.getFile(), allScanIssues);
				return new ProblemDescriptor[0];
			}

			// Ensure helper has the issues (in case they were pre-populated)
			problemHelperBuilder.scanIssueList(allScanIssues);
			ProblemHelper helperWithIssues = problemHelperBuilder.build();

			// NOTE: do NOT call problemHolderService.addScanIssues() here. The
			// only caller of this method (ResultPublisher.createAndRenderDecorations)
			// already cached this exact issue list via ResultPublisher.updateFindingsView()
			// moments earlier. Re-caching the same list here re-publishes
			// ISSUES_UPDATED_TOPIC a second time for the same scan cycle, causing
			// CxFindingsView to run a second, redundant applyDecorationsToOpenEditors()
			// pass concurrently with the direct decorateUI() call below - widening the
			// race window with IgnoreFileManager's async file-watcher callback and
			// producing inconsistent ignored/severity icon states on the same line.

			// Create problems with decoration
			List<ProblemDescriptor> allProblems = createProblemDescriptorsWithDecoration(helperWithIssues);

			if (allProblems.isEmpty()) {
				CxLogger.info(LOG_TAG + " No problem descriptors created for: " +
						problemHelper.getFile().getName());
				return new ProblemDescriptor[0];
			}

			// Cache problem descriptors
			helperWithIssues.getProblemHolderService().addProblemDescriptors(
					problemHelper.getFilePath(), allProblems);

			CxLogger.info(LOG_TAG + " Created " + allProblems.size() +
					" problem descriptors for: " + problemHelper.getFile().getName());

			return allProblems.toArray(new ProblemDescriptor[0]);

		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error during scan: " + e.getMessage(), e);
			return new ProblemDescriptor[0];
		}
	}

	/**
	 * Create problem descriptors with UI decoration.
	 *
	 * Removes existing annotations, validates issues, creates descriptors,
	 * and decorates the editor with visual feedback.
	 *
	 * @param problemHelper Helper with scan issues
	 * @return List of created problem descriptors
	 */
	private List<ProblemDescriptor> createProblemDescriptorsWithDecoration(
			ProblemHelper problemHelper) {

		if (isScanIssuePresent(problemHelper.getScanIssueList())) {
			// Clear existing decorations
			ProblemDecorator.removeAllHighlighters(problemHelper.getProject());

			// Build descriptors WITHOUT per-issue decoration: decorateUI() below
			// already redraws the full, merged issue list in one pass. Passing
			// isDecoratorEnabled=true here used to make ScanIssueProcessor call
			// ProblemDecorator.highlightLineAddGutterIconForProblem() once per
			// issue, and each of those calls clears and rebuilds ALL annotations
			// for the file (ProblemDecorator.decorateEditor() unconditionally
			// clears before adding) - so a 4-issue file flickered through 4
			// single-issue annotation states before the final full redraw,
			// occasionally leaving the hover to sample an incomplete annotation
			// model mid-flicker.
			List<ProblemDescriptor> descriptors = createProblemDescriptors(
					problemHelper, false);

			// Decorate UI
			if (!descriptors.isEmpty()) {
				decorateUI(problemHelper.getDocument(), problemHelper.getFile(),
						problemHelper.getScanIssueList());
			}

			return descriptors;
		}
		return Collections.emptyList();
	}

	/**
	 * Create problem descriptors without UI decoration.
	 *
	 * @param problemHelper Helper with scan issues
	 * @return List of created problem descriptors
	 */
	public List<ProblemDescriptor> createProblemDescriptorsWithoutDecoration(
			ProblemHelper problemHelper) {

		if (isScanIssuePresent(problemHelper.getScanIssueList())) {
			return createProblemDescriptors(problemHelper, false);
		}
		return Collections.emptyList();
	}

	/**
	 * Create problem descriptors from scan issues.
	 *
	 * For each scan issue:
	 * 1. Create ScanIssueProcessor
	 * 2. Validate and create ProblemDescriptor
	 * 3. Collect non-null descriptors
	 *
	 * @param problemHelper      Helper with context and issues
	 * @param isDecoratorEnabled Whether to enable visual decoration
	 * @return List of valid problem descriptors
	 */
	private List<ProblemDescriptor> createProblemDescriptors(
			ProblemHelper problemHelper,
			boolean isDecoratorEnabled) {

		List<ProblemDescriptor> descriptors = new ArrayList<>();
		ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper);

		for (ScanIssue scanIssue : problemHelper.getScanIssueList()) {
			ProblemDescriptor descriptor = processor.processScanIssue(
					scanIssue, isDecoratorEnabled);
			if (descriptor != null) {
				descriptors.add(descriptor);
			}
		}

		CxLogger.info(LOG_TAG + " Created " + descriptors.size() +
				" problem descriptors from " + problemHelper.getScanIssueList().size() +
				" scan issues");

		return descriptors;
	}

	/**
	 * Get existing problem descriptors for a file.
	 *
	 * Called when file hasn't changed since last scan.
	 * Returns cached problem descriptors.
	 *
	 * @param problemHolderService     Cache service
	 * @param filePath                 File path
	 * @param document                 Document (for validation)
	 * @param file                     IFile
	 * @param supportedEnabledScanners Enabled scanners
	 * @return Array of cached problem descriptors
	 */
	public ProblemDescriptor[] getExistingProblems(
			ProblemHolderService problemHolderService,
			String filePath,
			IDocument document,
			IFile file,
			List<ScannerService> supportedEnabledScanners) {

		ProblemHelper problemHelper = ProblemHelper.builder(file, file.getProject())
				.filePath(filePath)
				.document(document)
				.supportedScanners(supportedEnabledScanners)
				.problemHolderService(problemHolderService)
				.problemDecorator(this.problemDecorator)
				.build();

		// Get cached issues
		List<ScanIssue> scanIssueList = problemHolderService.getScanIssuesByFile(filePath);
		if (scanIssueList.isEmpty()) {
			CxLogger.warning(LOG_TAG + " No cached issues for: " + filePath);
			resetEditorAndResults(file.getProject(), filePath);
			decorateUIForIgnoreVulnerability(file, scanIssueList);
			return new ProblemDescriptor[0];
		}

		// Get cached problem descriptors
		List<ProblemDescriptor> cachedDescriptors = problemHolderService.getProblemDescriptors(filePath);
		if (cachedDescriptors.isEmpty()) {
			CxLogger.warning(LOG_TAG + " No cached problem descriptors for: " + filePath);
			decorateUIForIgnoreVulnerability(file, scanIssueList);
			return new ProblemDescriptor[0];
		}

		// Decorate UI with cached issues
		decorateUI(document, file, scanIssueList);

		CxLogger.info(LOG_TAG + " Returning " + cachedDescriptors.size() +
				" cached problem descriptors for: " + file.getName());

		return cachedDescriptors.toArray(new ProblemDescriptor[0]);
	}

	/**
	 * Decorate UI with scan results (gutter icons, underlines).
	 *
	 * @param document      Document to decorate
	 * @param file          File being decorated
	 * @param scanIssueList Issues to show
	 */
	public void decorateUI(IDocument document, IFile file, List<ScanIssue> scanIssueList) {
		try {
			ProblemDecorator.decorateEditor(file, scanIssueList);
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error decorating UI: " + e.getMessage(), e);
		}
	}

	/**
	 * Decorate UI for ignored vulnerabilities or when NO issues found.
	 *
	 * ✅ CRITICAL: This is called when scan returns 0 issues.
	 * We MUST clear old annotations/decorations from the editor.
	 * By calling decorateEditor() with the (empty) list, it will:
	 * 1. Clear old annotations via clearAnnotations()
	 * 2. Return early since the list is empty (nothing to add)
	 *
	 * @param file          File to decorate
	 * @param scanIssueList Issues (may be empty)
	 */
	public void decorateUIForIgnoreVulnerability(IFile file, List<ScanIssue> scanIssueList) {
		try {
			CxLogger.info(LOG_TAG + " decorateUIForIgnoreVulnerability called for: " + file.getName() +
					" with " + scanIssueList.size() + " issues");
			// Clear decorations from editor (this also works with empty list)
			ProblemDecorator.decorateEditor(file, scanIssueList);
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error in decorateUIForIgnoreVulnerability: " + e.getMessage(), e);
		}
	}

	/**
	 * Reset editor and clear all cached results for a file.
	 *
	 * Called when:
	 * - File is closed
	 * - Scan encounters error
	 * - User requests reset
	 *
	 * @param project  Project containing file
	 * @param filePath File path to reset
	 */
	public void resetEditorAndResults(IProject project, String filePath) {
		try {
			if (project == null || !project.isOpen()) {
				return;
			}

			// Clear visual decorations
			ProblemDecorator.removeAllHighlighters(project);

			// Clear cached data
			ProblemHolderService problemHolderService = ProblemHolderService.getInstance(project);
			if (problemHolderService != null && filePath != null && !filePath.isEmpty()) {
				problemHolderService.removeProblemDescriptorsForFile(filePath);
				problemHolderService.removeScanIssues(filePath);
			}

			CxLogger.info(LOG_TAG + " Reset editor and results for: " + filePath);

		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error resetting: " + e.getMessage(), e);
		}
	}

	/**
	 * Check if scan issues are present.
	 *
	 * @param scanIssueList List to check
	 * @return true if not null and not empty
	 */
	private boolean isScanIssuePresent(List<ScanIssue> scanIssueList) {
		return scanIssueList != null && !scanIssueList.isEmpty();
	}
}
