package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.checkmarx.eclipse.enums.PluginListenerType;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.PluginListenerDefinition;

class PluginListenerDefinitionTest {

	private PluginListenerDefinition listenerDefinition;
	@Mock
	private DisplayModel mockDisplayModel;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	// ─── Constructor Tests ───────────────────────────────────────────

	@Test
	void testConstructor_withValidParameters_createsInstance() {
		List<DisplayModel> results = new ArrayList<>();
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, results);

		assertNotNull(listenerDefinition);
		assertEquals(PluginListenerType.LOAD_RESULTS_FOR_SCAN, listenerDefinition.getListenerType());
		assertSame(results, listenerDefinition.getResutls());
	}

	@Test
	void testConstructor_withNullResults_acceptsNull() {
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, null);

		assertNotNull(listenerDefinition);
		assertNull(listenerDefinition.getResutls());
	}

	@Test
	void testConstructor_withEmptyResultsList() {
		List<DisplayModel> results = new ArrayList<>();
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, results);

		assertNotNull(listenerDefinition.getResutls());
		assertTrue(listenerDefinition.getResutls().isEmpty());
	}

	@Test
	void testConstructor_withMultipleResults() {
		DisplayModel result1 = new DisplayModel.DisplayModelBuilder("Result1").build();
		DisplayModel result2 = new DisplayModel.DisplayModelBuilder("Result2").build();
		List<DisplayModel> results = Arrays.asList(result1, result2);

		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, results);

		assertEquals(2, listenerDefinition.getResutls().size());
		assertTrue(listenerDefinition.getResutls().contains(result1));
		assertTrue(listenerDefinition.getResutls().contains(result2));
	}

	// ─── ListenerType Tests ───────────────────────────────────────────

	@Test
	void testGetListenerType_returnsLoadResultsForScan() {
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, new ArrayList<>());

		assertEquals(PluginListenerType.LOAD_RESULTS_FOR_SCAN, listenerDefinition.getListenerType());
	}

	@Test
	void testGetListenerType_returnsFilterChanged() {
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.FILTER_CHANGED, new ArrayList<>());

		assertEquals(PluginListenerType.FILTER_CHANGED, listenerDefinition.getListenerType());
	}

	@Test
	void testSetListenerType_updatesValue() {
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, new ArrayList<>());
		listenerDefinition.setListenerType(PluginListenerType.GET_RESULTS);

		assertEquals(PluginListenerType.GET_RESULTS, listenerDefinition.getListenerType());
	}

	// ─── Results Tests ───────────────────────────────────────────────

	@Test
	void testGetResults_returnsProvidedList() {
		List<DisplayModel> results = new ArrayList<>();
		DisplayModel model = new DisplayModel.DisplayModelBuilder("TestModel").build();
		results.add(model);

		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, results);

		assertEquals(1, listenerDefinition.getResutls().size());
		assertSame(model, listenerDefinition.getResutls().get(0));
	}

	@Test
	void testSetResults_updatesResultsList() {
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, new ArrayList<>());
		List<DisplayModel> newResults = Arrays.asList(mockDisplayModel);

		listenerDefinition.setResutls(newResults);

		assertEquals(1, listenerDefinition.getResutls().size());
		assertSame(mockDisplayModel, listenerDefinition.getResutls().get(0));
	}

	@Test
	void testSetResults_null_acceptsNull() {
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, new ArrayList<>());
		listenerDefinition.setResutls(null);

		assertNull(listenerDefinition.getResutls());
	}

	@Test
	void testSetResults_emptyList_replacesWithEmpty() {
		List<DisplayModel> initialResults = Arrays.asList(mockDisplayModel);
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, initialResults);

		listenerDefinition.setResutls(new ArrayList<>());

		assertNotNull(listenerDefinition.getResutls());
		assertTrue(listenerDefinition.getResutls().isEmpty());
	}

	// ─── Combination Tests ───────────────────────────────────────────

	@Test
	void testSetListenerType_andResults_together() {
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, new ArrayList<>());
		List<DisplayModel> newResults = Arrays.asList(mockDisplayModel);

		listenerDefinition.setListenerType(PluginListenerType.GET_RESULTS);
		listenerDefinition.setResutls(newResults);

		assertEquals(PluginListenerType.GET_RESULTS, listenerDefinition.getListenerType());
		assertEquals(1, listenerDefinition.getResutls().size());
	}

	@Test
	void testMultipleSetOperations_finalStateCorrect() {
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, new ArrayList<>());

		listenerDefinition.setListenerType(PluginListenerType.GET_RESULTS);
		List<DisplayModel> results = Arrays.asList(mockDisplayModel);
		listenerDefinition.setResutls(results);
		listenerDefinition.setListenerType(PluginListenerType.LOAD_RESULTS_FOR_SCAN);

		assertEquals(PluginListenerType.LOAD_RESULTS_FOR_SCAN, listenerDefinition.getListenerType());
		assertEquals(1, listenerDefinition.getResutls().size());
	}

	@Test
	void testConstructor_preservesListReference() {
		List<DisplayModel> results = new ArrayList<>();
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, results);

		DisplayModel model = new DisplayModel.DisplayModelBuilder("Added").build();
		results.add(model);

		assertEquals(1, listenerDefinition.getResutls().size());
	}

	@Test
	void testWithCollectionsEmptyList() {
		listenerDefinition = new PluginListenerDefinition(PluginListenerType.LOAD_RESULTS_FOR_SCAN, Collections.emptyList());

		assertNotNull(listenerDefinition.getResutls());
		assertTrue(listenerDefinition.getResutls().isEmpty());
	}
}
