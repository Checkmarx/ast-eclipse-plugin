package com.checkmarx.eclipse.views.problems.commands;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.widgets.Shell;

/**
 * Placeholder handler for context-menu Action 3 (e.g. future "Change Status").
 * Wired to command {@code com.checkmarx.eclipse.problems.command.action3}.
 */
public class ProblemAction3Handler extends AbstractProblemHandler {

	@Override
	protected void perform(List<IMarker> markers, Shell shell) {
		showPlaceholder(shell, "Action 3", markers);
	}
}
