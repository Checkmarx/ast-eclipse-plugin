package com.checkmarx.eclipse.runner;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;

public class Authenticator {

	
	protected static final String AUTH_STATUS = "Authentication Status: ";

	public static final Authenticator INSTANCE = new Authenticator();

	public String doAuthentication(String serverUrl, String authUrl, String tenant, String apiKey,
			String additionalParams) {

		CxConfig config = CxConfig.builder().baseUri(serverUrl).baseAuthUri(authUrl).tenant(tenant).apiKey(apiKey)
				.additionalParameters(additionalParams).build();

		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());

		CxWrapper wrapper;
		try {
			wrapper = new CxWrapper(config, log);
			String cxValidateOutput = wrapper.authValidate();
			CxLogger.info(AUTH_STATUS + cxValidateOutput);
			return cxValidateOutput;
		} catch (IOException | InterruptedException | CxException e) {
			CxLogger.error(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, e.getMessage()), e);
			return e.getMessage();
		}
	}

}
