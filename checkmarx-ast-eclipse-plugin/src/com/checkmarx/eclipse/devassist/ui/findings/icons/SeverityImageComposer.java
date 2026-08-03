package com.checkmarx.eclipse.devassist.ui.findings.icons;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import com.checkmarx.eclipse.devassist.ui.findings.model.FileNodeLabel;
import java.util.HashMap;
import java.util.Map;

/**
 * Composes severity icons into a single visual representation.
 * Creates badges like: [C:4] [H:3] [M:1] as actual icon images
 */
public class SeverityImageComposer {

    private static final Map<String, Image> compositeImageCache = new HashMap<>();

    /**
     * Create a full composite image with severity icon badges displayed inline.
     * Shows actual colored severity icons (🔴 🟠 🟡 🟢) after the filename.
     */
    public static Image createFullCompositeImage(FileNodeLabel fileNode) {
        if (fileNode == null || fileNode.getProblemCount() == null || fileNode.getProblemCount().isEmpty()) {
            return null;
        }

        try {
            Display display = Display.getDefault();
            Image compositeImage = createFullBadgeImage(display, fileNode);
            if (compositeImage != null) {
                
            }
            return compositeImage;
        } catch (Exception e) {
            System.err.println("[SEVERITY-COMPOSER] Error creating full composite image: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[SEVERITY-COMPOSER] Error creating composite image: " + e.getMessage());
            return null;
        }
    }

    /**
     * Create a badge image showing severity levels inline
     */
    private static Image createBadgeImage(Display display, FileNodeLabel fileNode) {
        try {
            // Get individual severity icons
            Image criticalIcon = IconRegistry.getIcon("critical", IconRegistry.Size.SMALL); // 16x16
            Image highIcon = IconRegistry.getIcon("high", IconRegistry.Size.SMALL);
            Image mediumIcon = IconRegistry.getIcon("medium", IconRegistry.Size.SMALL);
            Image lowIcon = IconRegistry.getIcon("low", IconRegistry.Size.SMALL);

            // Calculate total width needed
            int iconSize = 16;
            int spacing = 1;
            int width = 0;

            if (hasCount(fileNode, "critical")) {
                width += iconSize + spacing;
            }
            if (hasCount(fileNode, "high")) {
                width += iconSize + spacing;
            }
            if (hasCount(fileNode, "medium")) {
                width += iconSize + spacing;
            }
            if (hasCount(fileNode, "low")) {
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

            // Draw critical icon if count > 0
            if (hasCount(fileNode, "critical") && criticalIcon != null) {
                gc.drawImage(criticalIcon, x, y);
                x += iconSize + spacing;
            }

            // Draw high icon if count > 0
            if (hasCount(fileNode, "high") && highIcon != null) {
                gc.drawImage(highIcon, x, y);
                x += iconSize + spacing;
            }

            // Draw medium icon if count > 0
            if (hasCount(fileNode, "medium") && mediumIcon != null) {
                gc.drawImage(mediumIcon, x, y);
                x += iconSize + spacing;
            }

            // Draw low icon if count > 0
            if (hasCount(fileNode, "low") && lowIcon != null) {
                gc.drawImage(lowIcon, x, y);
                x += iconSize + spacing;
            }

            gc.dispose();
            
            return compositeImage;

        } catch (Exception e) {
            System.err.println("[SEVERITY-COMPOSER] Error creating badge image: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Create a full badge image showing only severity icons inline (no text).
     * Displays: [🔴][🟠][🟡][🟢] based on which severities have counts
     */
    private static Image createFullBadgeImage(Display display, FileNodeLabel fileNode) {
        try {
            // Get individual severity icons
            Image criticalIcon = IconRegistry.getIcon("critical", IconRegistry.Size.SMALL); // 16x16
            Image highIcon = IconRegistry.getIcon("high", IconRegistry.Size.SMALL);
            Image mediumIcon = IconRegistry.getIcon("medium", IconRegistry.Size.SMALL);
            Image lowIcon = IconRegistry.getIcon("low", IconRegistry.Size.SMALL);

            // Calculate total width needed
            int iconSize = 16;
            int spacing = 2;
            int totalWidth = 0;

            // Count how many icons we need
            int iconCount = 0;
            if (hasCount(fileNode, "critical")) iconCount++;
            if (hasCount(fileNode, "high")) iconCount++;
            if (hasCount(fileNode, "medium")) iconCount++;
            if (hasCount(fileNode, "low")) iconCount++;

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

            // Draw critical icon
            if (hasCount(fileNode, "critical") && criticalIcon != null) {
                gc.drawImage(criticalIcon, x, y);
                x += iconSize + spacing;
            }

            // Draw high icon
            if (hasCount(fileNode, "high") && highIcon != null) {
                gc.drawImage(highIcon, x, y);
                x += iconSize + spacing;
            }

            // Draw medium icon
            if (hasCount(fileNode, "medium") && mediumIcon != null) {
                gc.drawImage(mediumIcon, x, y);
                x += iconSize + spacing;
            }

            // Draw low icon
            if (hasCount(fileNode, "low") && lowIcon != null) {
                gc.drawImage(lowIcon, x, y);
                x += iconSize + spacing;
            }

            gc.dispose();
            
            return compositeImage;

        } catch (Exception e) {
            System.err.println("[SEVERITY-COMPOSER] Error creating full badge image: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static boolean hasCount(FileNodeLabel fileNode, String severity) {
        Long count = fileNode.getProblemCount().get(severity);
        return count != null && count > 0;
    }

    private static String createCacheKey(FileNodeLabel fileNode) {
        StringBuilder key = new StringBuilder();
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
