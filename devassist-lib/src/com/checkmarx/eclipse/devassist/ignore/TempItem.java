package com.checkmarx.eclipse.devassist.ignore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TempItem {
    private String Title;
    private String SecretValue;
    private String SimilarityID;
    private String FileName;
    private Integer Line;
    private Integer RuleID;
    private String PackageManager;
    private String PackageName;
    private String PackageVersion;
    private String ImageName;
    private String ImageTag;

    public TempItem() {
    }


    public static TempItem forOss(String pm, String name, String version) {
        TempItem t = new TempItem();
        t.PackageManager = pm;
        t.PackageName = name;
        t.PackageVersion = version;
        return t;
    }

    public static TempItem forSecret(String title, String secretValue) {
        TempItem t = new TempItem();
        t.Title = title;
        t.SecretValue = secretValue;
        return t;
    }

    public static TempItem forIac(String title, String similarityId) {
        TempItem t = new TempItem();
        t.Title = title;
        t.SimilarityID = similarityId;
        return t;
    }

    public static TempItem forContainer(String imageName, String imageTag) {
        TempItem t = new TempItem();
        t.ImageName = imageName;
        t.ImageTag = imageTag;
        return t;
    }

    public static TempItem forAsca(String fileName, Integer line, Integer ruleId) {
        TempItem t = new TempItem();
        t.FileName = fileName;
        t.Line = line;
        t.RuleID = ruleId;
        return t;
    }


	public String getTitle() {
		return Title;
	}


	public void setTitle(String title) {
		Title = title;
	}


	public String getSecretValue() {
		return SecretValue;
	}


	public void setSecretValue(String secretValue) {
		SecretValue = secretValue;
	}


	public String getSimilarityID() {
		return SimilarityID;
	}


	public void setSimilarityID(String similarityID) {
		SimilarityID = similarityID;
	}


	public String getFileName() {
		return FileName;
	}


	public void setFileName(String fileName) {
		FileName = fileName;
	}


	public Integer getLine() {
		return Line;
	}


	public void setLine(Integer line) {
		Line = line;
	}


	public Integer getRuleID() {
		return RuleID;
	}


	public void setRuleID(Integer ruleID) {
		RuleID = ruleID;
	}


	public String getPackageManager() {
		return PackageManager;
	}


	public void setPackageManager(String packageManager) {
		PackageManager = packageManager;
	}


	public String getPackageName() {
		return PackageName;
	}


	public void setPackageName(String packageName) {
		PackageName = packageName;
	}


	public String getPackageVersion() {
		return PackageVersion;
	}


	public void setPackageVersion(String packageVersion) {
		PackageVersion = packageVersion;
	}


	public String getImageName() {
		return ImageName;
	}


	public void setImageName(String imageName) {
		ImageName = imageName;
	}


	public String getImageTag() {
		return ImageTag;
	}


	public void setImageTag(String imageTag) {
		ImageTag = imageTag;
	}
    
    
}
