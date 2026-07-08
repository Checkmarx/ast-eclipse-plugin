package com.checkmarx.eclipse.views.problems.model;

import java.util.Objects;

import com.checkmarx.eclipse.enums.Severity;

/**
 * Immutable, UI-agnostic representation of a single security finding that has
 * to be surfaced in Eclipse's Problems View.
 *
 * <p>
 * The shape of this object intentionally mirrors a real Checkmarx scan result
 * (finding id, rule id, message, file, line, severity, status) so that the
 * mock provider used in Phase 1 can later be replaced by a real scan-backed
 * provider without touching any of the marker / publisher / navigation code.
 * </p>
 *
 * <p>
 * This type has <b>no</b> dependency on Eclipse resource APIs on purpose: it is
 * pure data. Mapping a {@code ScanProblem} to an {@code IResource}/{@code IMarker}
 * is the responsibility of the marker layer.
 * </p>
 */
public final class ScanProblem {

	private final String id;
	private final String ruleId;
	private final String message;
	private final String fileName;
	private final int line;
	private final int column;
	private final Severity severity;
	private final String status;

	private ScanProblem(Builder builder) {
		this.id = builder.id;
		this.ruleId = builder.ruleId;
		this.message = builder.message;
		this.fileName = builder.fileName;
		this.line = builder.line;
		this.column = builder.column;
		this.severity = builder.severity;
		this.status = builder.status;
	}

	public String getId() {
		return id;
	}

	public String getRuleId() {
		return ruleId;
	}

	public String getMessage() {
		return message;
	}

	/**
	 * @return the (possibly relative) file name / path the finding refers to.
	 */
	public String getFileName() {
		return fileName;
	}

	public int getLine() {
		return line;
	}

	public int getColumn() {
		return column;
	}

	public Severity getSeverity() {
		return severity;
	}

	public String getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ScanProblem)) {
			return false;
		}
		ScanProblem that = (ScanProblem) o;
		return Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public String toString() {
		return "ScanProblem[" + severity + " " + message + " @ " + fileName + ":" + line + "]";
	}

	/**
	 * Fluent builder. Sensible defaults are applied so callers only set the
	 * attributes they care about.
	 */
	public static final class Builder {

		private String id;
		private String ruleId = "";
		private String message = "";
		private String fileName = "";
		private int line = 1;
		private int column = 1;
		private Severity severity = Severity.INFO;
		private String status = "TO_VERIFY";

		public Builder(String id) {
			this.id = id;
		}

		public Builder ruleId(String value) {
			this.ruleId = value;
			return this;
		}

		public Builder message(String value) {
			this.message = value;
			return this;
		}

		public Builder fileName(String value) {
			this.fileName = value;
			return this;
		}

		public Builder line(int value) {
			this.line = value;
			return this;
		}

		public Builder column(int value) {
			this.column = value;
			return this;
		}

		public Builder severity(Severity value) {
			this.severity = value;
			return this;
		}

		public Builder status(String value) {
			this.status = value;
			return this;
		}

		public ScanProblem build() {
			return new ScanProblem(this);
		}
	}
}
