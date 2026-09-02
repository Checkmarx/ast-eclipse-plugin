package checkmarx.ast.eclipse.plugin.tests.unit.devassist.problems;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;
import com.checkmarx.eclipse.devassist.problems.ProblemDecorator;

/**
 * Unit tests for {@link ProblemDecorator}. The public API is almost entirely
 * workbench/editor-driven, so most coverage here targets the private static
 * pure-geometry/mapping helpers via reflection, using a real (lightweight)
 * {@link Document} instead of a live editor. The public entry points are
 * exercised only via their safe "no open editor" no-op path.
 */
class ProblemDecoratorTest {

	private static String invokeNormalizeFilePath(String path) throws Exception {
		Method m = ProblemDecorator.class.getDeclaredMethod("normalizeFilePath", String.class);
		m.setAccessible(true);
		return (String) m.invoke(null, path);
	}

	private static String invokeMapSeverity(String severity) throws Exception {
		Method m = ProblemDecorator.class.getDeclaredMethod("mapSeverityToAnnotationType", String.class);
		m.setAccessible(true);
		return (String) m.invoke(null, severity);
	}

	private static int invokeLeadingWhitespace(IDocument document, int offset, int length) throws Exception {
		Method m = ProblemDecorator.class.getDeclaredMethod("getLeadingWhitespaceOffset", IDocument.class, int.class,
				int.class);
		m.setAccessible(true);
		return (int) m.invoke(null, document, offset, length);
	}

	private static Position invokeCalculateRange(ITextEditor editor, ScanIssue issue) throws Exception {
		Method m = ProblemDecorator.class.getDeclaredMethod("calculateRange", ITextEditor.class, ScanIssue.class);
		m.setAccessible(true);
		return (Position) m.invoke(null, editor, issue);
	}

	private static Position invokeDecorateOssFirstLineOnly(ITextEditor editor, ScanIssue issue) throws Exception {
		Method m = ProblemDecorator.class.getDeclaredMethod("decorateOssFirstLineOnly", ITextEditor.class,
				ScanIssue.class);
		m.setAccessible(true);
		return (Position) m.invoke(null, editor, issue);
	}

	private ITextEditor editorWithDocument(IDocument document) {
		ITextEditor editor = mock(ITextEditor.class);
		IDocumentProvider provider = mock(IDocumentProvider.class);
		IEditorInput input = mock(IEditorInput.class);
		when(editor.getEditorInput()).thenReturn(input);
		when(editor.getDocumentProvider()).thenReturn(provider);
		when(provider.getDocument(input)).thenReturn(document);
		return editor;
	}

	private ScanIssue issueWithLocation(int line, int start, int end) {
		ScanIssue issue = new ScanIssue();
		Location location = new Location(line, start, end);
		issue.getLocations().add(location);
		return issue;
	}

	@Test
	@DisplayName("normalizeFilePath converts backslashes to forward slashes")
	void normalizeFilePathConvertsSeparators() throws Exception {
		String normalized = invokeNormalizeFilePath("C:\\repo\\src\\Main.java");
		assertTrue(normalized.contains("/repo/src/"));
		assertTrue(!normalized.contains("\\"));
	}

	@Test
	@DisplayName("normalizeFilePath returns empty string for null or empty input")
	void normalizeFilePathHandlesNullOrEmpty() throws Exception {
		assertEquals("", invokeNormalizeFilePath(null));
		assertEquals("", invokeNormalizeFilePath(""));
	}

	@Test
	@DisplayName("mapSeverityToAnnotationType maps each known severity to its annotation type")
	void mapSeverityToAnnotationTypeMapsKnownSeverities() throws Exception {
		// DevAssistUtils.isDarkTheme() falls back to Display.getSystemColor() when no
		// e4 CSS theme id is available, which requires the SWT UI thread - and Tycho
		// surefire runs tests off it (-nouithread). Statically mock it so the theme
		// suffix is deterministic and no real Display access happens.
		try (MockedStatic<DevAssistUtils> mocked = org.mockito.Mockito.mockStatic(DevAssistUtils.class)) {
			mocked.when(DevAssistUtils::isDarkTheme).thenReturn(false);

			assertEquals("com.checkmarx.eclipse.findings.malicious", invokeMapSeverity("Malicious"));
			assertEquals("com.checkmarx.eclipse.findings.critical", invokeMapSeverity("Critical"));
			assertEquals("com.checkmarx.eclipse.findings.high", invokeMapSeverity("High"));
			assertEquals("com.checkmarx.eclipse.findings.medium", invokeMapSeverity("Medium"));
			assertEquals("com.checkmarx.eclipse.findings.low", invokeMapSeverity("Low"));
			assertEquals("com.checkmarx.eclipse.findings.unknown", invokeMapSeverity("Unknown"));
			assertEquals("com.checkmarx.eclipse.findings.ok", invokeMapSeverity("OK"));
			assertEquals("com.checkmarx.eclipse.findings.ignored", invokeMapSeverity("Ignored"));
		}
	}

	@Test
	@DisplayName("mapSeverityToAnnotationType appends the dark-theme suffix when isDarkTheme() is true")
	void mapSeverityToAnnotationTypeAppendsDarkSuffix() throws Exception {
		try (MockedStatic<DevAssistUtils> mocked = org.mockito.Mockito.mockStatic(DevAssistUtils.class)) {
			mocked.when(DevAssistUtils::isDarkTheme).thenReturn(true);

			assertEquals("com.checkmarx.eclipse.findings.high_dark", invokeMapSeverity("High"));
		}
	}

	@Test
	@DisplayName("mapSeverityToAnnotationType defaults to unknown for null or unrecognized severity")
	void mapSeverityToAnnotationTypeDefaultsToUnknown() throws Exception {
		try (MockedStatic<DevAssistUtils> mocked = org.mockito.Mockito.mockStatic(DevAssistUtils.class)) {
			mocked.when(DevAssistUtils::isDarkTheme).thenReturn(false);

			assertEquals("com.checkmarx.eclipse.findings.unknown", invokeMapSeverity(null));
			assertEquals("com.checkmarx.eclipse.findings.unknown", invokeMapSeverity("Weird"));
		}
	}

	@Test
	@DisplayName("getLeadingWhitespaceOffset counts leading spaces on a line")
	void getLeadingWhitespaceOffsetCountsSpaces() throws Exception {
		Document document = new Document("    indented code");
		assertEquals(4, invokeLeadingWhitespace(document, 0, document.getLength()));
	}

	@Test
	@DisplayName("getLeadingWhitespaceOffset returns 0 for a line with no leading whitespace")
	void getLeadingWhitespaceOffsetReturnsZeroWhenNoIndent() throws Exception {
		Document document = new Document("no indent here");
		assertEquals(0, invokeLeadingWhitespace(document, 0, document.getLength()));
	}

	@Test
	@DisplayName("calculateRange uses the issue's absolute location offsets when isAbsoluteOffset is set")
	void calculateRangeUsesAbsoluteOffsets() throws Exception {
		Document document = new Document("first line\nsecond line\nthird line");
		ITextEditor editor = editorWithDocument(document);

		Location location = new Location(2, 11, 17, true); // absolute offsets into "second"
		ScanIssue issue = new ScanIssue();
		issue.getLocations().add(location);

		Position pos = invokeCalculateRange(editor, issue);

		assertNotNull(pos);
		assertEquals(11, pos.getOffset());
		assertEquals(6, pos.getLength());
	}

	@Test
	@DisplayName("calculateRange falls back to the whole (trimmed) line when the range collapses")
	void calculateRangeFallsBackToLineWhenRangeInvalid() throws Exception {
		Document document = new Document("  indented line content");
		ITextEditor editor = editorWithDocument(document);

		// start == end (line-relative, both 0) forces the "highlight whole line" fallback
		Location location = new Location(1, 0, 0, false);
		ScanIssue issue = new ScanIssue();
		issue.getLocations().add(location);

		Position pos = invokeCalculateRange(editor, issue);

		assertNotNull(pos);
		assertTrue(pos.getLength() > 0);
	}

	@Test
	@DisplayName("calculateRange returns a safe default position when the document is unavailable")
	void calculateRangeReturnsDefaultWhenDocumentMissing() throws Exception {
		ITextEditor editor = mock(ITextEditor.class);
		IDocumentProvider provider = mock(IDocumentProvider.class);
		when(editor.getDocumentProvider()).thenReturn(provider);
		when(provider.getDocument(org.mockito.ArgumentMatchers.any())).thenReturn(null);

		Position pos = invokeCalculateRange(editor, issueWithLocation(1, 0, 1));

		assertEquals(0, pos.getOffset());
		assertEquals(1, pos.getLength());
	}

	@Test
	@DisplayName("decorateOssFirstLineOnly spans from the first location's start to the last location's end")
	void decorateOssFirstLineOnlySpansMultipleLocations() throws Exception {
		Document document = new Document("group {\n  dependency 'a:b:1.0'\n}");
		ITextEditor editor = editorWithDocument(document);

		ScanIssue issue = new ScanIssue();
		issue.getLocations().add(new Location(2, 2, 12)); // "dependency"
		issue.getLocations().add(new Location(2, 13, 28)); // "'a:b:1.0'"

		Position pos = invokeDecorateOssFirstLineOnly(editor, issue);

		assertNotNull(pos);
		assertTrue(pos.getLength() > 0);
	}

	@Test
	@DisplayName("decorateOssFirstLineOnly returns null when the issue has no locations")
	void decorateOssFirstLineOnlyReturnsNullForNoLocations() throws Exception {
		Document document = new Document("some content");
		ITextEditor editor = editorWithDocument(document);

		Position pos = invokeDecorateOssFirstLineOnly(editor, new ScanIssue());

		assertNull(pos);
	}

	@Test
	@DisplayName("decorateOssFirstLineOnly returns null when the location's line is out of bounds")
	void decorateOssFirstLineOnlyReturnsNullForOutOfBoundsLine() throws Exception {
		Document document = new Document("only one line");
		ITextEditor editor = editorWithDocument(document);

		Position pos = invokeDecorateOssFirstLineOnly(editor, issueWithLocation(99, 0, 1));

		assertNull(pos);
	}

	@Test
	@DisplayName("decorateEditor is a safe no-op when there is no open editor for the file")
	void decorateEditorNoOpsWhenNoEditorOpen() {
		IFile file = mock(IFile.class);
		when(file.getLocation()).thenReturn(org.eclipse.core.runtime.Path.fromOSString("/tmp/does-not-exist/Main.java"));

		assertDoesNotThrow(() -> ProblemDecorator.decorateEditor(file, List.of(issueWithLocation(1, 0, 1))));
	}

	@Test
	@DisplayName("clearDecorations, clearAllAnnotations, getStatistics and removeAllHighlighters are all safe no-ops")
	void publicUtilityMethodsAreSafe() {
		IFile file = mock(IFile.class);
		when(file.getLocation()).thenReturn(org.eclipse.core.runtime.Path.fromOSString("/tmp/does-not-exist/Main.java"));
		IProject project = mock(IProject.class);
		when(project.getName()).thenReturn("TestProject");

		assertDoesNotThrow(() -> ProblemDecorator.clearDecorations(file));
		assertDoesNotThrow(ProblemDecorator::clearAllAnnotations);
		assertNotNull(ProblemDecorator.getStatistics());
		assertDoesNotThrow(() -> ProblemDecorator.removeAllHighlighters(project));
	}
}
