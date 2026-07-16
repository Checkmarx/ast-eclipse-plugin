package com.checkmarx.eclipse.devassist.problems.hover;

import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.jdt.ui.text.java.hover.IJavaEditorTextHover;

import com.checkmarx.eclipse.devassist.problems.marker.ProblemMarkerConstants;

/**
 * Custom text hover for Checkmarx problems in Java editor.
 *
 * Provides rich vulnerability details when hovering over underlined code regions.
 * This implementation:
 * - Implements IJavaEditorTextHover (required for Java editor integration)
 * - Finds markers at the exact hover offset (precise character range checking)
 * - Returns the hover region bounded by marker CHAR_START/CHAR_END
 * - Handles multiple overlapping markers by selecting the innermost one
 * - Creates rich information controls displaying vulnerability details
 */
public class CxTextHover implements IJavaEditorTextHover, ITextHover, ITextHoverExtension, ITextHoverExtension2 {

	private ITextViewer textViewer;
	private IMarker currentMarker;
	private IEditorPart editor;

	public CxTextHover() {
		// Default constructor for Eclipse instantiation
	}

	@Override
	public void setEditor(IEditorPart editor) {
		this.editor = editor;
	}

	@Override
	public String getHoverInfo(ITextViewer viewer, IRegion hoverRegion) {
		return null;
	}

	@Override
	public Object getHoverInfo2(ITextViewer viewer, IRegion hoverRegion) {
		System.out.println("[CX-HOVER] getHoverInfo2 called. Returning marker: " + currentMarker);
		return this.currentMarker;
	}

	@Override
	public IInformationControlCreator getHoverControlCreator() {
		return new IInformationControlCreator() {
			@Override
			public IInformationControl createInformationControl(org.eclipse.swt.widgets.Shell parent) {
				System.out.println("[CX-HOVER] Creating control inside Creator wrapper...");
				if (currentMarker != null) {
					return new CxSimpleHoverControl(parent, currentMarker);
				}
				return null;
			}
		};
	}

	@Override
	public IRegion getHoverRegion(ITextViewer viewer, int offset) {
		System.out.println("[CX-HOVER] getHoverRegion called at offset: " + offset);
		try {
			// Step 1: Find marker containing this offset
			IMarker marker = findMarkerContainingOffset(viewer, offset);
			if (marker == null) {
				System.out.println("[CX-HOVER] No marker found at offset " + offset);
				return null;
			}

			// Step 2: Get the exact character range from the marker
			Integer charStart = (Integer) marker.getAttribute(IMarker.CHAR_START);
			Integer charEnd = (Integer) marker.getAttribute(IMarker.CHAR_END);

			if (charStart != null && charEnd != null && charStart <= offset && offset < charEnd) {
				this.currentMarker = marker; // Cache for getHoverInfo2()
				System.out.println("[CX-HOVER] ✓ Returning region: " + charStart + "-" + charEnd);
				return new Region(charStart, charEnd - charStart);
			}
		} catch (Exception e) {
			System.err.println("[CX-HOVER] Error in getHoverRegion: " + e.getMessage());
		}
		return null;
	}

	/**
	 * Find a Checkmarx marker whose CHAR_START/CHAR_END range contains the given offset.
	 * If multiple markers overlap, returns the innermost (smallest range).
	 *
	 * @param viewer the text viewer
	 * @param offset the character offset to check
	 * @return the marker containing offset, or null if none found
	 */
	private IMarker findMarkerContainingOffset(ITextViewer viewer, int offset) {
		try {
			if (viewer == null || viewer.getDocument() == null) {
				System.out.println("[CX-HOVER] findMarkerContainingOffset: viewer or document is null");
				return null;
			}

			// Get the file from the active editor
			IFile file = getFileFromActiveEditor();
			if (file == null) {
				System.out.println("[CX-HOVER] findMarkerContainingOffset: Could not get file from editor");
				return null;
			}

			System.out.println("[CX-HOVER] findMarkerContainingOffset: Searching markers in file: " + file.getName());

			// Try DEPTH_ZERO first (markers on file itself)
			IMarker[] markers = file.findMarkers(ProblemMarkerConstants.MARKER_TYPE, true, IResource.DEPTH_ZERO);
			System.out.println("[CX-HOVER] findMarkerContainingOffset: Found " + markers.length + " markers at DEPTH_ZERO");

			// If no markers found, try DEPTH_INFINITE (markers on child resources)
			if (markers.length == 0) {
				System.out.println("[CX-HOVER] findMarkerContainingOffset: No markers at DEPTH_ZERO, trying DEPTH_INFINITE...");
				markers = file.findMarkers(ProblemMarkerConstants.MARKER_TYPE, true, IResource.DEPTH_INFINITE);
				System.out.println("[CX-HOVER] findMarkerContainingOffset: Found " + markers.length + " markers at DEPTH_INFINITE");
			}

			IMarker bestMarker = null;
			int smallestRange = Integer.MAX_VALUE;

			// Find the marker with the smallest range containing offset (handles overlapping markers)
			for (IMarker marker : markers) {
				try {
					Integer charStart = (Integer) marker.getAttribute(IMarker.CHAR_START);
					Integer charEnd = (Integer) marker.getAttribute(IMarker.CHAR_END);
					String message = (String) marker.getAttribute(IMarker.MESSAGE);

					System.out.println("[CX-HOVER]   Marker: " + message + " range=[" + charStart + "-" + charEnd + "]");

					// Check if offset falls within this marker's range
					if (charStart != null && charEnd != null && charStart <= offset && offset < charEnd) {
						int range = charEnd - charStart;
						System.out.println("[CX-HOVER]     ✓ Offset " + offset + " is INSIDE range, size=" + range);
						if (range < smallestRange) {
							smallestRange = range;
							bestMarker = marker;
							System.out.println("[CX-HOVER]     ✓ Selected as best marker (innermost)");
						}
					} else {
						System.out.println("[CX-HOVER]     ✗ Offset " + offset + " is OUTSIDE range");
					}
				} catch (Exception e) {
					System.out.println("[CX-HOVER]   Error checking marker: " + e.getMessage());
				}
			}

			if (bestMarker != null) {
				System.out.println("[CX-HOVER] findMarkerContainingOffset: ✓ FOUND marker");
			} else {
				System.out.println("[CX-HOVER] findMarkerContainingOffset: ✗ NO marker found");
			}

			return bestMarker;
		} catch (Exception e) {
			System.err.println("[CX-HOVER] findMarkerContainingOffset: EXCEPTION - " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Get the IFile from the currently active editor.
	 */
	private IFile getFileFromActiveEditor() {
		try {
			if (this.editor != null) {
				org.eclipse.ui.IEditorInput input = this.editor.getEditorInput();
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
