package com.checkmarx.eclipse.devassist.scanners.secrets;

import com.checkmarx.ast.secretsrealtime.SecretsRealtimeResults;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;

/**
 * Command for coordinating Secrets scanner operations.
 */
public class SecretsScannerCommand {

	private final IProject project;
	private final SecretsScannerService scannerService;
	private static final String LOG_TAG = "[SECRETS-COMMAND]";

	public SecretsScannerCommand(IProject project) {
		this.project = project;
		this.scannerService = new SecretsScannerService(project);
		CxLogger.info(LOG_TAG + " Created for project: " + project.getName());
	}

	public boolean shouldScan(String filePath) {
		return scannerService.shouldScanFile(filePath);
	}

	public ScanResult<SecretsRealtimeResults> scan(String filePath, IDocument document) {
		return scannerService.scan(filePath, document, project);
	}

	public void dispose() {
		try {
			scannerService.close();
			CxLogger.info(LOG_TAG + " Disposed for project: " + project.getName());
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error disposing: " + e.getMessage());
		}
	}
}
