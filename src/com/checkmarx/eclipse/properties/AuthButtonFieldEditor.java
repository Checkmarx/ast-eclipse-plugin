package com.checkmarx.eclipse.properties;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.swt.widgets.Composite;

import com.checkmarx.eclipse.runner.Authenticator;

public class AuthButtonFieldEditor extends StringButtonFieldEditor {

	  protected AuthButtonFieldEditor(String name, String labelText,
	            Composite parent) {
	        super(name, labelText, parent);
	        setChangeButtonText("Test Connection");

	    }

		@Override
		protected String changePressed() {

			Integer result = Authenticator.INSTANCE.doAuthentication();
			if(result == 0)
			{
				MessageDialog.openInformation(null, "Authentication", "Connection successfull !");
			}
			else
			{
				MessageDialog.openInformation(null, "Authentication", "Please check your credentials!");
			}
			
			return null;
		}
		
		public void emptyTextfield() {
			setStringValue("");
			Preferences.store(Preferences.API_KEY, "");
		}
		
		

}
