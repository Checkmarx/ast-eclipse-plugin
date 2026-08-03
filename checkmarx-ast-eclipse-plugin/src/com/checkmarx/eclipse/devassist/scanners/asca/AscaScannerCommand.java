package com.checkmarx.eclipse.devassist.scanners.asca;

import com.checkmarx.eclipse.devassist.basescanner.BaseScannerCommand;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;

/**
 * ASCA Scanner Command that manages the lifecycle of ASCA realtime scanning.
 * Integrates with the scanner registry system to handle enabling/disabling of ASCA scanning.
 */
public class AscaScannerCommand extends BaseScannerCommand {

	public AscaScannerService ascaScannerService;
	private static final String LOG_TAG = "[ASCA-COMMAND]";

	/**
	 * Create an ASCA scanner command for a project.
	 *
	 * @param project Eclipse project
	 */
	public AscaScannerCommand(IProject project) {
		super(project, AscaScannerService.createConfig());
		this.ascaScannerService = new AscaScannerService(project);
		CxLogger.info(LOG_TAG + " Created for project: " + project.getName());
	}

	@Override
	public void initializeScanner() {
		CxLogger.info(LOG_TAG + " Initialized for real-time scanning");
	}

	/**
	 * Perform an ASCA scan on a file.
	 *
	 * @param filePath File path to scan
	 * @param document Document content
	 * @return Scan result
	 */
	public ScanResult<Object> scan(String filePath, IDocument document) {
		return ascaScannerService.scanWithDocument(filePath, document);
	}

	@Override
	public void dispose() {
		try {
			ascaScannerService.close();
			CxLogger.info(LOG_TAG + " Disposed for project: " + project.getName());
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error disposing: " + e.getMessage());
		}
	}
}
