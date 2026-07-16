package com.checkmarx.eclipse.devassist.problems.icon;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.checkmarx.eclipse.Activator;

/**
 * Icon registry for problem severity icons. Loads severity icons from
 * /icons/severity/ directory and caches them for performance.
 */
public class IconRegistry {

	private static final IconRegistry INSTANCE = new IconRegistry();
	private static final String ICON_DIR = "icons/severity/";
	private static final Map<String, Image> ICON_CACHE = new HashMap<>();

	private IconRegistry() {
		loadIcons();
	}

	public static IconRegistry getInstance() {
		return INSTANCE;
	}

	public Image getIcon(String severity, int size) {
		if (severity == null) {
			return null;
		}

		String sizeStr = size + "px";
		String key = severity.toLowerCase() + "_" + sizeStr;

		if (ICON_CACHE.containsKey(key)) {
			return ICON_CACHE.get(key);
		}

		String iconPath = ICON_DIR + severity.toLowerCase() + "_" + sizeStr + ".svg";
		try {
			Image image = AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, iconPath).createImage();
			ICON_CACHE.put(key, image);
			return image;
		} catch (Exception e) {
			System.out.println("[ICON-REGISTRY] Icon not found: " + iconPath);
			return null;
		}
	}

	private void loadIcons() {
		System.out.println("[ICON-REGISTRY] Initializing icon registry for problems...");
	}

	public void dispose() {
		for (Image image : ICON_CACHE.values()) {
			if (image != null && !image.isDisposed()) {
				image.dispose();
			}
		}
		ICON_CACHE.clear();
	}
}
