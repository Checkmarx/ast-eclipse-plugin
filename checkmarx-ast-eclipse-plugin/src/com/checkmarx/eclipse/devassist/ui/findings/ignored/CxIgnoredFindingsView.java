package com.checkmarx.eclipse.devassist.ui.findings.ignored;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

import com.checkmarx.eclipse.devassist.ignore.IgnoreEntry;
import com.checkmarx.eclipse.devassist.ignore.IgnoreFileManager;
import com.checkmarx.eclipse.devassist.ignore.IgnoreManager;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Lists every ignored finding across all engines, with per-occurrence jump-to-line
 * navigation and per-entry / bulk "Revive" (un-ignore).
 *
 * Mirrors JetBrains' "Ignored Findings" tool-window tab (DevAssistIgnoredFindings) - the
 * only place a previously-ignored finding can be inspected or restored, since ignored
 * findings are otherwise excluded entirely from the main Findings view and the editor.
 */
public class CxIgnoredFindingsView extends ViewPart implements IgnoreFileManager.IgnoreListener {

	public static final String ID = "com.checkmarx.eclipse.devassist.ui.findings.ignored.CxIgnoredFindingsView";

	private static final String LOG_TAG = "[IGNORED-VIEW]";

	private final IgnoreManager ignoreManager = IgnoreManager.getInstance();

	private TreeViewer treeViewer;

	@Override
	public void createPartControl(Composite parent) {
		GridLayout layout = new GridLayout(1, false);
		parent.setLayout(layout);

		Composite toolbar = new Composite(parent, SWT.NONE);
		toolbar.setLayout(new GridLayout(2, false));
		toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		Button reviveSelectedButton = new Button(toolbar, SWT.PUSH);
		reviveSelectedButton.setText("Revive Selected");
		reviveSelectedButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				reviveSelection();
			}
		});

		Button reviveAllButton = new Button(toolbar, SWT.PUSH);
		reviveAllButton.setText("Revive All");
		reviveAllButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ignoreManager.reviveEntries(ignoreManager.getIgnoredEntries());
			}
		});

		Tree tree = new Tree(parent, SWT.MULTI | SWT.FULL_SELECTION | SWT.BORDER);
		tree.setHeaderVisible(true);
		tree.setLinesVisible(true);
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		treeViewer = new TreeViewer(tree);
		treeViewer.setContentProvider(new IgnoredEntryContentProvider());

		addColumn("Finding", 260, new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof IgnoreEntry) {
					return ((IgnoreEntry) element).getTitle();
				}
				if (element instanceof IgnoreEntry.FileReference) {
					return fileNameOf(((IgnoreEntry.FileReference) element).getPath());
				}
				return String.valueOf(element);
			}
		});
		addColumn("Engine", 100, new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof IgnoreEntry) {
					return String.valueOf(((IgnoreEntry) element).getType());
				}
				return "";
			}
		});
		addColumn("Severity", 90, new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof IgnoreEntry) {
					return ((IgnoreEntry) element).getSeverity();
				}
				return "";
			}
		});
		addColumn("Location", 300, new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof IgnoreEntry.FileReference) {
					IgnoreEntry.FileReference ref = (IgnoreEntry.FileReference) element;
					return ref.getPath() + ":" + ref.getLine();
				}
				IgnoreEntry entry = (IgnoreEntry) element;
				long fileCount = entry.getFiles().stream().filter(IgnoreEntry.FileReference::isActive).count();
				return fileCount + " file" + (fileCount == 1 ? "" : "s");
			}
		});
		addColumn("Date Added", 160, new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof IgnoreEntry) {
					String date = ((IgnoreEntry) element).getDateAdded();
					return date == null ? "" : date;
				}
				return "";
			}
		});

		treeViewer.addDoubleClickListener(event -> jumpToSelection());

		createContextMenu();

		ignoreManager.addListener(this);
		refresh();
	}

	private void addColumn(String title, int width, ColumnLabelProvider labelProvider) {
		TreeViewerColumn column = new TreeViewerColumn(treeViewer, SWT.LEFT);
		TreeColumn treeColumn = column.getColumn();
		treeColumn.setText(title);
		treeColumn.setWidth(width);
		column.setLabelProvider(labelProvider);
	}

	private void createContextMenu() {
		Menu menu = new Menu(treeViewer.getTree());

		MenuItem reviveItem = new MenuItem(menu, SWT.PUSH);
		reviveItem.setText("Revive");
		reviveItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				reviveSelection();
			}
		});

		MenuItem jumpItem = new MenuItem(menu, SWT.PUSH);
		jumpItem.setText("Jump to Line");
		jumpItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				jumpToSelection();
			}
		});

		treeViewer.getTree().setMenu(menu);
	}

	private void reviveSelection() {
		java.util.Set<IgnoreEntry> toRevive = new java.util.LinkedHashSet<>();
		for (org.eclipse.swt.widgets.TreeItem item : treeViewer.getTree().getSelection()) {
			Object data = item.getData();
			if (data instanceof IgnoreEntry) {
				toRevive.add((IgnoreEntry) data);
			} else if (data instanceof IgnoreEntry.FileReference && item.getParentItem() != null) {
				Object parentData = item.getParentItem().getData();
				if (parentData instanceof IgnoreEntry) {
					toRevive.add((IgnoreEntry) parentData);
				}
			}
		}
		ignoreManager.reviveEntries(new java.util.ArrayList<>(toRevive));
	}

	private void jumpToSelection() {
		IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
		Object element = selection.getFirstElement();
		IgnoreEntry.FileReference ref = null;
		if (element instanceof IgnoreEntry.FileReference) {
			ref = (IgnoreEntry.FileReference) element;
		} else if (element instanceof IgnoreEntry) {
			List<IgnoreEntry.FileReference> files = ((IgnoreEntry) element).getFiles();
			ref = files.stream().filter(IgnoreEntry.FileReference::isActive).findFirst().orElse(null);
		}
		if (ref == null) {
			return;
		}
		openFileAtLine(ref.getPath(), ref.getLine());
	}

	private void openFileAtLine(String absolutePath, int line) {
		try {
			IFile file = ResourcesPlugin.getWorkspace().getRoot()
					.getFileForLocation(new org.eclipse.core.runtime.Path(absolutePath));
			if (file == null || !file.exists()) {
				CxLogger.warning(LOG_TAG + " Cannot resolve file for: " + absolutePath);
				return;
			}
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			IEditorPart editor = IDE.openEditor(page, file);
			ITextEditor textEditor = editor.getAdapter(ITextEditor.class);
			if (textEditor == null) {
				return;
			}
			org.eclipse.jface.text.IDocument document = textEditor.getDocumentProvider()
					.getDocument(textEditor.getEditorInput());
			if (document == null || line <= 0 || line > document.getNumberOfLines()) {
				return;
			}
			int offset = document.getLineOffset(line - 1);
			textEditor.selectAndReveal(offset, 0);
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Failed to jump to " + absolutePath + ":" + line + " - " + e.getMessage());
		}
	}

	private static String fileNameOf(String absolutePath) {
		if (absolutePath == null) {
			return "";
		}
		int idx = Math.max(absolutePath.lastIndexOf('/'), absolutePath.lastIndexOf('\\'));
		return idx >= 0 ? absolutePath.substring(idx + 1) : absolutePath;
	}

	private void refresh() {
		if (treeViewer == null || treeViewer.getControl().isDisposed()) {
			return;
		}
		treeViewer.setInput(ignoreManager.getIgnoredEntries());
	}

	@Override
	public void onIgnoreUpdated() {
		if (treeViewer != null && !treeViewer.getControl().isDisposed()) {
			treeViewer.getControl().getDisplay().asyncExec(this::refresh);
		}
	}

	@Override
	public void setFocus() {
		if (treeViewer != null) {
			treeViewer.getControl().setFocus();
		}
	}

	@Override
	public void dispose() {
		ignoreManager.removeListener(this);
		super.dispose();
	}

	/**
	 * Tree shape: root = List<IgnoreEntry> (top level), each entry's children = its
	 * active FileReferences.
	 */
	private static class IgnoredEntryContentProvider implements ITreeContentProvider {
		@Override
		public Object[] getElements(Object inputElement) {
			if (inputElement instanceof List) {
				return ((List<?>) inputElement).toArray();
			}
			return new Object[0];
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof IgnoreEntry) {
				return ((IgnoreEntry) parentElement).getFiles().stream()
						.filter(IgnoreEntry.FileReference::isActive)
						.toArray();
			}
			return new Object[0];
		}

		@Override
		public Object getParent(Object element) {
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return element instanceof IgnoreEntry && !((IgnoreEntry) element).getFiles().isEmpty();
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			// no-op
		}

		@Override
		public void dispose() {
			// no-op
		}
	}
}
