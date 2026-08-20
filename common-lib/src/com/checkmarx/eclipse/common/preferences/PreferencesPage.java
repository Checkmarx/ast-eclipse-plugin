package com.checkmarx.eclipse.common.preferences;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.PreferenceDialog;
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
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.checkmarx.eclipse.common.utils.PluginConstants;
import com.checkmarx.eclipse.common.listener.IAuthenticationSuccessHandler;
import com.checkmarx.eclipse.common.listener.ISettingsChangeNotifier;
import com.checkmarx.eclipse.common.runner.Authenticator;
import com.checkmarx.eclipse.common.runner.TenantSettingsProvider;
import com.checkmarx.eclipse.common.utils.CxLogger;

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
	private Link realtimeScannersLink;

	public PreferencesPage() {
		super(GRID);
		// Replaced Activator preference store listener with Preferences.STORE
		Preferences.STORE.addPropertyChangeListener(this::handlePropertyChange);
	}

	private void handlePropertyChange(PropertyChangeEvent event) {
		refreshRealtimeScannersLink();
	}

	/**
	 * Shows the "Go to Realtime Scanners" link only while the user is logged in -
	 * the page it opens has no meaningful content to configure otherwise.
	 */
	private void refreshRealtimeScannersLink() {
		if (realtimeScannersLink != null && !realtimeScannersLink.isDisposed()) {
			boolean isLoggedIn = StringUtils.isNotBlank(Preferences.getApiKey());
			
			realtimeScannersLink.setVisible(isLoggedIn);
			
			if (realtimeScannersLink.getLayoutData() instanceof GridData) {
				((GridData) realtimeScannersLink.getLayoutData()).exclude = !isLoggedIn;
			}

			// Re-layout the parent so other controls adjust dynamically
			Composite parent = realtimeScannersLink.getParent();
			if (parent != null && !parent.isDisposed()) {
				parent.layout(true, true);
			}
		}
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

		// Every widget on this page is parented directly to topComposite, in the exact order
		// it should visually appear. They used to be split between topComposite and
		// getFieldEditorParent(), which made the on-screen order depend on which of the two
		// composites was created first rather than on the order of the code below - keeping a
		// single parent removes that ambiguity.

		// helpLink lives in its own composite, isolated from the fields below, so its own
		// sizing/margins can never influence the spacing between the API key / additional
		// params labels and their input boxes.
		Composite helpComposite = new Composite(topComposite, SWT.NONE);
		GridLayout helpLayout = new GridLayout();
		helpLayout.numColumns = 1;
		helpLayout.marginHeight = 0;
		helpLayout.marginWidth = 0;
		helpComposite.setLayout(helpLayout);
		helpComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Link helpLink = new Link(helpComposite, SWT.NONE);
		helpLink.setText("<a href=\"" + PluginConstants.PREFERENCES_HELP_LINK_URL + "\">" + PluginConstants.PREFERENCES_HELP_LINK_TEXT + "</a>");
		helpLink.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
		helpLink.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				IWorkbenchBrowserSupport browserSupport = PlatformUI.getWorkbench().getBrowserSupport();
				try {
					browserSupport.getExternalBrowser().openURL(new URL(e.text));
				} catch (PartInitException | MalformedURLException e1) {
					CxLogger.error("Failed to open Checkmarx One Eclipse Plugin Help Page link.", e1);
					e1.printStackTrace();
				}
			}
		});

		spacer(topComposite);

		// apiKey and additionalParams get their own composite with a standard, fixed
		// label-to-input gap - kept separate from topComposite (and from helpComposite above)
		// so nothing else on the page can stretch or shrink that gap.
		Composite fieldsComposite = new Composite(topComposite, SWT.NONE);
		GridLayout fieldsLayout = new GridLayout();
		fieldsLayout.numColumns = 1;
		fieldsLayout.marginHeight = 0;
		fieldsLayout.marginWidth = 0;
		fieldsLayout.verticalSpacing = 4;
		fieldsComposite.setLayout(fieldsLayout);
		fieldsComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		StringFieldEditor apiKey = new StringFieldEditor(Preferences.API_KEY, PluginConstants.PREFERENCES_API_KEY, fieldsComposite);
		apiKeyField = apiKey;
		addField(apiKey);
		Text textControl = apiKey.getTextControl(fieldsComposite);
		textControl.setEchoChar('*');

		StringFieldEditor additionalParams = new StringFieldEditor(Preferences.ADDITIONAL_OPTIONS,
		        PluginConstants.PREFERENCES_ADDITIONAL_OPTIONS, StringFieldEditor.UNLIMITED, StringFieldEditor.VALIDATE_ON_KEY_STROKE, fieldsComposite);
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

		spacer(topComposite);

        Link cliHelp = new Link(topComposite, SWT.NONE);
        cliHelp.setText("<a href=\"https://checkmarx.com/resource/documents/en/34965-68626-global-flags.html\">CLI command that supports a set of global flags</a>");
        cliHelp.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
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

        spacer(topComposite);

		// Holds the Logout button reference so the Connect handler (defined before the
		// Logout button is created below) can disable/enable it during the connect flow.
		final Button[] logoutButtonHolder = new Button[1];

		Composite buttonsComposite = new Composite(topComposite, SWT.NONE);
		GridLayout buttonsLayout = new GridLayout();
		buttonsLayout.numColumns = 2;
		buttonsLayout.marginHeight = 0;
		buttonsLayout.marginWidth = 0;
		buttonsLayout.horizontalSpacing = 10;
		buttonsComposite.setLayout(buttonsLayout);
		buttonsComposite.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));

		// Give both buttons a fixed minimum width so they aren't sized to hug their text -
		// without this, "Logout" ends up noticeably narrower than "Connect to Checkmarx".
		final int buttonWidthHint = 140;

		Button connectionButton = new Button(buttonsComposite, SWT.PUSH);
		connectionButton.setText(PluginConstants.CONNECT_TO_CHECKMARX);
		GridData connectionButtonGridData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		connectionButtonGridData.widthHint = buttonWidthHint;
		connectionButton.setLayoutData(connectionButtonGridData);
		connectionButton.setEnabled(!apiKey.getStringValue().trim().isEmpty());

		// connectionLabel (the "Validating.../Connected" status text) is created after
		// buttonsComposite so it renders below the Connect/Logout buttons, per AUTH_SUCCESS_DISPLAY
		// placement - it's declared here, before the listeners below that reference it.
		spacer(topComposite);

		Label connectionLabel = new Label(topComposite, SWT.WRAP);
		connectionLabel.setLayoutData(
		        new GridData(SWT.FILL, SWT.CENTER, true, false)
		);

		textControl.addModifyListener(e -> {
		    connectionButton.setEnabled(!textControl.getText().trim().isEmpty());

		    // Any edit means whatever gets saved next (even via Apply/OK without ever
		    // clicking Test Connection) hasn't been checked against the server, so it must
		    // not keep looking "connected" on the strength of a previous, different key's
		    // validation.
		    Preferences.setCredentialsValidated(false);
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
						// The key was only just validated by "Test Connection" - it isn't persisted
						// to the store until the user clicks OK/Apply on this dialog, which they may
						// never do once they see the Welcome page. Persist it now so
						// isUserAuthenticated() (checked by ProjectLifecycleListener, and by
						// anything else gated on login) actually sees it.
						Preferences.STORE.setValue(Preferences.API_KEY, apiKey_str);
						Preferences.STORE.setValue(Preferences.ADDITIONAL_OPTIONS, additionalParams_str);
						Preferences.setCredentialsValidated(true);
						refreshRealtimeScannersLink();

						// Notify views (CheckmarxView/CxFindingsView) that credentials are now available
						// so they can switch from the credentials panel to the actual work views
						for (ISettingsChangeNotifier notifier : Preferences.getSettingsChangeNotifiers()) {
							notifier.notifySettingsApplied();
						}

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
							// Delegate to handler registered by devassist-lib (if available)
							IAuthenticationSuccessHandler handler = Preferences.getAuthenticationSuccessHandler();
							if (handler != null) {
								handler.onAuthenticationSuccess(mcpEnabled, logoutButtonHolder[0], apiKey_str, additionalParams_str);
							} else {
								CxLogger.warning("[PREFS] No authentication success handler registered - welcome dialog skipped");
								if (logoutButtonHolder[0] != null && !logoutButtonHolder[0].isDisposed()) {
									logoutButtonHolder[0].setEnabled(true);
								}
							}
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

		Button logoutButton = new Button(buttonsComposite, SWT.PUSH);
		logoutButtonHolder[0] = logoutButton;
		logoutButton.setText(PluginConstants.LOGOUT);
		GridData logoutButtonGridData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		logoutButtonGridData.widthHint = 80;
		logoutButton.setLayoutData(logoutButtonGridData);
		logoutButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MessageDialog confirmDialog = new MessageDialog(getShell(), PluginConstants.LOGOUT_CONFIRM_TITLE, null,
						PluginConstants.LOGOUT_CONFIRM_MESSAGE, MessageDialog.QUESTION,
						new String[] { "Yes", "Cancel" }, 0);
				if (confirmDialog.open() != 0) {
					return;
				}

				Preferences.clearApiKey();
				apiKey.setStringValue("");
//				textControl.setText("");
				connectionLabel.setText(PluginConstants.LOGOUT_SUCCESS_MESSAGE);
				refreshRealtimeScannersLink();
				getFieldEditorParent().layout();

				// Redraws the missing-credentials panel in CheckmarxView/CxFindingsView right
				// away. Without this, they only learn credentials are gone once performOk()
				// runs (i.e. the user clicks OK/Apply) - if they instead Cancel or just close
				// the dialog after Logout, both views kept showing stale "connected" content.
				// Notify main plugin that settings have changed
				for (ISettingsChangeNotifier notifier : Preferences.getSettingsChangeNotifiers()) {
					notifier.notifySettingsApplied();
				}
			}
		});

		spacer(topComposite);

		realtimeScannersLink = new Link(topComposite, SWT.NONE);
		realtimeScannersLink.setText("<a>"+PluginConstants.GO_TO_CHECKMARX_ONE_ASSIST+"</a>");
		realtimeScannersLink.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));

		// Call refresh after setting the LayoutData
		refreshRealtimeScannersLink();

		realtimeScannersLink.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(
					getShell(),
					"com.checkmarx.eclipse.devassist.prefs.checkmarxpreferencepage",
					null,
					null
				);
				if (dialog != null) {
					CxPreferencesDialogSizing.applyTo(dialog);
					dialog.open();
				}
			}
		});
	}

	private static String mapAuthResult(String result) {
		if (result != null && result.contains(PluginConstants.AUTH_SUCCESS_PATTERN)) {
			return PluginConstants.AUTH_SUCCESS_DISPLAY;
		}
		return result;
	}

	private Label spacer(Composite parent) {
		return new Label(parent, SWT.NONE);
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
				// Notify main plugin that settings have changed
				for (ISettingsChangeNotifier notifier : Preferences.getSettingsChangeNotifiers()) {
					notifier.notifySettingsApplied();
				}
			}
		}

		return ok;
	}
}