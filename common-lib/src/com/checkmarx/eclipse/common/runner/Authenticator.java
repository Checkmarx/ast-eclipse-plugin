package com.checkmarx.eclipse.common.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.utils.PluginConstants;
import com.checkmarx.eclipse.common.wrapper.WrapperProvider;

public class Authenticator {
	   private final Logger log;

	   public Authenticator() {
	       this.log = LoggerFactory.getLogger(Authenticator.class);
	   }
	   
	   // for test only
	   public Authenticator(Logger logger) {
	       this.log = logger;
	   }
	   
	   protected static final String AUTH_STATUS = "Authentication Status: ";
	   public static final Authenticator INSTANCE = new Authenticator();

	   public String doAuthentication(String apiKey, String additionalParams) {
	       try {
	           String cxValidateOutput = new WrapperProvider().authValidate(apiKey, additionalParams);
	           CxLogger.info(String.format(PluginConstants.INFO_AUTHENTICATION_STATUS, cxValidateOutput));
	           return cxValidateOutput;
	       } catch (Exception e) {
	           CxLogger.error(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, e.getMessage()), e);
	           return e.getMessage();
	       }
	   }
	}