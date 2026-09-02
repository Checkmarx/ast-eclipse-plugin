package checkmarx.ast.eclipse.plugin.tests.unit.devassist.problems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;

class ProblemHolderServiceTest {

	private ProblemHolderService service;

	@BeforeEach
	void setUp() {
		service = new ProblemHolderService();
	}

	@Test
	void testAddScanIssues_ValidInput() {
		String filePath = "testFile.java";
		ScanIssue issue = new ScanIssue();
		issue.setSeverity("HIGH");
		issue.setScanEngine(ScanEngine.OSS);
		List<ScanIssue> issues = Collections.singletonList(issue);

		service.addScanIssues(filePath, issues);

		Map<String, List<ScanIssue>> allIssues = service.getAllScanIssues();
		assertTrue(allIssues.containsKey(filePath));
		assertEquals(1, allIssues.get(filePath).size());
	}

	@Test
	void testGetAllScanIssues_Empty() {
		Map<String, List<ScanIssue>> allIssues = service.getAllScanIssues();
		assertTrue(allIssues.isEmpty());
	}

	@Test
	void testRemoveAllIssuesForScanner_ValidType() {
		String filePath = "testFile.java";
		ScanIssue issue = new ScanIssue();
		issue.setScanEngine(ScanEngine.OSS);
		service.addScanIssues(filePath, Collections.singletonList(issue));

		service.removeAllIssuesForScanner("OSS");
		Map<String, List<ScanIssue>> remainingIssues = service.getAllScanIssues();
		assertTrue(remainingIssues.isEmpty() || remainingIssues.get(filePath).isEmpty());
	}

	@Test
	void testGetScanIssuesByFile_NoIssues() {
		List<ScanIssue> issues = service.getScanIssuesByFile("nonExistentFile.java");
		assertTrue(issues.isEmpty());
	}

	@Test
	void testMergeScanIssues_ValidInput() {
		String filePath = "testFile.java";
		ScanIssue issue1 = new ScanIssue();
		issue1.setSeverity("HIGH");
		issue1.setScanEngine(ScanEngine.OSS);

		service.addScanIssues(filePath, Collections.singletonList(issue1));

		ScanIssue issue2 = new ScanIssue();
		issue2.setSeverity("MEDIUM");
		issue2.setScanEngine(ScanEngine.ASCA);
		service.mergeScanIssues(filePath, Collections.singletonList(issue2));

		List<ScanIssue> issues = service.getScanIssuesByFile(filePath);
		assertEquals(2, issues.size());
	}

	@Test
	void testRemoveScanIssues_ValidFile() {
		String filePath = "testFile.java";
		ScanIssue issue = new ScanIssue();
		issue.setScanEngine(ScanEngine.OSS);
		service.addScanIssues(filePath, Collections.singletonList(issue));

		service.removeScanIssues(filePath);
		List<ScanIssue> issues = service.getScanIssuesByFile(filePath);
		assertTrue(issues.isEmpty());
	}

	@Test
	void testRemoveScanIssuesByFileAndScanner_ValidInput() {
		String filePath = "testFile.java";
		ScanIssue issue1 = new ScanIssue();
		issue1.setScanEngine(ScanEngine.OSS);

		ScanIssue issue2 = new ScanIssue();
		issue2.setScanEngine(ScanEngine.ASCA);

		service.addScanIssues(filePath, List.of(issue1, issue2));
		service.removeScanIssuesByFileAndScanner("OSS", filePath);

		List<ScanIssue> remainingIssues = service.getScanIssuesByFile(filePath);
		assertEquals(1, remainingIssues.size());
		assertEquals(ScanEngine.ASCA, remainingIssues.get(0).getScanEngine());
	}

	@Test
	void testClearAll_RemovesAllIssues() {
		service.addScanIssues("file1.java", Collections.singletonList(new ScanIssue()));
		service.addScanIssues("file2.java", Collections.singletonList(new ScanIssue()));

		service.clearAll();

		Map<String, List<ScanIssue>> allIssues = service.getAllScanIssues();
		assertTrue(allIssues.isEmpty());
	}
}
