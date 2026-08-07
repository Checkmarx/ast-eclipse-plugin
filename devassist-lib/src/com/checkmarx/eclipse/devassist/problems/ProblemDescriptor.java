package com.checkmarx.eclipse.devassist.problems;

import java.util.List;

import org.eclipse.core.resources.IFile;

import com.checkmarx.eclipse.devassist.model.ScanIssue;

/**
 * Eclipse equivalent of JetBrains ProblemDescriptor.
 *
 * Represents a detected problem/issue in a file with:
 * - Issue metadata (file, scan issue, line number)
 * - Human-readable description
 * - Associated fixes for the problem
 *
 * Option B: Full structure with fixes list, mirroring JetBrains.
 */
public class ProblemDescriptor {

	private final IFile file;
	private final ScanIssue scanIssue;
	private final int lineNumber;
	private final String description;
	private final List<Object> fixes;

	/**
	 * Constructor for ProblemDescriptor.
	 *
	 * @param file The file being analyzed
	 * @param scanIssue The scan issue
	 * @param lineNumber Line number of the issue
	 * @param description Human-readable description
	 * @param fixes Associated fixes
	 */
	public ProblemDescriptor(IFile file, ScanIssue scanIssue, int lineNumber,
		String description, List<Object> fixes) {
		this.file = file;
		this.scanIssue = scanIssue;
		this.lineNumber = lineNumber;
		this.description = description;
		this.fixes = fixes;
	}

	public IFile getFile() {
		return file;
	}

	public ScanIssue getScanIssue() {
		return scanIssue;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public String getDescription() {
		return description;
	}

	public List<Object> getFixes() {
		return fixes;
	}

	/**
	 * Get the problem fixes as an array.
	 *
	 * @return Array of fixes (or empty array if none)
	 */
	public Object[] getFixesArray() {
		return fixes != null ? fixes.toArray() : new Object[0];
	}

	/**
	 * Builder for ProblemDescriptor.
	 */
	public static class Builder {
		private IFile file;
		private ScanIssue scanIssue;
		private int lineNumber;
		private String description;
		private List<Object> fixes;

		public Builder file(IFile file) {
			this.file = file;
			return this;
		}

		public Builder scanIssue(ScanIssue scanIssue) {
			this.scanIssue = scanIssue;
			return this;
		}

		public Builder lineNumber(int lineNumber) {
			this.lineNumber = lineNumber;
			return this;
		}

		public Builder description(String description) {
			this.description = description;
			return this;
		}

		public Builder fixes(List<Object> fixes) {
			this.fixes = fixes;
			return this;
		}

		public ProblemDescriptor build() {
			return new ProblemDescriptor(file, scanIssue, lineNumber, description, fixes);
		}
	}

	public static Builder builder() {
		return new Builder();
	}
}
