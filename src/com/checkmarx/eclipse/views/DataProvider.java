package com.checkmarx.eclipse.views;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.ast.wrapper.Execution;
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
		DisplayModel messageModel = new DisplayModel();
		messageModel.name = message;
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
		result.add(message("Scan results  call aborted."));
		return result;
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
			new DisplayModel();
			DisplayModel sastSeverityParentModel = DisplayModel.builder()
					.name(mapEntry.getKey() + " (" + listForEachSeverity.size() + ")").children(listForEachSeverity)
					.build();
			sastParentModelList.add(sastSeverityParentModel);

		}
		// SCA
		scaCount = 0;
		List<DisplayModel> scaParentModelList = new ArrayList<DisplayModel>();
		for (Map.Entry<String, List<DisplayModel>> mapEntry : scaResultsMap.entrySet()) {

			List<DisplayModel> listForEachSeverity = mapEntry.getValue();
			scaCount = scaCount + listForEachSeverity.size();
			new DisplayModel();
			DisplayModel scaSeverityParentModel = DisplayModel.builder()
					.name(mapEntry.getKey() + " (" + listForEachSeverity.size() + ")").children(listForEachSeverity)
					.build();
			scaParentModelList.add(scaSeverityParentModel);

		}

		// kics
		kicsCount = 0;
		List<DisplayModel> kicsParentModelList = new ArrayList<DisplayModel>();
		for (Map.Entry<String, List<DisplayModel>> mapEntry : kicsResultsMap.entrySet()) {

			List<DisplayModel> listForEachSeverity = mapEntry.getValue();
			kicsCount = kicsCount + listForEachSeverity.size();
			new DisplayModel();
			DisplayModel kicsSeverityParentModel = DisplayModel.builder()
					.name(mapEntry.getKey() + " (" + listForEachSeverity.size() + ")").children(listForEachSeverity)
					.build();
			kicsParentModelList.add(kicsSeverityParentModel);

		}

		List<DisplayModel> results = new ArrayList<>();
		if (sastParentModelList.size() > 0) {

			new DisplayModel();
			DisplayModel sastModel = DisplayModel.builder().name("SAST" + " (" + sastCount + ")")
					.children(sastParentModelList).build();
			results.add(sastModel);
		}
		if (scaParentModelList.size() > 0) {
			new DisplayModel();
			DisplayModel scaModel = DisplayModel.builder().name("SCA" + " (" + scaCount + ")")
					.children(scaParentModelList).build();
			results.add(scaModel);
		}
		if (kicsParentModelList.size() > 0) {
			new DisplayModel();
			DisplayModel kicsModel = DisplayModel.builder().name("KICS" + " (" + kicsCount + ")")
					.children(kicsParentModelList).build();
			results.add(kicsModel);
		}

//		 for (Map.Entry mapEntry : treeItemMap.entrySet()) {
//			 DisplayModel parentDisplayItem = new DisplayModel().builder().name((String) mapEntry.getKey()).children((List<DisplayModel>) mapEntry.getValue()).build();
//			 results.add(parentDisplayItem);
//		 }

		projectModel = DisplayModel.builder().name(scanId + " (" + scanResults.getTotalCount() + " Issues)")
				.children(results).build();

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


		return DisplayModel.builder().name(displayName).severity(resultItem.getSeverity()).type(resultItem.getType())
				.result(resultItem).build();

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
