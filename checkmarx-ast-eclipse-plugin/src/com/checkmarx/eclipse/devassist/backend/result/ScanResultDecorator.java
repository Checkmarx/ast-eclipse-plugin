package com.checkmarx.eclipse.devassist.backend.result;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.checkmarx.eclipse.devassist.ui.findings.editor.FindingsAnnotation;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.utils.CxLogger;

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
public class ScanResultDecorator {

	private static final String LOG_TAG = "[SCAN-DECORATOR]";

	// Track annotations we've created so we can remove them later
	private static final Map<String, List<Annotation>> fileAnnotations =
		new HashMap<>();

	/**
	 * Render scan results as annotations in the editor.
	 *
	 * Creates FindingsAnnotation objects for each issue and adds them
	 * to the editor's annotation model for visual display.
	 *
	 * @param file File that was scanned
	 * @param scanIssues Issues to visualize
	 */
	public static void decorateEditor(IFile file, List<ScanIssue> scanIssues) {
		System.out.println("[SCAN-DECORATOR-ENTRY] decorateEditor called with " +
			(scanIssues != null ? scanIssues.size() : "null") + " issues");

		if (file == null) {
			System.out.println("[SCAN-DECORATOR-ENTRY] ✗ File is NULL - returning");
			return;
		}
		if (scanIssues == null) {
			System.out.println("[SCAN-DECORATOR-ENTRY] ✗ ScanIssues is NULL - returning");
			return;
		}
		if (scanIssues.isEmpty()) {
			System.out.println("[SCAN-DECORATOR-ENTRY] ✗ ScanIssues is EMPTY - returning");
			return;
		}

		// **FIX: Use getLocation() (absolute path) for consistency with RealTimeScanJob and ResultPublisher**
		// This ensures fileAnnotations map keys match the same path format used throughout the codebase
		String filePath = file.getLocation().toOSString();
		System.out.println("[SCAN-DECORATOR-ENTRY] File path: " + filePath);

		try {
			// Find open editor for this file
			System.out.println("[SCAN-DECORATOR-ENTRY] [STEP 1/3] Finding open editor...");
			ITextEditor editor = findOpenEditor(file);
			if (editor == null) {
				System.out.println("[SCAN-DECORATOR-ENTRY] ✗ [STEP 1/3] No open editor for: " + filePath);
				CxLogger.info(LOG_TAG + " ✗ No open editor for: " + filePath);
				return;
			}
			System.out.println("[SCAN-DECORATOR-ENTRY] ✓ [STEP 1/3] Found editor: " + editor.getClass().getSimpleName());

			// Get annotation model from editor
			System.out.println("[SCAN-DECORATOR-ENTRY] [STEP 2/3] Getting annotation model...");
			IAnnotationModel annotationModel = editor.getDocumentProvider()
				.getAnnotationModel(editor.getEditorInput());

			if (annotationModel == null) {
				System.out.println("[SCAN-DECORATOR-ENTRY] ✗ [STEP 2/3] Annotation model is NULL");
				CxLogger.warning(LOG_TAG + " ✗ No annotation model available");
				return;
			}
			System.out.println("[SCAN-DECORATOR-ENTRY] ✓ [STEP 2/3] Got annotation model");

			// Remove previous annotations for this file
			System.out.println("[SCAN-DECORATOR-ENTRY] [STEP 3/3] Processing " + scanIssues.size() + " issues...");
			clearAnnotations(filePath, annotationModel);

			// Add new annotations for each issue
			List<Annotation> annotations = new java.util.ArrayList<>();

			for (ScanIssue issue : scanIssues) {
				try {
					FindingsAnnotation annotation = createAnnotation(editor, issue);
					if (annotation != null) {
						annotation.addButton(filePath, null);
						annotations.add(annotation);

						CxLogger.info(LOG_TAG + " ─────────────────────────────────────────────────");
						CxLogger.info(LOG_TAG + " Issue: " + issue.getTitle());
						CxLogger.info(LOG_TAG + "   Engine: " + issue.getScanEngine());
						CxLogger.info(LOG_TAG + "   Severity: " + issue.getSeverity());

						// **OSS-SPECIFIC LOGIC: Only decorate the first line (used for redirection)**
						// For OSS issues, decorate only the first location's line to keep it simple
						org.eclipse.jface.text.Position pos = null;

						if (issue.getScanEngine() != null &&
						    issue.getScanEngine().name().equalsIgnoreCase("OSS")) {
							// OSS: Decorate only the first line where package is declared
							pos = decorateOssFirstLineOnly(editor, issue);
						} else {
							// Other engines: Use standard range calculation
							pos = calculateRange(editor, issue);
						}

						if (pos != null && pos.getLength() > 0) {
							CxLogger.info(LOG_TAG + "   Calculated Position for Editor:");
							CxLogger.info(LOG_TAG + "     Offset: " + pos.getOffset());
							CxLogger.info(LOG_TAG + "     Length: " + pos.getLength());
							CxLogger.info(LOG_TAG + "     Range: [" + pos.getOffset() + "-" + (pos.getOffset() + pos.getLength()) + "]");

							// Add annotation to model for display
							annotationModel.addAnnotation(annotation, pos);
							CxLogger.info(LOG_TAG + "   ✓ Annotation added to model");
						} else {
							CxLogger.warning(LOG_TAG + "   ✗ FAILED: Invalid position (offset=" +
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

			CxLogger.info(LOG_TAG + " ══════════════════════════════════════════════════");
			CxLogger.info(LOG_TAG + " ✓ COMPLETE: Added " + annotations.size() +
				" annotations to editor");
			CxLogger.info(LOG_TAG + " ══════════════════════════════════════════════════");

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
	 * @param issue Scan issue
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
				issue.getDescription()
			);
			return annotation;
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error creating annotation: " +
				e.getMessage());
			return null;
		}
	}


	/**
	 * Map severity level to custom Findings annotation type.
	 * Handles all 8 severity levels including OK, UNKNOWN, and IGNORED.
	 *
	 * @param severity Severity string (MALICIOUS, CRITICAL, HIGH, MEDIUM, LOW, UNKNOWN, OK, IGNORED)
	 * @return Annotation type constant (com.checkmarx.eclipse.findings.{severity})
	 */
	private static String mapSeverityToAnnotationType(String severity) {
		if (severity == null) {
			return "com.checkmarx.eclipse.findings.unknown";
		}
		String upper = severity.toUpperCase();
		if (upper.contains("MALICIOUS")) {
			return "com.checkmarx.eclipse.findings.malicious";
		}
		if (upper.contains("CRITICAL") || upper.contains("ERROR")) {
			return "com.checkmarx.eclipse.findings.critical";
		}
		if (upper.contains("HIGH")) {
			return "com.checkmarx.eclipse.findings.high";
		}
		if (upper.contains("MEDIUM")) {
			return "com.checkmarx.eclipse.findings.medium";
		}
		if (upper.contains("LOW") || upper.contains("INFO")) {
			return "com.checkmarx.eclipse.findings.low";
		}
		if (upper.contains("UNKNOWN")) {
			return "com.checkmarx.eclipse.findings.unknown";
		}
		if (upper.contains("OK")) {
			return "com.checkmarx.eclipse.findings.ok";
		}
		if (upper.contains("IGNORED")) {
			return "com.checkmarx.eclipse.findings.ignored";
		}

		return "com.checkmarx.eclipse.findings.unknown";
	}

	/**
	 * Decorate only the first line for OSS issues (package declaration line).
	 *
	 * For OSS vulnerabilities, the Location has the exact character range,
	 * but it may span the entire dependency block. We simplify by decorating
	 * only the first line where the package is declared.
	 *
	 * @param editor Text editor
	 * @param issue OSS issue
	 * @return Position covering the entire first line, or null if unable to determine
	 */
	private static org.eclipse.jface.text.Position decorateOssFirstLineOnly(
		ITextEditor editor, ScanIssue issue) {

		try {
			org.eclipse.jface.text.IDocument document =
				editor.getDocumentProvider().getDocument(editor.getEditorInput());

			if (document == null) {
				CxLogger.warning(LOG_TAG + "   [OSS] Document is null!");
				return null;
			}

			// Get the line number from first location
			if (issue.getLocations() == null || issue.getLocations().isEmpty()) {
				CxLogger.warning(LOG_TAG + "   [OSS] No locations found!");
				return null;
			}

			com.checkmarx.eclipse.devassist.ui.findings.model.Location location =
				issue.getLocations().get(0);
			int lineNumber = location.getLine() - 1;  // Convert to 0-based

			int docLength = document.getLength();
			int lineCount = document.getNumberOfLines();

			// Bounds check
			if (lineNumber < 0 || lineNumber >= lineCount) {
				CxLogger.warning(LOG_TAG + "   [OSS] Line " + (lineNumber + 1) +
					" out of bounds (doc has " + lineCount + " lines)");
				return null;
			}

			// Get the entire line information
			org.eclipse.jface.text.IRegion lineInfo = document.getLineInformation(lineNumber);
			int lineOffset = lineInfo.getOffset();
			int lineLength = lineInfo.getLength();

			// For OSS, decorate the entire line (excluding trailing newline)
			// This ensures the gutter icon and underline appear on the whole line
			int decorationLength = lineLength;
			if (decorationLength == 0) {
				decorationLength = 1;  // Minimum 1 char
			}

			CxLogger.info(LOG_TAG + "   [OSS] Line " + (lineNumber + 1) +
				" (offset=" + lineOffset + ", length=" + decorationLength + ")");

			// Bounds check final position
			if (lineOffset < 0 || lineOffset > docLength) {
				CxLogger.warning(LOG_TAG + "   [OSS] Line offset " + lineOffset +
					" out of bounds (doc length=" + docLength + ")");
				return null;
			}

			if (lineOffset + decorationLength > docLength) {
				CxLogger.warning(LOG_TAG + "   [OSS] Adjusted length from " +
					decorationLength + " to " + (docLength - lineOffset));
				decorationLength = docLength - lineOffset;
			}

			if (decorationLength <= 0) {
				CxLogger.warning(LOG_TAG + "   [OSS] Invalid decoration length: " + decorationLength);
				return null;
			}

			CxLogger.info(LOG_TAG + "   [OSS] ✓ Decorating first line: [" + lineOffset +
				"-" + (lineOffset + decorationLength) + "] = " + decorationLength + " chars");

			return new org.eclipse.jface.text.Position(lineOffset, decorationLength);

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + "   [OSS] Error decorating first line: " +
				e.getMessage());
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
	 * @param issue Scan issue with location info
	 * @return org.eclipse.jface.text.Position representing the precise range
	 */
	private static org.eclipse.jface.text.Position calculateRange(
		ITextEditor editor, ScanIssue issue) {

		try {
			org.eclipse.jface.text.IDocument document =
				editor.getDocumentProvider().getDocument(editor.getEditorInput());

			if (document == null) {
				CxLogger.warning(LOG_TAG + " Document is null!");
				return new org.eclipse.jface.text.Position(0, 1);
			}

			int docLength = document.getLength();
			CxLogger.info(LOG_TAG + "   calculateRange() - Document length: " + docLength + " chars");

			// Use Location data if available for precise character range
			if (issue.getLocations() != null && !issue.getLocations().isEmpty()) {
				com.checkmarx.eclipse.devassist.ui.findings.model.Location location =
					issue.getLocations().get(0);

				int rawStartIndex = location.getStartIndex();
				int rawEndIndex = location.getEndIndex();
				int lineNumber = location.getLine() - 1;  // Convert to 0-based
				org.eclipse.jface.text.IRegion lineInfo = document.getLineInformation(lineNumber);
	            int lineOffset = lineInfo.getOffset();
	            int lineLength = lineInfo.getLength();

				// Determine if indices are absolute or line-relative
				// If rawStartIndex is 0 and we're not on line 1, they're likely line-relative
				boolean isLineRelative = (rawStartIndex == 0 && lineNumber > 0) ||
										  (rawEndIndex < 100 && rawEndIndex - rawStartIndex < 100);
				// Calculate leading whitespace on the line
	            int leadingWhitespace = getLeadingWhitespaceOffset(document, lineOffset, lineLength);

				int charStart, charEnd;

				if (isLineRelative) {
					int effectiveStart = (rawStartIndex == 0) ? leadingWhitespace : rawStartIndex;
	                
	                charStart = lineOffset + effectiveStart;
	                charEnd = lineOffset + rawEndIndex;

	                // Ensure charEnd is after charStart if end index was also line-relative/small
	                if (charEnd <= charStart) {
	                    charEnd = lineOffset + lineLength;
	                }
				} else {
					// Absolute: use directly
					CxLogger.info(LOG_TAG + "     → Treating as ABSOLUTE document offsets");
					charStart = rawStartIndex;
					charEnd = rawEndIndex;
				}

				// Bounds check
				boolean startFixed = false;
				boolean endFixed = false;

				if (charStart < 0 || charStart > docLength) {
					CxLogger.warning(LOG_TAG + "     ⚠ Start offset " + charStart + " out of bounds! Doc length: " + docLength);
					charStart = 0;
					startFixed = true;
				}
				if (charEnd < 0 || charEnd > docLength) {
					CxLogger.warning(LOG_TAG + "     ⚠ End offset " + charEnd + " out of bounds! Doc length: " + docLength);
					charEnd = docLength;
					endFixed = true;
				}

				if (startFixed || endFixed) {
					CxLogger.info(LOG_TAG + "     Bounds-checked: Start=" + charStart + ", End=" + charEnd);
				}

				// Ensure valid range
				if (charEnd > charStart && charStart >= 0) {
					int length = charEnd - charStart;
					CxLogger.info(LOG_TAG + "     ✓ PRECISE RANGE: [" + charStart + "-" + charEnd + "] = " + length + " chars");
					return new org.eclipse.jface.text.Position(charStart, length);
				}
			}

			// Fallback: Use line number from problematicLineNumber or location line
			CxLogger.info(LOG_TAG + "   Using FALLBACK line-based range:");
			int lineNumber = 0;
			if (issue.getProblematicLineNumber() != null) {
				lineNumber = issue.getProblematicLineNumber() - 1;
				CxLogger.info(LOG_TAG + "     Line from problematicLineNumber: " + (lineNumber + 1));
			} else if (issue.getLocations() != null && !issue.getLocations().isEmpty()) {
				lineNumber = issue.getLocations().get(0).getLine() - 1;
				CxLogger.info(LOG_TAG + "     Line from location: " + (lineNumber + 1));
			} else {
				CxLogger.warning(LOG_TAG + "     ⚠ No line number found, using line 0");
			}

			// Bounds check
			int lineCount = document.getNumberOfLines();
			CxLogger.info(LOG_TAG + "     Document has " + lineCount + " lines");

			if (lineNumber >= lineCount) {
				CxLogger.warning(LOG_TAG + "     ⚠ Line " + (lineNumber + 1) + " exceeds document (max " + lineCount + ")");
				lineNumber = Math.max(0, lineCount - 1);
			}
			lineNumber = Math.max(0, lineNumber);

			// Get line information for fallback (underline entire line)
			org.eclipse.jface.text.IRegion lineInfo = document.getLineInformation(lineNumber);
			int offset = lineInfo.getOffset();
			int length = Math.min(lineInfo.getLength(), 100); // Underline first 100 chars
			if (length == 0) {
				length = 1;
			}

			CxLogger.info(LOG_TAG + "     ✓ FALLBACK RANGE (line " + (lineNumber + 1) + "): [" + offset + "-" + (offset + length) + "] = " + length + " chars");
			return new org.eclipse.jface.text.Position(offset, length);

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error calculating range: " + e.getMessage());
			e.printStackTrace();
			return new org.eclipse.jface.text.Position(0, 1);
		}
	}
	
	/**
	 * Calculates the number of leading whitespace characters (spaces/tabs) on a given line.
	 *
	 * @param document Text document
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
	 * @param filePath File path
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

				CxLogger.info(LOG_TAG + " ✓ Cleared " + previousAnnotations.size() +
					" previous annotations");
			}
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error clearing annotations: " +
				e.getMessage());
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
					if (page != null) break;
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
			// **FIX: Use getLocation() (absolute path) for consistency with decorateEditor()**
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

			CxLogger.info(LOG_TAG + " ✓ Decorations cleared");

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
}
