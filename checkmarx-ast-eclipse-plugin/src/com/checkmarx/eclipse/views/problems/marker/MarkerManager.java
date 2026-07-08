package com.checkmarx.eclipse.views.problems.marker;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.views.findings.marker.MarkerIssueMapper;
import com.checkmarx.eclipse.views.problems.model.ScanProblem;
import com.checkmarx.eclipse.views.problems.util.WorkspaceFileResolver;

/**
 * Default {@link IMarkerManager} that maps {@link ScanProblem}s onto Eclipse
 * {@code IMarker}s of our custom {@link ProblemMarkerConstants#MARKER_TYPE}.
 *
 * <p>
 * Applies the design-document optimizations:
 * </p>
 * <ul>
 * <li><b>Batching</b> — all create/delete operations run inside a single
 * {@link IWorkspace#run} block with {@link IWorkspace#AVOID_UPDATE}, so listeners
 * (and therefore the Problems View) are notified once per cycle.</li>
 * <li><b>Transient markers</b> — the marker type is declared non-persistent in
 * {@code plugin.xml} and we also set {@link IMarker#TRANSIENT} defensively, so
 * ephemeral findings are never serialized to disk.</li>
 * <li><b>Per-resource deletion</b> — clearing uses {@link IResource#DEPTH_ZERO}
 * against the exact resources that carry our marker type.</li>
 * <li><b>Cached file lookup</b> — delegated to {@link WorkspaceFileResolver}.</li>
 * </ul>
 *
 * <p>
 * When a finding's file cannot be resolved in the workspace (expected for the
 * fictional mock file names), the marker is attached to the workspace root so
 * the finding is still visible in the Problems View. Native line navigation is
 * only available for markers attached to a concrete file.
 * </p>
 */
public class MarkerManager implements IMarkerManager {

	private final WorkspaceFileResolver fileResolver;

	public MarkerManager(WorkspaceFileResolver fileResolver) {
		this.fileResolver = fileResolver;
	}

	@Override
	public void createMarkers(List<ScanProblem> problems) {
		if (problems == null || problems.isEmpty()) {
			System.out.println("[PROBLEMS] No problems to create markers for");
			return;
		}

		System.out.println("[PROBLEMS] Creating markers for " + problems.size() + " problems...");

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final IWorkspaceRoot root = workspace.getRoot();
		fileResolver.clearCache();

		// int[] holders so the batched runnable (a lambda) can accumulate counts;
		// captured locals must be effectively final.
		final int[] created = {0};
		final int[] attachedToRoot = {0};
		try {
			workspace.run(monitor -> {
				for (ScanProblem problem : problems) {
					List<IFile> files = fileResolver.resolve(problem.getFileName());
					if (files.isEmpty()) {
						// No matching workspace file: attach to root so it's still visible in Problems View
						System.out.println("[PROBLEMS] File not found: " + problem.getFileName() + " - attaching to workspace root");
						createMarker(root, problem, false);
						attachedToRoot[0]++;
						created[0]++;
					} else {
						for (IFile file : files) {
							System.out.println("[PROBLEMS] Creating marker for: " + problem.getFileName() + " - " + problem.getMessage());
							createMarker(file, problem, true);
							created[0]++;
						}
					}
				}
			}, root, 0, null);
			System.out.println("[PROBLEMS] ✓ Created " + created[0] + " total markers");
			System.out.println("[PROBLEMS]   - Attached to files: " + (created[0] - attachedToRoot[0]));
			System.out.println("[PROBLEMS]   - Attached to workspace root: " + attachedToRoot[0]);
			CxLogger.info("Checkmarx Problems View: created " + created[0] + " marker(s); "
					+ attachedToRoot[0] + " attached to workspace root (file not found).");
		} catch (CoreException e) {
			System.out.println("[PROBLEMS] ✗ Error creating markers: " + e.getMessage());
			CxLogger.error(String.format(PluginConstants.ERROR_OPENING_FILE, e.getMessage()), e);
		}
	}

	@Override
	public void clearMarkers() {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final IWorkspaceRoot root = workspace.getRoot();
		try {
			workspace.run(monitor -> {
				// Our custom marker type is only ever created by this plugin, so a
				// single type-scoped, infinite-depth query is both correct and cheap
				// (it does not touch unrelated JDT/build markers), then each owning
				// resource is cleared with DEPTH_ZERO.
				IMarker[] markers = root.findMarkers(ProblemMarkerConstants.MARKER_TYPE, true,
						IResource.DEPTH_INFINITE);
				for (IMarker marker : markers) {
					if (marker.exists()) {
						marker.delete();
					}
				}
			}, root, 0, null);
		} catch (CoreException e) {
			CxLogger.error(String.format(PluginConstants.ERROR_FINDING_OR_DELETING_MARKER, e.getMessage()), e);
		}
	}

	/**
	 * Delete marker for a specific problem ID. Used when ignoring problems.
	 */
	public void deleteMarkerForProblem(String problemId) {
		if (problemId == null) {
			System.out.println("[PROBLEMS-MARKER] Cannot delete marker: null problemId");
			return;
		}

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final IWorkspaceRoot root = workspace.getRoot();
		try {
			workspace.run(monitor -> {
				IMarker[] markers = root.findMarkers(ProblemMarkerConstants.MARKER_TYPE, true,
						IResource.DEPTH_INFINITE);
				for (IMarker marker : markers) {
					try {
						Object findingId = marker.getAttribute(ProblemMarkerConstants.ATTR_FINDING_ID);
						if (problemId.equals(findingId) && marker.exists()) {
							System.out.println("[PROBLEMS-MARKER] Deleting marker for problem: " + problemId);
							marker.delete();
							return; // Found and deleted, exit
						}
					} catch (CoreException e) {
						// Continue to next marker
					}
				}
				System.out.println("[PROBLEMS-MARKER] No marker found for problem: " + problemId);
			}, root, 0, null);
		} catch (CoreException e) {
			System.err.println("[PROBLEMS-MARKER] Error deleting marker: " + e.getMessage());
			CxLogger.error(String.format(PluginConstants.ERROR_FINDING_OR_DELETING_MARKER, e.getMessage()), e);
		}
	}

	private void createMarker(IResource resource, ScanProblem problem, boolean withLine) throws CoreException {
		if (problem == null || problem.getSeverity() == null) {
			System.out.println("[PROBLEMS-MARKER] WARNING: Null problem or severity, skipping marker creation");
			return;
		}

		IMarker marker = resource.createMarker(ProblemMarkerConstants.MARKER_TYPE);
		marker.setAttribute(IMarker.TRANSIENT, true);
		marker.setAttribute(IMarker.SOURCE_ID, ProblemMarkerConstants.SOURCE_ID);

		// Use MarkerIssueMapper to populate standard attributes
		MarkerIssueMapper.populateMarkerFromProblem(marker, problem);
		System.out.println("[PROBLEMS-MARKER] Created marker: " + problem.getMessage());

		if (withLine) {
			int lineNum = problem.getLine();
			int column = problem.getColumn(); // 1-based
			marker.setAttribute(IMarker.LINE_NUMBER, lineNum);
			System.out.println("[PROBLEMS-MARKER]   Setting LINE_NUMBER to: " + lineNum);

			// Set character range for the red underline annotation
			// We calculate character offsets by reading the file
			try {
				if (resource instanceof IFile) {
					IFile file = (IFile) resource;
					String content = new String(file.getContents().readAllBytes());
					int charStart = calculateCharOffsetForLine(content, lineNum, column);
					// Underline a meaningful length (at least 10 chars, or to end of line)
					int charEnd = calculateEndOfLine(content, charStart);
					if (charEnd <= charStart) {
						charEnd = charStart + 10; // fallback
					}
					// Bounds check
					charEnd = Math.min(charEnd, content.length());
					marker.setAttribute(IMarker.CHAR_START, charStart);
					marker.setAttribute(IMarker.CHAR_END, charEnd);
					System.out.println("[PROBLEMS-MARKER]   Setting CHAR_START=" + charStart + ", CHAR_END=" + charEnd);
				}
			} catch (Exception e) {
				System.out.println("[PROBLEMS-MARKER] WARNING: Could not set character range: " + e.getMessage());
			}

			marker.setAttribute(IMarker.LOCATION,
					String.format(ProblemMarkerConstants.LOCATION_LINE_PATTERN, problem.getLine()));
		} else {
			// Preserve the intended location even when we cannot attach to the file.
			marker.setAttribute(IMarker.LOCATION,
					problem.getFileName() + " " + String.format(ProblemMarkerConstants.LOCATION_LINE_PATTERN,
							problem.getLine()));
		}
	}

	/**
	 * Calculate the character offset (0-based) for a given line and column (1-based).
	 */
	private int calculateCharOffsetForLine(String content, int lineNum, int column) {
		int currentLine = 1;
		int offset = 0;
		for (int i = 0; i < content.length(); i++) {
			if (currentLine == lineNum) {
				// Found the target line, skip leading whitespace and return first non-whitespace char
				int lineStart = offset;
				while (lineStart < content.length() && Character.isWhitespace(content.charAt(lineStart)) && content.charAt(lineStart) != '\n') {
					lineStart++;
				}
				return lineStart;
			}
			if (content.charAt(i) == '\n') {
				currentLine++;
				offset = i + 1;
			}
		}
		// Fallback: return start of content if line not found
		return 0;
	}

	/**
	 * Find the end of the line (first newline after the given offset, or end of content).
	 */
	private int calculateEndOfLine(String content, int startOffset) {
		for (int i = startOffset; i < content.length(); i++) {
			if (content.charAt(i) == '\n') {
				return i;
			}
		}
		return content.length();
	}

}
