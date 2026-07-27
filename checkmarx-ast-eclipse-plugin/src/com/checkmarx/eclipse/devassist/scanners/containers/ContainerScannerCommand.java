package com.checkmarx.eclipse.devassist.scanners.containers;

import com.checkmarx.ast.containersrealtime.ContainersRealtimeResults;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;

import java.util.Objects;

/**
 * Container Scanner Command that manages the lifecycle of container realtime scanning in Eclipse.
 * Coordinates execution, file eligibility validation, and disposal for a given workspace project.
 */
public class ContainerScannerCommand {

    private static final String LOG_TAG = "[CONTAINER-COMMAND]";

    private final IProject project;
    private final ContainerScannerService containerScannerService;
    private boolean isInitialized = false;

    /**
     * Main constructor for initializing the command with a project.
     *
     * @param project the Eclipse project instance
     */
    public ContainerScannerCommand(IProject project) {
        this(project, new ContainerScannerService(project));
    }

    /**
     * Dependency injection constructor (useful for unit testing or custom service setup).
     *
     * @param project               the Eclipse project instance
     * @param containerScannerService custom or pre-configured scanner service
     */
    public ContainerScannerCommand(IProject project, ContainerScannerService containerScannerService) {
        this.project = project;
        this.containerScannerService = containerScannerService;
        initializeScanner();
    }

    /**
     * Initializes the scanner, invoked during or after registration of the command.
     */
    public synchronized void initializeScanner() {
        if (!isInitialized) {
            this.isInitialized = true;
            String projectName = Objects.nonNull(project) ? project.getName() : "Unknown";
            CxLogger.info(LOG_TAG + " Container Scanner Command initialized for project: " + projectName);
        }
    }

    /**
     * Evaluates whether the specified file path is eligible for a Container scan
     * (Dockerfiles, Docker Compose, or Helm charts).
     *
     * @param filePath project-relative or absolute file path
     * @return true if the file should be scanned, false otherwise
     */
    public boolean shouldScan(String filePath) {
        return containerScannerService.shouldScanFile(filePath);
    }

    /**
     * Triggers a Container Realtime scan for the specified file path and active document.
     *
     * @param filePath absolute path to the file being scanned
     * @param document the open Eclipse IDocument buffer (or null if scanning directly from disk)
     * @return strongly typed ScanResult containing ContainersRealtimeResults and converted ScanIssues
     */
    public ScanResult<ContainersRealtimeResults> scan(String filePath, IDocument document) {
        if (!shouldScan(filePath)) {
            return null;
        }
        return containerScannerService.scan(filePath, document, project);
    }

    /**
     * Returns the underlying ContainerScannerService instance.
     *
     * @return the active ContainerScannerService
     */
    public ContainerScannerService getScannerService() {
        return containerScannerService;
    }

    /**
     * Disposes underlying resources and cleans up temporary structures.
     * Automatically called when the project or plugin context is closed/unloaded.
     */
    public void dispose() {
        try {
            if (containerScannerService != null) {
                containerScannerService.close();
            }
            this.isInitialized = false;
            String projectName = Objects.nonNull(project) ? project.getName() : "Unknown";
            CxLogger.info(LOG_TAG + " Container Scanner Command disposed for project: " + projectName);
        } catch (Exception e) {
            CxLogger.warning(LOG_TAG + " Error disposing Container Scanner Command: " + e.getMessage());
        }
    }
}