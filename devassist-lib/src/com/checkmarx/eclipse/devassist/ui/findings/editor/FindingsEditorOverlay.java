package com.checkmarx.eclipse.devassist.ui.findings.editor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.editors.text.TextEditor;
import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanIssue;

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
    private static final String ANNOTATION_TYPE_MALICIOUS = "com.checkmarx.eclipse.findings.malicious";
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
                
                return;
            }

            IDocument document = viewer.getDocument();
            if (document == null || lineNumber < 0 || lineNumber >= document.getNumberOfLines()) {
                
                return;
            }

            // Get line start and end offsets
            int lineStartOffset = document.getLineOffset(lineNumber);
            int lineLength = document.getLineLength(lineNumber);
            int lineEndOffset = lineStartOffset + lineLength;

            // Create annotation for the line
            String annotationType = getAnnotationTypeForSeverity(issue.getSeverity());
            FindingsAnnotation annotation = new FindingsAnnotation(annotationType, issue.getTitle(), issue.getDescription(), issue);
            Position position = new Position(lineStartOffset, lineEndOffset - lineStartOffset);

            // Add annotation to model
            IAnnotationModel annotationModel = viewer.getAnnotationModel();
            if (annotationModel != null) {
                annotationModel.addAnnotation(annotation, position);
                
                
                
                
                
            }
        } catch (BadLocationException e) {
            
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

            
        } catch (Exception e) {
            
        }
    }

    /**
     * Get annotation type based on severity level.
     * Maps all problem severities to their corresponding annotation types.
     */
    private static String getAnnotationTypeForSeverity(String severity) {
        if (severity == null) {
            return ANNOTATION_TYPE_MEDIUM;
        }

        switch (severity.toLowerCase()) {
            case "malicious":
                return ANNOTATION_TYPE_MALICIOUS;
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

