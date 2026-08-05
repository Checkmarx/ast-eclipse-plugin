package com.checkmarx.eclipse.devassist.scanners.secrets;

import com.checkmarx.ast.secretsrealtime.SecretsRealtimeResults;
import com.checkmarx.eclipse.devassist.basescanner.BaseScannerCommand;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;

/**
 * Command for coordinating Secrets scanner operations.
 * Extends BaseScannerCommand for consistent registration lifecycle.
 */
public class SecretsScannerCommand extends BaseScannerCommand {

	private static final String LOG_TAG = "[SECRETS-COMMAND]";

	private final SecretsScannerService scannerService;

	public SecretsScannerCommand(IProject project) {
		super(project, SecretsScannerService.createConfig());
		this.scannerService = new SecretsScannerService(project);
		CxLogger.info(LOG_TAG + " Created for project: " + project.getName());
	}

	/**
	 * Initializes the scanner, invoked when scanner is registered.
	 */
	@Override
	public void initializeScanner() {
		// Secrets scanning is triggered on demand via editor file changes
		CxLogger.info(LOG_TAG + " Initialized for project: " + project.getName());
	}

	public boolean shouldScan(String filePath) {
		return scannerService.shouldScanFile(filePath);
	}

	public ScanResult<SecretsRealtimeResults> scan(String filePath, IDocument document) {
		return scannerService.scan(filePath, document, project);
	}

	@Override
	public void dispose() {
		try {
			scannerService.close();
			CxLogger.info(LOG_TAG + " Disposed for project: " + project.getName());
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error disposing: " + e.getMessage());
		}
		super.dispose();
	}
}
