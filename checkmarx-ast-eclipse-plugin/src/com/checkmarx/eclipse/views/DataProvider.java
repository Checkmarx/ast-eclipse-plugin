package com.checkmarx.eclipse.views;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.codebashing.CodeBashing;
import com.checkmarx.ast.learnMore.LearnMore;
import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.ast.predicate.Predicate;
import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.results.ReportFormat;
import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.runner.Authenticator;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.filters.FilterState;

public class DataProvider {
	
	private static final List<String> SEVERITY_ORDER = Arrays.asList("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO");
	
	private static final String LIMIT_FILTER="limit=10000";		
	private static final String FILTER_SCANS_FOR_PROJECT = "project-id=%s,branch=%s,limit=10000,statuses=Completed";
	
	private static final String SAST_TREE_NAME = "SAST (%d)";
	private static final String SCA_TREE_NAME = "SCA (%d)";
	private static final String KICS_TREE_NAME = "KICS (%d)";
	private static final String RESULTS_TREE_NAME = "%s (%d Issues)";
	private static final String ECLIPSE_AGENT = "Eclipse";
	private static DataProvider _dataProvider = null;

	public static final AtomicBoolean abort = new AtomicBoolean(false);
	
	private Results currentResults;
	private String currentScanId;
	private String projectId;
	private List<DisplayModel> currentResultsTransformed;
	private List<String> platformStates = new ArrayList<>();
	
	/**
	 * Singleton data provider instance
	 * 
	 * @return
	 */
	public static final DataProvider getInstance() {
		if(_dataProvider == null) {
			_dataProvider = new DataProvider();
		}
		
		return _dataProvider;
	}

	public String getCurrentScanId() {
		return currentScanId;
	}

	public void setCurrentScanId(String currentScanId) {
		this.currentScanId = currentScanId;
	}

	public Results getCurrentResults() {
		return currentResults;
	}

	public void setCurrentResults(Results currentResults) {
		this.currentResults = currentResults;
	}

	/**
	 * Get One projects
	 * 
	 * @return
	 * @throws Exception 
	 */
	public List<Project> getProjects() throws Exception {
		List<Project> projectList = new ArrayList<Project>();
		
		CxWrapper cxWrapper = authenticateWithAST();
		
		if (cxWrapper != null) {
			try {
				projectList = cxWrapper.projectList(LIMIT_FILTER);

			} catch (IOException | InterruptedException | CxException e) {
				CxLogger.error(String.format(PluginConstants.ERROR_GETTING_PROJECTS, e.getMessage()), e);
			}
		}

		return projectList;
	}
	
	/**
	 * Get the codeBashing link
	 * @throws Exception 
	 */
	
	public CodeBashing getCodeBashingLink(String cwe, String language, String queryName) throws CxException, Exception  {
		CxWrapper cxWrapper = getWrapper();
		
		return cxWrapper.codeBashingList(cwe, language, queryName).get(0);
	}
	
	/**
	 * Get branches for a specific project
	 * 
	 * @param projectId
	 * @return
	 */
	public List<String> getBranchesForProject(String projectId) {
		this.projectId = projectId;
		List<String> branchList = new ArrayList<String>();
		
			try {
				CxWrapper cxWrapper = getWrapper();
				
				if(!StringUtils.isEmptyOrNull(projectId)) {
					branchList = cxWrapper.projectBranches(UUID.fromString(projectId), PluginConstants.EMPTY_STRING);
				}

			} catch (Exception e) {
				CxLogger.error(String.format(PluginConstants.ERROR_GETTING_BRANCHES, projectId, e.getMessage()), e);
			}
	

		return branchList;
	}
	
	/**
	 * Get scans for a specific project based on a provided branch
	 * 
	 * @param branch
	 * @return
	 */
	public List<Scan> getScansForProject(String branch) {
		List<Scan> scanList = new ArrayList<>();
		
		try {
			String filter = String.format(FILTER_SCANS_FOR_PROJECT, projectId, branch);
			CxWrapper cxWrapper = getWrapper();
			scanList = cxWrapper.scanList(filter);

		} catch (Exception e) {
			CxLogger.error(String.format(PluginConstants.ERROR_GETTING_SCANS, projectId, branch, e.getMessage()), e);
		}
	
		return scanList;
	}
	
	/**
	 * Authenticate to One with current credentials
	 * @throws Exception 
	 */
	private static CxWrapper authenticateWithAST() throws Exception {
		CxWrapper cxWrapper = null;
		
		try {
			
			cxWrapper = getWrapper();
			String validationResult = cxWrapper.authValidate();

			CxLogger.info(String.format(PluginConstants.INFO_AUTHENTICATION_STATUS, validationResult));

		} catch (CxException e) {
			CxLogger.error(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, e.getMessage()), e);
			throw new Exception(e);
		}
		
		return cxWrapper;
	}
	
	/**
	 * Get results for a specific scan id
	 * 
	 * @param scanId
	 * @return
	 */
	public List<DisplayModel> getResultsForScanId(String scanId) {
		abort.set(false);
		Results scanResults = null;
		
		setCurrentScanId(scanId);
		
		try {
			platformStates = getAllStatesFromPlatform();
		} catch (Exception e) {
			CxLogger.warning("Failed to fetch all platform states on scan load: " + e.getMessage());
		}

		try {						
			CxLogger.info(String.format(PluginConstants.INFO_FETCHING_RESULTS, scanId));
			CxWrapper cxWrapper = getWrapper();
			scanResults = cxWrapper.results(UUID.fromString(scanId), ECLIPSE_AGENT);
			setCurrentResults(scanResults);
			CxLogger.info(String.format(PluginConstants.INFO_SCAN_RESULTS_COUNT, scanResults.getTotalCount()));

		} catch (Exception e) {
			CxLogger.error(String.format(PluginConstants.ERROR_GETTING_RESULTS, scanId, e.getMessage()), e);
			return  Arrays.asList(PluginUtils.message("Error: " + e.getMessage()));
		}

		return processResults(scanResults, scanId);
	}

	/**
	 * Get scan information for a specific scan id
	 * 
	 * @param scanId
	 * @return
	 * @throws Exception 
	 */
	public Scan getScanInformation(String scanId) throws Exception {
		Scan scan = null;
		
		CxWrapper cxWrapper = getWrapper();
		
		try {
			CxLogger.info(String.format(PluginConstants.INFO_GETTING_SCAN_INFO, scanId));
			scan = cxWrapper.scanShow(UUID.fromString(scanId));
		} catch (Exception e) {
			CxLogger.error(String.format(PluginConstants.ERROR_GETTING_SCAN_INFO, e.getMessage()), e);
			throw new Exception(e);
		}

		return scan;
	}
	
	public List<String> getAvailableStates() {
		return platformStates != null ? platformStates : Collections.emptyList();
	}

	public List<String> getStatesForEngine(String engineType) {
		if ("SAST".equalsIgnoreCase(engineType)) {
			return platformStates != null ? platformStates : Collections.emptyList();
		} else {
			return new ArrayList<>(com.checkmarx.eclipse.enums.State.values().keySet());
		}
	}

	/**
	 * Process results to be displayed in the tree
	 * 
	 * @param scanResults
	 * @param scanId
	 * @return
	 */
	private List<DisplayModel> processResults(Results scanResults, String scanId) {
		if(scanResults == null || scanResults.getResults() == null || scanResults.getResults().isEmpty()) {
			return Collections.emptyList();
		}

		List<Result> resultsList = scanResults.getResults();
		
		// Add Checkmarx vulnerabilities to Problems View
		PluginUtils.addVulnerabilitiesToProblemsView(resultsList);

		// transform all the results at once to avoid multiple transformation steps
		currentResultsTransformed = resultsList.stream().map(resultItem -> transform(resultItem)).collect(Collectors.toList());

		// Divide all the results by scanner type
		Map<String, List<DisplayModel>> filteredResultsByScannerType = filterResultsByScannerType(currentResultsTransformed);

		//filter the results by enabled state
		filterStates(filteredResultsByScannerType);
		
		// build results based on selected filters
		return buildResults(scanId, filteredResultsByScannerType);
	}
	
	/**
	 * Sort results as they may have changed due to a triage update
	 * 
	 * @return
	 */
	public List<DisplayModel> sortResults(){
		// Divide all the results by scanner type
		Map<String, List<DisplayModel>> filteredResultsByScannerType = filterResultsByScannerType(currentResultsTransformed);
		// filter based on filter states
		filterStates(filteredResultsByScannerType);	
		// build results based on selected filters
		return buildResults(getCurrentScanId(), filteredResultsByScannerType);
	}
	
	/**
	 * Build results to be displayed in the tree
	 * 
	 * @param scanId
	 * @param filteredResultsByScannerType
	 * @return
	 */
	private List<DisplayModel> buildResults(String scanId, Map<String, List<DisplayModel>> filteredResultsByScannerType){
		
		// Filter results by selected severities
		filteredResultsByScannerType.entrySet().stream().forEach(entry -> entry.getValue().removeIf(result -> !FilterState.isSeverityEnabled(result.getSeverity())));
				
		if(FilterState.groupBySeverity) {
			groupResultsBySeverity(filteredResultsByScannerType);
		}
		
		if(FilterState.groupByStateName) {
			groupResultsByStateName(filteredResultsByScannerType);
		}
		
		if(FilterState.groupByQueryName) {
			groupResultsByQueryName(filteredResultsByScannerType);
		}
		
		
		return addResults(scanId, filteredResultsByScannerType);
	}
	
	private void filterStates(Map<String, List<DisplayModel>> filteredResultsByScannerType) {
		filteredResultsByScannerType.entrySet().stream().forEach(entry -> 
		{	
		entry.getValue().removeIf(x -> !FilterState.isFilterStateEnabled(x.getState().trim()));
		});
	
	}

	
	/**
	 *  Evaluates if each engine has results and adds it to the final map
	 * 
	 * @param scanId
	 * @param filteredResultsByScannerType
	 * @return
	 */
	private List<DisplayModel> addResults(String scanId, Map<String, List<DisplayModel>> filteredResultsByScannerType) {
		List<DisplayModel> returnList = new ArrayList<>();
		List<DisplayModel> results = new ArrayList<>();
		
		boolean constainsSASTResults = filteredResultsByScannerType.containsKey(PluginConstants.SAST) && filteredResultsByScannerType.get(PluginConstants.SAST).size() > 0;
		List<DisplayModel> sastResults = constainsSASTResults ? filteredResultsByScannerType.get(PluginConstants.SAST) : Collections.emptyList();
		int sastCount = constainsSASTResults ? getParentCounter(sastResults) : 0;
		
		boolean constainsSCAResults = filteredResultsByScannerType.containsKey(PluginConstants.SCA_DEPENDENCY) && filteredResultsByScannerType.get(PluginConstants.SCA_DEPENDENCY).size() > 0;
		List<DisplayModel> scaResults = constainsSCAResults ? filteredResultsByScannerType.get(PluginConstants.SCA_DEPENDENCY) : Collections.emptyList();
		int scaCount = constainsSCAResults ? getParentCounter(scaResults) : 0;
		
		boolean constainsKICKSResults = filteredResultsByScannerType.containsKey(PluginConstants.KICS_INFRASTRUCTURE) && filteredResultsByScannerType.get(PluginConstants.KICS_INFRASTRUCTURE).size() > 0;
		List<DisplayModel> kicsResults = constainsKICKSResults ? filteredResultsByScannerType.get(PluginConstants.KICS_INFRASTRUCTURE) : Collections.emptyList();
		int kicsCount = constainsKICKSResults ? getParentCounter(kicsResults) : 0;
		
		if (sastCount > 0) {
			DisplayModel sastModel = new DisplayModel.DisplayModelBuilder(String.format(SAST_TREE_NAME, sastCount)).setChildren(sastResults).build();
			results.add(sastModel);
		}
		
		if (scaCount > 0) {
			DisplayModel scaModel = new DisplayModel.DisplayModelBuilder(String.format(SCA_TREE_NAME, scaCount)).setChildren(scaResults).build();
			results.add(scaModel);
		}
		
		if (kicsCount > 0) {
			DisplayModel kicsModel = new DisplayModel.DisplayModelBuilder(String.format(KICS_TREE_NAME, kicsCount)).setChildren(kicsResults).build();
			results.add(kicsModel);
		}
		
		int totalCount = sastCount + scaCount + kicsCount;
		DisplayModel projectModel = new DisplayModel.DisplayModelBuilder(String.format(RESULTS_TREE_NAME, scanId, totalCount)).setChildren(results).build();
		
		returnList.add(projectModel);

		return returnList;
	}


	/**
	 * Creates a clean Result object with decoded HTML entities
	 *
	 * @param resultItem Original result object
	 * @return New Result object with cleaned values
	 */
	private Result createCleanResult(Result resultItem) {
	    String cleanDescription = resultItem.getDescription() != null ?
	        cleanHtmlEntities(resultItem.getDescription()) : null;

	    String cleanDescriptionHTML = resultItem.getDescriptionHTML() != null ?
	        cleanHtmlEntities(resultItem.getDescriptionHTML()) : null;

	    return new Result(
	        resultItem.getType(),
	        resultItem.getLabel(),
	        resultItem.getId(),
	        resultItem.getSimilarityId(),
	        resultItem.getStatus(),
	        resultItem.getState(),
	        resultItem.getSeverity(),
	        resultItem.getCreated(),
	        resultItem.getFirstFoundAt(),
	        resultItem.getFoundAt(),
	        resultItem.getFirstScan(),
	        resultItem.getFirstScanId(),
	        resultItem.getPublishedAt(),
	        resultItem.getRecommendations(),
	        cleanDescription,
	        cleanDescriptionHTML,
	        resultItem.getData(),
	        resultItem.getComments(),
	        resultItem.getVulnerabilityDetails(),
	        resultItem.getScaType()
	    );
	}

	/**
	 * Helper method to clean HTML entities from text
	 *
	 * @param input String containing HTML entities
	 * @return Cleaned string with decoded HTML entities
	 */
	private String cleanHtmlEntities(String input) {
	    if (input == null) return null;
	    return input
	        .replace("&#34;", "\"")
	        .replace("&quot;", "\"")
	        .replace("&#39;", "'")
	        .replace("&#35;", "#")
	        .replace("&#38;", "&")
	        .replace("&lt;", "<")
	        .replace("&gt;", ">");
	}

	/**
	 * Creates a Display Model which represents each result
	 *
	 * @param resultItem Result object to transform
	 * @return DisplayModel representing the result
	 */
	private DisplayModel transform(Result resultItem) {
	    List<Node> nodes = Optional.ofNullable(resultItem.getData().getNodes()).orElse(Collections.emptyList());

	    Result cleanResult = createCleanResult(resultItem);

		String queryName = cleanResult.getData().getQueryName() != null ?
				cleanResult.getData().getQueryName() :
				cleanResult.getSimilarityId();

	    String displayName = queryName;
	    if (nodes.size() > 0) {
	        Node node = nodes.get(0);
	        displayName += String.format(" (%s:%d)", new File(node.getFileName()).getName(), node.getLine());
	    }

	    return new DisplayModel.DisplayModelBuilder(displayName)
	            .setSeverity(cleanResult.getSeverity())
	            .setType(cleanResult.getType())
	            .setResult(cleanResult)
	            .setSate(cleanResult.getState())
	            .setQueryName(queryName)
	            .build();
	}

	/**
	 * Group results by scanner type
	 *
	 * @param allResultsTransformed
	 * @return
	 */
	private Map<String, List<DisplayModel>> filterResultsByScannerType(List<DisplayModel> allResultsTransformed) {
		Map<String, List<DisplayModel>> filteredMap = new HashMap<>();

		for (DisplayModel transformedResult : allResultsTransformed) {

			String scanType = transformedResult.getType();

			if (filteredMap.containsKey(scanType)) {
				filteredMap.get(scanType).add(transformedResult);
			} else {
				filteredMap.put(scanType, new ArrayList<>(Arrays.asList(transformedResult)));
			}

		}
		return filteredMap;
	}
	
	/**
	 * Group vulnerabilities by severity
	 * 
	 * @param filteredResultsByScannerType
	 */
	private void groupResultsBySeverity(Map<String, List<DisplayModel>> filteredResultsByScannerType) {		
		filteredResultsByScannerType.entrySet().stream().forEach(entry -> {
			
			Map<String, List<DisplayModel>> mapBySeverity = new LinkedHashMap<>();
			String scanner = entry.getKey();
			List<DisplayModel> vulnerabilities = entry.getValue();
			
			for (DisplayModel result : vulnerabilities) {
				String severityType = result.getSeverity();
				
				if (mapBySeverity.containsKey(severityType)) {
					 mapBySeverity.get(severityType).add(result);
				} else {
					mapBySeverity.put(severityType, new ArrayList<>(Arrays.asList(result)));
				}
			}
			
			 Map<String, List<DisplayModel>> sortedMapBySeverity = new LinkedHashMap<>();
	            SEVERITY_ORDER.forEach(severity -> {
	                if (mapBySeverity.containsKey(severity)) {
	                    sortedMapBySeverity.put(severity, mapBySeverity.get(severity));
	                }
	            });
			
			List<DisplayModel> children = createParentNodeByScanner(mapBySeverity);
			
			filteredResultsByScannerType.put(scanner, children);
		});
	}
	
	/**
	 * Group vulnerabilities by query name based on groupBySeverity state
	 * 
	 * @param results
	 */
	private void groupResultsByQueryName(Map<String, List<DisplayModel>> results) {

		
		// when group by state is enabled, the query names but be grouped by and created as children to state label
		if(FilterState.groupByStateName || FilterState.groupBySeverity) {
			results.entrySet().stream().forEach(entry -> entry.getValue().stream().forEach(severity ->
			{
				// here check if severity is enabled or state is enabled and we need to populate the children based on the state and severity filters selected
				// when both the filters are selected
				if(FilterState.groupBySeverity && FilterState.groupByStateName) {
					if(severity.getChildren() != null && severity.getChildren().size()>0) {
					severity.getChildren().stream().forEach( state -> {
						state.setChildren(groupByQueryName(state.getChildren()));
					});
				}
			}
				// when only one group by filter is selected
				else {
					severity.setChildren(groupByQueryName(severity.getChildren()));
				}
				
			
			}));
			
		}
		else {
			results.entrySet().stream().forEach(entry -> results.put(entry.getKey(), groupByQueryName(entry.getValue())));
		}
		
	}
	
	/**
	 * Group vulnerabilities by query name
	 * 
	 * @param children
	 * @return
	 */
	private List<DisplayModel> groupByQueryName(List<DisplayModel> vulnerabilities){
		Map<String, List<DisplayModel>> filteredByQueryName = new HashMap<>();
		
		for (DisplayModel vulnerability : vulnerabilities) {	
			String queryName = vulnerability.getQueryName();
			if (filteredByQueryName.containsKey(queryName)) {
				filteredByQueryName.get(queryName).add(vulnerability);
			} else {
				filteredByQueryName.put(queryName, new ArrayList<>(Arrays.asList(vulnerability)));
			}
		}
		
		return createParentNodeByScanner(filteredByQueryName);
		
		
	}
	
	private void groupResultsByStateName(Map<String, List<DisplayModel>> results) {
		if(FilterState.groupBySeverity) {
			results.entrySet().stream().forEach(entry -> entry.getValue().stream().forEach(severity -> severity.setChildren(groupByStateName(severity.getChildren()))));
			
		}else {
			results.entrySet().stream().forEach(entry -> results.put(entry.getKey(), groupByStateName(entry.getValue())));
		}
		
	}


	private List<DisplayModel> groupByStateName(List<DisplayModel> vulnerabilities) {
		Map<String, List<DisplayModel>> filteredByStateName = new HashMap<>();
		
		for (DisplayModel vulnerability : vulnerabilities) {
			String queryName = vulnerability.getState();
			if (filteredByStateName.containsKey(queryName)) {
				filteredByStateName.get(queryName).add(vulnerability);
			} else {
				filteredByStateName.put(queryName, new ArrayList<>(Arrays.asList(vulnerability)));
			}
		}
		
		return createParentNodeByScanner(filteredByStateName);
	}

	/**
	 * Creates parent node for each scanner
	 * 
	 * @param map
	 * @return
	 */
	private List<DisplayModel> createParentNodeByScanner(Map<String, List<DisplayModel>> map){
		List<DisplayModel> resultList = new ArrayList<>();
		
		for (Map.Entry<String, List<DisplayModel>> mapEntry : map.entrySet()) {

			int childCounter = 0;
			List<DisplayModel> listForEachSeverity = mapEntry.getValue();
			
			// When grouped by query name we need to count children
			for(DisplayModel dm : listForEachSeverity) {
				if(dm.getChildren() != null && dm.getChildren().size() > 0) {
					childCounter = childCounter + dm.getChildren().size();
				}
			}
						
			int parentCounter = childCounter > 0 ? childCounter : listForEachSeverity.size();
			DisplayModel parentModel = new DisplayModel.DisplayModelBuilder(mapEntry.getKey() + " (" + parentCounter + ")").setChildren(listForEachSeverity).build();
			
			resultList.add(parentModel);
		}
				
		return resultList;
	}
	
	
	
	/**
	 * Counts the number of results evaluating if the model has children or not
	 * 
	 * @param results
	 * @return
	 */
	
	private int getParentCounter(List<DisplayModel> results) {
		 int counter= 0;
		 for(DisplayModel dm : results) {
				if(dm.getChildren() != null && dm.getChildren().size() > 0) {
					counter = counter + Integer.parseInt(dm.getName().substring(dm.getName().indexOf("(") + 1, dm.getName().indexOf(")")));
				}
				else {
					counter = counter + 1;
				}
			}
		 
		 return counter;
	}
	
	
	/**
	 * Create a CxWrapper with current credentials
	 * 
	 * @return
	 * @throws Exception
	 */
	private static CxWrapper getWrapper() throws Exception {
		CxWrapper cxWrapper = null;
		
		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());

		CxConfig config = CxConfig.builder().apiKey(Preferences.getApiKey()).additionalParameters(Preferences.getAdditionalOptions()).build();

		try {
			cxWrapper = new CxWrapper(config, log);
		} catch (IOException e) {
			CxLogger.error(String.format(PluginConstants.ERROR_BUILDING_CX_WRAPPER, e.getMessage()), e);
			throw new Exception(e);
		}
		
		return cxWrapper;
	}
	
	/**
	 * Check if plugin has results loaded
	 * 
	 * @return
	 */
	public boolean containsResults() {
		return getCurrentResults() != null && getCurrentResults().getResults() != null && !getCurrentResults().getResults().isEmpty();
	}
	
	/*
	 * 
	 */
	
	public int getBestFixLocation(UUID scanId, String queryId, List<Node> bflNodes) throws Exception {
		CxWrapper cxWrapper = authenticateWithAST();
		int bflNode = -1;
		if(cxWrapper != null) {
			bflNode = cxWrapper.getResultsBfl(scanId, queryId, bflNodes);
		}
		return bflNode;
	}
	
	/**
	 * Get One Triage details
	 * 
	 * @return
	 * @throws Exception 
	 */
	public List<Predicate> getTriageShow(UUID projectID, String similarityID, String scanType) throws Exception {
		List<Predicate> triageList = new ArrayList<Predicate>();

		CxWrapper cxWrapper = authenticateWithAST();
		
		// TODO: remove this condition when CLI is updated to manage these checks
		if(scanType.equals(PluginConstants.KICS_INFRASTRUCTURE)) {
			scanType = "kics";
		}

		if (cxWrapper != null) {
			try {
				triageList = cxWrapper.triageShow(projectID, similarityID, scanType);

			} catch (IOException | InterruptedException | CxException e) {
				CxLogger.error(String.format(PluginConstants.ERROR_GETTING_TRIAGE_DETAILS, e.getMessage()), e);
			}
		}

		return triageList;
	}

	/**
	 * Update a vulnerability severity or state
	 * 
	 * @param projectId
	 * @param similarityId
	 * @param engineType
	 * @param state
	 * @param comment
	 * @param severity
	 * @throws Exception 
	 */
	public void triageUpdate(UUID projectId, String similarityId, String engineType, String state, String comment, String severity) throws Exception {

		try {
			CxWrapper cxWrapper = authenticateWithAST();
			
			if (cxWrapper != null) {
				cxWrapper.triageUpdate(projectId, similarityId, engineType, state, comment, severity);
			}
		} catch (Exception e) {
			CxLogger.error(String.format(PluginConstants.ERROR_UPDATING_TRIAGE, e.getMessage()), e);
			throw new Exception(e.getMessage());
			
		}
	}
	
	public List<LearnMore> learnMore(String queryId) throws Exception {	
		return authenticateWithAST().learnMore(queryId);
	}
	
	public Scan createScan(String sourcePath, String projectName, String branchName) throws IOException, InterruptedException, CxException, Exception {
		Map<String, String> scanArguments = new HashMap<>();
        scanArguments.put("-s", sourcePath);
        scanArguments.put("--project-name", projectName);
        scanArguments.put("--branch", branchName);
		scanArguments.put("--agent", ECLIPSE_AGENT);

        String additionalParameters = "--async --sast-incremental --resubmit";
        
        return authenticateWithAST().scanCreate(scanArguments, additionalParameters);
	}
	
	public void cancelScan(String scanId) throws IOException, InterruptedException, CxException, Exception {
		authenticateWithAST().scanCancel(scanId);
	}
	
	public boolean isScanAllowed() throws CxException, IOException, InterruptedException, Exception {
		return authenticateWithAST().ideScansEnabled();
	}

	/**
	 * Fetch ALL platform states (predefined + custom) irrespective of
	 * vulnerabilities.
	 */
	private List<String> getAllStatesFromPlatform() throws Exception {
		if (currentScanId == null || projectId == null) {
			return Collections.emptyList();
		}

		CxWrapper cxWrapper = authenticateWithAST();
		List<String> allStates = new ArrayList<>();

		try {
			List<CustomState> customStates = cxWrapper.triageGetStates(false);
			allStates = customStates.stream().map(CustomState::getName).collect(Collectors.toList());
		} catch (Exception e) {
			CxLogger.warning("Could not fetch platform states: " + e.getMessage());
		}

		return allStates;
	}
}
