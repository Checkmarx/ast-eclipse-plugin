package com.checkmarx.eclipse.common.preferences;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.util.PropertyChangeEvent;
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
import com.checkmarx.eclipse.common.utils.PluginConstants;
import com.checkmarx.eclipse.common.listener.IMcpInstallCallback;
import com.checkmarx.eclipse.common.listener.IMcpInstallHandler;
import com.checkmarx.eclipse.common.listener.ISettingsChangeNotifier;

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
    private Label mcpStatusLabel;
    private boolean loggedIn;


    public CheckmarxPreferencePage() {
        super();
        setPreferenceStore(com.checkmarx.eclipse.common.preferences.Preferences.STORE);
        // Listen for preference changes to update login state.
        // Critical: if user logs out in another page while this page is visible in the
        // same
        // dialog session, we need to refresh the UI to show logged-out content instead
        // of stale
        // logged-in checkboxes. Without this, performOk() would still run with stale
        // loggedIn=true.
        Preferences.STORE.addPropertyChangeListener(this::handlePreferenceChange);
    }

    /**
     * Called when preferences change (e.g., user logs out in another page of the
     * same dialog).
     * Re-reads the login state and updates the visible UI accordingly.
     */
    private void handlePreferenceChange(PropertyChangeEvent event) {
        // Re-check login state: if API key was cleared, we need to switch from
        // logged-in scanner checkboxes to logged-out message
        boolean isNowLoggedIn = Preferences.isAuthenticated();
        if (loggedIn != isNowLoggedIn) {
            loggedIn = isNowLoggedIn;
        }
    }

    @Override
    protected Control createContents(Composite parent) {
        loggedIn = Preferences.isAuthenticated();
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
        createSectionHeader(mainPanel, PluginConstants.DEVASSIST_PLUGIN_REALTIME_SCANNERS_ASCA_TITLE);
        Composite ascaComp = createIndentComposite(mainPanel);
        ascaCheckbox = new Button(ascaComp, SWT.CHECK);
        ascaCheckbox.setText(PluginConstants.ASCA_CHECKBOX);

        // --- OSS Section ---
        createSectionHeader(mainPanel, PluginConstants.DEVASSIST_PLUGIN_REALTIME_SCANNERS_OSS_TITLE);
        Composite ossComp = createIndentComposite(mainPanel);
        ossCheckbox = new Button(ossComp, SWT.CHECK);
        ossCheckbox.setText(PluginConstants.OSS_REALTIME_CHECKBOX);

        // --- Secrets Section ---
        createSectionHeader(mainPanel, PluginConstants.DEVASSIST_PLUGIN_REALTIME_SCANNERS_SECRETS_TITLE);
        Composite secretsComp = createIndentComposite(mainPanel);
        secretsCheckbox = new Button(secretsComp, SWT.CHECK);
        secretsCheckbox.setText(PluginConstants.SECRETS_REALTIME_CHECKBOX);

        // --- Containers Section ---
        createSectionHeader(mainPanel, PluginConstants.DEVASSIST_PLUGIN_REALTIME_SCANNERS_CONTAINERS_TITLE);
        Composite containersComp = createIndentComposite(mainPanel);
        containersCheckbox = new Button(containersComp, SWT.CHECK);
        containersCheckbox.setText(PluginConstants.CONTAINERS_REALTIME_CHECKBOX);

        // --- IaC Section ---
        createSectionHeader(mainPanel, PluginConstants.DEVASSIST_PLUGIN_REALTIME_SCANNERS_IAC_TITLE);
        Composite iacComp = createIndentComposite(mainPanel);
        iacCheckbox = new Button(iacComp, SWT.CHECK);
        iacCheckbox.setText(PluginConstants.IAC_REALTIME_CHECKBOX);

        // --- Container Tool Selection Section ---
        createSectionHeader(mainPanel, PluginConstants.DEVASSIST_PLUGIN_REALTIME_SCANNERS_IAC_PREFIX);
        Composite containerToolComp = createIndentComposite(mainPanel);
        Label containerDesc = new Label(containerToolComp, SWT.WRAP);
        containerDesc.setText(PluginConstants.CONTAINERS_TOOL_DESCRIPTION);
        GridData descData = new GridData(GridData.FILL_HORIZONTAL);
        containerDesc.setLayoutData(descData);

        containersToolCombo = new Combo(containerToolComp, SWT.READ_ONLY);
        containersToolCombo.setItems(PluginConstants.CONTAINERS_TOOLS);
        containersToolCombo.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // --- Checkmarx MCP Section ---
        // A horizontal rule marks this as a distinct settings group, separate from the
        // Realtime Scanner sections above.
        Label mcpSeparator = new Label(mainPanel, SWT.SEPARATOR | SWT.HORIZONTAL);
        GridData mcpSeparatorData = new GridData(GridData.FILL_HORIZONTAL);
        mcpSeparatorData.verticalIndent = 6;
        mcpSeparator.setLayoutData(mcpSeparatorData);

        createSectionHeader(mainPanel, PluginConstants.CHECKMARX_MCP_SECTION_TITLE);
        Composite mcpComp = createIndentComposite(mainPanel);
        
        Label mcpDesc = new Label(mcpComp, SWT.WRAP);
        mcpDesc.setText(PluginConstants.MCP_DESCRIPTION);
        mcpDesc.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        // installMcpLink and mcpStatusLabel share a row so the result message appears
        // right next to the link that triggered it, rather than on its own line.
        Composite installMcpRow = new Composite(mcpComp, SWT.NONE);
        GridLayout installMcpRowLayout = new GridLayout(2, false);
        installMcpRowLayout.marginWidth = 0;
        installMcpRowLayout.marginHeight = 0;
        installMcpRowLayout.horizontalSpacing = 10;
        installMcpRow.setLayout(installMcpRowLayout);
        installMcpRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Link installMcpLink = new Link(installMcpRow, SWT.NONE);
        installMcpLink.setText("<a>" + PluginConstants.INSTALL_MCP_LINK_TEXT + "</a>");
        installMcpLink.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

        mcpStatusLabel = new Label(installMcpRow, SWT.WRAP);
        mcpStatusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        installMcpLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                installMcp();
            }
        });

        Link editMcpSettingsLink = new Link(mcpComp, SWT.NONE);
        editMcpSettingsLink.setText("<a>" + PluginConstants.EDIT_MCP_SETTINGS_LINK_TEXT + "</a>");
        editMcpSettingsLink.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
        editMcpSettingsLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                editMcpSettings();
            }
        });

        loadValues();
        return mainPanel;
    }

    /**
     * Installs/updates the Checkmarx MCP server configuration. Delegates to the handler
     * registered by devassist-lib (this bundle - common-lib - doesn't depend on it directly),
     * and shows the result right next to the "Install MCP" link.
     */
    private void installMcp() {
        IMcpInstallHandler handler = Preferences.getMcpInstallHandler();
        if (handler == null) {
            CxLogger.warning("[PREFS] MCP install requested before the handler was registered");
            showMcpStatus(false, PluginConstants.MCP_INSTALL_UNAVAILABLE_MESSAGE);
            return;
        }

        showMcpStatus(null, PluginConstants.MCP_INSTALLING_STATE);

        handler.installMcp(new IMcpInstallCallback() {
            @Override
            public void onSuccess() {
                Display.getDefault().asyncExec(() -> showMcpStatus(true, PluginConstants.MCP_INSTALL_SUCCESS_MESSAGE));
            }

            @Override
            public void onAlreadyUpToDate() {
                Display.getDefault().asyncExec(() -> showMcpStatus(true, PluginConstants.MCP_ALREADY_UP_TO_DATE_MESSAGE));
            }

            @Override
            public void onFailure(String errorMessage) {
                Display.getDefault().asyncExec(() -> showMcpStatus(false, errorMessage));
            }
        });
    }

    /**
     * Updates mcpStatusLabel with an install result/progress message.
     *
     * @param success true = success (green), false = failure (red), null = in-progress
     *                (default color)
     */
    private void showMcpStatus(Boolean success, String message) {
        if (mcpStatusLabel == null || mcpStatusLabel.isDisposed()) {
            return;
        }

        Display display = mcpStatusLabel.getDisplay();
        if (success == null) {
            mcpStatusLabel.setForeground(null);
        } else if (success) {
            mcpStatusLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_GREEN));
        } else {
            mcpStatusLabel.setForeground(display.getSystemColor(SWT.COLOR_RED));
        }
        mcpStatusLabel.setText(message);
        mcpStatusLabel.getParent().layout(true, true);
    }

    /**
     * Opens GitHub Copilot for Eclipse's own MCP preference page, where the Checkmarx MCP
     * server entry (once installed) can be reviewed/edited alongside any other MCP servers.
     */
    private void editMcpSettings() {
        PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(getShell(),
                PluginConstants.COPILOT_MCP_PREFERENCE_PAGE_ID, null, null);
        if (dialog != null) {
            dialog.open();
        }
    }

    /**
     * Shown instead of the scanner checkboxes when the user isn't logged in - there
     * is nothing meaningful to configure until credentials are set in "Checkmarx
     * One".
     */
    private Control createLoggedOutContent(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginTop = 20;
        composite.setLayout(layout);
        composite.setLayoutData(new GridData(GridData.FILL_BOTH));

        Label message = new Label(composite, SWT.WRAP);
        message.setText(PluginConstants.LOGIN_NOTE_CXONE_ASSIST);
        message.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Link goToLoginLink = new Link(composite, SWT.NONE);
        goToLoginLink.setText("<a>"+PluginConstants.GO_TO_CHECKMARX_ONE+"</a>");
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
        // Check credentials fresh, not from captured field.
        // If user logged out while viewing another page, loggedIn would be stale.
        boolean isCurrentlyLoggedIn = Preferences.isAuthenticated();
        if (!isCurrentlyLoggedIn) {
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
        // Check credentials fresh, not from captured field.
        // Critical: if user logged out while viewing another page within the same
        // dialog session,
        // loggedIn would be stale and we'd save/notify with false authentication
        // status.
        boolean isCurrentlyLoggedIn = Preferences.isAuthenticated();
        if (!isCurrentlyLoggedIn) {
            return super.performOk();
        }
        IPreferenceStore store = getPreferenceStore();

        // Get current UI selections. It's possible this page's controls were never
        // created (Eclipse may call performOk() on pages that haven't had
        // createContents() invoked), or the controls may have been disposed. In
        // that case fall back to the stored preference values instead of
        // dereferencing null controls which caused an NPE in the field.
        boolean ascaSelected = store.getBoolean(PREF_ASCA_ENABLED);
        if (ascaCheckbox != null && !ascaCheckbox.isDisposed()) {
            ascaSelected = ascaCheckbox.getSelection();
        }

        boolean ossSelected = store.getBoolean(PREF_OSS_ENABLED);
        if (ossCheckbox != null && !ossCheckbox.isDisposed()) {
            ossSelected = ossCheckbox.getSelection();
        }

        boolean secretsSelected = store.getBoolean(PREF_SECRETS_ENABLED);
        if (secretsCheckbox != null && !secretsCheckbox.isDisposed()) {
            secretsSelected = secretsCheckbox.getSelection();
        }

        boolean containersSelected = store.getBoolean(PREF_CONTAINERS_ENABLED);
        if (containersCheckbox != null && !containersCheckbox.isDisposed()) {
            containersSelected = containersCheckbox.getSelection();
        }

        boolean iacSelected = store.getBoolean(PREF_IAC_ENABLED);
        if (iacCheckbox != null && !iacCheckbox.isDisposed()) {
            iacSelected = iacCheckbox.getSelection();
        }

        String containersTool = store.getString(PREF_CONTAINERS_TOOL);
        if (containersToolCombo != null && !containersToolCombo.isDisposed()) {
            try {
                String text = containersToolCombo.getText();
                if (text != null && !text.isBlank()) {
                    containersTool = text;
                }
            } catch (Exception ex) {
                // Defensive: protect against any SWT oddities; fall back to store value
                CxLogger.warning("[PREFS-PAGE] Failed to read containersToolCombo text, using stored value: " + ex.getMessage());
            }
        }

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

        // Step 3: Notify listeners (e.g., GlobalScannerController) about preference
        // changes
        // The listener will update GlobalScannerController based on new preferences
        // This decouples CheckmarxPreferencePage from devassist-lib modules
        for (ISettingsChangeNotifier notifier : Preferences.getSettingsChangeNotifiers()) {
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
