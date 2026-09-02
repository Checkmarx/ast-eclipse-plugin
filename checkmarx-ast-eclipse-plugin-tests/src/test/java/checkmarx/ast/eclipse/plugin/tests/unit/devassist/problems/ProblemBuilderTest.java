package checkmarx.ast.eclipse.plugin.tests.unit.devassist.problems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.devassist.model.ScanIssue;

/**
 * Unit tests for {@link ProblemBuilder}. Pure logic - builds a
 * {@link ProblemDescriptor} from a {@link ScanIssue}, no Eclipse
 * workspace/editor interaction involved.
 */
class ProblemBuilderTest {

	private ProblemHelper problemHelper(IFile file) {
		IProject project = mock(IProject.class);
		return ProblemHelper.builder(file, project).build();
	}

	@Test
	@DisplayName("build() wires file, scanIssue and line number through to the descriptor")
	void buildWiresBasicFields() {
		IFile file = mock(IFile.class);
		ScanIssue issue = new ScanIssue();
		issue.setTitle("SQL Injection");
		issue.setSeverity("High");
		issue.setDescription("desc");

		ProblemDescriptor descriptor = ProblemBuilder.build(problemHelper(file), issue, 42);

		assertSame(file, descriptor.getFile());
		assertSame(issue, descriptor.getScanIssue());
		assertEquals(42, descriptor.getLineNumber());
		assertTrue(descriptor.getFixes().isEmpty());
	}

	@Test
	@DisplayName("build() formats an HTML description containing title, severity and description")
	void buildFormatsHtmlDescription() {
		ScanIssue issue = new ScanIssue();
		issue.setTitle("SQL Injection");
		issue.setSeverity("High");
		issue.setDescription("Untrusted input used in query");

		ProblemDescriptor descriptor = ProblemBuilder.build(problemHelper(mock(IFile.class)), issue, 1);

		String description = descriptor.getDescription();
		assertTrue(description.startsWith("<html>"));
		assertTrue(description.endsWith("</html>"));
		assertTrue(description.contains("<b>SQL Injection</b>"));
		assertTrue(description.contains("Severity: High"));
		assertTrue(description.contains("Untrusted input used in query"));
	}

	@Test
	@DisplayName("build() HTML-escapes special characters in the title and description")
	void buildEscapesHtmlSpecialCharacters() {
		ScanIssue issue = new ScanIssue();
		issue.setTitle("<script>alert('xss')</script>");
		issue.setSeverity("Critical");
		issue.setDescription("Value \"a & b\" < c > d");

		ProblemDescriptor descriptor = ProblemBuilder.build(problemHelper(mock(IFile.class)), issue, 1);

		String description = descriptor.getDescription();
		assertTrue(description.contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;"));
		assertTrue(description.contains("Value &quot;a &amp; b&quot; &lt; c &gt; d"));
		assertTrue(!description.contains("<script>"), "Raw script tag must not appear unescaped");
	}

	@Test
	@DisplayName("build() omits the description section when the scan issue has no description")
	void buildOmitsMissingDescription() {
		ScanIssue issue = new ScanIssue();
		issue.setTitle("Rule");
		issue.setSeverity("Low");
		issue.setDescription("");

		ProblemDescriptor descriptor = ProblemBuilder.build(problemHelper(mock(IFile.class)), issue, 1);

		assertEquals("<html><b>Rule</b><br/>Severity: Low<br/></html>", descriptor.getDescription());
	}

	@Test
	@DisplayName("getFixesArray returns an empty array, matching the current no-fixes implementation")
	void getFixesArrayIsEmpty() {
		ScanIssue issue = new ScanIssue();
		issue.setTitle("Rule");
		issue.setSeverity("Low");

		ProblemDescriptor descriptor = ProblemBuilder.build(problemHelper(mock(IFile.class)), issue, 1);

		assertEquals(0, descriptor.getFixesArray().length);
	}
}
