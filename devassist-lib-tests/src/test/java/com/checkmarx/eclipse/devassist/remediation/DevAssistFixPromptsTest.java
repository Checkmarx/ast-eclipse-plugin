package com.checkmarx.eclipse.devassist.remediation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;

/**
 * Unit tests for {@link DevAssistFixPrompts}. These are pure static
 * string-builder methods with no Eclipse workspace dependency, so assertions
 * focus on the key inputs being interpolated into the generated prompt.
 */
class DevAssistFixPromptsTest {

	@Test
	@DisplayName("Cannot be instantiated")
	void constructorThrows() throws Exception {
		var ctor = DevAssistFixPrompts.class.getDeclaredConstructor();
		ctor.setAccessible(true);
		Exception ex = assertThrows(java.lang.reflect.InvocationTargetException.class, ctor::newInstance);
		assertTrue(ex.getCause() instanceof IllegalStateException);
	}

	@Test
	@DisplayName("buildSCARemediationPrompt includes package name, version, manager and severity")
	void buildSCARemediationPromptIncludesInputs() {
		String prompt = DevAssistFixPrompts.buildSCARemediationPrompt("lodash", "3.10.1", "npm", "High");
		assertTrue(prompt.contains(DevAssistUtils.getAgentName()));
		assertTrue(prompt.contains("lodash"));
		assertTrue(prompt.contains("3.10.1"));
		assertTrue(prompt.contains("npm"));
		assertTrue(prompt.contains("High"));
	}

	@Test
	@DisplayName("buildSecretRemediationPrompt includes title, description and severity, tolerating nulls")
	void buildSecretRemediationPromptIncludesInputs() {
		String prompt = DevAssistFixPrompts.buildSecretRemediationPrompt("AWS Key", "leaked key", "Critical");
		assertTrue(prompt.contains("AWS Key"));
		assertTrue(prompt.contains("leaked key"));
		assertTrue(prompt.contains("Critical"));

		// Null description/severity must not throw - they're rendered as empty text
		String promptWithNulls = DevAssistFixPrompts.buildSecretRemediationPrompt("AWS Key", null, null);
		assertTrue(promptWithNulls.contains("AWS Key"));
	}

	@Test
	@DisplayName("buildContainersRemediationPrompt includes file type, image name/tag and severity")
	void buildContainersRemediationPromptIncludesInputs() {
		String prompt = DevAssistFixPrompts.buildContainersRemediationPrompt("dockerfile", "nginx", "latest", "High");
		assertTrue(prompt.contains("dockerfile"));
		assertTrue(prompt.contains("nginx"));
		assertTrue(prompt.contains("latest"));
		assertTrue(prompt.contains("High"));
	}

	@Test
	@DisplayName("buildIACRemediationPrompt converts a zero-based line number to one-based in the prompt")
	void buildIACRemediationPromptConvertsLineNumber() {
		String prompt = DevAssistFixPrompts.buildIACRemediationPrompt("Open Security Group", "desc", "High",
				"Terraform", "false", "true", 9);
		assertTrue(prompt.contains("Open Security Group"));
		assertTrue(prompt.contains("Terraform"));
		assertTrue(prompt.contains("Problematic Line Number:** 10"), "Line number should be incremented by 1");
	}

	@Test
	@DisplayName("buildIACRemediationPrompt tolerates a null problematic line number")
	void buildIACRemediationPromptHandlesNullLineNumber() {
		String prompt = DevAssistFixPrompts.buildIACRemediationPrompt("Rule", "desc", "Low", "YAML", "a", "b", null);
		assertFalse(prompt.contains("Problematic Line Number:**"));
		assertTrue(prompt.contains("[unknown]"));
	}

	@Test
	@DisplayName("buildASCARemediationPrompt converts a zero-based line number to one-based in the prompt")
	void buildASCARemediationPromptConvertsLineNumber() {
		String prompt = DevAssistFixPrompts.buildASCARemediationPrompt("SQL Injection", "desc", "Critical",
				"use parameterized queries", 41);
		assertTrue(prompt.contains("SQL Injection"));
		assertTrue(prompt.contains("use parameterized queries"));
		assertTrue(prompt.contains("Problematic Line Number:** 42"));
	}

	@Test
	@DisplayName("buildASCARemediationPrompt tolerates a null problematic line number")
	void buildASCARemediationPromptHandlesNullLineNumber() {
		String prompt = DevAssistFixPrompts.buildASCARemediationPrompt("Rule", "desc", "Medium", "fix advise", null);
		assertTrue(prompt.contains("[problematic line number]"));
	}
}
