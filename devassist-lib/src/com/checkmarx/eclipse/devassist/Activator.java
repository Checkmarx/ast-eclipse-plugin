package com.checkmarx.eclipse.devassist;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

import com.checkmarx.eclipse.common.utils.CxLogger;
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
		// Calling a real static member (not just the .class literal) is what forces the JVM
		// to run McpInstallService's static initializer, which registers the auth handlers.
		// This also does its documented job: auto-install MCP if already authenticated.
		McpInstallService.attemptAutoInstall();
		CxLogger.info("[DEVASSIST] Initialized authentication handlers");
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		super.stop(context);
	}
}
