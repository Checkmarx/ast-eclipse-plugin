package checkmarx.ast.eclipse.plugin.tests.unit.devassist.problems;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.QualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.problems.ProblemDescriptor;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;

/**
 * Unit tests for {@link ProblemHolderService}. Tests the caching and retrieval
 * of problem descriptors organized by file path.
 */
@DisplayName("ProblemHolderService unit tests")
class ProblemHolderServiceTest {

	private ProblemHolderService holder;
	private IProject mockProject;

	@BeforeEach
	void setUp() throws Exception {
		mockProject = mock(IProject.class);
		// IProject is a bare mock, so getSessionProperty/setSessionProperty are no-ops by
		// default; back them with a real map so getInstance's session-property cache behaves
		// like the real Eclipse IProject implementation would across repeated calls.
		Map<QualifiedName, Object> sessionProperties = new HashMap<>();
		doAnswer(invocation -> sessionProperties.get((QualifiedName) invocation.getArgument(0)))
				.when(mockProject).getSessionProperty(any());
		doAnswer(invocation -> {
			sessionProperties.put(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(mockProject).setSessionProperty(any(), any());

		holder = ProblemHolderService.getInstance(mockProject);
	}

	private ScanIssue createIssue(String title, String severity, int line) {
		ScanIssue issue = new ScanIssue();
		issue.setTitle(title);
		issue.setSeverity(severity);
		Location location = new Location();
		location.setLine(line);
		issue.getLocations().add(location);
		return issue;
	}

	@Test
	@DisplayName("getInstance returns singleton for same project")
	void getInstanceReturnsSingleton() {
		ProblemHolderService holder2 = ProblemHolderService.getInstance(mockProject);
		assertSame(holder, holder2);
	}

	@Test
	@DisplayName("addScanIssues stores issues with file path key")
	void addScanIssuesStoresIssues() {
		String filePath = "/project/src/Main.java";
		ScanIssue issue = createIssue("SQL Injection", "High", 10);

		holder.addScanIssues(filePath, java.util.List.of(issue));

		List<ScanIssue> issues = holder.getScanIssuesByFile(filePath);
		assertNotNull(issues);
		assertEquals(1, issues.size());
		assertEquals("SQL Injection", issues.get(0).getTitle());
	}

	@Test
	@DisplayName("getScanIssuesByFile returns empty list for unknown file")
	void getScanIssuesByFileReturnsEmptyForUnknownFile() {
		List<ScanIssue> issues = holder.getScanIssuesByFile("/unknown/path/File.java");
		assertNotNull(issues);
		assertTrue(issues.isEmpty());
	}

	@Test
	@DisplayName("Multiple issues for same file are stored")
	void multipleIssuesStoredForSameFile() {
		String filePath = "/project/src/Main.java";
		ScanIssue issue1 = createIssue("SQL Injection", "High", 10);
		ScanIssue issue2 = createIssue("XSS", "Medium", 20);

		holder.addScanIssues(filePath, java.util.List.of(issue1, issue2));

		List<ScanIssue> issues = holder.getScanIssuesByFile(filePath);
		assertEquals(2, issues.size());
	}

	@Test
	@DisplayName("removeScanIssues clears all issues for file")
	void removeScanIssuesClearsIssues() {
		String filePath = "/project/src/Main.java";
		ScanIssue issue = createIssue("Rule", "High", 10);
		holder.addScanIssues(filePath, java.util.List.of(issue));

		holder.removeScanIssues(filePath);

		List<ScanIssue> issues = holder.getScanIssuesByFile(filePath);
		assertTrue(issues.isEmpty());
	}

	@Test
	@DisplayName("getAllScanIssues returns map of all issues")
	void getAllScanIssuesReturnsAllIssues() {
		String file1 = "/project/File1.java";
		String file2 = "/project/File2.java";

		ScanIssue issue1 = createIssue("Rule1", "High", 10);
		ScanIssue issue2 = createIssue("Rule2", "Medium", 20);
		holder.addScanIssues(file1, java.util.List.of(issue1));
		holder.addScanIssues(file2, java.util.List.of(issue2));

		Map<String, List<ScanIssue>> allIssues = holder.getAllScanIssues();
		assertEquals(2, allIssues.size());
		assertTrue(allIssues.containsKey(file1));
		assertTrue(allIssues.containsKey(file2));
	}

	@Test
	@DisplayName("clearAll removes all issues")
	void clearAllRemovesAllIssues() {
		String filePath = "/project/File.java";
		ScanIssue issue = createIssue("Rule", "High", 10);
		holder.addScanIssues(filePath, java.util.List.of(issue));

		holder.clearAll();

		Map<String, List<ScanIssue>> allIssues = holder.getAllScanIssues();
		assertTrue(allIssues.isEmpty());
	}

	@Test
	@DisplayName("addScanIssues with null issues handles gracefully")
	void addScanIssuesWithNullHandled() {
		assertDoesNotThrow(() -> holder.addScanIssues("/path", null));
	}

	@Test
	@DisplayName("Issue count reflects added issues")
	void issueCountAccurate() {
		String filePath = "/project/File.java";

		assertEquals(0, holder.getAllScanIssues().size());

		holder.addScanIssues(filePath, java.util.List.of(createIssue("R1", "High", 1)));
		assertEquals(1, holder.getAllScanIssues().size());

		holder.addScanIssues(filePath, java.util.List.of(createIssue("R2", "Medium", 2)));
		assertEquals(1, holder.getAllScanIssues().size()); // same file
	}

	@Test
	@DisplayName("Handles special characters in file paths")
	void handlesSpecialCharactersInPaths() {
		String filePath = "/project/src & test/File#1.java";
		ScanIssue issue = createIssue("Rule", "High", 10);
		holder.addScanIssues(filePath, java.util.List.of(issue));

		List<ScanIssue> issues = holder.getScanIssuesByFile(filePath);
		assertEquals(1, issues.size());
	}

	@Test
	@DisplayName("mergeScanIssues deduplicates by issue ID")
	void mergeScanIssuesDeduplicated() {
		String filePath = "/project/File.java";
		ScanIssue issue1 = createIssue("Rule1", "High", 10);
		issue1.setScanIssueId("ID1");
		ScanIssue issue2 = createIssue("Rule2", "Medium", 20);
		issue2.setScanIssueId("ID2");

		holder.addScanIssues(filePath, java.util.List.of(issue1));
		holder.mergeScanIssues(filePath, java.util.List.of(issue2));

		List<ScanIssue> issues = holder.getScanIssuesByFile(filePath);
		assertEquals(2, issues.size());
	}

	@Test
	@DisplayName("getCacheStats returns accurate statistics")
	void getCacheStatsReturnsStats() {
		String file1 = "/project/File1.java";
		String file2 = "/project/File2.java";
		ScanIssue issue1 = createIssue("Rule1", "High", 10);
		ScanIssue issue2 = createIssue("Rule2", "Medium", 20);

		holder.addScanIssues(file1, java.util.List.of(issue1));
		holder.addScanIssues(file2, java.util.List.of(issue2));

		String stats = holder.getCacheStats();
		assertNotNull(stats);
		assertTrue(stats.contains("Files:"));
		assertTrue(stats.contains("2"));
	}

	@Test
	@DisplayName("getProblemDescriptors returns cached descriptors")
	void getProblemDescriptorsReturnsCached() {
		String filePath = "/project/File.java";
		List<ProblemDescriptor> descriptors = java.util.List.of(mock(ProblemDescriptor.class));

		holder.addProblemDescriptors(filePath, descriptors);
		List<ProblemDescriptor> cached = holder.getProblemDescriptors(filePath);

		assertNotNull(cached);
		assertEquals(1, cached.size());
	}

	@Test
	@DisplayName("removeProblemDescriptorsForFile clears descriptors")
	void removeProblemDescriptorsForFileClearsCache() {
		String filePath = "/project/File.java";
		List<ProblemDescriptor> descriptors = java.util.List.of(mock(ProblemDescriptor.class));

		holder.addProblemDescriptors(filePath, descriptors);
		holder.removeProblemDescriptorsForFile(filePath);
		List<ProblemDescriptor> cached = holder.getProblemDescriptors(filePath);

		assertTrue(cached.isEmpty());
	}
}
