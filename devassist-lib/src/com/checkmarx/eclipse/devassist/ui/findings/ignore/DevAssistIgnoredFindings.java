package com.checkmarx.eclipse.devassist.ui.findings.ignore;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.checkmarx.eclipse.common.events.SettingsTopics;
import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.backend.Constants;
import com.checkmarx.eclipse.devassist.ignore.IgnoreEntry;
import com.checkmarx.eclipse.devassist.ignore.IgnoreFileManager;
import com.checkmarx.eclipse.devassist.ignore.IgnoreManager;
import com.checkmarx.eclipse.devassist.ui.findings.actions.VulnerabilityFilterAction;
import com.checkmarx.eclipse.devassist.ui.findings.actions.VulnerabilityFilterState;
import com.checkmarx.eclipse.devassist.ui.findings.icons.IconRegistry;
import com.checkmarx.eclipse.devassist.utils.DateFormatUtil;

/**
 * Tool window panel for viewing ignored vulnerability findings. Features strict
 * column alignment, rounded badge styling, dynamic dates, and centered empty
 * state.
 */
public class DevAssistIgnoredFindings extends ViewPart {

	public static final String ID = "com.checkmarx.eclipse.devassist.ui.findings.ignore.DevAssistIgnoredFindings";

	/**
	 * Refreshes this view's tab title/entry list right away if it's currently
	 * open, instead of relying solely on {@link IgnoreFileManager}'s listener
	 * notification. A brand-new project's {@code IgnoreFileManager} instance is
	 * created lazily by whichever caller (this view or the ignore action
	 * itself) resolves it first - if the ignore action gets there first, this
	 * view's listener isn't attached to it yet at that moment, so its very
	 * first "onIgnoreUpdated" notification would otherwise be missed. Ignore
	 * call sites (context-menu "Ignore"/"Ignore All", hover quick-fix links)
	 * call this directly, mirroring the existing CxFindingsView refresh-on-ignore
	 * pattern, so the tab count updates on the very first ignore too - not just
	 * from the second one onward once the listener happens to already be wired
	 * up.
	 */
	public static void refreshIfOpen() {
		try {
			org.eclipse.ui.IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			if (window == null) {
				return;
			}
			org.eclipse.ui.IWorkbenchPage page = window.getActivePage();
			if (page == null) {
				return;
			}
			org.eclipse.ui.IViewPart view = page.findView(ID);
			if (view instanceof DevAssistIgnoredFindings) {
				((DevAssistIgnoredFindings) view).refreshTable();
			}
		} catch (Exception e) {
			CxLogger.warning("[IGNORED-FINDINGS] Error refreshing view after ignore action: " + e.getMessage());
		}
	}

	private Composite parentComposite;
	private Composite container;
	private Composite openSettingsComposite;
	private Shell shell;
	private static final Image CHECKMARX_OPEN_SETTINGS_LOGO = AbstractUIPlugin
			.imageDescriptorFromPlugin(Constants.MAIN_PLUGIN_ID, "/icons/checkmarx-80.png").createImage();
	private org.osgi.service.event.EventHandler settingsEventHandler;

	// Top selection action bar
	private Composite selectionActionBar;
	private Label selectionCountLabel;
	private Button clearSelectionButton;
	private Button reviveSelectedButton;

	// Header row components
	private Composite headerComposite;
	private Button selectAllButton;
	private Label riskHeaderLabel;
	private Label lastUpdatedHeaderLabel;

	private ScrolledComposite scrolledContainer;
	private Composite cardsContainer;
	private Label emptyLabel;

	private IProject currentProject;
	private IgnoreFileManager ignoreFileManager;
	private final IgnoreFileManager.IgnoreListener ignoreListener = this::onIgnoreDataUpdated;
	// Reacts to project open/close so the view stops reading the now-closed
	// project's (stale/cached) IgnoreFileManager and switches to whatever
	// project is open now, instead of only re-resolving on the next incidental
	// refreshTable() call (e.g. setFocus()).
	private final IResourceChangeListener projectStateListener = this::handleWorkspaceResourceChange;

	// Independent from VulnerabilityFilterState.getInstance() (used by the main
	// Findings view) so toggling a severity here doesn't also filter that view.
	private final VulnerabilityFilterState filterState = new VulnerabilityFilterState();

	private List<IgnoreEntryCard> cards = new ArrayList<>();
	// Keyed by the ignore-file map key (stable identity for an entry) rather than
	// the IgnoreEntry instance itself - refreshFromDisk() re-deserializes the
	// ignore file into brand-new IgnoreEntry objects on every call (even a no-op
	// refresh triggered by setFocus() when the user switches back to this view),
	// so identity/equality-based tracking would silently drop the selection.
	private Set<String> selectedKeys = new HashSet<>();
	private boolean isProgrammaticSelectionChange = false;

	@Override
	public void createPartControl(Composite parent) {
		this.parentComposite = parent;
		this.shell = parent.getShell();

		GridLayout parentLayout = new GridLayout(1, true);
		parentLayout.marginWidth = 0;
		parentLayout.marginHeight = 0;
		parent.setLayout(parentLayout);

		subscribeToSettingsEvents();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(projectStateListener,
				IResourceChangeEvent.POST_CHANGE);

		ensureProjectAndIgnoreManager();

		refreshViewMode();
	}

	/**
	 * Detects project open/close transitions and refreshes the view so it stops
	 * showing data read from a project that just closed. Runs on the workspace
	 * notification thread, so the actual refresh is marshalled back to the UI
	 * thread.
	 */
	private void handleWorkspaceResourceChange(IResourceChangeEvent event) {
		IResourceDelta delta = event.getDelta();
		if (delta == null) {
			return;
		}
		boolean[] projectOpenStateChanged = { false };
		try {
			delta.accept(d -> {
				if (d.getResource() instanceof IProject && (d.getFlags() & IResourceDelta.OPEN) != 0) {
					projectOpenStateChanged[0] = true;
				}
				return true;
			});
		} catch (CoreException e) {
			CxLogger.warning("[IGNORED-FINDINGS] Error inspecting resource delta: " + e.getMessage());
		}
		if (!projectOpenStateChanged[0]) {
			return;
		}
		// Called on the workspace notification thread - don't touch any SWT widget
		// (including container.getDisplay()) until inside asyncExec.
		Display display = Display.getDefault();
		if (display == null || display.isDisposed()) {
			return;
		}
		display.asyncExec(() -> {
			if (container != null && !container.isDisposed()) {
				refreshTable();
			}
		});
	}

	/**
	 * Determines which panel to draw based on current credentials status,
	 * mirroring {@code CxFindingsView#refreshViewMode} - so the Ignored Findings
	 * window also gates on authentication instead of always showing the ignored
	 * entries list (or an empty state) while logged out.
	 */
	private void refreshViewMode() {
		if (parentComposite == null || parentComposite.isDisposed()) {
			return;
		}
		if (!Preferences.isAuthenticated()) {
			drawMissingCredentialsPanel(parentComposite);
		} else {
			drawIgnoredFindingsPanel(parentComposite);
		}
	}

	/**
	 * Subscribes to the settings-applied event topic so logging in/out toggles
	 * between the missing-credentials panel and the ignored-findings list live,
	 * without requiring the view to be closed and reopened.
	 */
	private void subscribeToSettingsEvents() {
		try {
			org.eclipse.e4.core.services.events.IEventBroker eventBroker = getSite()
					.getService(org.eclipse.e4.core.services.events.IEventBroker.class);
			if (eventBroker == null) {
				eventBroker = PlatformUI.getWorkbench()
						.getService(org.eclipse.e4.core.services.events.IEventBroker.class);
			}
			if (eventBroker != null) {
				settingsEventHandler = event -> Display.getDefault().asyncExec(this::refreshViewMode);
				eventBroker.subscribe(SettingsTopics.TOPIC_APPLY_SETTINGS, settingsEventHandler);
			}
		} catch (Exception e) {
			System.err.println("[IGNORED-FINDINGS] Error subscribing to IEventBroker: " + e.getMessage());
		}
	}

	/**
	 * Renders the missing credentials panel, matching {@code CxFindingsView}'s
	 * look (logo + Open Settings button) so both views behave consistently while
	 * logged out.
	 */
	private void drawMissingCredentialsPanel(Composite parent) {
		for (Control child : parent.getChildren()) {
			child.dispose();
		}
		clearToolbarContributions();

		openSettingsComposite = new Composite(parent, SWT.NONE);
		openSettingsComposite.setLayout(new GridLayout(1, true));
		openSettingsComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));

		Label cxLogo = new Label(openSettingsComposite, SWT.NONE);
		cxLogo.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
		cxLogo.setImage(CHECKMARX_OPEN_SETTINGS_LOGO);

		Button btn = new Button(openSettingsComposite, SWT.NONE);
		btn.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
		btn.setText(Constants.BTN_OPEN_SETTINGS);
		btn.addListener(SWT.Selection, event -> {
			PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(
					shell, "com.checkmarx.eclipse.properties.preferencespage", null, null);
			if (pref != null) {
				pref.open();
			}
		});

		setPartName("Ignored Findings");
		parent.layout(true, true);
	}

	/**
	 * Removes every contribution from the view toolbar - used while the missing
	 * credentials panel is showing, since the severity filter actions operate on
	 * an ignored-entries list that isn't rendered in that state.
	 */
	private void clearToolbarContributions() {
		IToolBarManager toolbar = getViewSite().getActionBars().getToolBarManager();
		toolbar.removeAll();
		toolbar.update(true);
		getViewSite().getActionBars().updateActionBars();
	}

	private void drawIgnoredFindingsPanel(Composite parent) {
		// Clear out the missing-credentials panel if it was showing
		for (Control child : parent.getChildren()) {
			child.dispose();
		}

		container = new Composite(parent, SWT.NONE);
		container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		GridLayout containerLayout = new GridLayout(1, false);
		containerLayout.marginWidth = 12;
		containerLayout.marginHeight = 10;
		container.setLayout(containerLayout);

		// -----------------------------------------------------------------
		// 1. TOP SELECTION ACTION BAR (Visible only when items are selected)
		// -----------------------------------------------------------------
		selectionActionBar = new Composite(container, SWT.NONE);
		GridLayout actionBarLayout = new GridLayout(3, false);
		actionBarLayout.marginWidth = 0;
		actionBarLayout.marginHeight = 0;
		selectionActionBar.setLayout(actionBarLayout);

		GridData actionBarData = new GridData(SWT.FILL, SWT.TOP, true, false);
		actionBarData.exclude = true;
		selectionActionBar.setLayoutData(actionBarData);
		selectionActionBar.setVisible(false);

		selectionCountLabel = new Label(selectionActionBar, SWT.NONE);
		selectionCountLabel.setText("0 Risk selected  |");
		selectionCountLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		clearSelectionButton = new Button(selectionActionBar, SWT.PUSH | SWT.FLAT);
		clearSelectionButton.setText("✕ Clear Selections");
		clearSelectionButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		clearSelectionButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				clearAllSelections();
			}
		});

		reviveSelectedButton = new Button(selectionActionBar, SWT.PUSH);
		reviveSelectedButton.setText("« Revive Selected");
		reviveSelectedButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		reviveSelectedButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				reviveSelected();
			}
		});

		// -----------------------------------------------------------------
		// 2. COLUMN HEADERS ROW (Strict Grid Alignment)
		// -----------------------------------------------------------------
		headerComposite = new Composite(container, SWT.NONE);
		GridLayout headerLayout = new GridLayout(4, false);
		headerLayout.marginWidth = 0;
		headerLayout.marginHeight = 4;
		headerLayout.horizontalSpacing = 16;
		headerComposite.setLayout(headerLayout);
		headerComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		// Col 1 Header: Checkbox
		selectAllButton = new Button(headerComposite, SWT.CHECK);
		GridData col1HeaderData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		selectAllButton.setLayoutData(col1HeaderData);
		selectAllButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				onSelectAllToggled(selectAllButton.getSelection());
			}
		});

		// Col 2 Header: Risk
		riskHeaderLabel = new Label(headerComposite, SWT.NONE);
		riskHeaderLabel.setText("Risk");
		riskHeaderLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
		GridData col2HeaderData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		riskHeaderLabel.setLayoutData(col2HeaderData);

		// Col 3 Header: Last Updated
		lastUpdatedHeaderLabel = new Label(headerComposite, SWT.NONE);
		lastUpdatedHeaderLabel.setText("Last Updated");
		lastUpdatedHeaderLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
		GridData col3HeaderData = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
		col3HeaderData.widthHint = 110;
		lastUpdatedHeaderLabel.setLayoutData(col3HeaderData);

		// Col 4 Header: Action Spacer
		Label reviveHeaderPlaceholder = new Label(headerComposite, SWT.NONE);
		GridData col4HeaderData = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
		col4HeaderData.widthHint = 95;
		reviveHeaderPlaceholder.setLayoutData(col4HeaderData);

		// -----------------------------------------------------------------
		// 3. EMPTY STATE & SCROLLED CARDS CONTAINER
		// -----------------------------------------------------------------
		emptyLabel = new Label(container, SWT.CENTER | SWT.WRAP);
		emptyLabel.setText("No ignored Findings");
		emptyLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
		GridData emptyData = new GridData(SWT.CENTER, SWT.CENTER, true, true);
		emptyLabel.setLayoutData(emptyData);

		scrolledContainer = new ScrolledComposite(container, SWT.V_SCROLL | SWT.H_SCROLL);
		scrolledContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scrolledContainer.setExpandHorizontal(true);
		scrolledContainer.setExpandVertical(true);

		cardsContainer = new Composite(scrolledContainer, SWT.NONE);
		GridLayout cardLayout = new GridLayout(1, true);
		cardLayout.marginWidth = 0;
		cardLayout.marginHeight = 0;
		cardLayout.verticalSpacing = 16;
		cardsContainer.setLayout(cardLayout);

		scrolledContainer.setContent(cardsContainer);

		// Recompute the scroll area's min height whenever the view is resized -
		// without this, a width change after the cards were last measured leaves
		// the wrap-label/badge-row heights (measured at the OLD width) stale, so
		// the scrollbar can under- or over-shoot the real content height again.
		scrolledContainer.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				updateScrolledMinHeight();
			}
		});

		setupToolbar();
		refreshTable();

		parent.layout(true, true);
	}

	/**
	 * Adds severity toggle-filter buttons to the view's toolbar, mirroring
	 * CxFindingsView's filter UX so ignored entries can be narrowed down by
	 * severity the same way active findings can.
	 */
	private void setupToolbar() {
		IToolBarManager toolbar = getViewSite().getActionBars().getToolBarManager();
		toolbar.removeAll();

		VulnerabilityFilterAction.IFilterChangeListener filterListener = this::refreshTable;

		toolbar.add(new VulnerabilityFilterAction.MaliciousFilter(filterListener, filterState));
		toolbar.add(new VulnerabilityFilterAction.CriticalFilter(filterListener, filterState));
		toolbar.add(new VulnerabilityFilterAction.HighFilter(filterListener, filterState));
		toolbar.add(new VulnerabilityFilterAction.MediumFilter(filterListener, filterState));
		toolbar.add(new VulnerabilityFilterAction.LowFilter(filterListener, filterState));

		toolbar.update(true);
		getViewSite().getActionBars().updateActionBars();
	}

	private static int activeFileCount(IgnoreEntry entry) {
		if (entry.getFiles() == null) {
			return 0;
		}
		return (int) entry.getFiles().stream().filter(IgnoreEntry.FileReference::isActive).count();
	}

	private void ensureProjectAndIgnoreManager() {
		if (currentProject != null && !currentProject.isOpen()) {
			// The project we were reading from just closed - drop the stale
			// reference (and its listener) instead of continuing to read whatever
			// IgnoreFileManager instance was cached for it, so the view re-resolves
			// against whatever project is actually open now.
			detachIgnoreFileManager();
			currentProject = null;
		}

		if (currentProject == null) {
			for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
				if (project.isOpen()) {
					currentProject = project;
					break;
				}
			}
		}

		if (currentProject != null && ignoreFileManager == null) {
			ignoreFileManager = IgnoreFileManager.getInstance(currentProject);
			ignoreFileManager.addListener(ignoreListener);
		}
	}

	private void detachIgnoreFileManager() {
		if (ignoreFileManager != null) {
			ignoreFileManager.removeListener(ignoreListener);
			ignoreFileManager = null;
		}
	}

	public void refreshTable() {
		ensureProjectAndIgnoreManager();

		if (container == null || container.isDisposed()) {
			return;
		}

		List<Map.Entry<String, IgnoreEntry>> entries;
		if (ignoreFileManager == null) {
			// No open project to read a .checkmarxIgnored file from (e.g. the
			// project that was showing here just closed) - render the empty state
			// instead of leaving whatever was last drawn for the closed project on
			// screen.
			entries = java.util.Collections.emptyList();
		} else {
			ignoreFileManager.refreshFromDisk();
			entries = ignoreFileManager.getIgnoreData().entrySet().stream()
					.filter(e -> activeFileCount(e.getValue()) > 0)
					.filter(e -> e.getValue().getSeverity() == null || filterState.hasFilter(e.getValue().getSeverity()))
					.collect(Collectors.toList());
		}

		// Drop selections for keys that no longer correspond to a currently
		// displayed entry (e.g. revived/removed elsewhere) - everything else
		// carries over across this refresh, including refreshes triggered by
		// setFocus() when the user returns to this view from elsewhere.
		Set<String> currentKeys = entries.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
		selectedKeys.retainAll(currentKeys);

		reconstructCards(entries);

		boolean hasEntries = !entries.isEmpty();
		int entryCount = entries.size();

		// Update tab title with count
		if (hasEntries) {
			setPartName("Ignored Findings " + entryCount);
		} else {
			setPartName("Ignored Findings");
		}

		emptyLabel.setVisible(!hasEntries);
		((GridData) emptyLabel.getLayoutData()).exclude = hasEntries;

		scrolledContainer.setVisible(hasEntries);
		((GridData) scrolledContainer.getLayoutData()).exclude = !hasEntries;

		headerComposite.setVisible(hasEntries);
		((GridData) headerComposite.getLayoutData()).exclude = !hasEntries;

		if (!hasEntries) {
			selectAllButton.setSelection(false);
			selectAllButton.setEnabled(false);
		} else {
			selectAllButton.setEnabled(true);
			// Reflect a carried-over "all selected" state in the header checkbox too,
			// e.g. after a refresh triggered by returning to this view.
			selectAllButton.setSelection(selectedKeys.size() == entryCount);
		}

		container.layout(true, true);
	}

	private void reconstructCards(List<Map.Entry<String, IgnoreEntry>> entries) {
		for (IgnoreEntryCard card : cards) {
			card.dispose();
		}
		cards.clear();

		for (Control child : cardsContainer.getChildren()) {
			child.dispose();
		}

		for (Map.Entry<String, IgnoreEntry> entry : entries) {
			IgnoreEntryCard card = new IgnoreEntryCard(cardsContainer, entry.getKey(), entry.getValue(), this,
					selectedKeys.contains(entry.getKey()));
			cards.add(card);
		}

		cardsContainer.layout(true, true);
		updateScrolledMinHeight();
		updateSelectionStateUI();
	}

	/**
	 * Recomputes the scroll area's min height after a card's content changes
	 * size (e.g. expanding/collapsing the "N more files" link), so the
	 * ScrolledComposite's scrollbar stays in sync with the actual content height.
	 */
	public void relayoutCards() {
		if (cardsContainer == null || cardsContainer.isDisposed()) {
			return;
		}
		cardsContainer.layout(true, true);
		updateScrolledMinHeight();
	}

	/**
	 * Measures cardsContainer's required height at its ACTUAL rendered width,
	 * not an unconstrained default width. computeSize(SWT.DEFAULT, SWT.DEFAULT)
	 * measures wrap-labels (the description text) and the badge-row GridLayout
	 * at their preferred, unconstrained width, which is wider than the width
	 * they're actually confined to inside the scrollable area - at that wider
	 * "preferred" width the same text wraps LESS, under-reporting the true
	 * height needed. setMinHeight() then reserved less scroll space than the
	 * content actually needs, clipping whatever renders last (the final card's
	 * badge row) once scrolled to the bottom.
	 */
	private void updateScrolledMinHeight() {
		if (scrolledContainer == null || scrolledContainer.isDisposed()
				|| cardsContainer == null || cardsContainer.isDisposed()) {
			return;
		}
		int width = scrolledContainer.getClientArea().width;
		Point size = width > 0
				? cardsContainer.computeSize(width, SWT.DEFAULT)
				: cardsContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		scrolledContainer.setMinHeight(size.y);
	}

	private void onSelectAllToggled(boolean selectAll) {
		isProgrammaticSelectionChange = true;
		try {
			selectedKeys.clear();
			for (IgnoreEntryCard card : cards) {
				card.setSelected(selectAll);
				if (selectAll) {
					selectedKeys.add(card.getKey());
				}
			}
			updateSelectionStateUI();
		} finally {
			isProgrammaticSelectionChange = false;
		}
	}

	public void onCardSelectionChanged(String key, boolean selected) {
		if (isProgrammaticSelectionChange) {
			return;
		}

		if (selected) {
			selectedKeys.add(key);
		} else {
			selectedKeys.remove(key);
		}

		if (selectedKeys.size() == cards.size() && !cards.isEmpty()) {
			selectAllButton.setSelection(true);
		} else {
			selectAllButton.setSelection(false);
		}

		updateSelectionStateUI();
	}

	private void clearAllSelections() {
		onSelectAllToggled(false);
		selectAllButton.setSelection(false);
	}

	private void updateSelectionStateUI() {
		boolean hasSelection = !selectedKeys.isEmpty();

		selectionActionBar.setVisible(hasSelection);
		((GridData) selectionActionBar.getLayoutData()).exclude = !hasSelection;

		if (hasSelection) {
			int count = selectedKeys.size();
			selectionCountLabel.setText(count + (count == 1 ? " Risk selected  |" : " Risks selected  |"));
		}

		container.layout(true, true);
	}

	public void onCardRevive(IgnoreEntry entry) {
		ensureProjectAndIgnoreManager();
		if (currentProject == null) {
			return;
		}
		IgnoreManager.getInstance(currentProject).reviveSingleEntry(entry);
		refreshTable();
	}

	/**
	 * Navigates to the file (and line, if known) referenced by a file badge on an
	 * ignore entry card, mirroring the file-badge navigation already implemented
	 * in the JetBrains plugin's DevAssistIgnoredFindings.
	 */
	public void navigateToFile(IgnoreEntry.FileReference file) {
		ensureProjectAndIgnoreManager();
		if (file == null || file.getPath() == null || currentProject == null) {
			return;
		}

		try {
			java.nio.file.Path absolutePath = Paths.get(currentProject.getLocation().toOSString(), file.getPath());
			IFile ifile = ResourcesPlugin.getWorkspace().getRoot()
					.getFileForLocation(new org.eclipse.core.runtime.Path(absolutePath.toString()));

			if (ifile == null || !ifile.exists()) {
				showErrorMessage("Could not find file: " + file.getPath());
				return;
			}

			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			IEditorPart editor = IDE.openEditor(page, ifile);

			Integer line = file.getLine();
			if (line != null && line > 0) {
				scrollToLine(editor, line);
			}
		} catch (Exception e) {
			showErrorMessage("Failed to open file: " + e.getMessage());
		}
	}

	private boolean scrollToLine(IEditorPart editor, int lineNumber) {
		if (editor == null || lineNumber <= 0) {
			return false;
		}
		try {
			org.eclipse.ui.texteditor.ITextEditor textEditor = editor
					.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class);
			if (textEditor == null && editor instanceof org.eclipse.ui.texteditor.ITextEditor) {
				textEditor = (org.eclipse.ui.texteditor.ITextEditor) editor;
			}

			if (textEditor != null) {
				org.eclipse.ui.texteditor.IDocumentProvider provider = textEditor.getDocumentProvider();
				if (provider != null) {
					org.eclipse.jface.text.IDocument document = provider.getDocument(textEditor.getEditorInput());
					if (document != null && lineNumber <= document.getNumberOfLines()) {
						int lineOffset = document.getLineOffset(lineNumber - 1);
						textEditor.selectAndReveal(lineOffset, 0);
						return true;
					}
				}
			}
		} catch (Exception e) {
			CxLogger.error("Failed to scroll to line " + lineNumber, e);
		}
		return false;
	}

	private void showErrorMessage(String message) {
		if (shell == null || shell.isDisposed()) {
			return;
		}
		MessageBox msgBox = new MessageBox(shell, SWT.ICON_ERROR);
		msgBox.setMessage(message);
		msgBox.setText("Checkmarx AI Assist");
		msgBox.open();
	}

	private void reviveSelected() {
		ensureProjectAndIgnoreManager();
		if (currentProject == null || selectedKeys.isEmpty()) {
			return;
		}

		// Resolve the currently-selected keys against the live ignore data at the
		// moment of the action, rather than reviving whatever IgnoreEntry
		// instances were cached at selection time - those can go stale (e.g. the
		// ignore file changing on disk between selecting and clicking Revive).
		Map<String, IgnoreEntry> currentData = ignoreFileManager.getIgnoreData();
		List<IgnoreEntry> entriesToRevive = selectedKeys.stream().map(currentData::get).filter(Objects::nonNull)
				.collect(Collectors.toList());
		selectedKeys.clear();
		if (entriesToRevive.isEmpty()) {
			return;
		}
		IgnoreManager.getInstance(currentProject).reviveMultipleEntries(entriesToRevive);
		refreshTable();
	}

	private void onIgnoreDataUpdated() {
		Display display = container != null && !container.isDisposed() ? container.getDisplay() : Display.getDefault();
		display.asyncExec(() -> {
			if (container != null && !container.isDisposed()) {
				refreshTable();
			}
		});
	}

	@Override
	public void setFocus() {
		refreshTable();
		if (cardsContainer != null && !cardsContainer.isDisposed()) {
			cardsContainer.setFocus();
		}
	}

	@Override
	public void dispose() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(projectStateListener);
		if (settingsEventHandler != null) {
			try {
				org.eclipse.e4.core.services.events.IEventBroker eventBroker = PlatformUI.getWorkbench()
						.getService(org.eclipse.e4.core.services.events.IEventBroker.class);
				if (eventBroker != null) {
					eventBroker.unsubscribe(settingsEventHandler);
				}
			} catch (Exception e) {
				System.err.println("[IGNORED-FINDINGS] Error unsubscribing from IEventBroker: " + e.getMessage());
			}
		}
		if (ignoreFileManager != null) {
			ignoreFileManager.removeListener(ignoreListener);
		}
		for (IgnoreEntryCard card : cards) {
			card.dispose();
		}
		if (container != null && !container.isDisposed()) {
			container.dispose();
		}
		super.dispose();
	}

	/**
	 * Inner class representing a single row card with strict column alignment.
	 */
	private static class IgnoreEntryCard {
		private final Composite cardComposite;
		private final Button checkboxButton;
		private final String key;
		private final IgnoreEntry entry;
		private final Font boldFont;
		private boolean isSelected;

		public IgnoreEntryCard(Composite parent, String key, IgnoreEntry entry, DevAssistIgnoredFindings parentView,
				boolean initiallySelected) {
			this.key = key;
			this.entry = entry;
			this.isSelected = initiallySelected;

			// Row Container
			cardComposite = new Composite(parent, SWT.NONE);
			cardComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

			GridLayout layout = new GridLayout(4, false);
			layout.marginWidth = 0;
			layout.marginHeight = 4;
			layout.horizontalSpacing = 16;
			cardComposite.setLayout(layout);

			// Column 1: Checkbox
			checkboxButton = new Button(cardComposite, SWT.CHECK);
			GridData col1Data = new GridData(SWT.LEFT, SWT.TOP, false, false);
			checkboxButton.setLayoutData(col1Data);
			checkboxButton.setSelection(initiallySelected);
			checkboxButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					isSelected = checkboxButton.getSelection();
					parentView.onCardSelectionChanged(key, isSelected);
				}
			});

			// Column 2: Content Container
			Composite contentComposite = new Composite(cardComposite, SWT.NONE);
			GridData col2Data = new GridData(SWT.FILL, SWT.FILL, true, false);
			contentComposite.setLayoutData(col2Data);

			GridLayout contentLayout = new GridLayout(1, false);
			contentLayout.marginWidth = 0;
			contentLayout.marginHeight = 0;
			contentLayout.verticalSpacing = 4;
			contentComposite.setLayout(contentLayout);

			// Title Line: Icons + Name
			Composite titleComposite = new Composite(contentComposite, SWT.NONE);
			titleComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
			GridLayout titleLayout = new GridLayout(3, false);
			titleLayout.marginWidth = 0;
			titleLayout.marginHeight = 0;
			titleLayout.horizontalSpacing = 6;
			titleComposite.setLayout(titleLayout);

			Image cardIcon = IconRegistry.getCardIcon(
					entry.getType() != null ? entry.getType().toString() : "VULNERABILITY",
					entry.getSeverity() != null ? entry.getSeverity() : "MEDIUM");
			if (cardIcon != null) {
				Label cardIconLabel = new Label(titleComposite, SWT.NONE);
				cardIconLabel.setImage(cardIcon);
			}

			Image severityIcon = IconRegistry.getThemeAwareIcon(
					entry.getSeverity() != null ? entry.getSeverity() : "MEDIUM", IconRegistry.Size.MEDIUM);
			if (severityIcon != null) {
				Label severityIconLabel = new Label(titleComposite, SWT.NONE);
				severityIconLabel.setImage(severityIcon);
			}

			Label nameLabel = new Label(titleComposite, SWT.NONE);
			nameLabel.setText(entry.getPackageName() != null ? entry.getPackageName() : "Unknown Risk");
			nameLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			this.boldFont = new Font(parent.getDisplay(), nameLabel.getFont().getFontData()[0].getName(),
					nameLabel.getFont().getFontData()[0].getHeight(), SWT.BOLD);
			nameLabel.setFont(boldFont);

			// Description Label
			Label descLabel = new Label(contentComposite, SWT.WRAP);
			String desc = entry.getDescription() != null && !entry.getDescription().isEmpty() ? entry.getDescription()
					: "Description not available";
			descLabel.setText(desc);
			descLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
			descLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

			// Tags Line with Configurable Rounded Badges
			if (entry.getFiles() != null && !entry.getFiles().isEmpty()) {
				List<IgnoreEntry.FileReference> activeFiles = entry.getFiles().stream()
						.filter(IgnoreEntry.FileReference::isActive).collect(Collectors.toList());

				Composite tagsComposite = new Composite(contentComposite, SWT.NONE);
				tagsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
				// +2 reserves columns for the type badge and the expand/collapse link, in
				// addition to one column per active file badge.
				GridLayout tagsLayout = new GridLayout(activeFiles.size() + 2, false);
				tagsLayout.marginWidth = 0;
				tagsLayout.marginHeight = 2;
				tagsLayout.horizontalSpacing = 6;
				tagsComposite.setLayout(tagsLayout);

				Color tagBorderColor = parent.getDisplay().getSystemColor(SWT.COLOR_GRAY);
				Color tagTextColor = parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);

				// Type Badge (Slight rounding: 4px)
				createFlatBadge(tagsComposite, entry.getType() != null ? entry.getType().toString() : "VULNERABILITY",
						tagBorderColor, tagTextColor, 4, null);

				// File Badges (Pill style rounding: 10px) - clickable, navigates to the
				// file (and line, if known), matching the JetBrains plugin's behavior.
				int visibleCount = Math.min(2, activeFiles.size());
				for (IgnoreEntry.FileReference file : activeFiles.subList(0, visibleCount)) {
					createFileBadge(tagsComposite, file, parentView, tagBorderColor, tagTextColor);
				}

				// "N more" / "see less" expand-collapse link for remaining files, matching
				// the JetBrains plugin's behavior.
				if (activeFiles.size() > visibleCount) {
					List<IgnoreEntry.FileReference> hidden = activeFiles.subList(visibleCount, activeFiles.size());
					List<Control> expandedControls = new ArrayList<>();

					Link expandLink = new Link(tagsComposite, SWT.NONE);
					expandLink.setText("<a>+" + hidden.size() + (hidden.size() == 1 ? " more file</a>" : " more files</a>"));
					expandLink.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

					expandLink.addListener(SWT.Selection, e -> {
						((GridData) expandLink.getLayoutData()).exclude = true;
						expandLink.setVisible(false);

						for (IgnoreEntry.FileReference file : hidden) {
							expandedControls
									.add(createFileBadge(tagsComposite, file, parentView, tagBorderColor, tagTextColor));
						}

						Link collapseLink = new Link(tagsComposite, SWT.NONE);
						collapseLink.setText("<a>see less</a>");
						collapseLink.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
						collapseLink.addListener(SWT.Selection, e2 -> {
							for (Control c : expandedControls) {
								c.dispose();
							}
							expandedControls.clear();
							((GridData) expandLink.getLayoutData()).exclude = false;
							expandLink.setVisible(true);
							refreshTagsLayout(tagsComposite, parentView);
						});
						expandedControls.add(collapseLink);

						refreshTagsLayout(tagsComposite, parentView);
					});
				}
			}

			// Column 3: Dynamic Last Updated Date
			Label lastUpdatedLabel = new Label(cardComposite, SWT.NONE);
			String relativeDate = DateFormatUtil.formatRelativeDate(entry.getDateAdded());
			lastUpdatedLabel.setText(relativeDate);
			lastUpdatedLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
			GridData col3Data = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
			col3Data.widthHint = 110;
			lastUpdatedLabel.setLayoutData(col3Data);

			// Column 4: Single Revive Button
			Button reviveButton = new Button(cardComposite, SWT.PUSH);
			reviveButton.setText("« Revive");
			GridData col4Data = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
			col4Data.widthHint = 95;
			reviveButton.setLayoutData(col4Data);

			reviveButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					parentView.onCardRevive(entry);
				}
			});
		}

		public void setSelected(boolean selected) {
			this.isSelected = selected;
			if (checkboxButton != null && !checkboxButton.isDisposed()) {
				checkboxButton.setSelection(selected);
			}
		}

		public IgnoreEntry getEntry() {
			return entry;
		}

		public String getKey() {
			return key;
		}

		public void dispose() {
			if (boldFont != null && !boldFont.isDisposed()) {
				boldFont.dispose();
			}
			if (cardComposite != null && !cardComposite.isDisposed()) {
				cardComposite.dispose();
			}
		}
	}

	/**
	 * Creates a clickable file badge that navigates to the referenced file (and
	 * line, if known) when clicked.
	 */
	private static Composite createFileBadge(Composite parent, IgnoreEntry.FileReference file,
			DevAssistIgnoredFindings parentView, Color borderColor, Color textColor) {
		String fileName = file.getPath() != null ? Paths.get(file.getPath()).getFileName().toString() : "unknown";
		return createFlatBadge(parent, "📄 " + fileName, borderColor, textColor, 10,
				() -> parentView.navigateToFile(file));
	}

	/**
	 * Re-flows the tags row and its ancestors after an expand/collapse toggle,
	 * and recomputes the scroll area's min height so the ScrolledComposite
	 * accounts for the new card height.
	 */
	private static void refreshTagsLayout(Composite tagsComposite, DevAssistIgnoredFindings parentView) {
		for (Composite c = tagsComposite; c != null && !c.isDisposed(); c = c.getParent()) {
			c.layout(true, true);
		}
		parentView.relayoutCards();
	}

	private static Composite createFlatBadge(Composite parent, String text, Color borderColor, Color textColor,
			int cornerRadius, Runnable onClick) {
		Composite badgeContainer = new Composite(parent, SWT.NONE);
		GridLayout containerLayout = new GridLayout(1, false);
		containerLayout.marginWidth = 6;
		containerLayout.marginHeight = 2;
		badgeContainer.setLayout(containerLayout);

		Label label = new Label(badgeContainer, SWT.NONE);
		label.setText(text);
		label.setForeground(textColor);
		label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

		badgeContainer.addPaintListener(e -> {
			e.gc.setAntialias(SWT.ON);
			e.gc.setForeground(borderColor);

			int width = badgeContainer.getBounds().width - 1;
			int height = badgeContainer.getBounds().height - 1;

			e.gc.drawRoundRectangle(0, 0, width, height, cornerRadius, cornerRadius);
		});

		label.setBackground(badgeContainer.getBackground());

		if (onClick != null) {
			Cursor handCursor = parent.getDisplay().getSystemCursor(SWT.CURSOR_HAND);
			badgeContainer.setCursor(handCursor);
			label.setCursor(handCursor);

			MouseAdapter clickListener = new MouseAdapter() {
				@Override
				public void mouseUp(MouseEvent e) {
					onClick.run();
				}
			};
			badgeContainer.addMouseListener(clickListener);
			label.addMouseListener(clickListener);
		}

		return badgeContainer;
	}
}