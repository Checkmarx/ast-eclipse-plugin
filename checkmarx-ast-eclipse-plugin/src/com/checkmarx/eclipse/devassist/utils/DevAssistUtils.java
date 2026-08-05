package com.checkmarx.eclipse.devassist.utils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import org.eclipse.jgit.annotations.NonNull;

import com.checkmarx.eclipse.devassist.backend.SeverityLevel;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Utility class for DevAssist operations. Provides methods for encoding, decoding,
 * severity normalization, and file type detection.
 */
public class DevAssistUtils {
	private static final String LOG_TAG = "[DEV-ASSIST-UTILS]";

	public static final String DOCKERFILE = "dockerfile";
	public static final String DOCKER_COMPOSE = "docker-compose";
	public static final String HELM = "helm";

	private DevAssistUtils() {
		// Private constructor to prevent instantiation
	}

	/**
	 * Generate a unique ID for scan issue based on line, rule info, and file name.
	 * Mirrors JetBrains pattern: base64(line + ruleInfo + fileName)
	 *
	 * @param line     Line number where issue occurs
	 * @param ruleInfo Rule ID + Rule Name concatenated
	 * @param fileName Name of the file (not full path, just filename)
	 * @return Deterministic base64-encoded ID
	 */
	public static String generateUniqueId(int line, String ruleInfo, String fileName) {
		String input = line + "|" + ruleInfo + "|" + fileName;
		return encodeBase64(input);
	}

	/**
	 * Encode the input string using Base64. Uses UTF-8 encoding to match JetBrains
	 * implementation.
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
	 * Decode a Base64 string back to its original form. Used for debugging or ID
	 * verification.
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

	/**
	 * Normalize severity string to match SeverityLevel enum format (capitalized).
	 *
	 * @param severity Raw severity string from API
	 * @return Normalized severity in SeverityLevel format, or original if no match
	 */
	public static String normalizeSeverity(String severity) {
		if (severity == null || severity.isEmpty()) {
			return "Unknown";
		}
		String upper = severity.toUpperCase();
		switch (upper) {
		case "MALICIOUS":
			return SeverityLevel.MALICIOUS.getSeverity();
		case "CRITICAL":
			return SeverityLevel.CRITICAL.getSeverity();
		case "HIGH":
			return SeverityLevel.HIGH.getSeverity();
		case "MEDIUM":
			return SeverityLevel.MEDIUM.getSeverity();
		case "LOW":
			return SeverityLevel.LOW.getSeverity();
		case "UNKNOWN":
			return SeverityLevel.UNKNOWN.getSeverity();
		case "OK":
			return SeverityLevel.OK.getSeverity();
		case "IGNORED":
			return SeverityLevel.IGNORED.getSeverity();
		default:
			return severity;
		}
	}

	/**
	 * Check if severity represents a problem (displayable finding).
	 *
	 * @param severity Severity string (case-insensitive)
	 * @return true if severity is a problem, false if OK/UNKNOWN/IGNORED
	 */
	public static boolean isProblem(String severity) {
		if (severity == null) {
			return false;
		}
		return !severity.equalsIgnoreCase(SeverityLevel.OK.getSeverity())
				&& !severity.equalsIgnoreCase(SeverityLevel.UNKNOWN.getSeverity())
				&& !severity.equalsIgnoreCase(SeverityLevel.IGNORED.getSeverity());
	}

	/**
	 * Check if the given file path corresponds to a Docker Compose file.
	 *
	 * @param filePath Full path to the file
	 * @return true if it's a Docker Compose file, false otherwise
	 */
	public static boolean isDockerComposeFile(@NonNull String filePath) {
		return Paths.get(filePath).getFileName().toString().toLowerCase().contains("docker-compose");
	}

	/**
	 * Check if the given file path corresponds to a Dockerfile.
	 *
	 * @param filePath Full path to the file
	 * @return true if it's a Dockerfile, false otherwise
	 */
	public static boolean isDockerFile(@NonNull String filePath) {
		return Paths.get(filePath).getFileName().toString().toLowerCase().contains("dockerfile");
	}

	/**
	 * Check if the given file path is a YAML file.
	 *
	 * @param filePath Full path to the file
	 * @return true if it's a YAML file, false otherwise
	 */
	public static boolean isYamlFile(String filePath) {
		if (Objects.isNull(filePath) || filePath.isBlank()) {
			return false;
		}
		String fileExtension = getFileExtension(filePath);
		return Objects.nonNull(fileExtension)
				&&  DevAssistConstants.CONTAINER_HELM_EXTENSION.contains(fileExtension.toLowerCase());
	}

	/**
	 * Extracts the file extension from a given file path string.
	 *
	 * @param filePath absolute or relative path to the file
	 * @return lower-case extension without the leading dot, or null if no extension exists
	 */
	public static String getFileExtension(String filePath) {
		if (filePath == null || filePath.isBlank()) {
			return null;
		}
		int lastDot = filePath.lastIndexOf('.');
		int lastSeparator = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));

		if (lastDot > lastSeparator && lastDot < filePath.length() - 1) {
			return filePath.substring(lastDot + 1).toLowerCase();
		}
		return null;
	}
}
