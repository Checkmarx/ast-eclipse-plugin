package com.checkmarx.eclipse.devassist.scanners.iac;

import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.eclipse.devassist.basescanner.BaseScannerCommand;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;

/**
 * Command for coordinating IaC scanner operations.
 *
 * Manages the lifecycle of IaC realtime scanning in Eclipse, integrating with
 * the scanner registry system to handle enabling/disabling of IaC scanning.
 * Extends BaseScannerCommand for consistent registration lifecycle.
 */
public class IacScannerCommand extends BaseScannerCommand {

    private static final String LOG_TAG = "[IAC-COMMAND]";

    private final IacScannerService scannerService;

    public IacScannerCommand(IProject project, IacScannerService scannerService) {
        super(project, IacScannerService.createConfig());
        this.scannerService = scannerService;
        CxLogger.info(LOG_TAG + " Created for project: " + project.getName());
    }

    public IacScannerCommand(IProject project) {
        this(project, new IacScannerService(project));
    }

    /**
     * Initializes the scanner, invoked when scanner is registered.
     * IaC scans are triggered on demand via editor file changes rather than bulk project scans.
     */
    @Override
    public void initializeScanner() {
        CxLogger.info(LOG_TAG + " Initialized for project: " + project.getName());
    }

    /**
     * Determines whether a file path should be scanned by the IaC scanner.
     *
     * @param filePath path to evaluate
     * @return {@code true} if the file is an IaC file eligible for scanning
     */
    public boolean shouldScan(String filePath) {
        return scannerService.shouldScanFile(filePath);
    }

    /**
     * Executes an IaC scan on a specific file given its document content.
     *
     * @param filePath path to the file being scanned
     * @param document editor document content
     * @return ScanResult containing issues found, or null
     */
    public ScanResult<IacRealtimeResults> scan(String filePath, IDocument document) {
        return scannerService.scan(filePath, document, project);
    }

    /**
     * Disposes the scanner and releases associated resources.
     * Triggered when project is closed or scanner is unregistered.
     */
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