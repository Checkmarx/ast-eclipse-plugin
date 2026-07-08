package com.checkmarx.eclipse.views.problems.hover;

import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.ui.IFileEditorInput;

import com.checkmarx.eclipse.views.problems.marker.ProblemMarkerConstants;

/**
 * Custom text hover for Checkmarx problems in Java editor.
 *
 * Provides rich vulnerability details when hovering over underlined code regions.
 * This implementation:
 * - Finds markers at the exact hover offset (precise character range checking)
 * - Returns the hover region bounded by marker CHAR_START/CHAR_END
 * - Handles multiple overlapping markers by selecting the innermost one
 * - Creates rich information controls displaying vulnerability details
 */
public class CxTextHover implements ITextHover, IInformationControlCreator {

	private ITextViewer textViewer;
	private IMarker currentMarker;

	public CxTextHover() {
		// Default constructor for Eclipse instantiation
	}

	@Override
	public String getHoverInfo(ITextViewer viewer, IRegion hoverRegion) {
		// Return non-empty string to trigger information control creator.
		// The actual content is rendered by createInformationControl().
		return " ";
	}

	@Override
	public IRegion getHoverRegion(ITextViewer viewer, int offset) {
		this.textViewer = viewer;
		this.currentMarker = null;

		try {
			System.out.println("[CX-HOVER] getHoverRegion: offset=" + offset);
			IMarker marker = findMarkerContainingOffset(viewer, offset);

			if (marker == null) {
				System.out.println("[CX-HOVER] getHoverRegion: No marker found at offset " + offset);
				return null;
			}

			if (!isCheckmarxMarker(marker)) {
				System.out.println("[CX-HOVER] getHoverRegion: Marker found but NOT Checkmarx type");
				return null;
			}

			System.out.println("[CX-HOVER] getHoverRegion: ✓ Found Checkmarx marker");
			this.currentMarker = marker;

			// Return region bounded by marker's CHAR_START/CHAR_END for precise hovering
			Integer charStart = (Integer) marker.getAttribute(IMarker.CHAR_START);
			Integer charEnd = (Integer) marker.getAttribute(IMarker.CHAR_END);

			if (charStart != null && charEnd != null && charStart <= charEnd) {
				System.out.println("[CX-HOVER] getHoverRegion: Returning region " + charStart + "-" + charEnd);
				return new Region(charStart, charEnd - charStart);
			}

			// Fallback: return minimal region at offset if char positions not available
			System.out.println("[CX-HOVER] getHoverRegion: Using fallback region (missing char range)");
			return new Region(offset, 1);
		} catch (Exception e) {
			System.err.println("[CX-HOVER] getHoverRegion: EXCEPTION - " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public IInformationControl createInformationControl(org.eclipse.swt.widgets.Shell parent) {
		try {
			if (currentMarker == null) {
				System.out.println("[CX-HOVER] createInformationControl: currentMarker is NULL");
				return null;
			}

			if (!isCheckmarxMarker(currentMarker)) {
				System.out.println("[CX-HOVER] createInformationControl: marker is NOT Checkmarx type");
				return null;
			}

			System.out.println("[CX-HOVER] createInformationControl: Creating CxSimpleHoverControl (robust SWT-based)...");
			IInformationControl control = new CxSimpleHoverControl(parent, currentMarker);
			System.out.println("[CX-HOVER] createInformationControl: ✓ Control created successfully");
			return control;
		} catch (Exception e) {
			System.err.println("[CX-HOVER] createInformationControl: EXCEPTION - " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace();
			return null;
		}
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

			// Find all Checkmarx markers in this file
			IMarker[] markers = file.findMarkers(ProblemMarkerConstants.MARKER_TYPE, true, IResource.DEPTH_ZERO);
			System.out.println("[CX-HOVER] findMarkerContainingOffset: Found " + markers.length + " total markers");

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
