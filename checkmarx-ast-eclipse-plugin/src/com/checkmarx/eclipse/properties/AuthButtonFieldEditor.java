package com.checkmarx.eclipse.properties;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.checkmarx.eclipse.runner.Authenticator;

public class AuthButtonFieldEditor extends StringButtonFieldEditor {

	Consumer<String> consumer;

	private StringFieldEditor serverUrl;
	private StringFieldEditor authUrl;
	private StringFieldEditor tenant;
	private StringFieldEditor apiKey;
	private StringFieldEditor additionalParams;
	private CLabel connectionLabel;

	AuthButtonFieldEditor(String name, String labelText, Composite parent, StringFieldEditor serverUrl,
			StringFieldEditor authUrl, StringFieldEditor tenant, StringFieldEditor apiKey,
			StringFieldEditor additionalParams, CLabel connectionLabel) {
		super(name, labelText, parent);
		setChangeButtonText(labelText);
		this.serverUrl = serverUrl;
		this.authUrl = authUrl;
		this.tenant = tenant;
		this.apiKey = apiKey;
		this.additionalParams = additionalParams;
		this.connectionLabel = connectionLabel;
	}

	@Override
	protected String changePressed() {
		connectionLabel.setText("Validating...");

		String serverUrl_str = serverUrl.getStringValue();
		String authUrl_str = authUrl.getStringValue();
		String tenant_str = tenant.getStringValue();
		String apiKey_str = apiKey.getStringValue();
		String additionalParams_str = additionalParams.getStringValue();

		CompletableFuture.supplyAsync(() -> {
			try {
				return Authenticator.INSTANCE.doAuthentication(serverUrl_str, authUrl_str, tenant_str, apiKey_str,
						additionalParams_str);
			} catch (Throwable t) {
				return t.getMessage();
			}
		}).thenAccept((result) -> Display.getDefault().syncExec(() -> connectionLabel.setText(result)));

		return null;
	}

	public void emptyTextfield() {
		setStringValue("");
		Preferences.store("", "");
	}

	void setValues() {
	}

}
