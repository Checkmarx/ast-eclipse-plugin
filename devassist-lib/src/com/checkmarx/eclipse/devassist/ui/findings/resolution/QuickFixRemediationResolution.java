package com.checkmarx.eclipse.devassist.ui.findings.resolution;

import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution2;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.remediation.RemediationManager;
import com.checkmarx.eclipse.devassist.ui.findings.icons.IconRegistry;
import com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;

import static com.checkmarx.eclipse.devassist.utils.DevAssistConstants.QUICK_FIX;

/**
 * Marker resolution that applies automated remediation for a Checkmarx finding.
 * Mirrors the JetBrains plugin's DevAssistFix (LocalQuickFix) behavior:
 * sends a remediation prompt to Copilot, falling back to clipboard copy.
 * Implements IMarkerResolution2 for efficient hasResolutions() checks.
 */
public class QuickFixRemediationResolution implements IMarkerResolution2 {

    private final Image icon;

    public QuickFixRemediationResolution(IMarker marker) {
        this.icon = ResolutionIconHelper.severityIconForMarker(marker);
    }

    @Override
    public String getLabel() {
        return DevAssistConstants.FIX_WITH_DEV_ASSIST;
    }

    @Override
    public String getDescription() {
        return "Apply an automated fix for this Checkmarx finding";
    }

    @Override
    public Image getImage() {
        return icon;
    }

    @Override
    public void run(IMarker marker) {
        try {
            ScanIssue issue = MarkerIssueMapper.fromMarker(marker);
            if (issue == null) {
                CxLogger.warning("QuickFixRemediationResolution: could not reconstruct ScanIssue from marker");
                return;
            }
            new RemediationManager().fixWithCxOneAssist(issue, QUICK_FIX);
        } catch (Exception e) {
            CxLogger.error("QuickFixRemediationResolution: failed to apply remediation", e);
        }
    }
}
