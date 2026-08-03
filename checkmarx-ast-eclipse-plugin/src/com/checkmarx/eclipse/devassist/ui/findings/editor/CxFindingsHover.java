//package com.checkmarx.eclipse.devassist.ui.findings.editor;
//
//import org.eclipse.jface.text.IInformationControl;
//import org.eclipse.jface.text.IInformationControlCreator;
//import org.eclipse.jface.text.IRegion;
//import org.eclipse.jface.text.ITextHover;
//import org.eclipse.jface.text.ITextHoverExtension;
//import org.eclipse.jface.text.ITextHoverExtension2;
//import org.eclipse.jface.text.ITextViewer;
//import org.eclipse.jface.text.Region;
//import org.eclipse.jface.text.source.Annotation;
//import org.eclipse.jface.text.source.IAnnotationModel;
//import org.eclipse.ui.IEditorPart;
//import org.eclipse.jdt.ui.text.java.hover.IJavaEditorTextHover;
//
///**
// * Custom hover for Checkmarx Findings annotations in the editor.
// *
// * Finds FindingsAnnotation objects at the hover offset and displays
// * detailed vulnerability information via CxFindingsHoverControl.
// *
// * Works independently of Eclipse markers - uses ScanIssue annotation model.
// */
//public class CxFindingsHover implements IJavaEditorTextHover, ITextHover, ITextHoverExtension, ITextHoverExtension2 {
//
//	private FindingsAnnotation currentAnnotation;
//	public CxFindingsHover() {
//		// Default constructor for Eclipse instantiation
//	}
//
//	@Override
//	public void setEditor(IEditorPart editor) {
//	}
//
//	@Override
//	public String getHoverInfo(ITextViewer viewer, IRegion hoverRegion) {
//		return null;
//	}
//
//	@Override
//	public Object getHoverInfo2(ITextViewer viewer, IRegion hoverRegion) {
//		System.out.println("[CX-FINDINGS-HOVER] getHoverInfo2 called");
//		return this.currentAnnotation;
//	}
//
//	@Override
//	public IInformationControlCreator getHoverControlCreator() {
//		return new IInformationControlCreator() {
//			@Override
//			public IInformationControl createInformationControl(org.eclipse.swt.widgets.Shell parent) {
//				System.out.println("[CX-FINDINGS-HOVER] Creating hover control for annotation: " + currentAnnotation);
//				if (currentAnnotation != null) {
//					return new CxFindingsHoverControl(parent, currentAnnotation);
//				}
//				return null;
//			}
//		};
//	}
//
//	@Override
//	public IRegion getHoverRegion(ITextViewer viewer, int offset) {
//		System.out.println("[CX-FINDINGS-HOVER] getHoverRegion called at offset: " + offset);
//		try {
//			// Find FindingsAnnotation at this offset
//			FindingsAnnotation annotation = findAnnotationContainingOffset(viewer, offset);
//			if (annotation == null) {
//				System.out.println("[CX-FINDINGS-HOVER] No annotation found at offset " + offset);
//				return null;
//			}
//
//			// Cache the annotation for getHoverInfo2()
//			this.currentAnnotation = annotation;
//			System.out.println("[CX-FINDINGS-HOVER] ✓ Found annotation: " + annotation.getTitle());
//
//			// Return the region covered by the annotation in the annotation model
//			if (viewer instanceof org.eclipse.jface.text.source.ISourceViewer) {
//				org.eclipse.jface.text.source.ISourceViewer sourceViewer =
//					(org.eclipse.jface.text.source.ISourceViewer) viewer;
//				IAnnotationModel annotationModel = sourceViewer.getAnnotationModel();
//				if (annotationModel != null) {
//					org.eclipse.jface.text.Position pos = annotationModel.getPosition(annotation);
//					if (pos != null) {
//						System.out.println("[CX-FINDINGS-HOVER] ✓ Returning region: " + pos.getOffset() + "-" + (pos.getOffset() + pos.getLength()));
//						return new Region(pos.getOffset(), pos.getLength());
//					}
//				}
//			}
//		} catch (Exception e) {
//			System.err.println("[CX-FINDINGS-HOVER] Error in getHoverRegion: " + e.getMessage());
//		}
//		return null;
//	}
//
//	/**
//	 * Find a FindingsAnnotation whose position contains the given offset.
//	 * If multiple annotations overlap, returns the innermost (smallest range).
//	 */
//	private FindingsAnnotation findAnnotationContainingOffset(ITextViewer viewer, int offset) {
//		try {
//			if (viewer == null) {
//				System.out.println("[CX-FINDINGS-HOVER] findAnnotationContainingOffset: viewer is null");
//				return null;
//			}
//
//			IAnnotationModel annotationModel = null;
//			if (viewer instanceof org.eclipse.jface.text.source.ISourceViewer) {
//				annotationModel = ((org.eclipse.jface.text.source.ISourceViewer) viewer).getAnnotationModel();
//			}
//
//			if (annotationModel == null) {
//				System.out.println("[CX-FINDINGS-HOVER] findAnnotationContainingOffset: annotation model is null");
//				return null;
//			}
//			System.out.println("[CX-FINDINGS-HOVER] findAnnotationContainingOffset: Searching annotations...");
//
//			FindingsAnnotation bestAnnotation = null;
//			int smallestRange = Integer.MAX_VALUE;
//
//			// Iterate through all annotations in the model
//			@SuppressWarnings("unchecked")
//			java.util.Iterator<Annotation> iterator = annotationModel.getAnnotationIterator();
//			while (iterator.hasNext()) {
//				Annotation annotation = iterator.next();
//
//				if (annotation instanceof FindingsAnnotation) {
//					FindingsAnnotation findingsAnnotation = (FindingsAnnotation) annotation;
//					org.eclipse.jface.text.Position pos = annotationModel.getPosition(annotation);
//
//					if (pos != null) {
//						int start = pos.getOffset();
//						int end = pos.getOffset() + pos.getLength();
//
//						System.out.println("[CX-FINDINGS-HOVER]   Annotation: " + findingsAnnotation.getTitle() +
//							" range=[" + start + "-" + end + "]");
//
//						// Check if offset falls within this annotation's range
//						if (start <= offset && offset < end) {
//							int range = pos.getLength();
//							System.out.println("[CX-FINDINGS-HOVER]     ✓ Offset " + offset + " is INSIDE range, size=" + range);
//							if (range < smallestRange) {
//								smallestRange = range;
//								bestAnnotation = findingsAnnotation;
//								System.out.println("[CX-FINDINGS-HOVER]     ✓ Selected as best annotation (innermost)");
//							}
//						} else {
//							System.out.println("[CX-FINDINGS-HOVER]     ✗ Offset " + offset + " is OUTSIDE range");
//						}
//					}
//				}
//			}
//
//			if (bestAnnotation != null) {
//				System.out.println("[CX-FINDINGS-HOVER] findAnnotationContainingOffset: ✓ FOUND annotation");
//			} else {
//				System.out.println("[CX-FINDINGS-HOVER] findAnnotationContainingOffset: ✗ NO annotation found");
//			}
//
//			return bestAnnotation;
//		} catch (Exception e) {
//			System.err.println("[CX-FINDINGS-HOVER] findAnnotationContainingOffset: EXCEPTION - " + e.getMessage());
//			e.printStackTrace();
//			return null;
//		}
//	}
//}

package com.checkmarx.eclipse.devassist.ui.findings.editor;

import org.eclipse.jface.text.*;
import org.eclipse.swt.widgets.Shell;

public class CxFindingsHover implements ITextHover, ITextHoverExtension {

    @Override
    public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
        return null; // Not used when ITextHoverExtension is implemented
    }

    @Override
    public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
        return new Region(offset, 0);
    }

    @Override
    public IInformationControlCreator getHoverControlCreator() {
        return new AbstractReusableInformationControlCreator() {
            @Override
            protected IInformationControl doCreateInformationControl(Shell parent) {
                // Returns custom popup window containing real SWT Buttons
                return new CxFindingsInformationControl(parent);
            }
        };
    }
}