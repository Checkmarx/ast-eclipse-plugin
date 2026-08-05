package com.checkmarx.eclipse.devassist.scanners.oss;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.ossrealtime.OssRealtimeVulnerability;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.eclipse.devassist.common.ScanResult;
import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;
import com.checkmarx.eclipse.utils.CxLogger;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adaptor class for handling OSS scan results and converting them into a standardized format
 * using the {@link ScanResult} interface.
 * 
 * This class wraps an {@link OssRealtimeResults} instance and provides methods to process and extract
 * meaningful scan issues based on vulnerabilities detected in the packages.
 *
 * Adapted from JetBrains implementation for Eclipse platform.
 */
public class OssScanResultAdaptor implements ScanResult<OssRealtimeResults> {

	private static final String LOG_TAG = "[OSS-ADAPTOR]";

	private final OssRealtimeResults ossRealtimeResults;
	private final String filePath;
	private final List<ScanIssue> scanIssues;

	/**
	 * Constructs an instance of {@code OssScanResultAdaptor} with the specified OSS real-time results.
	 *
	 * @param ossRealtimeResults the OSS real-time scan results to be wrapped by this adapter
	 * @param filePath           the path of the file being scanned
	 */
	public OssScanResultAdaptor(OssRealtimeResults ossRealtimeResults, String filePath) {
		this.ossRealtimeResults = ossRealtimeResults;
		this.filePath = filePath;
		this.scanIssues = buildIssues();
	}

	/**
	 * Retrieves the raw OSS real-time scan results wrapped by this adapter.
	 *
	 * @return an {@link OssRealtimeResults} instance containing the results of the OSS scan
	 */
	@Override
	public OssRealtimeResults getResults() {
		return ossRealtimeResults;
	}

	/**
	 * Retrieves a list of scan issues discovered in the OSS real-time scan.
	 *
	 * @return a list of {@link ScanIssue} objects representing findings, or an empty list if none
	 */
	@Override
	public List<ScanIssue> getIssues() {
		return scanIssues;
	}

	/**
	 * Builds a list of ScanIssue objects from the OSS scan results.
	 * Processes packages obtained from scan results into standardized ScanIssue items.
	 *
	 * @return a list of ScanIssue objects
	 */
	private List<ScanIssue> buildIssues() {
		List<OssRealtimeScanPackage> packages = Objects.nonNull(getResults()) ? getResults().getPackages() : null;
		if (Objects.isNull(packages) || packages.isEmpty()) {
			CxLogger.info(LOG_TAG + " No scan results or packages available");
			return Collections.emptyList();
		}

		List<ScanIssue> issues = packages.stream()
				.map(this::createScanIssue)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		CxLogger.info(LOG_TAG + " Converted " + issues.size() + " OSS scan issues for file: " + filePath);
		return issues;
	}

	/**
	 * Creates a {@link ScanIssue} object based on the provided {@link OssRealtimeScanPackage}.
	 *
	 * @param packageObj the package object containing scan findings
	 * @return a structured {@link ScanIssue} instance
	 */
	private ScanIssue createScanIssue(OssRealtimeScanPackage packageObj) {
		if (packageObj == null) {
			return null;
		}

		try {
			ScanIssue scanIssue = new ScanIssue();

			scanIssue.setPackageManager(packageObj.getPackageManager());
			scanIssue.setTitle(packageObj.getPackageName());
			scanIssue.setPackageVersion(packageObj.getPackageVersion());
			scanIssue.setScanEngine(ScanEngine.OSS);
			scanIssue.setSeverity(DevAssistUtils.normalizeSeverity(packageObj.getStatus()));
			scanIssue.setFilePath(this.filePath);

			// Process location information
			if (Objects.nonNull(packageObj.getLocations()) && !packageObj.getLocations().isEmpty()) {
				packageObj.getLocations().forEach(location ->
						scanIssue.getLocations().add(createLocation(location)));
			}

			// Process vulnerabilities
			if (Objects.nonNull(packageObj.getVulnerabilities()) && !packageObj.getVulnerabilities().isEmpty()) {
				packageObj.getVulnerabilities().forEach(vulnerability ->
						scanIssue.getVulnerabilities().add(createVulnerability(vulnerability)));
			}

			// Set primary problem line based on first location (if available)
			int primaryLine = (Objects.nonNull(scanIssue.getLocations()) && !scanIssue.getLocations().isEmpty())
					? scanIssue.getLocations().get(0).getLine()
					: 1;
			scanIssue.setProblematicLineNumber(primaryLine);

			// Generate unique ID based on line, package manager + title, and version
			scanIssue.setScanIssueId(getUniqueId(scanIssue));

			return scanIssue;

		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Failed to convert package to ScanIssue: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Creates a {@link Vulnerability} instance based on the provided {@link OssRealtimeVulnerability}.
	 *
	 * @param vulnerabilityObj the OSS vulnerability object
	 * @return a standardized {@link Vulnerability} object
	 */
	private Vulnerability createVulnerability(OssRealtimeVulnerability vulnerabilityObj) {
		Vulnerability vulnerability = new Vulnerability();

		vulnerability.setCve(vulnerabilityObj.getCve());
		vulnerability.setTitle(vulnerabilityObj.getCve());
		vulnerability.setDescription(vulnerabilityObj.getDescription());
		vulnerability.setSeverity(DevAssistUtils.normalizeSeverity(vulnerabilityObj.getSeverity()));
		vulnerability.setFixVersion(vulnerabilityObj.getFixVersion());

		return vulnerability;
	}

	/**
	 * Creates a {@link Location} object based on the provided {@link RealtimeLocation}.
	 *
	 * @param location the real-time location details
	 * @return a new {@link Location} instance with 1-based line indexing
	 */
	private Location createLocation(RealtimeLocation location) {
		return new Location(getLine(location), location.getStartIndex(), location.getEndIndex());
	}

	/**
	 * Adjusts zero-based line numbers from OSS scanner to 1-based line numbers.
	 *
	 * @param location the real-time location
	 * @return 1-based line number
	 */
	private int getLine(RealtimeLocation location) {
		return location.getLine() + 1;
	}

	/**
	 * Generates a unique ID for the given scan issue using line, package identifier, and version.
	 *
	 * @param scanIssue the scan issue
	 * @return unique string identifier
	 */
	private String getUniqueId(ScanIssue scanIssue) {
		int line = (Objects.nonNull(scanIssue.getLocations()) && !scanIssue.getLocations().isEmpty())
				? scanIssue.getLocations().get(0).getLine()
				: 0;

		return DevAssistUtils.generateUniqueId(
				line,
				scanIssue.getPackageManager() + scanIssue.getTitle(),
				scanIssue.getPackageVersion()
		);
	}
}