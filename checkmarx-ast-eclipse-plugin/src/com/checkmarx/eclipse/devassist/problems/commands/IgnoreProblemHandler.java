package com.checkmarx.eclipse.devassist.problems.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.checkmarx.eclipse.devassist.problems.CxProblemsServices;
import com.checkmarx.eclipse.devassist.ui.findings.ignored.IgnoredProblemsStore;
import com.checkmarx.eclipse.devassist.problems.model.ScanProblem;

/**
 * Handler for ignoring a problem from the native Problems View.
 * Moves the problem from active to ignored list and republishes.
 */
public class IgnoreProblemHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			// Get the problem from selection (passed via command parameter or context)
			ScanProblem problemToIgnore = getProblemFromContext(event);

			if (problemToIgnore != null) {
				System.out.println("[IGNORE-HANDLER] Ignoring problem: " + problemToIgnore.getId() +
						" - " + problemToIgnore.getMessage());

				// Add to ignored store
				IgnoredProblemsStore ignoredStore = IgnoredProblemsStore.getInstance();
				ignoredStore.ignoreProblem(problemToIgnore.getId());

				// Republish problems (will filter out ignored ones)
				CxProblemsServices.publisher().publish();

				System.out.println("[IGNORE-HANDLER] ✓ Problem ignored and native Problems View updated");
			} else {
				System.out.println("[IGNORE-HANDLER] No problem found in context");
			}

		} catch (Exception e) {
			System.err.println("[IGNORE-HANDLER] Error ignoring problem: " + e.getMessage());
			e.printStackTrace();
			throw new ExecutionException("Failed to ignore problem", e);
		}

		return null;
	}

	/**
	 * Extract the ScanProblem from the command context.
	 * This would typically be passed from the right-click context menu.
	 */
	private ScanProblem getProblemFromContext(ExecutionEvent event) {
		try {
			// Try to get from command parameter (set by context menu)
			String problemId = event.getParameter("problemId");
			String message = event.getParameter("message");
			String fileName = event.getParameter("fileName");
			String lineStr = event.getParameter("line");

			if (problemId != null) {
				int line = 0;
				try {
					line = Integer.parseInt(lineStr != null ? lineStr : "0");
				} catch (NumberFormatException e) {
					// Use default line number
				}

				ScanProblem problem = new ScanProblem.Builder(problemId)
						.message(message != null ? message : "Unknown")
						.fileName(fileName != null ? fileName : "Unknown")
						.line(line)
						.build();

				System.out.println("[IGNORE-HANDLER] Problem from context: " + problemId);
				return problem;
			}
		} catch (Exception e) {
			System.err.println("[IGNORE-HANDLER] Error extracting problem from context: " + e.getMessage());
		}

		return null;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
