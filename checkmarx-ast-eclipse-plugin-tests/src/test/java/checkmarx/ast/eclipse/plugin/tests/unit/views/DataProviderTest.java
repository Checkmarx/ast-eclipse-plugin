package checkmarx.ast.eclipse.plugin.tests.unit.views;

import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.filters.FilterState;

import checkmarx.ast.eclipse.plugin.tests.common.Environment;

import com.checkmarx.ast.codebashing.CodeBashing;
import com.checkmarx.ast.learnMore.LearnMore;
import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.ast.predicate.Predicate;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataProviderTest {
	
    protected static final String SCAN_ID = Environment.SCAN_ID;
    private static final String TEST_PROJECT = "pedrompflopes/WebGoat";


	
    DataProvider dataProvider;

    private static final String VALID_SCAN_UUID = "00000000-0000-0000-0000-000000000001";

    @BeforeEach
    void setUp() {
        dataProvider = DataProvider.getInstance();
        dataProvider.setCurrentResults(null);
        dataProvider.setCurrentScanId(null);
        FilterState.resetFilters();
    }

    @Test
    void testSingletonInstance() {
        DataProvider instance1 = DataProvider.getInstance();
        DataProvider instance2 = DataProvider.getInstance();
        assertSame(instance1, instance2, "DataProvider should be singleton");
    }

    @Test
    void testSetAndGetCurrentScanId() {
        String scanId = "scan-123";
        dataProvider.setCurrentScanId(scanId);
        assertEquals(scanId, dataProvider.getCurrentScanId());
    }

    @Test
    void testSetAndGetCurrentResults() {
        Results mockResults = mock(Results.class);
        dataProvider.setCurrentResults(mockResults);
        assertEquals(mockResults, dataProvider.getCurrentResults());
    }

    @Test
    void testGetProjectsReturnsList() throws Exception {
        List<Project> projects = dataProvider.getProjects();
        assertNotNull(projects);
    }

    @Test
    void testGetProjectsByNameReturnsList() throws Exception {
        List<Project> projects = dataProvider.getProjects(TEST_PROJECT);
        assertNotNull(projects);
    }

    @Test
    void testGetBranchesForProjectReturnsList() {
        List<String> branches = dataProvider.getBranchesForProject("e7478063-976c-4c79-b762-93074dabad24");
        assertNotNull(branches);
    }

    @Test
    void testGetScansForProjectReturnsList() {
        List<Scan> scans = dataProvider.getScansForProject("main");
        assertNotNull(scans);
    }

    @Test
    void testGetResultsForScanIdReturnsList() {
        List<?> results = dataProvider.getResultsForScanId(SCAN_ID);
        assertNotNull(results);
    }

    @Test
    void testSortResultsReturnsList() {
        List<?> sorted = dataProvider.sortResults();
        assertNotNull(sorted);
    }

    @Test
    void testContainsResults() {
        dataProvider.setCurrentResults(null);
        assertFalse(dataProvider.containsResults());
    }

    @Test
    void testGetTriageShowReturnsList() throws Exception {
        List<Predicate> triage = dataProvider.getTriageShow(UUID.randomUUID(), "simId", "SAST");
        assertNotNull(triage);
    }

    @Test
    void testTriageUpdateDoesNotThrow() throws Exception {

        UUID projectId = UUID.randomUUID();
        String similarityId = "-930213981328";

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(
                CxWrapper.class,
                (mock, context) -> {
                    doNothing().when(mock).triageUpdate(
                            any(UUID.class),
                            anyString(),
                            anyString(),
                            anyString(),
                            anyString(),
                            anyString()
                    );
                })) {
            DataProvider provider = DataProvider.getInstance();
            assertDoesNotThrow(() ->
                    provider.triageUpdate(
                            projectId,
                            similarityId,
                            "SAST",
                            "TO_VERIFY",
                            "comment",
                            "HIGH"
                    )
            );
        }
    }
    
    @Test
    void testContainsResultsTrue() {

        Results results = mock(Results.class);

        List<com.checkmarx.ast.results.result.Result> list = new ArrayList<>();
        list.add(mock(com.checkmarx.ast.results.result.Result.class));

        when(results.getResults()).thenReturn(list);

        dataProvider.setCurrentResults(results);

        assertTrue(dataProvider.containsResults());
    }

    @Test
    void testGetStatesForEngineSAST() {

        List<String> states = dataProvider.getStatesForEngine("SAST");

        assertNotNull(states);
    }

    @Test
    void testGetStatesForEngineOther() {

        List<String> states = dataProvider.getStatesForEngine("SCA");

        assertNotNull(states);
    }

    @Test
    void testGetCustomStates() {

        List<String> states = dataProvider.getCustomStates();

        assertNotNull(states);
    }

    @Test
    void testGetResultsForScanIdInvalid() {

        List<?> results = dataProvider.getResultsForScanId("invalid-uuid");

        assertNotNull(results);
    }

    @Test
    void testGetBranchesForProjectEmpty() {

        List<String> branches = dataProvider.getBranchesForProject("");

        assertNotNull(branches);
    }

    @Test
    void testGetScansForProjectNullBranch() {

        List<Scan> scans = dataProvider.getScansForProject(null);

        assertNotNull(scans);
    }

    @Test
    void testGetScanInformationException() {

        assertThrows(Exception.class, () -> {
            dataProvider.getScanInformation("invalid-scan");
        });
    }

    @Test
    void testGetResultsForScanId_emptyResults_returnsEmptyList() throws Exception {
        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Collections.emptyList());
        when(mockResults.getTotalCount()).thenReturn(0);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
        })) {
            List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void testGetResultsForScanId_withSastResult_coversProcessResultsPipeline() throws Exception {
        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("SQL_Injection");

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
            // root node wraps the scan
            assertTrue(result.get(0).getName().contains(VALID_SCAN_UUID));
        }
    }

    @Test
    void testGetResultsForScanId_withScaResult_coversScaPath() throws Exception {
        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("vulnerable-lib");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sca");
        when(mockResult.getSeverity()).thenReturn("CRITICAL");
        when(mockResult.getState()).thenReturn("CONFIRMED");
        when(mockResult.getSimilarityId()).thenReturn("sim-sca");

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
    void testGetResultsForScanId_withKicsResult_coversKicsPath() throws Exception {
        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("Dockerfile_Exposed_Port");
        when(mockData.getFileName()).thenReturn("Dockerfile");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("kics");
        when(mockResult.getSeverity()).thenReturn("MEDIUM");
        when(mockResult.getState()).thenReturn("TO_VERIFY");
        when(mockResult.getSimilarityId()).thenReturn("sim-kics");

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
    void testGetResultsForScanId_wrapperThrows_returnsErrorModel() throws Exception {
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.results(any(UUID.class), anyString())).thenThrow(new RuntimeException("network error"));
            when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
        })) {
            List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
            assertNotNull(result);
            assertFalse(result.isEmpty());
            // error path returns a message model
            assertTrue(result.get(0).getName().startsWith("Error:"));
        }
    }

    @Test
    void testSortResults_afterLoadingMockedResults_returnsNonEmptyList() throws Exception {
        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("XSS");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("TO_VERIFY");
        when(mockResult.getSimilarityId()).thenReturn("sim-xss");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
        when(mockResults.getTotalCount()).thenReturn(1);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
        })) {
            dataProvider.getResultsForScanId(VALID_SCAN_UUID);
            List<DisplayModel> sorted = dataProvider.sortResults();
            assertNotNull(sorted);
            assertFalse(sorted.isEmpty());
        }
    }

    @Test
    void testSortResults_groupByStateName_coversStatePath() throws Exception {
        FilterState.resetFilters();
        FilterState.groupBySeverity = false;
        FilterState.groupByStateName = true;
        FilterState.groupByQueryName = false;

        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("Injection");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("TO_VERIFY");
        when(mockResult.getSimilarityId()).thenReturn("sim-inj");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
        when(mockResults.getTotalCount()).thenReturn(1);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
        })) {
            dataProvider.getResultsForScanId(VALID_SCAN_UUID);
            List<DisplayModel> sorted = dataProvider.sortResults();
            assertNotNull(sorted);
        }
    }

    @Test
    void testSortResults_groupByQueryName_coversQueryPath() throws Exception {
        FilterState.resetFilters();
        FilterState.groupBySeverity = false;
        FilterState.groupByStateName = false;
        FilterState.groupByQueryName = true;

        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("BufferOverflow");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("CRITICAL");
        when(mockResult.getState()).thenReturn("CONFIRMED");
        when(mockResult.getSimilarityId()).thenReturn("sim-bo");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
        when(mockResults.getTotalCount()).thenReturn(1);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
        })) {
            dataProvider.getResultsForScanId(VALID_SCAN_UUID);
            List<DisplayModel> sorted = dataProvider.sortResults();
            assertNotNull(sorted);
        }
    }

    @Test
    void testGetResultsForScanId_multipleResults_allScanners() throws Exception {
        Data sastData = mock(Data.class);
        when(sastData.getNodes()).thenReturn(null);
        when(sastData.getQueryName()).thenReturn("SQLI");

        Result sastResult = mock(Result.class);
        when(sastResult.getData()).thenReturn(sastData);
        when(sastResult.getType()).thenReturn("sast");
        when(sastResult.getSeverity()).thenReturn("HIGH");
        when(sastResult.getState()).thenReturn("TO_VERIFY");
        when(sastResult.getSimilarityId()).thenReturn("sim-s");

        Data scaData = mock(Data.class);
        when(scaData.getNodes()).thenReturn(null);
        when(scaData.getQueryName()).thenReturn("log4j");

        Result scaResult = mock(Result.class);
        when(scaResult.getData()).thenReturn(scaData);
        when(scaResult.getType()).thenReturn("sca");
        when(scaResult.getSeverity()).thenReturn("CRITICAL");
        when(scaResult.getState()).thenReturn("TO_VERIFY");
        when(scaResult.getSimilarityId()).thenReturn("sim-sc");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(sastResult, scaResult));
        when(mockResults.getTotalCount()).thenReturn(2);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
        })) {
            List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
            assertNotNull(result);
            assertFalse(result.isEmpty());
            // root model children = SAST node + SCA node
            List<DisplayModel> children = result.get(0).getChildren();
            assertTrue(children.size() >= 2);
        }
    }

    @Test
    void testGetScansForProject_withMockedWrapper_returnsList() throws Exception {
        Scan mockScan = mock(Scan.class);
        when(mockScan.getId()).thenReturn("scan-123");

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.scanList(anyString())).thenReturn(Arrays.asList(mockScan));
        })) {
            List<Scan> scans = dataProvider.getScansForProject("main");
            assertNotNull(scans);
            assertFalse(scans.isEmpty());
            assertEquals("scan-123", scans.get(0).getId());
        }
    }

    @Test
    void testIsScanAllowed_withMockedWrapper_returnsValue() throws Exception {
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.ideScansEnabled()).thenReturn(true);
        })) {
            boolean result = dataProvider.isScanAllowed();
            assertTrue(result);
        }
    }

    @Test
    void testGetResultsForScanId_customStateResult_coversCustomStatePath() throws Exception {
        CustomState customState = mock(CustomState.class);
        when(customState.getName()).thenReturn("IN_PROGRESS");

        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("RaceCondition");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("IN_PROGRESS");
        when(mockResult.getSimilarityId()).thenReturn("sim-rc");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
        when(mockResults.getTotalCount()).thenReturn(1);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            // projectId is null so getAllStatesFromPlatform returns early, no triageGetStates call
        })) {
            List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
            assertNotNull(result);
        }
    }

    @Test
    void testGetProjects_withMockedWrapper_returnsProjects() throws Exception {
        Project mockProject = mock(Project.class);
        when(mockProject.getName()).thenReturn("MyProject");

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("SUCCESS");
            when(mock.projectList(anyString())).thenReturn(Arrays.asList(mockProject));
        })) {
            List<Project> projects = dataProvider.getProjects();
            assertNotNull(projects);
            assertFalse(projects.isEmpty());
            assertEquals("MyProject", projects.get(0).getName());
        }
    }

    @Test
    void testGetProjectsByName_withMockedWrapper_returnsProjects() throws Exception {
        Project mockProject = mock(Project.class);
        when(mockProject.getName()).thenReturn("FilteredProject");

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("SUCCESS");
            when(mock.projectList(anyString())).thenReturn(Arrays.asList(mockProject));
        })) {
            List<Project> projects = dataProvider.getProjects("FilteredProject");
            assertNotNull(projects);
            assertFalse(projects.isEmpty());
            assertEquals("FilteredProject", projects.get(0).getName());
        }
    }

    @Test
    void testGetBranchesForProject_withMockedWrapper_returnsBranches() throws Exception {
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.projectBranches(any(UUID.class), anyString())).thenReturn(Arrays.asList("main", "develop"));
        })) {
            List<String> branches = dataProvider.getBranchesForProject(VALID_SCAN_UUID);
            assertNotNull(branches);
            assertFalse(branches.isEmpty());
            assertTrue(branches.contains("main"));
        }
    }

    @Test
    void testGetScanInformation_withMockedWrapper_returnsScan() throws Exception {
        Scan mockScan = mock(Scan.class);
        when(mockScan.getId()).thenReturn(VALID_SCAN_UUID);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.scanShow(any(UUID.class))).thenReturn(mockScan);
        })) {
            Scan scan = dataProvider.getScanInformation(VALID_SCAN_UUID);
            assertNotNull(scan);
            assertEquals(VALID_SCAN_UUID, scan.getId());
        }
    }

    @Test
    void testGetTriageShow_withMockedWrapper_returnsList() throws Exception {
        Predicate mockPredicate = mock(Predicate.class);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.triageShow(any(UUID.class), anyString(), anyString())).thenReturn(Arrays.asList(mockPredicate));
        })) {
            List<Predicate> result = dataProvider.getTriageShow(UUID.randomUUID(), "sim-123", "SAST");
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    void testGetTriageShow_kicsType_coversKicsBranch() throws Exception {
        Predicate mockPredicate = mock(Predicate.class);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.triageShow(any(UUID.class), anyString(), anyString())).thenReturn(Arrays.asList(mockPredicate));
        })) {
            List<Predicate> result = dataProvider.getTriageShow(UUID.randomUUID(), "sim-kics", "kics");
            assertNotNull(result);
        }
    }

    @Test
    void testTriageUpdate_whenCxWrapperThrows_rethrowsException() throws Exception {
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            doThrow(new RuntimeException("update failed")).when(mock).triageUpdate(
                any(UUID.class), anyString(), anyString(), anyString(), anyString(), anyString()
            );
        })) {
            assertThrows(Exception.class, () -> dataProvider.triageUpdate(
                UUID.randomUUID(), "sim-1", "SAST", "TO_VERIFY", "comment", "HIGH"
            ));
        }
    }

    @Test
    void testCreateScan_withMockedWrapper_returnsScan() throws Exception {
        Scan mockScan = mock(Scan.class);
        when(mockScan.getId()).thenReturn("new-scan-id");

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.scanCreate(anyMap(), anyString())).thenReturn(mockScan);
        })) {
            Scan scan = dataProvider.createScan("/path/to/source", "MyProject", "main");
            assertNotNull(scan);
            assertEquals("new-scan-id", scan.getId());
        }
    }

    @Test
    void testCancelScan_withMockedWrapper_doesNotThrow() throws Exception {
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            doNothing().when(mock).scanCancel(anyString());
        })) {
            assertDoesNotThrow(() -> dataProvider.cancelScan(VALID_SCAN_UUID));
        }
    }

    @Test
    void testLearnMore_withMockedWrapper_returnsList() throws Exception {
        LearnMore mockLearnMore = mock(LearnMore.class);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.learnMore(anyString())).thenReturn(Arrays.asList(mockLearnMore));
        })) {
            List<LearnMore> result = dataProvider.learnMore("query-123");
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    void testGetBestFixLocation_withMockedWrapper_returnsNodeIndex() throws Exception {
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.getResultsBfl(any(UUID.class), anyString(), anyList())).thenReturn(2);
        })) {
            int idx = dataProvider.getBestFixLocation(UUID.randomUUID(), "query-1", new ArrayList<>());
            assertEquals(2, idx);
        }
    }

    @Test
    void testGetResultsForScanId_withProjectIdSet_coversAllStatesFromPlatform() throws Exception {
        CustomState customState = mock(CustomState.class);
        when(customState.getName()).thenReturn("TO_VERIFY");

        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("XSSInjection");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("TO_VERIFY");
        when(mockResult.getSimilarityId()).thenReturn("sim-xss-2");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
        when(mockResults.getTotalCount()).thenReturn(1);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.projectBranches(any(UUID.class), anyString())).thenReturn(Arrays.asList("main"));
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenReturn(Arrays.asList(customState));
        })) {
            // Sets projectId on the DataProvider instance
            dataProvider.getBranchesForProject(VALID_SCAN_UUID);
            // Now getAllStatesFromPlatform runs (currentScanId and projectId both non-null)
            List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    void testSortResults_allGroupBy_coversNestedQueryNamePath() throws Exception {
        FilterState.resetFilters();
        FilterState.groupBySeverity = true;
        FilterState.groupByStateName = true;
        FilterState.groupByQueryName = true;

        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("SQLInjection");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("TO_VERIFY");
        when(mockResult.getSimilarityId()).thenReturn("sim-sql-2");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
        when(mockResults.getTotalCount()).thenReturn(1);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
        })) {
            dataProvider.getResultsForScanId(VALID_SCAN_UUID);
            List<DisplayModel> sorted = dataProvider.sortResults();
            assertNotNull(sorted);
            assertFalse(sorted.isEmpty());
        }
    }

    @Test
    void testGetCustomStates_withNonPredefinedPlatformState_returnsCustomState() throws Exception {
        CustomState customState = mock(CustomState.class);
        when(customState.getName()).thenReturn("MY_CUSTOM_STATE");

        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("TestQuery");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("MY_CUSTOM_STATE");
        when(mockResult.getSimilarityId()).thenReturn("sim-custom-state");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
        when(mockResults.getTotalCount()).thenReturn(1);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.projectBranches(any(UUID.class), anyString())).thenReturn(Arrays.asList("main"));
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenReturn(Arrays.asList(customState));
        })) {
            dataProvider.getBranchesForProject(VALID_SCAN_UUID);
            dataProvider.getResultsForScanId(VALID_SCAN_UUID);

            List<String> customStates = dataProvider.getCustomStates();
            assertNotNull(customStates);
            assertTrue(customStates.contains("MY_CUSTOM_STATE"),
                    "MY_CUSTOM_STATE is not predefined and should appear in custom states");
        }
    }

    @Test
    void testGetCustomStates_withOnlyPredefinedStates_returnsEmpty() throws Exception {
        CustomState predefinedState = mock(CustomState.class);
        when(predefinedState.getName()).thenReturn("TO_VERIFY");

        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("Predefined");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("TO_VERIFY");
        when(mockResult.getSimilarityId()).thenReturn("sim-predefined");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
        when(mockResults.getTotalCount()).thenReturn(1);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.projectBranches(any(UUID.class), anyString())).thenReturn(Arrays.asList("main"));
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenReturn(Arrays.asList(predefinedState));
        })) {
            dataProvider.getBranchesForProject(VALID_SCAN_UUID);
            dataProvider.getResultsForScanId(VALID_SCAN_UUID);

            List<String> customStates = dataProvider.getCustomStates();
            assertNotNull(customStates);
            assertFalse(customStates.contains("TO_VERIFY"),
                    "TO_VERIFY is predefined and must be filtered out");
        }
    }

    @Test
    void testGetCodeBashingLink_withMockedWrapper_returnsLink() throws Exception {
        CodeBashing mockCodeBashing = mock(CodeBashing.class);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.codeBashingList(anyString(), anyString(), anyString()))
                .thenReturn(Arrays.asList(mockCodeBashing));
        })) {
            CodeBashing result = dataProvider.getCodeBashingLink("cwe-89", "java", "SQL_Injection");
            assertNotNull(result);
        }
    }

    @Test
    void testGetResultsForScanId_withNodes_coversNodeDisplayNamePath() throws Exception {
        Node mockNode = mock(Node.class);
        when(mockNode.getFileName()).thenReturn("/src/com/example/Foo.java");
        when(mockNode.getLine()).thenReturn(42);

        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(Arrays.asList(mockNode));
        when(mockData.getQueryName()).thenReturn("BufferOverflow");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("TO_VERIFY");
        when(mockResult.getSimilarityId()).thenReturn("sim-node-test");

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
    void testGetResultsForScanId_withHtmlEntities_coversCleanHtmlEntitiesPath() throws Exception {
        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("XSSInjection");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("TO_VERIFY");
        when(mockResult.getSimilarityId()).thenReturn("sim-html-test");
        when(mockResult.getDescription()).thenReturn("&lt;script&gt;&amp;test&#34;");
        when(mockResult.getDescriptionHTML()).thenReturn("&lt;b&gt;Bold&lt;/b&gt;");

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
    void testSortResults_groupBySeverityAndStateName_noQueryName_coversNestedStatePath() throws Exception {
        FilterState.resetFilters();
        FilterState.groupBySeverity = true;
        FilterState.groupByStateName = true;
        FilterState.groupByQueryName = false;

        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("PathTraversal");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("MEDIUM");
        when(mockResult.getState()).thenReturn("CONFIRMED");
        when(mockResult.getSimilarityId()).thenReturn("sim-nested-state");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
        when(mockResults.getTotalCount()).thenReturn(1);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenReturn(Collections.emptyList());
        })) {
            dataProvider.getResultsForScanId(VALID_SCAN_UUID);
            List<DisplayModel> sorted = dataProvider.sortResults();
            assertNotNull(sorted);
            assertFalse(sorted.isEmpty());
        }
    }

    @Test
    void testGetStatesForEngine_SAST_withPlatformStatesLoaded_returnsNonEmptyList() throws Exception {
        CustomState customState = mock(CustomState.class);
        when(customState.getName()).thenReturn("IN_PROGRESS");

        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("Deserialization");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("IN_PROGRESS");
        when(mockResult.getSimilarityId()).thenReturn("sim-states-test");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
        when(mockResults.getTotalCount()).thenReturn(1);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.projectBranches(any(UUID.class), anyString())).thenReturn(Arrays.asList("main"));
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenReturn(Arrays.asList(customState));
        })) {
            dataProvider.getBranchesForProject(VALID_SCAN_UUID);
            dataProvider.getResultsForScanId(VALID_SCAN_UUID);

            List<String> states = dataProvider.getStatesForEngine("SAST");
            assertNotNull(states);
            assertFalse(states.isEmpty());
            assertTrue(states.contains("IN_PROGRESS"));
        }
    }

    @Test
    void testContainsResults_withNonNullResultsButNullGetResults_returnsFalse() {
        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(null);
        dataProvider.setCurrentResults(mockResults);
        assertFalse(dataProvider.containsResults());
    }

    @Test
    void testContainsResults_withEmptyResultsList_returnsFalse() {
        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Collections.emptyList());
        dataProvider.setCurrentResults(mockResults);
        assertFalse(dataProvider.containsResults());
    }

    @Test
    void testGetBranchesForProject_wrapperThrows_returnsEmptyList() throws Exception {
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.projectBranches(any(UUID.class), anyString()))
                    .thenThrow(new RuntimeException("branch fetch failed"));
        })) {
            List<String> branches = dataProvider.getBranchesForProject(VALID_SCAN_UUID);
            assertNotNull(branches);
            assertTrue(branches.isEmpty());
        }
    }

    @Test
    void testGetScansForProject_wrapperThrows_returnsEmptyList() throws Exception {
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.scanList(anyString())).thenThrow(new RuntimeException("scan list failed"));
        })) {
            List<com.checkmarx.ast.scan.Scan> scans = dataProvider.getScansForProject("main");
            assertNotNull(scans);
            assertTrue(scans.isEmpty());
        }
    }

    @Test
    void testGetResultsForScanId_triageGetStatesThrows_handlesGracefully() throws Exception {
        Data mockData = mock(Data.class);
        when(mockData.getNodes()).thenReturn(null);
        when(mockData.getQueryName()).thenReturn("TestQuery");

        Result mockResult = mock(Result.class);
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResult.getType()).thenReturn("sast");
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("TO_VERIFY");
        when(mockResult.getSimilarityId()).thenReturn("sim-triage-throw");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
        when(mockResults.getTotalCount()).thenReturn(1);

        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class, (mock, ctx) -> {
            when(mock.authValidate()).thenReturn("OK");
            when(mock.projectBranches(any(UUID.class), anyString())).thenReturn(Arrays.asList("main"));
            when(mock.results(any(UUID.class), anyString())).thenReturn(mockResults);
            when(mock.triageGetStates(anyBoolean())).thenThrow(new RuntimeException("states fetch failed"));
        })) {
            dataProvider.getBranchesForProject(VALID_SCAN_UUID);
            List<DisplayModel> result = dataProvider.getResultsForScanId(VALID_SCAN_UUID);
            assertNotNull(result);
        }
    }

    @Test
    void testGetResultsForScanId_withKicsResultAndEmptyScannerTypes_coversAddResultsEmptyPaths() throws Exception {
        Data kicsData = mock(Data.class);
        when(kicsData.getNodes()).thenReturn(null);
        when(kicsData.getQueryName()).thenReturn("Exposed_Port");
        when(kicsData.getFileName()).thenReturn("Dockerfile");

        Result kicsResult = mock(Result.class);
        when(kicsResult.getData()).thenReturn(kicsData);
        when(kicsResult.getType()).thenReturn("kics");
        when(kicsResult.getSeverity()).thenReturn("HIGH");
        when(kicsResult.getState()).thenReturn("TO_VERIFY");
        when(kicsResult.getSimilarityId()).thenReturn("sim-kics-add");

        Results mockResults = mock(Results.class);
        when(mockResults.getResults()).thenReturn(Arrays.asList(kicsResult));
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
}