package com.checkmarx.eclipse.devassist.backend;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Utility class for DevAssist backend operations.
 * Mirrors JetBrains DevAssistUtils pattern.
 */
public class DevAssistUtils {
	private static final String LOG_TAG = "[DEV-ASSIST-UTILS]";

	private DevAssistUtils() {
		// Private constructor to prevent instantiation
	}

	/**
	 * Generate a unique ID for scan issue based on line, rule info, and file name.
	 * Mirrors JetBrains pattern: base64(line + ruleInfo + fileName)
	 *
	 * @param line Line number where issue occurs
	 * @param ruleInfo Rule ID + Rule Name concatenated
	 * @param fileName Name of the file (not full path, just filename)
	 * @return Deterministic base64-encoded ID
	 */
	public static String generateUniqueId(int line, String ruleInfo, String fileName) {
		// Concatenate components with delimiter for clarity
		String input = line + "|" + ruleInfo + "|" + fileName;
		return encodeBase64(input);
	}

	/**
	 * Encode the input string using Base64.
	 * Uses UTF-8 encoding to match JetBrains implementation.
	 *
	 * @param input String to be encoded
	 * @return Base64 encoded string
	 */
	public static String encodeBase64(String input) {
		if (input == null || input.isEmpty()) {
			CxLogger.warning(LOG_TAG + " Attempting to encode null or empty string");
			return "";
		}
		try {
			return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error encoding string to Base64: " + e.getMessage(), e);
			return "";
		}
	}

	/**
	 * Decode a Base64 string back to its original form.
	 * Used for debugging or ID verification.
	 *
	 * @param encoded Base64 encoded string
	 * @return Decoded string
	 */
	public static String decodeBase64(String encoded) {
		if (encoded == null || encoded.isEmpty()) {
			return "";
		}
		try {
			return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error decoding Base64 string: " + e.getMessage());
			return "";
		}
	}
}
