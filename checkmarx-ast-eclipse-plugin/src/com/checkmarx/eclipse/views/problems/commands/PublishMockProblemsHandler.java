package com.checkmarx.eclipse.views.problems.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;

import com.checkmarx.eclipse.views.problems.CxProblemsServices;

/**
 * Command handler that (re)publishes the mock findings into the Problems View.
 * Wired to command {@code com.checkmarx.eclipse.problems.command.publishMock}.
 *
 * <p>
 * It delegates entirely to the wired {@code IProblemsPublisher}; the handler
 * itself holds no logic beyond the command &rarr; service hop, so replacing the
 * mock provider does not touch this class.
 * </p>
 */
public class PublishMockProblemsHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) {
		CxProblemsServices.publisher().publish();
		return null;
	}
}
