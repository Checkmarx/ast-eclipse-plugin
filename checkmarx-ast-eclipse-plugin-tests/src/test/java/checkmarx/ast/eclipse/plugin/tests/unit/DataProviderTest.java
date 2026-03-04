package checkmarx.ast.eclipse.plugin.tests.unit;

import com.checkmarx.eclipse.views.DataProvider;

import checkmarx.ast.eclipse.plugin.tests.common.Environment;

import com.checkmarx.ast.results.Results;
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

    @BeforeEach
    void setUp() {
        dataProvider = DataProvider.getInstance();
        dataProvider.setCurrentResults(null);
        dataProvider.setCurrentScanId(null);
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
        System.out.println("Projects found: " + projects.size());
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
}