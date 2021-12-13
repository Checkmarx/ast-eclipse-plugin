package com.checkmarx.eclipse.runner;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxConfig.InvalidCLIConfigException;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;

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

	public String doAuthentication(String serverUrl, String authUrl, String tenant, String apiKey,
			String additionalParams) {

		CxConfig config = CxConfig.builder().baseUri(serverUrl).baseAuthUri(authUrl).tenant(tenant).apiKey(apiKey)
				.additionalParameters(additionalParams).build();

		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());

		CxWrapper wrapper;
		try {
			wrapper = new CxWrapper(config, log);
			String cxValidateOutput = wrapper.authValidate();
			System.out.println("Authentication Status: " + cxValidateOutput);
			return cxValidateOutput;
		} catch (IOException e) {
			return e.getMessage();
		} catch (InterruptedException e) {
			return e.getMessage();
		} catch (InvalidCLIConfigException e) {
			return e.getMessage();
		} catch (CxException e) {
			return e.getMessage();
		}
	}

}
