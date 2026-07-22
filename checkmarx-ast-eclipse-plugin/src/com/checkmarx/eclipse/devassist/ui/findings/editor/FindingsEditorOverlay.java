package com.checkmarx.eclipse.devassist.ui.findings.editor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.editors.text.TextEditor;
import com.checkmarx.eclipse.devassist.ui.findings.model.Location;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;

/**
 * Manages highlighting and underlining of problematic code lines in the editor.
 * Provides visual feedback for findings by underlining vulnerable code with severity-based colors.
 *
 * Supports:
 * - Red wavy underline for CRITICAL/HIGH issues
 * - Yellow wavy underline for MEDIUM issues
 * - Blue wavy underline for LOW issues
 * - Auto-clear on navigation away
 */
public class FindingsEditorOverlay {

    // These match the annotation types defined in plugin.xml
    private static final String ANNOTATION_TYPE_CRITICAL = "com.checkmarx.eclipse.findings.critical";
    private static final String ANNOTATION_TYPE_HIGH = "com.checkmarx.eclipse.findings.high";
    private static final String ANNOTATION_TYPE_MEDIUM = "com.checkmarx.eclipse.findings.medium";
    private static final String ANNOTATION_TYPE_LOW = "com.checkmarx.eclipse.findings.low";

    /**
     * Highlight a problematic line in the editor.
     *
     * @param editor The TextEditor to highlight in
     * @param issue The scan issue containing location information
     */
    public static void highlightIssueLine(TextEditor editor, ScanIssue issue) {
        try {
            if (editor == null || issue == null || issue.getLocations() == null || issue.getLocations().isEmpty()) {
                return;
            }

            Location location = issue.getLocations().get(0);
            int lineNumber = location.getLine() - 1; // Convert to 0-based

            ISourceViewer viewer = (ISourceViewer) editor.getAdapter(ISourceViewer.class);
            if (viewer == null) {
                System.out.println("[FINDINGS-OVERLAY] Could not get source viewer from editor");
                return;
            }

            IDocument document = viewer.getDocument();
            if (document == null || lineNumber < 0 || lineNumber >= document.getNumberOfLines()) {
                System.out.println("[FINDINGS-OVERLAY] Invalid document or line number: " + lineNumber);
                return;
            }

            // Get line start and end offsets
            int lineStartOffset = document.getLineOffset(lineNumber);
            int lineLength = document.getLineLength(lineNumber);
            int lineEndOffset = lineStartOffset + lineLength;

            // Create annotation for the line
            String annotationType = getAnnotationTypeForSeverity(issue.getSeverity());
            FindingsAnnotation annotation = new FindingsAnnotation(annotationType, issue.getTitle(), issue.getDescription());
            Position position = new Position(lineStartOffset, lineEndOffset - lineStartOffset);

            // Add annotation to model
            IAnnotationModel annotationModel = viewer.getAnnotationModel();
            if (annotationModel != null) {
                annotationModel.addAnnotation(annotation, position);
                System.out.println("Annotation added");
                System.out.println("Annotation model = " + annotationModel.getClass().getName());
                System.out.println("Annotation type = " + annotation.getType());
                System.out.println("Offset = " + position.offset);
                System.out.println("Length = " + position.length);
            }
        } catch (BadLocationException e) {
            System.out.println("[FINDINGS-OVERLAY] Error highlighting line: " + e.getMessage());
        }
    }

    /**
     * Clear all findings annotations from the editor.
     */
    public static void clearHighlights(TextEditor editor) {
        try {
            if (editor == null) {
                return;
            }

            ISourceViewer viewer = (ISourceViewer) editor.getAdapter(ISourceViewer.class);
            if (viewer == null) {
                return;
            }

            IAnnotationModel annotationModel = viewer.getAnnotationModel();
            if (annotationModel == null) {
                return;
            }

            // Remove all findings annotations
            annotationModel.getAnnotationIterator().forEachRemaining(annotation -> {
                if (annotation instanceof FindingsAnnotation) {
                    annotationModel.removeAnnotation(annotation);
                }
            });

            System.out.println("[FINDINGS-OVERLAY] ✓ Cleared all findings highlights");
        } catch (Exception e) {
            System.out.println("[FINDINGS-OVERLAY] Error clearing highlights: " + e.getMessage());
        }
    }

    /**
     * Get annotation type based on severity level.
     */
    private static String getAnnotationTypeForSeverity(String severity) {
        if (severity == null) {
            return ANNOTATION_TYPE_MEDIUM;
        }

        switch (severity.toLowerCase()) {
            case "critical":
            case "high":
                return ANNOTATION_TYPE_CRITICAL;
            case "medium":
                return ANNOTATION_TYPE_MEDIUM;
            case "low":
            case "info":
                return ANNOTATION_TYPE_LOW;
            default:
                return ANNOTATION_TYPE_MEDIUM;
        }
    }
}
