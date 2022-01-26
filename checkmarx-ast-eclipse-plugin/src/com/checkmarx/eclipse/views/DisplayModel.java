package com.checkmarx.eclipse.views;

import java.util.ArrayList;
import java.util.List;

import com.checkmarx.ast.results.result.Result;

public class DisplayModel {
	
	public DisplayModel parent;
	public List<DisplayModel> children = new ArrayList<>();
	
    public String name;
    public String type;
    public String severity;
    public String state;
    
    public Result result;

    private DisplayModel(DisplayModelBuilder builder) {
    	this.name = builder.name;
    	this.type = builder.type;
    	this.severity = builder.severity;
    	this.result = builder.result;
    	this.children = builder.children;
    	this.state = builder.state;    	
    }
    
	public DisplayModel getParent() {
		return parent;
	}

	public void setParent(DisplayModel parent) {
		this.parent = parent;
	}

	public List<DisplayModel> getChildren() {
		return children;
	}

	public void setChildren(List<DisplayModel> children) {
		this.children = children;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSeverity() {
		return severity;
	}

	public void setSeverity(String severity) {
		this.severity = severity;
	}
	
	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public Result getResult() {
		return result;
	}

	public void setResult(Result result) {
		this.result = result;
	}
	
	public static class DisplayModelBuilder{
		public DisplayModel parent;
		public List<DisplayModel> children = new ArrayList<>();
		
	    public String name;
	    public String type;
	    public String severity;
	    public String state;
	    
	    public Result result;

		public DisplayModelBuilder(String name) {
			this.name = name;
		}

		public DisplayModelBuilder setParent(DisplayModel parent) {
			this.parent = parent;
			return this;
		}

		public DisplayModelBuilder setChildren(List<DisplayModel> children) {
			this.children = children;
			return this;
		}

		public DisplayModelBuilder setName(String name) {
			this.name = name;
			return this;
		}

		public DisplayModelBuilder setType(String type) {
			this.type = type;
			return this;
		}

		public DisplayModelBuilder setSeverity(String severity) {
			this.severity = severity;
			return this;
		}

		public DisplayModelBuilder setResult(Result result) {
			this.result = result;
			return this;
		}
		
		public DisplayModelBuilder setSate(String state) {
			this.state = state;
			return this;
		}
	    
		public DisplayModel build() {
			return new DisplayModel(this);
		}
	}
}
