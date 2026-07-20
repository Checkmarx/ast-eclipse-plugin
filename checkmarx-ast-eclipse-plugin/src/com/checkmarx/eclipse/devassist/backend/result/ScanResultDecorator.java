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
		if (file == null || scanIssues == null || scanIssues.isEmpty()) {
			return;
		}

		String filePath = file.getFullPath().toOSString();
		CxLogger.info(LOG_TAG + " Decorating editor with " + scanIssues.size() +
			" annotations: " + filePath);

		try {
			// Find open editor for this file
			ITextEditor editor = findOpenEditor(file);
			if (editor == null) {
				CxLogger.info(LOG_TAG + " No open editor for: " + filePath);
				return;
			}

			// Get annotation model from editor
			IAnnotationModel annotationModel = editor.getDocumentProvider()
				.getAnnotationModel(editor.getEditorInput());

			if (annotationModel == null) {
				CxLogger.warning(LOG_TAG + " No annotation model available");
				return;
			}

			// Remove previous annotations for this file
			clearAnnotations(filePath, annotationModel);

			// Add new annotations for each issue
			List<Annotation> annotations = new java.util.ArrayList<>();

			for (ScanIssue issue : scanIssues) {
				try {
					FindingsAnnotation annotation = createAnnotation(editor, issue);
					if (annotation != null) {
						annotations.add(annotation);

						// Add annotation to model for display
						annotationModel.addAnnotation(annotation,
							calculateRange(editor, issue));
					}
				} catch (Exception e) {
					CxLogger.warning(LOG_TAG + " Error creating annotation: " +
						e.getMessage());
				}
			}

			// Store annotations for later cleanup
			fileAnnotations.put(filePath, annotations);

			CxLogger.info(LOG_TAG + " ✓ Added " + annotations.size() +
				" annotations to editor");

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error decorating editor: " +
				e.getMessage());
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
			// Create annotation with issue details
			FindingsAnnotation annotation = new FindingsAnnotation(issue);

			CxLogger.info(LOG_TAG + " ✓ Created annotation for: " + issue.getTitle());
			return annotation;

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error creating annotation: " +
				e.getMessage());
			return null;
		}
	}

	/**
	 * Calculate the source range for an annotation.
	 *
	 * Maps issue line/column to the actual text range in the document
	 * so the annotation appears at the correct location.
	 *
	 * @param editor Text editor
	 * @param issue Scan issue with line/column info
	 * @return org.eclipse.jface.text.Position representing the range
	 */
	private static org.eclipse.jface.text.Position calculateRange(
		ITextEditor editor, ScanIssue issue) {

		try {
			org.eclipse.jface.text.IDocument document =
				editor.getDocumentProvider().getDocument(editor.getEditorInput());

			if (document == null) {
				return new org.eclipse.jface.text.Position(0, 1);
			}

			// Get line number (1-indexed in ScanIssue, 0-indexed in document)
			int lineNumber = (issue.getProblematicLineNumber() != null ?
				issue.getProblematicLineNumber() : 1) - 1;

			// Get line information
			org.eclipse.jface.text.IRegion lineInfo = document.getLineInformation(
				Math.max(0, lineNumber)
			);

			// Create position from line start to line end
			return new org.eclipse.jface.text.Position(
				lineInfo.getOffset(),
				Math.min(lineInfo.getLength(), 1)
			);

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error calculating range: " +
				e.getMessage());
			// Return safe default position
			return new org.eclipse.jface.text.Position(0, 1);
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
			String filePath = file.getFullPath().toOSString();
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
