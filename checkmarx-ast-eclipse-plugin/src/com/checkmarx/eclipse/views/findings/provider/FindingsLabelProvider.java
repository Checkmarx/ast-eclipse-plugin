package com.checkmarx.eclipse.views.findings.provider;

import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;

import com.checkmarx.eclipse.views.findings.model.FileNodeLabel;
import com.checkmarx.eclipse.views.findings.model.ScanDetailWithPath;
import com.checkmarx.eclipse.views.findings.model.ScanIssue;
import com.checkmarx.eclipse.views.findings.icons.IconRegistry;

/**
 * Label provider for the Findings tree viewer.
 * Extends {@link DelegatingStyledCellLabelProvider.IStyledLabelProvider} for custom rendering.
 * Displays severity icons with counts for file nodes and issue details for leaf nodes.
 */
public class FindingsLabelProvider extends DelegatingStyledCellLabelProvider {

    public FindingsLabelProvider() {
        super(new IStyledLabelProvider() {
            @Override
            public StyledString getStyledText(Object element) {
                if (element instanceof FileNodeLabel) {
                    FileNodeLabel fileNode = (FileNodeLabel) element;
                    StyledString styledString = new StyledString(fileNode.getFileName());

                    if (fileNode.getProblemCount() != null && !fileNode.getProblemCount().isEmpty()) {
                        // Display severity icons with counts instead of text labels
                        styledString.append(formatSeverityIconsWithCounts(fileNode), StyledString.COUNTER_STYLER);
                    }
                    return styledString;
                } else if (element instanceof ScanDetailWithPath) {
                    ScanDetailWithPath detailWithPath = (ScanDetailWithPath) element;
                    ScanIssue detail = detailWithPath.getDetail();

                    String text = formatIssueText(detail);
                    return new StyledString(text);
                }
                return new StyledString(element.toString());
            }

            @Override
            public Image getImage(Object element) {
                if (element instanceof FileNodeLabel) {
                    FileNodeLabel fileNode = (FileNodeLabel) element;

                    // Use file type icon if resolved at node creation time
                    Image fileIcon = fileNode.getIcon();
                    if (fileIcon != null) {
                        return fileIcon;
                    }

                    // Fallback to severity icon (highest severity in file)
                    String highestSeverity = getHighestSeverityInFile(fileNode);
                    return IconRegistry.getIcon(highestSeverity, IconRegistry.Size.SMALL);
                } else if (element instanceof ScanDetailWithPath) {
                    ScanDetailWithPath detailWithPath = (ScanDetailWithPath) element;
                    String severity = detailWithPath.getDetail().getSeverity();
                    return IconRegistry.getIcon(severity, IconRegistry.Size.SMALL);
                }
                return null;
            }

            @Override
            public void dispose() {
            }

            @Override
            public void addListener(ILabelProviderListener listener) {
            }

            @Override
            public void removeListener(ILabelProviderListener listener) {
            }

            @Override
            public boolean isLabelProperty(Object element, String property) {
                return false;
            }

            private String getHighestSeverityInFile(FileNodeLabel fileNode) {
                if (fileNode.getProblemCount() == null || fileNode.getProblemCount().isEmpty()) {
                    return "low";
                }

                // Priority order: critical > high > medium > low
                if (fileNode.getProblemCount().containsKey("critical")) {
                    return "critical";
                } else if (fileNode.getProblemCount().containsKey("high")) {
                    return "high";
                } else if (fileNode.getProblemCount().containsKey("medium")) {
                    return "medium";
                } else {
                    return "low";
                }
            }

            private String formatIssueText(ScanIssue detail) {
                String issueTitle = getIssueTitle(detail);
                String lineNumber = getLineNumberText(detail);
                return issueTitle + lineNumber;
            }

            private String getIssueTitle(ScanIssue detail) {
                switch (detail.getScanEngine()) {
                    case OSS:
                        return detail.getSeverity() + "-risk package: " + detail.getTitle() + "@" + detail.getPackageVersion();
                    case SECRETS:
                        return detail.getSeverity() + "-risk secret: " + detail.getTitle();
                    case CONTAINERS:
                        return detail.getSeverity() + "-risk container image: " + detail.getTitle() + ":" + detail.getImageTag();
                    case ASCA:
                    case IAC:
                        return detail.getTitle();
                    default:
                        return detail.getDescription();
                }
            }

            private String getLineNumberText(ScanIssue detail) {
                if (detail.getLocations() != null && !detail.getLocations().isEmpty()) {
                    int lineNumber = detail.getLocations().get(0).getLine();
                    int columnNumber = detail.getLocations().get(0).getStartIndex();
                    return "  [Ln " + lineNumber + ", Col " + columnNumber + "]";
                }
                return "";
            }

            /**
             * Format severity counts with visual icons instead of text labels.
             * Displays: "🔴 4  🟠 3  🟡 2" instead of "critical:4 high:3 medium:2"
             */
            private String formatSeverityIconsWithCounts(FileNodeLabel fileNode) {
                StringBuilder sb = new StringBuilder();

                // Display in order: critical → high → medium → low
                String[] severities = { "critical", "high", "medium", "low" };
                String[] icons = { "🔴", "🟠", "🟡", "🟢" };

                for (int i = 0; i < severities.length; i++) {
                    Long count = fileNode.getProblemCount().get(severities[i]);
                    if (count != null && count > 0) {
                        if (sb.length() > 0) {
                            sb.append("  "); // Spacing between icons
                        }
                        sb.append(icons[i]).append(" ").append(count);
                    }
                }

                return sb.length() > 0 ? "  " + sb.toString() : "";
            }
        });
    }
}
