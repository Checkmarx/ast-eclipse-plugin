package com.checkmarx.eclipse.views.findings.provider;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.checkmarx.eclipse.views.findings.model.FileNodeLabel;
import com.checkmarx.eclipse.views.findings.model.ScanIssue;
import com.checkmarx.eclipse.views.findings.model.ScanDetailWithPath;

import java.util.List;
import java.util.Map;

/**
 * Content provider for the Findings tree viewer.
 * Implements {@link ITreeContentProvider} to provide hierarchical content structure.
 * Organizes scan issues by file path as parent nodes with individual issues as children.
 */
public class FindingsContentProvider implements ITreeContentProvider {

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    }

    @Override
    public Object[] getElements(Object inputElement) {
        if (inputElement instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, List<ScanIssue>> map = (Map<String, List<ScanIssue>>) inputElement;
            return map.entrySet().stream()
                    .map(entry -> new FileNodeLabel(
                            getFileName(entry.getKey()),
                            entry.getKey(),
                            entry.getValue()))
                    .toArray();
        }
        return new Object[0];
    }

    @Override
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof FileNodeLabel) {
            FileNodeLabel fileNode = (FileNodeLabel) parentElement;
            return fileNode.getIssues().stream()
                    .map(issue -> new ScanDetailWithPath(issue, fileNode.getFilePath()))
                    .toArray();
        }
        return new Object[0];
    }

    @Override
    public Object getParent(Object element) {
        if (element instanceof ScanDetailWithPath) {
            // Parent is the file node - would need to track in the model
            return null;
        }
        return null;
    }

    @Override
    public boolean hasChildren(Object element) {
        if (element instanceof FileNodeLabel) {
            return !((FileNodeLabel) element).getIssues().isEmpty();
        }
        return false;
    }

    private String getFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "Unknown";
        }
        int lastSeparator = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            return filePath.substring(lastSeparator + 1);
        }
        return filePath;
    }

    @Override
    public void dispose() {
        // Cleanup if needed
    }
}
