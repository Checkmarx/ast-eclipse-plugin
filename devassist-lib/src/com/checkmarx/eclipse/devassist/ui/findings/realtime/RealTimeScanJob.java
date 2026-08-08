package com.checkmarx.eclipse.devassist.ui.findings.realtime;

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
				
				return Status.CANCEL_STATUS;
			}

			// Check if the job was cancelled while waiting
			if (monitor.isCanceled()) {
				
				return Status.CANCEL_STATUS;
			}

			// **STEP 1: Check authentication status**
			if (!isUserAuthenticated()) {
				
				
				return Status.OK_STATUS; // Return OK but don't scan
			}






			// Call our backend scanners via ScanManager
			try {
				org.eclipse.core.resources.IProject project = file.getProject();
				if (project == null || !project.isOpen()) {
					
					return Status.OK_STATUS;
				}

				String projectName = project.getName();
				org.eclipse.core.runtime.QualifiedName registryKey = new org.eclipse.core.runtime.QualifiedName(
					"com.checkmarx.eclipse.plugin", "scanner-registry");
				org.eclipse.core.runtime.QualifiedName stateHolderKey = new org.eclipse.core.runtime.QualifiedName(
					"com.checkmarx.eclipse.plugin", "state-holder");

				// Get or lazily initialize backend services
				com.checkmarx.eclipse.devassist.backend.ScannerRegistry registry =
					(com.checkmarx.eclipse.devassist.backend.ScannerRegistry)
					project.getSessionProperty(registryKey);

				com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder stateHolder =
					(com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder)
					project.getSessionProperty(stateHolderKey);

				// Lazy initialization if not found
				if (registry == null) {
					
					registry = new com.checkmarx.eclipse.devassist.backend.ScannerRegistry(project);
					project.setSessionProperty(registryKey, registry);
					
				}

				if (stateHolder == null) {
					
					stateHolder = new com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder();
					project.setSessionProperty(stateHolderKey, stateHolder);
					
				}

				// Execute backend scanners
				
				com.checkmarx.eclipse.devassist.common.ScanManager scanManager =
					new com.checkmarx.eclipse.devassist.common.ScanManager(registry, stateHolder);

				String filePath = file.getLocation().toOSString();
				

				java.util.List<com.checkmarx.eclipse.devassist.model.ScanIssue> issues =
					scanManager.scanFile(filePath);

				
				for (com.checkmarx.eclipse.devassist.model.ScanIssue issue : issues) {
				}

				// Publish results to UI
				
				if (!issues.isEmpty()) {
					com.checkmarx.eclipse.devassist.backend.result.ResultPublisher.publishResults(file, issues);
					
				} else {
					
				}

			} catch (Exception e) {
				System.err.println("[REALTIME] ✗ ERROR in step above: " + e.getMessage());
				e.printStackTrace();
				System.err.println("[REALTIME] Stack trace:");
				for (StackTraceElement elem : e.getStackTrace()) {
					System.err.println("[REALTIME]   at " + elem);
				}
			}

			
			return Status.OK_STATUS;

		} catch (Exception e) {
			System.err.println("[REALTIME] ✗ UNEXPECTED ERROR during real-time scan: " + e.getMessage());
			e.printStackTrace();
			System.err.println("[REALTIME] Full stack trace:");
			for (StackTraceElement elem : e.getStackTrace()) {
				System.err.println("[REALTIME]   at " + elem);
			}
			// Return error status but don't fail the job permanently
			return new Status(IStatus.WARNING, "com.checkmarx.eclipse.plugin",
					"Real-time scan failed for " + fileName, e);
		}
	}

	/**
	 * Check if user is authenticated by checking if API key is configured.
	 */
	private boolean isUserAuthenticated() {
		String apiKey = com.checkmarx.eclipse.common.properties.SharedPreferences.getApiKey();
		return apiKey != null && !apiKey.trim().isEmpty();
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
		
		super.canceling();
	}

	public String getFileName() {
		return fileName;
	}

	public IFile getFile() {
		return file;
	}
}
