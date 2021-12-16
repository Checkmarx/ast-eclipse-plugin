package com.checkmarx.eclipse.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;

import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.actions.ActionName;
import com.checkmarx.eclipse.views.filters.FilterState;

public class PluginUtils {

	private static final String PARAM_TIMESTAMP_PATTERN = "yyyy-MM-dd | HH:mm:ss";
	private static final String PARAM_SCAN_ID_VALID_FORMAT = "[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[0-9a-f]{12}";

	/**
	 * Converts a String timestamp to a specific format
	 * 
	 * @param timestamp
	 * @return
	 */
	public static String convertStringTimeStamp(String timestamp) {
		String parsedDate = null;

		try {

			Instant instant = Instant.parse(timestamp);

			DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(PARAM_TIMESTAMP_PATTERN).withZone(ZoneId.systemDefault());
			parsedDate = dateTimeFormatter.format(instant);
		} catch (Exception e) {
			System.out.println(e);
			return timestamp;
		}

		return parsedDate;
	}

	/**
	 * Validate scan id format
	 * 
	 * @param scanId
	 * @return
	 */
	public static boolean validateScanIdFormat(String scanId) {
		return scanId.matches(PARAM_SCAN_ID_VALID_FORMAT);
	}

	/**
	 * Enables a combo viewer
	 * 
	 * @param comboviewer
	 * @param enable
	 */
	public static void enableComboViewer(ComboViewer comboviewer, boolean enable) {
		comboviewer.getCombo().setEnabled(enable);
	}

	/**
	 * Set combo viewer placeholder
	 * 
	 * @param comboViewer
	 * @param text
	 */
	public static void setTextForComboViewer(ComboViewer comboViewer, String text) {
		comboViewer.getCombo().setText(text);
		comboViewer.getCombo().update();
	}

	/**
	 * Enable/Disable filter actions
	 * 
	 * @param filterActions
	 */
	public static void updateFiltersEnabledAndCheckedState(List<Action> filterActions) {
		for (Action action : filterActions) {
			// avoid to disable group by severity and group by query name actions
			if (!action.getId().equals(ActionName.GROUP_BY_SEVERITY.name()) && !action.getId().equals(ActionName.GROUP_BY_QUERY_NAME.name())) {
				action.setEnabled(DataProvider.getInstance().getCurrentScanId() != null);
			}

			action.setChecked(FilterState.isSeverityEnabled(action.getId()));
		}
	}
	
	/**
	 * Create a display model to be presented in the tree
	 * 
	 * @param message
	 * @return
	 */
	public static DisplayModel message(String message) {
		return new DisplayModel.DisplayModelBuilder(message).build();
	}
	
	/**
	 * Show message in the tree
	 * 
	 * @param message
	 */
	public static void showMessage(DisplayModel rootModel, TreeViewer viewer, String message) {
		rootModel.children.clear();
		rootModel.children.add(PluginUtils.message(message));
		viewer.refresh();
	}
}
