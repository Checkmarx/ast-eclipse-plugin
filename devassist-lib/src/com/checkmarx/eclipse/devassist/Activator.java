package com.checkmarx.eclipse.devassist;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

import com.checkmarx.eclipse.common.properties.Preferences;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.backend.GlobalScannerController;
import com.checkmarx.eclipse.devassist.backend.ScannerPreferencesListener;
import com.checkmarx.eclipse.devassist.configuration.McpInstallService;

/**
 * Devassist library activator.
 * Initializes McpInstallService to register authentication handlers.
 */
public class Activator extends Plugin {

	public static final String PLUGIN_ID = "com.checkmarx.eclipse.devassist";

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);

		try {
			// Step 1: Register scanner preferences listener
			// Bridges CheckmarxPreferencePage changes to GlobalScannerController
			ScannerPreferencesListener preferencesListener = new ScannerPreferencesListener();
			Preferences.setSettingsChangeNotifier(preferencesListener);
			CxLogger.info("[DEVASSIST] Registered ScannerPreferencesListener");

			// Step 2: Initialize GlobalScannerController with current preferences
			// Ensures scanner execution guards use latest stored preferences
			GlobalScannerController controller = GlobalScannerController.getInstance();

			// Load preferences from store and sync with controller
			boolean ascaEnabled = Preferences.STORE.getBoolean(Preferences.PREF_ASCA_ENABLED);
			boolean ossEnabled = Preferences.STORE.getBoolean(Preferences.PREF_OSS_ENABLED);
			boolean secretsEnabled = Preferences.STORE.getBoolean(Preferences.PREF_SECRETS_ENABLED);
			boolean containersEnabled = Preferences.STORE.getBoolean(Preferences.PREF_CONTAINERS_ENABLED);
			boolean iacEnabled = Preferences.STORE.getBoolean(Preferences.PREF_IAC_ENABLED);

			CxLogger.info("[ACTIVATOR] Initial preferences loaded: ASCA=" + ascaEnabled + ", OSS=" + ossEnabled +
						 ", SECRETS=" + secretsEnabled + ", CONTAINERS=" + containersEnabled + ", IAC=" + iacEnabled);

			// Sync preferences to controller (mirrors JetBrains initialization)
			if (ascaEnabled) controller.enableScanner(com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType.ASCA);
			else controller.disableScanner(com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType.ASCA);

			if (ossEnabled) controller.enableScanner(com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType.OSS);
			else controller.disableScanner(com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType.OSS);

			if (secretsEnabled) controller.enableScanner(com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType.SECRETS);
			else controller.disableScanner(com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType.SECRETS);

			if (containersEnabled) controller.enableScanner(com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType.CONTAINERS);
			else controller.disableScanner(com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType.CONTAINERS);

			if (iacEnabled) controller.enableScanner(com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType.IAC);
			else controller.disableScanner(com.checkmarx.eclipse.devassist.backend.ScannerRegistry.ScannerType.IAC);

			CxLogger.info("[DEVASSIST] Initialized GlobalScannerController with preferences. " +
						 controller.getStateReport());

		} catch (Exception e) {
			CxLogger.error("[DEVASSIST] Error during initialization: " + e.getMessage(), e);
		}

		try {
			// Step 3: Register authentication handlers (existing code)
			// Calling a real static member (not just the .class literal) is what forces the JVM
			// to run McpInstallService's static initializer, which registers the auth handlers.
			// This also does its documented job: auto-install MCP if already authenticated.
			McpInstallService.attemptAutoInstall();
			CxLogger.info("[DEVASSIST] Initialized authentication handlers");
		} catch (Exception e) {
			CxLogger.error("[DEVASSIST] Error registering authentication handlers: " + e.getMessage(), e);
		}
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		super.stop(context);
	}
}
