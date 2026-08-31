package com.checkmarx.eclipse.devassist.remediation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.telemetry.TelemetryService;

/**
 * Unit tests for {@link RemediationLinkHandler}. The IGNORE_THIS_TYPE /
 * IGNORE_ALL_OF_THIS_TYPE actions resolve the active project via
 * {@code ResourcesPlugin.getWorkspace().getRoot().getProjects()}; the test
 * workspace has no open projects, so those actions exercise the
 * "no active project" failure path without needing any workspace fixture.
 */
class RemediationLinkHandlerTest {

	private final RemediationLinkHandler handler = new RemediationLinkHandler();

	private ScanIssue ossIssue() {
		ScanIssue issue = new ScanIssue();
		issue.setScanEngine(ScanEngine.OSS);
		issue.setScanIssueId("issue-1");
		issue.setTitle("lodash");
		issue.setPackageVersion("3.10.1");
		issue.setPackageManager("npm");
		issue.setSeverity("High");
		issue.setFilePath("/repo/package.json");
		return issue;
	}

	@Test
	@DisplayName("handleLink(link, issue) returns false when link has no separator")
	void handleLinkRejectsLinkWithoutSeparator() {
		assertFalse(handler.handleLink("copyfixprompt", ossIssue()));
	}

	@Test
	@DisplayName("handleLink(link, issue) returns false when issue id segment is missing")
	void handleLinkRejectsMissingIssueId() {
		assertFalse(handler.handleLink("copyfixprompt:", ossIssue()));
	}

	@Test
	@DisplayName("handleLink(link, issue) returns false for an unsupported action")
	void handleLinkRejectsUnsupportedAction() {
		assertFalse(handler.handleLink("notarealaction:issue-1:OSS", ossIssue()));
	}

	@Test
	@DisplayName("handleLink(link) without issue context returns false when link has no separator")
	void handleLinkWithoutIssueRejectsLinkWithoutSeparator() {
		assertFalse(handler.handleLink("copyfixprompt"));
	}

	@Test
	@DisplayName("handleLink(link) without issue context returns false when engine name segment is missing")
	void handleLinkWithoutIssueRejectsMissingEngineName() {
		assertFalse(handler.handleLink("copyfixprompt:issue-1"));
	}

	@Test
	@DisplayName("FIX action delegates to RemediationManager and logs telemetry")
	void handleLinkDelegatesFixAction() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class);
				MockedStatic<TelemetryService> telemetry = mockStatic(TelemetryService.class)) {
			copilot.when(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Agent"))).thenReturn(true);

			boolean handled = handler.handleLink("copyfixprompt:issue-1:OSS", ossIssue());

			assertTrue(handled);
			copilot.verify(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Agent")));
			telemetry.verify(() -> TelemetryService.logFixWithCxOneAssistAction(any(ScanIssue.class)));
		}
	}

	@Test
	@DisplayName("VIEW_DETAILS action delegates to RemediationManager and logs telemetry")
	void handleLinkDelegatesViewDetailsAction() {
		try (MockedStatic<CopilotIntegration> copilot = mockStatic(CopilotIntegration.class);
				MockedStatic<TelemetryService> telemetry = mockStatic(TelemetryService.class)) {
			copilot.when(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Ask"))).thenReturn(true);

			boolean handled = handler.handleLink("viewdetails:issue-1:OSS", ossIssue());

			assertTrue(handled);
			copilot.verify(() -> CopilotIntegration.sendPromptToCopilot(anyString(), eq("Ask")));
			telemetry.verify(() -> TelemetryService.logViewDetailsAction(any(ScanIssue.class)));
		}
	}

	@Test
	@DisplayName("IGNORE_THIS_TYPE fails gracefully when no project is open in the workspace")
	void handleLinkIgnoreThisTypeFailsWithoutActiveProject() {
		assertFalse(handler.handleLink("ignorethis:issue-1:OSS", ossIssue()));
	}

	@Test
	@DisplayName("IGNORE_ALL_OF_THIS_TYPE fails gracefully when no project is open in the workspace")
	void handleLinkIgnoreAllOfThisTypeFailsWithoutActiveProject() {
		assertFalse(handler.handleLink("ignoreallofthis:issue-1:OSS", ossIssue()));
	}
}
