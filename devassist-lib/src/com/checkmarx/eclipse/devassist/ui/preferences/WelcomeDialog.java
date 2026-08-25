package com.checkmarx.eclipse.devassist.ui.preferences;

import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.css.swt.theme.ITheme;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.IServiceLocator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.service.event.EventHandler;
import com.checkmarx.eclipse.common.utils.CxLogger;

/**
 * Welcome dialog for Checkmarx Eclipse plugin.
 * Displayed after successful authentication to inform users about key features.
 */
public class WelcomeDialog extends TitleAreaDialog {

	private static final int DIALOG_WIDTH = 800;
	private static final int DIALOG_HEIGHT = 620;
	private static final int WRAP_WIDTH = 250;
	private static final int BULLET_INDENT = 20;
	private static final int CONTENT_MARGIN = 20;
	private static final int IMAGE_PANEL_WIDTH = 380;
	private static final String SCANNER_IMAGE_PATH = "icons/welcomePageScanner.svg";
	private static final String SCANNER_IMAGE_PATH_DARK = "icons/welcomePageScanner_dark.svg";
	// Key the e4 CSS theme engine itself is registered under on the Display;
	// this is the same lookup org.eclipse.e4.ui.css.swt.internal.theme.ThemeEngineManager
	// uses internally, so it reflects Eclipse's actual active theme (not a guess).
	private static final String THEME_ENGINE_DISPLAY_KEY = "org.eclipse.e4.ui.css.swt.theme";
	private static final String DARK_THEME_ID_FRAGMENT = "dark";

	private final boolean mcpEnabled;
	private Button realTimeScannersCheckbox;
	private final RealTimeSettingsManager settingsManager;
	private Image scannerImage;
	private Label scannerImageLabel;
	private IEventBroker themeEventBroker;
	private EventHandler themeChangeHandler;

	/**
	 * Constructor
	 * @param parentShell the parent shell
	 * @param mcpEnabled whether MCP is enabled for the tenant
	 */
	public WelcomeDialog(Shell parentShell, boolean mcpEnabled) {
		this(parentShell, mcpEnabled, new DefaultRealTimeSettingsManager());
	}

	/**
	 * Constructor with dependency injection for testability
	 * @param parentShell the parent shell
	 * @param mcpEnabled whether MCP is enabled for the tenant
	 * @param settingsManager manager for real-time settings
	 */
	public WelcomeDialog(Shell parentShell, boolean mcpEnabled, RealTimeSettingsManager settingsManager) {
		super(parentShell);
		this.mcpEnabled = mcpEnabled;
		this.settingsManager = settingsManager;
		// Deliberately not adding SWT.RESIZE: the dialog is sized to show every
		// section at once, so resizing (which could clip content again) is disabled.

		// Log MCP status for debugging
		String mcpStatus = mcpEnabled ? "ENABLED" : "DISABLED";
		CxLogger.info("[WELCOME] MCP status: " + mcpStatus);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Checkmarx");
		shell.setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle(DevAssistConstants.WELCOME_TITLE);
		setMessage(DevAssistConstants.WELCOME_SUBTITLE);
		setTitleImage(null); // Remove title image for cleaner look

		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new GridLayout(1, false));

		createContentArea(container);

		return container;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, OK, DevAssistConstants.WELCOME_CLOSE_BUTTON, true);
	}

	private void createContentArea(Composite container) {

	    // Everything below is laid out directly (no scrolled composite) so that,
	    // combined with the fixed, non-resizable dialog size, all content is
	    // visible at once without scrolling or clipping.
	    Composite mainRow = new Composite(container, SWT.NONE);
	    GridLayout rowLayout = new GridLayout(2, false);
	    rowLayout.marginLeft = CONTENT_MARGIN;
	    rowLayout.marginRight = CONTENT_MARGIN;
	    rowLayout.marginTop = CONTENT_MARGIN;
	    rowLayout.marginBottom = CONTENT_MARGIN;
	    rowLayout.horizontalSpacing = 20;
	    mainRow.setLayout(rowLayout);
	    mainRow.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

	    // Left column - Feature card + main bullets, stacked
	    Composite leftColumn = new Composite(mainRow, SWT.NONE);
	    GridLayout leftLayout = new GridLayout(1, false);
	    leftLayout.marginWidth = 0;
	    leftLayout.marginHeight = 0;
	    leftLayout.verticalSpacing = 8;
	    leftColumn.setLayout(leftLayout);
	    leftColumn.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

	    if (mcpEnabled) {
	        addFeatureCard(leftColumn);
	    }

	    addBullet(leftColumn,  DevAssistConstants.WELCOME_MAIN_FEATURE_1);
	    addBullet(leftColumn,  DevAssistConstants.WELCOME_MAIN_FEATURE_2);
	    addBullet(leftColumn,  DevAssistConstants.WELCOME_MAIN_FEATURE_3);
	    addBullet(leftColumn,  DevAssistConstants.WELCOME_MAIN_FEATURE_4);

	    // Right column - scanner image
	    createScannerImage(mainRow);
	}

	private void createScannerImage(Composite container) {
		Composite rightPanel = new Composite(container, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		rightPanel.setLayout(layout);
		GridData gd = new GridData(SWT.CENTER, SWT.TOP, true, false);
		gd.widthHint = IMAGE_PANEL_WIDTH;
		rightPanel.setLayoutData(gd);

		scannerImageLabel = new Label(rightPanel, SWT.CENTER);
		scannerImageLabel.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));

		scannerImage = loadScannerImage();
		if (scannerImage != null) {
			scannerImageLabel.setImage(scannerImage);
		}
		scannerImageLabel.addDisposeListener(e -> {
			unsubscribeThemeChangeListener();
			if (scannerImage != null && !scannerImage.isDisposed()) {
				scannerImage.dispose();
			}
		});

		registerThemeChangeListener();
	}

	private Image loadScannerImage() {
		String path = isDarkTheme() ? SCANNER_IMAGE_PATH_DARK : SCANNER_IMAGE_PATH;
		try {
			// devassist-lib bundle symbolic name
			ImageDescriptor descriptor = AbstractUIPlugin.imageDescriptorFromPlugin("com.checkmarx.eclipse.devassist", path);
			if (descriptor != null) {
				return descriptor.createImage();
			}
		} catch (Exception e) {
			CxLogger.error("Failed to load welcome scanner image", e);
		}
		return null;
	}

	/**
	 * Reads Eclipse's own e4 CSS theme engine - the same mechanism the Platform
	 * uses to decide dark vs. light styling - so the scanner image always matches
	 * whatever theme Eclipse is actually rendering with, instead of guessing from
	 * a color sample (which broke down in practice, e.g. custom/high-contrast themes).
	 */
	private boolean isDarkTheme() {
		ITheme activeTheme = getActiveTheme();
		if (activeTheme != null && activeTheme.getId() != null) {
			return activeTheme.getId().toLowerCase().contains(DARK_THEME_ID_FRAGMENT);
		}
		return isDarkByBackgroundLuminance();
	}

	private ITheme getActiveTheme() {
		try {
			Display display = Display.getCurrent();
			Object engineData = display != null ? display.getData(THEME_ENGINE_DISPLAY_KEY) : null;
			if (engineData instanceof IThemeEngine) {
				return ((IThemeEngine) engineData).getActiveTheme();
			}
		} catch (Throwable t) {
			// e4 CSS theming bundle not present/active in this runtime; caller falls back.
			CxLogger.error("Eclipse e4 theme engine unavailable, falling back to color heuristic",
					t instanceof Exception ? (Exception) t : new Exception(t));
		}
		return null;
	}

	/**
	 * Fallback for the rare runtime where the e4 CSS theme engine isn't registered
	 * on the Display: approximate dark mode from the widget background luminance.
	 */
	private boolean isDarkByBackgroundLuminance() {
		Color background = Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
		double luminance = (0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue()) / 255.0;
		return luminance < 0.5;
	}

	/**
	 * Keeps the scanner image correct if the user flips Eclipse's theme (Preferences &gt;
	 * General &gt; Appearance) while this dialog happens to be open, instead of only
	 * checking the theme once at open time.
	 */
	private void registerThemeChangeListener() {
		try {
			// Get event broker from OSGi service registry via PlatformUI
			Object serviceLocator = PlatformUI.getWorkbench();
			if (serviceLocator instanceof IServiceLocator) {
				themeEventBroker = ((IServiceLocator) serviceLocator).getService(IEventBroker.class);
			}

			if (themeEventBroker != null) {
				themeChangeHandler = event -> Display.getDefault().asyncExec(this::refreshScannerImageForThemeChange);
				themeEventBroker.subscribe(IThemeEngine.Events.THEME_CHANGED, themeChangeHandler);
			}
		} catch (Exception e) {
			CxLogger.error("Failed to subscribe to Eclipse theme change events", e);
		}
	}

	private void unsubscribeThemeChangeListener() {
		if (themeEventBroker != null && themeChangeHandler != null) {
			themeEventBroker.unsubscribe(themeChangeHandler);
		}
		themeEventBroker = null;
		themeChangeHandler = null;
	}

	private void refreshScannerImageForThemeChange() {
		if (scannerImageLabel == null || scannerImageLabel.isDisposed()) {
			return;
		}
		Image newImage = loadScannerImage();
		Image oldImage = scannerImage;
		scannerImage = newImage;
		scannerImageLabel.setImage(newImage);
		scannerImageLabel.getParent().layout();
		if (oldImage != null && !oldImage.isDisposed()) {
			oldImage.dispose();
		}
	}

	private void addFeatureCard(Composite parent) {
		Composite card = new Composite(parent, SWT.BORDER);
		GridLayout layout = new GridLayout(1, false);
		layout.marginLeft = 10;
		layout.marginRight = 10;
		layout.marginTop = 10;
		layout.marginBottom = 10;
		layout.verticalSpacing = 4;
		card.setLayout(layout);
		card.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		// Set subtle background color
		Color bgColor = Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
		card.setBackground(bgColor);

		// Card header with checkbox
		createCardHeader(card);

		// Card features
		addBullet(card, DevAssistConstants.WELCOME_ASSIST_FEATURE_1);
		addBullet(card, DevAssistConstants.WELCOME_ASSIST_FEATURE_2);
		addBullet(card, DevAssistConstants.WELCOME_ASSIST_FEATURE_3);

		if (mcpEnabled) {
			addBullet(card, DevAssistConstants.WELCOME_MCP_INSTALLED_INFO);
		}
	}

	private void createCardHeader(Composite parent) {
		Composite header = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginLeft = 0;
		layout.marginRight = 0;
		layout.horizontalSpacing = 6;
		header.setLayout(layout);
		header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		header.setBackground(parent.getBackground());

		realTimeScannersCheckbox = new Button(header, SWT.CHECK);
		realTimeScannersCheckbox.setEnabled(mcpEnabled);
		realTimeScannersCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		Label titleLabel = new Label(header, SWT.NONE);
		titleLabel.setText(DevAssistConstants.WELCOME_ASSIST_TITLE);
		titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		FontData fontData = titleLabel.getFont().getFontData()[0];
		fontData.setStyle(SWT.BOLD);
		Font boldFont = new Font(Display.getCurrent(), fontData);
		titleLabel.setFont(boldFont);
		titleLabel.setBackground(parent.getBackground());
		titleLabel.addDisposeListener(e -> boldFont.dispose());

		header.setBackground(parent.getBackground());

		// Configure checkbox behavior
		configureCheckboxBehavior();
		refreshCheckboxState();
	}

	private void addBullet(Composite parent, String text) {
		Composite bulletPanel = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginLeft = BULLET_INDENT;
		layout.marginRight = 0;
		layout.marginTop = 0;
		layout.marginBottom = 0;
		layout.horizontalSpacing = 6;
		bulletPanel.setLayout(layout);
		bulletPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		// Bullet point
		Label bulletLabel = new Label(bulletPanel, SWT.NONE);
		bulletLabel.setText("•");
		bulletLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));

		FontData fontData = bulletLabel.getFont().getFontData()[0];
		fontData.setStyle(SWT.BOLD);
		Font boldFont = new Font(Display.getCurrent(), fontData);
		bulletLabel.setFont(boldFont);
		bulletLabel.addDisposeListener(e -> boldFont.dispose());

		// Text with wrapping
		Label textLabel = new Label(bulletPanel, SWT.WRAP);
		textLabel.setText(text);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		gd.widthHint = WRAP_WIDTH;
		textLabel.setLayoutData(gd);
	}

	private void configureCheckboxBehavior() {
		if (realTimeScannersCheckbox == null) return;

		realTimeScannersCheckbox.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
			@Override
			public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
				boolean anyCurrentlyEnabled = settingsManager.areAnyEnabled();
				settingsManager.setAll(!anyCurrentlyEnabled);
				refreshCheckboxState();
			}
		});
	}

	private void refreshCheckboxState() {
		if (realTimeScannersCheckbox == null) return;

		boolean anyEnabled = settingsManager.areAnyEnabled();
		realTimeScannersCheckbox.setSelection(anyEnabled);
		updateCheckboxTooltip();
	}

	private void updateCheckboxTooltip() {
		if (realTimeScannersCheckbox == null) return;

		if (!mcpEnabled) {
			realTimeScannersCheckbox.setToolTipText("Checkmarx MCP is not enabled for this tenant.");
			return;
		}

		boolean allEnabled = settingsManager.areAllEnabled();
		boolean anyEnabled = settingsManager.areAnyEnabled();

		String tooltipText;
		if (allEnabled) {
			tooltipText = "Disable all real-time scanners";
		} else if (anyEnabled) {
			tooltipText = "Some scanners are enabled. Click to enable all real-time scanners";
		} else {
			tooltipText = "Enable all real-time scanners";
		}
		realTimeScannersCheckbox.setToolTipText(tooltipText);
	}

	@Override
	protected void okPressed() {
		super.okPressed();
	}

	@Override
	protected Point getInitialSize() {
		return new Point(DIALOG_WIDTH, DIALOG_HEIGHT);
	}

	/**
	 * Get the real-time scanners checkbox (for testing purposes)
	 */
	public Button getRealTimeScannersCheckbox() {
		return realTimeScannersCheckbox;
	}

	/**
	 * Manager interface for real-time settings
	 */
	public interface RealTimeSettingsManager {
		boolean areAllEnabled();
		boolean areAnyEnabled();
		void setAll(boolean enable);
	}

	/**
	 * Default implementation using ScannerStateManager for persistence
	 */
	private static class DefaultRealTimeSettingsManager implements RealTimeSettingsManager {
		private final com.checkmarx.eclipse.devassist.state.ScannerStateManager stateManager;
		private com.checkmarx.eclipse.devassist.state.ScannerState currentState;

		DefaultRealTimeSettingsManager() {
			this.stateManager = new com.checkmarx.eclipse.devassist.state.ScannerStateManager();
			this.currentState = stateManager.loadState();
		}

		@Override
		public boolean areAllEnabled() {
			for (com.checkmarx.eclipse.devassist.model.ScanEngine engine : com.checkmarx.eclipse.devassist.model.ScanEngine.values()) {
				if (!currentState.isEnabled(engine)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public boolean areAnyEnabled() {
			for (com.checkmarx.eclipse.devassist.model.ScanEngine engine : com.checkmarx.eclipse.devassist.model.ScanEngine.values()) {
				if (currentState.isEnabled(engine)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public void setAll(boolean enable) {
			for (com.checkmarx.eclipse.devassist.model.ScanEngine engine : com.checkmarx.eclipse.devassist.model.ScanEngine.values()) {
				currentState.setEnabled(engine, enable);
			}
			stateManager.saveState(currentState);
		}
	}
}
