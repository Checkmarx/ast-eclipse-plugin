package com.checkmarx.eclipse.views;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxConfig.InvalidCLIConfigException;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.runner.Authenticator;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.filters.FilterState;

public class DataProvider {
	
	private static final String LIMIT_FILTER="limit=10000";		
	private static final String FILTER_SCANS_FOR_PROJECT = "project-id=%s,branch=%s,limit=10000,statuses=Completed";
	
	private static final String SAST_TREE_NAME = "SAST (%d)";
	private static final String SCA_TREE_NAME = "SCA (%d)";
	private static final String KICS_TREE_NAME = "KICS (%d)";
	private static final String RESULTS_TREE_NAME = "%s (%d Issues)";
	
	private static DataProvider _dataProvider = null;

	public static final AtomicBoolean abort = new AtomicBoolean(false);
	
	private Results currentResults;
	private String currentScanId;
	private String projectId;
	private CxWrapper wrapper;
	
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
	 * Get AST projects
	 * 
	 * @return
	 */
	public List<Project> getProjects() {
		List<Project> projectList = new ArrayList<Project>();
		
		authenticateWithAST();
		
		if (wrapper != null) {
			try {
				projectList = wrapper.projectList(LIMIT_FILTER);

			} catch (IOException | InterruptedException | CxException e) {
				CxLogger.error(String.format(PluginConstants.ERROR_GETTING_PROJECTS, e.getMessage()), e);
			}
		}

		return projectList;
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

		if (wrapper != null) {
			try {
				branchList = wrapper.projectBranches(UUID.fromString(projectId), "");

			} catch (IOException | InterruptedException | CxException e) {
				CxLogger.error(String.format(PluginConstants.ERROR_GETTING_BRANCHES, projectId, e.getMessage()), e);
			}
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

		if (wrapper != null) {
			try {
				String filter = String.format(FILTER_SCANS_FOR_PROJECT, projectId, branch);
				scanList = wrapper.scanList(filter);

			} catch (IOException | InterruptedException | CxException e) {
				CxLogger.error(String.format(PluginConstants.ERROR_GETTING_SCANS, projectId, branch, e.getMessage()), e);
			}
		}

		return scanList;
	}
	
	/**
	 * Authenticate to AST with current credentials
	 */
	private void authenticateWithAST() {
		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());

		try {
			CxConfig config = CxConfig.builder().baseUri(Preferences.getServerUrl()).tenant(Preferences.getTenant())
					.apiKey(Preferences.getApiKey()).additionalParameters(Preferences.getAdditionalOptions()).build();

			wrapper = new CxWrapper(config, log);
			String validationResult = wrapper.authValidate();

			CxLogger.info(String.format(PluginConstants.INFO_AUTHENTICATION_STATUS, validationResult));

		} catch (Exception e) {
			CxLogger.error(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, e.getMessage()), e);
		}
	}
	
	/**
	 * Creates a new wrapper to validate AST authentication
	 * 
	 * @return
	 */
	//TODO: check if this method is needed when introduced new changes regarding authentication panel
	public List<DisplayModel> validateAuthentication(){
		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());
		
		CxConfig config = CxConfig.builder().baseUri(Preferences.getServerUrl()).tenant(Preferences.getTenant())
				.apiKey(Preferences.getApiKey()).additionalParameters(Preferences.getAdditionalOptions()).build();
		
		try {
			wrapper = new CxWrapper(config, log);
		} catch (InvalidCLIConfigException | IOException e) {
			CxLogger.error(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, e.getMessage()), e);
			return  Arrays.asList(PluginUtils.message("Error: " + e.getMessage()));
		}
		
		return Collections.emptyList();
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

		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());
		
		setCurrentScanId(scanId);

		try {
			CxConfig config = CxConfig.builder().baseUri(Preferences.getServerUrl()).tenant(Preferences.getTenant()).apiKey(Preferences.getApiKey()).additionalParameters("").build();
			
			// TODO: remove this new wrapper creation. Change to just use one and update the wrapper when the checkmarx credentials changes.
			CxWrapper wrapper = new CxWrapper(config, log);
			String validationResult = wrapper.authValidate();
			
			CxLogger.info(String.format(PluginConstants.INFO_AUTHENTICATION_STATUS, validationResult));
			CxLogger.info(String.format(PluginConstants.INFO_FETCHING_RESULTS, scanId));

			scanResults = wrapper.results(UUID.fromString(scanId));
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
	public Scan getScanInformation(String scanId) {
		Scan scan = null;

		try {
			CxLogger.info(String.format(PluginConstants.INFO_GETTING_SCAN_INFO, scanId));
			scan = wrapper.scanShow(UUID.fromString(scanId));
		} catch (Exception e) {
			CxLogger.error(String.format(PluginConstants.ERROR_GETTING_SCAN_INFO, e.getMessage()), e);
		}

		return scan;
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

		// transform all the results at once to avoid multiple transformation steps
		List<DisplayModel> allResultsTransformed = resultsList.stream().map(resultItem -> transform(resultItem)).collect(Collectors.toList());

		// Divide all the results by scanner type
		Map<String, List<DisplayModel>> filteredResultsByScannerType = filterResultsByScannerTypeV2(allResultsTransformed);

		// build results based on selected filters
		return buildResults(scanId, filteredResultsByScannerType);
	}
	
	/**
	 * Build results to be displayed in the tree
	 * 
	 * @param scanId
	 * @param filteredResultsByScannerType
	 * @return
	 */
	private List<DisplayModel> buildResults(String scanId, Map<String, List<DisplayModel>> filteredResultsByScannerType){
		if(FilterState.groupBySeverity) {
			// Divide the results for each scanner as per the severity
			Map<String, List<DisplayModel>> sastResultsMap = new HashMap<>();
			Map<String, List<DisplayModel>> scaResultsMap = new HashMap<>();
			Map<String, List<DisplayModel>> kicsResultsMap = new HashMap<>();

			if (filteredResultsByScannerType.containsKey(PluginConstants.SAST)) {
				List<DisplayModel> sastList = filteredResultsByScannerType.get(PluginConstants.SAST);
				sastResultsMap = filterResultsBySeverityV2(sastList);
				
				if(FilterState.groupByQueryName && !sastResultsMap.isEmpty()) {
					filterResultsByQueryName(sastResultsMap);
				}
			}
			
			if (filteredResultsByScannerType.containsKey(PluginConstants.SCA_DEPENDENCY)) {
				List<DisplayModel> scaList = filteredResultsByScannerType.get(PluginConstants.SCA_DEPENDENCY);
				scaResultsMap = filterResultsBySeverityV2(scaList);
				
				if(FilterState.groupByQueryName && !scaResultsMap.isEmpty()) {
					filterResultsByQueryName(scaResultsMap);
				}
			}
			
			if (filteredResultsByScannerType.containsKey(PluginConstants.KICS_INFRASTRUCTURE)) {
				List<DisplayModel> kicsList = filteredResultsByScannerType.get(PluginConstants.KICS_INFRASTRUCTURE);
				kicsResultsMap = filterResultsBySeverityV2(kicsList);
				
				if(FilterState.groupByQueryName && !kicsResultsMap.isEmpty()) {
					filterResultsByQueryName(kicsResultsMap);
				}
			}

			// Parent node for SAST
			Map<Integer, List<DisplayModel>> sastParentModelList = createParentNodeByScanner(sastResultsMap);
			Integer sastCount = sastParentModelList.keySet().stream().findFirst().get();
			List<DisplayModel> sastChildren = sastParentModelList.get(sastCount);

			// Parent node for SCA
			Map<Integer, List<DisplayModel>> scaParentModelList = createParentNodeByScanner(scaResultsMap);
			Integer scaCount = scaParentModelList.keySet().stream().findFirst().get();
			List<DisplayModel> scaChildren = scaParentModelList.get(scaCount);

			// Parent node for KICS
			Map<Integer, List<DisplayModel>> kicsParentModelList = createParentNodeByScanner(kicsResultsMap);
			Integer kicsCount = kicsParentModelList.keySet().stream().findFirst().get();
			List<DisplayModel> kicsChildren = kicsParentModelList.get(kicsCount);
			
			return addResults(scanId, sastCount, sastChildren, scaCount, scaChildren, kicsCount, kicsChildren);
			
		}else if(FilterState.groupByQueryName) {
			filterResultsByQueryName(filteredResultsByScannerType);
		}
		
		boolean constainsSASTResults = filteredResultsByScannerType.containsKey(PluginConstants.SAST) && filteredResultsByScannerType.get(PluginConstants.SAST).size() > 0;
		List<DisplayModel> sastResults = constainsSASTResults ? filteredResultsByScannerType.get(PluginConstants.SAST) : Collections.emptyList();
		int sastCount = constainsSASTResults ? getParentCounter(sastResults) : 0;
		
		boolean constainsSCAResults = filteredResultsByScannerType.containsKey(PluginConstants.SCA_DEPENDENCY) && filteredResultsByScannerType.get(PluginConstants.SCA_DEPENDENCY).size() > 0;
		List<DisplayModel> scaResults = constainsSCAResults ? filteredResultsByScannerType.get(PluginConstants.SCA_DEPENDENCY) : Collections.emptyList();
		int scaCount = constainsSCAResults ? getParentCounter(scaResults) : 0;
		
		boolean constainsKICKSResults = filteredResultsByScannerType.containsKey(PluginConstants.KICS_INFRASTRUCTURE) && filteredResultsByScannerType.get(PluginConstants.KICS_INFRASTRUCTURE).size() > 0;
		List<DisplayModel> kicsResults = constainsKICKSResults ? filteredResultsByScannerType.get(PluginConstants.KICS_INFRASTRUCTURE) : Collections.emptyList();
		int kicsCount = constainsKICKSResults ? getParentCounter(kicsResults) : 0;

		return addResults(scanId, sastCount, sastResults, scaCount, scaResults, kicsCount, kicsResults);
	}
	
	/**
	 * Evaluates if each engine has results and adds it to the final map
	 * 
	 * @param scanId
	 * @param sastCount
	 * @param sastChildren
	 * @param scaCount
	 * @param scaChildren
	 * @param kicsCount
	 * @param kicsChildren
	 * @return
	 */
	private List<DisplayModel> addResults(String scanId, Integer sastCount, List<DisplayModel> sastChildren, Integer scaCount, List<DisplayModel> scaChildren, Integer kicsCount, List<DisplayModel> kicsChildren) {
		List<DisplayModel> returnList = new ArrayList<>();
		List<DisplayModel> results = new ArrayList<>();
		
		if (sastCount > 0) {

			DisplayModel sastModel = new DisplayModel.DisplayModelBuilder(String.format(SAST_TREE_NAME, sastCount)).setChildren(sastChildren).build();
			results.add(sastModel);
		}
		
		if (scaCount > 0) {
			
			DisplayModel scaModel = new DisplayModel.DisplayModelBuilder(String.format(SCA_TREE_NAME, scaCount)).setChildren(scaChildren).build();
			results.add(scaModel);
		}
		
		if (kicsCount > 0) {
			
			DisplayModel kicsModel = new DisplayModel.DisplayModelBuilder(String.format(KICS_TREE_NAME, kicsCount)).setChildren(kicsChildren).build();
			results.add(kicsModel);
		}
		
		int totalCount = sastCount + scaCount + kicsCount;
		DisplayModel projectModel = new DisplayModel.DisplayModelBuilder(String.format(RESULTS_TREE_NAME, scanId, totalCount)).setChildren(results).build();
		
		returnList.add(projectModel);

		return returnList;
	}

	/**
	 * Creates a Display Model which represents each result
	 * 
	 * @param resultItem
	 * @return
	 */
	private DisplayModel transform(Result resultItem) {
		String displayName = resultItem.getType().equals(PluginConstants.SCA_DEPENDENCY) ? resultItem.getSimilarityId() : resultItem.getData().getQueryName();
		
		return new DisplayModel.DisplayModelBuilder(displayName).setSeverity(resultItem.getSeverity()).setType(resultItem.getType()).setResult(resultItem).build();
	}

	/**
	 * Group results by scanner type
	 * 
	 * @param allResultsTransformed
	 * @return
	 */
	private Map<String, List<DisplayModel>> filterResultsByScannerTypeV2(List<DisplayModel> allResultsTransformed) {
		Map<String, List<DisplayModel>> filteredMap = new HashMap<>();

		for (DisplayModel transformedResult : allResultsTransformed) {

			String scanType = transformedResult.getType();

			if (filteredMap.containsKey(scanType)) {
				List<DisplayModel> mapResultList = filteredMap.get(scanType);
				mapResultList.add(transformedResult);
			} else {
				List<DisplayModel> mapResultList = new ArrayList<>();
				mapResultList.add(transformedResult);
				filteredMap.put(scanType, mapResultList);
			}

		}
		return filteredMap;
	}

	/**
	 * Group results by Severity
	 * 
	 * @param resultList
	 * @return
	 */
	private Map<String, List<DisplayModel>> filterResultsBySeverityV2(List<DisplayModel> resultList) {
		Map<String, List<DisplayModel>> filteredMapBySeverity = new HashMap<>();

		for (DisplayModel result : resultList) {
			String severityType = result.getSeverity();
			
			if(FilterState.isSeverityEnabled(severityType)) {
				if (filteredMapBySeverity.containsKey(severityType)) {
					List<DisplayModel> mapResultList = filteredMapBySeverity.get(severityType);
					mapResultList.add(result);
				} else {
					List<DisplayModel> mapResultList = new ArrayList<>();
					mapResultList.add(result);
					filteredMapBySeverity.put(severityType, mapResultList);
				}
			}
		}
		
		return filteredMapBySeverity;
	}
	
	/**
	 * Group results by query name
	 * 
	 * @param results
	 */
	private void filterResultsByQueryName(Map<String, List<DisplayModel>> results) {
		for (Map.Entry<String, List<DisplayModel>> entry : results.entrySet()) {
			
			String severityOrScannerType = entry.getKey();
			List<DisplayModel> vulnerabilities = entry.getValue();
			
			Map<String, List<DisplayModel>> filteredByQueryName = new HashMap<>();

			for (DisplayModel result : vulnerabilities) {
				
				String queryName = result.getName();
				
				if (filteredByQueryName.containsKey(queryName)) {
					List<DisplayModel> mapResultList = filteredByQueryName.get(queryName);
					mapResultList.add(result);
				} else {
					List<DisplayModel> mapResultList = new ArrayList<>();
					mapResultList.add(result);
					filteredByQueryName.put(queryName, mapResultList);
				}
			}
			
			Map<Integer, List<DisplayModel>> parentModelList = createParentNodeByScanner(filteredByQueryName);
			Integer sastCount = parentModelList.keySet().stream().findFirst().get();
			List<DisplayModel> children = parentModelList.get(sastCount);
			results.put(severityOrScannerType, children);
		}
	}
	
	/**
	 * Creates parent node for each scanner
	 * 
	 * @param map
	 * @return
	 */
	private Map<Integer, List<DisplayModel>> createParentNodeByScanner(Map<String, List<DisplayModel>> map){
		Map<Integer, List<DisplayModel>> result = new HashMap<>();
		List<DisplayModel> resultList = new ArrayList<>();
		int counter = 0;
		
		for (Map.Entry<String, List<DisplayModel>> mapEntry : map.entrySet()) {

			int childCounter = 0;
			List<DisplayModel> listForEachSeverity = mapEntry.getValue();
			
			// When grouped by query name we need to count children
			for(DisplayModel dm : listForEachSeverity) {
				if(dm.getChildren() != null && dm.getChildren().size() > 0) {
					childCounter = childCounter + dm.getChildren().size();
				}
			}
			
			counter += childCounter == 0 ? listForEachSeverity.size() : childCounter;
			
			int parentCounter = childCounter > 0 ? childCounter : listForEachSeverity.size();
			DisplayModel parentModel = new DisplayModel.DisplayModelBuilder(mapEntry.getKey() + " (" + parentCounter + ")").setChildren(listForEachSeverity).build();
			
			resultList.add(parentModel);
		}
		
		result.put(counter, resultList);
		
		return result;
	}
	
	/**
	 * Counts the number of results evaluating if the model has children or not
	 * 
	 * @param results
	 * @return
	 */
	private int getParentCounter(List<DisplayModel> results) {
		int counter = 0;

		for (DisplayModel dm : results) {
			if (dm.getChildren() != null && !dm.getChildren().isEmpty()) {
				counter += dm.getChildren().size();
			}
		}

		return counter > 0 ? counter : results.size();
	}
	
	/**
	 * Filter results based on current filter
	 * 
	 * @return
	 */
	public List<DisplayModel> filterResults(){		
		return processResults(getCurrentResults(), getCurrentScanId());
	}
}
