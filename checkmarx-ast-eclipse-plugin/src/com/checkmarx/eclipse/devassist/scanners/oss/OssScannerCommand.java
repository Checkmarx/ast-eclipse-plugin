package com.checkmarx.eclipse.devassist.scanners.oss;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.eclipse.devassist.basescanner.BaseScannerCommand;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Command for coordinating OSS scanner operations in Eclipse.
 *
 * Manages the lifecycle and initialization of OSS scanning:
 * - Extends BaseScannerCommand for consistent registration lifecycle
 * - Traverses project workspace files recursively upon initialization
 * - Executes background job scans on supported manifest files
 * - Publishes findings via ProblemHolderService
 */
public class OssScannerCommand extends BaseScannerCommand {

    private static final String LOG_TAG = "[OSS-COMMAND]";

    public final OssScannerService ossScannerService;

    public OssScannerCommand(IProject project) {
        super(project, OssScannerService.createConfig());
        this.ossScannerService = new OssScannerService(project);
        CxLogger.info(LOG_TAG + " Created for project: " + project.getName());
    }

    /**
     * Initializes the scanner, invoked when scanner is registered.
     * Launches a background Eclipse Job to scan all manifest files in the project workspace.
     */
    @Override
    public void initializeScanner() {
        Job scanJob = new Job("Starting Checkmarx OSS Real-time Scan") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                monitor.beginTask("Scanning manifest files in project: " + project.getName(), IProgressMonitor.UNKNOWN);
                scanAllManifestFilesInFolder(monitor);
                monitor.done();
                return Status.OK_STATUS;
            }
        };
        scanJob.schedule();
    }

    /**
     * Scans all manifest files in the opened project workspace.
     * Recursively iterates through project resources (excluding node_modules)
     * and triggers an OSS real-time scan on each matching manifest file.
     */
    private void scanAllManifestFilesInFolder(IProgressMonitor monitor) {
        if (project == null || !project.isOpen()) {
            return;
        }

        List<IFile> matchedFiles = new ArrayList<>();

        List<PathMatcher> pathMatchers = DevAssistConstants.MANIFEST_FILE_PATTERNS.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());

        try {
            // Recursively traverse project workspace files (equivalent to ProjectRootManager in JetBrains)
            project.accept(new IResourceVisitor() {
                @Override
                public boolean visit(IResource resource) throws CoreException {
                    if (monitor.isCanceled()) {
                        return false;
                    }

                    // Skip node_modules folder subtree entirely
                    if (resource.getType() == IResource.FOLDER && resource.getName().equals("node_modules")) {
                        return false;
                    }

                    if (resource.getType() == IResource.FILE && resource.exists()) {
                        IFile file = (IFile) resource;
                        String path = file.getLocation() != null ? file.getLocation().toOSString() : file.getFullPath().toString();

                        for (PathMatcher matcher : pathMatchers) {
                            if (matcher.matches(Paths.get(path))) {
                                matchedFiles.add(file);
                                break;
                            }
                        }
                    }
                    return true;
                }
            });
        } catch (CoreException e) {
            CxLogger.error(LOG_TAG + " Exception during workspace traversal for project " + project.getName() + ": " + e.getMessage(), e);
        }

        // Execute scan on each discovered manifest file
        for (IFile file : matchedFiles) {
            if (monitor.isCanceled()) {
                break;
            }

            String uri = file.getLocation() != null ? file.getLocation().toOSString() : file.getFullPath().toString();
            try {
                // Perform OSS scan using service
                ScanResult<OssRealtimeResults> ossRealtimeResults = ossScannerService.scanWithDocument(uri, new Document());

                if (Objects.isNull(ossRealtimeResults)) {
                    CxLogger.warning(LOG_TAG + " Scan failed for manifest file: " + uri);
                    continue;
                }

                // Add findings to problem markers
                List<ScanIssue> issues = ossRealtimeResults.getIssues();
                ProblemHolderService.addToCxOneFindings(file, issues);

            } catch (Exception e) {
                CxLogger.warning(LOG_TAG + " Scan failed for manifest file: " + uri + " with exception: " + e.getMessage());
            }
        }
    }

    /**
     * Check if a file should be scanned by this command.
     */
    public boolean shouldScan(String filePath) {
        return ossScannerService.shouldScanFile(filePath);
    }

    /**
     * Execute scan on a file with document content.
     */
    public ScanResult<OssRealtimeResults> scan(String filePath, IDocument document) {
        return ossScannerService.scanWithDocument(filePath, document);
    }

    /**
     * Execute scan on a file path directly.
     */
    public ScanResult<OssRealtimeResults> scan(String filePath) {
        return ossScannerService.scan(filePath);
    }

    /**
     * Disposes the scanner and releases resources.
     */
    @Override
    public void dispose() {
        try {
            ossScannerService.close();
            CxLogger.info(LOG_TAG + " Disposed for project: " + project.getName());
        } catch (Exception e) {
            CxLogger.warning(LOG_TAG + " Error disposing: " + e.getMessage());
        }
        super.dispose();
    }
}