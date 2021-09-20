package com.checkmarx.eclipse.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.checkmarx.ast.results.structure.CxResult;
import com.checkmarx.ast.results.structure.CxResultOutput;

public class DataProvider {
	
	private static final Bundle BUNDLE = FrameworkUtil.getBundle(DataProvider.class);
	private static final ILog LOG = Platform.getLog(BUNDLE);

	public static final  DataProvider INSTANCE = new DataProvider();

	
	public DisplayModel processResults(CxResultOutput resultsCommandOutput){
		
		DisplayModel projectModel;
		List<CxResult> allResults = resultsCommandOutput.getResults();
		
		List<CxResult> sastResults = new ArrayList<>();
		List<CxResult> scaResults =  new ArrayList<>();
		List<CxResult> kicsResults = new ArrayList<>();
		
		//filtering by each scanner type
		sastResults.addAll(filterResultsByScannerType(allResults, "sast"));
		scaResults.addAll(filterResultsByScannerType(allResults, "sca"));
		kicsResults.addAll(filterResultsByScannerType(allResults, "infrastructure"));
	
		//filtering by each severity type
		Map<String, List<CxResult>> sastMap = new HashMap<>();
		sastMap.put("LOW", filterResultsBySeverity(sastResults, "LOW"));
		sastMap.put("MEDIUM", filterResultsBySeverity(sastResults, "MEDIUM"));
		sastMap.put("HIGH", filterResultsBySeverity(sastResults, "HIGH"));

		List<DisplayModel> sastParentModelList = new ArrayList<DisplayModel>();
		
		 for (Map.Entry mapEntry : sastMap.entrySet()) {
			 
			 List<CxResult> resultList = (List<CxResult>) mapEntry.getValue();
			 
			 List<DisplayModel> sastChildModelList = resultList.stream()
							.map(resultItem -> transform(resultItem)).collect(Collectors.toList());
			
			if(sastChildModelList.size() > 0)
			{
				new DisplayModel();
				DisplayModel sastModel = DisplayModel.builder().name((String) mapEntry.getKey()).children(sastChildModelList).build();
				sastParentModelList.add(sastModel);
			}
		
		 }

		
		
//		List<DisplayModel> sastResultsModel = sastResults.stream()
//				.map(resultItem -> transform(resultItem)).collect(Collectors.toList());
		List<DisplayModel> scaResultsModel = scaResults.stream()
				.map(resultItem -> transform(resultItem)).collect(Collectors.toList());
		List<DisplayModel> kicsResultsModel = kicsResults.stream()
				.map(resultItem -> transform(resultItem)).collect(Collectors.toList());
		
		List<DisplayModel> results = new ArrayList<>();		
		if(sastParentModelList.size()>0)
		{
			new DisplayModel();
			DisplayModel sastModel = DisplayModel.builder().name("SAST").children(sastParentModelList).build();
			results.add(sastModel);
		}
		if(scaResultsModel.size()>0)
		{
			new DisplayModel();
			DisplayModel scaModel = DisplayModel.builder().name("SCA").children(scaResultsModel).build();
			results.add(scaModel);
		}
		if(kicsResultsModel.size()>0)
		{
			new DisplayModel();
			DisplayModel kicsModel = DisplayModel.builder().name("KICS").children(kicsResultsModel).build();
			results.add(kicsModel);	
		}
		
		

		
//		 for (Map.Entry mapEntry : treeItemMap.entrySet()) {
//			 DisplayModel parentDisplayItem = new DisplayModel().builder().name((String) mapEntry.getKey()).children((List<DisplayModel>) mapEntry.getValue()).build();
//			 results.add(parentDisplayItem);
//		 }
	
		
		projectModel = DisplayModel.builder().name("Scan ID can be placed here")
				.type(" Total Issues Found :" +  resultsCommandOutput.getTotalCount()).children(results)
				.build();
		return projectModel;
	}
	
	private DisplayModel transform(CxResult resultItem) {

		String queryName = (resultItem.getData()).getQueryName();		
	
		return DisplayModel.builder().name(queryName).state(resultItem.getState()).status(resultItem.getStatus()).severity(resultItem.getSeverity()).type(resultItem.getType()).build();
	}
	
	private List<CxResult> filterResultsByScannerType(List<CxResult> resultList ,String scannerType) {
		
		List<CxResult> filteredResults = new ArrayList<>();
		
		for (CxResult cxResult : resultList) {
			
			if(cxResult.getType().equalsIgnoreCase(scannerType))
			{
				filteredResults.add(cxResult);
			}
		}
		return filteredResults;
	}
	
	private List<CxResult> filterResultsBySeverity(List<CxResult> resultList ,String severity) {
		
		List<CxResult> filteredResult = new ArrayList<>();
		
		for (CxResult cxResult : resultList) {
			
			if(cxResult.getSeverity().equalsIgnoreCase(severity))
			{
				filteredResult.add(cxResult);
			}
		}
		return filteredResult;
	}
}
