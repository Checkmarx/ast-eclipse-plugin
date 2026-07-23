package checkmarx.ast.eclipse.plugin.tests.unit.views.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.actions.CxBaseAction;

class CxBaseActionTest {

	@Mock
	private DisplayModel mockRootModel;

	@Mock
	private TreeViewer mockResultsTree;

	@Mock
	private Action mockAction;

	private CxBaseAction testAction;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);

		testAction = new CxBaseAction(mockRootModel, mockResultsTree) {
			@Override
			public Action createAction() {
				return mockAction;
			}
		};
	}

	@Test
	void testConstructor_withValidParameters() {
		CxBaseAction action = new CxBaseAction(mockRootModel, mockResultsTree) {
			@Override
			public Action createAction() {
				return null;
			}
		};
		assertNotNull(action);
	}

	@Test
	void testConstructor_storesRootModel() {
		assertEquals(mockRootModel, testAction.rootModel);
	}

	@Test
	void testConstructor_storesResultsTree() {
		assertEquals(mockResultsTree, testAction.resultsTree);
	}

	@Test
	void testCreateAction_isAbstract() {
		assertNotNull(testAction);
		testAction.createAction();
		verify(mockAction, never()).run();
	}

	@Test
	void testCreateAction_returnsCorrectAction() {
		Action result = testAction.createAction();
		assertEquals(mockAction, result);
	}

	@Test
	void testRootModelPublicField() {
		DisplayModel newModel = mock(DisplayModel.class);
		testAction.rootModel = newModel;
		assertEquals(newModel, testAction.rootModel);
	}

	@Test
	void testResultsTreePublicField() {
		TreeViewer newViewer = mock(TreeViewer.class);
		testAction.resultsTree = newViewer;
		assertEquals(newViewer, testAction.resultsTree);
	}

	@Test
	void testMultipleInstances_independentState() {
		DisplayModel model1 = mock(DisplayModel.class);
		DisplayModel model2 = mock(DisplayModel.class);
		TreeViewer viewer1 = mock(TreeViewer.class);
		TreeViewer viewer2 = mock(TreeViewer.class);

		CxBaseAction action1 = new CxBaseAction(model1, viewer1) {
			@Override
			public Action createAction() {
				return null;
			}
		};

		CxBaseAction action2 = new CxBaseAction(model2, viewer2) {
			@Override
			public Action createAction() {
				return null;
			}
		};

		assertEquals(model1, action1.rootModel);
		assertEquals(model2, action2.rootModel);
		assertEquals(viewer1, action1.resultsTree);
		assertEquals(viewer2, action2.resultsTree);
	}

	@Test
	void testConstructor_withNullRootModel() {
		CxBaseAction action = new CxBaseAction(null, mockResultsTree) {
			@Override
			public Action createAction() {
				return null;
			}
		};
		assertNull(action.rootModel);
		assertEquals(mockResultsTree, action.resultsTree);
	}

	@Test
	void testConstructor_withNullResultsTree() {
		CxBaseAction action = new CxBaseAction(mockRootModel, null) {
			@Override
			public Action createAction() {
				return null;
			}
		};
		assertEquals(mockRootModel, action.rootModel);
		assertNull(action.resultsTree);
	}

	@Test
	void testConstructor_withBothNullParameters() {
		CxBaseAction action = new CxBaseAction(null, null) {
			@Override
			public Action createAction() {
				return null;
			}
		};
		assertNull(action.rootModel);
		assertNull(action.resultsTree);
	}

	@Test
	void testFieldAccess_afterConstruction() {
		testAction.rootModel = mockRootModel;
		testAction.resultsTree = mockResultsTree;

		assertSame(mockRootModel, testAction.rootModel);
		assertSame(mockResultsTree, testAction.resultsTree);
	}
}
