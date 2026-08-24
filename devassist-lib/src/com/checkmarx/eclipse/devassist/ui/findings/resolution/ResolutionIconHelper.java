package com.checkmarx.eclipse.devassist.ui.findings.resolution;

import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.graphics.Image;

import com.checkmarx.eclipse.devassist.ui.findings.icons.IconRegistry;

/**
 * Shared helper for IMarkerResolution2 implementations to look up the
 * severity icon for a Checkmarx marker, so all 4 Quick Fix actions for a
 * given finding show the same severity-colored icon (reusing the existing
 * IconRegistry SVG severity icons rather than introducing new action-specific
 * icon assets).
 */
final class ResolutionIconHelper {

    private static final String ATTR_SEVERITY = "cx.severity";

    private ResolutionIconHelper() {
    }

    /**
     * Reads the marker's stored severity attribute directly (without fully
     * reconstructing a ScanIssue) and resolves it to a severity icon.
     *
     * @param marker the Checkmarx problem marker
     * @return the severity Image, or null if unavailable/marker deleted
     */
    static Image severityIconForMarker(IMarker marker) {
        try {
            if (marker == null || !marker.exists()) {
                return null;
            }
            String severity = marker.getAttribute(ATTR_SEVERITY, null);
            return severity != null ? IconRegistry.getIcon(severity) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
