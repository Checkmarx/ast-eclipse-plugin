package com.checkmarx.eclipse.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.eclipse.jface.viewers.ComboViewer;

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
	public static void enableComboViewer(ComboViewer comboviewer, boolean enable){
		comboviewer.getCombo().setEnabled(enable);
	}
	
	/**
	 * Set combo viewer placeholder
	 * 
	 * @param comboViewer
	 * @param text
	 */
	public static void setTextForComboViewer(ComboViewer comboViewer , String text) {
		comboViewer.getCombo().setText(text);
	}
}
