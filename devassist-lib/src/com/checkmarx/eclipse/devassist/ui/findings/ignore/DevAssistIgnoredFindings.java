package com.checkmarx.eclipse.devassist.ui.findings.ignore;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.ViewPart;

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

	private Composite container;

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

	// Independent from VulnerabilityFilterState.getInstance() (used by the main
	// Findings view) so toggling a severity here doesn't also filter that view.
	private final VulnerabilityFilterState filterState = new VulnerabilityFilterState();

	private List<IgnoreEntryCard> cards = new ArrayList<>();
	private Set<IgnoreEntry> selectedEntries = new HashSet<>();
	private boolean isProgrammaticSelectionChange = false;

	@Override
	public void createPartControl(Composite parent) {
		container = new Composite(parent, SWT.NONE);
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

		ensureProjectAndIgnoreManager();
		if (ignoreFileManager != null) {
			ignoreFileManager.addListener(ignoreListener);
		}
		setupToolbar();
		refreshTable();
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
		if (currentProject == null) {
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			if (projects.length > 0) {
				currentProject = projects[0];
			}
		}
		if (currentProject != null && ignoreFileManager == null) {
			ignoreFileManager = IgnoreFileManager.getInstance(currentProject);
		}
	}

	public void refreshTable() {
		ensureProjectAndIgnoreManager();
		if (ignoreFileManager == null) {
			return;
		}

		ignoreFileManager.refreshFromDisk();
		List<IgnoreEntry> entries = ignoreFileManager.getAllIgnoreEntries().stream()
				.filter(entry -> activeFileCount(entry) > 0)
				.filter(entry -> entry.getSeverity() == null || filterState.hasFilter(entry.getSeverity()))
				.collect(Collectors.toList());

		if (container == null || container.isDisposed()) {
			return;
		}

		reconstructCards(entries);

		boolean hasEntries = !entries.isEmpty();
		int entryCount = entries.size();

		// Update tab title with count
		if (hasEntries) {
			setPartName("Ignored Findings (" + entryCount + ")");
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
		}

		container.layout(true, true);
	}

	private void reconstructCards(List<IgnoreEntry> entries) {
		for (IgnoreEntryCard card : cards) {
			card.dispose();
		}
		cards.clear();
		selectedEntries.clear();

		for (Control child : cardsContainer.getChildren()) {
			child.dispose();
		}

		for (IgnoreEntry entry : entries) {
			IgnoreEntryCard card = new IgnoreEntryCard(cardsContainer, entry, this);
			cards.add(card);
		}

		cardsContainer.layout(true, true);
		scrolledContainer.setMinHeight(cardsContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT).y);
		updateSelectionStateUI();
	}

	private void onSelectAllToggled(boolean selectAll) {
		isProgrammaticSelectionChange = true;
		try {
			selectedEntries.clear();
			for (IgnoreEntryCard card : cards) {
				card.setSelected(selectAll);
				if (selectAll) {
					selectedEntries.add(card.getEntry());
				}
			}
			updateSelectionStateUI();
		} finally {
			isProgrammaticSelectionChange = false;
		}
	}

	public void onCardSelectionChanged(IgnoreEntry entry, boolean selected) {
		if (isProgrammaticSelectionChange) {
			return;
		}

		if (selected) {
			selectedEntries.add(entry);
		} else {
			selectedEntries.remove(entry);
		}

		if (selectedEntries.size() == cards.size() && !cards.isEmpty()) {
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
		boolean hasSelection = !selectedEntries.isEmpty();

		selectionActionBar.setVisible(hasSelection);
		((GridData) selectionActionBar.getLayoutData()).exclude = !hasSelection;

		if (hasSelection) {
			int count = selectedEntries.size();
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

	private void reviveSelected() {
		ensureProjectAndIgnoreManager();
		if (currentProject == null || selectedEntries.isEmpty()) {
			return;
		}

		List<IgnoreEntry> entriesToRevive = new ArrayList<>(selectedEntries);
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
		private final IgnoreEntry entry;
		private final Font boldFont;
		private boolean isSelected = false;

		public IgnoreEntryCard(Composite parent, IgnoreEntry entry, DevAssistIgnoredFindings parentView) {
			this.entry = entry;

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
			checkboxButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					isSelected = checkboxButton.getSelection();
					parentView.onCardSelectionChanged(entry, isSelected);
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
				GridLayout tagsLayout = new GridLayout(activeFiles.size() + 1, false);
				tagsLayout.marginWidth = 0;
				tagsLayout.marginHeight = 2;
				tagsLayout.horizontalSpacing = 6;
				tagsComposite.setLayout(tagsLayout);

				Color tagBorderColor = parent.getDisplay().getSystemColor(SWT.COLOR_GRAY);
				Color tagTextColor = parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);

				// Type Badge (Slight rounding: 4px)
				createFlatBadge(tagsComposite, entry.getType() != null ? entry.getType().toString() : "VULNERABILITY",
						tagBorderColor, tagTextColor, 4);

				// File Badges (Pill style rounding: 10px)
				for (IgnoreEntry.FileReference file : activeFiles.stream().limit(2).collect(Collectors.toList())) {
					String fileName = file.getPath() != null ? Paths.get(file.getPath()).getFileName().toString()
							: "unknown";
					createFlatBadge(tagsComposite, "📄 " + fileName, tagBorderColor, tagTextColor, 10);
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

		public void dispose() {
			if (boldFont != null && !boldFont.isDisposed()) {
				boldFont.dispose();
			}
			if (cardComposite != null && !cardComposite.isDisposed()) {
				cardComposite.dispose();
			}
		}
	}

	private static void createFlatBadge(Composite parent, String text, Color borderColor, Color textColor,
			int cornerRadius) {
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
	}	
}