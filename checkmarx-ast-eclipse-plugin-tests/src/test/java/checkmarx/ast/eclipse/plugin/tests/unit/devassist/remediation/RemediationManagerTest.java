package checkmarx.ast.eclipse.plugin.tests.unit.devassist.remediation;

import static com.checkmarx.eclipse.devassist.utils.DevAssistConstants.QUICK_FIX;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.remediation.CopilotIntegration;
import com.checkmarx.eclipse.devassist.remediation.RemediationManager;

/**
 * Unit tests for {@link RemediationManager}. {@link CopilotIntegration} is
 * statically mocked to always report success, so the AI-send path is
 * exercised without ever reaching the real clipboard/notification fallback
 * (which would touch the live SWT Display/clipboard).
 */
class RemediationManagerTest {

	private final RemediationManager manager = new RemediationManager();

	private ScanIssue ossIssue() {
		ScanIssue issue = new ScanIssue();
		issue.setScanEngine(ScanEngine.OSS);
		issue.setTitle("lodash");
		issue.setPackageVersion("3.10.1");
		issue.setPackageManager("npm");
		issue.setSeverity("High");
		issue.setFilePath("/repo/package.json");
		return issue;
	}

	private ScanIssue secretsIssue() {
		ScanIssue issue = new ScanIssue();
		issue.setScanEngine(ScanEngine.SECRETS);
		issue.setTitle("AWS Key");
		issue.setDescription("leaked key");
		issue.setSeverity("Critical");
		issue.setFilePath("/repo/config.properties");
		return issue;
	}

	private ScanIssue containersIssue() {
		ScanIssue issue = new ScanIssue();
		issue.setScanEngine(ScanEngine.CONTAINERS);
		issue.setTitle("nginx");
		issue.setImageTag("latest");
		issue.setFileType("dockerfile");
		issue.setSeverity("High");
		issue.setFilePath("/repo/Dockerfile");
		return issue;
	}

	private ScanIssue iacIssueWithVulnerability(String vulnerabilityId) {
		ScanIssue issue = new ScanIssue();
		issue.setScanEngine(ScanEngine.IAC);
		issue.setScanIssueId("issue-1");
		issue.setTitle("Open Security Group");
		issue.setDescription("desc");
		issue.setSeverity("High");
		issue.setFileType("Terraform");
		issue.setFilePath("/repo/main.tf");
		issue.setProblematicLineNumber(9);
		Vulnerability vulnerability = new Vulnerability();
		vulnerability.setVulnerabilityId(vulnerabilityId);
		vulnerability.setTitle("Open Security Group");
		vulnerability.setDescription("desc");
		vulnerability.setSeverity("High");
		vulnerability.setExpectedValue("false");
		vulnerability.setActualValue("true");
		issue.getVulnerabilities().add(vulnerability);
		return issue;
	}

	private ScanIssue ascaIssueWithVulnerability(String vulnerabilityId) {
		ScanIssue issue = new ScanIssue();
		issue.setScanEngine(ScanEngine.ASCA);
		issue.setScanIssueId("issue-2");
		issue.setTitle("SQL Injection");
		issue.setDescription("desc");
		issue.setSeverity("Critical");
		issue.setRemediationAdvise("use parameterized queries");
		issue.setFilePath("/repo/Main.java");
		issue.setProblematicLineNumber(41);
		Vulnerability vulnerability = new Vulnerability();
		vulnerability.setVulnerabilityId(vulnerabilityId);
		vulnerability.setTitle("SQL Injection");
		vulnerability.setDescription("desc");
		vulnerability.setSeverity("Critical");
		vulnerability.setRemediationAdvise("use parameterized queries");
		issue.getVulnerabilities().add(vulnerability);
		return issue;
	}

	@Test
	@DisplayName("fixWithCxOneAssist sends the SCA remediation prompt to Copilot in Agent mode for OSS issues")
	void fixWithCxOneAssistSendsOssPromptToCopilot() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class)) {
			copilot.when(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Agent"))).thenReturn(true);

			manager.fixWithCxOneAssist(ossIssue(), QUICK_FIX);

			ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
			copilot.verify(() -> CopilotIntegration.sendPromptToCopilot(promptCaptor.capture(), eq("Agent")));
			assertTrue(promptCaptor.getValue().contains("lodash"));
			assertTrue(promptCaptor.getValue().contains("3.10.1"));
		}
	}

	@Test
	@DisplayName("fixWithCxOneAssist sends the secret remediation prompt for SECRETS issues")
	void fixWithCxOneAssistSendsSecretsPromptToCopilot() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class)) {
			copilot.when(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Agent"))).thenReturn(true);

			manager.fixWithCxOneAssist(secretsIssue(), QUICK_FIX);

			ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
			copilot.verify(() -> CopilotIntegration.sendPromptToCopilot(promptCaptor.capture(), eq("Agent")));
			assertTrue(promptCaptor.getValue().contains("AWS Key"));
		}
	}

	@Test
	@DisplayName("fixWithCxOneAssist sends the container remediation prompt for CONTAINERS issues")
	void fixWithCxOneAssistSendsContainersPromptToCopilot() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class)) {
			copilot.when(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Agent"))).thenReturn(true);

			manager.fixWithCxOneAssist(containersIssue(), QUICK_FIX);

			ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
			copilot.verify(() -> CopilotIntegration.sendPromptToCopilot(promptCaptor.capture(), eq("Agent")));
			assertTrue(promptCaptor.getValue().contains("nginx"));
			assertTrue(promptCaptor.getValue().contains("latest"));
		}
	}

	@Test
	@DisplayName("fixWithCxOneAssist sends the IAC remediation prompt when the vulnerability resolves")
	void fixWithCxOneAssistSendsIacPromptToCopilot() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class)) {
			copilot.when(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Agent"))).thenReturn(true);

			manager.fixWithCxOneAssist(iacIssueWithVulnerability("issue-1"), QUICK_FIX);

			ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
			copilot.verify(() -> CopilotIntegration.sendPromptToCopilot(promptCaptor.capture(), eq("Agent")));
			assertTrue(promptCaptor.getValue().contains("Open Security Group"));
		}
	}

	@Test
	@DisplayName("fixWithCxOneAssist does nothing for IAC when actionId is blank")
	void fixWithCxOneAssistSkipsIacWhenActionIdBlank() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class)) {
			manager.fixWithCxOneAssist(iacIssueWithVulnerability("issue-1"), "");
			copilot.verifyNoInteractions();
		}
	}

	@Test
	@DisplayName("fixWithCxOneAssist does nothing for IAC when the vulnerability cannot be resolved")
	void fixWithCxOneAssistSkipsIacWhenVulnerabilityMissing() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class)) {
			manager.fixWithCxOneAssist(iacIssueWithVulnerability("other-id"), QUICK_FIX);
			copilot.verifyNoInteractions();
		}
	}

	@Test
	@DisplayName("fixWithCxOneAssist sends the ASCA remediation prompt when the vulnerability resolves")
	void fixWithCxOneAssistSendsAscaPromptToCopilot() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class)) {
			copilot.when(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Agent"))).thenReturn(true);

			manager.fixWithCxOneAssist(ascaIssueWithVulnerability("issue-2"), QUICK_FIX);

			ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
			copilot.verify(() -> CopilotIntegration.sendPromptToCopilot(promptCaptor.capture(), eq("Agent")));
			assertTrue(promptCaptor.getValue().contains("SQL Injection"));
		}
	}

	@Test
	@DisplayName("viewDetails sends the SCA explanation prompt to Copilot in Ask mode")
	void viewDetailsSendsOssPromptToCopilotInAskMode() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class)) {
			copilot.when(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Ask"))).thenReturn(true);

			manager.viewDetails(ossIssue(), QUICK_FIX);

			copilot.verify(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Ask")), times(1));
		}
	}

	@Test
	@DisplayName("viewDetails does nothing for ASCA when actionId is blank")
	void viewDetailsSkipsAscaWhenActionIdBlank() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class)) {
			manager.viewDetails(ascaIssueWithVulnerability("issue-2"), null);
			copilot.verifyNoInteractions();
		}
	}

	@Test
	@DisplayName("viewDetails sends the ASCA explanation prompt when the vulnerability resolves")
	void viewDetailsSendsAscaPromptToCopilot() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class)) {
			copilot.when(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Ask"))).thenReturn(true);

			manager.viewDetails(ascaIssueWithVulnerability("issue-2"), QUICK_FIX);

			ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
			copilot.verify(() -> CopilotIntegration.sendPromptToCopilot(promptCaptor.capture(), eq("Ask")));
			assertTrue(promptCaptor.getValue().contains("SQL Injection"));
		}
	}
}
