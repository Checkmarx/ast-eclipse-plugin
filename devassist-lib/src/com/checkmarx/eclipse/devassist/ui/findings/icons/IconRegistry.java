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

        // Register card icons for ignored findings (package, secret, containers, vulnerability)
        // Package icons (OSS)
        registerIcon("card_package_critical", "icons/ignored_card/card-package-critical.svg");
        registerIcon("card_package_critical_dark", "icons/ignored_card/card-package-critical_dark.svg");
        registerIcon("card_package_high", "icons/ignored_card/card-package-high.svg");
        registerIcon("card_package_high_dark", "icons/ignored_card/card-package-high_dark.svg");
        registerIcon("card_package_medium", "icons/ignored_card/card-package-medium.svg");
        registerIcon("card_package_medium_dark", "icons/ignored_card/card-package-medium_dark.svg");
        registerIcon("card_package_low", "icons/ignored_card/card-package-low.svg");
        registerIcon("card_package_low_dark", "icons/ignored_card/card-package-low_dark.svg");
        registerIcon("card_package_malicious", "icons/ignored_card/card-package-malicious.svg");
        registerIcon("card_package_malicious_dark", "icons/ignored_card/card-package-malicious_dark.svg");

        // Secret icons (SECRETS)
        registerIcon("card_secret_critical", "icons/ignored_card/card-secret-critical.svg");
        registerIcon("card_secret_critical_dark", "icons/ignored_card/card-secret-critical_dark.svg");
        registerIcon("card_secret_high", "icons/ignored_card/card-secret-high.svg");
        registerIcon("card_secret_high_dark", "icons/ignored_card/card-secret-high_dark.svg");
        registerIcon("card_secret_medium", "icons/ignored_card/card-secret-medium.svg");
        registerIcon("card_secret_medium_dark", "icons/ignored_card/card-secret-medium_dark.svg");
        registerIcon("card_secret_low", "icons/ignored_card/card-secret-low.svg");
        registerIcon("card_secret_low_dark", "icons/ignored_card/card-secret-low_dark.svg");
        registerIcon("card_secret_malicious", "icons/ignored_card/card-secret-malicious.svg");
        registerIcon("card_secret_malicious_dark", "icons/ignored_card/card-secret-malicious_dark.svg");

        // Container icons (CONTAINERS)
        registerIcon("card_containers_critical", "icons/ignored_card/card-containers-critical.svg");
        registerIcon("card_containers_critical_dark", "icons/ignored_card/card-containers-critical_dark.svg");
        registerIcon("card_containers_high", "icons/ignored_card/card-containers-high.svg");
        registerIcon("card_containers_high_dark", "icons/ignored_card/card-containers-high_dark.svg");
        registerIcon("card_containers_medium", "icons/ignored_card/card-containers-medium.svg");
        registerIcon("card_containers_medium_dark", "icons/ignored_card/card-containers-medium_dark.svg");
        registerIcon("card_containers_low", "icons/ignored_card/card-containers-low.svg");
        registerIcon("card_containers_low_dark", "icons/ignored_card/card-containers-low_dark.svg");
        registerIcon("card_containers_malicious", "icons/ignored_card/card-containers-malicious.svg");
        registerIcon("card_containers_malicious_dark", "icons/ignored_card/card-containers-malicious_dark.svg");

        // Vulnerability icons (IAC/ASCA)
        registerIcon("card_vulnerability_critical", "icons/ignored_card/card-vulnerability-critical.svg");
        registerIcon("card_vulnerability_critical_dark", "icons/ignored_card/card-vulnerability-critical_dark.svg");
        registerIcon("card_vulnerability_high", "icons/ignored_card/card-vulnerability-high.svg");
        registerIcon("card_vulnerability_high_dark", "icons/ignored_card/card-vulnerability-high_dark.svg");
        registerIcon("card_vulnerability_medium", "icons/ignored_card/card-vulnerability-medium.svg");
        registerIcon("card_vulnerability_medium_dark", "icons/ignored_card/card-vulnerability-medium_dark.svg");
        registerIcon("card_vulnerability_low", "icons/ignored_card/card-vulnerability-low.svg");
        registerIcon("card_vulnerability_low_dark", "icons/ignored_card/card-vulnerability-low_dark.svg");
        registerIcon("card_vulnerability_malicious", "icons/ignored_card/card-vulnerability-malicious.svg");
        registerIcon("card_vulnerability_malicious_dark", "icons/ignored_card/card-vulnerability-malicious_dark.svg");
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

    /**
     * Get card icon for a given type and severity (theme-aware).
     *
     * @param type     Type of scan (OSS, SECRETS, CONTAINERS, ASCA, IAC)
     * @param severity Severity level
     * @return Image instance or null if not found
     */
    public static Image getCardIcon(String type, String severity) {
        if (type == null || severity == null) {
            return null;
        }

        String prefix = switch (type.toUpperCase()) {
            case "OSS" -> "card_package";
            case "SECRETS" -> "card_secret";
            case "CONTAINERS" -> "card_containers";
            case "ASCA", "IAC" -> "card_vulnerability";
            default -> "card_vulnerability";
        };

        String key = prefix + "_" + severity.toLowerCase();

        // Append _dark suffix if dark theme is active
        if (DevAssistUtils.isDarkTheme()) {
            key += "_dark";
        }

        return imageRegistry.get(key);
    }
}
