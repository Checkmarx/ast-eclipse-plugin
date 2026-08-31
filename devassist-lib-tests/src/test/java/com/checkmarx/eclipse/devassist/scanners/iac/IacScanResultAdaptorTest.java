package com.checkmarx.eclipse.devassist.scanners.iac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;

/**
 * Unit tests for {@link IacScanResultAdaptor}. Pure logic tests - no Eclipse
 * workspace/resource dependency required.
 */
class IacScanResultAdaptorTest {

	private IacRealtimeResults mockResults(List<IacRealtimeResults.Issue> issues) {
		IacRealtimeResults results = mock(IacRealtimeResults.class);
		when(results.getResults()).thenReturn(issues);
		return results;
	}

	private RealtimeLocation mockLocation(int line, int start, int end) {
		RealtimeLocation location = mock(RealtimeLocation.class);
		when(location.getLine()).thenReturn(line);
		when(location.getStartIndex()).thenReturn(start);
		when(location.getEndIndex()).thenReturn(end);
		return location;
	}

	private IacRealtimeResults.Issue mockIssue(String title, String description, String severity, String similarityId,
			List<RealtimeLocation> locations) {
		IacRealtimeResults.Issue issue = mock(IacRealtimeResults.Issue.class);
		when(issue.getTitle()).thenReturn(title);
		when(issue.getDescription()).thenReturn(description);
		when(issue.getSeverity()).thenReturn(severity);
		when(issue.getSimilarityId()).thenReturn(similarityId);
		when(issue.getLocations()).thenReturn(locations);
		return issue;
	}

	@Test
	@DisplayName("getResults returns original results reference")
	void getResultsReturnsOriginal() {
		IacRealtimeResults results = mockResults(Collections.emptyList());
		IacScanResultAdaptor adaptor = new IacScanResultAdaptor(results, "/repo/main.tf");
		assertSame(results, adaptor.getResults());
	}

	@Test
	@DisplayName("getIssues returns empty list when results or issue list are null/empty")
	void getIssuesHandlesNullOrEmpty() {
		assertTrue(new IacScanResultAdaptor(null, "/repo/main.tf").getIssues().isEmpty());
		assertTrue(new IacScanResultAdaptor(mockResults(null), "/repo/main.tf").getIssues().isEmpty());
		assertTrue(new IacScanResultAdaptor(mockResults(Collections.emptyList()), "/repo/main.tf").getIssues()
				.isEmpty());
	}

	@Test
	@DisplayName("Single issue converts into ScanIssue with proper fields")
	void getIssuesConvertsSingleIssue() {
		RealtimeLocation location = mockLocation(9, 0, 5);
		IacRealtimeResults.Issue issue = mockIssue("Open Security Group", "desc", "High", "SIM-1",
				List.of(location));

		IacScanResultAdaptor adaptor = new IacScanResultAdaptor(mockResults(List.of(issue)), "/repo/main.tf");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		ScanIssue scanIssue = issues.get(0);
		assertEquals("Open Security Group", scanIssue.getTitle());
		assertEquals("High", scanIssue.getSeverity());
		assertEquals("desc", scanIssue.getDescription());
		assertEquals("/repo/main.tf", scanIssue.getFilePath());
		assertEquals(ScanEngine.IAC, scanIssue.getScanEngine());
		assertEquals(9, scanIssue.getProblematicLineNumber());
		assertEquals(1, scanIssue.getLocations().size());
		// Location line is zero-based upstream and incremented by 1 in the adaptor
		assertEquals(10, scanIssue.getLocations().get(0).getLine());
		assertEquals(1, scanIssue.getVulnerabilities().size());
		assertEquals(scanIssue.getScanIssueId(), scanIssue.getVulnerabilities().get(0).getVulnerabilityId());
	}

	@Test
	@DisplayName("Issues on same line are grouped and titled as multiple issues")
	void getIssuesGroupsMultipleIssuesPerLine() {
		RealtimeLocation location1 = mockLocation(20, 0, 5);
		RealtimeLocation location2 = mockLocation(20, 0, 8);
		IacRealtimeResults.Issue high = mockIssue("HighRule", "high-desc", "High", "SIM-1", List.of(location1));
		IacRealtimeResults.Issue low = mockIssue("LowRule", "low-desc", "Low", "SIM-2", List.of(location2));

		IacScanResultAdaptor adaptor = new IacScanResultAdaptor(mockResults(Arrays.asList(high, low)),
				"/repo/main.tf");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size(), "Same line issues should be grouped");
		ScanIssue grouped = issues.get(0);
		assertEquals("2" + DevAssistConstants.MULTIPLE_IAC_ISSUES, grouped.getTitle());
		assertEquals(2, grouped.getVulnerabilities().size());
		assertNotEquals(grouped.getVulnerabilities().get(0).getVulnerabilityId(),
				grouped.getVulnerabilities().get(1).getVulnerabilityId());
	}

	@Test
	@DisplayName("Issues without locations default to line 1")
	void getIssuesDefaultsLineWhenNoLocations() {
		IacRealtimeResults.Issue issue = mockIssue("NoLocationRule", "desc", "Medium", "SIM-3", null);

		IacScanResultAdaptor adaptor = new IacScanResultAdaptor(mockResults(List.of(issue)), "/repo/main.tf");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		assertEquals(1, issues.get(0).getProblematicLineNumber());
		assertEquals(1, issues.get(0).getLocations().size(), "A fallback location should be added");
	}

	@Test
	@DisplayName("Severity 'info' is mapped to Low")
	void mapSeverityTreatsInfoAsLow() {
		IacRealtimeResults.Issue issue = mockIssue("InfoRule", "desc", "info", "SIM-4",
				List.of(mockLocation(3, 0, 1)));

		IacScanResultAdaptor adaptor = new IacScanResultAdaptor(mockResults(List.of(issue)), "/repo/main.tf");

		assertEquals("Low", adaptor.getIssues().get(0).getSeverity());
	}

	@Test
	@DisplayName("Null severity defaults to Medium")
	void mapSeverityDefaultsToMediumForNull() {
		IacRealtimeResults.Issue issue = mockIssue("NullSeverityRule", "desc", null, "SIM-5",
				List.of(mockLocation(3, 0, 1)));

		IacScanResultAdaptor adaptor = new IacScanResultAdaptor(mockResults(List.of(issue)), "/repo/main.tf");

		assertEquals("Medium", adaptor.getIssues().get(0).getSeverity());
	}

	@Test
	@DisplayName("actualValue, expectedValue and similarityId are carried onto the vulnerability")
	void vulnerabilityCarriesActualExpectedAndSimilarityId() {
		IacRealtimeResults.Issue issue = mockIssue("Rule", "desc", "High", "SIM-6", List.of(mockLocation(1, 0, 1)));
		when(issue.getActualValue()).thenReturn("actual");
		when(issue.getExpectedValue()).thenReturn("expected");

		IacScanResultAdaptor adaptor = new IacScanResultAdaptor(mockResults(List.of(issue)), "/repo/main.tf");

		var vulnerability = adaptor.getIssues().get(0).getVulnerabilities().get(0);
		assertEquals("actual", vulnerability.getActualValue());
		assertEquals("expected", vulnerability.getExpectedValue());
		assertEquals("SIM-6", vulnerability.getSimilarityId());
	}

	@Test
	@DisplayName("Issues on different lines are not grouped")
	void issuesOnDifferentLinesAreNotGrouped() {
		IacRealtimeResults.Issue issue1 = mockIssue("Rule1", "desc1", "High", "SIM-7",
				List.of(mockLocation(1, 0, 1)));
		IacRealtimeResults.Issue issue2 = mockIssue("Rule2", "desc2", "High", "SIM-8",
				List.of(mockLocation(2, 0, 1)));

		IacScanResultAdaptor adaptor = new IacScanResultAdaptor(mockResults(Arrays.asList(issue1, issue2)),
				"/repo/main.tf");

		assertEquals(2, adaptor.getIssues().size());
	}
}
