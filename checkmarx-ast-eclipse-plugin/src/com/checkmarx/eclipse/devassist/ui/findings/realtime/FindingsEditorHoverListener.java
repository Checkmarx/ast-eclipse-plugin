package com.checkmarx.eclipse.devassist.ui.findings.realtime;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.jface.text.source.ISourceViewer;

import com.checkmarx.eclipse.devassist.ui.findings.editor.CxFindingsHover;

/**
 * Listens for editor open/close events and installs custom hover handlers for Findings.
 *
 * This listener handles dynamic installation of hovers on editors that open during
 * the session. It installs CxFindingsHover which provides rich vulnerability details
 * when hovering over underlined code with FindingsAnnotation.
 *
 * Works independently from Eclipse's native Problems View.
 */
public class FindingsEditorHoverListener implements IPartListener2 {

	private static final CxFindingsHover findingsHover = new CxFindingsHover();

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
	 * Install Checkmarx Findings hover handler on the given editor if it has a text viewer.
	 *
	 * Installs CxFindingsHover which finds FindingsAnnotations at the hover offset
	 * and displays detailed vulnerability information.
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

				// Register findings hover for Java code and JavaDoc content types
				sourceViewer.setTextHover(findingsHover, "org.eclipse.jdt.ui.javaCode");
				sourceViewer.setTextHover(findingsHover, "org.eclipse.jdt.ui.javaDocCode");

				System.out.println("[FINDINGS-HOVER] ✓ Installed hover on editor");
			}
		} catch (Exception e) {
			System.err.println("[FINDINGS-HOVER] Error installing hover: " + e.getMessage());
		}
	}
}
