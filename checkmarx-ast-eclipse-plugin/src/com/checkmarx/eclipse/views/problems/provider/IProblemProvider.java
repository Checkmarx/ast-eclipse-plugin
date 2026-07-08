package com.checkmarx.eclipse.views.problems.provider;

import java.util.List;

import com.checkmarx.eclipse.views.problems.model.ScanProblem;

/**
 * Supplies the set of {@link ScanProblem}s that should currently be shown in
 * the Problems View.
 *
 * <p>
 * This is the single seam that decouples <i>where findings come from</i> from
 * <i>how they are displayed</i>. Phase 1 ships {@code MockProblemProvider};
 * Phase 2 will add a scan-backed implementation. No other class in the
 * {@code problems} package needs to change when the source of data changes.
 * </p>
 */
public interface IProblemProvider {

	/**
	 * @return the current problems, never {@code null} (may be empty).
	 */
	List<ScanProblem> getProblems();
}
