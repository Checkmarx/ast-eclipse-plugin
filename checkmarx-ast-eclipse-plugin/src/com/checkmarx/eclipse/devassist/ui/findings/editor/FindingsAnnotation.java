//package com.checkmarx.eclipse.devassist.ui.findings.editor;
//
//import org.eclipse.jface.text.source.Annotation;
//
///**
// * Custom annotation for Findings window highlighting.
// * Represents a security finding annotation in the editor with title and description.
// */
//public class FindingsAnnotation extends Annotation {
//
//    private final String title;
//    private final String description;
//
//    public FindingsAnnotation(String annotationType, String title, String description) {
//        super(annotationType, false, null);
//        this.title = title;
//        this.description = description;
//    }
//
//    public String getTitle() {
//        return title;
//    }
//
//    public String getDescription() {
//        return description;
//    }
//
//    @Override
//    public String getText() {
//        return title;
//    }
//}
package com.checkmarx.eclipse.devassist.ui.findings.editor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jface.text.source.Annotation;

public class FindingsAnnotation extends Annotation {

    private String title;
    private String description;
    private List<AnnotationButton> buttons = new ArrayList<>();

    public FindingsAnnotation(String type, String title, String description) {
        super(type, false, title);
        this.title = title;
        this.description = description;
    }

    public void addButton(String label, Runnable action) {
        buttons.add(new AnnotationButton(label, action));
    }

    public List<AnnotationButton> getButtons() {
        return buttons;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }

    public static class AnnotationButton {
        public String label;
        public Runnable action;

        public AnnotationButton(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }
}