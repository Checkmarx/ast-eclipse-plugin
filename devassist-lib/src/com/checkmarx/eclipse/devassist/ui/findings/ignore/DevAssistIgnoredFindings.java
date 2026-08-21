package com.checkmarx.eclipse.devassist.ui.findings.ignore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.part.ViewPart;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.ignore.IgnoreEntry;
import com.checkmarx.eclipse.devassist.ignore.IgnoreFileManager;
import com.checkmarx.eclipse.devassist.ignore.IgnoreManager;

/**
 * Tool window panel for viewing ignored vulnerability findings.
 * Displays ignored entries as individual cards in a flow layout.
 * Each card has a checkbox for selection and a revive button.
 * Selected cards can be revived using a "Revive Selected" button that appears at the top.
 */
public class DevAssistIgnoredFindings extends ViewPart {

    public static final String ID = "com.checkmarx.eclipse.devassist.ui.findings.ignore.DevAssistIgnoredFindings";

    private Composite container;
    private ScrolledComposite scrolledContainer;
    private Composite cardsContainer;
    private Label emptyLabel;
    private Label reviveSelectedLabel;
    private Button reviveSelectedButton;
    private IProject currentProject;
    private IgnoreFileManager ignoreFileManager;
    private final IgnoreFileManager.IgnoreListener ignoreListener = this::onIgnoreDataUpdated;

    // Track cards and their selected state
    private List<IgnoreEntryCard> cards = new ArrayList<>();
    private Set<IgnoreEntry> selectedEntries = new HashSet<>();

    @Override
    public void createPartControl(Composite parent) {
        container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));

        // Title/Header with "Revive Selected" button
        Composite headerComposite = new Composite(container, SWT.NONE);
        headerComposite.setLayout(new GridLayout(2, false));
        headerComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        reviveSelectedLabel = new Label(headerComposite, SWT.NONE);
        reviveSelectedLabel.setText("Ignored Vulnerabilities");
        reviveSelectedLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        reviveSelectedButton = new Button(headerComposite, SWT.PUSH);
        reviveSelectedButton.setText("Revive Selected");
        reviveSelectedButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        reviveSelectedButton.setEnabled(false);
        reviveSelectedButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                reviveSelected();
            }
        });

        // Empty state label
        emptyLabel = new Label(container, SWT.WRAP);
        emptyLabel.setText("No ignored vulnerabilities. Ignored findings will be listed here.");
        emptyLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        emptyLabel.setVisible(true);

        // Scrolled container for cards
        scrolledContainer = new ScrolledComposite(container, SWT.V_SCROLL | SWT.H_SCROLL);
        scrolledContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolledContainer.setExpandHorizontal(true);
        scrolledContainer.setExpandVertical(true);

        cardsContainer = new Composite(scrolledContainer, SWT.NONE);
        GridLayout cardLayout = new GridLayout(1, true);
        cardLayout.marginLeft = 10;
        cardLayout.marginRight = 10;
        cardLayout.marginTop = 10;
        cardLayout.marginBottom = 10;
        cardLayout.verticalSpacing = 8;
        cardsContainer.setLayout(cardLayout);

        scrolledContainer.setContent(cardsContainer);
        scrolledContainer.setVisible(false);

        ensureProjectAndIgnoreManager();
        if (ignoreFileManager != null) {
            ignoreFileManager.addListener(ignoreListener);
        }
        refreshTable();

        CxLogger.info("[IGNORED_FINDINGS] View created with card-based UI");
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

    /**
     * Reloads the ignored entries from disk and refreshes the card view.
     * Called on initial view creation and whenever the ignore file changes
     * (new ignore added, entry revived from elsewhere, file watcher update).
     */
    public void refreshTable() {
        CxLogger.info("[IGNORED_FINDINGS] Refreshing ignored findings view");
        ensureProjectAndIgnoreManager();
        if (ignoreFileManager == null) {
            CxLogger.warning("[IGNORED_FINDINGS] ignoreFileManager is null");
            return;
        }

        // Force a fresh read of .checkmarxIgnored from disk
        ignoreFileManager.refreshFromDisk();
        List<IgnoreEntry> entries = ignoreFileManager.getAllIgnoreEntries().stream()
                .filter(entry -> activeFileCount(entry) > 0)
                .collect(java.util.stream.Collectors.toList());

        CxLogger.info("[IGNORED_FINDINGS] Found " + entries.size() + " ignored entries");

        if (container == null || container.isDisposed()) {
            return;
        }

        // Reconstruct cards
        reconstructCards(entries);

        boolean hasEntries = !entries.isEmpty();
        emptyLabel.setVisible(!hasEntries);
        ((GridData) emptyLabel.getLayoutData()).exclude = hasEntries;
        scrolledContainer.setVisible(hasEntries);
        ((GridData) scrolledContainer.getLayoutData()).exclude = !hasEntries;
        container.layout(true, true);

        CxLogger.info("[IGNORED_FINDINGS] Refresh complete - " + entries.size() + " entries displayed");
    }

    /**
     * Reconstruct the card view with current entries.
     */
    private void reconstructCards(List<IgnoreEntry> entries) {
        CxLogger.info("[IGNORED_FINDINGS] Reconstructing " + entries.size() + " card(s)");

        // Dispose old cards
        for (IgnoreEntryCard card : cards) {
            card.dispose();
        }
        cards.clear();
        selectedEntries.clear();

        // Dispose old children in cardsContainer
        for (org.eclipse.swt.widgets.Control child : cardsContainer.getChildren()) {
            child.dispose();
        }

        // Create new cards
        for (IgnoreEntry entry : entries) {
            IgnoreEntryCard card = new IgnoreEntryCard(cardsContainer, entry, this);
            cards.add(card);
            CxLogger.info("[IGNORED_FINDINGS] Created card for: " + entry.getPackageName());
        }

        cardsContainer.layout(true, true);
        scrolledContainer.setMinHeight(cardsContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT).y);

        // Update "Revive Selected" button visibility
        updateReviveSelectedButton();
    }

    /**
     * Called when a card's checkbox state changes.
     */
    public void onCardSelectionChanged(IgnoreEntry entry, boolean selected) {
        CxLogger.info("[IGNORED_FINDINGS] Card selection changed: " + entry.getPackageName() + " = " + selected);
        if (selected) {
            selectedEntries.add(entry);
        } else {
            selectedEntries.remove(entry);
        }
        updateReviveSelectedButton();
    }

    /**
     * Called when a card's revive button is clicked.
     */
    public void onCardRevive(IgnoreEntry entry) {
        CxLogger.info("[IGNORED_FINDINGS] Reviving entry from card: " + entry.getPackageName());
        ensureProjectAndIgnoreManager();
        if (currentProject == null) {
            CxLogger.warning("[IGNORED_FINDINGS] currentProject is null");
            return;
        }
        IgnoreManager.getInstance(currentProject).reviveSingleEntry(entry);
        refreshTable();
    }

    /**
     * Update "Revive Selected" button visibility and enablement.
     */
    private void updateReviveSelectedButton() {
        if (reviveSelectedButton == null || reviveSelectedButton.isDisposed()) {
            return;
        }

        boolean hasSelection = !selectedEntries.isEmpty();
        reviveSelectedButton.setEnabled(hasSelection);

        if (hasSelection) {
            reviveSelectedButton.setText("Revive Selected (" + selectedEntries.size() + ")");
        } else {
            reviveSelectedButton.setText("Revive Selected");
        }
    }

    private void reviveSelected() {
        CxLogger.info("[IGNORED_FINDINGS] ============================================");
        CxLogger.info("[IGNORED_FINDINGS] Reviving " + selectedEntries.size() + " selected entries");
        ensureProjectAndIgnoreManager();
        if (currentProject == null) {
            CxLogger.warning("[IGNORED_FINDINGS] currentProject is null");
            return;
        }
        if (selectedEntries.isEmpty()) {
            CxLogger.warning("[IGNORED_FINDINGS] selectedEntries is empty");
            return;
        }

        // Create a copy to iterate over (selection might change during iteration)
        List<IgnoreEntry> entriesToRevive = new ArrayList<>(selectedEntries);
        CxLogger.info("[IGNORED_FINDINGS] Created copy of selectedEntries: " + entriesToRevive.size() + " entries");

        for (IgnoreEntry entry : entriesToRevive) {
            try {
                CxLogger.info("[IGNORED_FINDINGS] Reviving entry: " + entry.getPackageName() + " (type: " + entry.getType() + ")");
                IgnoreManager.getInstance(currentProject).reviveSingleEntry(entry);
                CxLogger.info("[IGNORED_FINDINGS] Successfully revived: " + entry.getPackageName());
            } catch (Exception e) {
                CxLogger.warning("[IGNORED_FINDINGS] Error reviving " + entry.getPackageName() + ": " + e.getMessage());
            }
        }

        CxLogger.info("[IGNORED_FINDINGS] All " + entriesToRevive.size() + " entries processed");
        CxLogger.info("[IGNORED_FINDINGS] Refreshing table...");
        refreshTable();
        CxLogger.info("[IGNORED_FINDINGS] ============================================");
    }

    private void onIgnoreDataUpdated() {
        Display display = container != null && !container.isDisposed()
                ? container.getDisplay()
                : Display.getDefault();
        display.asyncExec(() -> {
            if (container != null && !container.isDisposed()) {
                refreshTable();
            }
        });
    }

    @Override
    public void setFocus() {
        // Pick up any external edits to .checkmarxIgnored made while this view
        // wasn't in focus, rather than relying solely on the file watcher.
        CxLogger.info("[IGNORED_FINDINGS] setFocus() called - will refresh to catch external file changes");
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
     * Inner class representing a single ignored entry card.
     * Displays entry details with checkbox and revive button in a card-like container.
     */
    private static class IgnoreEntryCard {
        private final Composite cardComposite;
        private final Button checkboxButton;
        private final IgnoreEntry entry;
        private final DevAssistIgnoredFindings parent;
        private final org.eclipse.swt.graphics.Font boldFont;
        private boolean isSelected = false;

        public IgnoreEntryCard(Composite parent, IgnoreEntry entry, DevAssistIgnoredFindings parentView) {
            this.entry = entry;
            this.parent = parentView;

            // Card container with border-like appearance
            cardComposite = new Composite(parent, SWT.BORDER);
            cardComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            cardComposite.setLayout(new GridLayout(6, false));

            // Add some styling (background)
            cardComposite.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));

            // Checkbox (column 1)
            checkboxButton = new Button(cardComposite, SWT.CHECK);
            checkboxButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
            checkboxButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    isSelected = checkboxButton.getSelection();
                    CxLogger.info("[CARD] Checkbox toggled: " + entry.getPackageName() + " = " + isSelected);
                    parentView.onCardSelectionChanged(entry, isSelected);
                }
            });

            // Package name (column 2) - with bold font
            Label nameLabel = new Label(cardComposite, SWT.NONE);
            nameLabel.setText(entry.getPackageName() != null ? entry.getPackageName() : "Unknown");
            nameLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            // Create bold font and store reference for disposal
            this.boldFont = new org.eclipse.swt.graphics.Font(parent.getDisplay(),
                    nameLabel.getFont().getFontData()[0].getName(),
                    nameLabel.getFont().getFontData()[0].getHeight(),
                    SWT.BOLD);
            nameLabel.setFont(boldFont);

            // Type (column 3)
            Label typeLabel = new Label(cardComposite, SWT.NONE);
            typeLabel.setText(entry.getType() != null ? entry.getType().toString() : "");
            typeLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

            // Severity (column 4)
            Label severityLabel = new Label(cardComposite, SWT.NONE);
            severityLabel.setText(entry.getSeverity() != null ? entry.getSeverity() : "");
            severityLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

            // Files count (column 5)
            Label filesLabel = new Label(cardComposite, SWT.NONE);
            filesLabel.setText(activeFileCount(entry) + " file(s)");
            filesLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

            // Revive button (column 6)
            Button reviveButton = new Button(cardComposite, SWT.PUSH);
            reviveButton.setText("Revive");
            reviveButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
            reviveButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    CxLogger.info("[CARD] Revive button clicked: " + entry.getPackageName());
                    parentView.onCardRevive(entry);
                }
            });

            CxLogger.info("[CARD] Created card for: " + entry.getPackageName());
        }

        public void dispose() {
            if (boldFont != null && !boldFont.isDisposed()) {
                boldFont.dispose();
                CxLogger.info("[CARD] Disposed bold font for: " + entry.getPackageName());
            }
            if (cardComposite != null && !cardComposite.isDisposed()) {
                cardComposite.dispose();
            }
        }

        public boolean isSelected() {
            return isSelected;
        }

        private static int activeFileCount(IgnoreEntry entry) {
            if (entry.getFiles() == null) {
                return 0;
            }
            return (int) entry.getFiles().stream().filter(IgnoreEntry.FileReference::isActive).count();
        }
    }
}
