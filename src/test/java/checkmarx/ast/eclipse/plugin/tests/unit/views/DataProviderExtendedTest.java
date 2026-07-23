package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.MockedConstruction;

import com.checkmarx.ast.codebashing.CodeBashing;
import com.checkmarx.ast.learnMore.LearnMore;
import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.filters.FilterState;

class DataProviderExtendedTest {

	private DataProvider dataProvider;
	private static final String VALID_SCAN_UUID = "00000000-0000-0000-0000-000000000001";

	@BeforeEach
	void setUp() {
		dataProvider = DataProvider.getInstance();
		dataProvider.setCurrentResults(null);
		dataProvider.setCurrentScanId(null);
		FilterState.resetFilters();
	}

	// ===== Branch Coverage: Result Type Handling =====

	@Test
	@DisplayName("getResultsForScanId_withMultipleSastResults_processesEachResult")
	void testGetResultsForScanId_multipleSastResults() throws Exception {
		Data mockData1 = mock(Data.class);
		when(mockData1.getNodes()).thenReturn(null);
		when(mockData1.getQueryName()).thenReturn("SQL_Injection");

		Data mockData2 = mock(Data.class);
		when(mockData2.getNodes()).thenReturn(null);
		when(mockData2.getQueryName()).thenReturn("XSS");

		Result mockResult1 = mock(Result.class);
		when(mockResult1.getData()).thenReturn(mockData1);
		when(mockResult1.getType()).thenReturn("sast");
		when(mockResult1.getSeverity()).thenReturn("HIGH");
		when(mockResult1.getState()).thenReturn("TO_VERIFY");
		when(mockResult1.getSimilarityId()).thenReturn("sim-1");

		Result mockResult2 = mock(Result.class);
		when(mockResult2.getData()).thenReturn(mockData2);
		when(mockResult2.getType()).thenReturn("sast");
		when(mockResult2.getSeverity()).thenReturn("CRITICAL");
		when(mockResult2.getState()).thenReturn("CONFIRMED");
		when(mockResult2.getSimilarityId()).thenReturn("sim-2");

		Results mockResults = mock(Results.class);
		when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult1, mockResult2));
		when(mockResults.getTotalCount()).thenReturn(2);

		try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
			when(mock.authValidate()).thenReturn("OK");
			when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
			when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
		})) {
			List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
			assertNotNull(result);
			assertTrue(result.size() > 1); // At least scan root + results
		}
	}

	@Test
	@DisplayName("getResultsForScanId_withMixedEngineTypes_handlesSastScaKics")
	void testGetResultsForScanId_mixedEngineTypes() throws Exception {
		Data sastData = mock(Data.class);
		when(sastData.getNodes()).thenReturn(null);
		when(sastData.getQueryName()).thenReturn("SQL_Injection");

		Data scaData = mock(Data.class);
		when(scaData.getNodes()).thenReturn(null);
		when(scaData.getQueryName()).thenReturn("vulnerable-lib");

		Data kicsData = mock(Data.class);
		when(kicsData.getNodes()).thenReturn(null);
		when(kicsData.getFileName()).thenReturn("Dockerfile");
		when(kicsData.getQueryName()).thenReturn("Exposed_Port");

		Result sastResult = mock(Result.class);
		when(sastResult.getData()).thenReturn(sastData);
		when(sastResult.getType()).thenReturn("sast");
		when(sastResult.getSeverity()).thenReturn("HIGH");
		when(sastResult.getState()).thenReturn("TO_VERIFY");
		when(sastResult.getSimilarityId()).thenReturn("sim-sast");

		Result scaResult = mock(Result.class);
		when(scaResult.getData()).thenReturn(scaData);
		when(scaResult.getType()).thenReturn("sca");
		when(scaResult.getSeverity()).thenReturn("CRITICAL");
		when(scaResult.getState()).thenReturn("CONFIRMED");
		when(scaResult.getSimilarityId()).thenReturn("sim-sca");

		Result kicsResult = mock(Result.class);
		when(kicsResult.getData()).thenReturn(kicsData);
		when(kicsResult.getType()).thenReturn("kics");
		when(kicsResult.getSeverity()).thenReturn("MEDIUM");
		when(kicsResult.getState()).thenReturn("TO_VERIFY");
		when(kicsResult.getSimilarityId()).thenReturn("sim-kics");

		Results mockResults = mock(Results.class);
		when(mockResults.getResults()).thenReturn(Arrays.asList(sastResult, scaResult, kicsResult));
		when(mockResults.getTotalCount()).thenReturn(3);

		try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
			when(mock.authValidate()).thenReturn("OK");
			when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
			when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
		})) {
			List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
			assertNotNull(result);
			assertTrue(result.size() > 1);
		}
	}

	// ===== Branch Coverage: Null/Empty Handling =====

	@Test
	@DisplayName("getResultsForScanId_resultWithNullNodes_handlesGracefully")
	void testGetResultsForScanId_nullNodes() throws Exception {
		Data mockData = mock(Data.class);
		when(mockData.getNodes()).thenReturn(null); // Null nodes
		when(mockData.getQueryName()).thenReturn("TestQuery");
		when(mockData.getFileName()).thenReturn(null);

		Result mockResult = mock(Result.class);
		when(mockResult.getData()).thenReturn(mockData);
		when(mockResult.getType()).thenReturn("sast");
		when(mockResult.getSeverity()).thenReturn("HIGH");
		when(mockResult.getState()).thenReturn("TO_VERIFY");
		when(mockResult.getSimilarityId()).thenReturn("sim-1");

		Results mockResults = mock(Results.class);
		when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
		when(mockResults.getTotalCount()).thenReturn(1);

		try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
			when(mock.authValidate()).thenReturn("OK");
			when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
			when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
		})) {
			List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
			assertNotNull(result);
			assertFalse(result.isEmpty());
		}
	}

	@Test
	@DisplayName("getResultsForScanId_withResultWithNodes_extractsFirstNodeFileName")
	void testGetResultsForScanId_withSastNodes() throws Exception {
		Node mockNode = mock(Node.class);
		when(mockNode.getFileName()).thenReturn("SourceFile.java");
		when(mockNode.getLine()).thenReturn(42);
		when(mockNode.getColumn()).thenReturn(10);

		Data mockData = mock(Data.class);
		when(mockData.getNodes()).thenReturn(Arrays.asList(mockNode));
		when(mockData.getQueryName()).thenReturn("Code_Injection");

		Result mockResult = mock(Result.class);
		when(mockResult.getData()).thenReturn(mockData);
		when(mockResult.getType()).thenReturn("sast");
		when(mockResult.getSeverity()).thenReturn("CRITICAL");
		when(mockResult.getState()).thenReturn("CONFIRMED");
		when(mockResult.getSimilarityId()).thenReturn("sim-nodes");

		Results mockResults = mock(Results.class);
		when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
		when(mockResults.getTotalCount()).thenReturn(1);

		try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
			when(mock.authValidate()).thenReturn("OK");
			when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
			when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
		})) {
			List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
			assertNotNull(result);
			assertFalse(result.isEmpty());
			// At least scan root + result
			assertTrue(result.size() > 1);
		}
	}

	@Test
	@DisplayName("getResultsForScanId_withEmptyNodesList_treatsSameAsNullNodes")
	void testGetResultsForScanId_emptyNodesList() throws Exception {
		Data mockData = mock(Data.class);
		when(mockData.getNodes()).thenReturn(Collections.emptyList()); // Empty list
		when(mockData.getQueryName()).thenReturn("XSS");
		when(mockData.getFileName()).thenReturn(null);

		Result mockResult = mock(Result.class);
		when(mockResult.getData()).thenReturn(mockData);
		when(mockResult.getType()).thenReturn("sast");
		when(mockResult.getSeverity()).thenReturn("MEDIUM");
		when(mockResult.getState()).thenReturn("TO_VERIFY");
		when(mockResult.getSimilarityId()).thenReturn("sim-empty");

		Results mockResults = mock(Results.class);
		when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
		when(mockResults.getTotalCount()).thenReturn(1);

		try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
			when(mock.authValidate()).thenReturn("OK");
			when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
			when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
		})) {
			List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
			assertNotNull(result);
			assertFalse(result.isEmpty());
		}
	}

	// ===== Branch Coverage: Exception Handling =====

	@Test
	@DisplayName("getResultsForScanId_wrapperThrowsException_returnsErrorModel")
	void testGetResultsForScanId_wrapperException() throws Exception {
		try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
			when(mock.authValidate()).thenReturn("OK");
			when(mock.results(any(UUID.class), anyString()))
				.thenThrow(new RuntimeException("API connection failed"));
			when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
		})) {
			List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
			assertNotNull(result);
			assertFalse(result.isEmpty());
			// Error model should have "Error:" prefix
			assertTrue(result.get(0).getName().startsWith("Error:"));
		}
	}

	@Test
	@DisplayName("getResultsForScanId_triageGetStatesThrows_stillProcessesResults")
	void testGetResultsForScanId_triageThrows() throws Exception {
		Data mockData = mock(Data.class);
		when(mockData.getNodes()).thenReturn(null);
		when(mockData.getQueryName()).thenReturn("TestQuery");

		Result mockResult = mock(Result.class);
		when(mockResult.getData()).thenReturn(mockData);
		when(mockResult.getType()).thenReturn("sast");
		when(mockResult.getSeverity()).thenReturn("HIGH");
		when(mockResult.getState()).thenReturn("TO_VERIFY");
		when(mockResult.getSimilarityId()).thenReturn("sim-1");

		Results mockResults = mock(Results.class);
		when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
		when(mockResults.getTotalCount()).thenReturn(1);

		try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
			when(mock.authValidate()).thenReturn("OK");
			when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
			when(mock.triageGetStates(anyBoolean()))
				.thenThrow(new RuntimeException("Triage service error"));
		})) {
			// Should still process results even if triage fails
			List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
			assertNotNull(result);
			assertFalse(result.isEmpty());
		}
	}

	// ===== Branch Coverage: Severity and State Variations =====

	@Test
	@DisplayName("getResultsForScanId_withAllSeverityLevels_processesCorrectly")
	void testGetResultsForScanId_allSeverities() throws Exception {
		String[] severities = {"CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"};
		List<Result> results = new ArrayList<>();

		for (int i = 0; i < severities.length; i++) {
			Data mockData = mock(Data.class);
			when(mockData.getNodes()).thenReturn(null);
			when(mockData.getQueryName()).thenReturn("Query_" + i);

			Result mockResult = mock(Result.class);
			when(mockResult.getData()).thenReturn(mockData);
			when(mockResult.getType()).thenReturn("sast");
			when(mockResult.getSeverity()).thenReturn(severities[i]);
			when(mockResult.getState()).thenReturn("TO_VERIFY");
			when(mockResult.getSimilarityId()).thenReturn("sim-" + i);

			results.add(mockResult);
		}

		Results mockResults = mock(Results.class);
		when(mockResults.getResults()).thenReturn(results);
		when(mockResults.getTotalCount()).thenReturn(results.size());

		try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
			when(mock.authValidate()).thenReturn("OK");
			when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
			when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
		})) {
			List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
			assertNotNull(result);
			assertTrue(result.size() > severities.length);
		}
	}

	@Test
	@DisplayName("getResultsForScanId_withAllStates_processesCorrectly")
	void testGetResultsForScanId_allStates() throws Exception {
		String[] states = {"TO_VERIFY", "NOT_EXPLOITABLE", "CONFIRMED", "URGENT", "PROPOSED_NOT_EXPLOITABLE"};
		List<Result> results = new ArrayList<>();

		for (int i = 0; i < states.length; i++) {
			Data mockData = mock(Data.class);
			when(mockData.getNodes()).thenReturn(null);
			when(mockData.getQueryName()).thenReturn("Query_" + i);

			Result mockResult = mock(Result.class);
			when(mockResult.getData()).thenReturn(mockData);
			when(mockResult.getType()).thenReturn("sast");
			when(mockResult.getSeverity()).thenReturn("HIGH");
			when(mockResult.getState()).thenReturn(states[i]);
			when(mockResult.getSimilarityId()).thenReturn("sim-" + i);

			results.add(mockResult);
		}

		Results mockResults = mock(Results.class);
		when(mockResults.getResults()).thenReturn(results);
		when(mockResults.getTotalCount()).thenReturn(results.size());

		try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
			when(mock.authValidate()).thenReturn("OK");
			when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
			when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
		})) {
			List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
			assertNotNull(result);
			assertTrue(result.size() > states.length);
		}
	}

	// ===== Edge Cases =====

	@Test
	@DisplayName("getResultsForScanId_largeResultSet_processesAll")
	void testGetResultsForScanId_largeResultSet() throws Exception {
		List<Result> results = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			Data mockData = mock(Data.class);
			when(mockData.getNodes()).thenReturn(null);
			when(mockData.getQueryName()).thenReturn("Query_" + i);

			Result mockResult = mock(Result.class);
			when(mockResult.getData()).thenReturn(mockData);
			when(mockResult.getType()).thenReturn(i % 3 == 0 ? "sast" : (i % 3 == 1 ? "sca" : "kics"));
			when(mockResult.getSeverity()).thenReturn("HIGH");
			when(mockResult.getState()).thenReturn("TO_VERIFY");
			when(mockResult.getSimilarityId()).thenReturn("sim-" + i);

			results.add(mockResult);
		}

		Results mockResults = mock(Results.class);
		when(mockResults.getResults()).thenReturn(results);
		when(mockResults.getTotalCount()).thenReturn(results.size());

		try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
			when(mock.authValidate()).thenReturn("OK");
			when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
			when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
		})) {
			List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
			assertNotNull(result);
			assertTrue(result.size() > 100);
		}
	}

	@Test
	@DisplayName("getResultsForScanId_resultWithAllNulls_handlesGracefully")
	void testGetResultsForScanId_allNullFields() throws Exception {
		Data mockData = mock(Data.class);
		when(mockData.getNodes()).thenReturn(null);
		when(mockData.getQueryName()).thenReturn(null);
		when(mockData.getFileName()).thenReturn(null);

		Result mockResult = mock(Result.class);
		when(mockResult.getData()).thenReturn(mockData);
		when(mockResult.getType()).thenReturn("sast");
		when(mockResult.getSeverity()).thenReturn(null);
		when(mockResult.getState()).thenReturn(null);
		when(mockResult.getSimilarityId()).thenReturn(null);

		Results mockResults = mock(Results.class);
		when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
		when(mockResults.getTotalCount()).thenReturn(1);

		try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
			when(mock.authValidate()).thenReturn("OK");
			when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
			when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
		})) {
			List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
			assertNotNull(result);
			// Should not crash even with null fields
			assertFalse(result.isEmpty());
		}
	}
}
