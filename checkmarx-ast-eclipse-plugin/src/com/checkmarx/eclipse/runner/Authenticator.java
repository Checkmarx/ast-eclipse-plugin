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
	   private final Logger log;
	   
	   private Authenticator() {
	       this.log = LoggerFactory.getLogger(Authenticator.class);
	   }
	   
	   // for test only
	   public Authenticator(Logger logger) {
	       this.log = logger;
	   }
	   
	   protected static final String AUTH_STATUS = "Authentication Status: ";
	   public static final Authenticator INSTANCE = new Authenticator();

	   public String doAuthentication(String apiKey, String additionalParams) {
	       CxConfig config = CxConfig.builder()
	               .apiKey(apiKey)
	               .additionalParameters(additionalParams)
	               .build();
	       try {
	           CxWrapper wrapper = new CxWrapper(config, log);
	           String cxValidateOutput = wrapper.authValidate();
	           log.info(AUTH_STATUS + cxValidateOutput);
	           return cxValidateOutput;
	       } catch (IOException | InterruptedException | CxException e) {
	           log.error(String.format(PluginConstants.ERROR_AUTHENTICATING_AST, e.getMessage()), e);
	           return e.getMessage();
	       }
	   }
	}