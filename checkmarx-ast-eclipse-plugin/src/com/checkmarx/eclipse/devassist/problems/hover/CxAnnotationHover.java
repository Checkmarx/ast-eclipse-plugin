package com.checkmarx.eclipse.devassist.problems.hover;

import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.ui.IFileEditorInput;

import com.checkmarx.eclipse.devassist.problems.marker.ProblemMarkerConstants;

/**
 * Annotation hover for Checkmarx problem markers.
 *
 * Provides hover popups when the user hovers over marker annotations
 * (red underlines, gutter icons) in the editor. This complements CxTextHover
 * by handling annotation-specific hover requests from Eclipse's annotation system.
 *
 * This is less precise than CxTextHover (which works at character offset level)
 * but is useful for gutter icon hovers and other annotation scenarios.
 */
public class CxAnnotationHover implements IAnnotationHover, IInformationControlCreator {

	private ISourceViewer sourceViewer;
	private IMarker currentMarker;

	public CxAnnotationHover(ISourceViewer sourceViewer) {
		this.sourceViewer = sourceViewer;
	}

	@Override
	public String getHoverInfo(ISourceViewer viewer, int lineNumber) {
		try {
			IMarker marker = findCheckmarxMarkerAtLine(lineNumber);
			if (marker != null && isCheckmarxMarker(marker)) {
				this.currentMarker = marker;
				// Return non-empty string to trigger information control creation
				return " ";
			}
		} catch (Exception e) {
			// Silently ignore; hover simply won't appear
		}

		return null;
	}

	@Override
	public IInformationControl createInformationControl(Shell parent) {
		try {
			if (currentMarker != null && isCheckmarxMarker(currentMarker)) {
				return new CxHoverInformationControl(parent, currentMarker, sourceViewer);
			}
		} catch (Exception e) {
			// Silently ignore; hover simply won't appear
		}

		return null;
	}

	/**
	 * Find a Checkmarx marker at the given line number.
	 * Since annotation hover works at line level, any marker on the line is acceptable.
	 */
	private IMarker findCheckmarxMarkerAtLine(int lineNumber) {
		try {
			IFile file = getFileFromActiveEditor();
			if (file == null) {
				return null;
			}

			// Find Checkmarx markers on this file
			IMarker[] markers = file.findMarkers(ProblemMarkerConstants.MARKER_TYPE, true, IResource.DEPTH_ZERO);

			// Return first Checkmarx marker at this line
			for (IMarker marker : markers) {
				try {
					Integer markerLine = (Integer) marker.getAttribute(IMarker.LINE_NUMBER);
					if (markerLine != null && markerLine == lineNumber) {
						return marker;
					}
				} catch (Exception e) {
					// Skip markers with missing attributes
				}
			}
		} catch (Exception e) {
			// Silently ignore; hover simply won't appear
		}

		return null;
	}

	/**
	 * Check if a marker is a Checkmarx problem marker.
	 */
	private boolean isCheckmarxMarker(IMarker marker) {
		try {
			return marker.exists() && marker.isSubtypeOf(ProblemMarkerConstants.MARKER_TYPE);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Get the IFile from the currently active editor.
	 */
	private IFile getFileFromActiveEditor() {
		try {
			org.eclipse.ui.IEditorPart editor = getActiveEditor();
			if (editor != null) {
				org.eclipse.ui.IEditorInput input = editor.getEditorInput();
				if (input instanceof IFileEditorInput) {
					return ((IFileEditorInput) input).getFile();
				}
			}
		} catch (Exception e) {
			// Silently ignore
		}
		return null;
	}

	/**
	 * Get the currently active editor in the workbench.
	 */
	private org.eclipse.ui.IEditorPart getActiveEditor() {
		try {
			org.eclipse.ui.IWorkbenchWindow window = org.eclipse.ui.PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			if (window != null) {
				org.eclipse.ui.IWorkbenchPage page = window.getActivePage();
				if (page != null) {
					return page.getActiveEditor();
				}
			}
		} catch (Exception e) {
			// Silently ignore
		}
		return null;
	}
}
