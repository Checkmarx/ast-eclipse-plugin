package com.checkmarx.eclipse.views.findings.editor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Text;

import com.checkmarx.eclipse.views.findings.model.ScanIssue;

/**
 * Provides custom hover information for findings in the editor.
 * Shows issue details and action buttons on hover.
 */
public class FindingsHoverProvider implements ITextHover, ITextHoverExtension {

    private ScanIssue currentIssue;
    private ITextViewer textViewer;

    public FindingsHoverProvider(ScanIssue issue, ITextViewer viewer) {
        this.currentIssue = issue;
        this.textViewer = viewer;
    }

    @Override
    public String getHoverInfo(ITextViewer viewer, IRegion hoverRegion) {
    	 System.out.println("[HOVER] getHoverInfo()");
        if (currentIssue == null) {
            return null;
        }

        // Build tooltip text (used as cache key by the hover framework; actual UI comes from
        // FindingsInformationControl, built from currentIssue directly).
        StringBuilder tooltip = new StringBuilder();
        String severity = currentIssue.getSeverity();
        tooltip.append("=== ").append(severity != null ? severity.toUpperCase() : "UNKNOWN").append(" ===\n\n");
        tooltip.append("Title: ").append(currentIssue.getTitle()).append("\n\n");
        tooltip.append("Description:\n").append(currentIssue.getDescription()).append("\n\n");
        if (currentIssue.getRemediationAdvise() != null) {
            tooltip.append("Remediation:\n").append(currentIssue.getRemediationAdvise()).append("\n\n");
        }
        tooltip.append("[This is a custom hover with action buttons below]\n");
        tooltip.append("  [Quick Fix] [Ignore] [Copy] [Open Details]");

        return tooltip.toString();
    }

    @Override
    public IRegion getHoverRegion(ITextViewer viewer, int offset) {
    	
    	System.out.println("[HOVER] getHoverRegion offset=" + offset);
        // Return region covering the entire line if it's the problematic line
        try {
            if (viewer == null || currentIssue == null
                    || currentIssue.getLocations() == null || currentIssue.getLocations().isEmpty()) {
                return null;
            }

            IDocument document = viewer.getDocument();
            if (document == null) {
                return null;
            }

            int lineNumber = document.getLineOfOffset(offset);
            int problematicLine = currentIssue.getLocations().get(0).getLine() - 1;
            if (lineNumber == problematicLine) {
                int lineStartOffset = document.getLineOffset(lineNumber);
                int lineLength = document.getLineLength(lineNumber);
                return new Region(lineStartOffset, lineLength);
            }
        } catch (BadLocationException e) {
            // Offset no longer valid (e.g. document changed) - no hover to show
        }
        return null;
    }

    @Override
    public IInformationControlCreator getHoverControlCreator() {
        return new IInformationControlCreator() {
            @Override
            public IInformationControl createInformationControl(Shell parent) {
                return new FindingsInformationControl(parent, currentIssue);
            }
        };
    }

    /**
     * Custom information control for displaying findings with action buttons.
     */
    public static class FindingsInformationControl implements IInformationControl {

        private Shell shell;
        private ScanIssue issue;

        public FindingsInformationControl(Shell parent, ScanIssue issue) {
            this.issue = issue;
            this.shell = new Shell(parent, SWT.TOOL | SWT.ON_TOP);
            this.shell.setLayout(new GridLayout(1, false));
            this.shell.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));

            createContents();
        }

        private void createContents() {
            // Severity label
            Label severityLabel = new Label(shell, SWT.NONE);
            severityLabel.setText(getSeverityIcon(issue.getSeverity()) + " " + getSeverityText(issue.getSeverity()));
            severityLabel.setFont(shell.getDisplay().getSystemFont());
            GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
            severityLabel.setLayoutData(gd);

            // Title label
            Label titleLabel = new Label(shell, SWT.WRAP);
            titleLabel.setText("Title: " + (issue.getTitle() != null ? issue.getTitle() : ""));
            gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
            gd.widthHint = 400;
            titleLabel.setLayoutData(gd);

            // Description text (scrollable)
            Text descriptionText = new Text(shell, SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL);
            descriptionText.setText(issue.getDescription() != null ? issue.getDescription() : "");
            gd = new GridData(SWT.FILL, SWT.FILL, true, true);
            gd.heightHint = 90;
            gd.widthHint = 400;
            descriptionText.setLayoutData(gd);

            // Remediation advice (if available)
            if (issue.getRemediationAdvise() != null && !issue.getRemediationAdvise().isEmpty()) {
                Label remediationLabel = new Label(shell, SWT.WRAP);
                remediationLabel.setText("Remediation: " + issue.getRemediationAdvise());
                gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
                gd.widthHint = 400;
                remediationLabel.setLayoutData(gd);
            }

            // Buttons composite
            Composite buttonsComposite = new Composite(shell, SWT.NONE);
            buttonsComposite.setLayout(new GridLayout(4, true));
            gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
            buttonsComposite.setLayoutData(gd);

            // Quick Fix button
            Button quickFixBtn = new Button(buttonsComposite, SWT.PUSH);
            quickFixBtn.setText("Quick Fix");
            quickFixBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            quickFixBtn.addListener(SWT.Selection, e -> onQuickFixClick());

            // Ignore button
            Button ignoreBtn = new Button(buttonsComposite, SWT.PUSH);
            ignoreBtn.setText("Ignore");
            ignoreBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            ignoreBtn.addListener(SWT.Selection, e -> onIgnoreClick());

            // Copy button
            Button copyBtn = new Button(buttonsComposite, SWT.PUSH);
            copyBtn.setText("Copy");
            copyBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            copyBtn.addListener(SWT.Selection, e -> onCopyClick());

            // Open Details button
            Button openBtn = new Button(buttonsComposite, SWT.PUSH);
            openBtn.setText("Open Window");
            openBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            openBtn.addListener(SWT.Selection, e -> onOpenWindowClick());
        }

        private static String getSeverityIcon(String severity) {
            if (severity == null) {
                return "⚪"; // white circle for unknown
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

        private static String getSeverityText(String severity) {
            return severity != null ? severity.toUpperCase() : "UNKNOWN";
        }

        private void onQuickFixClick() {
            System.out.println("[FINDINGS-HOVER] Quick Fix clicked for: " + issue.getTitle());
            // TODO: Implement quick fix logic
        }

        private void onIgnoreClick() {
            System.out.println("[FINDINGS-HOVER] Ignore clicked for: " + issue.getTitle());
            // TODO: Implement ignore logic
        }

        private void onCopyClick() {
            String title = issue.getTitle() != null ? issue.getTitle() : "";
            String description = issue.getDescription() != null ? issue.getDescription() : "";
            String text = title + "\n" + description;
            shell.getDisplay().asyncExec(() -> {
                org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(shell.getDisplay());
                org.eclipse.swt.dnd.TextTransfer transfer = org.eclipse.swt.dnd.TextTransfer.getInstance();
                clipboard.setContents(new Object[] { text }, new org.eclipse.swt.dnd.Transfer[] { transfer });
                clipboard.dispose();
                System.out.println("[FINDINGS-HOVER] ✓ Copied to clipboard");
            });
        }

        private void onOpenWindowClick() {
            System.out.println("[FINDINGS-HOVER] Open Window clicked for: " + issue.getTitle());
            // TODO: Open Findings window and navigate to issue
        }

        @Override
        public void setInformation(String information) {
        }

        @Override
        public void setSize(int width, int height) {
            if (shell != null) {
                shell.setSize(width, height);
            }
        }

        @Override
        public void setLocation(Point location) {
            if (shell != null && location != null) {
                shell.setLocation(location);
            }
        }

        @Override
        public void setSizeConstraints(int maxWidth, int maxHeight) {
        }

        @Override
        public void dispose() {
            if (shell != null && !shell.isDisposed()) {
                shell.dispose();
            }
        }

        @Override
        public void setVisible(boolean visible) {
            if (shell != null) {
                shell.setVisible(visible);
            }
        }

        @Override
        public void setForegroundColor(org.eclipse.swt.graphics.Color color) {
        }

        @Override
        public void setBackgroundColor(org.eclipse.swt.graphics.Color color) {
        }

        @Override
        public boolean isFocusControl() {
            return shell != null && shell.isFocusControl();
        }

        @Override
        public void setFocus() {
            if (shell != null) {
                shell.setFocus();
            }
        }

        @Override
        public void addDisposeListener(org.eclipse.swt.events.DisposeListener listener) {
            if (shell != null) {
                shell.addDisposeListener(listener);
            }
        }

        @Override
        public void removeDisposeListener(org.eclipse.swt.events.DisposeListener listener) {
            if (shell != null) {
                shell.removeDisposeListener(listener);
            }
        }

        @Override
        public Point computeSizeHint() {
            return new Point(400, 200);
        }

        @Override
        public void addFocusListener(FocusListener listener) {
            if (shell != null) {
                shell.addFocusListener(listener);
            }
        }

        @Override
        public void removeFocusListener(FocusListener listener) {
            if (shell != null) {
                shell.removeFocusListener(listener);
            }
        }
    }
}
