package com.checkmarx.eclipse.common.preferences;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.eclipse.core.runtime.preferences.InstanceScope;

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
    private Label ascaInstallationMsg;
    private Button ossCheckbox;
    private Button secretsCheckbox;
    private Button containersCheckbox;
    private Button iacCheckbox;
    private Combo containersToolCombo;
    private Label mcpStatusLabel;

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
		// Pass the Bundle-SymbolicName of the plugin storing the preferences
        IPreferenceStore store = new ScopedPreferenceStore(
            InstanceScope.INSTANCE, 
            "com.checkmarx.eclipse.plugin" // Replace with exact Bundle-SymbolicName from MANIFEST.MF
        );
        setPreferenceStore(store);
	}

	@Override
    protected Control createContents(Composite parent) {
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
        // Save current UI control state into PreferenceStore
        IPreferenceStore store = getPreferenceStore();
        store.setValue(PREF_ASCA_ENABLED, ascaCheckbox.getSelection());
        store.setValue(PREF_OSS_ENABLED, ossCheckbox.getSelection());
        store.setValue(PREF_SECRETS_ENABLED, secretsCheckbox.getSelection());
        store.setValue(PREF_CONTAINERS_ENABLED, containersCheckbox.getSelection());
        store.setValue(PREF_IAC_ENABLED, iacCheckbox.getSelection());

        if (containersToolCombo.getText() != null) {
            store.setValue(PREF_CONTAINERS_TOOL, containersToolCombo.getText());
        }

        // Trigger change event for listeners
        store.firePropertyChangeEvent("scannerPreferencesChanged", null, null);

        return super.performOk();
    }

}
