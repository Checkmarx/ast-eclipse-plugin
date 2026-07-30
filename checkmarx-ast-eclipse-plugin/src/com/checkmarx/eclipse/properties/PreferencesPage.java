package com.checkmarx.eclipse.properties;

import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.runner.Authenticator;
import com.checkmarx.eclipse.runner.TenantSettingsProvider;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.ui.WelcomeDialog;
import com.checkmarx.eclipse.devassist.configuration.McpInstallService;
import com.checkmarx.eclipse.devassist.backend.listener.ProjectLifecycleListener;
import com.checkmarx.eclipse.startup.PluginStartup;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.PreferencesUtil;

import java.net.MalformedURLException;
import java.net.URL;


public class PreferencesPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	// Captured once the fields are loaded, so performOk() can tell whether THIS
	// page's own settings actually changed. Needed because Eclipse's shared
	// Preferences dialog calls performOk() on every page the user visited during
	// the session - not just the one they edited - so simply opening/looking at
	// "Checkmarx One" while really only changing "Checkmarx Scanner Configuration"
	// (Realtime Scanners) would otherwise still unconditionally fire
	// TOPIC_APPLY_SETTINGS below and refresh the unrelated Checkmarx One scan view.
	private StringFieldEditor apiKeyField;
	private StringFieldEditor additionalParamsField;
	private String initialApiKey;
	private String initialAdditionalOptions;

	public PreferencesPage() {
		super(GRID);
		Activator.getDefault().getPreferenceStore().addPropertyChangeListener(this::handlePropertyChange);
	}

	private void handlePropertyChange(PropertyChangeEvent event) {

	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(Preferences.STORE);
		setMessage("Checkmarx One preferences");
	}

	@Override
	protected void createFieldEditors() {
		Composite topComposite = new Composite(getFieldEditorParent(), SWT.NONE);
		GridData topGridData = new GridData();
		topGridData.horizontalAlignment = GridData.FILL;
		topGridData.verticalAlignment = GridData.FILL;
		topGridData.grabExcessHorizontalSpace = true;
		topComposite.setLayoutData(topGridData);

		getFieldEditorParent().setLayoutData(topGridData);

		GridLayout parentLayout = new GridLayout();
		parentLayout.numColumns = 1;
		parentLayout.horizontalSpacing = 0;
		parentLayout.verticalSpacing = 0;
		parentLayout.marginHeight = 0;
		parentLayout.marginWidth = 0;
		topComposite.setLayout(parentLayout);

		StringFieldEditor apiKey = new StringFieldEditor(Preferences.API_KEY, PluginConstants.PREFERENCES_API_KEY, topComposite);
		apiKeyField = apiKey;
		addField(apiKey);
		Text textControl = apiKey.getTextControl(topComposite);
		textControl.setEchoChar('*');

		StringFieldEditor additionalParams = new StringFieldEditor(Preferences.ADDITIONAL_OPTIONS,
		        PluginConstants.PREFERENCES_ADDITIONAL_OPTIONS, StringFieldEditor.UNLIMITED, StringFieldEditor.VALIDATE_ON_KEY_STROKE, topComposite);
		additionalParamsField = additionalParams;
		addField(additionalParams);

		// Baseline for the change-detection guard in performOk() - captured now that
		// both fields have loaded their values from the preference store.
		initialApiKey = apiKey.getStringValue();
		initialAdditionalOptions = additionalParams.getStringValue();

        //set the width for API Key text field
		GridData gridData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
		gridData.widthHint = 500; // Some width
		gridData.grabExcessHorizontalSpace = false;
		gridData.horizontalAlignment = GridData.FILL;
		textControl.setLayoutData(gridData);

		addField(space());


        Link cliHelp = new Link(getFieldEditorParent(), SWT.NONE);
        cliHelp.setText("<a href=\"https://checkmarx.com/resource/documents/en/34965-68626-global-flags.html\">CLI command that supports a set of global flags</a>");
        cliHelp.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
		GridData linkGridData = new GridData(SWT.END, SWT.CENTER, true, false);
		cliHelp.setLayoutData(linkGridData);
		cliHelp.addSelectionListener(new SelectionAdapter() {
		    @Override
		    public void widgetSelected(SelectionEvent e) {		      
		            IWorkbenchBrowserSupport browserSupport = PlatformUI.getWorkbench().getBrowserSupport();
		            try {
						browserSupport.getExternalBrowser().openURL(new URL(e.text));
					} catch (PartInitException | MalformedURLException e1) {
						CxLogger.error("Failed to open CLI help documentation link.", e1);
						e1.printStackTrace();
					}		        
		    }
		});

        addField(space());

        Label connectionLabel = new Label(getFieldEditorParent(), SWT.WRAP);
        connectionLabel.setLayoutData(
                new GridData(SWT.FILL, SWT.CENTER, true, false)
        );

		// Holds the Logout button reference so the Connect handler (defined before the
		// Logout button is created below) can disable/enable it during the connect flow.
		final Button[] logoutButtonHolder = new Button[1];

		Button connectionButton = new Button(topComposite, SWT.PUSH);
		connectionButton.setText(PluginConstants.PREFERENCES_TEST_CONNECTION);
		connectionButton.setEnabled(!apiKey.getStringValue().trim().isEmpty());
		textControl.addModifyListener(e -> {
		    connectionButton.setEnabled(!textControl.getText().trim().isEmpty());
		});
		connectionButton.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent e) {

				String apiKey_str = apiKey.getStringValue();

				String additionalParams_str = additionalParams.getStringValue();
				connectionButton.setEnabled(false);
				connectionLabel.setText(PluginConstants.PREFERENCES_VALIDATING_STATE);
				getFieldEditorParent().layout();

				// Disable Logout for the duration of the connect/validate flow so a user can't
				// interrupt it mid-flight (e.g. closing the dialog or logging out) in a way that
				// leaves the flow half-finished and the welcome dialog never shown.
				if (logoutButtonHolder[0] != null && !logoutButtonHolder[0].isDisposed()) {
					logoutButtonHolder[0].setEnabled(false);
				}

				CompletableFuture.supplyAsync(() -> {
					try {
						return Authenticator.INSTANCE.doAuthentication(
								apiKey_str, additionalParams_str);
					} catch (Throwable t) {
						CxLogger.error(PluginConstants.ERROR_AUTHENTICATING_AST, new Exception(t));
						return t.getMessage();
					}
				}).thenAccept((result) -> Display.getDefault().syncExec(() -> {
					// Guard every widget touch below: if the preferences dialog was closed
					// while this connect/validate call was in flight, these are disposed.
					// Previously an unguarded call here threw and aborted this whole runnable,
					// which is why the welcome dialog never appeared after closing the dialog.
					if (!connectionButton.isDisposed()) {
						connectionButton.setEnabled(true);
					}

					// Show welcome dialog on successful authentication. The "Validating..."
					// message is left on screen (not switched to "Connected") until the
					// welcome dialog is actually about to appear, so the label never claims
					// success before the user sees the welcome page.
					if (result != null && result.contains(PluginConstants.AUTH_SUCCESS_PATTERN)) {
						// Trigger MCP auto-installation after successful authentication
						CxLogger.info("[PREFS] Authentication successful, triggering MCP auto-install...");
						McpInstallService.attemptAutoInstall();

						// Fetch MCP enabled status from server asynchronously
						CompletableFuture.supplyAsync(() -> {
							try {
								return TenantSettingsProvider.INSTANCE.isAiMcpServerEnabled(
										apiKey_str, additionalParams_str);
							} catch (Exception ex) {
								CxLogger.error("Failed to fetch MCP status", ex);
								return false;
							}
						}).thenAccept((mcpEnabled) -> Display.getDefault().syncExec(() -> {
							if (!connectionLabel.isDisposed()) {
								connectionLabel.setText(mapAuthResult(result));
							}
							if (!getFieldEditorParent().isDisposed()) {
								getFieldEditorParent().layout();
							}
							showWelcomeDialog(mcpEnabled, logoutButtonHolder[0], apiKey_str, additionalParams_str);
						}));
					} else {
						// Authentication failed - the flow ends here with no welcome dialog,
						// so show the failure message right away and restore Logout.
						if (!connectionLabel.isDisposed()) {
							connectionLabel.setText(mapAuthResult(result));
						}
						if (!getFieldEditorParent().isDisposed()) {
							getFieldEditorParent().layout();
						}
						if (logoutButtonHolder[0] != null && !logoutButtonHolder[0].isDisposed()) {
							logoutButtonHolder[0].setEnabled(true);
						}
					}
				}));
			}
		});

		addField(space());

		Button logoutButton = new Button(topComposite, SWT.PUSH);
		logoutButtonHolder[0] = logoutButton;
		logoutButton.setText("Logout");
		logoutButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Preferences.clearApiKey();
				apiKey.setStringValue("");
//				textControl.setText("");
//				connectionLabel.setText("");
				getFieldEditorParent().layout();
			}
		});

		addField(space());

		Link realtimeScannersLink = new Link(getFieldEditorParent(), SWT.NONE);
		realtimeScannersLink.setText("<a>Go to Realtime Scanners</a>");
		realtimeScannersLink.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
		realtimeScannersLink.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				PreferencesUtil.createPreferenceDialogOn(
					getShell(),
					"com.checkmarx.eclipse.devassist.prefs.checkmarxpreferencepage",
					null,
					null
				).open();
			}
		});
	}



	private static String mapAuthResult(String result) {
		if (result != null && result.contains(PluginConstants.AUTH_SUCCESS_PATTERN)) {
			return PluginConstants.AUTH_SUCCESS_DISPLAY;
		}
		return result;
	}

	private void showWelcomeDialog(boolean mcpEnabled, Button logoutButton, String apiKey_str, String additionalParams_str) {
		try {
			// The key was only just validated by "Test Connection" - it isn't persisted
			// to the store until the user clicks OK/Apply on this dialog, which they may
			// never do once they see the Welcome page. Persist it now so
			// isUserAuthenticated() (checked by ProjectLifecycleListener below, and by
			// anything else gated on login) actually sees it.
			Preferences.STORE.setValue(Preferences.API_KEY, apiKey_str);
			Preferences.STORE.setValue(Preferences.ADDITIONAL_OPTIONS, additionalParams_str);

			// Trigger the same initial OSS/IaC/container workspace scan that runs for
			// already-open projects at plugin launch (PluginStartup.initializeBackendScanners()).
			// A project that was already open before this login never gets that scan
			// otherwise, since ProjectLifecycleListener only scans a project when it
			// *opens* while the user is authenticated - re-run it now that login succeeded.
			ProjectLifecycleListener projectListener = PluginStartup.getProjectListener();
			if (projectListener != null) {
				CxLogger.info("[PREFS] Login succeeded - triggering workspace OSS/IaC/container scan...");
				projectListener.scanAlreadyOpenProjects();
			}

			WelcomeDialog dlg = new WelcomeDialog(
				Display.getDefault().getActiveShell(),
				mcpEnabled);

			// Re-enable Logout right as the welcome dialog is about to appear, so it stays
			// disabled for the entire connect/validate flow and only becomes usable once
			// that flow has visibly completed.
			if (logoutButton != null && !logoutButton.isDisposed()) {
				logoutButton.setEnabled(true);
			}

			dlg.open();
		} catch (Exception ex) {
			CxLogger.error("Failed to show welcome dialog", ex);
			if (logoutButton != null && !logoutButton.isDisposed()) {
				logoutButton.setEnabled(true);
			}
		}
	}

	private FieldEditor space() {
		return new LabelFieldEditor("", getFieldEditorParent());
	}

	@Override
	public boolean performOk() {
		boolean ok = super.performOk();

		if (ok) {
			// Only notify listeners (e.g. the Checkmarx One scan view refresh) if this
			// page's own settings actually changed in this session. Without this guard,
			// merely having visited this page in the same Preferences dialog session as
			// the unrelated "Checkmarx Scanner Configuration" (Realtime Scanners) page -
			// a sibling top-level page in the same tree - is enough for Eclipse to call
			// this performOk() too when the user only meant to save realtime scanner
			// settings, spuriously refreshing the Checkmarx One scan window.
			String currentApiKey = apiKeyField != null ? apiKeyField.getStringValue() : null;
			String currentAdditionalOptions = additionalParamsField != null ? additionalParamsField.getStringValue() : null;
			boolean settingsActuallyChanged =
					!java.util.Objects.equals(currentApiKey, initialApiKey)
					|| !java.util.Objects.equals(currentAdditionalOptions, initialAdditionalOptions);

			if (settingsActuallyChanged) {
				PluginUtils.getEventBroker().post(PluginConstants.TOPIC_APPLY_SETTINGS, PluginConstants.EMPTY_STRING);
			}
		}

		return ok;
	}
}