package com.checkmarx.eclipse.views.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.views.DisplayModel;

public class ActionOpenPreferencesPage extends CxBaseAction {
	
	private static final String PREFERENCE_PAGE_ID = "com.checkmarx.eclipse.properties.preferencespage";
	private static final String LABEL_PREFERENCES = "Preferences";

	private Shell shell;
	
	public ActionOpenPreferencesPage(DisplayModel rootModel, TreeViewer resultsTree, Shell shell) {
		super(rootModel, resultsTree);
		
		this.shell = shell;
	}

	/**
	 * Creates a JFace action to open the preference page
	 */
	public Action createAction() {
		Action openPreferencesPageAction = new Action() {
			@Override
			public void run() {
				PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(shell, PREFERENCE_PAGE_ID, null, null);
				
				if (pref != null) {
					pref.open();
				}
			}
		};

		openPreferencesPageAction.setId(ActionName.PREFERENCES.name());
		openPreferencesPageAction.setText(LABEL_PREFERENCES);
		
		return openPreferencesPageAction;
	}

}
