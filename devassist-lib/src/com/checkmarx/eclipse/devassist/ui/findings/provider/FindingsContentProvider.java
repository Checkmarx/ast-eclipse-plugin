package com.checkmarx.eclipse.devassist.ui.findings.provider;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

import com.checkmarx.eclipse.devassist.ui.findings.model.FileNodeLabel;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanDetailWithPath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Content provider for the Findings tree viewer. Implements
 * {@link ITreeContentProvider} to provide hierarchical content structure.
 * Organizes scan issues by file path as parent nodes with individual issues as
 * children.
 */
public class FindingsContentProvider implements ITreeContentProvider {

	private final Map<ImageDescriptor, Image> imageCache = new HashMap<>();

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
	}

	@Override
	public Object[] getElements(Object inputElement) {
		if (inputElement instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, List<ScanIssue>> map = (Map<String, List<ScanIssue>>) inputElement;
			return map.entrySet().stream().map(entry -> {
				String fileName = getFileName(entry.getKey());
				Image fileIcon = getFileIcon(fileName);
				return new FileNodeLabel(fileName, entry.getKey(), entry.getValue(), fileIcon);
			}).toArray();
		}
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
				return imageCache.computeIfAbsent(imageDescriptor, descriptor -> descriptor.createImage());
			}
		} catch (Exception e) {
			CxLogger.error("Error retrieving file icon for " + fileName, e);
		}

		return null;
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof FileNodeLabel) {
			FileNodeLabel fileNode = (FileNodeLabel) parentElement;
			return fileNode.getIssues().stream()
					.map(issue -> new ScanDetailWithPath(issue, fileNode.getFilePath(), fileNode)).toArray();
		}
		return new Object[0];
	}

	@Override
	public Object getParent(Object element) {
		if (element instanceof ScanDetailWithPath) {
			return ((ScanDetailWithPath) element).getParentNode();
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
		// Dispose all cached native OS handles to prevent memory leaks
		for (Image image : imageCache.values()) {
			if (image != null && !image.isDisposed()) {
				image.dispose();
			}
		}
		imageCache.clear();
	}

}
