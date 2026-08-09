package com.checkmarx.eclipse.common.properties;

import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

import com.checkmarx.eclipse.common.preferences.CheckmarxPreferencePage;


/**
 * Eclipse's shared Window &gt; Preferences dialog (WorkbenchPreferenceDialog) remembers
 * its shell size across sessions. Once that remembered size is smaller than what this
 * plugin's own pages need, they get clipped behind an inner scrollbar on every later
 * reopen, no matter how much content they actually have.
 *
 * Rather than changing that shared dialog's sizing/resizing behaviour - which would
 * also affect every other plugin's preference pages - this only grows the dialog
 * (never shrinks it) while one of this plugin's own pages is the one actually being
 * shown, right when it's first shown and again on every later switch back to it.
 */
public final class CxPreferencesDialogSizing {

	private CxPreferencesDialogSizing() {
	}

	public static void applyTo(PreferenceDialog dialog) {
		growIfOwnPage(dialog, dialog.getSelectedPage());
		dialog.addPageChangedListener(event -> growIfOwnPage(dialog, event.getSelectedPage()));
	}

	private static void growIfOwnPage(PreferenceDialog dialog, Object page) {
		if (!(page instanceof PreferencesPage) && !(page instanceof CheckmarxPreferencePage)) {
			return;
		}

		Shell shell = dialog.getShell();
		if (shell == null || shell.isDisposed()) {
			return;
		}

		Point required = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
		Point current = shell.getSize();
		int width = Math.max(required.x, current.x);
		int height = Math.max(required.y, current.y);
		if (width != current.x || height != current.y) {
			shell.setSize(width, height);
		}
	}
}
