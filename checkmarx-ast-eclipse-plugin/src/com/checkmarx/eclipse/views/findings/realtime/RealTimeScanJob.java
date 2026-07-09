package com.checkmarx.eclipse.views.findings.realtime;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

/**
 * Real-time scan job with debounce support.
 *
 * When the user edits a file, CheckmarxDocumentListener calls reschedule() repeatedly
 * as the user types. This job cancels the previous scheduled execution and starts a
 * new 1-second timer, so the scan only runs after the user pauses typing.
 *
 * Equivalent to:
 * - JetBrains' real-time inspection pipeline (with debounce built-in)
 * - Eclipse's incremental builder, but for on-demand scanning
 *
 * This is a background Job, so it runs off the UI thread and won't freeze the editor.
 */
public class RealTimeScanJob extends Job {

	private final IFile file;
	private final String fileName;

	// Store the timestamp when the user last made changes
	private long lastChangeTime = System.currentTimeMillis();

	/**
	 * Create a real-time scan job for a specific file.
	 *
	 * @param file the IFile resource to scan
	 * @param fileName the file name (for logging)
	 */
	public RealTimeScanJob(IFile file, String fileName) {
		super("Checkmarx Real-Time Scan: " + fileName);
		this.file = file;
		this.fileName = fileName;

		// Configure job properties for background execution
		setSystem(false); // Show in progress view
		setPriority(Job.DECORATE); // Lower priority than user interactions
		setUser(false); // Not a user-initiated job

		System.out.println("[REALTIME] ✓ RealTimeScanJob created for: " + fileName);
	}

	/**
	 * Get the Eclipse log for this plugin.
	 */
	private ILog getLog() {
		return Platform.getLog(getClass());
	}

	/**
	 * Reschedule this job with a given delay (debounce).
	 *
	 * If the job is already scheduled, it is cancelled and rescheduled with a new delay.
	 * This ensures the scan only runs after the user stops typing for the specified delay.
	 *
	 * @param delayMs delay in milliseconds before the job should run
	 */
	public synchronized void reschedule(long delayMs) {
		// Update the last change time
		this.lastChangeTime = System.currentTimeMillis();

		// Cancel any previously scheduled execution
		cancel();

		// Schedule the job to run after the delay
		schedule(delayMs);

		System.out.println("[REALTIME] Job rescheduled for: " + fileName + " (delay=" + delayMs + "ms)");
	}

	/**
	 * Run the real-time scan.
	 *
	 * This method is called by the Eclipse Jobs framework after the debounce delay expires.
	 * It performs the actual scanning logic.
	 *
	 * Currently, this just logs a message. In production, you would:
	 * 1. Parse the file
	 * 2. Run security checks (synchronously or via backend API)
	 * 3. Create markers for problems found
	 * 4. Update the editor decoration
	 *
	 * @param monitor progress monitor for cancellation support
	 * @return Status.OK if successful, Status.CANCEL if cancelled
	 */
	@Override
	protected IStatus run(IProgressMonitor monitor) {
		try {
			// Check if file still exists and is accessible
			if (file == null || !file.exists()) {
				System.out.println("[REALTIME] ✗ File no longer exists: " + fileName);
				return Status.CANCEL_STATUS;
			}

			// Check if the job was cancelled while waiting
			if (monitor.isCanceled()) {
				System.out.println("[REALTIME] ✗ Scan cancelled for: " + fileName);
				return Status.CANCEL_STATUS;
			}

			// Log to Eclipse Error Log
			String logMessage = "I am watching what user is typing in file: " + fileName;
			getLog().log(new Status(Status.INFO, "com.checkmarx.eclipse.plugin", logMessage));

			System.out.println("[REALTIME] ════════════════════════════════════════");
			System.out.println("[REALTIME] INFO: I am watching what user is typing.");
			System.out.println("[REALTIME] ════════════════════════════════════════");
			System.out.println("[REALTIME] File: " + fileName);
			System.out.println("[REALTIME] Last change: " + (System.currentTimeMillis() - lastChangeTime) + "ms ago");
			System.out.println("[REALTIME] ════════════════════════════════════════");

			// TODO: Replace the above log statement with actual scanning logic:
			// 1. Parse the file content:
			//    String content = file.getLocation().toFile().getContent();
			//
			// 2. Call backend API (asynchronously):
			//    List<SecurityFinding> findings = checkmarxService.scanFileAsync(file, content);
			//
			// 3. Create markers for each finding:
			//    for (SecurityFinding finding : findings) {
			//        IMarker marker = file.createMarker(CHECKMARX_MARKER_TYPE);
			//        marker.setAttribute(IMarker.LINE_NUMBER, finding.getLine());
			//        marker.setAttribute(IMarker.MESSAGE, finding.getMessage());
			//    }
			//
			// 4. Update editor UI (back on UI thread):
			//    PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			//        refreshEditor(file);
			//    });

			return Status.OK_STATUS;

		} catch (Exception e) {
			System.err.println("[REALTIME] ✗ Error during real-time scan: " + e.getMessage());
			e.printStackTrace();
			// Return error status but don't fail the job permanently
			return new Status(IStatus.WARNING, "com.checkmarx.eclipse.plugin",
					"Real-time scan failed for " + fileName, e);
		}
	}

	@Override
	public boolean belongsTo(Object family) {
		// Group all Checkmarx real-time scan jobs together
		// This allows Eclipse to cancel all scans at once if needed
		return family != null && family.equals("com.checkmarx.realtime.scan");
	}

	/**
	 * Called when the job is cancelled.
	 * Cleanup any resources if needed.
	 */
	@Override
	protected void canceling() {
		System.out.println("[REALTIME] Cancelling scan for: " + fileName);
		super.canceling();
	}

	public String getFileName() {
		return fileName;
	}

	public IFile getFile() {
		return file;
	}
}
