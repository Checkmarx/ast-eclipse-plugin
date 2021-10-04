package com.checkmarx.eclipse.runner;

import java.io.IOException;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.exceptions.CxException;
import com.checkmarx.ast.results.CxValidateOutput;
import com.checkmarx.ast.scans.CxAuth;
import com.checkmarx.ast.scans.CxScanConfig;
import com.checkmarx.eclipse.properties.Preferences;

public class Authenticator {

	public static final Authenticator INSTANCE = new Authenticator();

//	private String pollCallback(String token) throws IOException, InterruptedException, AuthException,
//			KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
//
//
//		for (int i = 0; i < 20; i++) {
//			HttpResponse response = httpClient.execute(post);
//			String responseJson = EntityUtils.toString(response.getEntity(), "UTF-8");
//			AuthResponse authResponse = objectMapper.readValue(responseJson, AuthResponse.class);
//			if (authResponse.isOk()) {
//				return authResponse.getApi();
//			}
//			Thread.sleep(2000);
//		}
//
//		throw new AuthException("timeout, please try again");
//
//	}

	public Integer doAuthentication() {

		CxScanConfig config = new CxScanConfig();

	    config.setBaseUri(Preferences.getServerUrl());
	    config.setTenant(Preferences.getTenant());
	    config.setApiKey(Preferences.getApiKey());
	    
		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());

		CxAuth cxAuth;
		try {
			cxAuth = new CxAuth(config, log);
			CxValidateOutput cxValidateOutput = cxAuth.cxAuthValidate();
			Integer result = cxValidateOutput.getExitCode();
			System.out.println("Authentication Status :" + cxValidateOutput.getMessage());
			return result;
		} catch (CxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

		
	}

}
