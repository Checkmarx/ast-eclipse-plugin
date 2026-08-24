package checkmarx.ast.eclipse.plugin.tests.unit.wrapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.common.wrapper.WrapperProvider;

class WrapperProviderTest {

    private final WrapperProvider wrapperProvider = new WrapperProvider();

    @Test
    void testIsAiMcpServerEnabled_forwardsCredentialsAndReturnsWrapperResult() throws Exception {
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class,
                (mock, context) -> when(mock.aiMcpServerEnabled()).thenReturn(true))) {

            boolean result = wrapperProvider.isAiMcpServerEnabled("api-key", "--param");

            assertTrue(result);
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void testIsAiMcpServerEnabled_propagatesFalseWhenDisabled() throws Exception {
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class,
                (mock, context) -> when(mock.aiMcpServerEnabled()).thenReturn(false))) {

            boolean result = wrapperProvider.isAiMcpServerEnabled("api-key", "--param");

            assertFalse(result);
        }
    }

    @Test
    void testGetProjects_forwardsLimitAndReturnsWrapperResult() throws Exception {
        Project mockProject = mock(Project.class);
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class,
                (mock, context) -> when(mock.projectList("limit=10")).thenReturn(List.of(mockProject)))) {

            List<Project> projects = wrapperProvider.getProjects("limit=10");

            assertEquals(1, projects.size());
            assertSame(mockProject, projects.get(0));
        }
    }

    @Test
    void testTriageGetStates_propagatesExceptionFromWrapper() throws Exception {
        try (MockedConstruction<CxWrapper> mocked = mockConstruction(CxWrapper.class,
                (mock, context) -> when(mock.triageGetStates(false)).thenThrow(new RuntimeException("boom")))) {

            assertThrows(RuntimeException.class, () -> wrapperProvider.triageGetStates(false));
        }
    }
}
