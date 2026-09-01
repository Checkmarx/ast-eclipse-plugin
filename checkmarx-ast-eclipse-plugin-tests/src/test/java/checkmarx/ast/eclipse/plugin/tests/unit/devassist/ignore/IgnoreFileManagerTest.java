package checkmarx.ast.eclipse.plugin.tests.unit.devassist.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.utils.ScanEngine;

/**
 * Unit tests for {@link IgnoreFileManager}. Each test uses a fresh mocked
 * {@link IProject} backed by a manually managed temp directory as its
 * location, so file I/O (ensureIgnoreFileExists/save/load) exercises the
 * real disk path without touching the actual workspace. The temp directory
 * is created in {@code @BeforeEach} and deleted in {@code @AfterEach}
 * (JUnit5's built-in {@code @TempDir} parameter resolver is not available in
 * this Eclipse-bundled JUnit5 runtime).
 */
class IgnoreFileManagerTest {

	private Path tempDir;

	@BeforeEach
	void createTempDir() throws IOException {
		tempDir = Files.createTempDirectory("ignore-file-manager-test");
	}

	@AfterEach
	void deleteTempDir() throws IOException {
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

	private IProject projectAt(Path root) {
		IProject project = mock(IProject.class);
		IPath ipath = org.eclipse.core.runtime.Path.fromOSString(root.toAbsolutePath().toString());
		when(project.getLocation()).thenReturn(ipath);
		return project;
	}

	private IgnoreEntry.FileReference fileRef(String path, boolean active, int line) {
		return new IgnoreEntry.FileReference(path, active, line, "");
	}

	@Test
	@DisplayName("Constructing a manager creates the .checkmarx/.checkmarxIgnored file with empty content")
	void constructorCreatesIgnoreFile() {
		IProject project = projectAt(tempDir);
		IgnoreFileManager manager = new IgnoreFileManager(project);

		Path ignoreFile = tempDir.resolve(".checkmarx").resolve(".checkmarxIgnored");
		assertTrue(Files.exists(ignoreFile));
		assertTrue(manager.getIgnoreData().isEmpty());
	}

	@Test
	@DisplayName("updateIgnoreData stores the entry in memory and getAllIgnoreEntries reflects it")
	void updateIgnoreDataStoresEntry() {
		IgnoreFileManager manager = new IgnoreFileManager(projectAt(tempDir));
		IgnoreEntry entry = new IgnoreEntry();
		entry.type = ScanEngine.OSS;
		entry.packageName = "lodash";

		manager.updateIgnoreData("OSS:lodash:1.0.0:npm", entry);

		assertEquals(1, manager.getAllIgnoreEntries().size());
		assertTrue(manager.getIgnoreData().containsKey("OSS:lodash:1.0.0:npm"));
	}

	@Test
	@DisplayName("saveIgnoreDataToDisk persists data that a fresh manager instance reloads for the same project")
	void savedDataIsReloadedByANewInstance() {
		IProject project = projectAt(tempDir);
		IgnoreFileManager writer = new IgnoreFileManager(project);
		IgnoreEntry entry = new IgnoreEntry();
		entry.type = ScanEngine.SECRETS;
		entry.packageName = "AWS Key";
		writer.updateIgnoreData("SECRETS:AWS Key:secretvalue:path", entry);

		// Bypass the static getInstance() cache to verify the on-disk file itself,
		// not just the in-memory singleton.
		IgnoreFileManager reader = new IgnoreFileManager(project);
		assertTrue(reader.getIgnoreData().containsKey("SECRETS:AWS Key:secretvalue:path"));
	}

	@Test
	@DisplayName("normalizePath relativizes a path under the project root to a forward-slash relative path")
	void normalizePathRelativizesUnderProjectRoot() {
		IgnoreFileManager manager = new IgnoreFileManager(projectAt(tempDir));
		Path file = tempDir.resolve("src").resolve("Main.java");

		String normalized = manager.normalizePath(file.toString());

		assertEquals("src/Main.java", normalized);
	}

	@Test
	@DisplayName("normalizePath falls back to a slash-converted raw path when relativizing fails")
	void normalizePathFallsBackOnMismatchedRoot() {
		IgnoreFileManager manager = new IgnoreFileManager(projectAt(tempDir));

		String normalized = manager.normalizePath("Z:\\unrelated\\Main.java");

		assertEquals("Z:/unrelated/Main.java", normalized);
	}

	@Test
	@DisplayName("normalizePath returns empty string for null or empty input")
	void normalizePathHandlesNullOrEmpty() {
		IgnoreFileManager manager = new IgnoreFileManager(projectAt(tempDir));
		assertEquals("", manager.normalizePath(null));
		assertEquals("", manager.normalizePath(""));
	}

	@Test
	@DisplayName("isIgnored(similarityId) reflects presence of the key in the ignore data map")
	void isIgnoredChecksSimilarityIdKey() {
		IgnoreFileManager manager = new IgnoreFileManager(projectAt(tempDir));
		assertFalse(manager.isIgnored("some-key"));

		manager.updateIgnoreData("some-key", new IgnoreEntry());
		assertTrue(manager.isIgnored("some-key"));
		assertFalse(manager.isIgnored(null));
		assertFalse(manager.isIgnored(""));
	}

	@Test
	@DisplayName("matchesEntry compares OSS entries by package name, version and manager")
	void matchesEntryComparesOssFields() {
		IgnoreFileManager manager = new IgnoreFileManager(projectAt(tempDir));

		IgnoreEntry a = new IgnoreEntry();
		a.type = ScanEngine.OSS;
		a.packageName = "lodash";
		a.packageVersion = "1.0.0";
		a.packageManager = "npm";

		IgnoreEntry sameIdentity = new IgnoreEntry();
		sameIdentity.type = ScanEngine.OSS;
		sameIdentity.packageName = "lodash";
		sameIdentity.packageVersion = "1.0.0";
		sameIdentity.packageManager = "npm";

		IgnoreEntry differentVersion = new IgnoreEntry();
		differentVersion.type = ScanEngine.OSS;
		differentVersion.packageName = "lodash";
		differentVersion.packageVersion = "2.0.0";
		differentVersion.packageManager = "npm";

		assertTrue(manager.matchesEntry(a, sameIdentity));
		assertFalse(manager.matchesEntry(a, differentVersion));
	}

	@Test
	@DisplayName("matchesEntry returns false when entry types differ or type is unhandled")
	void matchesEntryRejectsDifferentOrUnhandledTypes() {
		IgnoreFileManager manager = new IgnoreFileManager(projectAt(tempDir));

		IgnoreEntry oss = new IgnoreEntry();
		oss.type = ScanEngine.OSS;
		IgnoreEntry secrets = new IgnoreEntry();
		secrets.type = ScanEngine.SECRETS;
		assertFalse(manager.matchesEntry(oss, secrets));

		IgnoreEntry allA = new IgnoreEntry();
		allA.type = ScanEngine.ALL;
		IgnoreEntry allB = new IgnoreEntry();
		allB.type = ScanEngine.ALL;
		assertFalse(manager.matchesEntry(allA, allB), "ALL is not handled by any case branch, so it falls to default false");
	}

	@Test
	@DisplayName("reviveEntry deactivates all file references for a matching entry and persists the change")
	void reviveEntryDeactivatesMatchingEntry() {
		IgnoreFileManager manager = new IgnoreFileManager(projectAt(tempDir));
		IgnoreEntry entry = new IgnoreEntry();
		entry.type = ScanEngine.CONTAINERS;
		entry.imageName = "nginx";
		entry.imageTag = "latest";
		entry.files.add(fileRef("Dockerfile", true, 1));
		manager.updateIgnoreData("CONTAINERS:nginx:latest", entry);

		IgnoreEntry toRevive = new IgnoreEntry();
		toRevive.type = ScanEngine.CONTAINERS;
		toRevive.imageName = "nginx";
		toRevive.imageTag = "latest";

		assertTrue(manager.reviveEntry(toRevive));
		assertFalse(manager.getIgnoreData().get("CONTAINERS:nginx:latest").getFiles().get(0).isActive());
	}

	@Test
	@DisplayName("reviveEntry returns false when no matching entry exists")
	void reviveEntryReturnsFalseWhenNotFound() {
		IgnoreFileManager manager = new IgnoreFileManager(projectAt(tempDir));
		IgnoreEntry toRevive = new IgnoreEntry();
		toRevive.type = ScanEngine.CONTAINERS;
		toRevive.imageName = "does-not-exist";
		toRevive.imageTag = "latest";

		assertFalse(manager.reviveEntry(toRevive));
	}

	@Test
	@DisplayName("deleteIgnoreFiles clears in-memory data and removes the ignore file from disk")
	void deleteIgnoreFilesClearsStateAndFiles() {
		IgnoreFileManager manager = new IgnoreFileManager(projectAt(tempDir));
		IgnoreEntry entry = new IgnoreEntry();
		entry.type = ScanEngine.OSS;
		manager.updateIgnoreData("OSS:pkg:1.0:npm", entry);
		assertTrue(Files.exists(manager.getIgnoreFilePath()));

		manager.deleteIgnoreFiles();

		assertTrue(manager.getIgnoreData().isEmpty());
		assertFalse(Files.exists(manager.getIgnoreFilePath()));
	}
}
