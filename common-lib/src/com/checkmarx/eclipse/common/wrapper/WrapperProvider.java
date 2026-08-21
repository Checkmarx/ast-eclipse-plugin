package com.checkmarx.eclipse.common.wrapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.codebashing.CodeBashing;
import com.checkmarx.ast.containersrealtime.ContainersRealtimeResults;
import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.ast.learnMore.LearnMore;
import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.ast.predicate.Predicate;
import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.ast.secretsrealtime.SecretsRealtimeResults;

/**
 * Exposes CxWrapper operations to the rest of the plugin. Every call goes
 * through CxWrapperFactory so the wrapper is always built with the current
 * credentials and agent information.
 */
public class WrapperProvider {

	/**
	 * Authenticate with the given credentials, independently of what is currently saved
	 * in Preferences (e.g. the Preferences page "Test Connection" action).
	 * @param apiKey
	 * @param additionalParameters
	 * @return
	 * @throws Exception
	 */
	public String authValidate(String apiKey, String additionalParameters) throws Exception {
		return CxWrapperFactory.build(apiKey, additionalParameters).authValidate();
	}

	/**
	 * Gets the list of projects from the Checkmarx API, optionally filter the results.
	 * @param filter
	 * @return
	 * @throws Exception
	 */
	public List<Project> getProjects(String filter) throws Exception {
		return CxWrapperFactory.build().projectList(filter);
	}

	/**
	 * Fetch a single project directly by its ID.
	 * @param projectId
	 * @return
	 * @throws Exception
	 */
	public Project projectShow(UUID projectId) throws Exception {
		return CxWrapperFactory.build().projectShow(projectId);
	}

	/**
	 * Get branches for a specific project.
	 * @param projectId
	 * @param filter
	 * @return
	 * @throws Exception
	 */
	public List<String> projectBranches(UUID projectId, String filter) throws Exception {
		return CxWrapperFactory.build().projectBranches(projectId, filter);
	}

	/**
	 * Get scans matching the given filter.
	 * @param filter
	 * @return
	 * @throws Exception
	 */
	public List<Scan> scanList(String filter) throws Exception {
		return CxWrapperFactory.build().scanList(filter);
	}

	/**
	 * Get scan information for a specific scan id.
	 * @param scanId
	 * @return
	 * @throws Exception
	 */
	public Scan scanShow(UUID scanId) throws Exception {
		return CxWrapperFactory.build().scanShow(scanId);
	}

	/**
	 * Create a scan for the given source path/project/branch.
	 * @param scanArguments
	 * @param additionalParameters
	 * @return
	 * @throws Exception
	 */
	public Scan scanCreate(Map<String, String> scanArguments, String additionalParameters) throws Exception {
		return CxWrapperFactory.build().scanCreate(scanArguments, additionalParameters);
	}

	/**
	 * Cancel a running scan.
	 * @param scanId
	 * @throws Exception
	 */
	public void scanCancel(String scanId) throws Exception {
		CxWrapperFactory.build().scanCancel(scanId);
	}

	/**
	 * Get results for a specific scan id.
	 * @param scanId
	 * @param agent
	 * @return
	 * @throws Exception
	 */
	public Results results(UUID scanId, String agent) throws Exception {
		return CxWrapperFactory.build().results(scanId, agent);
	}

	/**
	 * Get the codeBashing lessons matching a CWE/language/query name.
	 * @param cwe
	 * @param language
	 * @param queryName
	 * @return
	 * @throws Exception
	 */
	public List<CodeBashing> codeBashingList(String cwe, String language, String queryName) throws Exception {
		return CxWrapperFactory.build().codeBashingList(cwe, language, queryName);
	}

	/**
	 * Get the best fix location among the given nodes.
	 * @param scanId
	 * @param queryId
	 * @param bflNodes
	 * @return
	 * @throws Exception
	 */
	public int getResultsBfl(UUID scanId, String queryId, List<Node> bflNodes) throws Exception {
		return CxWrapperFactory.build().getResultsBfl(scanId, queryId, bflNodes);
	}

	/**
	 * Get triage details for a similarity id.
	 * @param projectId
	 * @param similarityId
	 * @param scanType
	 * @return
	 * @throws Exception
	 */
	public List<Predicate> triageShow(UUID projectId, String similarityId, String scanType) throws Exception {
		return CxWrapperFactory.build().triageShow(projectId, similarityId, scanType);
	}

	/**
	 * Update a vulnerability severity or state.
	 * @param projectId
	 * @param similarityId
	 * @param engineType
	 * @param state
	 * @param comment
	 * @param severity
	 * @throws Exception
	 */
	public void triageUpdate(UUID projectId, String similarityId, String engineType, String state, String comment,
			String severity) throws Exception {
		CxWrapperFactory.build().triageUpdate(projectId, similarityId, engineType, state, comment, severity);
	}

	/**
	 * Triages the states from the Checkmarx API, optionally forcing a refresh of the cached states.
	 * @param forceRefresh
	 * @return
	 * @throws Exception
	 */
	public List<CustomState> triageGetStates(boolean forceRefresh) throws Exception {
		return CxWrapperFactory.build().triageGetStates(forceRefresh);
	}

	/**
	 * Get learn more information for a query.
	 * @param queryId
	 * @return
	 * @throws Exception
	 */
	public List<LearnMore> learnMore(String queryId) throws Exception {
		return CxWrapperFactory.build().learnMore(queryId);
	}

	/**
	 * Check if scanning from the IDE is allowed for the current tenant.
	 * @return
	 * @throws Exception
	 */
	public boolean ideScansEnabled() throws Exception {
		return CxWrapperFactory.build().ideScansEnabled();
	}
	
	/**
	 * Check if AI MCP (Checkmarx One Assist) is enabled for the current tenant.
	 * @return
	 * @throws Exception
	 */
	public boolean isAiMcpServerEnabled(String apiKey, String additionalParameter) throws Exception {
		return CxWrapperFactory.build(apiKey, additionalParameter).aiMcpServerEnabled();
	}
	
	/**
	 * Run a Checkmarx ASCA (AI Security Code Assistant) realtime scan on a file.
	 * @param path
	 * @param latestVersion
	 * @param agent
	 * @param ignoreFilePath
	 * @return
	 * @throws Exception
	 */
	public ScanResult scanAsca(String path, boolean latestVersion, String agent, String ignoreFilePath) throws Exception {
		return CxWrapperFactory.build().ScanAsca(path, latestVersion, agent, ignoreFilePath);
	}

	/**
	 * Run a Checkmarx OSS (Software Composition Analysis) realtime scan on a manifest file.
	 * @param path
	 * @param ignoreFilePath
	 * @return
	 * @throws Exception
	 */
	public OssRealtimeResults ossRealtimeScan(String path, String ignoreFilePath) throws Exception {
		return CxWrapperFactory.build().ossRealtimeScan(path, ignoreFilePath);
	}

	/**
	 * Run a Checkmarx Containers realtime scan on a file.
	 * @param path
	 * @param ignoreFilePath
	 * @return
	 * @throws Exception
	 */
	public ContainersRealtimeResults containersRealtimeScan(String path, String ignoreFilePath) throws Exception {
		return CxWrapperFactory.build().containersRealtimeScan(path, ignoreFilePath);
	}

	/**
	 * Run a Checkmarx IaC realtime scan on a file.
	 * @param path
	 * @param containerTool
	 * @param ignoreFilePath
	 * @return
	 * @throws Exception
	 */
	public IacRealtimeResults iacRealtimeScan(String path, String containerTool, String ignoreFilePath) throws Exception {
		return CxWrapperFactory.build().iacRealtimeScan(path, containerTool, ignoreFilePath);
	}

	/**
	 * Run a Checkmarx Secrets realtime scan on a file.
	 * @param path
	 * @param ignoreFilePath
	 * @return
	 * @throws Exception
	 */
	public SecretsRealtimeResults secretsRealtimeScan(String path, String ignoreFilePath) throws Exception {
		return CxWrapperFactory.build().secretsRealtimeScan(path, ignoreFilePath);
	}
}
