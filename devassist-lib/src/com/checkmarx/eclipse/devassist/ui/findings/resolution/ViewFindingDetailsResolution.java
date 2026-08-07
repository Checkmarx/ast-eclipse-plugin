package com.checkmarx.eclipse.devassist.ui.findings.resolution;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IMarkerResolution2;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper;
import com.checkmarx.eclipse.devassist.model.ScanIssue;

/**
 * Marker resolution that opens a dialog showing complete finding details.
 * Reconstructs ScanIssue from marker attributes and displays rich UI.
 * Implements IMarkerResolution2 for better performance with hasResolutions() check.
 */
public class ViewFindingDetailsResolution implements IMarkerResolution2 {

    public ViewFindingDetailsResolution(IMarker marker) {
        // Constructor parameter kept for instantiation, marker details retrieved from run() parameter
    }

    @Override
    public String getLabel() {
        return "View Finding Details";
    }

    @Override
    public String getDescription() {
        return "Open detailed information about this Checkmarx finding";
    }

    @Override
    public Image getImage() {
        // Optional: Return an icon. For now, use default
        return null;
    }

    @Override
    public void run(IMarker marker) {
        try {
            // Reconstruct ScanIssue from marker attributes
            ScanIssue issue = MarkerIssueMapper.fromMarker(marker);
            if (issue == null) {
                
                return;
            }

            // Open the details dialog
            FindingDetailsDialog dialog = new FindingDetailsDialog(
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                    issue
            );
            dialog.open();

            

        } catch (Exception e) {
            
            e.printStackTrace();
        }
    }

    /**
     * Simple dialog that displays finding details.
     * Reuses the UI structure from FindingsInformationControl.
     */
    private static class FindingDetailsDialog extends Dialog {

        private ScanIssue issue;

        public FindingDetailsDialog(Shell parentShell, ScanIssue issue) {
            super(parentShell);
            this.issue = issue;
            setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
        }

        @Override
        protected void configureShell(Shell newShell) {
            super.configureShell(newShell);
            newShell.setText("Checkmarx Finding Details - " + (issue.getTitle() != null ? issue.getTitle() : ""));
            newShell.setSize(500, 400);

            // Center on screen
            Shell parent = getParentShell();
            if (parent != null) {
                org.eclipse.swt.graphics.Rectangle bounds = parent.getBounds();
                Point size = newShell.getSize();
                newShell.setLocation(
                        bounds.x + (bounds.width - size.x) / 2,
                        bounds.y + (bounds.height - size.y) / 2
                );
            }
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite container = (Composite) super.createDialogArea(parent);
            container.setLayout(new GridLayout(1, false));

            // Severity label with icon
            Label severityLabel = new Label(container, SWT.NONE);
            severityLabel.setText(getSeverityIcon(issue.getSeverity()) + " " + getSeverityText(issue.getSeverity()));
            severityLabel.setFont(container.getDisplay().getSystemFont());
            GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
            severityLabel.setLayoutData(gd);

            // Title label
            Label titleLabel = new Label(container, SWT.WRAP);
            titleLabel.setText("Title: " + (issue.getTitle() != null ? issue.getTitle() : ""));
            gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
            gd.widthHint = 480;
            titleLabel.setLayoutData(gd);

            // Description text (scrollable)
            Text descriptionText = new Text(container, SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL | SWT.BORDER);
            descriptionText.setText(issue.getDescription() != null ? issue.getDescription() : "");
            gd = new GridData(SWT.FILL, SWT.FILL, true, true);
            gd.heightHint = 120;
            gd.widthHint = 480;
            descriptionText.setLayoutData(gd);

            // Remediation advice (if available)
            if (issue.getRemediationAdvise() != null && !issue.getRemediationAdvise().isEmpty()) {
                Label remediationLabel = new Label(container, SWT.WRAP);
                remediationLabel.setText("Remediation: " + issue.getRemediationAdvise());
                gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
                gd.widthHint = 480;
                remediationLabel.setLayoutData(gd);
            }

            // Buttons composite
            Composite buttonsComposite = new Composite(container, SWT.NONE);
            buttonsComposite.setLayout(new GridLayout(4, true));
            gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
            buttonsComposite.setLayoutData(gd);

            // Quick Fix button
            Button quickFixBtn = new Button(buttonsComposite, SWT.PUSH);
            quickFixBtn.setText("⚡ Quick Fix");
            quickFixBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            quickFixBtn.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    onQuickFixClick();
                }
            });

            // Ignore button
            Button ignoreBtn = new Button(buttonsComposite, SWT.PUSH);
            ignoreBtn.setText("🚫 Ignore");
            ignoreBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            ignoreBtn.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    onIgnoreClick();
                }
            });

            // Copy button
            Button copyBtn = new Button(buttonsComposite, SWT.PUSH);
            copyBtn.setText("📋 Copy");
            copyBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            copyBtn.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    onCopyClick();
                }
            });

            // Open Window button
            Button openBtn = new Button(buttonsComposite, SWT.PUSH);
            openBtn.setText("🪟 Details");
            openBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            openBtn.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    onOpenWindowClick();
                }
            });

            return container;
        }

        @Override
        protected void createButtonsForButtonBar(Composite parent) {
            // Remove default OK/Cancel buttons, add Close button
            createButton(parent, org.eclipse.jface.dialogs.IDialogConstants.CLOSE_ID, "Close", true);
        }

        private void onQuickFixClick() {
            
            // TODO: Implement remediation integration
        }

        private void onIgnoreClick() {
            
            // TODO: Implement ignore logic
        }

        private void onCopyClick() {
            String title = issue.getTitle() != null ? issue.getTitle() : "";
            String description = issue.getDescription() != null ? issue.getDescription() : "";
            String text = title + "\n" + description;

            getShell().getDisplay().asyncExec(() -> {
                Clipboard clipboard = new Clipboard(getShell().getDisplay());
                TextTransfer transfer = TextTransfer.getInstance();
                clipboard.setContents(new Object[] { text }, new Transfer[] { transfer });
                clipboard.dispose();
                
            });
        }

        private void onOpenWindowClick() {
            
            // TODO: Open Findings window and navigate to this issue
        }

        private String getSeverityIcon(String severity) {
            if (severity == null) {
                return "⚪";
            }
            switch (severity.toLowerCase()) {
                case "critical":
                    return "🔴";
                case "high":
                    return "🟠";
                case "medium":
                    return "🟡";
                case "low":
                    return "🟢";
                default:
                    return "⚪";
            }
        }

        private String getSeverityText(String severity) {
            return severity != null ? severity.toUpperCase() : "UNKNOWN";
        }
    }
}

