package com.checkmarx.eclipse.devassist.ui.findings.hover;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jdt.ui.text.java.hover.IJavaEditorTextHover;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.MarkerAnnotation;

import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.editor.FindingsAnnotation;
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
 * hover available and selectable, but does NOT enable it by default - on a
 * fresh install the user must check "Checkmarx Finding" in that preference
 * page (and give it the "None"/combination slot to see it on a plain mouse
 * hover with no modifier key). Until then, hovering shows Eclipse's default
 * combination annotation hover instead (plain, unstyled marker text).
 */
public class CheckmarxAnnotationHover implements IJavaEditorTextHover, ITextHoverExtension2, ITextHoverExtension {

    /**
     * Minimum size the popup is forced to regardless of what
     * AbstractInformationControlManager tries to impose. The manager
     * hardcodes a 60-characters-by-6-lines constraint for hover popups
     * (see AbstractInformationControlManager: fWidthConstraint=60,
     * fHeightConstraint=6) and pushes it into the control via
     * setSizeConstraints(w, h) right before asking the control to size
     * itself - BrowserInformationControl.computeSizeHint() then clamps its
     * own natural content size down to that constraint. Since
     * setSizeConstraints(int, int) is not final, overriding it to enlarge
     * whatever the manager passes in is the supported way to opt out of
     * that 6-line default and show the full finding immediately, without
     * requiring the user to move the mouse into the popup first - the same
     * "readable on first hover" behaviour as m2e's pom.xml dependency hover.
     */
    private static final int MIN_POPUP_WIDTH = 480;
    private static final int MIN_POPUP_HEIGHT = 400;

    /**
     * Creates the small (~6-line) preview control shown on the initial
     * mouse hover. Mirrors JDT's own AbstractAnnotationHover/JavadocHover:
     * the browser control returned here overrides
     * getInformationPresenterControlCreator() to point at the enlarged
     * PresenterControlCreator below - without that override,
     * AbstractInformationControlManager.canReplace() always returns false,
     * so moving the mouse toward the popup can never "enrich" it into the
     * bigger, reachable control and the popup instead closes on the next
     * pixel of mouse movement outside the hovered line.
     * <p>
     * Must extend AbstractReusableInformationControlCreator (not a bare
     * IInformationControlCreator lambda/anonymous class) so the SAME
     * browser widget is reused across repeated hover computations -
     * otherwise AbstractInformationControlManager.getInformationControl()
     * disposes and recreates the control on every mouse-hover tick
     * (it only skips that when the creator implements
     * IInformationControlCreatorExtension, which the reusable base class
     * does), which was cutting the browser off mid-render before it could
     * finish laying out the HTML.
     */
    private static final class HoverControlCreator extends AbstractReusableInformationControlCreator {
        private final IInformationControlCreator presenterControlCreator;

        HoverControlCreator(IInformationControlCreator presenterControlCreator) {
            this.presenterControlCreator = presenterControlCreator;
        }

        @Override
        public IInformationControl doCreateInformationControl(Shell parent) {
            String tooltipAffordance = EditorsUI.getTooltipAffordanceString();
            if (BrowserInformationControl.isAvailable(parent)) {
                BrowserInformationControl control = new BrowserInformationControl(parent, JFaceResources.DIALOG_FONT, tooltipAffordance) {
                    @Override
                    public IInformationControlCreator getInformationPresenterControlCreator() {
                        return presenterControlCreator;
                    }

                    @Override
                    public void setSizeConstraints(int maxWidth, int maxHeight) {
                        super.setSizeConstraints(Math.max(maxWidth, MIN_POPUP_WIDTH), Math.max(maxHeight, MIN_POPUP_HEIGHT));
                    }
                };
                setupActionHandler(control);
                return control;
            }
            return new DefaultInformationControl(parent, tooltipAffordance) {
                @Override
                public IInformationControlCreator getInformationPresenterControlCreator() {
                    return presenterControlCreator;
                }
            };
        }

        private void setupActionHandler(BrowserInformationControl control) {
            try {
                java.lang.reflect.Field browserField = BrowserInformationControl.class.getDeclaredField("fBrowser");
                browserField.setAccessible(true);
                org.eclipse.swt.browser.Browser browser = (org.eclipse.swt.browser.Browser) browserField.get(control);
                if (browser != null && !browser.isDisposed()) {
                    CxLogger.info("[HOVER] HoverControlCreator: Setting up LocationListener for action buttons");
                    browser.addLocationListener(new LocationListener() {
                        @Override
                        public void changing(LocationEvent event) {
                            CxLogger.info("[HOVER] LocationListener.changing: " + event.location);
                            if (event.location.contains("#action:")) {
                                CxLogger.info("[HOVER] Blocking action URL: " + event.location);
                                event.doit = false;
                            }
                        }

                        @Override
                        public void changed(LocationEvent event) {
                            CxLogger.info("[HOVER] LocationListener.changed: " + event.location);
                            int actionIndex = event.location.indexOf("#action:");
                            if (actionIndex >= 0) {
                                event.doit = false;
                                String action = event.location.substring(actionIndex + 8); // +8 for "#action:"
                                CxLogger.info("[HOVER] Extracted action: " + action);
                                handleHoverAction(action);
                            }
                        }
                    });
                    CxLogger.info("[HOVER] LocationListener added successfully to HoverControlCreator");
                } else {
                    CxLogger.info("[HOVER] HoverControlCreator: Browser is null or disposed");
                }
            } catch (Exception e) {
                CxLogger.error("Failed to setup action handler for hover buttons (HoverControlCreator)", e);
            }
        }
    }

    private static void handleHoverAction(String action) {
        CxLogger.info("[HOVER] Action button clicked: " + action);
        switch (action) {
            case "fix":
                CxLogger.info("[HOVER] Fix with AI action triggered");
                break;
            case "details":
                CxLogger.info("[HOVER] View Details action triggered");
                break;
            case "ignore":
                CxLogger.info("[HOVER] Ignore action triggered");
                break;
            case "copy":
                CxLogger.info("[HOVER] Copy Details action triggered");
                break;
            default:
                CxLogger.info("[HOVER] Unknown action: " + action);
        }
    }

    /**
     * Creates the enlarged, resizable, focusable control that replaces the
     * small preview once the mouse moves toward it - this is what actually
     * lets the user read the full finding and reach the action links.
     */
    private static final class PresenterControlCreator extends AbstractReusableInformationControlCreator {
        @Override
        public IInformationControl doCreateInformationControl(Shell parent) {
            if (BrowserInformationControl.isAvailable(parent)) {
                BrowserInformationControl control = new BrowserInformationControl(parent, JFaceResources.DIALOG_FONT, true) {
                    @Override
                    public void setSizeConstraints(int maxWidth, int maxHeight) {
                        super.setSizeConstraints(Math.max(maxWidth, MIN_POPUP_WIDTH), Math.max(maxHeight, MIN_POPUP_HEIGHT));
                    }
                };
                setupActionHandler(control);
                return control;
            }
            return new DefaultInformationControl(parent, true);
        }

        private void setupActionHandler(BrowserInformationControl control) {
            try {
                java.lang.reflect.Field browserField = BrowserInformationControl.class.getDeclaredField("fBrowser");
                browserField.setAccessible(true);
                org.eclipse.swt.browser.Browser browser = (org.eclipse.swt.browser.Browser) browserField.get(control);
                if (browser != null && !browser.isDisposed()) {
                    CxLogger.info("[HOVER] PresenterControlCreator: Setting up LocationListener for action buttons");
                    browser.addLocationListener(new LocationListener() {
                        @Override
                        public void changing(LocationEvent event) {
                            CxLogger.info("[HOVER] LocationListener.changing: " + event.location);
                            if (event.location.contains("#action:")) {
                                CxLogger.info("[HOVER] Blocking action URL: " + event.location);
                                event.doit = false;
                            }
                        }

                        @Override
                        public void changed(LocationEvent event) {
                            CxLogger.info("[HOVER] LocationListener.changed: " + event.location);
                            int actionIndex = event.location.indexOf("#action:");
                            if (actionIndex >= 0) {
                                event.doit = false;
                                String action = event.location.substring(actionIndex + 8); // +8 for "#action:"
                                CxLogger.info("[HOVER] Extracted action: " + action);
                                handleHoverAction(action);
                            }
                        }
                    });
                    CxLogger.info("[HOVER] LocationListener added successfully to PresenterControlCreator");
                } else {
                    CxLogger.info("[HOVER] PresenterControlCreator: Browser is null or disposed");
                }
            } catch (Exception e) {
                CxLogger.error("Failed to setup action handler for hover buttons (PresenterControlCreator)", e);
            }
        }
    }

    private IInformationControlCreator hoverControlCreator;
    private IInformationControlCreator presenterControlCreator;

    @Override
    public void setEditor(IEditorPart editor) {
        // No editor-specific state needed: getHoverInfo2() derives everything
        // it needs from the ITextViewer/ISourceViewer passed at hover time.
    }

    /**
     * Returns the whole line as the hover's "subject area" rather than a
     * zero-width point at the cursor. JFace keeps the popup alive only while
     * the mouse stays inside this region, so a zero-width region gave the
     * mouse nowhere to go - it dismissed on the next pixel of movement,
     * before the browser control could finish laying out the full HTML and
     * before the mouse could travel toward the popup to interact with it.
     */
    @Override
    public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
        IDocument document = textViewer.getDocument();
        if (document != null) {
            try {
                return document.getLineInformationOfOffset(offset);
            } catch (BadLocationException e) {
                // fall through to point region below
            }
        }
        return new Region(offset, 0);
    }

    /**
     * Without this, JFace falls back to a plain-text control and the HTML
     * markup produced by getHoverInfo2()/CheckmarxProblemDescriptionFormatter
     * would either show as literal tags or be flattened to plain text - the
     * same BrowserInformationControl mechanism JDT's own Javadoc/Problem
     * hovers use to render rich HTML.
     * <p>
     * Returns a cached instance (not a fresh one per call) because
     * TextViewerHoverManager.computeInformation() calls this on every hover
     * computation and re-registers whatever it gets via
     * setCustomInformationControlCreator() - a stable,
     * AbstractReusableInformationControlCreator-based instance lets that
     * call recognize "same creator" and keep reusing the existing control
     * instead of tearing it down and rebuilding it each time.
     */
    @Override
    public IInformationControlCreator getHoverControlCreator() {
        if (hoverControlCreator == null) {
            hoverControlCreator = new HoverControlCreator(getPresenterControlCreator());
        }
        return hoverControlCreator;
    }

    private IInformationControlCreator getPresenterControlCreator() {
        if (presenterControlCreator == null) {
            presenterControlCreator = new PresenterControlCreator();
        }
        return presenterControlCreator;
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
            html.append("<html><body style='margin:0;padding:4px;font-family:Arial,sans-serif;font-size:11px;")
                .append("word-wrap:break-word;overflow-wrap:break-word;'>");

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

                // Handle FindingsAnnotation (custom Checkmarx annotations from scan decorator)
                if (annotation instanceof FindingsAnnotation) {
                    FindingsAnnotation findingsAnn = (FindingsAnnotation) annotation;
                    String title = findingsAnn.getTitle();
                    if (title != null && !title.isEmpty()) {
                        String description = findingsAnn.getDescription();
                        String sectionHtml = buildHtmlForFinding(title, description);
                        checkmarxSections.add(sectionHtml);
                        CxLogger.info("[HOVER] Found FindingsAnnotation: " + title);
                        continue;
                    }
                }

                String message = annotation.getText();
                if (message != null && !message.isEmpty()) {
                    otherMessages.add(message);
                }
            }

            CxLogger.info("[HOVER] Line " + (lineNumber + 1) + ": Found " + checkmarxSections.size()
                + " Checkmarx section(s), " + otherMessages.size() + " other message(s)");

            if (checkmarxSections.isEmpty() && otherMessages.isEmpty()) {
                CxLogger.info("[HOVER] No content to display, returning null");
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
                CxLogger.info("[HOVER] Marker " + markerId + ": Failed to extract ScanIssue from marker");
                return "";
            }
            String html = "<div>" + CheckmarxProblemDescriptionFormatter.formatDescriptionHtml(issue) + "</div>";
            CxLogger.info("[HOVER] Marker " + markerId + ": Built HTML section for issue: " + issue.getTitle());
            return html;
        } catch (Exception e) {
            CxLogger.error("CheckmarxAnnotationHover: failed to build hover content for marker " + markerId, e);
            return "";
        }
    }

    private String buildHtmlForFinding(String title, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='padding:4px;'>");

        // Title
        sb.append("<b style='color:#e84c3d;font-size:12px;'>")
          .append(HtmlEscapeUtil.escape(title))
          .append("</b>");
        sb.append("<br/>");

        // Description
        if (description != null && !description.isEmpty()) {
            sb.append("<div style='margin:4px 0;color:#333;font-size:11px;'>")
              .append(HtmlEscapeUtil.escape(description))
              .append("</div>");
        }

        // Action links (clickable) - use fragment identifier that LocationListener can intercept
        sb.append("<div style='margin-top:6px;border-top:1px solid #ddd;padding-top:4px;font-size:10px;'>");
        sb.append("<a href='#action:fix' style='color:#0066cc;text-decoration:underline;cursor:pointer;margin-right:8px;'>Fix</a>");
        sb.append("<a href='#action:details' style='color:#0066cc;text-decoration:underline;cursor:pointer;margin-right:8px;'>Details</a>");
        sb.append("<a href='#action:ignore' style='color:#0066cc;text-decoration:underline;cursor:pointer;margin-right:8px;'>Ignore</a>");
        sb.append("<a href='#action:copy' style='color:#0066cc;text-decoration:underline;cursor:pointer;'>Copy</a>");
        sb.append("</div>");

        sb.append("</div>");
        return sb.toString();
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
