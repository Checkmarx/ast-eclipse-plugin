package com.checkmarx.eclipse.devassist.utils;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Objects;

import org.eclipse.core.resources.IFile;
import org.eclipse.e4.ui.css.swt.theme.ITheme;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.backend.SeverityLevel;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.model.Vulnerability;
import com.checkmarx.eclipse.devassist.remediation.NotificationPopup;

/**
 * Utility class for DevAssist operations. Provides methods for encoding,
 * decoding,
 * severity normalization, and file type detection.
 */
public class DevAssistUtils {
	private static final String LOG_TAG = "[DEV-ASSIST-UTILS]";

	public static final String DOCKERFILE = "dockerfile";
	public static final String DOCKER_COMPOSE = "docker-compose";
	public static final String HELM = "helm";
	private static final String THEME_ENGINE_DISPLAY_KEY = "org.eclipse.e4.ui.css.swt.theme";
	private static final String DARK_THEME_ID_FRAGMENT = "dark";

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
				&& DevAssistConstants.CONTAINER_HELM_EXTENSION.contains(fileExtension.toLowerCase());
	}

	/**
	 * Extracts the file extension from a given file path string.
	 *
	 * @param filePath absolute or relative path to the file
	 * @return lower-case extension without the leading dot, or null if no extension
	 *         exists
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

	/**
	 * Get the live IDocument for a file if it is currently open in an editor.
	 *
	 * CRITICAL: Every scanner's scan(String filePath) previously passed a brand-new
	 * empty Document, which forced getFileContent() to fall back to reading the
	 * file
	 * from disk. This meant real-time scans always scanned the last SAVED content,
	 * never the current unsaved edit - causing results to lag one edit/save behind.
	 *
	 * Runs the editor lookup on the UI thread (via syncExec) since scan() is
	 * invoked
	 * from a background Job thread and Workbench/editor APIs are not thread-safe.
	 *
	 * @param filePath Absolute OS file path to look up
	 * @return the live IDocument if the file is open in a text editor, else null
	 */
	public static IDocument getLiveDocumentForFile(String filePath) {
		if (filePath == null || filePath.isBlank()) {
			return null;
		}

		final IDocument[] result = new IDocument[1];
		try {
			Display display = Display.getDefault();
			if (display == null || display.isDisposed()) {
				return null;
			}

			display.syncExec(() -> {
				try {
					IWorkbench workbench = PlatformUI.getWorkbench();
					if (workbench == null || workbench.isClosing()) {
						return;
					}
					for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
						for (IWorkbenchPage page : window.getPages()) {
							for (IEditorReference ref : page.getEditorReferences()) {
								IEditorPart editor = ref.getEditor(false);
								if (!(editor instanceof ITextEditor)) {
									continue;
								}
								ITextEditor textEditor = (ITextEditor) editor;
								try {
									IFile file = textEditor.getEditorInput().getAdapter(IFile.class);
									if (file != null && file.getLocation() != null
											&& file.getLocation().toOSString().equals(filePath)) {
										result[0] = textEditor.getDocumentProvider()
												.getDocument(textEditor.getEditorInput());
										return;
									}
								} catch (Exception e) {
									// Skip editors we can't inspect
								}
							}
						}
					}
				} catch (Exception e) {
					CxLogger.warning(
							LOG_TAG + " Error resolving live document for: " + filePath + " - " + e.getMessage());
				}
			});
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error in getLiveDocumentForFile: " + e.getMessage());
		}

		return result[0];
	}

	public static String getAgentName() {
		// TODO Auto-generated method stub
		return DevAssistConstants.CX_AGENT_NAME;
	}

	/**
	 * Returns the vulnerability details for the given vulnerability id.
	 *
	 * @param scanIssue       scan issue containing vulnerabilities details
	 * @param vulnerabilityId - vulnerability id
	 * @return Vulnerability - vulnerability details
	 */
	public static Vulnerability getVulnerabilityDetails(ScanIssue scanIssue, String vulnerabilityId) {
		if (Objects.isNull(scanIssue.getVulnerabilities()) || scanIssue.getVulnerabilities().isEmpty()) {
			CxLogger.warning(String.format("No vulnerabilities found in scan issue object for scan engine: %s.",
					scanIssue.getScanEngine().name()));
			return null;
		}
		return scanIssue.getVulnerabilities().stream()
				.filter(vulnerability -> vulnerability.getVulnerabilityId().equals(vulnerabilityId))
				.findFirst()
				.orElse(null);
	}

	/**
	 * Copies text to the system clipboard.
	 *
	 * @param text the text to copy
	 * @return true if successful, false otherwise
	 */
	public static boolean copyToClipboard(String text) {
		try {
			Display display = Display.getDefault();
			display.syncExec(() -> {
				Clipboard clipboard = new Clipboard(display);
				try {
					clipboard.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
				} finally {
					clipboard.dispose();
				}
			});
			CxLogger.info("CX#: Content copied to clipboard");
			return true;
		} catch (Exception e) {
			CxLogger.error("CX#: Failed to copy to clipboard: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Copies the given text to the system clipboard and shows a standard
	 * Eclipse notification popup confirming the action.
	 */
	public static boolean copyToClipboardWithNotification(String notificationMessage, String notificationTitle) {
		try {
			Display display = Display.getCurrent() != null ? Display.getCurrent() : Display.getDefault();

			// 1. Copy to clipboard
			Clipboard clipboard = new Clipboard(display);
			try {
				clipboard.setContents(new Object[] { notificationMessage },
						new Transfer[] { TextTransfer.getInstance() });
			} finally {
				clipboard.dispose();
			}

			// 2. Show notification (must run on UI thread)
			display.asyncExec(() -> {
				NotificationPopup popup = new NotificationPopup(display, notificationTitle,
						notificationMessage);
				popup.open();
			});
			return true;
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error copying to clipboard: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Get a Quick fix name for the quick fix action.
	 * Returns the appropriate fix name based on the plugin context.
	 * For Eclipse, defaults to DEV_ASSIST as this plugin is the DevAssist variant.
	 *
	 * @return Quick fix name string
	 */
	public static String getAssistQuickFixName() {
		return DevAssistConstants.FIX_WITH_DEV_ASSIST;
	}

	/**
	 * Returns a resource URL string suitable for embedding in an <img src='...'>
	 * tag
	 * for the given simple icon key (e.g. "critical", "high", "package",
	 * "malicious").
	 *
	 * @param iconPath severity or logical icon path
	 * @return external form URL or empty string if not found
	 */
	public static String themeBasedPNGIconForHtmlImage(String iconPath) {
		if (iconPath == null || iconPath.isEmpty()) {
			return "";
		}
		boolean dark = isDarkTheme();
		String candidate = iconPath;
		if (dark) {
			int extensionIndex = iconPath.lastIndexOf(".png");
			if (extensionIndex >= 0) {
				candidate = iconPath.substring(0, extensionIndex) + "_dark" + iconPath.substring(extensionIndex);
			} else {
				candidate = iconPath + "_dark";
			}
		}
		URL res = DevAssistUtils.class.getResource(candidate);
		if (res == null && dark) {
			// Fallback to the light variant
			candidate = iconPath;
			res = DevAssistUtils.class.getResource(candidate);
		}
		return res != null ? res.toExternalForm() : "";
	}

	/**
	 * Reads Eclipse's own e4 CSS theme engine - the same mechanism the Platform
	 * uses to decide dark vs. light styling - so the scanner image always matches
	 * whatever theme Eclipse is actually rendering with, instead of guessing from
	 * a color sample (which broke down in practice, e.g. custom/high-contrast
	 * themes).
	 */
	public static boolean isDarkTheme() {
		ITheme activeTheme = getActiveTheme();
		if (activeTheme != null && activeTheme.getId() != null) {
			return activeTheme.getId().toLowerCase().contains(DARK_THEME_ID_FRAGMENT);
		}
		return isDarkByBackgroundLuminance();
	}

	private static ITheme getActiveTheme() {
		try {
			Display display = Display.getCurrent();
			Object engineData = display != null ? display.getData(THEME_ENGINE_DISPLAY_KEY) : null;
			if (engineData instanceof IThemeEngine) {
				return ((IThemeEngine) engineData).getActiveTheme();
			}
		} catch (Throwable t) {
			// e4 CSS theming bundle not present/active in this runtime; caller falls back.
			CxLogger.error("Eclipse e4 theme engine unavailable, falling back to color heuristic",
					t instanceof Exception ? (Exception) t : new Exception(t));
		}
		return null;
	}

	/**
	 * Fallback for the rare runtime where the e4 CSS theme engine isn't registered
	 * on the Display: approximate dark mode from the widget background luminance.
	 */
	private static boolean isDarkByBackgroundLuminance() {
		Color background = Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
		double luminance = (0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue())
				/ 255.0;
		return luminance < 0.5;
	}
	
	/**
	 * Returns the container tool configured in the global settings.
	 * @return
	 */
	public static String getContainerTool() {
		try {
			// Prefer the typed preference store access which returns the stored value
			// or an empty string if not present. Fall back to the generic getPref
			// only if needed. Always return a sensible default when empty/null.
			String value = Preferences.STORE.getString(Preferences.PREF_CONTAINERS_TOOL);
			if (value == null || value.isBlank()) {
				// Try the legacy getter which may consult the preference service
				value = Preferences.getPref(Preferences.PREF_CONTAINERS_TOOL);
			}
			return (value == null || value.isBlank()) ? "docker" : value;
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error retrieving container tool preference: " + e.getMessage(), e);
			return "docker"; // default to docker if preference retrieval fails
		}
    }
}
