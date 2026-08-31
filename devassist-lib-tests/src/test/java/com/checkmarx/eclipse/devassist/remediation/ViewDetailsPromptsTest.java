package com.checkmarx.eclipse.devassist.remediation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.model.Vulnerability;

/**
 * Unit tests for {@link ViewDetailsPrompts}. Pure static string-builder
 * methods - no Eclipse workspace dependency.
 */
class ViewDetailsPromptsTest {

	@Test
	@DisplayName("Cannot be instantiated")
	void constructorThrows() throws Exception {
		var ctor = ViewDetailsPrompts.class.getDeclaredConstructor();
		ctor.setAccessible(true);
		Exception ex = assertThrows(java.lang.reflect.InvocationTargetException.class, ctor::newInstance);
		assertTrue(ex.getCause() instanceof IllegalStateException);
	}

	private Vulnerability vulnerability(String cve, String severity, String description) {
		Vulnerability v = new Vulnerability();
		v.setCve(cve);
		v.setSeverity(severity);
		v.setDescription(description);
		return v;
	}

	@Test
	@DisplayName("buildSCAExplanationPrompt lists each CVE when vulnerabilities are present")
	void buildSCAExplanationPromptListsVulnerabilities() {
		List<Vulnerability> vulns = List.of(vulnerability("CVE-2024-0001", "High", "desc1"),
				vulnerability("CVE-2024-0002", "Low", "desc2"));

		String prompt = ViewDetailsPrompts.buildSCAExplanationPrompt("lodash", "3.10.1", "High", vulns);

		assertTrue(prompt.contains("lodash"));
		assertTrue(prompt.contains("3.10.1"));
		assertTrue(prompt.contains("CVE-2024-0001"));
		assertTrue(prompt.contains("CVE-2024-0002"));
	}

	@Test
	@DisplayName("buildSCAExplanationPrompt notes missing CVEs when vulnerability list is empty")
	void buildSCAExplanationPromptHandlesEmptyVulnerabilities() {
		String prompt = ViewDetailsPrompts.buildSCAExplanationPrompt("axios", "0.1.0", "Low",
				Collections.emptyList());
		assertTrue(prompt.contains("No CVEs were provided"));
	}

	@Test
	@DisplayName("buildSCAExplanationPrompt uses the malicious-package branch when status is Malicious")
	void buildSCAExplanationPromptHandlesMaliciousStatus() {
		String prompt = ViewDetailsPrompts.buildSCAExplanationPrompt("evil-pkg", "1.0.0", "Malicious",
				Collections.emptyList());
		assertTrue(prompt.contains("Malicious Package Detected"));
		assertTrue(prompt.contains("evil-pkg") || prompt.contains("1.0.0"));
	}

	@Test
	@DisplayName("buildSecretsExplanationPrompt includes title, description and severity")
	void buildSecretsExplanationPromptIncludesInputs() {
		String prompt = ViewDetailsPrompts.buildSecretsExplanationPrompt("AWS Key", "leaked", "Critical");
		assertTrue(prompt.contains("AWS Key"));
		assertTrue(prompt.contains("leaked"));
		assertTrue(prompt.contains("Critical"));
	}

	@Test
	@DisplayName("buildContainersExplanationPrompt uses the malicious branch when severity is Malicious")
	void buildContainersExplanationPromptHandlesMaliciousSeverity() {
		String prompt = ViewDetailsPrompts.buildContainersExplanationPrompt("dockerfile", "nginx", "latest",
				"Malicious");
		assertTrue(prompt.contains("Malicious Container Image"));
		assertTrue(prompt.contains("Malicious Container Detected"));
	}

	@Test
	@DisplayName("buildContainersExplanationPrompt uses the vulnerability branch for non-malicious severity")
	void buildContainersExplanationPromptHandlesVulnerableSeverity() {
		String prompt = ViewDetailsPrompts.buildContainersExplanationPrompt("dockerfile", "nginx", "latest", "High");
		assertTrue(prompt.contains("Vulnerable Container Image"));
		assertTrue(prompt.contains("Container Vulnerabilities"));
	}

	@Test
	@DisplayName("buildIACExplanationPrompt includes title, file type, expected and actual values")
	void buildIACExplanationPromptIncludesInputs() {
		String prompt = ViewDetailsPrompts.buildIACExplanationPrompt("Open Security Group", "desc", "High",
				"Terraform", "false", "true");
		assertTrue(prompt.contains("Open Security Group"));
		assertTrue(prompt.contains("Terraform"));
		assertTrue(prompt.contains("false"));
		assertTrue(prompt.contains("true"));
	}

	@Test
	@DisplayName("buildASCAExplanationPrompt includes rule name, description and severity")
	void buildASCAExplanationPromptIncludesInputs() {
		String prompt = ViewDetailsPrompts.buildASCAExplanationPrompt("SQL Injection", "desc", "Critical");
		assertTrue(prompt.contains("SQL Injection"));
		assertTrue(prompt.contains("desc"));
		assertTrue(prompt.contains("Critical"));
	}
}
