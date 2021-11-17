package com.checkmarx.eclipse.views;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.runner.Authenticator;
import com.checkmarx.eclipse.utils.PluginConstants;

public class DataProvider {

	private static final Bundle BUNDLE = FrameworkUtil.getBundle(DataProvider.class);
	private static final ILog LOG = Platform.getLog(BUNDLE);

	public static final DataProvider INSTANCE = new DataProvider();

	public static final AtomicBoolean abort = new AtomicBoolean(false);

	private List<String> scanTypes = new ArrayList<String>();
	private List<String> severityTypes = new ArrayList<String>();

	private Integer sastCount = 0;
	private Integer scaCount = 0;
	private Integer kicsCount = 0;

	public DisplayModel message(String message) {
		DisplayModel messageModel = new DisplayModel.DisplayModelBuilder(message).build();
		return messageModel;
	}
	
	public List<DisplayModel> error(Exception e) {
		e.printStackTrace();
		List<DisplayModel> result = new ArrayList<>();
		result.add(message("Error: " + e.getMessage()));
		return result;
	}

	public List<DisplayModel> abortResult() {
		List<DisplayModel> result = new ArrayList<>();
		result.add(message("Scan results call aborted."));
		return result;
	}

	public List<Project> getProjectList()
	{
		List<Project> projectList = new  ArrayList<Project>();
		CxWrapper wrapper =  authenticateWithAST();
		if(wrapper!=null)
		{
			try {
				String projectLimitFilter = "limit=10000";
				projectList = wrapper.projectList(projectLimitFilter);
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return projectList;
	}
	
	public List<Scan> getScanListOfProject(String projectId)
	{
		List<Scan> scanList = new  ArrayList<Scan>();
		CxWrapper wrapper =  authenticateWithAST();
		if(wrapper!=null)
		{
			try {
				String projectIdFilter = "project-id=" + projectId;
				scanList = wrapper.scanList(projectIdFilter);
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return scanList;
	}
	
	private CxWrapper authenticateWithAST()
	{
		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());

		try {
			CxConfig config = CxConfig.builder().baseUri(Preferences.getServerUrl()).tenant(Preferences.getTenant())
					.apiKey(Preferences.getApiKey()).additionalParameters("").build();
			
			CxWrapper wrapper = new CxWrapper(config, log);
			String validationResult = wrapper.authValidate();
			
			System.out.println("Authentication Status :" + validationResult);
			return wrapper;
	
		} catch (Exception e) {
			return null;
		}
	}
	
	public List<DisplayModel> getResultsForScanId(String scanId) {

		abort.set(false);
		Results scanResults = null;

		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());

		try {
			CxConfig config = CxConfig.builder().baseUri(Preferences.getServerUrl()).tenant(Preferences.getTenant())
					.apiKey(Preferences.getApiKey()).additionalParameters("").build();
			
			CxWrapper wrapper = new CxWrapper(config, log);
			String validationResult = wrapper.authValidate();
			

			System.out.println("Authentication Status :" + validationResult);
			System.out.println("Fetching the results for scanId :" + scanId);

			scanResults = wrapper.results(UUID.fromString(scanId));
			System.out.println("Scan results :" + scanResults.getTotalCount());

		} catch (Exception e) {
			return error(e);
		}

		return processResults(scanResults, scanId);
	}

	private List<DisplayModel> processResults(Results scanResults, String scanId) {

		DisplayModel projectModel;
		List<Result> resultsList = scanResults.getResults();

		// transform all the results at once to avoid multiple transformation steps
		List<DisplayModel> allResultsTransformed = resultsList.stream().map(resultItem -> transform(resultItem))
				.collect(Collectors.toList());

		// Divide all the results as per the scanner type
		Map<String, List<DisplayModel>> filteredResultsByScannerType = filterResultsByScannerTypeV2(
				allResultsTransformed);

		// Divide the results for each scanner as per the severity
		Map<String, List<DisplayModel>> sastResultsMap = new HashMap<>();
		Map<String, List<DisplayModel>> scaResultsMap = new HashMap<>();
		Map<String, List<DisplayModel>> kicsResultsMap = new HashMap<>();

		if (filteredResultsByScannerType.containsKey(PluginConstants.SAST)) {
			List<DisplayModel> sastList = filteredResultsByScannerType.get(PluginConstants.SAST);
			sastResultsMap = filterResultsBySeverityV2(sastList);
		}
		if (filteredResultsByScannerType.containsKey(PluginConstants.SCA_DEPENDENCY)) {
			List<DisplayModel> scaList = filteredResultsByScannerType.get(PluginConstants.SCA_DEPENDENCY);
			scaResultsMap = filterResultsBySeverityV2(scaList);
		}
		if (filteredResultsByScannerType.containsKey(PluginConstants.KICS_INFRASTRUCTURE)) {
			List<DisplayModel> kicsList = filteredResultsByScannerType.get(PluginConstants.KICS_INFRASTRUCTURE);
			kicsResultsMap = filterResultsBySeverityV2(kicsList);
		}

		// Creating a parent node for each scanner
		// SAST
		sastCount = 0;
		List<DisplayModel> sastParentModelList = new ArrayList<DisplayModel>();
		for (Map.Entry<String, List<DisplayModel>> mapEntry : sastResultsMap.entrySet()) {

			List<DisplayModel> listForEachSeverity = mapEntry.getValue();
			sastCount = sastCount + listForEachSeverity.size();
			
			DisplayModel sastSeverityParentModel = new DisplayModel.DisplayModelBuilder(mapEntry.getKey() + " (" + listForEachSeverity.size() + ")").setChildren(listForEachSeverity)
					.build();
			
			sastParentModelList.add(sastSeverityParentModel);

		}
		// SCA
		scaCount = 0;
		List<DisplayModel> scaParentModelList = new ArrayList<DisplayModel>();
		for (Map.Entry<String, List<DisplayModel>> mapEntry : scaResultsMap.entrySet()) {

			List<DisplayModel> listForEachSeverity = mapEntry.getValue();
			scaCount = scaCount + listForEachSeverity.size();

			DisplayModel scaSeverityParentModel = new DisplayModel.DisplayModelBuilder(mapEntry.getKey() + " (" + listForEachSeverity.size() + ")").setChildren(listForEachSeverity)
					.build();
			scaParentModelList.add(scaSeverityParentModel);

		}

		// kics
		kicsCount = 0;
		List<DisplayModel> kicsParentModelList = new ArrayList<DisplayModel>();
		for (Map.Entry<String, List<DisplayModel>> mapEntry : kicsResultsMap.entrySet()) {

			List<DisplayModel> listForEachSeverity = mapEntry.getValue();
			kicsCount = kicsCount + listForEachSeverity.size();
		
			DisplayModel kicsSeverityParentModel = new DisplayModel.DisplayModelBuilder(mapEntry.getKey() + " (" + listForEachSeverity.size() + ")").setChildren(listForEachSeverity)
					.build();
			kicsParentModelList.add(kicsSeverityParentModel);

		}

		List<DisplayModel> results = new ArrayList<>();
		if (sastParentModelList.size() > 0) {


			DisplayModel sastModel = new DisplayModel.DisplayModelBuilder("SAST" + " (" + sastCount + ")").setChildren(sastParentModelList).build();
			results.add(sastModel);
		}
		if (scaParentModelList.size() > 0) {
		
			DisplayModel scaModel = new DisplayModel.DisplayModelBuilder("SCA" + " (" + scaCount + ")").setChildren(scaParentModelList).build();
			results.add(scaModel);
		}
		if (kicsParentModelList.size() > 0) {
		
			DisplayModel kicsModel = new DisplayModel.DisplayModelBuilder("KICS" + " (" + kicsCount + ")").setChildren(kicsParentModelList).build();
			results.add(kicsModel);
		}

//		 for (Map.Entry mapEntry : treeItemMap.entrySet()) {
//			 DisplayModel parentDisplayItem = new DisplayModel().builder().name((String) mapEntry.getKey()).children((List<DisplayModel>) mapEntry.getValue()).build();
//			 results.add(parentDisplayItem);
//		 }

		projectModel = new DisplayModel.DisplayModelBuilder(scanId + " (" + scanResults.getTotalCount() + " Issues)").setChildren(results).build();

		List<DisplayModel> returnList = new ArrayList<>();
		returnList.add(projectModel);

		return returnList;
	}

	private DisplayModel transform(Result resultItem) {

		String displayName;
		if ((resultItem.getType()).equals(PluginConstants.SCA_DEPENDENCY)) {
			displayName = resultItem.getSimilarityId();
		} else {
			displayName = (resultItem.getData()).getQueryName();
		}


		return new DisplayModel.DisplayModelBuilder(displayName).setSeverity(resultItem.getSeverity()).setType(resultItem.getType())
				.setResult(resultItem).build();

//		return DisplayModel.builder().name(displayName).state(resultItem.getState()).status(resultItem.getStatus())
//				.severity(resultItem.getSeverity()).type(resultItem.getType()).description(description).nodes(nodesList).build();
	}

//	private List<CxResult> filterResultsByScannerType(List<CxResult> resultList, String scannerType) {
//
//		List<CxResult> filteredResults = new ArrayList<>();
//
//		for (CxResult cxResult : resultList) {
//
//			if (cxResult.getType().equalsIgnoreCase(scannerType)) {
//				filteredResults.add(cxResult);
//			}
//		}
//		return filteredResults;
//	}

//	private Map<String, List<CxResult>> filterResultsByScannerType(List<CxResult> resultList) {
//
//		Map<String, List<CxResult>> filteredMap = new HashMap<>();
//
//		for (CxResult cxResult : resultList) {
//
//			String scanType = cxResult.getType();
//
//			if (filteredMap.containsKey(scanType)) {
//				List<CxResult> mapResultList = filteredMap.get(scanType);
//				mapResultList.add(cxResult);
//			} else {
//				List<CxResult> mapResultList = new ArrayList<>();
//				mapResultList.add(cxResult);
//				filteredMap.put(scanType, mapResultList);
//			}
//
//		}
//		return filteredMap;
//	}

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

//	private List<CxResult> filterResultsBySeverity(List<CxResult> resultList, String severity) {
//
//		List<CxResult> filteredResult = new ArrayList<>();
//
//		for (CxResult cxResult : resultList) {
//
//			if (cxResult.getSeverity().equalsIgnoreCase(severity)) {
//				filteredResult.add(cxResult);
//			}
//		}
//		return filteredResult;
//	}

	private Map<String, List<DisplayModel>> filterResultsBySeverityV2(List<DisplayModel> resultList) {

		Map<String, List<DisplayModel>> filteredMap = new HashMap<>();

		for (DisplayModel result : resultList) {

			String severityType = result.getSeverity();

			if (filteredMap.containsKey(severityType)) {
				List<DisplayModel> mapResultList = filteredMap.get(severityType);
				mapResultList.add(result);
			} else {
				List<DisplayModel> mapResultList = new ArrayList<>();
				mapResultList.add(result);
				filteredMap.put(severityType, mapResultList);
			}

		}
		return filteredMap;
	}
}
