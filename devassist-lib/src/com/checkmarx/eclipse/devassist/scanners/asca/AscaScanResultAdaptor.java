package com.checkmarx.eclipse.devassist.scanners.asca;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.ignore.IgnoreEntry;
import com.checkmarx.eclipse.devassist.ignore.IgnoreFileManager;
import com.checkmarx.eclipse.devassist.ignore.IgnoreManager;
import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;
import com.checkmarx.eclipse.common.utils.CxLogger;
import org.eclipse.core.resources.IProject;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter class for handling ASCA scan results and converting them into a
 * standardized format.
 *
 * This class wraps a ASCA {@link ScanResult} instance and provides methods to
 * process and extract
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

	private final com.checkmarx.ast.asca.ScanResult ascaScanResult;
	private final String filePath;
	private final IProject project;
	private final List<ScanIssue> scanIssues;

	/**
	 * Constructs an instance of AscaScanResultAdaptor with the specified ASCA scan
	 * results.
	 *
	 * @param ascaScanResult the ASCA scan results to be wrapped
	 * @param filePath       the path of the file being scanned
	 * @param project        the project the file belongs to, used to filter out
	 *                       already-ignored vulnerabilities - ASCA's CLI has no
	 *                       ignore-file exclusion of its own (see
	 *                       AscaScannerService#getIgnoreFilePath), so this app-level
	 *                       filtering is the only enforcement point
	 */
	public AscaScanResultAdaptor(com.checkmarx.ast.asca.ScanResult ascaScanResult, String filePath, IProject project) {
		this(ascaScanResult, filePath, project, true);
	}

	/**
	 * Constructs an instance of AscaScanResultAdaptor with optional ignore filtering.
	 *
	 * @param ascaScanResult the ASCA scan results to be wrapped
	 * @param filePath       the path of the file being scanned
	 * @param project        the project the file belongs to, used to filter out
	 *                       already-ignored vulnerabilities
	 * @param filterIgnored  whether to filter out already-ignored vulnerabilities
	 *                       (true = filter, false = keep all). Pass false when
	 *                       building the adaptor used to reconcile ignored
	 *                       entries' line numbers, since that pass needs every
	 *                       vulnerability - including already-ignored ones - to
	 *                       track which occurrences are still present in the code.
	 */
	public AscaScanResultAdaptor(com.checkmarx.ast.asca.ScanResult ascaScanResult, String filePath, IProject project,
			boolean filterIgnored) {
		this.ascaScanResult = ascaScanResult;
		this.filePath = filePath;
		this.project = project;
		this.scanIssues = filterIgnored ? buildIssues() : buildIssuesUnfiltered();
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
	 * Builds a list of ScanIssue objects from the ASCA scan results, filtering out
	 * already-ignored vulnerabilities.
	 * Groups multiple vulnerabilities on the same line and sorts them by severity.
	 */
	private List<ScanIssue> buildIssues() {
		return buildIssuesInternal(true);
	}

	/**
	 * Builds a list of ScanIssue objects from the ASCA scan results WITHOUT filtering
	 * ignored vulnerabilities. Used to reconcile ignored entries' line numbers, where all
	 * vulnerabilities (including already-ignored ones) are needed to track which occurrences
	 * are still present in the code.
	 */
	private List<ScanIssue> buildIssuesUnfiltered() {
		return buildIssuesInternal(false);
	}

	/**
	 * Groups multiple vulnerabilities on the same line and sorts them by severity.
	 *
	 * @param applyFilter true to filter out already-ignored vulnerabilities, false to keep all
	 */
	private List<ScanIssue> buildIssuesInternal(boolean applyFilter) {
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
							detailsList.sort(
									Comparator.comparingInt((ScanDetail detail) -> getSeverityPrecedence(detail.getSeverity())).reversed());
							return detailsList;
						})));

		// ASCA's CLI has no ignore-file exclusion of its own (see
		// AscaScannerService#getIgnoreFilePath), so already-ignored vulnerabilities must be
		// filtered out here, at the point ScanIssues are built - fetched once per file build
		// rather than per group, since it's the same snapshot for every line in this file.
		IgnoreManager ignoreManager = (applyFilter && project != null) ? IgnoreManager.getInstance(project) : null;
		List<IgnoreEntry> ignoreEntries = (applyFilter && project != null)
				? IgnoreFileManager.getInstance(project).getAllIgnoreEntries() : Collections.emptyList();

		List<ScanIssue> issues = groupedIssues.values().stream()
				.map(detailList -> createScanIssueForGroup(detailList, ignoreManager, ignoreEntries))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		CxLogger.info(LOG_TAG + (applyFilter ? " Converted " : " Converted (unfiltered) ") + issues.size()
				+ " grouped scan issues for file: " + filePath);
		return issues;
	}

	/**
	 * Creates a ScanIssue from a group of ASCA scan details that are on the same
	 * line, filtering out any vulnerability already recorded as ignored -
	 * individually, by rule name + problematic line - rather than dropping or
	 * keeping the whole group, so ignoring one vulnerability on a multi-vulnerability
	 * line never hides the others on that line.
	 *
	 * @param ascaScanDetails the list of ASCA scan details for the same line
	 *                        (already sorted by severity)
	 * @param ignoreManager   resolves whether a given vulnerability is ignored, or
	 *                        {@code null} if no project context is available or filtering
	 *                        is disabled
	 * @param ignoreEntries   the current ignore entries to check against
	 * @return a ScanIssue representing the ASCA finding(s), or null if every
	 *         vulnerability in the group is ignored or conversion fails
	 */
	private ScanIssue createScanIssueForGroup(List<ScanDetail> ascaScanDetails, IgnoreManager ignoreManager,
			List<IgnoreEntry> ignoreEntries) {
		if (ascaScanDetails == null || ascaScanDetails.isEmpty()) {
			return null;
		}

		try {
			ScanIssue scanIssue = getScanIssue(ascaScanDetails);

			// Add vulnerabilities from all details in the group, skipping ones already ignored
			for (int i = 0; i < ascaScanDetails.size(); i++) {
				ScanDetail detail = ascaScanDetails.get(i);
				String vulnerabilityId = (i == 0) ? scanIssue.getScanIssueId() : null;
				Vulnerability vuln = createVulnerability(detail, vulnerabilityId);

				if (ignoreManager != null && ignoreManager.isAscaVulnerabilityIgnored(vuln, ignoreEntries, filePath)) {
					CxLogger.info(LOG_TAG + " Skipping ignored vulnerability '" + vuln.getTitle() +
							"' on line " + detail.getLine());
					continue;
				}
				scanIssue.getVulnerabilities().add(vuln);
			}

			// If every vulnerability on this line is ignored, drop the ScanIssue entirely
			if (scanIssue.getVulnerabilities().isEmpty()) {
				return null;
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
	 * Creates a ScanIssue with appropriate title and basic properties from a group
	 * of ASCA scan details.
	 *
	 * @param ascaScanDetails the list of ASCA scan details (already sorted by
	 *                        severity)
	 * @return a ScanIssue with basic properties set
	 */
	private ScanIssue getScanIssue(List<ScanDetail> ascaScanDetails) {
		ScanIssue scanIssue = new ScanIssue();
		ScanDetail firstDetail = ascaScanDetails.get(0); // Highest severity (already sorted)

		// Set title based on whether there are multiple issues on the same line
		String title;
		if (ascaScanDetails.size() > 1) {
			title = ascaScanDetails.size() + DevAssistConstants.MULTIPLE_ASCA_ISSUES;
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
		vulnerability.setRuleId(scanDetail.getRuleID());
		vulnerability.setProblematicLine(scanDetail.getProblematicLine());

		CxLogger.info(LOG_TAG + " Created vulnerability '" + scanDetail.getRuleName() +
				"' with vulnerabilityId '" + vulnerabilityId + "'");

		return vulnerability;
	}

	/**
	 * Updates the ScanIssue title and location based on vulnerability count and
	 * scan details.
	 */
	private void updateScanIssueTitleAndLocation(ScanIssue scanIssue, List<ScanDetail> ascaScanDetails) {
		// Update title based on actual number of vulnerabilities
		if (scanIssue.getVulnerabilities().size() == 1) {
			scanIssue.setTitle(scanIssue.getVulnerabilities().get(0).getTitle());
		} else if (scanIssue.getVulnerabilities().size() > 1) {
			scanIssue.setTitle(scanIssue.getVulnerabilities().size() + DevAssistConstants.MULTIPLE_ASCA_ISSUES);
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
			case "malicious":
				return 6;
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
