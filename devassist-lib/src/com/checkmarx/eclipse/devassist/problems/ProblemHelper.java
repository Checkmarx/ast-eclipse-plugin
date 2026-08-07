package com.checkmarx.eclipse.devassist.problems;

import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;

import com.checkmarx.eclipse.devassist.basescanner.ScannerService;
import com.checkmarx.eclipse.devassist.model.ScanIssue;

/**
 * Helper class that aggregates all context needed for problem processing.
 *
 * Holds: file, project, document, scanners, issues, holder service, decorator.
 * Used by orchestration flow to pass context to various processing stages.
 *
 * Mirrors JetBrains ProblemHelper with Eclipse types.
 */
public class ProblemHelper {

	private final IFile file;
	private final IProject project;
	private final String filePath;
	private final IDocument document;
	private final List<ScannerService> supportedScanners;
	private final List<ScanIssue> scanIssueList;
	private final ProblemHolderService problemHolderService;
	private final ProblemDecorator problemDecorator;

	/**
	 * Constructor for ProblemHelper.
	 */
	public ProblemHelper(IFile file, IProject project, String filePath,
		IDocument document, List<ScannerService> supportedScanners,
		List<ScanIssue> scanIssueList, ProblemHolderService problemHolderService,
		ProblemDecorator problemDecorator) {
		this.file = file;
		this.project = project;
		this.filePath = filePath;
		this.document = document;
		this.supportedScanners = supportedScanners;
		this.scanIssueList = scanIssueList;
		this.problemHolderService = problemHolderService;
		this.problemDecorator = problemDecorator;
	}

	public IFile getFile() {
		return file;
	}

	public IProject getProject() {
		return project;
	}

	public String getFilePath() {
		return filePath;
	}

	public IDocument getDocument() {
		return document;
	}

	public List<ScannerService> getSupportedScanners() {
		return supportedScanners;
	}

	public List<ScanIssue> getScanIssueList() {
		return scanIssueList;
	}

	public ProblemHolderService getProblemHolderService() {
		return problemHolderService;
	}

	public ProblemDecorator getProblemDecorator() {
		return problemDecorator;
	}

	/**
	 * Builder method enforcing mandatory fields: file, project.
	 *
	 * Mirrors JetBrains ProblemHelper.builder(PsiFile, Project).
	 *
	 * @param file IFile to process
	 * @param project IProject containing the file
	 * @return Builder with file and project set
	 * @throws IllegalArgumentException if file or project is null
	 */
	public static Builder builder(IFile file, IProject project) {
		if (Objects.isNull(file) || Objects.isNull(project)) {
			throw new IllegalArgumentException(
				"Mandatory fields required: file, project");
		}
		return new Builder()
			.file(file)
			.project(project);
	}

	/**
	 * Create a new builder from this ProblemHelper.
	 *
	 * @return Builder with all fields from this instance
	 */
	public Builder toBuilder() {
		return builder(this.file, this.project)
			.filePath(this.filePath)
			.document(this.document)
			.supportedScanners(this.supportedScanners)
			.scanIssueList(this.scanIssueList)
			.problemHolderService(this.problemHolderService)
			.problemDecorator(this.problemDecorator);
	}

	/**
	 * Builder for ProblemHelper.
	 */
	public static class Builder {
		private IFile file;
		private IProject project;
		private String filePath;
		private IDocument document;
		private List<ScannerService> supportedScanners;
		private List<ScanIssue> scanIssueList;
		private ProblemHolderService problemHolderService;
		private ProblemDecorator problemDecorator;

		public Builder file(IFile file) {
			this.file = file;
			return this;
		}

		public Builder project(IProject project) {
			this.project = project;
			return this;
		}

		public Builder filePath(String filePath) {
			this.filePath = filePath;
			return this;
		}

		public Builder document(IDocument document) {
			this.document = document;
			return this;
		}

		public Builder supportedScanners(List<ScannerService> supportedScanners) {
			this.supportedScanners = supportedScanners;
			return this;
		}

		public Builder scanIssueList(List<ScanIssue> scanIssueList) {
			this.scanIssueList = scanIssueList;
			return this;
		}

		public Builder problemHolderService(ProblemHolderService problemHolderService) {
			this.problemHolderService = problemHolderService;
			return this;
		}

		public Builder problemDecorator(ProblemDecorator problemDecorator) {
			this.problemDecorator = problemDecorator;
			return this;
		}

		public ProblemHelper build() {
			return new ProblemHelper(file, project, filePath, document,
				supportedScanners, scanIssueList, problemHolderService, problemDecorator);
		}
	}
}
