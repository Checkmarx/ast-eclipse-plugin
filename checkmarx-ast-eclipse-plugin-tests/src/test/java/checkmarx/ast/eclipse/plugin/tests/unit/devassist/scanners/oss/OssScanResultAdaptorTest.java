package checkmarx.ast.eclipse.plugin.tests.unit.devassist.scanners.oss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.ossrealtime.OssRealtimeVulnerability;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.scanners.oss.OssScanResultAdaptor;

/**
 * Unit tests for {@link OssScanResultAdaptor}. Pure logic tests - no Eclipse
 * workspace/resource dependency required.
 */
class OssScanResultAdaptorTest {

	private OssRealtimeResults mockResults(List<OssRealtimeScanPackage> packages) {
		OssRealtimeResults results = mock(OssRealtimeResults.class);
		when(results.getPackages()).thenReturn(packages);
		return results;
	}

	private RealtimeLocation mockLocation(int line, int start, int end) {
		RealtimeLocation location = mock(RealtimeLocation.class);
		when(location.getLine()).thenReturn(line);
		when(location.getStartIndex()).thenReturn(start);
		when(location.getEndIndex()).thenReturn(end);
		return location;
	}

	private OssRealtimeVulnerability mockVulnerability(String cve, String description, String severity,
			String fixVersion) {
		OssRealtimeVulnerability vulnerability = mock(OssRealtimeVulnerability.class);
		when(vulnerability.getCve()).thenReturn(cve);
		when(vulnerability.getDescription()).thenReturn(description);
		when(vulnerability.getSeverity()).thenReturn(severity);
		when(vulnerability.getFixVersion()).thenReturn(fixVersion);
		return vulnerability;
	}

	private OssRealtimeScanPackage mockPackage(String manager, String name, String version, String status,
			List<RealtimeLocation> locations, List<OssRealtimeVulnerability> vulnerabilities) {
		OssRealtimeScanPackage pkg = mock(OssRealtimeScanPackage.class);
		when(pkg.getPackageManager()).thenReturn(manager);
		when(pkg.getPackageName()).thenReturn(name);
		when(pkg.getPackageVersion()).thenReturn(version);
		when(pkg.getStatus()).thenReturn(status);
		when(pkg.getLocations()).thenReturn(locations);
		when(pkg.getVulnerabilities()).thenReturn(vulnerabilities);
		return pkg;
	}

	@Test
	@DisplayName("getResults returns original results reference")
	void getResultsReturnsOriginal() {
		OssRealtimeResults results = mockResults(Collections.emptyList());
		OssScanResultAdaptor adaptor = new OssScanResultAdaptor(results, "/repo/pom.xml");
		assertSame(results, adaptor.getResults());
	}

	@Test
	@DisplayName("getIssues returns empty list when results or packages are null/empty")
	void getIssuesHandlesNullOrEmpty() {
		assertTrue(new OssScanResultAdaptor(null, "/repo/pom.xml").getIssues().isEmpty());
		assertTrue(new OssScanResultAdaptor(mockResults(null), "/repo/pom.xml").getIssues().isEmpty());
		assertTrue(new OssScanResultAdaptor(mockResults(Collections.emptyList()), "/repo/pom.xml").getIssues()
				.isEmpty());
	}

	@Test
	@DisplayName("Single package with location and vulnerability converts to a ScanIssue")
	void getIssuesConvertsSinglePackage() {
		RealtimeLocation location = mockLocation(6, 0, 10);
		OssRealtimeVulnerability vulnerability = mockVulnerability("CVE-2024-0001", "desc", "High", "2.0.0");
		OssRealtimeScanPackage pkg = mockPackage("npm", "lodash", "1.0.0", "High", List.of(location),
				List.of(vulnerability));

		OssScanResultAdaptor adaptor = new OssScanResultAdaptor(mockResults(List.of(pkg)), "/repo/package.json");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		ScanIssue issue = issues.get(0);
		assertEquals("npm", issue.getPackageManager());
		assertEquals("lodash", issue.getTitle());
		assertEquals("1.0.0", issue.getPackageVersion());
		assertEquals(ScanEngine.OSS, issue.getScanEngine());
		assertEquals("/repo/package.json", issue.getFilePath());
		assertEquals(1, issue.getLocations().size());
		// Location line is zero-based upstream and incremented by 1 in the adaptor
		assertEquals(7, issue.getLocations().get(0).getLine());
		assertEquals(7, issue.getProblematicLineNumber());
		assertEquals(1, issue.getVulnerabilities().size());
		assertEquals("CVE-2024-0001", issue.getVulnerabilities().get(0).getCve());
		assertEquals("CVE-2024-0001", issue.getVulnerabilities().get(0).getTitle());
		assertEquals("2.0.0", issue.getVulnerabilities().get(0).getFixVersion());
	}

	@Test
	@DisplayName("Package without locations defaults problematic line to 1")
	void getIssuesDefaultsLineWhenNoLocations() {
		OssRealtimeScanPackage pkg = mockPackage("npm", "axios", "0.1.0", "Low", Collections.emptyList(),
				Collections.emptyList());

		OssScanResultAdaptor adaptor = new OssScanResultAdaptor(mockResults(List.of(pkg)), "/repo/package.json");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		assertEquals(1, issues.get(0).getProblematicLineNumber());
	}

	@Test
	@DisplayName("Packages that resolve to the same scan issue id are de-duplicated, keeping the first")
	void getIssuesDeduplicatesByScanIssueId() {
		// Same manager/name/version/location -> identical generated scan issue id
		RealtimeLocation location = mockLocation(2, 0, 5);
		OssRealtimeScanPackage pkg1 = mockPackage("npm", "lodash", "1.0.0", "High", List.of(location),
				Collections.emptyList());
		OssRealtimeScanPackage pkg2 = mockPackage("npm", "lodash", "1.0.0", "High", List.of(location),
				Collections.emptyList());

		OssScanResultAdaptor adaptor = new OssScanResultAdaptor(mockResults(List.of(pkg1, pkg2)),
				"/repo/package.json");

		assertEquals(1, adaptor.getIssues().size(), "Duplicate packages should collapse into a single scan issue");
	}

	@Test
	@DisplayName("Distinct packages produce distinct scan issues")
	void getIssuesKeepsDistinctPackagesSeparate() {
		OssRealtimeScanPackage pkg1 = mockPackage("npm", "lodash", "1.0.0", "High", Collections.emptyList(),
				Collections.emptyList());
		OssRealtimeScanPackage pkg2 = mockPackage("npm", "axios", "0.1.0", "Low", Collections.emptyList(),
				Collections.emptyList());

		OssScanResultAdaptor adaptor = new OssScanResultAdaptor(mockResults(List.of(pkg1, pkg2)),
				"/repo/package.json");

		assertEquals(2, adaptor.getIssues().size());
	}

	@Test
	@DisplayName("Null package entries in the results are skipped without throwing")
	void getIssuesSkipsNullPackages() {
		OssRealtimeScanPackage pkg = mockPackage("npm", "axios", "0.1.0", "Low", Collections.emptyList(),
				Collections.emptyList());
		List<OssRealtimeScanPackage> packages = new java.util.ArrayList<>();
		packages.add(null);
		packages.add(pkg);

		OssScanResultAdaptor adaptor = new OssScanResultAdaptor(mockResults(packages), "/repo/package.json");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		assertEquals("axios", issues.get(0).getTitle());
	}
}
