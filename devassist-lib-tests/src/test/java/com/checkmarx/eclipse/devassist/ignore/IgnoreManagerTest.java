package com.checkmarx.eclipse.devassist.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.devassist.utils.ScanEngine;

/**
 * Unit tests for {@link IgnoreManager}. Uses a mocked {@link IProject} backed
 * by a manually managed temp directory (see {@link IgnoreFileManagerTest} for
 * why {@code @TempDir} isn't used) so the wrapped {@link IgnoreFileManager}'s
 * real (but isolated) file I/O runs without touching the actual workspace.
 * <p>
 * Operations that would trigger a rescan ({@code addIgnoredEntry},
 * {@code addAllIgnoredEntry}) resolve the target file via
 * {@code ResourcesPlugin.getWorkspace()...getFileForLocation(...)}, which
 * returns null for these synthetic test paths (they were never added to the
 * real Eclipse workspace) - so the rescan branch safely no-ops instead of
 * scheduling a real {@code RealTimeScanJob}.
 */
class IgnoreManagerTest {

	private Path tempDir;
	private IProject project;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("ignore-manager-test");
		project = mock(IProject.class);
		IPath ipath = org.eclipse.core.runtime.Path.fromOSString(tempDir.toAbsolutePath().toString());
		when(project.getLocation()).thenReturn(ipath);
	}

	@AfterEach
	void tearDown() throws IOException {
		IgnoreManager.dispose(project);
		IgnoreFileManager.dispose(project);
		if (tempDir == null || !Files.exists(tempDir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(tempDir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// best-effort cleanup
				}
			});
		}
	}

	private ScanIssue ossIssue(String path) {
		ScanIssue issue = new ScanIssue();
		issue.setScanEngine(com.checkmarx.eclipse.devassist.model.ScanEngine.OSS);
		issue.setScanIssueId("issue-oss-1");
		issue.setTitle("lodash");
		issue.setPackageManager("npm");
		issue.setPackageVersion("1.0.0");
		issue.setFilePath(path);
		Location location = new Location();
		location.setLine(3);
		issue.getLocations().add(location);
		return issue;
	}

	private ScanIssue ascaIssueWithVulnerability(String path, int line, String ruleName, String problematicLine) {
		ScanIssue issue = new ScanIssue();
		issue.setScanEngine(com.checkmarx.eclipse.devassist.model.ScanEngine.ASCA);
		issue.setScanIssueId("issue-asca-1");
		issue.setTitle(ruleName);
		issue.setFilePath(path);
		Location location = new Location();
		location.setLine(line);
		issue.getLocations().add(location);
		Vulnerability vulnerability = new Vulnerability();
		vulnerability.setVulnerabilityId("issue-asca-1");
		vulnerability.setTitle(ruleName);
		vulnerability.setRuleId(1);
		vulnerability.setProblematicLine(problematicLine);
		issue.getVulnerabilities().add(vulnerability);
		return issue;
	}

	@Test
	@DisplayName("createJsonKeyForIgnoreEntry builds the OSS composite key from manager/title/version")
	void createJsonKeyForOss() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		String filePath = tempDir.resolve("package.json").toString();
		String key = manager.createJsonKeyForIgnoreEntry(ossIssue(filePath), "");
		assertEquals("OSS:npm:lodash:1.0.0", key);
	}

	@Test
	@DisplayName("createJsonKeyForIgnoreEntry returns empty string for a null issue or missing scan engine")
	void createJsonKeyHandlesNullInputs() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		assertEquals("", manager.createJsonKeyForIgnoreEntry(null, ""));
		assertEquals("", manager.createJsonKeyForIgnoreEntry(new ScanIssue(), ""));
	}

	@Test
	@DisplayName("createJsonKeyForIgnoreEntry resolves the ASCA key via the matching vulnerability's rule id")
	void createJsonKeyForAsca() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		String filePath = tempDir.resolve("Main.java").toString();
		ScanIssue issue = ascaIssueWithVulnerability(filePath, 10, "SQLInjection", "eval(x)");

		String key = manager.createJsonKeyForIgnoreEntry(issue, DevAssistConstants.QUICK_FIX);

		assertEquals("ASCA:SQLInjection:1:Main.java", key);
	}

	@Test
	@DisplayName("hasIgnoredEntries reflects whether any ignore entry exists for the given engine")
	void hasIgnoredEntriesReflectsEngineType() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		assertFalse(manager.hasIgnoredEntries(ScanEngine.OSS));

		String filePath = tempDir.resolve("package.json").toString();
		manager.addIgnoredEntry(ossIssue(filePath), DevAssistConstants.QUICK_FIX);

		assertTrue(manager.hasIgnoredEntries(ScanEngine.OSS));
		assertFalse(manager.hasIgnoredEntries(ScanEngine.SECRETS));
	}

	@Test
	@DisplayName("addIgnoredEntry followed by isIgnored reports the issue as ignored for its file")
	void addIgnoredEntryThenIsIgnored() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		String filePath = tempDir.resolve("package.json").toString();
		ScanIssue issue = ossIssue(filePath);

		manager.addIgnoredEntry(issue, DevAssistConstants.QUICK_FIX);

		assertTrue(manager.isIgnored(issue));
	}

	@Test
	@DisplayName("isIgnored returns false for an issue that was never ignored")
	void isIgnoredReturnsFalseForUnknownIssue() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		String filePath = tempDir.resolve("package.json").toString();
		assertFalse(manager.isIgnored(ossIssue(filePath)));
	}

	@Test
	@DisplayName("isIgnored always returns false for ASCA issues (filtering happens upstream in the adaptor)")
	void isIgnoredAlwaysFalseForAsca() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		String filePath = tempDir.resolve("Main.java").toString();
		ScanIssue issue = ascaIssueWithVulnerability(filePath, 10, "SQLInjection", "eval(x)");

		manager.addIgnoredEntry(issue, DevAssistConstants.QUICK_FIX);

		assertFalse(manager.isIgnored(issue));
	}

	@Test
	@DisplayName("isIgnored returns false when null is passed")
	void isIgnoredHandlesNull() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		assertFalse(manager.isIgnored(null));
	}

	@Test
	@DisplayName("addAllIgnoredEntry covers the clicked occurrence even when the problem holder has no matches")
	void addAllIgnoredEntryCoversClickedOccurrenceWhenHolderEmpty() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		String filePath = tempDir.resolve("package.json").toString();
		ScanIssue issue = ossIssue(filePath);

		manager.addAllIgnoredEntry(issue, DevAssistConstants.QUICK_FIX);

		assertTrue(manager.isIgnored(issue));
	}

	@Test
	@DisplayName("isAscaVulnerabilityIgnored matches by rule name, file path and problematic line")
	void isAscaVulnerabilityIgnoredMatchesByRuleNameAndProblematicLine() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		String filePath = tempDir.resolve("Main.java").toString();

		IgnoreEntry entry = new IgnoreEntry();
		entry.type = ScanEngine.ASCA;
		entry.packageName = "SQLInjection";
		IgnoreEntry.FileReference ref = new IgnoreEntry.FileReference("Main.java", true, 10, "eval(x)");
		entry.files.add(ref);

		Vulnerability matching = new Vulnerability();
		matching.setTitle("SQLInjection");
		matching.setProblematicLine("eval(x)");

		Vulnerability differentLine = new Vulnerability();
		differentLine.setTitle("SQLInjection");
		differentLine.setProblematicLine("execute(y)");

		Vulnerability differentRule = new Vulnerability();
		differentRule.setTitle("XSS");
		differentRule.setProblematicLine("eval(x)");

		assertTrue(manager.isAscaVulnerabilityIgnored(matching, List.of(entry), filePath));
		assertFalse(manager.isAscaVulnerabilityIgnored(differentLine, List.of(entry), filePath));
		assertFalse(manager.isAscaVulnerabilityIgnored(differentRule, List.of(entry), filePath));
	}

	@Test
	@DisplayName("isAscaVulnerabilityIgnored returns false for a null vulnerability or null entries")
	void isAscaVulnerabilityIgnoredHandlesNullInputs() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		assertFalse(manager.isAscaVulnerabilityIgnored(null, List.of(), "path"));
		assertFalse(manager.isAscaVulnerabilityIgnored(new Vulnerability(), null, "path"));
	}

	@Test
	@DisplayName("removeIgnoreEntriesForFileIfEmpty removes ASCA entries whose only file reference matches")
	void removeIgnoreEntriesForFileIfEmptyRemovesMatchingAscaEntry() {
		IgnoreManager manager = IgnoreManager.getInstance(project);
		String filePath = tempDir.resolve("Main.java").toString();
		ScanIssue issue = ascaIssueWithVulnerability(filePath, 10, "SQLInjection", "eval(x)");
		manager.addIgnoredEntry(issue, DevAssistConstants.QUICK_FIX);
		assertTrue(manager.hasIgnoredEntries(ScanEngine.ASCA));

		manager.removeIgnoreEntriesForFileIfEmpty(filePath);

		assertFalse(manager.hasIgnoredEntries(ScanEngine.ASCA));
	}

	@Test
	@DisplayName("Multiple getInstance calls for the same project return the same cached instance")
	void getInstanceCachesPerProject() {
		IgnoreManager first = IgnoreManager.getInstance(project);
		IgnoreManager second = IgnoreManager.getInstance(project);
		assertTrue(first == second);
	}
}
