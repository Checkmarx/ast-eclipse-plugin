package checkmarx.ast.eclipse.plugin.tests.unit.devassist.scanners.containers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.ast.containersrealtime.ContainersRealtimeImage;
import com.checkmarx.ast.containersrealtime.ContainersRealtimeResults;
import com.checkmarx.ast.containersrealtime.ContainersRealtimeVulnerability;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.scanners.containers.ContainerScanResultAdaptor;

/**
 * Unit tests for {@link ContainerScanResultAdaptor}. Pure logic tests - no
 * Eclipse workspace/resource dependency required.
 */
class ContainerScanResultAdaptorTest {

	private RealtimeLocation mockLocation(int line, int start, int end) {
		RealtimeLocation location = mock(RealtimeLocation.class);
		when(location.getLine()).thenReturn(line);
		when(location.getStartIndex()).thenReturn(start);
		when(location.getEndIndex()).thenReturn(end);
		return location;
	}

	private ContainersRealtimeVulnerability mockVulnerability(String cve, String severity) {
		ContainersRealtimeVulnerability vulnerability = mock(ContainersRealtimeVulnerability.class);
		when(vulnerability.getCve()).thenReturn(cve);
		when(vulnerability.getSeverity()).thenReturn(severity);
		return vulnerability;
	}

	private ContainersRealtimeImage mockImage(String name, String tag, String status, List<RealtimeLocation> locations,
			List<ContainersRealtimeVulnerability> vulnerabilities) {
		ContainersRealtimeImage image = mock(ContainersRealtimeImage.class);
		when(image.getImageName()).thenReturn(name);
		when(image.getImageTag()).thenReturn(tag);
		when(image.getStatus()).thenReturn(status);
		when(image.getLocations()).thenReturn(locations);
		when(image.getVulnerabilities()).thenReturn(vulnerabilities);
		return image;
	}

	private ContainersRealtimeResults mockResults(List<ContainersRealtimeImage> images) {
		ContainersRealtimeResults results = mock(ContainersRealtimeResults.class);
		when(results.getImages()).thenReturn(images);
		return results;
	}

	@Test
	@DisplayName("getResults returns original results reference")
	void getResultsReturnsOriginal() {
		ContainersRealtimeResults results = mockResults(Collections.emptyList());
		ContainerScanResultAdaptor adaptor = new ContainerScanResultAdaptor(results, "dockerfile", "/repo/Dockerfile");
		assertSame(results, adaptor.getResults());
	}

	@Test
	@DisplayName("getIssues returns empty list when results or images are null/empty")
	void getIssuesHandlesNullOrEmptyImages() {
		assertTrue(new ContainerScanResultAdaptor(null, "dockerfile", "/repo/Dockerfile").getIssues().isEmpty());
		assertTrue(new ContainerScanResultAdaptor(mockResults(null), "dockerfile", "/repo/Dockerfile").getIssues()
				.isEmpty());
		assertTrue(new ContainerScanResultAdaptor(mockResults(Collections.emptyList()), "dockerfile",
				"/repo/Dockerfile").getIssues().isEmpty());
	}

	@Test
	@DisplayName("Single image with location and vulnerability converts to a ScanIssue")
	void getIssuesConvertsSingleImage() {
		RealtimeLocation location = mockLocation(4, 0, 10);
		ContainersRealtimeVulnerability vulnerability = mockVulnerability("CVE-2024-1234", "High");
		ContainersRealtimeImage image = mockImage("nginx", "latest", "High", List.of(location),
				List.of(vulnerability));

		ContainerScanResultAdaptor adaptor = new ContainerScanResultAdaptor(mockResults(List.of(image)), "dockerfile",
				"/repo/Dockerfile");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		ScanIssue issue = issues.get(0);
		assertEquals("nginx", issue.getTitle());
		assertEquals("latest", issue.getImageTag());
		assertEquals(ScanEngine.CONTAINERS, issue.getScanEngine());
		assertEquals("dockerfile", issue.getFileType());
		assertEquals("/repo/Dockerfile", issue.getFilePath());
		assertEquals(1, issue.getLocations().size());
		// Location line is zero-based upstream and incremented by 1 in the adaptor
		assertEquals(5, issue.getLocations().get(0).getLine());
		assertEquals(5, issue.getProblematicLineNumber());
		assertEquals(1, issue.getVulnerabilities().size());
		assertEquals("CVE-2024-1234", issue.getVulnerabilities().get(0).getCve());
	}

	@Test
	@DisplayName("Image without locations defaults problematic line to 1")
	void getIssuesDefaultsLineWhenNoLocations() {
		ContainersRealtimeImage image = mockImage("alpine", "3.19", "Low", Collections.emptyList(),
				Collections.emptyList());

		ContainerScanResultAdaptor adaptor = new ContainerScanResultAdaptor(mockResults(List.of(image)), "dockerfile",
				"/repo/Dockerfile");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		assertEquals(1, issues.get(0).getProblematicLineNumber());
		assertTrue(issues.get(0).getLocations().isEmpty());
	}

	@Test
	@DisplayName("Vulnerability description maps to risk text based on severity")
	void vulnerabilityDescriptionMapsRiskText() {
		ContainersRealtimeVulnerability critical = mockVulnerability("CVE-1", "Critical");
		ContainersRealtimeVulnerability unknownSeverity = mockVulnerability("CVE-2", "Weird");
		ContainersRealtimeImage image = mockImage("img", "tag", "Critical", Collections.emptyList(),
				List.of(critical, unknownSeverity));

		ContainerScanResultAdaptor adaptor = new ContainerScanResultAdaptor(mockResults(List.of(image)), "dockerfile",
				"/repo/Dockerfile");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals("Container image contains critical severity security vulnerabilities.",
				issues.get(0).getVulnerabilities().get(0).getDescription());
		assertEquals("Weird", issues.get(0).getVulnerabilities().get(1).getDescription());
	}

	@Test
	@DisplayName("Multiple images produce multiple scan issues")
	void multipleImagesProduceMultipleIssues() {
		ContainersRealtimeImage image1 = mockImage("nginx", "1.0", "High", Collections.emptyList(),
				Collections.emptyList());
		ContainersRealtimeImage image2 = mockImage("redis", "2.0", "Low", Collections.emptyList(),
				Collections.emptyList());

		ContainerScanResultAdaptor adaptor = new ContainerScanResultAdaptor(mockResults(List.of(image1, image2)),
				"docker-compose", "/repo/docker-compose.yml");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(2, issues.size());
	}
}
