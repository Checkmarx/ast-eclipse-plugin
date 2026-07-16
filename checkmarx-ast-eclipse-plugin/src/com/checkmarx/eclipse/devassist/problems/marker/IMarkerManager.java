package com.checkmarx.eclipse.devassist.problems.marker;

import java.util.List;

import com.checkmarx.eclipse.devassist.problems.model.ScanProblem;

/**
 * Owns the lifecycle of Checkmarx {@code IMarker}s in the workspace.
 *
 * <p>
 * Implementations are responsible for the marker-side performance practices
 * described in the design document: workspace batching, non-persistent
 * (transient) markers, and per-resource deletion.
 * </p>
 */
public interface IMarkerManager {

	/**
	 * Create one marker per {@link ScanProblem}. Implementations should perform
	 * all creation inside a single workspace operation (batched) so the Problems
	 * View is notified once rather than once per marker.
	 *
	 * @param problems findings to publish; {@code null}/empty is a no-op.
	 */
	void createMarkers(List<ScanProblem> problems);

	/**
	 * Delete every Checkmarx-owned marker previously created by this plugin.
	 */
	void clearMarkers();

	/**
	 * Delete marker for a specific problem ID. Used when ignoring problems.
	 *
	 * @param problemId the finding ID to delete marker for.
	 */
	void deleteMarkerForProblem(String problemId);
}
