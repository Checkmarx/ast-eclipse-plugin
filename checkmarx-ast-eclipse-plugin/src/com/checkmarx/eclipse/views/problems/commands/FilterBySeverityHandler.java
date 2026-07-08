package com.checkmarx.eclipse.views.problems.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.views.problems.filter.FilterStateManager;

/**
 * Handler for severity filter buttons in Problems View toolbar.
 * Manages filter state and triggers view refresh.
 */
public class FilterBySeverityHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// Get the severity from command parameter
		String severity = event.getParameter("com.checkmarx.eclipse.severity");

		System.out.println("[CX-FILTER-HANDLER] Filter requested for severity: " + severity);

		try {
			FilterStateManager stateManager = FilterStateManager.getInstance();
			ProblemsViewFilterManager filterManager = ProblemsViewFilterManager.getInstance();

			// Special case: "ALL" clears all filters
			if ("ALL".equalsIgnoreCase(severity)) {
				stateManager.clearAll();
				System.out.println("[CX-FILTER-HANDLER] ✓ All filters cleared");
			} else {
				// For other severities, toggle the selection
				Severity sev = Severity.valueOf(severity.toUpperCase());
				stateManager.toggleSeverity(sev);
				System.out.println("[CX-FILTER-HANDLER] ✓ Filter toggled: " + severity);
			}

			// Refresh the Problems View with new filter state
			// (FilterStatusLabelProvider will auto-update via FilterStateManager listener)
			filterManager.refreshProblemsView();

		} catch (Exception e) {
			System.err.println("[CX-FILTER-HANDLER] Error applying filter: " + e.getMessage());
			e.printStackTrace();
		}

		return null;
	}
}
