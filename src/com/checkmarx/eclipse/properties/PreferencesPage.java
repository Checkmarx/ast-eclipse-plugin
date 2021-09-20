package com.checkmarx.eclipse.properties;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.checkmarx.eclipse.Activator;

public class PreferencesPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	
	private AuthButtonFieldEditor tokenField; 

	public PreferencesPage() {
		super(GRID);
		Activator.getDefault().getPreferenceStore().addPropertyChangeListener(this::handlePropertyChange);
	}
	
	private void handlePropertyChange(PropertyChangeEvent event) {

	}
	
	@Override
	public void init(IWorkbench workbench) {
		   setPreferenceStore(Preferences.STORE);
	       setMessage("Checkmarx AST preferences");
	       setDescription("- Please use 'Test Connection' to verify the credentials.");

	}

	@Override
	protected void createFieldEditors() {
        addField(new StringFieldEditor(Preferences.SERVER_URL, "Server Url:", getFieldEditorParent()));
        addField(new StringFieldEditor(Preferences.AUTHENTICATION_URL, "Authentication Url:", getFieldEditorParent()));
        
        addField(new StringFieldEditor(Preferences.TENANT, "Tenant:", getFieldEditorParent()));

        tokenField = new AuthButtonFieldEditor(Preferences.API_KEY, "AST API Key:", getFieldEditorParent());
        Text textControl = tokenField.getTextControl(getFieldEditorParent());
        //set the width for API Key text field        
        GridData gridData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
        gridData.widthHint = 300;  // Some width
        textControl.setLayoutData(gridData);
        
        addField(tokenField);
        addField(space());
        addField(new StringFieldEditor(Preferences.ADDITIONAL_OPTIONS, "Additional Options:",50,5,0, getFieldEditorParent()));

	}
	
	private FieldEditor space() {
		return new LabelFieldEditor("", getFieldEditorParent());
	}
	
//	private FieldEditor label(String label) {
//		return new LabelFieldEditor(label, getFieldEditorParent());
//	}
	
	public void persist() {
		super.performOk();
	}

}
