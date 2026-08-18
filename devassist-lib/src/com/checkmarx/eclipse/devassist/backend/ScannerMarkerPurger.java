package com.checkmarx.eclipse.devassist.backend;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType;
import com.checkmarx.eclipse.devassist.problems.ProblemDecorator;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper;

/**
 * Purges findings for a scanner that has just been disabled.
 *
 * When a scanner is disabled, ScannerFactory stops running it for future scans, but
 * results it already produced (cached ScanIssues, editor decorations, and IMarkers)
 * remain until something removes them. This purges all three, workspace-wide, so a
 * disabled scanner's findings disappear immediately.
 */
public class ScannerMarkerPurger {

	private static final String LOG_TAG = "[SCANNER-MARKER-PURGER]";
	private static final String MARKER_TYPE = "com.checkmarx.eclipse.plugin.checkmarxProblemMarker";
	private static final String PLUGIN_ID = "com.checkmarx.eclipse.plugin";
	private static final QualifiedName PROBLEM_HOLDER_KEY = new QualifiedName(PLUGIN_ID, "problem-holder");

	private ScannerMarkerPurger() {
	}

	/**
	 * Remove all markers, cached issues, and editor decorations produced by the given
	 * scanner, across every open project in the workspace.
	 *
	 * @param type Scanner type that was just disabled
	 */
	public static void purgeScanner(ScannerType type) {
		if (type == null) {
			return;
		}

		String scannerName = type.name();
		purgeMarkers(scannerName);
		purgeCacheAndDecorations(scannerName);
	}

	private static void purgeMarkers(String scannerName) {
		try {
			IMarker[] markers = ResourcesPlugin.getWorkspace().getRoot()
				.findMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
			int deleted = 0;
			for (IMarker marker : markers) {
				String engine = marker.getAttribute(MarkerIssueMapper.ATTR_SCAN_ENGINE, null);
				if (scannerName.equals(engine)) {
					marker.delete();
					deleted++;
				}
			}
			CxLogger.info(LOG_TAG + " Deleted " + deleted + " markers for scanner: " + scannerName);
		} catch (CoreException e) {
			CxLogger.error(LOG_TAG + " Error deleting markers for scanner " + scannerName + ": " + e.getMessage(), e);
		}
	}

	private static void purgeCacheAndDecorations(String scannerName) {
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (!project.isOpen()) {
				continue;
			}
			try {
				ProblemHolderService problemHolder = (ProblemHolderService) project.getSessionProperty(PROBLEM_HOLDER_KEY);
				if (problemHolder == null) {
					continue;
				}

				List<String> affectedFiles = problemHolder.removeAllIssuesForScanner(scannerName);
				for (String filePath : affectedFiles) {
					IFile[] files = ResourcesPlugin.getWorkspace().getRoot()
						.findFilesForLocation(org.eclipse.core.runtime.Path.fromOSString(filePath));
					IFile file = (files != null && files.length > 0) ? files[0] : null;
					if (file != null) {
						List<com.checkmarx.eclipse.devassist.model.ScanIssue> remaining =
							problemHolder.getScanIssuesByFile(filePath);
						ProblemDecorator.decorateEditor(file, remaining);
					}
				}
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error purging cache for project " + project.getName() + ": " + e.getMessage());
			}
		}
	}
}
