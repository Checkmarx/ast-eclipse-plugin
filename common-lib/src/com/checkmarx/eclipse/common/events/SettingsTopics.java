package com.checkmarx.eclipse.common.events;

/**
 * Event broker topic names shared across bundles (main plugin publishes,
 * devassist and the main view subscribe).
 */
public class SettingsTopics {

	public static final String TOPIC_APPLY_SETTINGS = "ApplySettings";

	private SettingsTopics() {
	}
}
