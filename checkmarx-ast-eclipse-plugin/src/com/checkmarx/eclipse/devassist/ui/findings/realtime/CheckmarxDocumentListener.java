package com.checkmarx.eclipse.devassist.ui.findings.realtime;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;

import com.checkmarx.eclipse.devassist.inspection.DevAssistScanScheduler;

/**
 * Real-time document listener for Checkmarx scanning.
 *
 * Equivalent to JetBrains' LocalInspectionTool.buildVisitor() — detects when
 * the user edits the currently opened file and triggers a real-time scan with
 * debounce (1 second of inactivity).
 *
 * This listener observes every keystroke and delegates to DevAssistScanScheduler
 * for debounced scanning coordination.
 */
public class CheckmarxDocumentListener implements IDocumentListener {

	private final RealTimeScanJob scanJob;
	private final IFile file;
	private final String fileName;
	private final DevAssistScanScheduler scheduler;

	/**
	 * Create a document listener for a specific file.
	 *
	 * @param fileName the name of the file being edited (for logging)
	 * @param scanJob the RealTimeScanJob to trigger on document changes
	 * @param file the IFile being edited
	 * @param scheduler the scheduler to coordinate scan rescheduling
	 */
	public CheckmarxDocumentListener(String fileName, RealTimeScanJob scanJob, IFile file, DevAssistScanScheduler scheduler) {
		this.fileName = fileName;
		this.scanJob = scanJob;
		this.file = file;
		this.scheduler = scheduler;
	}

	/**
	 * Called when the document is about to be changed.
	 * We don't need to do anything here, but we implement it for completeness.
	 */
	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
		// No action needed before change
	}

	/**
	 * Called when the document has been changed.
	 * Triggers the debounced real-time scan via DevAssistScanScheduler.
	 *
	 * This is equivalent to JetBrains' InspectionVisitor methods being called
	 * during AST traversal — every edit triggers a potential scan.
	 */
	@Override
	public void documentChanged(DocumentEvent event) {
		try {
			// Reschedule the debounced scan job via scheduler
			// This cancels the previous job (if still scheduled) and starts a new 1-second timer
			if (scheduler != null && file != null) {
				scheduler.rescheduleInspection(file, 1000); // 1000ms = 1 second debounce
			} else if (scanJob != null) {
				// Fallback to direct reschedule if scheduler not available
				scanJob.reschedule(1000);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Dispose this listener and clean up associated resources.
	 * Call this when the editor is closed.
	 */
	public void dispose() {
		if (scanJob != null) {
			scanJob.cancel();
		}
	}

	public String getFileName() {
		return fileName;
	}
}
