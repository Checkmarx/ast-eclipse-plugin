package com.checkmarx.eclipse.devassist.ui.findings.icons;

import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.checkmarx.eclipse.Activator;

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
        LOW("low"),
        UNKNOWN("unknown"),
        OK("ok"),
        IGNORED("ignored");

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

        // Register small icons (16px)
        registerIcon("malicious_16", "icons/severity/malicious_16.svg");
        registerIcon("critical_16", "icons/severity/critical_16.svg");
        registerIcon("high_16", "icons/severity/high_16.svg");
        registerIcon("medium_16", "icons/severity/medium_16.svg");
        registerIcon("low_16", "icons/severity/low_16.svg");
        registerIcon("unknown_16", "icons/severity/unknown_16.svg");
        registerIcon("ok_16", "icons/severity/ok_16.svg");
        registerIcon("ignored_16", "icons/severity/ignored_16.svg");

        // Register medium icons (20px)
        registerIcon("malicious_20", "icons/severity/malicious_20.svg");
        registerIcon("critical_20", "icons/severity/critical_20.svg");
        registerIcon("high_20", "icons/severity/high_20.svg");
        registerIcon("medium_20", "icons/severity/medium_20.svg");
        registerIcon("low_20", "icons/severity/low_20.svg");
        registerIcon("unknown_20", "icons/severity/unknown_20.svg");
        registerIcon("ok_20", "icons/severity/ok_20.svg");
        registerIcon("ignored_20", "icons/severity/ignored_20.svg");

        // Register base icons
        registerIcon("malicious", "icons/severity/malicious.svg");
        registerIcon("critical", "icons/severity/critical.svg");
        registerIcon("high", "icons/severity/high.svg");
        registerIcon("medium", "icons/severity/medium.svg");
        registerIcon("low", "icons/severity/low.svg");
        registerIcon("unknown", "icons/severity/unknown.svg");
        registerIcon("ok", "icons/severity/ok.svg");
        registerIcon("ignored", "icons/severity/ignored.svg");
    }

    private static void registerIcon(String key, String path) {
        AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, path);
        imageRegistry.put(key, AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, path));
    }

    /**
     * Get icon for a severity level and size.
     * Normalizes severity to match SeverityLevel enum values, then converts to lowercase for icon lookup.
     *
     * @param severity Severity level (case-insensitive)
     * @param size     Icon size
     * @return Image instance or null if not found
     */
    public static Image getIcon(String severity, Size size) {
        if (severity == null) {
            return null;
        }

        // Normalize severity to SeverityLevel format, then convert to lowercase for icon key
        String normalized = com.checkmarx.eclipse.devassist.backend.DevAssistUtils.normalizeSeverity(severity);
        String key = normalized.toLowerCase() + size.getSuffix();
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
