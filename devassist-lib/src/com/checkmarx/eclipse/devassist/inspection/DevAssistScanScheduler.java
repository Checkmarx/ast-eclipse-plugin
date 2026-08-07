package com.checkmarx.eclipse.devassist.inspection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

import com.checkmarx.eclipse.devassist.problems.ProblemHelper;
import com.checkmarx.eclipse.devassist.ui.findings.realtime.RealTimeScanJob;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Scheduler that wraps and coordinates RealTimeScanJob for background file scanning.
 *
 * Responsibilities:
 * - Manage scheduling of real-time scans with debounce
 * - Track pending scans per file
 * - Cancel pending scans when needed
 * - Provide clean API for scan orchestration
 *
 * Wraps Eclipse RealTimeScanJob which extends Job for background execution.
 */
public class DevAssistScanScheduler {

	private static final String LOG_TAG = "[SCAN-SCHEDULER]";
	private static final long DEFAULT_DEBOUNCE_DELAY_MS = 1000L;

	// Track pending jobs per file path
	private final Map<String, RealTimeScanJob> pendingScans = new ConcurrentHashMap<>();

	/**
	 * Schedule a scan for a file with default debounce delay (1 second).
	 *
	 * If a scan is already pending for this file, returns false.
	 * Use reschedule() to cancel and restart with new delay.
	 *
	 * @param file File to scan
	 * @param problemHelper Problem context (unused in current impl, for alignment)
	 * @return true if scheduled, false if already pending
	 */
	public boolean scheduleInspection(IFile file, ProblemHelper problemHelper) {
		return scheduleInspection(file, DEFAULT_DEBOUNCE_DELAY_MS);
	}

	/**
	 * Schedule a scan for a file with custom debounce delay.
	 *
	 * @param file File to scan
	 * @param delayMs Debounce delay in milliseconds
	 * @return true if scheduled, false if already pending
	 */
	public boolean scheduleInspection(IFile file, long delayMs) {
		if (file == null) {
			return false;
		}

		String filePath = file.getLocation().toOSString();

		// Check if already pending
		if (pendingScans.containsKey(filePath)) {
			CxLogger.info(LOG_TAG + " Scan already pending for: " + filePath);
			return false;
		}

		try {
			// Create new job
			RealTimeScanJob scanJob = new RealTimeScanJob(file, file.getName());

			// Track it
			pendingScans.put(filePath, scanJob);

			// Schedule with debounce delay
			scanJob.schedule(delayMs);

			CxLogger.info(LOG_TAG + " Scheduled scan for: " + filePath +
				" (delay=" + delayMs + "ms)");
			return true;

		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Failed to schedule scan: " + e.getMessage(), e);
			pendingScans.remove(filePath);
			return false;
		}
	}

	/**
	 * Reschedule a pending scan (cancel current, start new with delay).
	 *
	 * Used by CheckmarxDocumentListener when user types:
	 * - First keystroke: schedule with 1s delay
	 * - While typing: reschedule (cancel, start new 1s timer)
	 * - After user pauses: job runs
	 *
	 * @param file File to reschedule
	 * @param delayMs New debounce delay
	 * @return true if rescheduled, false if no pending job
	 */
	public boolean rescheduleInspection(IFile file, long delayMs) {
		if (file == null) {
			return false;
		}

		String filePath = file.getLocation().toOSString();
		RealTimeScanJob existingJob = pendingScans.get(filePath);

		if (existingJob == null) {
			// No pending job, schedule new one
			return scheduleInspection(file, delayMs);
		}

		try {
			// Cancel current
			existingJob.cancel();

			// Reschedule with new delay
			existingJob.reschedule(delayMs);

			CxLogger.info(LOG_TAG + " Rescheduled scan for: " + filePath +
				" (delay=" + delayMs + "ms)");
			return true;

		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Failed to reschedule: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Cancel a pending scan for a file.
	 *
	 * @param file File to cancel scan for
	 * @return true if cancelled, false if no pending scan
	 */
	public boolean cancelScheduledInspection(IFile file) {
		if (file == null) {
			return false;
		}

		String filePath = file.getLocation().toOSString();
		RealTimeScanJob job = pendingScans.remove(filePath);

		if (job == null) {
			return false;
		}

		try {
			job.cancel();
			CxLogger.info(LOG_TAG + " Cancelled scan for: " + filePath);
			return true;
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error cancelling scan: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Trigger inspection on the entire project (force re-inspection).
	 *
	 * @param project Project to inspect
	 */
	public void triggerInspection(IProject project) {
		if (project == null) {
			return;
		}
		CxLogger.info(LOG_TAG + " Triggering inspection for project: " + project.getName());
		// Future: force re-inspect all files in project
	}

	/**
	 * Get number of pending scans.
	 *
	 * @return Count of scheduled but not yet running scans
	 */
	public int getPendingScansCount() {
		return pendingScans.size();
	}

	/**
	 * Get statistics for debugging.
	 *
	 * @return Summary string
	 */
	public String getStatistics() {
		return "Pending scans: " + pendingScans.size() +
			", Tracked files: " + pendingScans.keySet();
	}
}
