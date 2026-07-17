package com.checkmarx.eclipse.views.ui;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.checkmarx.eclipse.utils.PluginConstants;

/**
 * Welcome dialog for Checkmarx Eclipse plugin.
 * Displayed after successful authentication to inform users about key features.
 */
public class WelcomeDialog extends TitleAreaDialog {

	private static final int DIALOG_WIDTH = 720;
	private static final int DIALOG_HEIGHT = 500;
	private static final int WRAP_WIDTH = 250;
	private static final int BULLET_INDENT = 20;
	private static final int CONTENT_MARGIN = 20;

	private final boolean mcpEnabled;
	private Button realTimeScannersCheckbox;
	private final RealTimeSettingsManager settingsManager;

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
		setShellStyle(getShellStyle() | SWT.RESIZE);

		// Log MCP status for debugging
		String mcpStatus = mcpEnabled ? "ENABLED" : "DISABLED";
		com.checkmarx.eclipse.utils.CxLogger.info("[WELCOME] MCP status: " + mcpStatus);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Checkmarx");
		shell.setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle(PluginConstants.WELCOME_TITLE);
		setMessage(PluginConstants.WELCOME_SUBTITLE);
		setTitleImage(null); // Remove title image for cleaner look

		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new GridLayout(1, false));

		// Create left panel with scrolling
		createLeftPanel(container);

		return container;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, OK, PluginConstants.WELCOME_CLOSE_BUTTON, true);
	}

	private void createLeftPanel(Composite container) {

	    ScrolledComposite scrolled = new ScrolledComposite(container, SWT.V_SCROLL);
	    scrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
	    scrolled.setExpandHorizontal(true);
	    scrolled.setExpandVertical(true);

	    // Left panel (main content)
	    Composite leftPanel = new Composite(scrolled, SWT.NONE);
	    GridLayout layout = new GridLayout(1, false);
	    layout.marginLeft = CONTENT_MARGIN;
	    layout.marginRight = CONTENT_MARGIN;
	    layout.marginTop = CONTENT_MARGIN;
	    layout.marginBottom = CONTENT_MARGIN;
	    layout.verticalSpacing = 5;
	    leftPanel.setLayout(layout);

	    // ---------- Top Section ----------
	    Composite topSection = new Composite(leftPanel, SWT.NONE);
	    GridLayout topLayout = new GridLayout(2, false);
	    topLayout.marginWidth = 0;
	    topLayout.marginHeight = 0;
	    topLayout.horizontalSpacing = 20;
	    topSection.setLayout(topLayout);
	    topSection.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

	    // Left side - Feature Card
	    if (mcpEnabled) {
	        addFeatureCard(topSection);
	    }

	    // Right side - Image
	    createScannerImage(topSection);

	    // ---------- Main Features ----------
	    addBullet(leftPanel, PluginConstants.WELCOME_MAIN_FEATURE_1);
	    addBullet(leftPanel, PluginConstants.WELCOME_MAIN_FEATURE_2);
	    addBullet(leftPanel, PluginConstants.WELCOME_MAIN_FEATURE_3);
	    addBullet(leftPanel, PluginConstants.WELCOME_MAIN_FEATURE_4);

	    scrolled.setContent(leftPanel);
	    scrolled.setMinSize(leftPanel.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}

	private void createScannerImage(Composite container) {
		Composite rightPanel = new Composite(container, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginLeft = CONTENT_MARGIN;
		layout.marginRight = CONTENT_MARGIN;
		layout.marginTop = CONTENT_MARGIN;
		layout.marginBottom = CONTENT_MARGIN;
		rightPanel.setLayout(layout);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = 350;
		rightPanel.setLayoutData(gd);

		// Placeholder for future image/icon
		Label imageLabel = new Label(rightPanel, SWT.CENTER);
		imageLabel.setText("Checkmarx Scanner");
		imageLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));

		FontData fontData = imageLabel.getFont().getFontData()[0];
		fontData.setHeight(14);
		Font boldFont = new Font(Display.getCurrent(), fontData);
		imageLabel.setFont(boldFont);
		imageLabel.addDisposeListener(e -> boldFont.dispose());
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
		addBullet(card, PluginConstants.WELCOME_ASSIST_FEATURE_1);
		addBullet(card, PluginConstants.WELCOME_ASSIST_FEATURE_2);
		addBullet(card, PluginConstants.WELCOME_ASSIST_FEATURE_3);

		if (mcpEnabled) {
			addBullet(card, PluginConstants.WELCOME_MCP_INSTALLED_INFO);
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
		titleLabel.setText(PluginConstants.WELCOME_ASSIST_TITLE);
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
	 * Default implementation - can be extended when MCP settings are available
	 */
	private static class DefaultRealTimeSettingsManager implements RealTimeSettingsManager {
		@Override
		public boolean areAllEnabled() {
			// Future: Check all real-time scanner settings
			return false;
		}

		@Override
		public boolean areAnyEnabled() {
			// Future: Check if any real-time scanner is enabled
			return false;
		}

		@Override
		public void setAll(boolean enable) {
			// Future: Set all real-time scanners
		}
	}
}
