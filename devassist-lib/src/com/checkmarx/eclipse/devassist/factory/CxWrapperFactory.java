package com.checkmarx.eclipse.devassist.factory;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.backend.Constants;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Builds wrapper objects according to the current configuration.
 */
public class CxWrapperFactory {

    private static final String ECLIPSE_AGENT_NAME = "Eclipse";

    // Main plugin bundle id (the id declared on the Eclipse feature), used to
    // resolve the installed plugin version.
    private static final String MAIN_PLUGIN_BUNDLE_ID = "com.checkmarx.eclipse.plugin";

    public static CxWrapper build() throws CxException, Exception {
        return getWrapper();
    }

    /**
     * Create a CxWrapper with current credentials and configuration
     *
     * @return initialized CxWrapper instance
     * @throws Exception if wrapper instantiation fails
     */
    private static CxWrapper getWrapper() throws Exception {
        CxWrapper cxWrapper = null;

        Logger log = LoggerFactory.getLogger(CxWrapperFactory.class.getName());

        String agentName = ECLIPSE_AGENT_NAME;
        String pluginVersion = getPluginVersion();
        if (pluginVersion != null && !pluginVersion.isEmpty()) {
            agentName = agentName + "_" + pluginVersion;
        }

        CxConfig.CxConfigBuilder builder = CxConfig.builder()
                .agentName(agentName)
                .apiKey(Preferences.getApiKey())
                .additionalParameters(Preferences.getAdditionalOptions());

        CxConfig config = builder.build();

        try {
            cxWrapper = new CxWrapper(config, log);
        } catch (IOException e) {
            CxLogger.error(String.format(Constants.ERROR_BUILDING_CX_WRAPPER, e.getMessage()), e);
            throw new Exception(e);
        }

        return cxWrapper;
    }

    /**
     * Retrieves the installed plugin version from the main plugin's bundle descriptor.
     *
     * @return plugin version string, or empty string if version cannot be determined
     */
    private static String getPluginVersion() {
        try {
            Bundle bundle = Platform.getBundle(MAIN_PLUGIN_BUNDLE_ID);
            if (bundle != null) {
                String version = bundle.getVersion().toString();
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (Exception e) {
            CxLogger.warning("Failed to read plugin version for " + MAIN_PLUGIN_BUNDLE_ID + ": " + e.getMessage());
        }
        return "";
    }
}