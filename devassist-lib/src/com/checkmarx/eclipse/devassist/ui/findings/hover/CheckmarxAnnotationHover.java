package com.checkmarx.eclipse.devassist.ui.findings.hover;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jdt.ui.text.java.hover.IJavaEditorTextHover;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.MarkerAnnotation;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper;
import com.checkmarx.eclipse.devassist.utils.HtmlEscapeUtil;

/**
 * Line hover for Checkmarx findings, contributed to the JDT Java editor via
 * org.eclipse.jdt.ui.javaEditorTextHovers (the only public Eclipse extension
 * point for adding a hover to an editor this plugin does not own). Renders
 * the Checkmarx problem description as HTML, and appends the text of any
 * OTHER annotations already present on the same line (JDT compiler errors,
 * other linters, etc.) so hovering never hides existing information for the
 * line - it only adds to it. This mirrors how JetBrains merges multiple
 * inspection results (HighlightInfo entries) into a single hover popup.
 * <p>
 * Must implement IJavaEditorTextHover (not just ITextHover) because JDT's
 * hover framework (JavaEditorTextHoverDescriptor.createTextHover()) casts
 * contributed hover classes to IJavaEditorTextHover.
 * <p>
 * NOTE: JDT gates which contributed hovers are actually active via user
 * preferences (Preferences > Java > Editor > Hovers, keyed by this hover's
 * id and a modifier-key/state-mask). Registering the extension makes this
 * hover available and selectable, but does not by itself guarantee it is
 * enabled by default for every user/installation - this should be verified
 * in a running Eclipse instance.
 */
public class CheckmarxAnnotationHover implements IJavaEditorTextHover, ITextHoverExtension2 {

    @Override
    public void setEditor(IEditorPart editor) {
        // No editor-specific state needed: getHoverInfo2() derives everything
        // it needs from the ITextViewer/ISourceViewer passed at hover time.
    }

    @Override
    public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
        return new Region(offset, 0);
    }

    @Override
    public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
        Object info = getHoverInfo2(textViewer, hoverRegion);
        return info != null ? info.toString() : null;
    }

    @Override
    public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
        long startTime = System.currentTimeMillis();
        try {
            if (!(textViewer instanceof ISourceViewer)) {
                return null;
            }
            ISourceViewer sourceViewer = (ISourceViewer) textViewer;
            IAnnotationModel model = sourceViewer.getAnnotationModel();
            IDocument document = sourceViewer.getDocument();
            if (model == null || document == null) {
                return null;
            }

            int lineNumber;
            try {
                lineNumber = document.getLineOfOffset(hoverRegion.getOffset());
            } catch (Exception e) {
                CxLogger.error("CheckmarxAnnotationHover: failed to get line number", e);
                return null;
            }

            StringBuilder html = new StringBuilder();
            html.append("<html><body style='margin:0;padding:4px;font-family:Arial,sans-serif;font-size:11px;'>");

            Set<Long> seenMarkerIds = new HashSet<>();
            List<String> checkmarxSections = new ArrayList<>();
            List<String> otherMessages = new ArrayList<>();

            Iterator<Annotation> it = model.getAnnotationIterator();
            while (it.hasNext()) {
                Annotation annotation = it.next();
                if (annotation == null || annotation.isMarkedDeleted()) {
                    continue;
                }

                Position position = null;
                try {
                    position = model.getPosition(annotation);
                } catch (Exception e) {
                    continue;
                }

                if (position == null || !isOnLine(document, position, lineNumber)) {
                    continue;
                }

                if (annotation instanceof MarkerAnnotation) {
                    MarkerAnnotation markerAnnotation = (MarkerAnnotation) annotation;
                    IMarker marker = markerAnnotation.getMarker();
                    if (isCheckmarxMarker(marker)) {
                        Long id = marker.getId();
                        if (!seenMarkerIds.contains(id)) {
                            seenMarkerIds.add(id);
                            String section = buildCheckmarxSection(marker, id);
                            if (!section.isEmpty()) {
                                checkmarxSections.add(section);
                            }
                        }
                        continue;
                    }
                }

                String message = annotation.getText();
                if (message != null && !message.isEmpty()) {
                    otherMessages.add(message);
                }
            }

            if (checkmarxSections.isEmpty() && otherMessages.isEmpty()) {
                return null;
            }

            for (int i = 0; i < checkmarxSections.size(); i++) {
                if (i > 0) {
                    html.append("<hr style='margin:4px 0;border:none;border-top:1px solid #ccc;'/>");
                }
                html.append(checkmarxSections.get(i));
            }

            if (!otherMessages.isEmpty()) {
                if (!checkmarxSections.isEmpty()) {
                    html.append("<hr style='margin:4px 0;border:none;border-top:1px solid #ccc;'/>");
                }
                for (String message : otherMessages) {
                    html.append("<div style='color:#666;'>").append(HtmlEscapeUtil.escape(message)).append("</div>");
                }
            }

            html.append("</body></html>");
            return html.toString();
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > 100) {
                CxLogger.info("CheckmarxAnnotationHover.getHoverInfo2() took " + elapsed + "ms");
            }
        }
    }

    private String buildCheckmarxSection(IMarker marker, Long markerId) {
        try {
            ScanIssue issue = MarkerIssueMapper.fromMarker(marker);
            if (issue == null) {
                return "";
            }
            return "<div>" + CheckmarxProblemDescriptionFormatter.formatDescriptionHtml(issue) + "</div>";
        } catch (Exception e) {
            CxLogger.error("CheckmarxAnnotationHover: failed to build hover content for marker " + markerId, e);
            return "";
        }
    }

    private boolean isCheckmarxMarker(IMarker marker) {
        try {
            return marker != null && marker.exists()
                    && marker.isSubtypeOf("com.checkmarx.eclipse.plugin.checkmarxProblemMarker");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isOnLine(IDocument document, Position position, int lineNumber) {
        try {
            int startLine = document.getLineOfOffset(position.getOffset());
            int endLine = document.getLineOfOffset(position.getOffset() + Math.max(position.getLength() - 1, 0));
            return lineNumber >= startLine && lineNumber <= endLine;
        } catch (Exception e) {
            return false;
        }
    }
}
