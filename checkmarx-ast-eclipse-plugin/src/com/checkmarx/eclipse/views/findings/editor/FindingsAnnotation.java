package com.checkmarx.eclipse.views.findings.editor;

import org.eclipse.jface.text.source.Annotation;

/**
 * Custom annotation for Findings window highlighting.
 * Represents a security finding annotation in the editor with title and description.
 */
public class FindingsAnnotation extends Annotation {

    private final String title;
    private final String description;

    public FindingsAnnotation(String annotationType, String title, String description) {
        super(annotationType, false, null);
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String getText() {
        return title;
    }
}
