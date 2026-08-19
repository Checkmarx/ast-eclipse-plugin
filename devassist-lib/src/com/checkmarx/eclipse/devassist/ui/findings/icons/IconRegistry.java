package com.checkmarx.eclipse.devassist.ui.findings.icons;

import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.checkmarx.eclipse.devassist.backend.Constants;
import com.checkmarx.eclipse.devassist.utils.DevAssistUtils;

/**
 * Registry for managing Checkmarx severity icons.
 * Handles icon loading and caching for different sizes and themes.
 */
public class IconRegistry {

    public enum Size {
        SMALL("_16"),
        MEDIUM("_20");

        private final String suffix;

        Size(String suffix) {
            this.suffix = suffix;
        }

        public String getSuffix() {
            return suffix;
        }
    }

    public enum Severity {
        MALICIOUS("malicious"),
        CRITICAL("critical"),
        HIGH("high"),
        MEDIUM("medium"),
        LOW("low");

        private final String name;

        Severity(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static ImageRegistry imageRegistry;

    static {
        initializeRegistry();
    }

    private static void initializeRegistry() {
        imageRegistry = PlatformUI.getWorkbench().getDisplay() != null
                ? new ImageRegistry(PlatformUI.getWorkbench().getDisplay())
                : new ImageRegistry();
        // Register small icons (16px) - light and dark variants
        registerIcon("malicious_16", "icons/severity_16/malicious.svg");
        registerIcon("malicious_16_dark", "icons/severity_16/malicious_dark.svg");
        registerIcon("critical_16", "icons/severity_16/critical.svg");
        registerIcon("critical_16_dark", "icons/severity_16/critical_dark.svg");
        registerIcon("high_16", "icons/severity_16/high.svg");
        registerIcon("high_16_dark", "icons/severity_16/high_dark.svg");
        registerIcon("medium_16", "icons/severity_16/medium.svg");
        registerIcon("medium_16_dark", "icons/severity_16/medium_dark.svg");
        registerIcon("low_16", "icons/severity_16/low.svg");
        registerIcon("low_16_dark", "icons/severity_16/low_dark.svg");

        // Register medium icons (20px) - light and dark variants
        registerIcon("malicious_20", "icons/severity_20/malicious.svg");
        registerIcon("malicious_20_dark", "icons/severity_20/malicious_dark.svg");
        registerIcon("critical_20", "icons/severity_20/critical.svg");
        registerIcon("critical_20_dark", "icons/severity_20/critical_dark.svg");
        registerIcon("high_20", "icons/severity_20/high.svg");
        registerIcon("high_20_dark", "icons/severity_20/high_dark.svg");
        registerIcon("medium_20", "icons/severity_20/medium.svg");
        registerIcon("medium_20_dark", "icons/severity_20/medium_dark.svg");
        registerIcon("low_20", "icons/severity_20/low.svg");
        registerIcon("low_20_dark", "icons/severity_20/low_dark.svg");

        // Register base icons - light and dark variants
        registerIcon("malicious", "icons/severity/malicious.svg");
        registerIcon("malicious_dark", "icons/severity/malicious_dark.svg");
        registerIcon("critical", "icons/severity/critical.svg");
        registerIcon("critical_dark", "icons/severity/critical_dark.svg");
        registerIcon("high", "icons/severity/high.svg");
        registerIcon("high_dark", "icons/severity/high_dark.svg");
        registerIcon("medium", "icons/severity/medium.svg");
        registerIcon("medium_dark", "icons/severity/medium_dark.svg");
        registerIcon("low", "icons/severity/low.svg");
        registerIcon("low_dark", "icons/severity/low_dark.svg");

        registerIcon("star_action", "icons/start-action.svg");
        registerIcon("devassistBadge", "icons/devassist_badge.svg");
    }

    private static void registerIcon(String key, String path) {
        // Load icons from devassist module instead of main plugin
        imageRegistry.put(key, AbstractUIPlugin.imageDescriptorFromPlugin("com.checkmarx.eclipse.devassist", path));
    }

    /**
     * Get icon for a severity level and size.
     *
     * @param severity Severity level (case-insensitive)
     * @param size     Icon size
     * @return Image instance or null if not found
     */
    public static Image getIcon(String severity, Size size) {
        if (severity == null) {
            return null;
        }

        String key = severity.toLowerCase() + size.getSuffix();
        return imageRegistry.get(key);
    }

    /**
     * Get theme-aware icon for a severity level and size.
     * Returns dark variant in dark theme, light variant in light theme.
     *
     * @param severity Severity level (case-insensitive)
     * @param size     Icon size
     * @return Image instance or null if not found
     */
    public static Image getThemeAwareIcon(String severity, Size size) {
        if (severity == null) {
            return null;
        }

        String key = severity.toLowerCase() + size.getSuffix();

        // Append _dark suffix if dark theme is active
        if (DevAssistUtils.isDarkTheme()) {
            key += "_dark";
        }

        return imageRegistry.get(key);
    }

    /**
     * Get icon for a severity level with default small size.
     *
     * @param severity Severity level
     * @return Image instance or null if not found
     */
    public static Image getIcon(String severity) {
        return getIcon(severity, Size.SMALL);
    }

    /**
     * Get image registry.
     *
     * @return ImageRegistry instance
     */
    public static ImageRegistry getRegistry() {
        return imageRegistry;
    }
}
