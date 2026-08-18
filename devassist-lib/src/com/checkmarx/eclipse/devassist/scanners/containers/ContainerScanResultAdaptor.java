package com.checkmarx.eclipse.devassist.scanners.containers;

import com.checkmarx.ast.containersrealtime.ContainersRealtimeImage;
import com.checkmarx.ast.containersrealtime.ContainersRealtimeResults;
import com.checkmarx.ast.containersrealtime.ContainersRealtimeVulnerability;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;
import com.checkmarx.eclipse.common.utils.CxLogger;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adaptor for Container image scan results in Eclipse.
 *
 * Converts typed container vulnerability data (ContainersRealtimeResults) into 
 * standardized ScanIssue, Vulnerability, and Location objects.
 */
public class ContainerScanResultAdaptor implements ScanResult<ContainersRealtimeResults> {

    private static final String LOG_TAG = "[CONTAINER-ADAPTOR]";

    private static final String MALICIOUS_RISK_CONTAINER = "Container image contains malicious risk dependencies or configuration.";
    private static final String CRITICAL_RISK_CONTAINER = "Container image contains critical severity security vulnerabilities.";
    private static final String HIGH_RISK_CONTAINER = "Container image contains high severity security vulnerabilities.";
    private static final String MEDIUM_RISK_CONTAINER = "Container image contains medium severity security vulnerabilities.";
    private static final String LOW_RISK_CONTAINER = "Container image contains low severity security vulnerabilities.";

    private final ContainersRealtimeResults containersRealtimeResults;
    private final String fileType;
    private final String filePath;
    private final List<ScanIssue> scanIssues;

    /**
     * Constructs an instance of ContainerScanResultAdaptor with typed Container real-time results.
     *
     * @param containersRealtimeResults the container real-time scan results from AST SDK
     * @param fileType                  the file extension/type (e.g., "dockerfile")
     * @param filePath                  the project-relative or absolute file path
     */
    public ContainerScanResultAdaptor(ContainersRealtimeResults containersRealtimeResults, String fileType, String filePath) {
        this.containersRealtimeResults = containersRealtimeResults;
        this.fileType = fileType;
        this.filePath = filePath;
        this.scanIssues = buildIssues();
    }

    @Override
    public ContainersRealtimeResults getResults() {
        return containersRealtimeResults;
    }

    @Override
    public List<ScanIssue> getIssues() {
        return scanIssues;
    }

    /**
     * Processes images obtained from the scan results and converts them into standardized scan issues.
     */
    public List<ScanIssue> buildIssues() {
        List<ContainersRealtimeImage> images = Objects.nonNull(getResults()) ? getResults().getImages() : null;
        if (Objects.isNull(images) || images.isEmpty()) {
            return Collections.emptyList();
        }
        return images.stream()
                .map(this::createScanIssue)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Creates a ScanIssue object based on the provided ContainersRealtimeImage.
     */
    private ScanIssue createScanIssue(ContainersRealtimeImage containersImageObj) {
        try {
            ScanIssue scanIssue = new ScanIssue();
            scanIssue.setScanEngine(ScanEngine.CONTAINERS);
            scanIssue.setTitle(containersImageObj.getImageName());
            scanIssue.setImageTag(containersImageObj.getImageTag());
            scanIssue.setSeverity(DevAssistUtils.normalizeSeverity(containersImageObj.getStatus()));
            scanIssue.setFileType(this.fileType);
            scanIssue.setFilePath(this.filePath);

            if (Objects.nonNull(containersImageObj.getLocations()) && !containersImageObj.getLocations().isEmpty()) {
                containersImageObj.getLocations().forEach(location -> 
                        scanIssue.getLocations().add(createLocation(location)));
            }

            if (Objects.nonNull(containersImageObj.getVulnerabilities()) && !containersImageObj.getVulnerabilities().isEmpty()) {
                containersImageObj.getVulnerabilities().forEach(vulnerability -> 
                        scanIssue.getVulnerabilities().add(createVulnerability(vulnerability)));
            }

            scanIssue.setScanIssueId(getUniqueId(scanIssue));

            int line = !scanIssue.getLocations().isEmpty() ? scanIssue.getLocations().get(0).getLine() : 1;
            scanIssue.setProblematicLineNumber(line);

            return scanIssue;
        } catch (Exception e) {
            CxLogger.warning(LOG_TAG + " Error creating scan issue for image " + containersImageObj.getImageName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a Vulnerability instance based on the provided ContainersRealtimeVulnerability.
     */
    private Vulnerability createVulnerability(ContainersRealtimeVulnerability vulnerabilityObj) {
        Vulnerability vulnerability = new Vulnerability();
        vulnerability.setCve(vulnerabilityObj.getCve());
        vulnerability.setDescription(this.getDescription(vulnerabilityObj.getSeverity()));
        vulnerability.setSeverity(DevAssistUtils.normalizeSeverity(vulnerabilityObj.getSeverity()));
        return vulnerability;
    }

    /**
     * Maps severity string into standard risk description text for container vulnerabilities.
     */
    private String getDescription(String severity) {
        if (Objects.isNull(severity) || severity.isEmpty()) {
            return severity;
        }
        String normalized = severity.toUpperCase();
        switch (normalized) {
            case "MALICIOUS":
                return MALICIOUS_RISK_CONTAINER;
            case "CRITICAL":
                return CRITICAL_RISK_CONTAINER;
            case "HIGH":
                return HIGH_RISK_CONTAINER;
            case "MEDIUM":
                return MEDIUM_RISK_CONTAINER;
            case "LOW":
                return LOW_RISK_CONTAINER;
            default:
                return severity;
        }
    }

    /**
     * Creates a Location object based on the provided RealtimeLocation.
     * Note: Adjusts zero-based line numbers from scan results to one-based line numbers.
     */
    private Location createLocation(RealtimeLocation location) {
        int line = getLine(location);
        int startIndex = location.getStartIndex();
        int endIndex = location.getEndIndex();
        return new Location(line, startIndex, endIndex);
    }

    /**
     * Retrieves the line number from the given RealtimeLocation object and increments it by 1.
     */
    private int getLine(RealtimeLocation location) {
        return location.getLine() + 1;
    }

    /**
     * Generates a unique ID for the given scan issue.
     */
    private String getUniqueId(ScanIssue scanIssue) {
        int line = (Objects.nonNull(scanIssue.getLocations()) && !scanIssue.getLocations().isEmpty())
                ? scanIssue.getLocations().get(0).getLine() : 0;
        return DevAssistUtils.generateUniqueId(line, scanIssue.getTitle(), scanIssue.getImageTag());
    }
}