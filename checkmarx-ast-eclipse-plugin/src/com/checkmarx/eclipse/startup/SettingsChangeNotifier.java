package com.checkmarx.eclipse.startup;

import com.checkmarx.eclipse.common.listener.ISettingsChangeNotifier;
import com.checkmarx.eclipse.common.utils.PluginConstants;
import com.checkmarx.eclipse.utils.PluginUtils;

/**
 * Notifies views and components when preferences have been applied.
 *
 * Triggers UI updates in CheckmarxView/CxFindingsView when settings change,
 * allowing them to respond to credential or configuration updates.
 */
public class SettingsChangeNotifier implements ISettingsChangeNotifier {

	@Override
	public void notifySettingsApplied() {
		PluginUtils.getEventBroker().post(PluginConstants.TOPIC_APPLY_SETTINGS, PluginConstants.EMPTY_STRING);
	}
}
