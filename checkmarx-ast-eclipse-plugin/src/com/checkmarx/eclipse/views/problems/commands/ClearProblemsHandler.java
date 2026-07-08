package com.checkmarx.eclipse.views.problems.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;

import com.checkmarx.eclipse.views.problems.CxProblemsServices;

/**
 * Command handler that removes all Checkmarx problems from the Problems View.
 * Wired to command {@code com.checkmarx.eclipse.problems.command.clear}.
 */
public class ClearProblemsHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) {
		CxProblemsServices.publisher().clear();
		return null;
	}
}
