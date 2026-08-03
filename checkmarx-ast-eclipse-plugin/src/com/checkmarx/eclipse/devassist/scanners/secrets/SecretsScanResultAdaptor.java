package com.checkmarx.eclipse.devassist.scanners.secrets;

import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.ast.secretsrealtime.SecretsRealtimeResults;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.backend.DevAssistUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adapter class for handling Secrets scan results and converting them into a standardized format
 * using the {@link ScanResult} interface.
 * This class wraps a {@link SecretsRealtimeResults} instance and provides methods to process and extract
 * meaningful scan issues based on secrets detected in the files.
 */
public class SecretsScanResultAdaptor implements ScanResult<SecretsRealtimeResults> {

    private final SecretsRealtimeResults secretsRealtimeResults;
    private final String filePath;
    private final List<ScanIssue> scanIssues;

    /**
     * Constructs an instance of {@code SecretsScanResultAdaptor} with the specified Secrets real-time results.
     * This adapter allows conversion and processing of Secrets scan results into a standardized format.
     *
     * @param secretsRealtimeResults the Secrets real-time scan results to be wrapped by this adapter
     * @param filePath the path of the scanned file
     */
    public SecretsScanResultAdaptor(SecretsRealtimeResults secretsRealtimeResults, String filePath) {
        this.secretsRealtimeResults = secretsRealtimeResults;
        this.filePath = filePath;
        this.scanIssues = buildIssues();
    }

    /**
     * Retrieves the Secrets real-time scan results wrapped by this adapter.
     *
     * @return the Secrets scan results instance containing the results of the Secrets scan
     */
    @Override
    public SecretsRealtimeResults getResults() {
        return secretsRealtimeResults;
    }

    /**
     * Retrieves a list of scan issues discovered in the Secrets real-time scan.
     *
     * @return a list of {@code ScanIssue} objects representing the secrets found during the scan
     */
    @Override
    public List<ScanIssue> getIssues() {
        return scanIssues;
    }

    /**
     * Retrieves a list of scan issues discovered in the Secrets real-time scan.
     * This method processes the secrets obtained from the scan results,
     * converts them into standardized scan issues, and returns the list.
     * If no secrets are found, an empty list is returned.
     *
     * @return a list of {@code ScanIssue} objects representing the secrets found during the scan,
     * or an empty list if no secrets are detected.
     */
    public List<ScanIssue> buildIssues() {
        if (Objects.isNull(getResults())) {
            return Collections.emptyList();
        }

        List<SecretsRealtimeResults.Secret> secrets = getResults().getSecrets();
        if (Objects.isNull(secrets) || secrets.isEmpty()) {
            return Collections.emptyList();
        }

        return secrets.stream()
                .map(this::createScanIssue)
                .collect(Collectors.toList());
    }

    /**
     * Creates a {@code ScanIssue} object based on the provided secret result.
     * The method processes the secret details and converts them into a structured format to
     * represent a scan issue.
     *
     * @param secret the secret result containing information about the detected secret,
     *               including its title, severity, description, and locations.
     * @return a {@code ScanIssue} object encapsulating the details such as title, scan engine,
     * severity, and secret locations derived from the provided secret result.
     */
    private ScanIssue createScanIssue(SecretsRealtimeResults.Secret secret) {
        ScanIssue scanIssue = new ScanIssue();

        scanIssue.setTitle(secret.getTitle());
        scanIssue.setScanEngine(ScanEngine.SECRETS);
        scanIssue.setSeverity(secret.getSeverity());
        scanIssue.setFilePath(this.filePath);
        scanIssue.setDescription(secret.getDescription()); // Set description on ScanIssue for tooltip display
        scanIssue.setSecretValue(secret.getSecretValue());

        // Add locations if available
        if (Objects.nonNull(secret.getLocations()) && !secret.getLocations().isEmpty()) {
            secret.getLocations().forEach(location ->
                    scanIssue.getLocations().add(createLocation(location)));
        }

        // Fallback location if none are provided by the engine
        if (scanIssue.getLocations().isEmpty()) {
            Location fallbackLocation = new Location(1, 0, 100);
            scanIssue.getLocations().add(fallbackLocation);
        }

        // Create vulnerability with secret details
        Vulnerability vulnerability = new Vulnerability();
        vulnerability.setTitle(secret.getTitle());
        vulnerability.setDescription(secret.getDescription());
        vulnerability.setSeverity(secret.getSeverity());

        scanIssue.getVulnerabilities().add(vulnerability);
        scanIssue.setScanIssueId(getUniqueId(scanIssue));
        return scanIssue;
    }

    /**
     * Creates a {@code Location} object based on the provided location information.
     * This method extracts the line, start index, and end index from the given
     * location and constructs a new {@code Location} instance.
     *
     * @param location the location containing details such as line,
     *                 start index, and end index for the location.
     * @return a new {@code Location} instance with the appropriate line and indices.
     */
    private Location createLocation(RealtimeLocation location) {
        return new Location(getLine(location), location.getStartIndex(), location.getEndIndex());
    }

    /**
     * Retrieves the line number from the given {@code RealtimeLocation} object, increments it by one, and returns the result.
     *
     * @param location the {@code RealtimeLocation} object containing the original line number
     * @return the incremented line number based on the {@code RealtimeLocation}'s line value
     * @apiNote Current Secrets scan result line numbers are zero-based, so this method adjusts them to be one-based.
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
        return DevAssistUtils.generateUniqueId(line, scanIssue.getTitle(), scanIssue.getDescription());
    }
}