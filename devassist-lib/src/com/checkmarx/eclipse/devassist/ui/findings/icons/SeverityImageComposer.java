package com.checkmarx.eclipse.devassist.ui.findings.icons;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Display;
import com.checkmarx.eclipse.devassist.ui.findings.model.FileNodeLabel;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Composes severity icons into a single visual representation.
 * Creates badges like: [C:4] [H:3] [M:1] as actual icon images
 */
public class SeverityImageComposer {

    private static final Map<String, Image> compositeImageCache = new HashMap<>();

    // Shared severity icon instances
    private static final Image MALICIOUS_ICON = IconRegistry.getIcon(DevAssistConstants.MALICIOUS,
            IconRegistry.Size.SMALL);
    private static final Image CRITICAL_ICON = IconRegistry.getIcon(DevAssistConstants.CRITICAL,
            IconRegistry.Size.SMALL);
    private static final Image HIGH_ICON = IconRegistry.getIcon(DevAssistConstants.HIGH, IconRegistry.Size.SMALL);
    private static final Image MEDIUM_ICON = IconRegistry.getIcon(DevAssistConstants.MEDIUM, IconRegistry.Size.SMALL);
    private static final Image LOW_ICON = IconRegistry.getIcon(DevAssistConstants.LOW, IconRegistry.Size.SMALL);

    /**
     * Create a full composite image with severity icon badges displayed inline.
     * Shows actual colored severity icons (🔴 🟠 🟡 🟢) after the filename.
     */
    public static Image createFullCompositeImage(FileNodeLabel fileNode) {
        if (fileNode == null || fileNode.getProblemCount() == null || fileNode.getProblemCount().isEmpty()) {
            return null;
        }

        // Create cache key with a prefix to avoid collisions with
        // createSeverityBadgeImage
        String cacheKey = "full_" + createCacheKey(fileNode);
        if (compositeImageCache.containsKey(cacheKey)) {
            return compositeImageCache.get(cacheKey);
        }

        try {
            Display display = Display.getDefault();
            Image compositeImage = createFullBadgeImage(display, fileNode);

            if (compositeImage != null) {
                compositeImageCache.put(cacheKey, compositeImage);
            }

            return compositeImage;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create a composite image showing severity icons with counts inline.
     * Example: Creates visual badges for Critical:4, High:3, Medium:1
     */
    public static Image createSeverityBadgeImage(FileNodeLabel fileNode) {
        if (fileNode == null || fileNode.getProblemCount() == null || fileNode.getProblemCount().isEmpty()) {
            return null;
        }

        // Create cache key
        String cacheKey = createCacheKey(fileNode);
        if (compositeImageCache.containsKey(cacheKey)) {
            return compositeImageCache.get(cacheKey);
        }

        try {
            // Get display for image creation
            Display display = Display.getDefault();

            // Create a composite image showing severity badges
            // Format: Show icon + count for each severity with > 0 count
            Image compositeImage = createBadgeImage(display, fileNode);

            if (compositeImage != null) {
                compositeImageCache.put(cacheKey, compositeImage);
            }

            return compositeImage;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create a badge image showing severity levels inline
     */
    private static Image createBadgeImage(Display display, FileNodeLabel fileNode) {
        try {

            // Calculate total width needed
            int iconSize = 16;
            int spacing = 1;
            int width = 0;

            if (hasCount(fileNode, DevAssistConstants.MALICIOUS)) {
                width += iconSize + spacing;
            }
            if (hasCount(fileNode, DevAssistConstants.CRITICAL)) {
                width += iconSize + spacing;
            }
            if (hasCount(fileNode, DevAssistConstants.HIGH)) {
                width += iconSize + spacing;
            }
            if (hasCount(fileNode, DevAssistConstants.MEDIUM)) {
                width += iconSize + spacing;
            }
            if (hasCount(fileNode, DevAssistConstants.LOW)) {
                width += iconSize + spacing;
            }

            if (width == 0) {
                return null;
            }

            // Adjust width to remove last spacing
            width = Math.max(0, width - spacing);

            // Create composite image
            Image compositeImage = new Image(display, width, iconSize);
            GC gc = new GC(compositeImage);
            gc.setBackground(display.getSystemColor(org.eclipse.swt.SWT.COLOR_WIDGET_BACKGROUND));
            gc.fillRectangle(0, 0, width, iconSize);
            gc.setAntialias(org.eclipse.swt.SWT.ON);

            int x = 0;
            int y = 0;

            if (hasCount(fileNode, "malicious") && MALICIOUS_ICON != null) {
                gc.drawImage(MALICIOUS_ICON, x, y);
                x += iconSize + spacing;
            }

            // Draw critical icon if count > 0
            if (hasCount(fileNode, "critical") && CRITICAL_ICON != null) {
                gc.drawImage(CRITICAL_ICON, x, y);
                x += iconSize + spacing;
            }

            // Draw high icon if count > 0
            if (hasCount(fileNode, "high") && HIGH_ICON != null) {
                gc.drawImage(HIGH_ICON, x, y);
                x += iconSize + spacing;
            }

            // Draw medium icon if count > 0
            if (hasCount(fileNode, "medium") && MEDIUM_ICON != null) {
                gc.drawImage(MEDIUM_ICON, x, y);
                x += iconSize + spacing;
            }

            // Draw low icon if count > 0
            if (hasCount(fileNode, "low") && LOW_ICON != null) {
                gc.drawImage(LOW_ICON, x, y);
                x += iconSize + spacing;
            }

            gc.dispose();
            return compositeImage;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create a full badge image showing only severity icons inline (no text).
     * Displays: [🔴][🟠][🟡][🟢] based on which severities have counts
     */
    private static Image createFullBadgeImage(Display display, FileNodeLabel fileNode) {
        try {

            // Calculate total width needed
            int iconSize = 16;
            int spacing = 2;
            int totalWidth = 0;

            // Count how many icons we need
            int iconCount = 0;
            if (hasCount(fileNode, DevAssistConstants.MALICIOUS))
                iconCount++;
            if (hasCount(fileNode, DevAssistConstants.CRITICAL))
                iconCount++;
            if (hasCount(fileNode, DevAssistConstants.HIGH))
                iconCount++;
            if (hasCount(fileNode, DevAssistConstants.MEDIUM))
                iconCount++;
            if (hasCount(fileNode, DevAssistConstants.LOW))
                iconCount++;

            if (iconCount == 0) {
                return null;
            }

            // Calculate width: (iconSize + spacing) * count - spacing
            totalWidth = (iconSize + spacing) * iconCount - spacing;

            // Create composite image with severity icons
            Image compositeImage = new Image(display, totalWidth, iconSize);
            GC gc = new GC(compositeImage);
            gc.setBackground(display.getSystemColor(org.eclipse.swt.SWT.COLOR_WIDGET_BACKGROUND));
            gc.fillRectangle(0, 0, totalWidth, iconSize);
            gc.setAntialias(org.eclipse.swt.SWT.ON);

            int x = 0;
            int y = 0;

            if (hasCount(fileNode, DevAssistConstants.MALICIOUS) && MALICIOUS_ICON != null) {
                gc.drawImage(MALICIOUS_ICON, x, y);
                x += iconSize + spacing;
            }

            // Draw critical icon
            if (hasCount(fileNode, DevAssistConstants.CRITICAL) && CRITICAL_ICON != null) {
                gc.drawImage(CRITICAL_ICON, x, y);
                x += iconSize + spacing;
            }

            // Draw high icon
            if (hasCount(fileNode, DevAssistConstants.HIGH) && HIGH_ICON != null) {
                gc.drawImage(HIGH_ICON, x, y);
                x += iconSize + spacing;
            }

            // Draw medium icon
            if (hasCount(fileNode, DevAssistConstants.MEDIUM) && MEDIUM_ICON != null) {
                gc.drawImage(MEDIUM_ICON, x, y);
                x += iconSize + spacing;
            }

            // Draw low icon
            if (hasCount(fileNode, DevAssistConstants.LOW) && LOW_ICON != null) {
                gc.drawImage(LOW_ICON, x, y);
                x += iconSize + spacing;
            }

            gc.dispose();
            return compositeImage;

        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasCount(FileNodeLabel fileNode, String severity) {
        Long count = fileNode.getProblemCount().get(severity);
        return count != null && count > 0;
    }

    private static String createCacheKey(FileNodeLabel fileNode) {
        StringBuilder key = new StringBuilder();
        key.append("m:").append(fileNode.getProblemCount().getOrDefault("malicious", 0L)).append("|");
        key.append("c:").append(fileNode.getProblemCount().getOrDefault("critical", 0L)).append("|");
        key.append("h:").append(fileNode.getProblemCount().getOrDefault("high", 0L)).append("|");
        key.append("m:").append(fileNode.getProblemCount().getOrDefault("medium", 0L)).append("|");
        key.append("l:").append(fileNode.getProblemCount().getOrDefault("low", 0L));
        return key.toString();
    }

    public static void clearCache() {
        for (Image img : compositeImageCache.values()) {
            if (img != null && !img.isDisposed()) {
                img.dispose();
            }
        }
        compositeImageCache.clear();
    }
}
