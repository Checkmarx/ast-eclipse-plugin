package com.checkmarx.eclipse.views;

import java.util.ArrayList;
import java.util.List;

import com.checkmarx.ast.results.structure.CxResultComments;
import com.checkmarx.ast.results.structure.CxResultData;
import com.checkmarx.ast.results.structure.CxResultVulnerabilityDetails;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisplayModel {
	
	public DisplayModel parent;
	public List<DisplayModel> children = new ArrayList<>();
	
    public String name;
    public String type;
    public String severity;
    
    public String status;
    public String state;
    public String line;
    public String column;
    public String sourceNode;
    public String sourceFile;
    public String sinkNode;
    public String sinkFile;
	


}
