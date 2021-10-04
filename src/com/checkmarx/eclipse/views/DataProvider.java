package com.checkmarx.eclipse.views;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.exceptions.CxException;
import com.checkmarx.ast.results.CxValidateOutput;
import com.checkmarx.ast.results.structure.CxResult;
import com.checkmarx.ast.results.structure.CxResultOutput;
import com.checkmarx.ast.scans.CxAuth;
import com.checkmarx.ast.scans.CxScanConfig;
import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.runner.Authenticator;

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
	
	public List<DisplayModel> abortResult() {
		List<DisplayModel> result = new ArrayList<>();
		result.add(message("Scan results  call aborted."));
		return result;
	}
	
	public List<DisplayModel> getResultsForScanId(String scanId) {
		
		abort.set(false);
		CxResultOutput resultCommandOutput = null;
		CxScanConfig config = new CxScanConfig();

	    config.setBaseUri(Preferences.getServerUrl());
	    config.setTenant(Preferences.getTenant());
	    config.setApiKey(Preferences.getApiKey());
	    
		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());
		
		try {
			CxAuth cxAuth = new CxAuth(config, log);
			CxValidateOutput cxValidateOutput = cxAuth.cxAuthValidate();
			System.out.println("Authentication Status :" + cxValidateOutput.getMessage());
			System.out.println("Fetching the results for scanId :" + scanId);
			resultCommandOutput = cxAuth.cxGetResults(scanId); //adfa3bb4-754d-4444-b8ca-67edbe767186 for sca kics and sast
			System.out.println("Scan results :" + resultCommandOutput.getTotalCount());			
			
		} catch (IOException | CxException | URISyntaxException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return processResultsV2(resultCommandOutput, scanId);
	}
	
//	public List<DisplayModel> processResultsV1(CxResultOutput resultsCommandOutput) {
//
//		DisplayModel projectModel;
//		List<CxResult> allResults = resultsCommandOutput.getResults();
//
//		Map<String, List<CxResult>> filterResultsByScannerType = filterResultsByScannerType(allResults);
//
//		// Map<String, List<CxResult>> sastResultsMap =
//		// filterResultsBySeverity(allResults);
//
//		List<CxResult> sastResults = new ArrayList<>();
//		List<CxResult> scaResults = new ArrayList<>();
//		List<CxResult> kicsResults = new ArrayList<>();
//
//		// filtering by each scanner type
//		sastResults.addAll(filterResultsByScannerType(allResults, "sast"));
//		scaResults.addAll(filterResultsByScannerType(allResults, "sca"));
//		kicsResults.addAll(filterResultsByScannerType(allResults, "infrastructure"));
//
//		// filtering by each severity type
//		Map<String, List<CxResult>> sastMap = new HashMap<>();
//		sastMap.put("LOW", filterResultsBySeverity(sastResults, "LOW"));
//		sastMap.put("MEDIUM", filterResultsBySeverity(sastResults, "MEDIUM"));
//		sastMap.put("HIGH", filterResultsBySeverity(sastResults, "HIGH"));
//
//		List<DisplayModel> sastParentModelList = new ArrayList<DisplayModel>();
//
//		for (Map.Entry mapEntry : sastMap.entrySet()) {
//
//			List<CxResult> resultList = (List<CxResult>) mapEntry.getValue();
//
//			List<DisplayModel> sastChildModelList = resultList.stream().map(resultItem -> transform(resultItem))
//					.collect(Collectors.toList());
//
//			if (sastChildModelList.size() > 0) {
//				new DisplayModel();
//				DisplayModel sastModel = DisplayModel.builder().name((String) mapEntry.getKey())
//						.children(sastChildModelList).build();
//				sastParentModelList.add(sastModel);
//			}
//
//		}
//
////		List<DisplayModel> sastResultsModel = sastResults.stream()
////				.map(resultItem -> transform(resultItem)).collect(Collectors.toList());
//		List<DisplayModel> scaResultsModel = scaResults.stream().map(resultItem -> transform(resultItem))
//				.collect(Collectors.toList());
//		List<DisplayModel> kicsResultsModel = kicsResults.stream().map(resultItem -> transform(resultItem))
//				.collect(Collectors.toList());
//
//		List<DisplayModel> results = new ArrayList<>();
//		if (sastParentModelList.size() > 0) {
//			new DisplayModel();
//			DisplayModel sastModel = DisplayModel.builder().name("SAST").children(sastParentModelList).build();
//			results.add(sastModel);
//		}
//		if (scaResultsModel.size() > 0) {
//			new DisplayModel();
//			DisplayModel scaModel = DisplayModel.builder().name("SCA").children(scaResultsModel).build();
//			results.add(scaModel);
//		}
//		if (kicsResultsModel.size() > 0) {
//			new DisplayModel();
//			DisplayModel kicsModel = DisplayModel.builder().name("KICS").children(kicsResultsModel).build();
//			results.add(kicsModel);
//		}
//
////		 for (Map.Entry mapEntry : treeItemMap.entrySet()) {
////			 DisplayModel parentDisplayItem = new DisplayModel().builder().name((String) mapEntry.getKey()).children((List<DisplayModel>) mapEntry.getValue()).build();
////			 results.add(parentDisplayItem);
////		 }
//
//		projectModel = DisplayModel.builder()
//				.name("<Place the Scan ID here>" + " (" + resultsCommandOutput.getTotalCount() + " Issues)")
//				.children(results).build();
//
//		List<DisplayModel> returnList = new ArrayList<>();
//		returnList.add(projectModel);
//
//		return returnList;
//	}

	private List<DisplayModel> processResultsV2(CxResultOutput resultsCommandOutput, String scanId) { ////////// ----VERSION
																						////////// 2---------/////////////////

		DisplayModel projectModel;
		List<CxResult> allResults = resultsCommandOutput.getResults();

		// transform all the results at once to avoid multiple transformation steps
		List<DisplayModel> allResultsTransformed = allResults.stream().map(resultItem -> transform(resultItem))
				.collect(Collectors.toList());

		// Divide all the results as per the scanner type
		Map<String, List<DisplayModel>> filteredResultsByScannerType = filterResultsByScannerTypeV2(
				allResultsTransformed);

		// Divide the results for each scanner as per the severity
		Map<String, List<DisplayModel>> sastResultsMap = new HashMap<>();
		Map<String, List<DisplayModel>> scaResultsMap = new HashMap<>();
		Map<String, List<DisplayModel>> kicsResultsMap = new HashMap<>();

		if (filteredResultsByScannerType.containsKey("sast")) {
			List<DisplayModel> sastList = filteredResultsByScannerType.get("sast");
			sastResultsMap = filterResultsBySeverityV2(sastList);
		}
		if (filteredResultsByScannerType.containsKey("dependency")) {
			List<DisplayModel> scaList = filteredResultsByScannerType.get("dependency");
			scaResultsMap = filterResultsBySeverityV2(scaList);
		}
		if (filteredResultsByScannerType.containsKey("infrastructure")) {
			List<DisplayModel> kicsList = filteredResultsByScannerType.get("infrastructure");
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

		projectModel = DisplayModel.builder()
				.name(scanId + " (" + resultsCommandOutput.getTotalCount() + " Issues)")
				.children(results).build();

		List<DisplayModel> returnList = new ArrayList<>();
		returnList.add(projectModel);

		return returnList;
	}

	private DisplayModel transform(CxResult resultItem) {

		String queryName = (resultItem.getData()).getQueryName();
		String description = (resultItem.getData()).getDescription();

		return DisplayModel.builder().name(queryName).state(resultItem.getState()).status(resultItem.getStatus())
				.severity(resultItem.getSeverity()).type(resultItem.getType()).description(description).build();
	}

	private List<CxResult> filterResultsByScannerType(List<CxResult> resultList, String scannerType) {

		List<CxResult> filteredResults = new ArrayList<>();

		for (CxResult cxResult : resultList) {

			if (cxResult.getType().equalsIgnoreCase(scannerType)) {
				filteredResults.add(cxResult);
			}
		}
		return filteredResults;
	}

	private Map<String, List<CxResult>> filterResultsByScannerType(List<CxResult> resultList) {

		Map<String, List<CxResult>> filteredMap = new HashMap<>();

		for (CxResult cxResult : resultList) {

			String scanType = cxResult.getType();

			if (filteredMap.containsKey(scanType)) {
				List<CxResult> mapResultList = filteredMap.get(scanType);
				mapResultList.add(cxResult);
			} else {
				List<CxResult> mapResultList = new ArrayList<>();
				mapResultList.add(cxResult);
				filteredMap.put(scanType, mapResultList);
			}

		}
		return filteredMap;
	}

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

	private List<CxResult> filterResultsBySeverity(List<CxResult> resultList, String severity) {

		List<CxResult> filteredResult = new ArrayList<>();

		for (CxResult cxResult : resultList) {

			if (cxResult.getSeverity().equalsIgnoreCase(severity)) {
				filteredResult.add(cxResult);
			}
		}
		return filteredResult;
	}

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
