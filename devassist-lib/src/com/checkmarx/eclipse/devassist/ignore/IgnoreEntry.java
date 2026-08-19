package com.checkmarx.eclipse.devassist.ignore;

import com.checkmarx.eclipse.devassist.utils.ScanEngine;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IgnoreEntry {
    public List<FileReference> files = new ArrayList<>();
    public ScanEngine type; // or enum
    public String similarityId;
    public String packageManager;
    public String packageName;
    public String packageVersion;
    public Integer ruleId;
    public String imageName;
    public String imageTag;
    public String severity;
    public String description;
    public String dateAdded;
    public String title;
    public String secretValue;

    public IgnoreEntry() {
    }

    
    
    public List<FileReference> getFiles() {
		return files;
	}



	public void setFiles(List<FileReference> files) {
		this.files = files;
	}



	public ScanEngine getType() {
		return type;
	}



	public void setType(ScanEngine type) {
		this.type = type;
	}



	public String getSimilarityId() {
		return similarityId;
	}



	public void setSimilarityId(String similarityId) {
		this.similarityId = similarityId;
	}



	public String getPackageManager() {
		return packageManager;
	}



	public void setPackageManager(String packageManager) {
		this.packageManager = packageManager;
	}



	public String getPackageName() {
		return packageName;
	}



	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}



	public String getPackageVersion() {
		return packageVersion;
	}



	public void setPackageVersion(String packageVersion) {
		this.packageVersion = packageVersion;
	}



	public Integer getRuleId() {
		return ruleId;
	}



	public void setRuleId(Integer ruleId) {
		this.ruleId = ruleId;
	}



	public String getImageName() {
		return imageName;
	}



	public void setImageName(String imageName) {
		this.imageName = imageName;
	}



	public String getImageTag() {
		return imageTag;
	}



	public void setImageTag(String imageTag) {
		this.imageTag = imageTag;
	}



	public String getSeverity() {
		return severity;
	}



	public void setSeverity(String severity) {
		this.severity = severity;
	}



	public String getDescription() {
		return description;
	}



	public void setDescription(String description) {
		this.description = description;
	}



	public String getDateAdded() {
		return dateAdded;
	}



	public void setDateAdded(String dateAdded) {
		this.dateAdded = dateAdded;
	}



	public String getTitle() {
		return title;
	}



	public void setTitle(String title) {
		this.title = title;
	}



	public String getSecretValue() {
		return secretValue;
	}



	public void setSecretValue(String secretValue) {
		this.secretValue = secretValue;
	}



	public static final class FileReference {
        public String path;
        public boolean active;
        public Integer line;
        public String problematicLine;

        public FileReference() {
        }

        public FileReference(String relativePath, boolean b, int line, String problematicLine) {
            this.path = relativePath;
            this.active = b;
            this.line = line;
            if (problematicLine != null && !problematicLine.isEmpty()) {
                this.problematicLine = problematicLine;
            }
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public Integer getLine() {
            return line;
        }

        public void setLine(Integer line) {
            this.line = line;
        }

        public String getProblematicLine() {
            return problematicLine;
        }

        public void setProblematicLine(String problematicLine) {
            this.problematicLine = problematicLine;
        }
    }
}
