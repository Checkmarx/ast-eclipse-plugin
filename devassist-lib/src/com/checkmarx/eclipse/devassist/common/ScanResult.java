package com.checkmarx.eclipse.devassist.common;

import com.checkmarx.eclipse.devassist.model.ScanIssue;
import java.util.List;

/**
 * Interface for a scan result wrapper.
 *
 * Adaptor classes implement this interface to wrap raw scanner results
 * and provide conversion to standardized ScanIssue objects.
 *
 * @param <T> Type of raw scanner result (e.g., OssRealtimeResults, SecretsRealtimeResults)
 */
public interface ScanResult<T> {

	/**
	 * Get the raw scan results from the scanner.
	 *
	 * @return Raw scanner results of type T
	 */
	T getResults();

	/**
	 * Get the standardized list of scan issues from the raw results.
	 *
	 * This converts the scanner-specific result format into a uniform
	 * list of ScanIssue objects that can be displayed in the UI.
	 *
	 * @return List of ScanIssue objects
	 */
	List<ScanIssue> getIssues();
}
