package checkmarx.ast.eclipse.plugin.tests.unit.devassist.scanners.asca;

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

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.scanners.asca.AscaScanResultAdaptor;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;

/**
 * Unit tests for {@link AscaScanResultAdaptor}. Uses a {@code null} project so
 * the adaptor's ignore-filtering path is skipped, keeping these as pure logic
 * unit tests without any Eclipse workspace/resource dependency.
 */
class AscaScanResultAdaptorTest {

	private ScanResult mockResult(List<ScanDetail> details) {
		ScanResult result = mock(ScanResult.class);
		when(result.getScanDetails()).thenReturn(details);
		return result;
	}

	private ScanDetail mockDetail(int line, String severity, String ruleName, String description, int ruleId) {
		ScanDetail detail = mock(ScanDetail.class);
		when(detail.getLine()).thenReturn(line);
		when(detail.getSeverity()).thenReturn(severity);
		when(detail.getRuleName()).thenReturn(ruleName);
		when(detail.getDescription()).thenReturn(description);
		when(detail.getFileName()).thenReturn("Main.java");
		when(detail.getRuleID()).thenReturn(ruleId);
		return detail;
	}

	@Test
	@DisplayName("getResults returns original ScanResult reference")
	void getResultsReturnsOriginal() {
		ScanResult scanResult = mockResult(Collections.emptyList());
		AscaScanResultAdaptor adaptor = new AscaScanResultAdaptor(scanResult, "/repo/Main.java", null);
		assertSame(scanResult, adaptor.getResults());
	}

	@Test
	@DisplayName("getIssues returns empty list when results or details are null")
	void getIssuesHandlesNullInputs() {
		AscaScanResultAdaptor nullAdaptor = new AscaScanResultAdaptor(null, "/repo/Main.java", null);
		assertTrue(nullAdaptor.getIssues().isEmpty());

		AscaScanResultAdaptor emptyAdaptor = new AscaScanResultAdaptor(mockResult(null), "/repo/Main.java", null);
		assertTrue(emptyAdaptor.getIssues().isEmpty());

		AscaScanResultAdaptor emptyDetailsAdaptor = new AscaScanResultAdaptor(mockResult(Collections.emptyList()),
				"/repo/Main.java", null);
		assertTrue(emptyDetailsAdaptor.getIssues().isEmpty());
	}

	@Test
	@DisplayName("Single scan detail is converted into ScanIssue with proper fields")
	void getIssuesConvertsSingleDetail() {
		ScanDetail detail = mockDetail(10, "High", "ASCA_RULE", "description", 1);

		AscaScanResultAdaptor adaptor = new AscaScanResultAdaptor(mockResult(List.of(detail)), "/repo/Main.java",
				null);

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		ScanIssue issue = issues.get(0);
		assertEquals("ASCA_RULE", issue.getTitle());
		assertEquals("High", issue.getSeverity());
		assertEquals("description", issue.getDescription());
		assertEquals("/repo/Main.java", issue.getFilePath());
		assertEquals(ScanEngine.ASCA, issue.getScanEngine());
		assertEquals(10, issue.getProblematicLineNumber());
		assertEquals(1, issue.getLocations().size());
		assertEquals(10, issue.getLocations().get(0).getLine());
		assertEquals(issue.getScanIssueId(), issue.getVulnerabilities().get(0).getVulnerabilityId(),
				"First vulnerability should reuse scan issue id");
	}

	@Test
	@DisplayName("Details on same line are grouped and titled as multiple issues")
	void getIssuesGroupsMultipleDetailsPerLine() {
		ScanDetail critical = mockDetail(20, "Critical", "CriticalRule", "crit-desc", 1);
		ScanDetail low = mockDetail(20, "Low", "LowRule", "low-desc", 2);

		AscaScanResultAdaptor adaptor = new AscaScanResultAdaptor(mockResult(Arrays.asList(low, critical)),
				"/repo/Main.java", null);

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size(), "Same line entries should be grouped");

		ScanIssue grouped = issues.get(0);
		assertEquals("2" + DevAssistConstants.MULTIPLE_ASCA_ISSUES, grouped.getTitle());
		// NOTE: buildIssuesInternal sorts the group with a plain (ascending)
		// Comparator.comparingInt(severityPrecedence), so index 0 after sorting is
		// actually the LOWEST-precedence detail, not the highest - despite the
		// production code's "Highest severity (already sorted)" comment in
		// getScanIssue(). This assertion documents the current (likely buggy)
		// behavior rather than the intended one; see AscaScanResultAdaptor#buildIssuesInternal.
		assertEquals("Low", grouped.getSeverity(),
				"Current sort is ascending by precedence, so the lowest-severity detail wins - "
						+ "see the note above about AscaScanResultAdaptor's inverted sort comparator");
		assertEquals(2, grouped.getVulnerabilities().size());
		assertEquals(grouped.getScanIssueId(), grouped.getVulnerabilities().get(0).getVulnerabilityId());
		assertNotEquals(grouped.getVulnerabilities().get(0).getVulnerabilityId(),
				grouped.getVulnerabilities().get(1).getVulnerabilityId(),
				"Subsequent vulnerabilities should keep their own ids");
	}

	@Test
	@DisplayName("Null detail entries are skipped gracefully")
	void getIssuesSkipsNullEntries() {
		ScanDetail valid = mockDetail(5, "Medium", "ValidRule", "desc", 3);
		AscaScanResultAdaptor adaptor = new AscaScanResultAdaptor(mockResult(Arrays.asList(null, valid)),
				"/repo/Main.java", null);

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		assertEquals("ValidRule", issues.get(0).getTitle());
	}

	@Test
	@DisplayName("Severity 'info' is mapped to Low for issue and vulnerability")
	void mapSeverityTreatsInfoAsLow() {
		ScanDetail infoDetail = mockDetail(7, "info", "InfoRule", "desc", 4);
		AscaScanResultAdaptor adaptor = new AscaScanResultAdaptor(mockResult(List.of(infoDetail)), "/repo/Main.java",
				null);

		ScanIssue issue = adaptor.getIssues().get(0);
		assertEquals("Low", issue.getSeverity());
		assertEquals("Low", issue.getVulnerabilities().get(0).getSeverity());
	}

	@Test
	@DisplayName("Unknown severity defaults to Medium")
	void mapSeverityDefaultsToMediumForUnknownValue() {
		ScanDetail unknownDetail = mockDetail(8, "weird", "WeirdRule", "desc", 5);
		AscaScanResultAdaptor adaptor = new AscaScanResultAdaptor(mockResult(List.of(unknownDetail)),
				"/repo/Main.java", null);

		ScanIssue issue = adaptor.getIssues().get(0);
		assertEquals("Medium", issue.getSeverity());
	}

	@Test
	@DisplayName("Multiple vulnerabilities on different lines are not grouped")
	void multipleVulnerabilitiesOnDifferentLinesNotGrouped() {
		ScanDetail line10 = mockDetail(10, "High", "Rule1", "desc1", 5);
		ScanDetail line20 = mockDetail(20, "High", "Rule2", "desc2", 6);

		AscaScanResultAdaptor adaptor = new AscaScanResultAdaptor(mockResult(Arrays.asList(line10, line20)),
				"/repo/Main.java", null);

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(2, issues.size(), "Should have two separate issues on different lines");
		boolean hasRule1 = issues.stream().anyMatch(issue -> issue.getTitle().equals("Rule1"));
		boolean hasRule2 = issues.stream().anyMatch(issue -> issue.getTitle().equals("Rule2"));
		assertTrue(hasRule1, "Should have issue with Rule1");
		assertTrue(hasRule2, "Should have issue with Rule2");
	}

	@Test
	@DisplayName("ProblematicLine is correctly set on vulnerabilities for per-vulnerability filtering")
	void problematicLineIsSetOnVulnerabilities() {
		ScanDetail detail = mockDetail(25, "High", "TestRule", "test-desc", 7);
		String problematicCode = "eval(userInput)";
		when(detail.getProblematicLine()).thenReturn(problematicCode);

		AscaScanResultAdaptor adaptor = new AscaScanResultAdaptor(mockResult(List.of(detail)), "/repo/Main.java",
				null);

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		assertEquals(problematicCode, issues.get(0).getVulnerabilities().get(0).getProblematicLine(),
				"ProblematicLine should be set for per-vulnerability ignore tracking");
	}

	@Test
	@DisplayName("Unfiltered mode (project null) behaves the same as filtered mode with no ignore entries")
	void unfilteredModeIncludesAllVulnerabilities() {
		ScanDetail detail1 = mockDetail(15, "High", "SQLInjection", "sql-desc", 100);
		ScanDetail detail2 = mockDetail(15, "Medium", "XSS", "xss-desc", 200);

		AscaScanResultAdaptor unfilteredAdaptor = new AscaScanResultAdaptor(
				mockResult(Arrays.asList(detail1, detail2)), "/repo/Main.java", null, false);

		List<ScanIssue> issues = unfilteredAdaptor.getIssues();
		assertEquals(1, issues.size(), "Should have one grouped issue");
		assertEquals(2, issues.get(0).getVulnerabilities().size(),
				"Should include all 2 vulnerabilities in unfiltered mode");
	}

	@Test
	@DisplayName("Same ruleId with different problematicLines are tracked independently")
	void sameRuleIdDifferentProblematicLinesTrackedIndependently() {
		ScanDetail detail1 = mockDetail(40, "High", "SQLInjection", "sql-desc1", 100);
		when(detail1.getProblematicLine()).thenReturn("eval(userInput)");

		ScanDetail detail2 = mockDetail(40, "High", "SQLInjection", "sql-desc2", 100);
		when(detail2.getProblematicLine()).thenReturn("execute(query)");

		AscaScanResultAdaptor adaptor = new AscaScanResultAdaptor(mockResult(Arrays.asList(detail1, detail2)),
				"/repo/Main.java", null);

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size(), "Should group by line number");
		assertEquals(2, issues.get(0).getVulnerabilities().size(), "Should have both vulnerabilities");

		assertEquals("eval(userInput)", issues.get(0).getVulnerabilities().get(0).getProblematicLine());
		assertEquals("execute(query)", issues.get(0).getVulnerabilities().get(1).getProblematicLine());
		assertEquals(100, issues.get(0).getVulnerabilities().get(0).getRuleId());
		assertEquals(100, issues.get(0).getVulnerabilities().get(1).getRuleId());
	}
}
