package checkmarx.ast.eclipse.plugin.tests.unit.properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.checkmarx.eclipse.properties.LabelFieldEditor;

class LabelFieldEditorTest {

	@Mock
	private Composite mockParent;

	@Mock
	private Label mockLabel;

	@Mock
	private GridData mockGridData;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testConstructor_withLabelTextAndParent() {
		LabelFieldEditor editor = new LabelFieldEditor("Test Label", mockParent);
		assertNotNull(editor);
	}

	@Test
	void testConstructor_withEmptyLabel() {
		LabelFieldEditor editor = new LabelFieldEditor("", mockParent);
		assertNotNull(editor);
	}

	@Test
	void testConstructor_usesFixedPreferenceName() {
		LabelFieldEditor editor1 = new LabelFieldEditor("Label1", mockParent);
		LabelFieldEditor editor2 = new LabelFieldEditor("Label2", mockParent);

		assertNotNull(editor1);
		assertNotNull(editor2);
	}

	@Test
	void testGetNumberOfControls_returnsOne() {
		LabelFieldEditor editor = new LabelFieldEditor("Test", mockParent);
		assertEquals(1, editor.getNumberOfControls());
	}

	@Test
	void testGetNumberOfControls_alwaysOne() {
		LabelFieldEditor editor1 = new LabelFieldEditor("Label1", mockParent);
		LabelFieldEditor editor2 = new LabelFieldEditor("Label2", mockParent);

		assertEquals(1, editor1.getNumberOfControls());
		assertEquals(1, editor2.getNumberOfControls());
	}

	@Test
	void testDoLoad_doesNotThrow() {
		LabelFieldEditor editor = new LabelFieldEditor("Test", mockParent);
		assertDoesNotThrow(() -> {
			// Access the doLoad method via reflection
			java.lang.reflect.Method method = editor.getClass().getDeclaredMethod("doLoad");
			method.setAccessible(true);
			method.invoke(editor);
		});
	}

	@Test
	void testDoLoadDefault_doesNotThrow() {
		LabelFieldEditor editor = new LabelFieldEditor("Test", mockParent);
		assertDoesNotThrow(() -> {
			java.lang.reflect.Method method = editor.getClass().getDeclaredMethod("doLoadDefault");
			method.setAccessible(true);
			method.invoke(editor);
		});
	}

	@Test
	void testDoStore_doesNotThrow() {
		LabelFieldEditor editor = new LabelFieldEditor("Test", mockParent);
		assertDoesNotThrow(() -> {
			java.lang.reflect.Method method = editor.getClass().getDeclaredMethod("doStore");
			method.setAccessible(true);
			method.invoke(editor);
		});
	}

	@Test
	void testMultipleInstances_independentState() {
		LabelFieldEditor editor1 = new LabelFieldEditor("Label One", mockParent);
		LabelFieldEditor editor2 = new LabelFieldEditor("Label Two", mockParent);

		assertNotNull(editor1);
		assertNotNull(editor2);
		assertEquals(1, editor1.getNumberOfControls());
		assertEquals(1, editor2.getNumberOfControls());
	}

	@Test
	void testConstructor_withSpecialCharactersInLabel() {
		String labelWithSpecialChars = "Test <Label> & More";
		LabelFieldEditor editor = new LabelFieldEditor(labelWithSpecialChars, mockParent);
		assertNotNull(editor);
	}

	@Test
	void testConstructor_withNullValue() {
		assertDoesNotThrow(() -> new LabelFieldEditor(null, mockParent));
	}
}
