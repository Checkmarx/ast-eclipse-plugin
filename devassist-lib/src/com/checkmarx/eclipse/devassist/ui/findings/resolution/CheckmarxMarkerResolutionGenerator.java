package com.checkmarx.eclipse.devassist.ui.findings.resolution;

import org.eclipse.core.resources.IMarker;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator2;

/**
 * Provides marker resolutions for Checkmarx findings.
 * Invoked when user presses Ctrl+1 on a marker or selects "Quick Fix" from context menu.
 * Implements IMarkerResolutionGenerator2 for efficient hasResolutions() check.
 */
public class CheckmarxMarkerResolutionGenerator implements IMarkerResolutionGenerator2 {

    @Override
    public IMarkerResolution[] getResolutions(IMarker marker) {
        return new IMarkerResolution[] {
            new ViewFindingDetailsResolution(marker)
        };
    }

    @Override
    public boolean hasResolutions(IMarker marker) {
        // We always provide the "View Finding Details" resolution
        return true;
    }
}
