package com.checkmarx.eclipse.devassist.scanners.asca;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.backend.DevAssistUtils;
import com.checkmarx.eclipse.utils.CxLogger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter class for handling ASCA scan results and converting them into a standardized format.
 *
 * This class wraps a ASCA {@link ScanResult} instance and provides methods to process and extract
 * meaningful scan issues based on ASCA findings detected in the files.
 *
 * Features:
 * - Groups multiple vulnerabilities on the same line
 * - Sorts vulnerabilities by severity precedence
 * - Filters ignored vulnerabilities (optional)
 * - Generates proper unique IDs
 * - Tracks location information
 *
 * Adapted from JetBrains implementation for Eclipse platform.
 */
public class AscaScanResultAdaptor implements ScanResult<Object> {

	private static final String LOG_TAG = "[ASCA-ADAPTOR]";
	private static final String MULTIPLE_ISSUES_SUFFIX = " ASCA issues";

	private final com.checkmarx.ast.asca.ScanResult ascaScanResult;
	private final String filePath;
	private final List<ScanIssue> scanIssues;

	/**
	 * Constructs an instance of AscaScanResultAdaptor with the specified ASCA scan results.
	 *
	 * @param ascaScanResult the ASCA scan results to be wrapped
	 * @param filePath the path of the file being scanned
	 */
	public AscaScanResultAdaptor(com.checkmarx.ast.asca.ScanResult ascaScanResult, String filePath) {
		this.ascaScanResult = ascaScanResult;
		this.filePath = filePath;
		this.scanIssues = buildIssues();
	}

	@Override
	public com.checkmarx.ast.asca.ScanResult getResults() {
		return ascaScanResult;
	}

	@Override
	public List<ScanIssue> getIssues() {
		return scanIssues;
	}

	/**
	 * Builds a list of ScanIssue objects from the ASCA scan results.
	 * Groups multiple vulnerabilities on the same line and sorts them by severity.
	 */
	private List<ScanIssue> buildIssues() {
		if (ascaScanResult == null || ascaScanResult.getScanDetails() == null) {
			CxLogger.info(LOG_TAG + " No scan results or scan details available");
			return Collections.emptyList();
		}

		List<ScanDetail> scanDetails = ascaScanResult.getScanDetails();
		if (scanDetails.isEmpty()) {
			return Collections.emptyList();
		}

		// Group scan details by line number, then sort by severity precedence
		Map<Integer, List<ScanDetail>> groupedIssues = scanDetails.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.groupingBy(
						ScanDetail::getLine,
						Collectors.collectingAndThen(Collectors.toList(), detailsList -> {
							detailsList.sort(Comparator.comparingInt(detail ->
									getSeverityPrecedence(detail.getSeverity())));
							return detailsList;
						})
				));

		List<ScanIssue> issues = groupedIssues.values().stream()
				.map(this::createScanIssueForGroup)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		CxLogger.info(LOG_TAG + " Converted " + issues.size() + " grouped scan issues for file: " + filePath);
		return issues;
	}

	/**
	 * Creates a ScanIssue from a group of ASCA scan details that are on the same line.
	 *
	 * @param ascaScanDetails the list of ASCA scan details for the same line (already sorted by severity)
	 * @return a ScanIssue representing the ASCA finding(s), or null if conversion fails
	 */
	private ScanIssue createScanIssueForGroup(List<ScanDetail> ascaScanDetails) {
		if (ascaScanDetails == null || ascaScanDetails.isEmpty()) {
			return null;
		}

		try {
			ScanIssue scanIssue = getScanIssue(ascaScanDetails);

			// Add vulnerabilities from all details in the group
			for (int i = 0; i < ascaScanDetails.size(); i++) {
				ScanDetail detail = ascaScanDetails.get(i);
				String vulnerabilityId = (i == 0) ? scanIssue.getScanIssueId() : null;
				Vulnerability vuln = createVulnerability(detail, vulnerabilityId);
				scanIssue.getVulnerabilities().add(vuln);
			}

			// Update title based on actual number of vulnerabilities
			updateScanIssueTitleAndLocation(scanIssue, ascaScanDetails);

			CxLogger.info(LOG_TAG + " Created ScanIssue with " + scanIssue.getVulnerabilities().size() +
					" vulnerabilities on line " + scanIssue.getProblematicLineNumber());
			return scanIssue;

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Failed to convert scan details group to ScanIssue: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Creates a ScanIssue with appropriate title and basic properties from a group of ASCA scan details.
	 *
	 * @param ascaScanDetails the list of ASCA scan details (already sorted by severity)
	 * @return a ScanIssue with basic properties set
	 */
	private ScanIssue getScanIssue(List<ScanDetail> ascaScanDetails) {
		ScanIssue scanIssue = new ScanIssue();
		ScanDetail firstDetail = ascaScanDetails.get(0); // Highest severity (already sorted)

		// Set title based on whether there are multiple issues on the same line
		String title;
		if (ascaScanDetails.size() > 1) {
			title = ascaScanDetails.size() + MULTIPLE_ISSUES_SUFFIX;
		} else {
			title = firstDetail.getRuleName();
		}

		scanIssue.setTitle(title);
		scanIssue.setDescription(firstDetail.getDescription());
		scanIssue.setSeverity(mapSeverity(firstDetail.getSeverity()));
		scanIssue.setFilePath(filePath);
		scanIssue.setScanEngine(ScanEngine.ASCA);
		scanIssue.setProblematicLineNumber(firstDetail.getLine());
		scanIssue.setRuleId(firstDetail.getRuleID());

		// Generate unique ID based on line, rule ID, and rule name
		String scanIssueId = generateUniqueId(firstDetail);
		scanIssue.setScanIssueId(scanIssueId);

		return scanIssue;
	}

	/**
	 * Creates a Vulnerability object from a ASCA scan detail.
	 *
	 * @param scanDetail the ASCA scan detail
	 * @param overrideId optional vulnerability ID to use instead of generating one
	 * @return a Vulnerability object
	 */
	private Vulnerability createVulnerability(ScanDetail scanDetail, String overrideId) {
		Vulnerability vulnerability = new Vulnerability();

		// Generate or use provided vulnerability ID
		String vulnerabilityId = generateUniqueId(scanDetail);
		if (overrideId != null && !overrideId.isBlank()) {
			vulnerabilityId = overrideId;
		}

		vulnerability.setVulnerabilityId(vulnerabilityId);
		vulnerability.setTitle(scanDetail.getRuleName());
		vulnerability.setDescription(scanDetail.getDescription());
		vulnerability.setSeverity(mapSeverity(scanDetail.getSeverity()));

		CxLogger.info(LOG_TAG + " Created vulnerability '" + scanDetail.getRuleName() +
				"' with vulnerabilityId '" + vulnerabilityId + "'");

		return vulnerability;
	}

	/**
	 * Updates the ScanIssue title and location based on vulnerability count and scan details.
	 */
	private void updateScanIssueTitleAndLocation(ScanIssue scanIssue, List<ScanDetail> ascaScanDetails) {
		// Update title based on actual number of vulnerabilities
		if (scanIssue.getVulnerabilities().size() == 1) {
			scanIssue.setTitle(scanIssue.getVulnerabilities().get(0).getTitle());
		} else if (scanIssue.getVulnerabilities().size() > 1) {
			scanIssue.setTitle(scanIssue.getVulnerabilities().size() + MULTIPLE_ISSUES_SUFFIX);
		}

		// Add location information from first detail
		ScanDetail firstDetail = ascaScanDetails.get(0);
		Location location = new Location();
		location.setLine(firstDetail.getLine());
		scanIssue.getLocations().add(location);
	}

	/**
	 * Maps ASCA severity levels to standardized severity strings.
	 *
	 * @param ascaSeverity the ASCA severity level
	 * @return standardized severity string
	 */
	private String mapSeverity(String ascaSeverity) {
		if (ascaSeverity == null) {
			return "Medium";
		}

		switch (ascaSeverity.toLowerCase()) {
			case "critical":
				return "Critical";
			case "high":
				return "High";
			case "medium":
				return "Medium";
			case "low":
				return "Low";
			case "info":
				return "Low";
			default:
				return "Medium";
		}
	}

	/**
	 * Get severity precedence for sorting (higher number = higher severity).
	 */
	private int getSeverityPrecedence(String severity) {
		if (severity == null) {
			return 3;
		}

		switch (severity.toLowerCase()) {
			case "critical":
				return 5;
			case "high":
				return 4;
			case "medium":
				return 3;
			case "low":
				return 2;
			case "info":
				return 1;
			default:
				return 3;
		}
	}

	/**
	 * Generates a unique ID for the given scan detail.
	 */
	private String generateUniqueId(ScanDetail scanDetail) {
		if (scanDetail != null) {
			return DevAssistUtils.generateUniqueId(
					scanDetail.getLine(),
					scanDetail.getRuleID() + scanDetail.getRuleName(),
					scanDetail.getFileName());
		}
		return ScanEngine.ASCA.name();
	}
}
