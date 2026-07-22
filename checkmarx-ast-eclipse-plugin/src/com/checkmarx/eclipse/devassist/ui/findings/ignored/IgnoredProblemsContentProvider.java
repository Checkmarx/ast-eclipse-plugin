package com.checkmarx.eclipse.devassist.ui.findings.ignored;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Content provider for ignored problems tree view. Organizes issues by file.
 */
public class IgnoredProblemsContentProvider implements ITreeContentProvider {

	private Map<String, List<ScanIssue>> fileToIssues = new HashMap<>();

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		fileToIssues.clear();
		if (newInput instanceof List<?>) {
			@SuppressWarnings("unchecked")
			List<ScanIssue> issues = (List<ScanIssue>) newInput;
			for (ScanIssue issue : issues) {
				String fileName = extractFileName(issue.getFilePath());
				fileToIssues.computeIfAbsent(fileName, k -> new ArrayList<>()).add(issue);
			}
		}
	}

	@Override
	public Object[] getElements(Object inputElement) {
		return fileToIssues.keySet().toArray();
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof String) {
			List<ScanIssue> issues = fileToIssues.get(parentElement);
			return issues != null ? issues.toArray() : new Object[0];
		}
		return new Object[0];
	}

	@Override
	public Object getParent(Object element) {
		if (element instanceof ScanIssue) {
			ScanIssue issue = (ScanIssue) element;
			return extractFileName(issue.getFilePath());
		}
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		if (element instanceof String) {
			List<ScanIssue> issues = fileToIssues.get(element);
			return issues != null && !issues.isEmpty();
		}
		return false;
	}

	private String extractFileName(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return "Unknown";
		}
		int lastSeparator = Math.max(filePath.lastIndexOf('\\'), filePath.lastIndexOf('/'));
		return lastSeparator >= 0 ? filePath.substring(lastSeparator + 1) : filePath;
	}

	@Override
	public void dispose() {
		fileToIssues.clear();
	}
}
