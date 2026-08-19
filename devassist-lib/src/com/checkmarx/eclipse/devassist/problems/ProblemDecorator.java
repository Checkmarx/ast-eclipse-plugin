package com.checkmarx.eclipse.devassist.problems;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.checkmarx.eclipse.devassist.ui.findings.editor.FindingsAnnotation;
import com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper;
import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;
import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * Renders scan results as editor decorations.
 *
 * Creates visual indicators for issues in the editor:
 * - Gutter icons (severity indicators on line numbers)
 * - Line highlighting (background color by severity)
 * - Annotations (squiggly underlines and tooltips)
 *
 * Integrates with Eclipse's SourceViewerConfiguration to display
 * issue markers alongside the editor content.
 */
public class ProblemDecorator {

	private static final String LOG_TAG = "[SCAN-DECORATOR]";

	// Track annotations we've created so we can remove them later
	private static final Map<String, List<Annotation>> fileAnnotations = new HashMap<>();

	/**
	 * Render scan results as annotations in the editor.
	 *
	 * Creates FindingsAnnotation objects for each issue and adds them
	 * to the editor's annotation model for visual display.
	 *
	 * @param file       File that was scanned
	 * @param scanIssues Issues to visualize
	 */
	public static void decorateEditor(IFile file, List<ScanIssue> scanIssues) {
		if (file == null) {
			return;
		}
		if (scanIssues == null) {
			scanIssues = List.of();
		}

		// **FIX: Use getLocation() (absolute path) for consistency with RealTimeScanJob
		// and ResultPublisher**
		// This ensures fileAnnotations map keys match the same path format used
		// throughout the codebase
		String filePath = file.getLocation().toOSString();

		try {
			// Find open editor for this file
			ITextEditor editor = findOpenEditor(file);
			if (editor == null) {
				CxLogger.info(LOG_TAG + "No open editor for: " + filePath);
				return;
			}

			// Get annotation model from editor
			IAnnotationModel annotationModel = editor.getDocumentProvider()
					.getAnnotationModel(editor.getEditorInput());

			if (annotationModel == null) {
				CxLogger.warning(LOG_TAG + "No annotation model available");
				return;
			}

			// Remove previous annotations for this file (BEFORE isEmpty check)
			// This ensures stale annotations are cleared even if file is now clean
			clearAnnotations(filePath, annotationModel);

			// Early return if no issues to add
			if (scanIssues.isEmpty()) {
				return;
			}

			// Add new annotations for each issue
			List<Annotation> annotations = new java.util.ArrayList<>();

			for (ScanIssue issue : scanIssues) {
				try {
					// Ensure the IMarker CheckmarxMarkerResolutionGenerator's
					// Ctrl+1/quick-fix-in-hover
					// actions anchor to exists as soon as the squiggly does, rather than only after
					// the
					// user separately navigates to this finding from the Findings view.
					MarkerIssueMapper.ensureMarker(file, issue);

					FindingsAnnotation annotation = createAnnotation(editor, issue);
					if (annotation != null) {
						annotation.addButton(filePath, null);
						annotations.add(annotation);
						// **OSS-SPECIFIC LOGIC: Only decorate the first line (used for redirection)**
						// For OSS issues, decorate only the first location's line to keep it simple
						Position pos = null;

						if (issue.getScanEngine() != null &&
								issue.getScanEngine().name().equalsIgnoreCase("OSS")) {
							// OSS: Decorate only the first line where package is declared
							pos = decorateOssFirstLineOnly(editor, issue);
						} else {
							// Other engines: Use standard range calculation
							pos = calculateRange(editor, issue);
						}

						if (pos != null && pos.getLength() > 0) {
							// Add annotation to model for display
							annotationModel.addAnnotation(annotation, pos);
							CxLogger.info(LOG_TAG + "Annotation added to model");
						} else {
							CxLogger.warning(LOG_TAG + "FAILED: Invalid position (offset=" +
									(pos != null ? pos.getOffset() : "null") + ", length=" +
									(pos != null ? pos.getLength() : "null") + ")");
						}
					}
				} catch (Exception e) {
					CxLogger.warning(LOG_TAG + " Error creating annotation: " +
							e.getMessage());
					e.printStackTrace();
				}
			}

			// Store annotations for later cleanup
			fileAnnotations.put(filePath, annotations);

			CxLogger.info(LOG_TAG + "COMPLETE: Added " + annotations.size() +
					" annotations to editor");

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error decorating editor: " +
					e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Create a FindingsAnnotation for a scan issue.
	 *
	 * FindingsAnnotation extends Eclipse's Annotation class and provides
	 * custom rendering (color, icon, tooltip) based on issue severity.
	 *
	 * @param editor Text editor
	 * @param issue  Scan issue
	 * @return FindingsAnnotation, or null if creation fails
	 */
	private static FindingsAnnotation createAnnotation(ITextEditor editor,
			ScanIssue issue) {
		try {
			// Get severity from issue
			String severity = issue.getSeverity();

			// DEBUG: Log the actual severity value
			CxLogger.info(LOG_TAG + " [DEBUG] Issue: " + issue.getTitle() +
					" | Severity from issue: " + (severity != null ? severity : "NULL"));

			// Map severity to annotation type
			String annotationType = mapSeverityToAnnotationType(severity);

			CxLogger.info(LOG_TAG + " [DEBUG] Mapped to annotation type: " + annotationType);

			// Create annotation with issue details
			FindingsAnnotation annotation = new FindingsAnnotation(
					annotationType,
					issue.getTitle(),
					issue.getDescription(),
					issue);
			return annotation;
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error creating annotation: " +
					e.getMessage());
			return null;
		}
	}

	/**
	 * Map severity level to theme-aware custom Findings annotation type.
	 * Returns dark theme variant in dark theme, light variant in light theme.
	 * Handles all 8 severity levels including OK, UNKNOWN, and IGNORED.
	 *
	 * @param severity Severity string (MALICIOUS, CRITICAL, HIGH, MEDIUM, LOW,
	 *                 UNKNOWN, OK, IGNORED)
	 * @return Annotation type constant (com.checkmarx.eclipse.findings.{severity}[_dark])
	 */
	private static String mapSeverityToAnnotationType(String severity) {
		// Append _dark suffix if dark theme is active
		String themeSuffix = DevAssistUtils.isDarkTheme() ? "_dark" : "";

		if (severity == null) {
			return "com.checkmarx.eclipse.findings.unknown" + themeSuffix;
		}
		String upper = severity.toUpperCase();
		if (upper.contains("MALICIOUS")) {
			return "com.checkmarx.eclipse.findings.malicious" + themeSuffix;
		}
		if (upper.contains("CRITICAL") || upper.contains("ERROR")) {
			return "com.checkmarx.eclipse.findings.critical" + themeSuffix;
		}
		if (upper.contains("HIGH")) {
			return "com.checkmarx.eclipse.findings.high" + themeSuffix;
		}
		if (upper.contains("MEDIUM")) {
			return "com.checkmarx.eclipse.findings.medium" + themeSuffix;
		}
		if (upper.contains("LOW") || upper.contains("INFO")) {
			return "com.checkmarx.eclipse.findings.low" + themeSuffix;
		}
		if (upper.contains("UNKNOWN")) {
			return "com.checkmarx.eclipse.findings.unknown" + themeSuffix;
		}
		if (upper.contains("OK")) {
			return "com.checkmarx.eclipse.findings.ok" + themeSuffix;
		}
		if (upper.contains("IGNORED")) {
			return "com.checkmarx.eclipse.findings.ignored" + themeSuffix;
		}

		return "com.checkmarx.eclipse.findings.unknown" + themeSuffix;
	}

	/**
	 * Decorate only the first line for OSS issues (package declaration line).
	 *
	 * For OSS vulnerabilities, the Location has the exact character range,
	 * but it may span the entire dependency block. We simplify by decorating
	 * only the first line where the package is declared.
	 *
	 * @param editor Text editor
	 * @param issue  OSS issue
	 * @return Position covering the entire first line, or null if unable to
	 *         determine
	 */
	/**
	 * Decorate the complete OSS dependency block using the first and last
	 * locations from the issue.
	 *
	 * For OSS vulnerabilities, the Locations array contains the line/range
	 * information for the complete dependency block. The decoration starts
	 * from the first location's StartIndex and ends at the last location's
	 * EndIndex.
	 *
	 * Leading whitespace before the first StartIndex and trailing whitespace
	 * after the last EndIndex are not decorated.
	 *
	 * @param editor Text editor
	 * @param issue  OSS issue
	 * @return Position covering the complete OSS dependency block, or null if
	 *         unable to determine
	 */
	private static Position decorateOssFirstLineOnly(ITextEditor editor, ScanIssue issue) {

		try {
			IDocument document = editor.getDocumentProvider()
					.getDocument(editor.getEditorInput());

			if (document == null) {
				CxLogger.warning(LOG_TAG + "   [OSS] Document is null!");
				return null;
			}

			// Get locations from issue
			if (issue.getLocations() == null || issue.getLocations().isEmpty()) {
				CxLogger.warning(LOG_TAG + "   [OSS] No locations found!");
				return null;
			}

			Location firstLocation = issue.getLocations().get(0);
			Location lastLocation = issue.getLocations().get(issue.getLocations().size() - 1);

			// Convert line numbers from 1-based to 0-based
			int firstLineNumber = firstLocation.getLine() - 1;
			int lastLineNumber = lastLocation.getLine() - 1;

			int lineCount = document.getNumberOfLines();
			int docLength = document.getLength();

			// Bounds check
			if (firstLineNumber < 0 || firstLineNumber >= lineCount) {
				CxLogger.warning(LOG_TAG + "   [OSS] First line " + (firstLineNumber + 1) + " out of bounds (doc has "
						+ lineCount + " lines)");
				return null;
			}

			if (lastLineNumber < 0 || lastLineNumber >= lineCount) {
				CxLogger.warning(LOG_TAG + "   [OSS] Last line " + (lastLineNumber + 1) + " out of bounds (doc has "
						+ lineCount + " lines)");
				return null;
			}

			IRegion firstLineInfo = document.getLineInformation(firstLineNumber);
			IRegion lastLineInfo = document.getLineInformation(lastLineNumber);
			int firstLineOffset = firstLineInfo.getOffset();
			int lastLineOffset = lastLineInfo.getOffset();
			int firstLineLength = firstLineInfo.getLength();
			int lastLineLength = lastLineInfo.getLength();
			int startIndex = firstLocation.getStartIndex();
			int endIndex = lastLocation.getEndIndex();
			startIndex = Math.max(0, Math.min(startIndex, firstLineLength));
			endIndex = Math.max(0, Math.min(endIndex, lastLineLength));

			int startOffset = firstLineOffset + startIndex;
			int endOffset = lastLineOffset + endIndex;

			while (startOffset < endOffset && startOffset < docLength
					&& Character.isWhitespace(document.getChar(startOffset))) {
				startOffset++;
			}
			while (endOffset > startOffset && endOffset <= docLength
					&& Character.isWhitespace(document.getChar(endOffset - 1))) {
				endOffset--;
			}

			if (startOffset < 0 || startOffset > docLength) {
				CxLogger.warning(LOG_TAG + "   [OSS] Invalid start offset: " + startOffset);
				return null;
			}

			if (endOffset < startOffset || endOffset > docLength) {
				CxLogger.warning(LOG_TAG + "   [OSS] Invalid end offset: " + endOffset);
				return null;
			}

			int decorationLength = endOffset - startOffset;

			if (decorationLength <= 0) {
				CxLogger.warning(LOG_TAG + "   [OSS] Invalid decoration length: " + decorationLength);
				return null;
			}

			CxLogger.info(LOG_TAG + "   [OSS] Decorating dependency block");

			CxLogger.info(LOG_TAG + "   [OSS] First line: " + (firstLineNumber + 1) + ", StartIndex: "
					+ firstLocation.getStartIndex());

			CxLogger.info(LOG_TAG + "   [OSS] Last line: " + (lastLineNumber + 1) + ", EndIndex: "
					+ lastLocation.getEndIndex());

			CxLogger.info(LOG_TAG + "   [OSS] Final Position: [" + startOffset + "-" + endOffset + "] = "
					+ decorationLength + " chars");

			return new Position(startOffset, decorationLength);

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + "   [OSS] Error decorating dependency block: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Calculate the precise source range for an annotation.
	 *
	 * Handles BOTH absolute and line-relative offsets depending on scanner:
	 * - Secrets API: Returns RealtimeLocation with ABSOLUTE document offsets
	 * - ASCA API: Returns character positions that are LINE-RELATIVE offsets
	 *
	 * @param editor Text editor
	 * @param issue  Scan issue with location info
	 * @return org.eclipse.jface.text.Position representing the precise range
	 */
	private static Position calculateRange(ITextEditor editor, ScanIssue issue) {
		try {
			IDocument document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
			if (document == null)
				return new org.eclipse.jface.text.Position(0, 1);

			int docLength = document.getLength();

			// 1. Precise location-based offset calculation
			if (issue.getLocations() != null && !issue.getLocations().isEmpty()) {
				Location location = issue.getLocations().get(0);
				int rawStart = location.getStartIndex();
				int rawEnd = location.getEndIndex();
				int line = Math.max(0, location.getLine() - 1);

				IRegion lineInfo = document.getLineInformation(line);
				int lineOffset = lineInfo.getOffset();
				int lineLength = lineInfo.getLength();

				int trimIndent = getLeadingWhitespaceOffset(document, lineOffset, lineLength);
				// Use explicit flag from Location instead of inferring from magnitude
				boolean isAbsoluteOffset = location.isAbsoluteOffset();

				int charStart = isAbsoluteOffset ? rawStart : (lineOffset + rawStart);
				int charEnd = isAbsoluteOffset ? rawEnd : (lineOffset + rawEnd);

				// If start points to the beginning of the line, shift past leading whitespace
				if (charStart <= lineOffset) {
					charStart = lineOffset + trimIndent;
				}

				if (charEnd <= charStart) {
					charEnd = lineOffset + lineLength;
				}

				// Clamp offsets safely within document bounds
				charStart = Math.max(0, Math.min(charStart, docLength));
				charEnd = Math.max(charStart, Math.min(charEnd, docLength));

				if (charEnd > charStart) {
					return new org.eclipse.jface.text.Position(charStart, charEnd - charStart);
				}
			}

			// 2. Fallback: Highlight line content (skipping leading indentation)
			int targetLine = 0;
			if (issue.getProblematicLineNumber() != null) {
				targetLine = issue.getProblematicLineNumber() - 1;
			} else if (issue.getLocations() != null && !issue.getLocations().isEmpty()) {
				targetLine = issue.getLocations().get(0).getLine() - 1;
			}

			int line = Math.max(0, Math.min(targetLine, document.getNumberOfLines() - 1));
			IRegion lineInfo = document.getLineInformation(line);

			int trimIndent = getLeadingWhitespaceOffset(document, lineInfo.getOffset(), lineInfo.getLength());
			int startOffset = lineInfo.getOffset() + trimIndent;
			int length = Math.max(1, lineInfo.getLength() - trimIndent);

			return new org.eclipse.jface.text.Position(startOffset, length);

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error calculating range: " + e.getMessage());
			return new org.eclipse.jface.text.Position(0, 1);
		}
	}

	/**
	 * Calculates the number of leading whitespace characters (spaces/tabs) on a
	 * given line.
	 *
	 * @param document   Text document
	 * @param lineOffset Start character offset of the line
	 * @param lineLength Total length of the line
	 * @return Number of leading whitespace characters
	 */
	private static int getLeadingWhitespaceOffset(org.eclipse.jface.text.IDocument document,
			int lineOffset,
			int lineLength) {
		try {
			String lineText = document.get(lineOffset, lineLength);
			int leadingSpaces = 0;

			while (leadingSpaces < lineText.length() &&
					Character.isWhitespace(lineText.charAt(leadingSpaces))) {
				leadingSpaces++;
			}

			return leadingSpaces;
		} catch (Exception e) {
			return 0;
		}
	}

	/**
	 * Clear previous annotations for a file.
	 *
	 * @param filePath        File path
	 * @param annotationModel Annotation model
	 */
	private static void clearAnnotations(String filePath,
			IAnnotationModel annotationModel) {

		try {
			List<Annotation> previousAnnotations = fileAnnotations.get(filePath);
			if (previousAnnotations != null) {
				for (Annotation annotation : previousAnnotations) {
					annotationModel.removeAnnotation(annotation);
				}
				fileAnnotations.remove(filePath);

				CxLogger.info(LOG_TAG + " Cleared " + previousAnnotations.size() +
						" previous annotations");
			}
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error clearing annotations: " +
					e.getMessage());
		}
	}

	/**
	 * Clear all annotations from all open editors (used on logout).
	 * Removes all FindingsAnnotation objects from the annotation models
	 * of currently open editors.
	 */
	public static void clearAllAnnotations() {
		try {
			IWorkbench workbench = PlatformUI.getWorkbench();
			if (workbench == null) {
				return;
			}

			for (var window : workbench.getWorkbenchWindows()) {
				IWorkbenchPage page = window.getActivePage();
				if (page == null) {
					continue;
				}

				// Get all open editors
				org.eclipse.ui.IEditorReference[] editors = page.getEditorReferences();
				for (org.eclipse.ui.IEditorReference editorRef : editors) {
					try {
						org.eclipse.ui.IEditorPart editorPart = editorRef.getEditor(false);
						if (editorPart == null) {
							continue;
						}

						// Use adapter pattern to get ITextEditor
						ITextEditor editor = editorPart.getAdapter(ITextEditor.class);
						if (editor == null) {
							continue;
						}

						IAnnotationModel annotationModel = editor.getDocumentProvider()
								.getAnnotationModel(editor.getEditorInput());
						if (annotationModel == null) {
							continue;
						}

						// Remove all FindingsAnnotation objects
						java.util.List<Annotation> toRemove = new java.util.ArrayList<>();
						annotationModel.getAnnotationIterator().forEachRemaining(annotation -> {
							if (annotation instanceof FindingsAnnotation) {
								toRemove.add(annotation);
							}
						});

						for (Annotation annotation : toRemove) {
							annotationModel.removeAnnotation(annotation);
						}
					} catch (Exception e) {
						CxLogger.warning(LOG_TAG + " Error clearing annotations from editor: " + e.getMessage());
					}
				}
			}

			// Clear the fileAnnotations map
			fileAnnotations.clear();
			CxLogger.info(LOG_TAG + " All annotations cleared from all open editors");
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error clearing all annotations: " + e.getMessage());
		}
	}

	/**
	 * Find open text editor for a file.
	 *
	 * @param file File to find editor for
	 * @return ITextEditor or null
	 */
	private static ITextEditor findOpenEditor(IFile file) {
		try {
			IWorkbench workbench = PlatformUI.getWorkbench();
			if (workbench == null) {
				return null;
			}

			IWorkbenchPage page = null;
			try {
				page = workbench.getActiveWorkbenchWindow().getActivePage();
			} catch (NullPointerException e) {
				// Workbench window not available, try all windows
				for (var window : workbench.getWorkbenchWindows()) {
					page = window.getActivePage();
					if (page != null)
						break;
				}
			}

			if (page == null) {
				return null;
			}

			var editors = page.getEditors();
			for (var editor : editors) {
				Object input = editor.getEditorInput();
				if (input instanceof org.eclipse.ui.IFileEditorInput) {
					IFile editorFile = ((org.eclipse.ui.IFileEditorInput) input)
							.getFile();
					if (editorFile.equals(file)) {
						// Try method 1: Direct ITextEditor instance
						if (editor instanceof ITextEditor) {
							return (ITextEditor) editor;
						}

						// Try method 2: ITextEditor adapter (for MavenPomEditor, etc.)
						ITextEditor textEditor = editor.getAdapter(ITextEditor.class);
						if (textEditor != null) {
							return textEditor;
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
	 * Remove all decorations for a file.
	 *
	 * Called when:
	 * - Results are cleared
	 * - File is closed
	 * - Editor is disposed
	 *
	 * @param file File to remove decorations from
	 */
	public static void clearDecorations(IFile file) {
		try {
			// **FIX: Use getLocation() (absolute path) for consistency with
			// decorateEditor()**
			// Ensures fileAnnotations map lookups use the same path format
			String filePath = file.getLocation().toOSString();
			CxLogger.info(LOG_TAG + " Clearing decorations for: " + filePath);

			ITextEditor editor = findOpenEditor(file);
			if (editor == null) {
				fileAnnotations.remove(filePath);
				return;
			}

			IAnnotationModel annotationModel = editor.getDocumentProvider()
					.getAnnotationModel(editor.getEditorInput());

			if (annotationModel != null) {
				clearAnnotations(filePath, annotationModel);
			}

			CxLogger.info(LOG_TAG + " Decorations cleared");

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error clearing decorations: " +
					e.getMessage());
		}
	}

	/**
	 * Get decorator statistics.
	 *
	 * @return Summary string
	 */
	public static String getStatistics() {
		int totalAnnotations = fileAnnotations.values().stream()
				.mapToInt(List::size)
				.sum();
		return "Decorated files: " + fileAnnotations.size() +
				", Total annotations: " + totalAnnotations;
	}

	/**
	 * Highlight a line and add gutter icon for a problem.
	 *
	 * Delegates to the decorateEditor() path which handles annotation creation
	 * and display in the editor's gutter and line highlighting.
	 *
	 * @param problemHelper     Problem helper with context (used to locate the file
	 *                          being edited)
	 * @param scanIssue         Scan issue to highlight
	 * @param isProblem         Whether this is a problem (not just note)
	 * @param problemLineNumber Line number to highlight
	 */
	public void highlightLineAddGutterIconForProblem(
			ProblemHelper problemHelper,
			ScanIssue scanIssue,
			boolean isProblem,
			int problemLineNumber) {

		if (!isProblem || scanIssue == null) {
			return;
		}

		try {
			// Get the file from problem helper and decorate it
			// Wrap single issue in a list and delegate to decorateEditor()
			IFile file = problemHelper.getFile();
			if (file != null && file.exists()) {
				decorateEditor(file, List.of(scanIssue));
			} else {
				CxLogger.warning(LOG_TAG + " Cannot decorate: file not found or null");
			}
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error in highlightLineAddGutterIconForProblem: " + e.getMessage(), e);
		}
	}

	/**
	 * Remove all highlighters/decorations from a project.
	 *
	 * Called by DevAssistInspectionMgr when resetting editor state.
	 * Clears all tracked annotations across all files.
	 *
	 * @param project Project to clear (used for context, actual clearing is
	 *                project-wide)
	 */
	public static void removeAllHighlighters(org.eclipse.core.resources.IProject project) {
		try {
			IWorkbench workbench = PlatformUI.getWorkbench();
			if (workbench == null) {
				CxLogger.info(LOG_TAG + " removeAllHighlighters: Workbench not available");
				return;
			}

			for (org.eclipse.ui.IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
				for (IWorkbenchPage page : window.getPages()) {
					for (org.eclipse.ui.IEditorReference ref : page.getEditorReferences()) {
						try {
							ITextEditor editor = (ITextEditor) ref.getEditor(false);
							if (editor != null) {
								IAnnotationModel annotationModel = editor.getDocumentProvider()
										.getAnnotationModel(editor.getEditorInput());
								if (annotationModel != null) {
									for (List<Annotation> annotations : fileAnnotations.values()) {
										for (Annotation ann : annotations) {
											try {
												annotationModel.removeAnnotation(ann);
											} catch (Exception e) {
												// Continue removing others
											}
										}
									}
								}
							}
						} catch (Exception e) {
							// Continue with other editors
						}
					}
				}
			}

			fileAnnotations.clear();
			CxLogger.info(LOG_TAG + " Removed all highlighters for project: " + project.getName());

		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error removing all highlighters: " + e.getMessage(), e);
		}
	}
}
