package checkmarx.ast.eclipse.plugin.tests.unit.devassist.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;

/**
 * Unit tests for {@link DevAssistUtils}'s pure-logic helpers. Deliberately
 * excludes {@code isDarkTheme}/{@code themeBasedPNGIconForHtmlImage} and
 * {@code getLiveDocumentForFile}/{@code copyToClipboard} - see
 * {@code ProblemDecoratorTest} for why a bare call into the SWT
 * {@code Display} (e.g. {@code getSystemColor}) from the test thread throws
 * {@code SWTException: Invalid thread access} under Tycho's {@code -nouithread}
 * surefire runtime, and why {@code Display.syncExec} is not a safe workaround
 * (it deadlocks in this harness).
 */
class DevAssistUtilsTest {

	@Test
	@DisplayName("generateUniqueId is deterministic for the same inputs and differs when any input changes")
	void generateUniqueIdIsDeterministic() {
		String id1 = DevAssistUtils.generateUniqueId(10, "ruleA", "Main.java");
		String id2 = DevAssistUtils.generateUniqueId(10, "ruleA", "Main.java");
		String id3 = DevAssistUtils.generateUniqueId(11, "ruleA", "Main.java");

		assertEquals(id1, id2);
		assertFalse(id1.equals(id3));
	}

	@Test
	@DisplayName("encodeBase64/decodeBase64 round-trip and handle null/empty input")
	void encodeDecodeBase64RoundTrip() {
		assertEquals("", DevAssistUtils.encodeBase64(null));
		assertEquals("", DevAssistUtils.encodeBase64(""));
		assertEquals("", DevAssistUtils.decodeBase64(null));
		assertEquals("", DevAssistUtils.decodeBase64(""));

		String encoded = DevAssistUtils.encodeBase64("hello world");
		assertEquals("hello world", DevAssistUtils.decodeBase64(encoded));
	}

	@Test
	@DisplayName("normalizeSeverity maps every known severity to its SeverityLevel display form")
	void normalizeSeverityMapsKnownValues() {
		assertEquals("Malicious", DevAssistUtils.normalizeSeverity("malicious"));
		assertEquals("Critical", DevAssistUtils.normalizeSeverity("CRITICAL"));
		assertEquals("High", DevAssistUtils.normalizeSeverity("High"));
		assertEquals("Medium", DevAssistUtils.normalizeSeverity("medium"));
		assertEquals("Low", DevAssistUtils.normalizeSeverity("low"));
		assertEquals("Unknown", DevAssistUtils.normalizeSeverity("unknown"));
		assertEquals("OK", DevAssistUtils.normalizeSeverity("ok"));
		assertEquals("Ignored", DevAssistUtils.normalizeSeverity("ignored"));
	}

	@Test
	@DisplayName("normalizeSeverity returns 'Unknown' for null/empty and passes through unrecognized values as-is")
	void normalizeSeverityHandlesEdgeCases() {
		assertEquals("Unknown", DevAssistUtils.normalizeSeverity(null));
		assertEquals("Unknown", DevAssistUtils.normalizeSeverity(""));
		assertEquals("Weird", DevAssistUtils.normalizeSeverity("Weird"));
	}

	@Test
	@DisplayName("isProblem is false for OK/Unknown/Ignored (case-insensitive) and true for everything else")
	void isProblemDistinguishesNonFindingSeverities() {
		assertFalse(DevAssistUtils.isProblem("OK"));
		assertFalse(DevAssistUtils.isProblem("unknown"));
		assertFalse(DevAssistUtils.isProblem("Ignored"));
		assertFalse(DevAssistUtils.isProblem(null));
		assertTrue(DevAssistUtils.isProblem("High"));
		assertTrue(DevAssistUtils.isProblem("Critical"));
	}

	@Test
	@DisplayName("isDockerComposeFile and isDockerFile match case-insensitively on the file name")
	void dockerFileDetectionIsCaseInsensitive() {
		assertTrue(DevAssistUtils.isDockerComposeFile("/repo/Docker-Compose.YML"));
		assertFalse(DevAssistUtils.isDockerComposeFile("/repo/Dockerfile"));
		assertTrue(DevAssistUtils.isDockerFile("/repo/DOCKERFILE.dev"));
		assertFalse(DevAssistUtils.isDockerFile("/repo/docker-compose.yml"));
	}

	@Test
	@DisplayName("isYamlFile accepts yml/yaml extensions and rejects everything else, including null/blank")
	void isYamlFileChecksExtension() {
		assertTrue(DevAssistUtils.isYamlFile("/repo/values.yaml"));
		assertTrue(DevAssistUtils.isYamlFile("/repo/values.yml"));
		assertFalse(DevAssistUtils.isYamlFile("/repo/values.json"));
		assertFalse(DevAssistUtils.isYamlFile(null));
		assertFalse(DevAssistUtils.isYamlFile(""));
	}

	@Test
	@DisplayName("getFileExtension extracts the lower-cased extension after the last separator")
	void getFileExtensionExtractsExtension() {
		assertEquals("java", DevAssistUtils.getFileExtension("/repo/src/Main.JAVA"));
		assertEquals("yml", DevAssistUtils.getFileExtension("C:\\repo\\docker-compose.yml"));
	}

	@Test
	@DisplayName("getFileExtension returns null when there is no extension, a trailing dot, or a dot before the last separator")
	void getFileExtensionHandlesNoExtensionCases() {
		assertNull(DevAssistUtils.getFileExtension(null));
		assertNull(DevAssistUtils.getFileExtension(""));
		assertNull(DevAssistUtils.getFileExtension("/repo/README"));
		assertNull(DevAssistUtils.getFileExtension("/repo/file."));
		assertNull(DevAssistUtils.getFileExtension("/repo.dir/README"));
	}

	@Test
	@DisplayName("getAgentName and getAssistQuickFixName return their fixed constant values")
	void agentNameAndQuickFixNameAreFixedConstants() {
		assertEquals(DevAssistConstants.CX_AGENT_NAME, DevAssistUtils.getAgentName());
		assertEquals(DevAssistConstants.FIX_WITH_DEV_ASSIST, DevAssistUtils.getAssistQuickFixName());
	}

	private ScanIssue issueWithVulnerabilities(Vulnerability... vulnerabilities) {
		ScanIssue issue = new ScanIssue();
		issue.setScanEngine(ScanEngine.OSS);
		for (Vulnerability v : vulnerabilities) {
			issue.getVulnerabilities().add(v);
		}
		return issue;
	}

	@Test
	@DisplayName("getVulnerabilityDetails finds a vulnerability by id among several")
	void getVulnerabilityDetailsFindsMatchingId() {
		Vulnerability v1 = new Vulnerability();
		v1.setVulnerabilityId("id-1");
		Vulnerability v2 = new Vulnerability();
		v2.setVulnerabilityId("id-2");
		ScanIssue issue = issueWithVulnerabilities(v1, v2);

		assertEquals(v2, DevAssistUtils.getVulnerabilityDetails(issue, "id-2"));
	}

	@Test
	@DisplayName("getVulnerabilityDetails returns null when no vulnerability matches or the list is empty")
	void getVulnerabilityDetailsReturnsNullWhenNotFound() {
		Vulnerability v1 = new Vulnerability();
		v1.setVulnerabilityId("id-1");

		assertNull(DevAssistUtils.getVulnerabilityDetails(issueWithVulnerabilities(v1), "does-not-exist"));
		assertNull(DevAssistUtils.getVulnerabilityDetails(issueWithVulnerabilities(), "id-1"));
	}

	@Test
	@DisplayName("getIgnoreFilePath returns empty string for a null project")
	void getIgnoreFilePathHandlesNullProject() {
		assertEquals("", DevAssistUtils.getIgnoreFilePath(null));
	}

	@Nested
	class GetIgnoreFilePathWithRealProject {

		private Path tempDir;
		private IProject project;

		@BeforeEach
		void setUp() throws IOException {
			tempDir = Files.createTempDirectory("dev-assist-utils-test");
			project = mock(IProject.class);
			IPath ipath = org.eclipse.core.runtime.Path.fromOSString(tempDir.toAbsolutePath().toString());
			when(project.getLocation()).thenReturn(ipath);
		}

		@AfterEach
		void tearDown() throws IOException {
			com.checkmarx.eclipse.devassist.ignore.IgnoreFileManager.dispose(project);
			if (tempDir == null || !Files.exists(tempDir)) {
				return;
			}
			try (Stream<Path> walk = Files.walk(tempDir)) {
				walk.sorted(Comparator.reverseOrder()).forEach(p -> {
					try {
						Files.deleteIfExists(p);
					} catch (IOException ignored) {
						// best-effort cleanup
					}
				});
			}
		}

		@Test
		@DisplayName("getIgnoreFilePath resolves the project's temp ignore list path")
		void resolvesTempListPath() {
			String path = DevAssistUtils.getIgnoreFilePath(project);

			assertTrue(path.endsWith(".checkmarxIgnoredTempList.json"));
			assertTrue(Files.exists(Path.of(path)));
		}
	}

	@Nested
	class GetContainerTool {

		@AfterEach
		void resetPreference() {
			Preferences.STORE.setToDefault(Preferences.PREF_CONTAINERS_TOOL);
		}

		@Test
		@DisplayName("getContainerTool defaults to 'docker' when no preference has been set")
		void defaultsToDocker() {
			Preferences.STORE.setToDefault(Preferences.PREF_CONTAINERS_TOOL);

			assertEquals("docker", DevAssistUtils.getContainerTool());
		}

		@Test
		@DisplayName("getContainerTool returns the stored preference value when set")
		void returnsStoredPreference() {
			Preferences.STORE.setValue(Preferences.PREF_CONTAINERS_TOOL, "podman");

			assertEquals("podman", DevAssistUtils.getContainerTool());
		}
	}
}
