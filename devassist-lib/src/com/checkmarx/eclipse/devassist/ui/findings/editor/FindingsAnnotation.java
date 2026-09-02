package com.checkmarx.eclipse.devassist.ui.findings.editor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jface.text.source.Annotation;
import com.checkmarx.eclipse.devassist.model.ScanIssue;

public class FindingsAnnotation extends Annotation {

    private String title;
    private String description;
    private ScanIssue scanIssue;
    private List<AnnotationButton> buttons = new ArrayList<>();

    public FindingsAnnotation(String type, String title, String description) {
        super(type, false, null);
        this.title = title;
        this.description = description;
    }

    public FindingsAnnotation(String type, String title, String description, ScanIssue scanIssue) {
        super(type, false, null);
        this.title = title;
        this.description = description;
        this.scanIssue = scanIssue;
    }

    public void addButton(String label, Runnable action) {
        buttons.add(new AnnotationButton(label, action));
    }

    public List<AnnotationButton> getButtons() {
        return buttons;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ScanIssue getScanIssue() {
        return scanIssue;
    }

    public static class AnnotationButton {
        public String label;
        public Runnable action;

        public AnnotationButton(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }
}