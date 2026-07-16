package com.checkmarx.eclipse.devassist.ui.findings.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a scan issue detected during a real-time scan.
 * Captures detailed information about security issues identified in a scanned project.
 * Each scan issue can have multiple locations and vulnerabilities.
 */
public class ScanIssue {

    private String scanIssueId;
    private String severity;
    private String title;
    private String description;
    private String remediationAdvise;
    private String packageVersion;
    private String packageManager;
    private String cve;
    private ScanEngine scanEngine;
    private String filePath;
    private String imageTag;
    private String fileType;
    private String secretValue;
    private String similarityId;
    private Integer ruleId;
    private Integer problematicLineNumber;
    private List<Location> locations = new ArrayList<>();
    private List<Vulnerability> vulnerabilities = new ArrayList<>();

    public ScanIssue() {
    }

    public ScanIssue(String scanIssueId, String severity, String title, String description,
            String remediationAdvise, String packageVersion, String packageManager, String cve,
            ScanEngine scanEngine, String filePath, String imageTag) {
        this.scanIssueId = scanIssueId;
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.remediationAdvise = remediationAdvise;
        this.packageVersion = packageVersion;
        this.packageManager = packageManager;
        this.cve = cve;
        this.scanEngine = scanEngine;
        this.filePath = filePath;
        this.imageTag = imageTag;
    }

    // Getters and Setters
    public String getScanIssueId() {
        return scanIssueId;
    }

    public void setScanIssueId(String scanIssueId) {
        this.scanIssueId = scanIssueId;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRemediationAdvise() {
        return remediationAdvise;
    }

    public void setRemediationAdvise(String remediationAdvise) {
        this.remediationAdvise = remediationAdvise;
    }

    public String getPackageVersion() {
        return packageVersion;
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    public String getPackageManager() {
        return packageManager;
    }

    public void setPackageManager(String packageManager) {
        this.packageManager = packageManager;
    }

    public String getCve() {
        return cve;
    }

    public void setCve(String cve) {
        this.cve = cve;
    }

    public ScanEngine getScanEngine() {
        return scanEngine;
    }

    public void setScanEngine(ScanEngine scanEngine) {
        this.scanEngine = scanEngine;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getImageTag() {
        return imageTag;
    }

    public void setImageTag(String imageTag) {
        this.imageTag = imageTag;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getSecretValue() {
        return secretValue;
    }

    public void setSecretValue(String secretValue) {
        this.secretValue = secretValue;
    }

    public String getSimilarityId() {
        return similarityId;
    }

    public void setSimilarityId(String similarityId) {
        this.similarityId = similarityId;
    }

    public Integer getRuleId() {
        return ruleId;
    }

    public void setRuleId(Integer ruleId) {
        this.ruleId = ruleId;
    }

    public Integer getProblematicLineNumber() {
        return problematicLineNumber;
    }

    public void setProblematicLineNumber(Integer problematicLineNumber) {
        this.problematicLineNumber = problematicLineNumber;
    }

    public List<Location> getLocations() {
        return locations;
    }

    public void setLocations(List<Location> locations) {
        this.locations = locations;
    }

    public List<Vulnerability> getVulnerabilities() {
        return vulnerabilities;
    }

    public void setVulnerabilities(List<Vulnerability> vulnerabilities) {
        this.vulnerabilities = vulnerabilities;
    }
}
