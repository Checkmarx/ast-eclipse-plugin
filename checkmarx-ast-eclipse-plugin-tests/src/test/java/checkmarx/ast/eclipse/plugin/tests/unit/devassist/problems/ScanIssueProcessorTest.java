package checkmarx.ast.eclipse.plugin.tests.unit.devassist.problems;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.problems.ProblemDescriptor;
import com.checkmarx.eclipse.devassist.problems.ProblemHelper;
import com.checkmarx.eclipse.devassist.problems.ScanIssueProcessor;

/**
 * Unit tests for {@link ScanIssueProcessor}'s validation pipeline. Uses a
 * real {@link Document} for line-range checks (cheap, no editor needed) and
 * keeps {@code isDecoratorEnabled} false in most cases to avoid touching
 * {@link ProblemDecorator}'s workbench-dependent code path.
 */
class ScanIssueProcessorTest {

	private static final String FIVE_LINE_TEXT = "line1\nline2\nline3\nline4\nline5";

	private ScanIssue issueWithLocation(int line, String severity) {
		ScanIssue issue = new ScanIssue();
		issue.setTitle("Rule");
		issue.setSeverity(severity);
		Location location = new Location();
		location.setLine(line);
		issue.getLocations().add(location);
		return issue;
	}

	private ProblemHelper problemHelper(IFile file, IDocument document) {
		IProject project = mock(IProject.class);
		return ProblemHelper.builder(file, project).document(document).build();
	}

	@Test
	@DisplayName("Returns null when the scan issue has no locations")
	void returnsNullForMissingLocation() {
		IDocument document = new Document(FIVE_LINE_TEXT);
		ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper(mock(IFile.class), document));

		ScanIssue issue = new ScanIssue();
		issue.setSeverity("High");

		assertNull(processor.processScanIssue(issue, false));
	}

	@Test
	@DisplayName("Returns null when the location's line is out of the document's range")
	void returnsNullForOutOfRangeLine() {
		IDocument document = new Document(FIVE_LINE_TEXT);
		ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper(mock(IFile.class), document));

		assertNull(processor.processScanIssue(issueWithLocation(0, "High"), false));
		assertNull(processor.processScanIssue(issueWithLocation(999, "High"), false));
	}

	@Test
	@DisplayName("Returns null when severity is null or blank")
	void returnsNullForBlankSeverity() {
		IDocument document = new Document(FIVE_LINE_TEXT);
		ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper(mock(IFile.class), document));

		assertNull(processor.processScanIssue(issueWithLocation(2, null), false));
		assertNull(processor.processScanIssue(issueWithLocation(2, "  "), false));
	}

	@Test
	@DisplayName("Returns a ProblemDescriptor for a valid issue with a reportable severity")
	void returnsDescriptorForReportableSeverity() {
		IDocument document = new Document(FIVE_LINE_TEXT);
		ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper(mock(IFile.class), document));

		ProblemDescriptor descriptor = processor.processScanIssue(issueWithLocation(2, "High"), false);

		assertNotNull(descriptor);
		assertNotNull(descriptor.getDescription());
	}

	@Test
	@DisplayName("Returns null (no descriptor) for a valid issue whose severity is not reportable")
	void returnsNullForNonReportableSeverity() {
		IDocument document = new Document(FIVE_LINE_TEXT);
		ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper(mock(IFile.class), document));

		assertNull(processor.processScanIssue(issueWithLocation(2, "unknown"), false));
		assertNull(processor.processScanIssue(issueWithLocation(2, "ok"), false));
		assertNull(processor.processScanIssue(issueWithLocation(2, "ignored"), false));
	}

	@Test
	@DisplayName("All five reportable severities (malicious/critical/high/medium/low) produce a descriptor")
	void allReportableSeveritiesProduceADescriptor() {
		IDocument document = new Document(FIVE_LINE_TEXT);
		ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper(mock(IFile.class), document));

		for (String severity : new String[] { "Malicious", "Critical", "High", "Medium", "Low" }) {
			assertNotNull(processor.processScanIssue(issueWithLocation(1, severity), false),
					"Expected a descriptor for severity: " + severity);
		}
	}

	@Test
	@DisplayName("Returns null and does not throw when the document throws while checking line range")
	void returnsNullWhenDocumentThrows() {
		IDocument document = mock(IDocument.class);
		when(document.getNumberOfLines()).thenThrow(new RuntimeException("boom"));
		ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper(mock(IFile.class), document));

		assertNull(processor.processScanIssue(issueWithLocation(1, "High"), false));
	}

	@Test
	@DisplayName("Decorator path with a non-existent file logs a warning instead of throwing")
	void decoratorPathHandlesNonExistentFileGracefully() {
		IDocument document = new Document(FIVE_LINE_TEXT);
		IFile file = mock(IFile.class);
		when(file.exists()).thenReturn(false);
		ScanIssueProcessor processor = new ScanIssueProcessor(problemHelper(file, document));

		ProblemDescriptor descriptor = processor.processScanIssue(issueWithLocation(2, "High"), true);

		assertNotNull(descriptor, "The descriptor itself should still be produced regardless of decoration outcome");
	}

	@Test
	@DisplayName("Two-argument constructor (file, document, problemHelper) is equivalent to the ProblemHelper-only one")
	void twoArgumentConstructorBehavesTheSame() {
		IDocument document = new Document(FIVE_LINE_TEXT);
		IFile file = mock(IFile.class);
		ProblemHelper helper = problemHelper(file, document);
		ScanIssueProcessor processor = new ScanIssueProcessor(file, document, helper);

		assertNotNull(processor.processScanIssue(issueWithLocation(2, "High"), false));
	}
}
