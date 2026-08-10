package com.checkmarx.eclipse.common.preferences;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.listener.ISettingsChangeNotifier;
import com.checkmarx.eclipse.common.properties.Preferences;

/**
 * Preference page for configuring Checkmarx scanner settings.
 * Allows users to enable/disable individual scanners and select scan frequency.
 */
public class CheckmarxPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	// Preference Keys
    public static final String PREF_ASCA_ENABLED = "scanner.asca.enabled";
    public static final String PREF_OSS_ENABLED = "scanner.oss.enabled";
    public static final String PREF_SECRETS_ENABLED = "scanner.secrets.enabled";
    public static final String PREF_CONTAINERS_ENABLED = "scanner.containers.enabled";
    public static final String PREF_IAC_ENABLED = "scanner.iac.enabled";
    public static final String PREF_CONTAINERS_TOOL = "scanner.containers.tool";

    // Controls
    private Label assistMessageLabel;
    private Button ascaCheckbox;
    private Button ossCheckbox;
    private Button secretsCheckbox;
    private Button containersCheckbox;
    private Button iacCheckbox;
    private Combo containersToolCombo;
    private boolean loggedIn;

    public static final String DEVASSIST_PLUGIN_REALTIME_SCANNERS_OSS_TITLE= "Checkmarx Developer Assist Open Source Realtime Scanner (OSS-Realtime): Activate OSS-Realtime";
	public static final String DEVASSIST_PLUGIN_REALTIME_SCANNERS_SECRETS_TITLE="Checkmarx Developer Assist Secret Detection Realtime Scanner: Activate Secret Detection Realtime";
	public static final String DEVASSIST_PLUGIN_REALTIME_SCANNERS_CONTAINERS_TITLE= "Checkmarx Developer Assist Containers Realtime Scanner: Activate Containers Realtime";
	public static final String DEVASSIST_PLUGIN_REALTIME_SCANNERS_IAC_TITLE= "Checkmarx Developer Assist IAC Realtime Scanner: Activate IAC Realtime";
	public static final String DEVASSIST_PLUGIN_REALTIME_SCANNERS_ASCA_TITLE= "Checkmarx Developer Assist AI Secure Coding Assistant (ASCA): Activate ASCA";
	public static final String DEVASSIST_PLUGIN_REALTIME_SCANNERS_IAC_PREFIX= "Checkmarx Developer Assist IAC Realtime Scanner: Containers Management Tool";
	public static final String DEVASSIST_PLUGIN_WELCOME_TITLE= "Welcome to Checkmarx Developer Assist";
	public static final String CONTAINERS_TOOL_DESCRIPTION="Select the Containers Management Tool to use for IaC scanning.";

	public CheckmarxPreferencePage() {
		super();
		setPreferenceStore(com.checkmarx.eclipse.common.properties.Preferences.STORE);
	}

	@Override
    protected Control createContents(Composite parent) {
        loggedIn = StringUtils.isNotBlank(Preferences.getApiKey());
        if (!loggedIn) {
            return createLoggedOutContent(parent);
        }

        Composite mainPanel = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.verticalSpacing = 8;
        layout.horizontalSpacing = 0;
        mainPanel.setLayout(layout);
        mainPanel.setLayoutData(new GridData(GridData.FILL_BOTH));

        // Assist Message Label (Hidden by default, red text)
        assistMessageLabel = new Label(mainPanel, SWT.NONE);
        assistMessageLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_RED));
        GridData msgData = new GridData(GridData.FILL_HORIZONTAL);
        msgData.exclude = true; // Equivalent to hidemode 3
        assistMessageLabel.setLayoutData(msgData);
        assistMessageLabel.setVisible(false);

        // --- ASCA Section ---
        createSectionHeader(mainPanel, DEVASSIST_PLUGIN_REALTIME_SCANNERS_ASCA_TITLE);
        Composite ascaComp = createIndentComposite(mainPanel);
        ascaCheckbox = new Button(ascaComp, SWT.CHECK);
        ascaCheckbox.setText("Enable ASCA Scanner");

        // --- OSS Section ---
        createSectionHeader(mainPanel, DEVASSIST_PLUGIN_REALTIME_SCANNERS_OSS_TITLE);
        Composite ossComp = createIndentComposite(mainPanel);
        ossCheckbox = new Button(ossComp, SWT.CHECK);
        ossCheckbox.setText("Enable OSS Scanner");

        // --- Secrets Section ---
        createSectionHeader(mainPanel, DEVASSIST_PLUGIN_REALTIME_SCANNERS_SECRETS_TITLE);
        Composite secretsComp = createIndentComposite(mainPanel);
        secretsCheckbox = new Button(secretsComp, SWT.CHECK);
        secretsCheckbox.setText("Enable Secrets Scanner");

        // --- Containers Section ---
        createSectionHeader(mainPanel, DEVASSIST_PLUGIN_REALTIME_SCANNERS_CONTAINERS_TITLE);
        Composite containersComp = createIndentComposite(mainPanel);
        containersCheckbox = new Button(containersComp, SWT.CHECK);
        containersCheckbox.setText("Enable Container Scanner");

        // --- IaC Section ---
        createSectionHeader(mainPanel, DEVASSIST_PLUGIN_REALTIME_SCANNERS_IAC_TITLE);
        Composite iacComp = createIndentComposite(mainPanel);
        iacCheckbox = new Button(iacComp, SWT.CHECK);
        iacCheckbox.setText("Enable IaC Scanner");

        // --- Container Tool Selection Section ---
        createSectionHeader(mainPanel, DEVASSIST_PLUGIN_REALTIME_SCANNERS_IAC_PREFIX);
        Composite containerToolComp = createIndentComposite(mainPanel);
        Label containerDesc = new Label(containerToolComp, SWT.WRAP);
        containerDesc.setText(CONTAINERS_TOOL_DESCRIPTION);
        GridData descData = new GridData(GridData.FILL_HORIZONTAL);
        containerDesc.setLayoutData(descData);

        containersToolCombo = new Combo(containerToolComp, SWT.READ_ONLY);
        containersToolCombo.setItems(new String[] { "docker", "podman"});
        containersToolCombo.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        loadValues();
        return mainPanel;
    }

	/**
	 * Shown instead of the scanner checkboxes when the user isn't logged in - there
	 * is nothing meaningful to configure until credentials are set in "Checkmarx One".
	 */
	private Control createLoggedOutContent(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginTop = 20;
        composite.setLayout(layout);
        composite.setLayoutData(new GridData(GridData.FILL_BOTH));

        Label message = new Label(composite, SWT.WRAP);
        message.setText("Log in to Checkmarx One to configure Realtime Scanners.");
        message.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Link goToLoginLink = new Link(composite, SWT.NONE);
        goToLoginLink.setText("<a>Go to Checkmarx One preferences</a>");
        goToLoginLink.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
        goToLoginLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(
                        parent.getShell(), "com.checkmarx.eclipse.properties.preferencespage", null, null);
                if (dialog != null) {
                    dialog.open();
                }
            }
        });

        return composite;
    }

	private Composite createIndentComposite(Composite parent) {
        Composite comp = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginLeft = 15;
        layout.marginTop = 0;
        comp.setLayout(layout);
        comp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        return comp;
    }

    private void loadValues() {
        IPreferenceStore store = getPreferenceStore();
        ascaCheckbox.setSelection(store.getBoolean(PREF_ASCA_ENABLED));
        ossCheckbox.setSelection(store.getBoolean(PREF_OSS_ENABLED));
        secretsCheckbox.setSelection(store.getBoolean(PREF_SECRETS_ENABLED));
        containersCheckbox.setSelection(store.getBoolean(PREF_CONTAINERS_ENABLED));
        iacCheckbox.setSelection(store.getBoolean(PREF_IAC_ENABLED));

        String tool = store.getString(PREF_CONTAINERS_TOOL);
        if (tool != null && !tool.isBlank()) {
            containersToolCombo.setText(tool);
        } else if (containersToolCombo.getItemCount() > 0) {
            containersToolCombo.select(0);
        }
    }

    @Override
    protected void performDefaults() {
        if (!loggedIn) {
            super.performDefaults();
            return;
        }
        IPreferenceStore store = getPreferenceStore();
        ascaCheckbox.setSelection(store.getDefaultBoolean(PREF_ASCA_ENABLED));
        ossCheckbox.setSelection(store.getDefaultBoolean(PREF_OSS_ENABLED));
        secretsCheckbox.setSelection(store.getDefaultBoolean(PREF_SECRETS_ENABLED));
        containersCheckbox.setSelection(store.getDefaultBoolean(PREF_CONTAINERS_ENABLED));
        iacCheckbox.setSelection(store.getDefaultBoolean(PREF_IAC_ENABLED));
        super.performDefaults();
    }

	/**
     * Helper to create a titled section with a horizontal line separator.
     */
	private void createSectionHeader(Composite parent, String titleText) {
		Composite headerComp = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginTop = 6;
		layout.marginBottom = 0;
		headerComp.setLayout(layout);
		headerComp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		int colonIndex = titleText.indexOf(":");

		StyledText title = new StyledText(headerComp, SWT.READ_ONLY | SWT.WRAP);
		title.setText(titleText);
		title.setBackground(headerComp.getBackground()); // Match background color
		title.setCaret(null); // Hide text cursor

		if (colonIndex != -1 && colonIndex + 1 < titleText.length()) {
			int start = colonIndex + 1; // Start right after the colon
			int length = titleText.length() - start;

			StyleRange boldStyle = new StyleRange();
			boldStyle.start = start;
			boldStyle.length = length;
			boldStyle.fontStyle = SWT.BOLD;

			title.setStyleRange(boldStyle);

		}
	}

	@Override
	public void init(IWorkbench workbench) {
		// Initialization if needed
	}

	@Override
    public boolean performOk() {
        if (!loggedIn) {
            return super.performOk();
        }
        IPreferenceStore store = getPreferenceStore();

        // Get current UI selections
        boolean ascaSelected = ascaCheckbox.getSelection();
        boolean ossSelected = ossCheckbox.getSelection();
        boolean secretsSelected = secretsCheckbox.getSelection();
        boolean containersSelected = containersCheckbox.getSelection();
        boolean iacSelected = iacCheckbox.getSelection();
        String containersTool = containersToolCombo.getText();

        // Step 1: Save current UI state to preference store
        store.setValue(PREF_ASCA_ENABLED, ascaSelected);
        store.setValue(PREF_OSS_ENABLED, ossSelected);
        store.setValue(PREF_SECRETS_ENABLED, secretsSelected);
        store.setValue(PREF_CONTAINERS_ENABLED, containersSelected);
        store.setValue(PREF_IAC_ENABLED, iacSelected);
        if (containersTool != null) {
            store.setValue(PREF_CONTAINERS_TOOL, containersTool);
        }

        // Diagnostic: Verify what was saved
        CxLogger.info("[PREFS-PAGE] Saved to preference store: ASCA=" + ascaSelected + ", OSS=" + ossSelected +
                     ", SECRETS=" + secretsSelected + ", CONTAINERS=" + containersSelected + ", IAC=" + iacSelected);

        // Step 2: Save as user preferences (mirrors JetBrains apply() method)
        // This preserves user's choices if features toggle on/off later
        Preferences.setUserPreferences(ascaSelected, ossSelected, secretsSelected,
                                      containersSelected, iacSelected);
        CxLogger.info("[PREFS-PAGE] Saved as user preferences");

        // Step 3: Notify listeners (e.g., GlobalScannerController) about preference changes
        // The listener will update GlobalScannerController based on new preferences
        // This decouples CheckmarxPreferencePage from devassist-lib modules
        ISettingsChangeNotifier notifier = Preferences.getSettingsChangeNotifier();
        if (notifier != null) {
            try {
                notifier.notifySettingsApplied();
                CxLogger.info("[PREFS] Notified settings change listeners");
            } catch (Exception e) {
                CxLogger.warning("[PREFS] Failed to notify settings change: " + e.getMessage());
            }
        }

        // Step 4: Trigger change event for listeners
        store.firePropertyChangeEvent("scannerPreferencesChanged", null, null);

        return super.performOk();
    }

}
