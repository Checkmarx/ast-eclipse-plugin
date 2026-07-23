package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.checkmarx.ast.results.result.Result;
import com.checkmarx.eclipse.views.DisplayModel;

class DisplayModelTest {

	private DisplayModel displayModel;
	private DisplayModel parentModel;

	@Mock
	private Result mockResult;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		parentModel = new DisplayModel.DisplayModelBuilder("Parent").build();
	}

	// ─── Builder Pattern Tests ───────────────────────────────────────────

	@Test
	void testBuilder_withName_createsDisplayModel() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test Name").build();

		assertNotNull(displayModel);
		assertEquals("Test Name", displayModel.getName());
	}

	@Test
	void testBuilder_withAllFields_setsAllProperties() {
		List<DisplayModel> children = new ArrayList<>();
		displayModel = new DisplayModel.DisplayModelBuilder("Root")
			.setType("SAST")
			.setSeverity("HIGH")
			.setQueryName("SQL Injection")
			.setSate("Confirmed")
			.setParent(parentModel)
			.setChildren(children)
			.setResult(mockResult)
			.build();

		assertEquals("Root", displayModel.getName());
		assertEquals("SAST", displayModel.getType());
		assertEquals("HIGH", displayModel.getSeverity());
		assertEquals("SQL Injection", displayModel.getQueryName());
		assertEquals("Confirmed", displayModel.getState());
		assertSame(parentModel, displayModel.getParent());
		assertSame(children, displayModel.getChildren());
		assertSame(mockResult, displayModel.getResult());
	}

	@Test
	void testBuilder_chainingMultipleMethods_returnsBuilder() {
		DisplayModel.DisplayModelBuilder builder = new DisplayModel.DisplayModelBuilder("Test");
		DisplayModel.DisplayModelBuilder result = builder.setType("KICS");

		assertNotNull(result);
		assertSame(builder, result);
	}

	@Test
	void testBuilder_withNullValues_acceptsNull() {
		displayModel = new DisplayModel.DisplayModelBuilder("Name")
			.setParent(null)
			.setResult(null)
			.build();

		assertNull(displayModel.getParent());
		assertNull(displayModel.getResult());
	}

	@Test
	void testBuilder_emptyChildrenList_createsEmptyList() {
		displayModel = new DisplayModel.DisplayModelBuilder("Parent")
			.setChildren(new ArrayList<>())
			.build();

		assertNotNull(displayModel.getChildren());
		assertTrue(displayModel.getChildren().isEmpty());
	}

	// ─── Getters and Setters ───────────────────────────────────────────

	@Test
	void testSetAndGetName() {
		displayModel = new DisplayModel.DisplayModelBuilder("Initial").build();
		displayModel.setName("Updated");

		assertEquals("Updated", displayModel.getName());
	}

	@Test
	void testSetAndGetType() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setType("SCA");

		assertEquals("SCA", displayModel.getType());
	}

	@Test
	void testSetAndGetSeverity() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setSeverity("CRITICAL");

		assertEquals("CRITICAL", displayModel.getSeverity());
	}

	@Test
	void testSetAndGetQueryName() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setQueryName("Hardcoded Secret");

		assertEquals("Hardcoded Secret", displayModel.getQueryName());
	}

	@Test
	void testSetAndGetState() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setState("Confirmed");

		assertEquals("Confirmed", displayModel.getState());
	}

	@Test
	void testSetAndGetResult() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setResult(mockResult);

		assertSame(mockResult, displayModel.getResult());
	}

	@Test
	void testSetAndGetParent() {
		displayModel = new DisplayModel.DisplayModelBuilder("Child").build();
		displayModel.setParent(parentModel);

		assertSame(parentModel, displayModel.getParent());
	}

	@Test
	void testSetAndGetChildren() {
		displayModel = new DisplayModel.DisplayModelBuilder("Parent").build();
		List<DisplayModel> children = new ArrayList<>();
		DisplayModel child = new DisplayModel.DisplayModelBuilder("Child").build();
		children.add(child);

		displayModel.setChildren(children);

		assertEquals(1, displayModel.getChildren().size());
		assertSame(child, displayModel.getChildren().get(0));
	}

	// ─── Null and Edge Case Tests ───────────────────────────────────────

	@Test
	void testSetName_null_acceptsNull() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setName(null);

		assertNull(displayModel.getName());
	}

	@Test
	void testSetType_null_acceptsNull() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setType(null);

		assertNull(displayModel.getType());
	}

	@Test
	void testSetSeverity_null_acceptsNull() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setSeverity(null);

		assertNull(displayModel.getSeverity());
	}

	@Test
	void testSetQueryName_null_acceptsNull() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setQueryName(null);

		assertNull(displayModel.getQueryName());
	}

	@Test
	void testSetState_null_acceptsNull() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setState(null);

		assertNull(displayModel.getState());
	}

	@Test
	void testSetParent_null_acceptsNull() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setParent(null);

		assertNull(displayModel.getParent());
	}

	@Test
	void testSetChildren_null_acceptsNull() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setChildren(null);

		assertNull(displayModel.getChildren());
	}

	@Test
	void testSetResult_null_acceptsNull() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();
		displayModel.setResult(null);

		assertNull(displayModel.getResult());
	}

	// ─── State Tests ───────────────────────────────────────────────────

	@Test
	void testBuilder_parentChildRelationship() {
		DisplayModel child = new DisplayModel.DisplayModelBuilder("Child")
			.setParent(parentModel)
			.build();

		assertEquals(parentModel, child.getParent());
	}

	@Test
	void testBuilder_hierarchyWithMultipleChildren() {
		DisplayModel child1 = new DisplayModel.DisplayModelBuilder("Child1").build();
		DisplayModel child2 = new DisplayModel.DisplayModelBuilder("Child2").build();
		List<DisplayModel> children = new ArrayList<>();
		children.add(child1);
		children.add(child2);

		parentModel.setChildren(children);

		assertEquals(2, parentModel.getChildren().size());
		assertTrue(parentModel.getChildren().contains(child1));
		assertTrue(parentModel.getChildren().contains(child2));
	}

	@Test
	void testBuilder_emptyName() {
		displayModel = new DisplayModel.DisplayModelBuilder("").build();

		assertEquals("", displayModel.getName());
	}

	@Test
	void testBuilder_emptyStringsForProperties() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test")
			.setType("")
			.setSeverity("")
			.setQueryName("")
			.setSate("")
			.build();

		assertEquals("", displayModel.getType());
		assertEquals("", displayModel.getSeverity());
		assertEquals("", displayModel.getQueryName());
		assertEquals("", displayModel.getState());
	}

	@Test
	void testDefaultChildren_emptyListInitialized() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();

		assertNotNull(displayModel.getChildren());
		assertTrue(displayModel.getChildren().isEmpty());
	}

	@Test
	void testMultipleSettersOnSameObject() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();

		displayModel.setName("UpdatedName");
		displayModel.setType("UpdatedType");
		displayModel.setSeverity("UpdatedSeverity");

		assertEquals("UpdatedName", displayModel.getName());
		assertEquals("UpdatedType", displayModel.getType());
		assertEquals("UpdatedSeverity", displayModel.getSeverity());
	}

	@Test
	void testBuilderInitializesDefaultEmptyList() {
		displayModel = new DisplayModel.DisplayModelBuilder("Test").build();

		List<DisplayModel> children = displayModel.getChildren();
		assertNotNull(children);
		assertTrue(children.isEmpty());
	}
}
