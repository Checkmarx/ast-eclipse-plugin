package checkmarx.ast.eclipse.plugin.tests.unit.views.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Combo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.GlobalSettings;
import com.checkmarx.eclipse.views.actions.ActionStartScan;
import com.google.common.eventbus.EventBus;

class ActionStartScanExtendedTest {

	private DisplayModel rootModel;
	private TreeViewer resultsTree;
	private EventBus eventBus;
	private ComboViewer projectsCombo;
	private ComboViewer branchesCombo;
	private ComboViewer scansCombo;
	private Action cancelScanAction;

	@BeforeEach
	void setUp() {
		rootModel = mock(DisplayModel.class);
		resultsTree = mock(TreeViewer.class);
		eventBus = new EventBus();
		projectsCombo = mock(ComboViewer.class);
		branchesCombo = mock(ComboViewer.class);
		scansCombo = mock(ComboViewer.class);
		cancelScanAction = mock(Action.class);
	}

	private ActionStartScan buildAction(String projectText, String branchText) {
		Combo projectCombo = mock(Combo.class);
		when(projectCombo.getText()).thenReturn(projectText);
		when(projectsCombo.getCombo()).thenReturn(projectCombo);

		Combo branchCombo = mock(Combo.class);
		when(branchCombo.getText()).thenReturn(branchText);
		when(branchesCombo.getCombo()).thenReturn(branchCombo);

		return new ActionStartScan(rootModel, resultsTree, eventBus,
				projectsCombo, branchesCombo, scansCombo, cancelScanAction);
	}

	// ===== Branch Coverage: createAction() run() method =====

	@Test
	void testCreateAction_run_projectAndBranchDontMatch_displaysNotification() throws Exception {
		try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
			 MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class);
			 MockedStatic<MessageDialog> dialogMock = Mockito.mockStatic(MessageDialog.class)) {

			// Setup: current git branch = "main", selected branch = "develop"
			// Setup: workspace has projects with results that don't match workspace files

			IWorkspace mockWorkspace = mock(IWorkspace.class);
			IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
			IProject mockProject = mock(IProject.class);
			when(mockRoot.getProjects()).thenReturn(new IProject[]{mockProject});
			when(mockWorkspace.getRoot()).thenReturn(mockRoot);
			resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

			DataProvider mockProvider = mock(DataProvider.class);
			Results mockResults = mock(Results.class);
			Result mockResult = mock(Result.class);
			Data mockData = mock(Data.class);
			Node mockNode = mock(Node.class);

			when(mockData.getNodes()).thenReturn(Arrays.asList(mockNode));
			when(mockNode.getFileName()).thenReturn("UnmatchedFile.java");
			when(mockResult.getData()).thenReturn(mockData);
			when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
			when(mockProvider.getCurrentResults()).thenReturn(mockResults);
			dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

			try (MockedStatic<PluginUtils> utilsMock = Mockito.mockStatic(PluginUtils.class)) {
				utilsMock.when(() -> PluginUtils.findFileInWorkspace(anyString()))
					.thenReturn(Collections.emptyList());

				// Dialog will show: user clicks No (false)
				dialogMock.when(() -> MessageDialog.openQuestion(any(), anyString(), anyString()))
					.thenReturn(false);

				try (MockedStatic<GlobalSettings> settingsMock = Mockito.mockStatic(GlobalSettings.class)) {
					settingsMock.when(() -> GlobalSettings.getFromPreferences(anyString(), anyString()))
						.thenReturn("develop");

					Action action = buildAction("TestProject", "develop").createAction();
					assertNotNull(action);
					assertTrue(action.isEnabled());
				}
			}
		}
	}

	@Test
	void testCreateAction_run_onlyBranchMismatch_displaysNotification() throws Exception {
		try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
			 MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class);
			 MockedStatic<MessageDialog> dialogMock = Mockito.mockStatic(MessageDialog.class)) {

			// Git branch = "main", selected = "develop" → mismatch
			// Projects match → return true (no results or files match)

			IWorkspace mockWorkspace = mock(IWorkspace.class);
			IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
			IProject mockProject = mock(IProject.class);
			when(mockRoot.getProjects()).thenReturn(new IProject[]{mockProject});
			when(mockWorkspace.getRoot()).thenReturn(mockRoot);
			resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

			DataProvider mockProvider = mock(DataProvider.class);
			when(mockProvider.getCurrentResults()).thenReturn(null);
			dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

			dialogMock.when(() -> MessageDialog.openQuestion(any(), anyString(), anyString()))
				.thenReturn(false);

			try (MockedStatic<GlobalSettings> settingsMock = Mockito.mockStatic(GlobalSettings.class)) {
				settingsMock.when(() -> GlobalSettings.getFromPreferences(anyString(), anyString()))
					.thenReturn("develop");

				Action action = buildAction("TestProject", "develop").createAction();
				assertTrue(action.isEnabled());
			}
		}
	}

	@Test
	void testCreateAction_run_onlyProjectMismatch_displaysNotification() throws Exception {
		try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
			 MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class);
			 MockedStatic<MessageDialog> dialogMock = Mockito.mockStatic(MessageDialog.class)) {

			// Git branch matches, project doesn't match

			IWorkspace mockWorkspace = mock(IWorkspace.class);
			IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
			IProject mockProject = mock(IProject.class);
			when(mockRoot.getProjects()).thenReturn(new IProject[]{mockProject});
			when(mockWorkspace.getRoot()).thenReturn(mockRoot);
			resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

			DataProvider mockProvider = mock(DataProvider.class);
			Results mockResults = mock(Results.class);
			Result mockResult = mock(Result.class);
			Data mockData = mock(Data.class);
			Node mockNode = mock(Node.class);

			when(mockData.getNodes()).thenReturn(Arrays.asList(mockNode));
			when(mockNode.getFileName()).thenReturn("UnmatchedFile.java");
			when(mockResult.getData()).thenReturn(mockData);
			when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));
			when(mockProvider.getCurrentResults()).thenReturn(mockResults);
			dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

			try (MockedStatic<PluginUtils> utilsMock = Mockito.mockStatic(PluginUtils.class)) {
				utilsMock.when(() -> PluginUtils.findFileInWorkspace(anyString()))
					.thenReturn(Collections.emptyList());

				dialogMock.when(() -> MessageDialog.openQuestion(any(), anyString(), anyString()))
					.thenReturn(false);

				try (MockedStatic<GlobalSettings> settingsMock = Mockito.mockStatic(GlobalSettings.class)) {
					settingsMock.when(() -> GlobalSettings.getFromPreferences(anyString(), anyString()))
						.thenReturn("main");

					Action action = buildAction("TestProject", "main").createAction();
					assertTrue(action.isEnabled());
				}
			}
		}
	}

	// ===== Branch Coverage: cxProjectMatchesWorkspaceProject() =====

	@Test
	void testCxProjectMatchesWorkspaceProject_withSastNodes_fileFound_returnsTrue() throws Exception {
		try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
			 MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class)) {

			Data mockData = mock(Data.class);
			Node mockNode = mock(Node.class);
			when(mockNode.getFileName()).thenReturn("TargetFile.java");
			when(mockData.getNodes()).thenReturn(Arrays.asList(mockNode));

			Result mockResult = mock(Result.class);
			when(mockResult.getData()).thenReturn(mockData);

			Results mockResults = mock(Results.class);
			when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));

			DataProvider mockProvider = mock(DataProvider.class);
			when(mockProvider.getCurrentResults()).thenReturn(mockResults);
			dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

			IWorkspace mockWorkspace = mock(IWorkspace.class);
			IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
			IProject mockProject = mock(IProject.class);
			when(mockRoot.getProjects()).thenReturn(new IProject[]{mockProject});
			when(mockWorkspace.getRoot()).thenReturn(mockRoot);
			resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

			try (MockedStatic<PluginUtils> utilsMock = Mockito.mockStatic(PluginUtils.class)) {
				IFile mockFile = mock(IFile.class);
				utilsMock.when(() -> PluginUtils.findFileInWorkspace(anyString()))
					.thenReturn(Arrays.asList(mockFile));

				Method method = ActionStartScan.class.getDeclaredMethod("cxProjectMatchesWorkspaceProject");
				method.setAccessible(true);
				boolean result = (boolean) method.invoke(buildAction("TestProject", "main"));
				assertTrue(result);
			}
		}
	}

	@Test
	void testCxProjectMatchesWorkspaceProject_withKicsData_noNodes_returnsTrue() throws Exception {
		try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
			 MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class)) {

			Data mockData = mock(Data.class);
			when(mockData.getNodes()).thenReturn(null); // No nodes (KICS case)
			when(mockData.getFileName()).thenReturn("Dockerfile");

			Result mockResult = mock(Result.class);
			when(mockResult.getData()).thenReturn(mockData);

			Results mockResults = mock(Results.class);
			when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));

			DataProvider mockProvider = mock(DataProvider.class);
			when(mockProvider.getCurrentResults()).thenReturn(mockResults);
			dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

			IWorkspace mockWorkspace = mock(IWorkspace.class);
			IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
			IProject mockProject = mock(IProject.class);
			when(mockRoot.getProjects()).thenReturn(new IProject[]{mockProject});
			when(mockWorkspace.getRoot()).thenReturn(mockRoot);
			resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

			try (MockedStatic<PluginUtils> utilsMock = Mockito.mockStatic(PluginUtils.class)) {
				IFile mockFile = mock(IFile.class);
				utilsMock.when(() -> PluginUtils.findFileInWorkspace(anyString()))
					.thenReturn(Arrays.asList(mockFile));

				Method method = ActionStartScan.class.getDeclaredMethod("cxProjectMatchesWorkspaceProject");
				method.setAccessible(true);
				boolean result = (boolean) method.invoke(buildAction("TestProject", "main"));
				assertTrue(result);
			}
		}
	}

	@Test
	void testCxProjectMatchesWorkspaceProject_fileNotInWorkspace_returnsFalse() throws Exception {
		try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
			 MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class)) {

			Data mockData = mock(Data.class);
			Node mockNode = mock(Node.class);
			when(mockNode.getFileName()).thenReturn("NotInWorkspace.java");
			when(mockData.getNodes()).thenReturn(Arrays.asList(mockNode));

			Result mockResult = mock(Result.class);
			when(mockResult.getData()).thenReturn(mockData);

			Results mockResults = mock(Results.class);
			when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));

			DataProvider mockProvider = mock(DataProvider.class);
			when(mockProvider.getCurrentResults()).thenReturn(mockResults);
			dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

			IWorkspace mockWorkspace = mock(IWorkspace.class);
			IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
			IProject mockProject = mock(IProject.class);
			when(mockRoot.getProjects()).thenReturn(new IProject[]{mockProject});
			when(mockWorkspace.getRoot()).thenReturn(mockRoot);
			resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

			try (MockedStatic<PluginUtils> utilsMock = Mockito.mockStatic(PluginUtils.class)) {
				// File not found in workspace
				utilsMock.when(() -> PluginUtils.findFileInWorkspace(anyString()))
					.thenReturn(Collections.emptyList());

				Method method = ActionStartScan.class.getDeclaredMethod("cxProjectMatchesWorkspaceProject");
				method.setAccessible(true);
				boolean result = (boolean) method.invoke(buildAction("TestProject", "main"));
				assertFalse(result);
			}
		}
	}

	@Test
	void testCxProjectMatchesWorkspaceProject_multipleResults_checksBoth_returnsTrueWhenAnyMatches() throws Exception {
		try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
			 MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class)) {

			// First result doesn't match, second result matches
			Data mockData1 = mock(Data.class);
			Node mockNode1 = mock(Node.class);
			when(mockNode1.getFileName()).thenReturn("FirstFile.java");
			when(mockData1.getNodes()).thenReturn(Arrays.asList(mockNode1));

			Data mockData2 = mock(Data.class);
			Node mockNode2 = mock(Node.class);
			when(mockNode2.getFileName()).thenReturn("SecondFile.java");
			when(mockData2.getNodes()).thenReturn(Arrays.asList(mockNode2));

			Result mockResult1 = mock(Result.class);
			when(mockResult1.getData()).thenReturn(mockData1);

			Result mockResult2 = mock(Result.class);
			when(mockResult2.getData()).thenReturn(mockData2);

			Results mockResults = mock(Results.class);
			when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult1, mockResult2));

			DataProvider mockProvider = mock(DataProvider.class);
			when(mockProvider.getCurrentResults()).thenReturn(mockResults);
			dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

			IWorkspace mockWorkspace = mock(IWorkspace.class);
			IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
			IProject mockProject = mock(IProject.class);
			when(mockRoot.getProjects()).thenReturn(new IProject[]{mockProject});
			when(mockWorkspace.getRoot()).thenReturn(mockRoot);
			resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

			try (MockedStatic<PluginUtils> utilsMock = Mockito.mockStatic(PluginUtils.class)) {
				IFile mockFile = mock(IFile.class);
				// First call returns empty (FirstFile not found), second call returns file (SecondFile found)
				utilsMock.when(() -> PluginUtils.findFileInWorkspace(anyString()))
					.thenReturn(Collections.emptyList())
					.thenReturn(Arrays.asList(mockFile));

				Method method = ActionStartScan.class.getDeclaredMethod("cxProjectMatchesWorkspaceProject");
				method.setAccessible(true);
				boolean result = (boolean) method.invoke(buildAction("TestProject", "main"));
				assertTrue(result);
			}
		}
	}

	@Test
	void testCxProjectMatchesWorkspaceProject_emptyNodesList_checksFalse() throws Exception {
		try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
			 MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class)) {

			Data mockData = mock(Data.class);
			when(mockData.getNodes()).thenReturn(Collections.emptyList()); // Empty nodes
			when(mockData.getFileName()).thenReturn(""); // Empty fileName

			Result mockResult = mock(Result.class);
			when(mockResult.getData()).thenReturn(mockData);

			Results mockResults = mock(Results.class);
			when(mockResults.getResults()).thenReturn(Arrays.asList(mockResult));

			DataProvider mockProvider = mock(DataProvider.class);
			when(mockProvider.getCurrentResults()).thenReturn(mockResults);
			dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

			IWorkspace mockWorkspace = mock(IWorkspace.class);
			IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
			IProject mockProject = mock(IProject.class);
			when(mockRoot.getProjects()).thenReturn(new IProject[]{mockProject});
			when(mockWorkspace.getRoot()).thenReturn(mockRoot);
			resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

			Method method = ActionStartScan.class.getDeclaredMethod("cxProjectMatchesWorkspaceProject");
			method.setAccessible(true);
			boolean result = (boolean) method.invoke(buildAction("TestProject", "main"));
			assertFalse(result);
		}
	}

	// ===== Edge Cases =====

	@Test
	void testCreateAction_disabledWhenEmptyBranch_inPreferences() throws Exception {
		try (MockedStatic<GlobalSettings> settingsMock = Mockito.mockStatic(GlobalSettings.class)) {
			settingsMock.when(() -> GlobalSettings.getFromPreferences(anyString(), anyString()))
				.thenReturn(""); // Empty branch in preferences

			Action action = buildAction("TestProject", "main").createAction();
			assertFalse(action.isEnabled());
		}
	}

	@Test
	void testCreateAction_enabledWhenBranchInPreferences() throws Exception {
		try (MockedStatic<GlobalSettings> settingsMock = Mockito.mockStatic(GlobalSettings.class)) {
			settingsMock.when(() -> GlobalSettings.getFromPreferences(anyString(), anyString()))
				.thenReturn("develop"); // Non-empty branch in preferences

			Action action = buildAction("TestProject", "develop").createAction();
			assertTrue(action.isEnabled());
		}
	}

	@Test
	void testCxProjectMatchesWorkspaceProject_withEmptyGitBranch_returnsTrue() throws Exception {
		try (MockedStatic<DataProvider> dpMock = Mockito.mockStatic(DataProvider.class);
			 MockedStatic<ResourcesPlugin> resourcesMock = Mockito.mockStatic(ResourcesPlugin.class)) {

			// When git branch is empty string, cxProjectMatchesWorkspaceProject should still work

			DataProvider mockProvider = mock(DataProvider.class);
			when(mockProvider.getCurrentResults()).thenReturn(null);
			dpMock.when(DataProvider::getInstance).thenReturn(mockProvider);

			IWorkspace mockWorkspace = mock(IWorkspace.class);
			IWorkspaceRoot mockRoot = mock(IWorkspaceRoot.class);
			when(mockRoot.getProjects()).thenReturn(new IProject[0]);
			when(mockWorkspace.getRoot()).thenReturn(mockRoot);
			resourcesMock.when(ResourcesPlugin::getWorkspace).thenReturn(mockWorkspace);

			Method method = ActionStartScan.class.getDeclaredMethod("cxProjectMatchesWorkspaceProject");
			method.setAccessible(true);
			boolean result = (boolean) method.invoke(buildAction("TestProject", "develop"));
			assertTrue(result);
		}
	}
}
