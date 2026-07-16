package com.checkmarx.eclipse.devassist.ui.findings.ignored;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.checkmarx.eclipse.devassist.problems.model.ScanProblem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Content provider for ignored problems tree view. Organizes problems by file.
 */
public class IgnoredProblemsContentProvider implements ITreeContentProvider {

	private Map<String, List<ScanProblem>> fileToProblems = new HashMap<>();

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		fileToProblems.clear();
		if (newInput instanceof List<?>) {
			@SuppressWarnings("unchecked")
			List<ScanProblem> problems = (List<ScanProblem>) newInput;
			for (ScanProblem problem : problems) {
				String fileName = extractFileName(problem.getFileName());
				fileToProblems.computeIfAbsent(fileName, k -> new ArrayList<>()).add(problem);
			}
		}
	}

	@Override
	public Object[] getElements(Object inputElement) {
		return fileToProblems.keySet().toArray();
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof String) {
			List<ScanProblem> problems = fileToProblems.get(parentElement);
			return problems != null ? problems.toArray() : new Object[0];
		}
		return new Object[0];
	}

	@Override
	public Object getParent(Object element) {
		if (element instanceof ScanProblem) {
			ScanProblem problem = (ScanProblem) element;
			return extractFileName(problem.getFileName());
		}
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		if (element instanceof String) {
			List<ScanProblem> problems = fileToProblems.get(element);
			return problems != null && !problems.isEmpty();
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
		fileToProblems.clear();
	}
}
