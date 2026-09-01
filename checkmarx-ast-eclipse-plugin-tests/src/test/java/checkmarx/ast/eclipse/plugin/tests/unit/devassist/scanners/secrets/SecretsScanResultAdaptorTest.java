package checkmarx.ast.eclipse.plugin.tests.unit.devassist.scanners.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.ast.secretsrealtime.SecretsRealtimeResults;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;

/**
 * Unit tests for {@link SecretsScanResultAdaptor}. Pure logic tests - no
 * Eclipse workspace/resource dependency required.
 */
class SecretsScanResultAdaptorTest {

	private SecretsRealtimeResults mockResults(List<SecretsRealtimeResults.Secret> secrets) {
		SecretsRealtimeResults results = mock(SecretsRealtimeResults.class);
		when(results.getSecrets()).thenReturn(secrets);
		return results;
	}

	private RealtimeLocation mockLocation(int line, int start, int end) {
		RealtimeLocation location = mock(RealtimeLocation.class);
		when(location.getLine()).thenReturn(line);
		when(location.getStartIndex()).thenReturn(start);
		when(location.getEndIndex()).thenReturn(end);
		return location;
	}

	private SecretsRealtimeResults.Secret mockSecret(String title, String severity, String description,
			String secretValue, List<RealtimeLocation> locations) {
		SecretsRealtimeResults.Secret secret = mock(SecretsRealtimeResults.Secret.class);
		when(secret.getTitle()).thenReturn(title);
		when(secret.getSeverity()).thenReturn(severity);
		when(secret.getDescription()).thenReturn(description);
		when(secret.getSecretValue()).thenReturn(secretValue);
		when(secret.getLocations()).thenReturn(locations);
		return secret;
	}

	@Test
	@DisplayName("getResults returns original results reference")
	void getResultsReturnsOriginal() {
		SecretsRealtimeResults results = mockResults(Collections.emptyList());
		SecretsScanResultAdaptor adaptor = new SecretsScanResultAdaptor(results, "/repo/config.properties");
		assertSame(results, adaptor.getResults());
	}

	@Test
	@DisplayName("getIssues returns empty list when results or secrets are null/empty")
	void getIssuesHandlesNullOrEmpty() {
		assertTrue(new SecretsScanResultAdaptor(null, "/repo/config.properties").getIssues().isEmpty());
		assertTrue(new SecretsScanResultAdaptor(mockResults(null), "/repo/config.properties").getIssues().isEmpty());
		assertTrue(new SecretsScanResultAdaptor(mockResults(Collections.emptyList()), "/repo/config.properties")
				.getIssues().isEmpty());
	}

	@Test
	@DisplayName("Single secret with location converts to a ScanIssue")
	void getIssuesConvertsSingleSecret() {
		RealtimeLocation location = mockLocation(11, 4, 40);
		SecretsRealtimeResults.Secret secret = mockSecret("AWS Access Key", "High", "desc", "AKIA...",
				List.of(location));

		SecretsScanResultAdaptor adaptor = new SecretsScanResultAdaptor(mockResults(List.of(secret)),
				"/repo/config.properties");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		ScanIssue issue = issues.get(0);
		assertEquals("AWS Access Key", issue.getTitle());
		assertEquals("High", issue.getSeverity());
		assertEquals("desc", issue.getDescription());
		assertEquals("AKIA...", issue.getSecretValue());
		assertEquals(ScanEngine.SECRETS, issue.getScanEngine());
		assertEquals("/repo/config.properties", issue.getFilePath());
		assertEquals(1, issue.getLocations().size());
		// Location line is zero-based upstream and incremented by 1 in the adaptor
		assertEquals(12, issue.getLocations().get(0).getLine());
		assertEquals(1, issue.getVulnerabilities().size());
		assertEquals("AWS Access Key", issue.getVulnerabilities().get(0).getTitle());
	}

	@Test
	@DisplayName("Secret without locations gets a fallback location at line 1")
	void getIssuesFallsBackToDefaultLocationWhenNoneProvided() {
		SecretsRealtimeResults.Secret secret = mockSecret("Generic Secret", "Medium", "desc", "value",
				Collections.emptyList());

		SecretsScanResultAdaptor adaptor = new SecretsScanResultAdaptor(mockResults(List.of(secret)),
				"/repo/config.properties");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(1, issues.size());
		assertEquals(1, issues.get(0).getLocations().size());
		assertEquals(1, issues.get(0).getLocations().get(0).getLine());
		assertEquals(0, issues.get(0).getLocations().get(0).getStartIndex());
		assertEquals(100, issues.get(0).getLocations().get(0).getEndIndex());
	}

	@Test
	@DisplayName("Multiple secrets each convert into their own ScanIssue")
	void getIssuesConvertsMultipleSecrets() {
		SecretsRealtimeResults.Secret secret1 = mockSecret("Secret1", "High", "d1", "v1", Collections.emptyList());
		SecretsRealtimeResults.Secret secret2 = mockSecret("Secret2", "Low", "d2", "v2", Collections.emptyList());

		SecretsScanResultAdaptor adaptor = new SecretsScanResultAdaptor(mockResults(List.of(secret1, secret2)),
				"/repo/config.properties");

		List<ScanIssue> issues = adaptor.getIssues();
		assertEquals(2, issues.size());
	}
}
