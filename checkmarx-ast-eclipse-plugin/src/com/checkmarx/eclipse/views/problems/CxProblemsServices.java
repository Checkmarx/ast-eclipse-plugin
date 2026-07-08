package com.checkmarx.eclipse.views.problems;

import com.checkmarx.eclipse.views.problems.marker.IMarkerManager;
import com.checkmarx.eclipse.views.problems.marker.MarkerManager;
import com.checkmarx.eclipse.views.problems.navigation.ProblemNavigator;
import com.checkmarx.eclipse.views.problems.provider.IProblemProvider;
import com.checkmarx.eclipse.views.problems.provider.MockProblemProvider;
import com.checkmarx.eclipse.views.problems.publisher.IProblemsPublisher;
import com.checkmarx.eclipse.views.problems.publisher.ProblemsViewPublisher;
import com.checkmarx.eclipse.views.problems.util.WorkspaceFileResolver;

/**
 * Composition root / poor-man's dependency-injection container for the Problems
 * View integration.
 *
 * <p>
 * This is the single place that decides which concrete collaborators are wired
 * together. Because Eclipse instantiates handlers and other extensions via
 * their no-arg constructors, they cannot receive dependencies through
 * constructor injection; instead they ask this factory for the already-wired
 * services. Instances are created lazily and cached.
 * </p>
 *
 * <p>
 * <b>Swapping the mock for real scan results is a one-line change here:</b>
 * replace {@link MockProblemProvider} with the scan-backed provider. Nothing
 * else in the package needs to change.
 * </p>
 */
public final class CxProblemsServices {

	private static WorkspaceFileResolver fileResolver;
	private static IProblemProvider problemProvider;
	private static IMarkerManager markerManager;
	private static IProblemsPublisher publisher;
	private static ProblemNavigator navigator;

	private CxProblemsServices() {
		// factory
	}

	public static synchronized WorkspaceFileResolver fileResolver() {
		if (fileResolver == null) {
			fileResolver = new WorkspaceFileResolver();
		}
		return fileResolver;
	}

	public static synchronized IProblemProvider problemProvider() {
		if (problemProvider == null) {
			// --- Phase 1: mock data. Replace with the scan-backed provider later. ---
			problemProvider = new MockProblemProvider();
		}
		return problemProvider;
	}

	public static synchronized IMarkerManager markerManager() {
		if (markerManager == null) {
			markerManager = new MarkerManager(fileResolver());
		}
		return markerManager;
	}

	public static synchronized IProblemsPublisher publisher() {
		if (publisher == null) {
			publisher = new ProblemsViewPublisher(problemProvider(), markerManager());
		}
		return publisher;
	}

	public static synchronized ProblemNavigator navigator() {
		if (navigator == null) {
			navigator = new ProblemNavigator();
		}
		return navigator;
	}
}
