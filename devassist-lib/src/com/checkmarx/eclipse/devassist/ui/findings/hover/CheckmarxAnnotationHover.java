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
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.remediation.RemediationLinkHandler;
import com.checkmarx.eclipse.devassist.ui.findings.editor.FindingsAnnotation;
import com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;
import com.checkmarx.eclipse.devassist.utils.HtmlEscapeUtil;
import org.eclipse.jface.resource.JFaceColors;

/**
 * Line hover for Checkmarx findings, contributed to the JDT Java editor via
 * org.eclipse.jdt.ui.javaEditorTextHovers (the only public Eclipse extension
 * point for adding a hover to an editor this plugin does not own). Renders the
 * Checkmarx problem description as HTML, and appends the text of any OTHER
 * annotations already present on the same line (JDT compiler errors, other
 * linters, etc.) so hovering never hides existing information for the line - it
 * only adds to it. This mirrors how JetBrains merges multiple inspection
 * results (HighlightInfo entries) into a single hover popup.
 * <p>
 * Must implement IJavaEditorTextHover (not just ITextHover) because JDT's hover
 * framework (JavaEditorTextHoverDescriptor.createTextHover()) casts contributed
 * hover classes to IJavaEditorTextHover.
 * <p>
 * NOTE: JDT gates which contributed hovers are actually active via user
 * preferences (Preferences > Java > Editor > Hovers, keyed by this hover's id
 * and a modifier-key/state-mask). Registering the extension makes this hover
 * available and selectable, but does NOT enable it by default - on a fresh
 * install the user must check "Checkmarx Finding" in that preference page (and
 * give it the "None"/combination slot to see it on a plain mouse hover with no
 * modifier key). Until then, hovering shows Eclipse's default combination
 * annotation hover instead (plain, unstyled marker text).
 */
public class CheckmarxAnnotationHover implements IJavaEditorTextHover, ITextHoverExtension2, ITextHoverExtension {

	/**
	 * Minimum size the popup is forced to regardless of what
	 * AbstractInformationControlManager tries to impose. The manager hardcodes a
	 * 60-characters-by-6-lines constraint for hover popups (see
	 * AbstractInformationControlManager: fWidthConstraint=60, fHeightConstraint=6)
	 * and pushes it into the control via setSizeConstraints(w, h) right before
	 * asking the control to size itself -
	 * BrowserInformationControl.computeSizeHint() then clamps its own natural
	 * content size down to that constraint. Since setSizeConstraints(int, int) is
	 * not final, overriding it to enlarge whatever the manager passes in is the
	 * supported way to opt out of that 6-line default and show the full finding
	 * immediately, without requiring the user to move the mouse into the popup
	 * first - the same "readable on first hover" behaviour as m2e's pom.xml
	 * dependency hover.
	 */
	private static final int MIN_POPUP_WIDTH = 580;
	private static final int MIN_POPUP_HEIGHT = 300;

	private static final CheckmarxProblemDescriptionFormatter PROBLEM_DESCRIPTRO = new CheckmarxProblemDescriptionFormatter();

	/**
	 * Creates the small (~6-line) preview control shown on the initial mouse hover.
	 * Mirrors JDT's own AbstractAnnotationHover/JavadocHover: the browser control
	 * returned here overrides getInformationPresenterControlCreator() to point at
	 * the enlarged PresenterControlCreator below - without that override,
	 * AbstractInformationControlManager.canReplace() always returns false, so
	 * moving the mouse toward the popup can never "enrich" it into the bigger,
	 * reachable control and the popup instead closes on the next pixel of mouse
	 * movement outside the hovered line.
	 * <p>
	 * Must extend AbstractReusableInformationControlCreator (not a bare
	 * IInformationControlCreator lambda/anonymous class) so the SAME browser widget
	 * is reused across repeated hover computations - otherwise
	 * AbstractInformationControlManager.getInformationControl() disposes and
	 * recreates the control on every mouse-hover tick (it only skips that when the
	 * creator implements IInformationControlCreatorExtension, which the reusable
	 * base class does), which was cutting the browser off mid-render before it
	 * could finish laying out the HTML.
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
				BrowserInformationControl control = new BrowserInformationControl(parent, JFaceResources.DIALOG_FONT,
						tooltipAffordance) {
					@Override
					public IInformationControlCreator getInformationPresenterControlCreator() {
						return presenterControlCreator;
					}

					@Override
					public void setSizeConstraints(int maxWidth, int maxHeight) {
						super.setSizeConstraints(Math.max(maxWidth, MIN_POPUP_WIDTH),
								Math.max(maxHeight, MIN_POPUP_HEIGHT));
					}
				};
				control.setBackgroundColor(
				        JFaceColors.getInformationViewerBackgroundColor(parent.getDisplay()));

				control.setForegroundColor(
				        JFaceColors.getInformationViewerForegroundColor(parent.getDisplay()));
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
							if (event.location.contains("#cxonedevassist/")) {
								CxLogger.info("[HOVER] Blocking remediation action URL: " + event.location);
								event.doit = false;
							}
						}

						@Override
						public void changed(LocationEvent event) {
							CxLogger.info("[HOVER] LocationListener.changed: " + event.location);
							int actionIndex = event.location.indexOf("#cxonedevassist/");
							if (actionIndex >= 0) {
								event.doit = false;
								String linkData = event.location.substring(actionIndex + 16); // +16 for "#cxonedevassist/"
								CxLogger.info("[HOVER] Extracted link data: " + linkData);
								handleHoverAction(linkData);
							}
						}
					});
					browser.addProgressListener(new ProgressListener() {
						@Override
						public void changed(ProgressEvent event) {
							// no-op: only the final completed() matters here
						}

						@Override
						public void completed(ProgressEvent event) {
							if (!browser.isDisposed()) {
								control.setSize(MIN_POPUP_WIDTH, MIN_POPUP_HEIGHT);
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

		if (currentFinding == null) {
			CxLogger.info("[HOVER] No finding context available for action: " + action);
			return;
		}

		RemediationLinkHandler linkHandler = new RemediationLinkHandler();
		boolean handled = linkHandler.handleLink(action, currentFinding);

		if (!handled) {
			CxLogger.info("[HOVER] Unknown or unhandled action: " + action);
		}
	}

	/**
	 * Creates the enlarged, resizable, focusable control that replaces the small
	 * preview once the mouse moves toward it - this is what actually lets the user
	 * read the full finding and reach the action links.
	 */
	private static final class PresenterControlCreator extends AbstractReusableInformationControlCreator {
		@Override
		public IInformationControl doCreateInformationControl(Shell parent) {
			if (BrowserInformationControl.isAvailable(parent)) {
				BrowserInformationControl control = new BrowserInformationControl(parent, JFaceResources.DIALOG_FONT,
						true) {
					
					@Override
					public void setSizeConstraints(int maxWidth, int maxHeight) {
						super.setSizeConstraints(Math.max(maxWidth, MIN_POPUP_WIDTH),
								Math.max(maxHeight, MIN_POPUP_HEIGHT));
					}
				};
				control.setBackgroundColor(
				        JFaceColors.getInformationViewerBackgroundColor(parent.getDisplay()));

				control.setForegroundColor(
				        JFaceColors.getInformationViewerForegroundColor(parent.getDisplay()));
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
							if (event.location.contains("#cxonedevassist/")) {
								CxLogger.info("[HOVER] Blocking remediation action URL: " + event.location);
								event.doit = false;
							}
						}

						@Override
						public void changed(LocationEvent event) {
							CxLogger.info("[HOVER] LocationListener.changed: " + event.location);
							int actionIndex = event.location.indexOf("#cxonedevassist/");
							if (actionIndex >= 0) {
								event.doit = false;
								String linkData = event.location.substring(actionIndex + 16); // +16 for "#cxonedevassist/"
								CxLogger.info("[HOVER] Extracted link data: " + linkData);
								handleHoverAction(linkData);
							}
						}
					});
					browser.addProgressListener(new ProgressListener() {
						@Override
						public void changed(ProgressEvent event) {
							// no-op: only the final completed() matters here
						}

						@Override
						public void completed(ProgressEvent event) {
							if (!browser.isDisposed()) {
								control.setSize(MIN_POPUP_WIDTH, MIN_POPUP_HEIGHT);
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
	private static ScanIssue currentFinding;

	@Override
	public void setEditor(IEditorPart editor) {
		// No editor-specific state needed: getHoverInfo2() derives everything
		// it needs from the ITextViewer/ISourceViewer passed at hover time.
	}

	/**
	 * Returns the whole line as the hover's "subject area" rather than a zero-width
	 * point at the cursor. JFace keeps the popup alive only while the mouse stays
	 * inside this region, so a zero-width region gave the mouse nowhere to go - it
	 * dismissed on the next pixel of movement, before the browser control could
	 * finish laying out the full HTML and before the mouse could travel toward the
	 * popup to interact with it.
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
	 * Without this, JFace falls back to a plain-text control and the HTML markup
	 * produced by getHoverInfo2()/CheckmarxProblemDescriptionFormatter would either
	 * show as literal tags or be flattened to plain text - the same
	 * BrowserInformationControl mechanism JDT's own Javadoc/Problem hovers use to
	 * render rich HTML.
	 * <p>
	 * Returns a cached instance (not a fresh one per call) because
	 * TextViewerHoverManager.computeInformation() calls this on every hover
	 * computation and re-registers whatever it gets via
	 * setCustomInformationControlCreator() - a stable,
	 * AbstractReusableInformationControlCreator-based instance lets that call
	 * recognize "same creator" and keep reusing the existing control instead of
	 * tearing it down and rebuilding it each time.
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
//			html.append("<html><body style='margin:0;padding:4px;font-family:Arial,sans-serif;font-size:11px;")
//			.append("word-wrap:break-word;overflow-wrap:break-word;'>");
			
			String backgroundColor = getHoverBackgroundColorHex();
			String foregroundColor = getHoverForegroundColorHex();

			html.append("<html>")
			    .append("<head>")
			    .append("<style>")
			    .append("html, body {")
			    .append("background-color:").append(backgroundColor).append(";")
			    .append("color:").append(foregroundColor).append(";")
			    .append("}")
			    .append("</style>")
			    .append("</head>")
			    .append("<body style='")
			    .append("margin:0;")
			    .append("padding:4px;")
			    .append("font-family:Arial,sans-serif;")
			    .append("font-size:11px;")
			    .append("word-wrap:break-word;")
			    .append("overflow-wrap:break-word;'>");

			// Determine text color for dynamic elements in the formatter
			// (e.g., ASCA/IAC vulnerability titles). This is done on the UI thread,
			// so it's safe to use from the formatter via parameter passing.
			String textColorForElements = getTextColorForTheme();

			Set<Long> seenMarkerIds = new HashSet<>();
			// Tracks scanIssueIds already rendered via a FindingsAnnotation (the live,
			// fully-populated ScanIssue) so a MarkerAnnotation for the SAME issue - which
			// Eclipse creates the moment a finding is clicked in the Findings view, and
			// which coexists indefinitely alongside the FindingsAnnotation in the same
			// annotation model - doesn't render the finding a second time with only its
			// root title/description (MarkerIssueMapper's marker-attribute reconstruction
			// is inherently lossier than the live object).
			Set<String> renderedIssueIds = new HashSet<>();
			// Fallback dedup key for issues without scanIssueId (e.g., OSS): title+line.
			// Mirrors MarkerIssueMapper.findMarker()'s line+title heuristic for issues
			// without a stable scanIssueId.
			Set<String> renderedIssueKeys = new HashSet<>();
			List<String> checkmarxSections = new ArrayList<>();
			List<String> otherMessages = new ArrayList<>();

			List<Annotation> lineAnnotations = new ArrayList<>();
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

				lineAnnotations.add(annotation);
			}

			// Pass 1: FindingsAnnotation first - it carries the live ScanIssue (full
			// vulnerabilities list intact), so it takes priority over any MarkerAnnotation
			// reconstruction of the same underlying issue.
			for (Annotation annotation : lineAnnotations) {
				if (!(annotation instanceof FindingsAnnotation)) {
					continue;
				}
				FindingsAnnotation findingsAnn = (FindingsAnnotation) annotation;
				ScanIssue scanIssue = findingsAnn.getScanIssue();
				if (scanIssue == null) {
					continue;
				}
				currentFinding = scanIssue;
				CxLogger.info("[HOVER] Captured ScanIssue for action handlers: " + scanIssue.getTitle());

				if (scanIssue.getScanIssueId() != null && !scanIssue.getScanIssueId().isEmpty()) {
					renderedIssueIds.add(scanIssue.getScanIssueId());
					CxLogger.warning("[HOVER] Pass 1 - Added to renderedIssueIds: " + scanIssue.getScanIssueId());
				} else {
					// Fallback dedup key for issues without scanIssueId.
					// Use enhanced key that includes engine-specific identifiers (e.g., package@version for OSS)
					String enhancedKey = getEnhancedFallbackKey(scanIssue);
					String fallbackKey = buildFallbackDedupKey(enhancedKey, lineNumber);
					renderedIssueKeys.add(fallbackKey);
					CxLogger.warning("[HOVER] Pass 1 - Added to renderedIssueKeys: " + fallbackKey);
				}

				// Use consolidated formatter for both ASCA/IAC (iterates vulnerabilities)
				// and other engines (uses root ScanIssue attributes)
				try {
					String sectionHtml = PROBLEM_DESCRIPTRO.formatDescriptionHtml(scanIssue, true, textColorForElements);
					if (!sectionHtml.isEmpty()) {
						checkmarxSections.add("<div>" + sectionHtml + "</div>");
						ScanEngine engine = scanIssue.getScanEngine();
						String engineName = (engine != null) ? engine.toString() : "UNKNOWN";
						CxLogger.info("[HOVER] " + engineName + ": Rendered ScanIssue via formatter - "
								+ scanIssue.getTitle());
					}
				} catch (Exception e) {
					CxLogger.error("[HOVER] Error formatting FindingsAnnotation: " + e.getMessage(), e);
				}
			}

			// Pass 2: MarkerAnnotation (Checkmarx markers) and everything else (JDT/other
			// linters). A Checkmarx marker is skipped here if its issue was already
			// rendered in pass 1 above.
//			for (Annotation annotation : lineAnnotations) {
//				if (annotation instanceof FindingsAnnotation
//						&& ((FindingsAnnotation) annotation).getScanIssue() != null) {
//					continue; // already handled in pass 1
//				}
//
//				if (annotation instanceof MarkerAnnotation) {
//					MarkerAnnotation markerAnnotation = (MarkerAnnotation) annotation;
//					IMarker marker = markerAnnotation.getMarker();
//					if (isCheckmarxMarker(marker)) {
//						Long id = marker.getId();
//						if (seenMarkerIds.contains(id)) {
//							continue;
//						}
//						seenMarkerIds.add(id);
//
//						String issueId = MarkerIssueMapper.getIssueId(marker);
//						// Check primary dedup key (issueId) first
//						if (!issueId.isEmpty() && renderedIssueIds.contains(issueId)) {
//							CxLogger.info("[HOVER] Skipping marker " + id + ": issue " + issueId
//									+ " already rendered via FindingsAnnotation");
//							continue;
//						}
//
//						// Check enhanced fallback dedup key for issues without stable issueId.
//						// Extract title and version from marker message for robust matching.
//						String markerMessage = marker.getAttribute(IMarker.MESSAGE, "");
//						String markerTitle = extractTitleFromMarkerMessage(markerMessage);
//						String markerVersion = extractVersionFromMarkerMessage(markerMessage);
//
//						// Build enhanced key matching Pass 1 logic
//						String enhancedMarkerKey = markerTitle;
//						if (!markerVersion.isEmpty()) {
//							// Include version for engines like OSS that report it
//							enhancedMarkerKey = markerTitle + "@" + markerVersion;
//						}
//
//						String fallbackKey = buildFallbackDedupKey(enhancedMarkerKey, lineNumber);
//						if (renderedIssueKeys.contains(fallbackKey)) {
//							CxLogger.info("[HOVER] Skipping marker " + id + ": issue (title=" + markerTitle
//									+ ", version=" + markerVersion + ", line=" + (lineNumber + 1)
//									+ ") already rendered via FindingsAnnotation");
//							continue;
//						}
//
//						CxLogger.warning("[HOVER] Pass 2 - Checking marker: " + fallbackKey
//								+ " | Available keys: " + renderedIssueKeys);
//
//						String section = buildCheckmarxSection(marker, id, textColorForElements);
//						if (!section.isEmpty()) {
//							checkmarxSections.add(section);
//							if (!issueId.isEmpty()) {
//								renderedIssueIds.add(issueId);
//								CxLogger.warning("[HOVER] Pass 2 - Rendered marker with ID: " + issueId);
//							} else {
//								// Track via enhanced fallback key if issueId is empty
//								renderedIssueKeys.add(fallbackKey);
//								CxLogger.warning("[HOVER] Pass 2 - Rendered marker with fallback key: " + fallbackKey);
//							}
//						}
//					} else {
//						// Non-Checkmarx marker (JDT, other linters) - collect as other message
//						String message = annotation.getText();
//						if (message != null && !message.isEmpty()) {
//							otherMessages.add(message);
//						}
//					}
//					continue;
//				}
//
//				// Collect other linter/annotation messages (JDT, etc.) that aren't handled
//				// above
//				String message = annotation.getText();
//				if (message != null && !message.isEmpty()) {
//					otherMessages.add(message);
//				}
//			}

			CxLogger.info("[HOVER] Line " + (lineNumber + 1) + ": Found " + checkmarxSections.size()
					+ " Checkmarx section(s), " + otherMessages.size() + " other message(s)");

			if (checkmarxSections.isEmpty()) {
				CxLogger.info("[HOVER] No Checkmarx findings to display, returning null");
				return null;
			}

			for (int i = 0; i < checkmarxSections.size(); i++) {
				if (i > 0) {
					html.append("<hr style='margin:4px 0;border:none;border-top:1px solid #ccc;'/>");
				}
				html.append(checkmarxSections.get(i));
			}

			if (!otherMessages.isEmpty()) {
				html.append("<hr style='margin:4px 0;border:none;border-top:1px solid #ccc;'/>");
				for (String message : otherMessages) {
					html.append("<div style='color:#666;font-size:10px;'>").append(HtmlEscapeUtil.escape(message))
							.append("</div>");
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


	private String buildCheckmarxSection(IMarker marker, Long markerId, String textColor) {
		try {
			ScanIssue issue = MarkerIssueMapper.fromMarker(marker);
			if (issue == null) {
				CxLogger.info("[HOVER] Marker " + markerId + ": Failed to extract ScanIssue from marker");
				return "";
			}
			currentFinding = issue;
			CxLogger.info("[HOVER] Captured ScanIssue for action handlers from marker: " + issue.getTitle());
			// Use consolidated formatter with clickable actions enabled (same as
			// FindingsAnnotation path)
			String html = "<div>" + PROBLEM_DESCRIPTRO.formatDescriptionHtml(issue, true, textColor) + "</div>";
			CxLogger.info("[HOVER] Marker " + markerId + ": Built HTML section for issue: " + issue.getTitle());
			return html;
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
	
	private static String getHoverBackgroundColorHex() {
	    Display display = Display.getDefault();
	    final String[] colorHex = new String[1];

	    Runnable runnable = () -> {
	        if (DevAssistUtils.isDarkTheme()) {
	            colorHex[0] = "#000000";
	        } else {
	            Color bg = display.getSystemColor(SWT.COLOR_INFO_BACKGROUND);

	            colorHex[0] = String.format("#%02x%02x%02x",
	            		bg.getRed(),
	                    bg.getGreen(),
	                    bg.getBlue());
	        }
	    };

	    if (Display.getCurrent() == display) {
	        runnable.run();
	    } else {
	        display.syncExec(runnable);
	    }

	    return colorHex[0];
	}
	private static String getHoverForegroundColorHex() {
	    Display display = Display.getDefault();

	    final String[] colorHex = new String[1];

	    Runnable runnable = () -> {
	        Color fg = JFaceColors.getInformationViewerForegroundColor(display);

	        colorHex[0] = String.format("#%02x%02x%02x",
	                fg.getRed(),
	                fg.getGreen(),
	                fg.getBlue());
	    };

	    if (Display.getCurrent() == display) {
	        runnable.run();
	    } else {
	        display.syncExec(runnable);
	    }

	    return colorHex[0];
	}

	private static String buildFallbackDedupKey(String title, int lineNumber) {
		return (title != null ? title : "") + "|" + lineNumber;
	}

	private static String getTextColorForTheme() {
		Display display = Display.getDefault();
		final String[] textColor = new String[1];

		Runnable runnable = () -> {
			if (DevAssistUtils.isDarkTheme()) {
				textColor[0] = "#FFFFFF";
			} else {
				textColor[0] = "#000000";
			}
		};

		if (Display.getCurrent() == display) {
			runnable.run();
		} else {
			display.syncExec(runnable);
		}

		return textColor[0];
	}

	/**
	 * Builds an enhanced fallback key that uniquely identifies a scan issue
	 * when no stable scanIssueId is available. Incorporates engine-specific
	 * identifiers for better deduplication.
	 *
	 * @param scanIssue the scan issue to generate a key for
	 * @return a unique identifier string for the issue
	 */
	private static String getEnhancedFallbackKey(ScanIssue scanIssue) {
		if (scanIssue == null || scanIssue.getTitle() == null) {
			return "";
		}

		ScanEngine engine = scanIssue.getScanEngine();

		// For OSS packages, include version for uniqueness
		if (engine == ScanEngine.OSS) {
			String version = scanIssue.getPackageVersion();
			if (version != null && !version.isEmpty()) {
				return scanIssue.getTitle() + "@" + version;
			}
		}

		// Default: use title only
		return scanIssue.getTitle();
	}
}
