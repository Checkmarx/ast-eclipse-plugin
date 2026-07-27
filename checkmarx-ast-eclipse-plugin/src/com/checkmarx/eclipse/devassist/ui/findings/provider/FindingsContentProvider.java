package com.checkmarx.eclipse.devassist.ui.findings.provider;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

import com.checkmarx.eclipse.devassist.ui.findings.model.FileNodeLabel;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanDetailWithPath;

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

            System.out.println("[FINDINGS-CONTENT] ========================================");
            System.out.println("[FINDINGS-CONTENT] Creating FileNodeLabel elements...");
            System.out.println("[FINDINGS-CONTENT] Input files: " + map.size());

            Object[] elements = map.entrySet().stream()
                    .map(entry -> {
                        String fileName = getFileName(entry.getKey());
                        Image fileIcon = getFileIcon(fileName);
                        List<ScanIssue> issues = entry.getValue();

                        System.out.println("[FINDINGS-CONTENT] File: " + fileName);
                        System.out.println("[FINDINGS-CONTENT]   Path: " + entry.getKey());
                        System.out.println("[FINDINGS-CONTENT]   Issues: " + issues.size());

                        // Calculate and log severity counts
                        java.util.Map<String, Long> counts = new java.util.HashMap<>();
                        for (ScanIssue issue : issues) {
                            String severity = issue.getSeverity();
                            counts.put(severity, counts.getOrDefault(severity, 0L) + 1);
                        }
                        counts.forEach((sev, cnt) ->
                            System.out.println("[FINDINGS-CONTENT]     " + sev + ": " + cnt)
                        );

                        return new FileNodeLabel(
                                fileName,
                                entry.getKey(),
                                issues,
                                fileIcon);
                    })
                    .toArray();

            System.out.println("[FINDINGS-CONTENT] âœ“ Created " + elements.length + " FileNodeLabel elements");
            System.out.println("[FINDINGS-CONTENT] ========================================");
            return elements;
        }

        System.out.println("[FINDINGS-CONTENT] âœ— Input is not a Map, type: " +
            (inputElement != null ? inputElement.getClass().getSimpleName() : "null"));
        return new Object[0];
    }

    private Image getFileIcon(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        try {
            IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
            ImageDescriptor imageDescriptor = registry.getImageDescriptor(fileName);

            if (imageDescriptor != null) {
                Image image = imageDescriptor.createImage();
                if (image != null) {
                    return image;
                }
            }
        } catch (Exception e) {
            System.out.println("[FINDINGS-CONTENT] Error getting file icon for: " + fileName + " - " + e.getMessage());
        }

        return null;
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

