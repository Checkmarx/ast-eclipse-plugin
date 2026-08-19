package com.checkmarx.eclipse.devassist.ui.findings.provider;

import java.util.Map;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;

import com.checkmarx.eclipse.devassist.ui.findings.model.FileNodeLabel;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanDetailWithPath;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.icons.IconRegistry;

/**
 * Label provider tailored exactly to render severity shield badges 
 * sequentially to the right of file labels.
 */
public class FindingsLabelProvider extends DelegatingStyledCellLabelProvider {

    private static final String[] SEVERITIES = { "critical", "high", "medium", "low" };
    private static final int BETWEEN_BADGE_SPACING = 4; // Space between different shield groups
    private static final int TEXT_TO_BADGE_PADDING = 28; // Space after filename before first badge

    public FindingsLabelProvider() {
        super(new IStyledLabelProvider() {
            @Override
            public StyledString getStyledText(Object element) {
                if (element instanceof FileNodeLabel) {
                    return new StyledString(((FileNodeLabel) element).getFileName());
                } else if (element instanceof ScanDetailWithPath) {
                    return new StyledString(formatIssueText(((ScanDetailWithPath) element).getDetail()));
                }
                return new StyledString(element.toString());
            }

            @Override
            public Image getImage(Object element) {
                if (element instanceof FileNodeLabel) {
                    return ((FileNodeLabel) element).getIcon();
                } else if (element instanceof ScanDetailWithPath) {
                    String severity = ((ScanDetailWithPath) element).getDetail().getSeverity();
                    return IconRegistry.getThemeAwareIcon(severity, IconRegistry.Size.SMALL);
                }
                return null;
            }

            @Override public void dispose() {}
            @Override public void addListener(ILabelProviderListener l) {}
            @Override public void removeListener(ILabelProviderListener l) {}
            @Override public boolean isLabelProperty(Object el, String prop) { return false; }

            private String formatIssueText(ScanIssue detail) {
                switch (detail.getScanEngine()) {
                    case OSS: return detail.getSeverity() + "-risk package: " + detail.getTitle() + "@" + detail.getPackageVersion() + getLineNumberText(detail);
                    case SECRETS: return detail.getSeverity() + "-risk secret: " + detail.getTitle() + getLineNumberText(detail);
                    case CONTAINERS: return detail.getSeverity() + "-risk container image: " + detail.getTitle() + ":" + detail.getImageTag() + getLineNumberText(detail);
                    case ASCA:
                    case IAC: return detail.getTitle() + getLineNumberText(detail);
                    default: return detail.getDescription() + getLineNumberText(detail);
                }
            }

            private String getLineNumberText(ScanIssue detail) {
                if (detail.getLocations() != null && !detail.getLocations().isEmpty()) {
                    return "  [Ln " + detail.getLocations().get(0).getLine() + ", Col " + detail.getLocations().get(0).getStartIndex() + "]";
                }
                return "";
            }
        });
    }

    @Override
    protected void measure(Event event, Object element) {
        super.measure(event, element);
        
        if (element instanceof FileNodeLabel) {
            FileNodeLabel fileNode = (FileNodeLabel) element;
            Map<String, Long> counts = fileNode.getProblemCount();
            
            if (counts != null && !counts.isEmpty()) {
                int extraWidth = TEXT_TO_BADGE_PADDING;
                for (String severity : SEVERITIES) {
                    if (counts.containsKey(severity) && counts.get(severity) > 0) {
                        String countStr = String.valueOf(counts.get(severity));
                        int textWidth = event.gc.textExtent(countStr).x;
                        // 16px (Icon) + 4px (Gap between icon & number) + number length + gap to next badge
                        extraWidth += 16 + 0 + textWidth + BETWEEN_BADGE_SPACING;
                    }
                }
                event.width += extraWidth;
            }
        }
    }

    @Override
    protected void paint(Event event, Object element) {
        // 1. Draw standard tree node elements (Expand/collapse arrows, file icons, text strings)
        super.paint(event, element);

        // 2. Lay down the right-aligned badges
        if (element instanceof FileNodeLabel) {
            FileNodeLabel fileNode = (FileNodeLabel) element;
            Map<String, Long> counts = fileNode.getProblemCount();

            if (counts != null && !counts.isEmpty()) {
                // Determine exactly where the file label ends horizontally
                Point textSize = event.gc.textExtent(fileNode.getFileName());
                
                // Base offset: layout context starting position + text length + margin padding
                int currentX = event.x + textSize.x + TEXT_TO_BADGE_PADDING;
                
                int rowHeight = event.height;
                int iconY = event.y + (rowHeight - 16) / 2;
                int textY = event.y + (rowHeight - event.gc.getFontMetrics().getHeight()) / 2;

                for (String severity : SEVERITIES) {
                    Long count = counts.get(severity);
                    if (count != null && count > 0) {
                        // Grab theme-aware shield icon (light or dark variant based on current theme)
                        Image badgePng = IconRegistry.getThemeAwareIcon(severity, IconRegistry.Size.MEDIUM);
                        
                        if (badgePng != null) {
                            // Draw Shield Badge
                            event.gc.drawImage(badgePng, currentX, iconY);
                            currentX += 16 + 4; // Shift right right past shield + a tiny gap
                            
                            // Draw Count Number tightly next to the shield
                            String countStr = String.valueOf(count);

                            // Set text color based on theme and selection state
                            if ((event.detail & SWT.SELECTED) != 0) {
                                event.gc.setForeground(event.display.getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT));
                            } else {
                                // Use theme-based colors for non-selected state
                                if (DevAssistUtils.isDarkTheme()) {
                                    event.gc.setForeground(event.display.getSystemColor(SWT.COLOR_WHITE));
                                } else {
                                    event.gc.setForeground(event.display.getSystemColor(SWT.COLOR_BLACK));
                                }
                            }

                            // Make count text bold
                            Font originalFont = event.gc.getFont();
                            FontData[] fontData = originalFont.getFontData();
                            for (FontData fd : fontData) {
                                fd.setStyle(fd.getStyle() | SWT.BOLD);
                                fd.setHeight(10);
                            }
                            org.eclipse.swt.graphics.Font boldFont = new org.eclipse.swt.graphics.Font(event.display, fontData);
                            event.gc.setFont(boldFont);

                            event.gc.drawString(countStr, currentX, textY, true);

                            // Restore original font
                            event.gc.setFont(originalFont);
                            boldFont.dispose();
                            
                            // Advance cursor layout pointer to the next shield group block
                            currentX += event.gc.textExtent(countStr).x + BETWEEN_BADGE_SPACING;
                        }
                    }
                }
            }
        }
    }
}