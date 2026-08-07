package com.checkmarx.eclipse.devassist.problems;

import java.util.Objects;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;

import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Processor that validates individual scan issues and creates problem descriptors.
 *
 * Encapsulates logic for:
 * - Validating scan issue data (location, line, severity)
 * - Creating problem descriptors for valid issues
 * - Triggering decoration for highlighted issues
 *
 * CRITICAL: Prevents crashes from invalid data by validating before processing.
 *
 * Mirrors JetBrains ScanIssueProcessor.
 */
public class ScanIssueProcessor {

	private static final String LOG_TAG = "[SCAN-ISSUE-PROCESSOR]";

	private final IFile file;
	private final IDocument document;
	private final ProblemHelper problemHelper;

	/**
	 * Constructor that takes file, document, and problemHelper.
	 *
	 * @param file The file being processed
	 * @param document The document
	 * @param problemHelper Problem helper with context
	 */
	public ScanIssueProcessor(IFile file, IDocument document, ProblemHelper problemHelper) {
		this.file = file;
		this.document = document;
		this.problemHelper = problemHelper;
	}

	/**
	 * Alternate constructor that extracts file and document from ProblemHelper.
	 *
	 * Mirrors JetBrains ScanIssueProcessor(ProblemHelper).
	 *
	 * @param problemHelper Problem helper containing file, document, etc.
	 */
	public ScanIssueProcessor(ProblemHelper problemHelper) {
		this.file = problemHelper.getFile();
		this.document = problemHelper.getDocument();
		this.problemHelper = problemHelper;
	}

	/**
	 * Process a single scan issue and create a problem descriptor if valid.
	 *
	 * Validation pipeline:
	 * 1. Check location exists and is not empty
	 * 2. Extract line number from location
	 * 3. Check line is within document range
	 * 4. Check severity is present and not blank
	 * 5. If all valid: create problem descriptor
	 * 6. If decorator enabled: highlight the issue
	 *
	 * Mirrors JetBrains ScanIssueProcessor.processScanIssue().
	 *
	 * @param scanIssue Scan issue to process
	 * @param isDecoratorEnabled Whether to add visual decorations
	 * @return ProblemDescriptor if valid, null if invalid
	 */
	public ProblemDescriptor processScanIssue(ScanIssue scanIssue, boolean isDecoratorEnabled) {

		// Validation: location exists and is not empty
		if (!isValidLocation(scanIssue)) {
			CxLogger.info(LOG_TAG + " Invalid location for: " + scanIssue.getTitle());
			return null;
		}

		// Extract line number
		int problemLineNumber = scanIssue.getLocations().get(0).getLine();

		// Validation: line number and severity are valid
		if (!isValidLineAndSeverity(problemLineNumber, scanIssue)) {
			CxLogger.info(LOG_TAG + " Invalid line/severity for: " + scanIssue.getTitle() +
				" (line=" + problemLineNumber + ", severity=" + scanIssue.getSeverity() + ")");
			return null;
		}

		try {
			return processValidIssue(scanIssue, problemLineNumber, isDecoratorEnabled);
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Exception processing issue: " +
				scanIssue.getTitle() + ": " + e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Validate that scan issue has a location.
	 *
	 * @param scanIssue Scan issue to validate
	 * @return true if location exists and is not empty
	 */
	private boolean isValidLocation(ScanIssue scanIssue) {
		return scanIssue.getLocations() != null && !scanIssue.getLocations().isEmpty();
	}

	/**
	 * Validate line number and severity.
	 *
	 * @param lineNumber Line number to check
	 * @param scanIssue Scan issue with severity
	 * @return true if line is in range and severity is not blank
	 */
	private boolean isValidLineAndSeverity(int lineNumber, ScanIssue scanIssue) {
		// Check line is within document bounds
		if (isLineOutOfRange(lineNumber)) {
			return false;
		}
		// Check severity is present and not blank
		return scanIssue.getSeverity() != null && !scanIssue.getSeverity().isBlank();
	}

	/**
	 * Check if line number is outside document range.
	 *
	 * @param lineNumber Line number to check
	 * @return true if line is out of range
	 */
	private boolean isLineOutOfRange(int lineNumber) {
		try {
			int lineCount = document.getNumberOfLines();
			return lineNumber < 1 || lineNumber > lineCount;
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error checking line range: " + e.getMessage(), e);
			return true;
		}
	}

	/**
	 * Process a valid scan issue.
	 *
	 * 1. Check if it's a "problem" (not just info/note)
	 * 2. If problem: create problem descriptor via ProblemBuilder
	 * 3. If decorator enabled: highlight the issue
	 *
	 * @param scanIssue The valid scan issue
	 * @param problemLineNumber Line number (already validated)
	 * @param isDecoratorEnabled Whether to decorate
	 * @return ProblemDescriptor if it's a problem, null if just info
	 */
	private ProblemDescriptor processValidIssue(
		ScanIssue scanIssue,
		int problemLineNumber,
		boolean isDecoratorEnabled) {

		boolean isProblem = isProblem(scanIssue.getSeverity().toLowerCase());

		ProblemDescriptor problemDescriptor = null;
		if (isProblem) {
			problemDescriptor = createProblemDescriptor(scanIssue, problemLineNumber);
		}

		if (isDecoratorEnabled) {
			highlightIssueIfNeeded(scanIssue, problemLineNumber, isProblem);
		}

		return problemDescriptor;
	}

	/**
	 * Check if severity indicates a reportable problem.
	 * Matches severity table in ProblemDecorator.mapSeverityToAnnotationType().
	 *
	 * @param severity Severity string (lowercase)
	 * @return true if problem, false if info/note/unknown/ok/ignored
	 */
	private boolean isProblem(String severity) {
		return severity.equals("malicious") ||
			severity.equals("critical") ||
			severity.equals("high") ||
			severity.equals("medium") ||
			severity.equals("low");
	}

	/**
	 * Create a problem descriptor via ProblemBuilder.
	 *
	 * @param scanIssue The scan issue
	 * @param problemLineNumber Line number
	 * @return ProblemDescriptor, or null on error
	 */
	private ProblemDescriptor createProblemDescriptor(ScanIssue scanIssue, int problemLineNumber) {
		try {
			return ProblemBuilder.build(problemHelper, scanIssue, problemLineNumber);
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Failed to create descriptor for: " +
				scanIssue.getTitle() + ": " + e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Highlight the issue in the editor and add gutter icon.
	 *
	 * Delegates to ProblemDecorator to add visual decoration.
	 *
	 * @param scanIssue The scan issue
	 * @param problemLineNumber Line number
	 * @param isProblem Whether it's a problem or just note
	 */
	private void highlightIssueIfNeeded(ScanIssue scanIssue, int problemLineNumber, boolean isProblem) {
		ProblemDecorator problemDecorator = problemHelper.getProblemDecorator();
		if (Objects.isNull(problemDecorator)) {
			problemDecorator = new ProblemDecorator();
		}
		problemDecorator.highlightLineAddGutterIconForProblem(
			problemHelper, scanIssue, isProblem, problemLineNumber);
	}
}
