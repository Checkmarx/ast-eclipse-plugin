package com.checkmarx.eclipse.devassist.ui.findings.realtime;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;

/**
 * Real-time document listener for Checkmarx scanning.
 *
 * Equivalent to JetBrains' LocalInspectionTool.buildVisitor() — detects when
 * the user edits the currently opened file and triggers a real-time scan with
 * debounce (1 second of inactivity).
 *
 * This listener observes every keystroke and delegates to RealTimeScanJob for
 * debounced scanning.
 */
public class CheckmarxDocumentListener implements IDocumentListener {

	private final RealTimeScanJob scanJob;
	private final String fileName;

	/**
	 * Create a document listener for a specific file.
	 *
	 * @param fileName the name of the file being edited (for logging)
	 * @param scanJob the RealTimeScanJob to trigger on document changes
	 */
	public CheckmarxDocumentListener(String fileName, RealTimeScanJob scanJob) {
		this.fileName = fileName;
		this.scanJob = scanJob;
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
	 * Triggers the debounced real-time scan.
	 *
	 * This is equivalent to JetBrains' InspectionVisitor methods being called
	 * during AST traversal — every edit triggers a potential scan.
	 */
	@Override
	public void documentChanged(DocumentEvent event) {
		try {
			// Get the changed text (may be empty for deletions)
			String changedText = event.getText();
			int offset = event.getOffset();
			int length = event.getLength();

			// Reschedule the debounced scan job
			// This cancels the previous job (if still scheduled) and starts a new 1-second timer
			scanJob.reschedule(1000); // 1000ms = 1 second debounce

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
