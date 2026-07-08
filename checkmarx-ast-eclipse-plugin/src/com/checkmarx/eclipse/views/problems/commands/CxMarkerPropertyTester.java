package com.checkmarx.eclipse.views.problems.commands;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IMarker;

import com.checkmarx.eclipse.views.problems.marker.ProblemMarkerConstants;

/**
 * Core-expression property tester used by the {@code visibleWhen} clause of the
 * Problems View context-menu contribution.
 *
 * <p>
 * It answers a single question — "is this selected marker a Checkmarx problem
 * marker?" — so the four custom actions are shown only when the selection
 * consists exclusively of our markers, and never on plain JDT/compiler
 * problems. This keeps visibility logic declarative in {@code plugin.xml}
 * rather than in ad-hoc selection listeners.
 * </p>
 */
@SuppressWarnings("restriction")
public class CxMarkerPropertyTester extends PropertyTester {

	public static final String PROPERTY_IS_CX_MARKER = "isCheckmarxMarker";

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if (!PROPERTY_IS_CX_MARKER.equals(property) || !(receiver instanceof IMarker)) {
			return false;
		}
		IMarker marker = (IMarker) receiver;
		try {
			return marker.exists() && marker.isSubtypeOf(ProblemMarkerConstants.MARKER_TYPE);
		} catch (Exception e) {
			return false;
		}
	}
}
