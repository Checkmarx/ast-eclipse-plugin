package com.checkmarx.eclipse.devassist.scanners.iac;

import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;
import com.checkmarx.eclipse.common.utils.CxLogger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter class for handling IaC scan results and converting them into a standardized format.
 *
 * This class wraps an IaC {@link IacRealtimeResults} instance and provides methods to process and extract
 * meaningful scan issues based on IaC misconfigurations detected in the files.
 *
 * Features:
 * - Groups multiple misconfigurations on the same line
 * - Sorts misconfigurations by severity precedence
 * - Generates proper unique IDs
 * - Tracks location information
 *
 * Adapted from JetBrains implementation for Eclipse platform.
 */
public class IacScanResultAdaptor implements ScanResult<IacRealtimeResults> {

	private static final String LOG_TAG = "[IAC-ADAPTOR]";

	private final IacRealtimeResults iacRealtimeResults;
	private final String filePath;
	private final List<ScanIssue> scanIssues;

	public IacScanResultAdaptor(IacRealtimeResults iacRealtimeResults, String filePath) {
		this.iacRealtimeResults = iacRealtimeResults;
		this.filePath = filePath;
		this.scanIssues = buildIssues();
	}

	@Override
	public IacRealtimeResults getResults() {
		return iacRealtimeResults;
	}

	@Override
	public List<ScanIssue> getIssues() {
		return scanIssues;
	}

	private List<ScanIssue> buildIssues() {
		if (iacRealtimeResults == null || iacRealtimeResults.getResults() == null) {
			CxLogger.info(LOG_TAG + " No scan results available");
			return Collections.emptyList();
		}

		List<IacRealtimeResults.Issue> issues = iacRealtimeResults.getResults();
		if (issues.isEmpty()) {
			return Collections.emptyList();
		}

		// Group issues by line number, then sort by severity precedence
		Map<Integer, List<IacRealtimeResults.Issue>> groupedIssues = issues.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.groupingBy(
						issue -> {
							if (issue.getLocations() != null && !issue.getLocations().isEmpty()) {
								return issue.getLocations().get(0).getLine();
							}
							return 1;
						},
						Collectors.collectingAndThen(Collectors.toList(), issuesList -> {
							issuesList.sort(Comparator.comparingInt(issue ->
									getSeverityPrecedence(issue.getSeverity())));
							return issuesList;
						})
				));

		List<ScanIssue> scanIssues = groupedIssues.values().stream()
				.map(this::createScanIssueForGroup)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		CxLogger.info(LOG_TAG + " Converted " + scanIssues.size() + " grouped scan issues for file: " + filePath);
		return scanIssues;
	}

	private ScanIssue createScanIssueForGroup(List<IacRealtimeResults.Issue> iacIssues) {
		if (iacIssues == null || iacIssues.isEmpty()) {
			return null;
		}

		try {
			ScanIssue scanIssue = getScanIssue(iacIssues);

			// Add vulnerabilities from all issues in the group
			for (int i = 0; i < iacIssues.size(); i++) {
				IacRealtimeResults.Issue iacIssue = iacIssues.get(i);
				String vulnerabilityId = (i == 0) ? scanIssue.getScanIssueId() : null;
				Vulnerability vuln = createVulnerability(iacIssue, vulnerabilityId);
				scanIssue.getVulnerabilities().add(vuln);
			}

			// Update title based on actual number of vulnerabilities
			updateScanIssueTitleAndLocation(scanIssue, iacIssues);

			CxLogger.info(LOG_TAG + " Created ScanIssue with " + scanIssue.getVulnerabilities().size() +
					" vulnerabilities on line " + scanIssue.getProblematicLineNumber());
			return scanIssue;

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Failed to convert issues group to ScanIssue: " + e.getMessage());
			return null;
		}
	}

	private ScanIssue getScanIssue(List<IacRealtimeResults.Issue> iacIssues) {
		ScanIssue scanIssue = new ScanIssue();
		IacRealtimeResults.Issue firstIssue = iacIssues.get(0);

		int firstLine = 1;
		if (firstIssue.getLocations() != null && !firstIssue.getLocations().isEmpty()) {
			firstLine = firstIssue.getLocations().get(0).getLine();
		}

		// Set title based on whether there are multiple issues on the same line
		String title;
		if (iacIssues.size() > 1) {
			title = iacIssues.size() + DevAssistConstants.MULTIPLE_IAC_ISSUES;
		} else {
			title = firstIssue.getTitle();
		}

		scanIssue.setTitle(title);
		scanIssue.setDescription(firstIssue.getDescription());
		scanIssue.setSeverity(mapSeverity(firstIssue.getSeverity()));
		scanIssue.setFilePath(filePath);
		scanIssue.setScanEngine(ScanEngine.IAC);
		scanIssue.setProblematicLineNumber(firstLine);

		String scanIssueId = generateUniqueId(firstIssue, firstLine);
		scanIssue.setScanIssueId(scanIssueId);

		return scanIssue;
	}

	private Vulnerability createVulnerability(IacRealtimeResults.Issue iacIssue, String overrideId) {
		Vulnerability vulnerability = new Vulnerability();

		int firstLine = 1;
		if (iacIssue.getLocations() != null && !iacIssue.getLocations().isEmpty()) {
			firstLine = iacIssue.getLocations().get(0).getLine();
		}

		String vulnerabilityId = generateUniqueId(iacIssue, firstLine);
		if (overrideId != null && !overrideId.isBlank()) {
			vulnerabilityId = overrideId;
		}

		vulnerability.setVulnerabilityId(vulnerabilityId);
		vulnerability.setTitle(iacIssue.getTitle());
		vulnerability.setDescription(iacIssue.getDescription());
		vulnerability.setSeverity(mapSeverity(iacIssue.getSeverity()));

		CxLogger.info(LOG_TAG + " Created vulnerability '" + iacIssue.getTitle() +
				"' with vulnerabilityId '" + vulnerabilityId + "'");

		return vulnerability;
	}

	private void updateScanIssueTitleAndLocation(ScanIssue scanIssue, List<IacRealtimeResults.Issue> iacIssues) {
		// Update title based on actual number of vulnerabilities
		if (scanIssue.getVulnerabilities().size() == 1) {
			scanIssue.setTitle(scanIssue.getVulnerabilities().get(0).getTitle());
		} else if (scanIssue.getVulnerabilities().size() > 1) {
			scanIssue.setTitle(scanIssue.getVulnerabilities().size() + DevAssistConstants.MULTIPLE_IAC_ISSUES);
		}

		// Add location information from issues
		for (IacRealtimeResults.Issue iacIssue : iacIssues) {
			if (iacIssue.getLocations() != null) {
				for (RealtimeLocation loc : iacIssue.getLocations()) {
					Location location = new Location();
					location.setLine(loc.getLine()+1);
					location.setStartIndex(loc.getStartIndex());
					location.setEndIndex(loc.getEndIndex());
					scanIssue.getLocations().add(location);
				}
			}
		}

		// Ensure at least one location
		if (scanIssue.getLocations().isEmpty()) {
			Location location = new Location();
			location.setLine(scanIssue.getProblematicLineNumber());
			scanIssue.getLocations().add(location);
		}
	}

	private String mapSeverity(String severity) {
		if (severity == null) {
			return "Medium";
		}

		switch (severity.toLowerCase()) {
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

	private String generateUniqueId(IacRealtimeResults.Issue iacIssue, int line) {
		if (iacIssue != null) {
			return DevAssistUtils.generateUniqueId(
					line,
					iacIssue.getSimilarityId() + iacIssue.getTitle(),
					filePath);
		}
		return ScanEngine.IAC.name();
	}
}
