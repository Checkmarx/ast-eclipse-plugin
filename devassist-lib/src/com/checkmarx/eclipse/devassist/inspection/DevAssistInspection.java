package com.checkmarx.eclipse.devassist.inspection;

/**
 * Inspection metadata and registry class.
 *
 * In JetBrains: extends LocalInspectionTool with checkFile() implementation.
 * In Eclipse: serves as metadata holder for inspection framework integration.
 *
 * Provides inspection ID, name, and grouping constants for registration.
 * Can be extended with inspection framework hooks in future.
 */
public class DevAssistInspection {

	// Inspection identity constants
	private static final String INSPECTION_ID = "com.checkmarx.eclipse.devassist.inspection";
	private static final String INSPECTION_NAME = "Checkmarx Developer Assist";
	private static final String INSPECTION_GROUP = "Checkmarx";

	/**
	 * Get the unique identifier for this inspection.
	 *
	 * @return Inspection ID for registration and lookup
	 */
	public String getInspectionId() {
		return INSPECTION_ID;
	}

	/**
	 * Get the human-readable name for this inspection.
	 *
	 * @return Inspection name for display in UI
	 */
	public String getInspectionName() {
		return INSPECTION_NAME;
	}

	/**
	 * Get the inspection group/category.
	 *
	 * @return Inspection group for organization in preferences
	 */
	public String getInspectionGroup() {
		return INSPECTION_GROUP;
	}
}
