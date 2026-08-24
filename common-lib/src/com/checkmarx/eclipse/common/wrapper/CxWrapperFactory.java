package com.checkmarx.eclipse.common.wrapper;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.utils.PluginConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Builds wrapper objects according to the current configuration.
 */
public class CxWrapperFactory {

    public static CxWrapper build() throws CxException, Exception {
        return build(Preferences.getApiKey(), Preferences.getAdditionalOptions());
    }

    /**
     * Create a CxWrapper with the given credentials and the current agent configuration.
     * Used when the credentials being validated aren't necessarily the ones already saved
     * (e.g. the Preferences page "Test Connection" action).
     *
     * @param apiKey the API key to authenticate with
     * @param additionalParameters additional CLI parameters
     * @return initialized CxWrapper instance
     * @throws Exception if wrapper instantiation fails
     */
    public static CxWrapper build(String apiKey, String additionalParameters) throws CxException, Exception {
        return getWrapper(apiKey, additionalParameters);
    }

    /**
     * Create a CxWrapper with the given credentials and configuration
     *
     * @return initialized CxWrapper instance
     * @throws Exception if wrapper instantiation fails
     */
    private static CxWrapper getWrapper(String apiKey, String additionalParameters) throws Exception {
        CxWrapper cxWrapper = null;

        Logger log = LoggerFactory.getLogger(CxWrapperFactory.class.getName());

        CxConfig config = CxConfig.builder()
                .apiKey(apiKey)
                .additionalParameters(additionalParameters)
                .agentName(getAgentInfo())
                .build();
        try {
            cxWrapper = new CxWrapper(config, log);
        } catch (IOException e) {
            CxLogger.error(String.format(PluginConstants.ERROR_BUILDING_CX_WRAPPER, e.getMessage()), e);
            throw new Exception(e);
        }

        return cxWrapper;
    }
    
    /**
     * Get the agent information string for the CxWrapper
     * @return
     */
	private static String getAgentInfo() {
		String pluginVersion = getPluginVersion();
		CxLogger.info(String.format("PLUGIN_VERSION: %s_%s", PluginConstants.AGENT_NAME, pluginVersion));
		return String.format("%s_%s", PluginConstants.AGENT_NAME, pluginVersion);
	}

	/**
	 * Resolve the version of the bundle this class ships in, as stamped by the
	 * build (Tycho replaces the "qualifier" placeholder in MANIFEST.MF with the
	 * real build qualifier), falling back when running outside an OSGi framework.
	 */
	private static String getPluginVersion() {
		try {
			Bundle bundle = FrameworkUtil.getBundle(CxWrapperFactory.class);
			return bundle != null ? bundle.getVersion().toString() : "0.0.0";
		} catch (Exception e) {
			 CxLogger.error(String.format("Exception occurred while getting plugin version. Root cause: %s", e.getMessage()), e);
			 return "0.0.0";
		}
	}
}