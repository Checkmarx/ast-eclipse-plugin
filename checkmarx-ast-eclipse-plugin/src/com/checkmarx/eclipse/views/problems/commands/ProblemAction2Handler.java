package com.checkmarx.eclipse.views.problems.commands;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.widgets.Shell;

/**
 * Placeholder handler for context-menu Action 2 (e.g. future "Remediate
 * Finding"). Wired to command
 * {@code com.checkmarx.eclipse.problems.command.action2}.
 */
public class ProblemAction2Handler extends AbstractProblemHandler {

	@Override
	protected void perform(List<IMarker> markers, Shell shell) {
		showPlaceholder(shell, "Action 2", markers);
	}
}
