package com.checkmarx.eclipse.devassist.factory;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.properties.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Builds wrapper objects according to the current configuration.
 */
public class CxWrapperFactory {

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

        CxConfig.CxConfigBuilder builder = CxConfig.builder()
                .apiKey(Preferences.getApiKey())
                .additionalParameters(Preferences.getAdditionalOptions());

        CxConfig config = builder.build();

        try {
            cxWrapper = new CxWrapper(config, log);
        } catch (IOException e) {
            CxLogger.error(String.format(PluginConstants.ERROR_BUILDING_CX_WRAPPER, e.getMessage()), e);
            throw new Exception(e);
        }

        return cxWrapper;
    }
}