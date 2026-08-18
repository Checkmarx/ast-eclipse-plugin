package com.checkmarx.eclipse.devassist.common;

/**
 * Configuration object for scanner engines.
 * Defines settings and messages for each scanner type.
 */
public class ScannerConfig {

	private final String engineName;
	private final String configSection;
	private final String activateKey;
	private final String enabledMessage;
	private final String disabledMessage;
	private final String errorMessage;

	private ScannerConfig(Builder builder) {
		this.engineName = builder.engineName;
		this.configSection = builder.configSection;
		this.activateKey = builder.activateKey;
		this.enabledMessage = builder.enabledMessage;
		this.disabledMessage = builder.disabledMessage;
		this.errorMessage = builder.errorMessage;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getEngineName() {
		return engineName;
	}

	public String getConfigSection() {
		return configSection;
	}

	public String getActivateKey() {
		return activateKey;
	}

	public String getEnabledMessage() {
		return enabledMessage;
	}

	public String getDisabledMessage() {
		return disabledMessage;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public static class Builder {
		private String engineName;
		private String configSection;
		private String activateKey;
		private String enabledMessage;
		private String disabledMessage;
		private String errorMessage;

		public Builder engineName(String engineName) {
			this.engineName = engineName;
			return this;
		}

		public Builder configSection(String configSection) {
			this.configSection = configSection;
			return this;
		}

		public Builder activateKey(String activateKey) {
			this.activateKey = activateKey;
			return this;
		}

		public Builder enabledMessage(String enabledMessage) {
			this.enabledMessage = enabledMessage;
			return this;
		}

		public Builder disabledMessage(String disabledMessage) {
			this.disabledMessage = disabledMessage;
			return this;
		}

		public Builder errorMessage(String errorMessage) {
			this.errorMessage = errorMessage;
			return this;
		}

		public ScannerConfig build() {
			return new ScannerConfig(this);
		}
	}
}
