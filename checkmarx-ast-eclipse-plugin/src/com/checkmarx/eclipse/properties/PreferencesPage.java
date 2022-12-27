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
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.runner.Authenticator;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.utils.PluginUtils;

public class PreferencesPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

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
		addField(apiKey);
		Text textControl = apiKey.getTextControl(topComposite);

		StringFieldEditor additionalParams = new StringFieldEditor(Preferences.ADDITIONAL_OPTIONS,
				PluginConstants.PREFERENCES_ADDITIONAL_OPTIONS, topGridData.widthHint, 5, 0, topComposite);
		addField(additionalParams);

        //set the width for API Key text field        
		GridData gridData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
		gridData.widthHint = 500; // Some width
		gridData.grabExcessHorizontalSpace = false;
		gridData.horizontalAlignment = GridData.FILL;
		textControl.setLayoutData(gridData);

		addField(space());

		Text connectionLabel = new Text(getFieldEditorParent(), SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL);
		//Set layout for scroll area to fit to page
		connectionLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Button connectionButton = new Button(topComposite, SWT.PUSH);
		connectionButton.setText(PluginConstants.PREFERENCES_TEST_CONNECTION);
		connectionButton.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent e) {

				String apiKey_str = apiKey.getStringValue();

				String additionalParams_str = additionalParams.getStringValue();
				connectionButton.setEnabled(false);
				connectionLabel.setText(PluginConstants.PREFERENCES_VALIDATING_STATE);
				getFieldEditorParent().layout();
				CompletableFuture.supplyAsync(() -> {
					try {
						return Authenticator.INSTANCE.doAuthentication(
								apiKey_str, additionalParams_str);
					} catch (Throwable t) {
						CxLogger.error(PluginConstants.ERROR_AUTHENTICATING_AST, new Exception(t));
						return t.getMessage();
					}
				}).thenAccept((result) -> Display.getDefault().syncExec(() -> {
					connectionLabel.setText(result);
					getFieldEditorParent().layout();
					connectionButton.setEnabled(true);
				}));
			}
		});
	}

	private FieldEditor space() {
		return new LabelFieldEditor("", getFieldEditorParent());
	}

	@Override
	public boolean performOk() {
		boolean ok = super.performOk();

		if (ok) {
			PluginUtils.getEventBroker().post(PluginConstants.TOPIC_APPLY_SETTINGS, PluginConstants.EMPTY_STRING);
		}

		return ok;
	}
}
