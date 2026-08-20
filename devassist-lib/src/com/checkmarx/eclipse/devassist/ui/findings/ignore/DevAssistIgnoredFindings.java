package com.checkmarx.eclipse.devassist.ui.findings.ignore;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.part.ViewPart;

import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.devassist.ignore.IgnoreEntry;
import com.checkmarx.eclipse.devassist.ignore.IgnoreFileManager;
import com.checkmarx.eclipse.devassist.ignore.IgnoreManager;

/**
 * Tool window panel for viewing ignored vulnerability findings.
 * Lists every active entry from the project's .checkmarxIgnored file and
 * allows the user to revive (un-ignore) individual entries.
 */
public class DevAssistIgnoredFindings extends ViewPart {

    public static final String ID = "com.checkmarx.eclipse.devassist.ui.findings.ignore.DevAssistIgnoredFindings";

    private Composite container;
    private TableViewer tableViewer;
    private Label emptyLabel;
    private IProject currentProject;
    private IgnoreFileManager ignoreFileManager;
    private final IgnoreFileManager.IgnoreListener ignoreListener = this::onIgnoreDataUpdated;

    @Override
    public void createPartControl(Composite parent) {
        container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));

        emptyLabel = new Label(container, SWT.WRAP);
        emptyLabel.setText("Ignored vulnerabilities will be listed here.");
        emptyLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        tableViewer = new TableViewer(container, SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER);
        Table table = tableViewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createColumn("Package", 220, IgnoreEntry::getPackageName);
        createColumn("Type", 90, entry -> entry.getType() != null ? entry.getType().toString() : "");
        createColumn("Severity", 90, IgnoreEntry::getSeverity);
        createColumn("Files", 60, entry -> String.valueOf(activeFileCount(entry)));
        createColumn("Date Added", 160, IgnoreEntry::getDateAdded);

        tableViewer.setContentProvider(ArrayContentProvider.getInstance());

        Composite buttonBar = new Composite(container, SWT.NONE);
        buttonBar.setLayout(new GridLayout(1, false));
        buttonBar.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, false));

        Button reviveButton = new Button(buttonBar, SWT.PUSH);
        reviveButton.setText("Revive Selected");
        reviveButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                reviveSelected();
            }
        });

        ensureProjectAndIgnoreManager();
        if (ignoreFileManager != null) {
            ignoreFileManager.addListener(ignoreListener);
        }
        refreshTable();

        CxLogger.info("DevAssistIgnoredFindings view created");
    }

    private void createColumn(String title, int width, java.util.function.Function<IgnoreEntry, String> textFn) {
        TableViewerColumn column = new TableViewerColumn(tableViewer, SWT.LEFT);
        TableColumn tableColumn = column.getColumn();
        tableColumn.setText(title);
        tableColumn.setWidth(width);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                if (!(element instanceof IgnoreEntry)) {
                    return "";
                }
                String text = textFn.apply((IgnoreEntry) element);
                return text != null ? text : "";
            }
        });
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
     * Reloads the ignored entries from disk and refreshes the table.
     * Called on initial view creation and whenever the ignore file changes
     * (new ignore added, entry revived from elsewhere, file watcher update).
     */
    public void refreshTable() {
        ensureProjectAndIgnoreManager();
        if (ignoreFileManager == null) {
            return;
        }
        List<IgnoreEntry> entries = ignoreFileManager.getAllIgnoreEntries().stream()
                .filter(entry -> activeFileCount(entry) > 0)
                .collect(java.util.stream.Collectors.toList());

        if (tableViewer.getTable().isDisposed()) {
            return;
        }
        tableViewer.setInput(entries.toArray(new IgnoreEntry[0]));

        boolean hasEntries = !entries.isEmpty();
        emptyLabel.setVisible(!hasEntries);
        ((GridData) emptyLabel.getLayoutData()).exclude = hasEntries;
        tableViewer.getTable().setVisible(hasEntries);
        ((GridData) tableViewer.getTable().getLayoutData()).exclude = !hasEntries;
        container.layout(true, true);
    }

    private void reviveSelected() {
        ensureProjectAndIgnoreManager();
        if (currentProject == null) {
            return;
        }
        IStructuredSelection selection = tableViewer.getStructuredSelection();
        if (selection.isEmpty()) {
            return;
        }
        IgnoreEntry entry = (IgnoreEntry) selection.getFirstElement();
        IgnoreManager.getInstance(currentProject).reviveSingleEntry(entry);
        refreshTable();
    }

    private void onIgnoreDataUpdated() {
        org.eclipse.swt.widgets.Display display = container != null && !container.isDisposed()
                ? container.getDisplay()
                : org.eclipse.swt.widgets.Display.getDefault();
        display.asyncExec(() -> {
            if (container != null && !container.isDisposed()) {
                refreshTable();
            }
        });
    }

    @Override
    public void setFocus() {
        if (tableViewer != null && tableViewer.getTable() != null && !tableViewer.getTable().isDisposed()) {
            tableViewer.getTable().setFocus();
        }
    }

    @Override
    public void dispose() {
        if (ignoreFileManager != null) {
            ignoreFileManager.removeListener(ignoreListener);
        }
        if (container != null && !container.isDisposed()) {
            container.dispose();
        }
        super.dispose();
    }
}
