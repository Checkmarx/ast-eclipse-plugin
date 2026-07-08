package com.checkmarx.eclipse.views.problems.commands;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.views.problems.marker.ProblemMarkerConstants;

/**
 * Base class for the four Checkmarx context-menu command handlers.
 *
 * <p>
 * Centralizes the boilerplate every handler needs — turning the current
 * workbench selection into the list of Checkmarx {@link IMarker}s it applies to
 * — so concrete handlers only express <i>what</i> the action does. This is the
 * DRY/Template-Method seam that keeps individual actions tiny.
 * </p>
 *
 * <p>
 * Problems View selection elements are not {@code IMarker}s directly (they are
 * marker view items); {@link Adapters#adapt} is the platform-blessed way to get
 * the underlying marker, exactly as the {@code visibleWhen} {@code <adapt>}
 * clause does.
 * </p>
 */
public abstract class AbstractProblemHandler extends AbstractHandler {

	@Override
	public final Object execute(ExecutionEvent event) throws ExecutionException {
		List<IMarker> markers = extractCheckmarxMarkers(event);
		Shell shell = HandlerUtil.getActiveShell(event);
		perform(markers, shell);
		return null;
	}

	/**
	 * Perform the concrete action against the selected Checkmarx markers.
	 *
	 * @param markers the selected Checkmarx markers (never {@code null}).
	 * @param shell   active shell for any UI feedback (may be {@code null}).
	 */
	protected abstract void perform(List<IMarker> markers, Shell shell);

	/**
	 * Extract the Checkmarx markers from the event's current selection.
	 */
	protected List<IMarker> extractCheckmarxMarkers(ExecutionEvent event) {
		List<IMarker> markers = new ArrayList<>();
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (!(selection instanceof IStructuredSelection)) {
			return markers;
		}
		for (Object element : ((IStructuredSelection) selection).toList()) {
			IMarker marker = Adapters.adapt(element, IMarker.class);
			if (marker != null && isCheckmarxMarker(marker)) {
				markers.add(marker);
			}
		}
		return markers;
	}

	/**
	 * Placeholder feedback shared by the Phase-1 actions: logs the invocation and
	 * shows a summary dialog. Real actions will replace this body with actual
	 * behaviour (details, remediation, triage, ignore, ...).
	 */
	protected void showPlaceholder(Shell shell, String actionName, List<IMarker> markers) {
		String summary = actionName + " invoked on " + markers.size() + " Checkmarx finding(s):\n\n"
				+ describe(markers);
		CxLogger.info("[Checkmarx Problems] " + actionName + " on " + markers.size() + " marker(s).");
		if (shell != null) {
			MessageDialog.openInformation(shell, "Checkmarx: " + actionName, summary);
		}
	}

	private String describe(List<IMarker> markers) {
		StringBuilder sb = new StringBuilder();
		int shown = 0;
		for (IMarker marker : markers) {
			if (shown++ == 5) {
				sb.append("  ... and ").append(markers.size() - 5).append(" more");
				break;
			}
			sb.append("  • ")
					.append(marker.getAttribute(IMarker.MESSAGE, "(no message)"))
					.append("  [")
					.append(marker.getAttribute(ProblemMarkerConstants.ATTR_SEVERITY, "?"))
					.append("]\n");
		}
		return sb.toString();
	}

	private boolean isCheckmarxMarker(IMarker marker) {
		try {
			return marker.exists() && marker.isSubtypeOf(ProblemMarkerConstants.MARKER_TYPE);
		} catch (Exception e) {
			return false;
		}
	}
}
