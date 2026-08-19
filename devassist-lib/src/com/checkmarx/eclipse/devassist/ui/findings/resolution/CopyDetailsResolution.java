package com.checkmarx.eclipse.devassist.ui.findings.resolution;

import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IMarkerResolution2;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;

/**
 * Marker resolution that copies the finding's title and description to the clipboard.
 * Implements IMarkerResolution2 for efficient hasResolutions() checks.
 */
public class CopyDetailsResolution implements IMarkerResolution2 {

    private final Image icon;

    public CopyDetailsResolution(IMarker marker) {
        this.icon = ResolutionIconHelper.severityIconForMarker(marker);
    }

    @Override
    public String getLabel() {
        return DevAssistConstants.COPY_DETAILS_FIX_NAME;
    }

    @Override
    public String getDescription() {
        return "Copy this finding's title and description to the clipboard";
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
                CxLogger.warning("CopyDetailsResolution: could not reconstruct ScanIssue from marker");
                return;
            }
            String title = issue.getTitle() != null ? issue.getTitle() : "";
            String description = issue.getDescription() != null ? issue.getDescription() : "";
            String text = title + "\n" + description;

            Display.getDefault().asyncExec(() -> {
                Clipboard clipboard = new Clipboard(Display.getDefault());
                try {
                    clipboard.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
                } finally {
                    clipboard.dispose();
                }
            });
        } catch (Exception e) {
            CxLogger.error("CopyDetailsResolution: failed to copy details", e);
        }
    }
}
