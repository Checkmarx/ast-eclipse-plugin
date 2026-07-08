package com.checkmarx.eclipse.views.problems.commands;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.widgets.Shell;

/**
 * Placeholder handler for context-menu Action 4 (e.g. future "Ignore Finding").
 * Wired to command {@code com.checkmarx.eclipse.problems.command.action4}.
 */
public class ProblemAction4Handler extends AbstractProblemHandler {

	@Override
	protected void perform(List<IMarker> markers, Shell shell) {
		showPlaceholder(shell, "Action 4", markers);
	}
}
