package com.checkmarx.eclipse.devassist.ui.findings.editor;

import org.eclipse.jface.text.AbstractInformationControl;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;

public class CxFindingsInformationControl extends AbstractInformationControl {

    public CxFindingsInformationControl(Shell parentShell) {
        super(parentShell, false);
        create();
    }

    @Override
    protected void createContent(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(4, true));

        // Label
        Label title = new Label(composite, SWT.NONE);
        title.setText("Checkmarx Finding Detected");
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1);
        title.setLayoutData(gd);

        // Clickable SWT Buttons inside Hover Popup
        Button quickFixBtn = new Button(composite, SWT.PUSH);
        quickFixBtn.setText("⚡ Quick Fix");
        quickFixBtn.addListener(SWT.Selection, e -> System.out.println("Quick Fix clicked from hover!"));

        Button ignoreBtn = new Button(composite, SWT.PUSH);
        ignoreBtn.setText("🚫 Ignore");
        ignoreBtn.addListener(SWT.Selection, e -> System.out.println("Ignore clicked from hover!"));

        Button copyBtn = new Button(composite, SWT.PUSH);
        copyBtn.setText("📋 Copy");

        Button detailsBtn = new Button(composite, SWT.PUSH);
        detailsBtn.setText("🪟 Details");
    }

    @Override
    public boolean hasContents() {
        return true;
    }
}