package com.checkmarx.eclipse.devassist.ui.findings.ignore;

import org.eclipse.core.resources.IProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.ViewPart;
import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * Tool window panel for viewing ignored vulnerability findings.
 * Simplified Eclipse SWT implementation.
 */
public class DevAssistIgnoredFindings extends ViewPart {

    public static final String ID = "com.checkmarx.eclipse.devassist.ui.findings.ignore.DevAssistIgnoredFindings";

    private Composite container;

    @Override
    public void createPartControl(Composite parent) {
        container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));

        // Create a simple label for now
        Label label = new Label(container, SWT.WRAP);
        label.setText("Ignored Findings View\n\nIgnored vulnerabilities will be listed here.");
        label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        CxLogger.info("DevAssistIgnoredFindings view created");
    }

    @Override
    public void setFocus() {
        if (container != null && !container.isDisposed()) {
            container.setFocus();
        }
    }

    @Override
    public void dispose() {
        if (container != null && !container.isDisposed()) {
            container.dispose();
        }
        super.dispose();
    }
}
