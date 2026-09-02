package com.checkmarx.eclipse.common.listener;

/**
 * Notifies listeners when settings have been applied or changed.
 *
 * Allows PreferencesPage (common-lib) to notify the main plugin about settings
 * changes without creating a reverse dependency.
 */
public interface ISettingsChangeNotifier {

	/**
	 * Notify that settings have been applied/changed.
	 * This triggers UI updates in views and components.
	 */
	void notifySettingsApplied();
}
