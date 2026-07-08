package com.checkmarx.eclipse.views.problems.marker;

import com.checkmarx.eclipse.utils.PluginConstants;

/**
 * Central definitions for the custom Checkmarx problem marker.
 *
 * <p>
 * The marker type id must match the {@code org.eclipse.core.resources.markers}
 * extension declared in {@code plugin.xml}. Eclipse qualifies the declared
 * extension {@code id} with the contributing bundle's symbolic name, so an
 * extension {@code id="checkmarxProblemMarker"} contributed by bundle
 * {@code com.checkmarx.eclipse.plugin} yields the fully-qualified type below.
 * </p>
 */
public final class ProblemMarkerConstants {

	private ProblemMarkerConstants() {
		// constants holder
	}

	/** Fully-qualified custom marker type (bundle symbolic name + extension id). */
	public static final String MARKER_TYPE = "com.checkmarx.eclipse.plugin.checkmarxProblemMarker";

	/** Reused so both the legacy path and the new path share one source tag. */
	public static final String SOURCE_ID = PluginConstants.PROBLEM_SOURCE_ID;

	/* Custom marker attributes carrying finding metadata (used later by actions). */
	public static final String ATTR_FINDING_ID = "checkmarxFindingId";
	public static final String ATTR_RULE_ID = "checkmarxRuleId";
	public static final String ATTR_SEVERITY = "checkmarxSeverity";
	public static final String ATTR_STATUS = "checkmarxStatus";

	/** {@code IMarker.LOCATION} text pattern, e.g. "line 42". */
	public static final String LOCATION_LINE_PATTERN = "line %d";
}
