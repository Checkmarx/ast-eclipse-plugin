package com.checkmarx.eclipse.devassist.problems.hover;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.ISourceViewer;

/**
 * Listens for editor open/close events and installs custom hover handlers on text editors.
 *
 * This listener is more reliable than extension points and handles dynamic installation
 * of hovers on editors that open during the session. It installs:
 * - CxTextHover: Precise character-offset-based hover for finding vulnerabilities at cursor
 * - CxAnnotationHover: Line-level hover for marker annotations (gutter icons, underlines)
 *
 * Both hovers display rich vulnerability details via CxHoverInformationControl.
 */
public class JavaEditorHoverListener implements IPartListener2 {

	private static final CxTextHover textHover = new CxTextHover();

	@Override
	public void partOpened(IWorkbenchPartReference partRef) {
		Object part = partRef.getPart(false);
		if (part instanceof IEditorPart) {
			installHoverOnEditor((IEditorPart) part);
		}
	}

	@Override
	public void partActivated(IWorkbenchPartReference partRef) {
		// Install on activation too, in case it wasn't installed earlier
		Object part = partRef.getPart(false);
		if (part instanceof IEditorPart) {
			installHoverOnEditor((IEditorPart) part);
		}
	}

	@Override
	public void partBroughtToTop(IWorkbenchPartReference partRef) {}

	@Override
	public void partClosed(IWorkbenchPartReference partRef) {}

	@Override
	public void partDeactivated(IWorkbenchPartReference partRef) {}

	@Override
	public void partHidden(IWorkbenchPartReference partRef) {}

	@Override
	public void partVisible(IWorkbenchPartReference partRef) {}

	@Override
	public void partInputChanged(IWorkbenchPartReference partRef) {}

	/**
	 * Install Checkmarx hover handlers on the given editor if it has a text viewer.
	 *
	 * Installs:
	 * - Text hover (primary): Precise character-offset detection
	 * - Annotation hover (optional): Line-level detection for gutter icons
	 *
	 * If annotation hover causes conflicts with other hovers, set
	 * the system property "checkmarx.hover.skipAnnotationHover" to "true"
	 * to disable it while keeping text hover functional.
	 */
	public void installHoverOnEditor(IEditorPart editor) {
		try {
			if (editor == null) {
				return;
			}

			// Adapt editor to ISourceViewer to check if it's a text editor
			Object viewer = null;
			try {
				viewer = editor.getAdapter(ISourceViewer.class);
			} catch (Exception e) {
				// Not a text editor; skip
				return;
			}

			if (viewer instanceof ISourceViewer) {
				ISourceViewer sourceViewer = (ISourceViewer) viewer;

				// Register text hover for Java code and JavaDoc content types (primary hover)
				sourceViewer.setTextHover(textHover, "org.eclipse.jdt.ui.javaCode");
				sourceViewer.setTextHover(textHover, "org.eclipse.jdt.ui.javaDocCode");

				// Register annotation hover only if not disabled (can cause conflicts with other hovers)
				boolean skipAnnotationHover = "true".equalsIgnoreCase(
					System.getProperty("checkmarx.hover.skipAnnotationHover", "false")
				);

				if (!skipAnnotationHover) {
					try {
						CxAnnotationHover annotationHover = new CxAnnotationHover(sourceViewer);
						sourceViewer.setAnnotationHover(annotationHover);
					} catch (Exception e) {
						// Annotation hover registration failed; continue with text hover only
					}
				}
			}
		} catch (Exception e) {
			// Silently ignore installation errors
		}
	}
}
