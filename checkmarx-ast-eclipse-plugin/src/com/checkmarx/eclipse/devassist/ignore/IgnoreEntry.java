package com.checkmarx.eclipse.devassist.ignore;

import java.util.ArrayList;
import java.util.List;

import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single ignored finding, persisted to disk.
 *
 * Mirrors JetBrains' IgnoreEntry (devassist-lib ignore package), adapted to Eclipse's
 * ScanIssue model: identity fields live directly on ScanIssue (packageManager, packageVersion,
 * similarityId, ruleId, secretValue, imageTag) rather than on a per-vulnerability sub-object,
 * so one IgnoreEntry corresponds to one ScanIssue-level stable key.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IgnoreEntry {

	private List<FileReference> files = new ArrayList<>();
	private ScanEngine type;
	private String title;
	private String severity;
	private String description;
	private String dateAdded;

	// Engine-specific identity fields (only the relevant subset is populated per engine)
	private String packageManager;
	private String packageVersion;
	private String similarityId;
	private Integer ruleId;
	private String secretValue;
	private String imageTag;

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

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
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

	public String getPackageManager() {
		return packageManager;
	}

	public void setPackageManager(String packageManager) {
		this.packageManager = packageManager;
	}

	public String getPackageVersion() {
		return packageVersion;
	}

	public void setPackageVersion(String packageVersion) {
		this.packageVersion = packageVersion;
	}

	public String getSimilarityId() {
		return similarityId;
	}

	public void setSimilarityId(String similarityId) {
		this.similarityId = similarityId;
	}

	public Integer getRuleId() {
		return ruleId;
	}

	public void setRuleId(Integer ruleId) {
		this.ruleId = ruleId;
	}

	public String getSecretValue() {
		return secretValue;
	}

	public void setSecretValue(String secretValue) {
		this.secretValue = secretValue;
	}

	public String getImageTag() {
		return imageTag;
	}

	public void setImageTag(String imageTag) {
		this.imageTag = imageTag;
	}

	/**
	 * A single (file, line) occurrence of the ignored finding.
	 *
	 * path is the ABSOLUTE OS path (not workspace-relative like JetBrains' single-project
	 * model) since an Eclipse workspace can contain multiple projects with no common root.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static final class FileReference {
		private String path;
		private boolean active;
		private int line;

		public FileReference() {
		}

		public FileReference(String path, boolean active, int line) {
			this.path = path;
			this.active = active;
			this.line = line;
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

		public int getLine() {
			return line;
		}

		public void setLine(int line) {
			this.line = line;
		}
	}
}
