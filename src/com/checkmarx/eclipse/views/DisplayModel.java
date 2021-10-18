package com.checkmarx.eclipse.views;

import java.util.ArrayList;
import java.util.List;

import com.checkmarx.ast.results.result.Result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class DisplayModel {
	
	public DisplayModel parent;
	public List<DisplayModel> children = new ArrayList<>();
	
    public String name;
    public String type;
    public String severity;
    
      public Result result;
	


}
