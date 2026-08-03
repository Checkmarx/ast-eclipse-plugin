package com.checkmarx.eclipse.devassist.ui.findings.ignored;

import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.part.ViewPart;

import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.ignored.IgnoredProblemsStore.IgnoredProblemsListener;

/**
 * Custom view for displaying ignored problems/findings. Shows problems that have been
 * explicitly ignored from the native Problems View and findings from the Findings View.
 * Allows restoring problems/findings back to the active list.
 */
public class CxIgnoredProblemsView extends ViewPart implements IgnoredProblemsListener {

	public static final String ID = "com.checkmarx.eclipse.devassist.ui.findings.ignored.CxIgnoredProblemsView";

	private TreeViewer treeViewer;
	private IgnoredProblemsStore ignoredStore;
	private List<ScanIssue> allIssues;

	@Override
	public void createPartControl(Composite parent) {
		System.out.println("[IGNORED-VIEW] Creating Ignored Problems View...");

		ignoredStore = IgnoredProblemsStore.getInstance();
		ignoredStore.addListener(this);

		// Create TreeViewer
		treeViewer = new TreeViewer(parent, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		treeViewer.setContentProvider(new IgnoredProblemsContentProvider());
		treeViewer.setLabelProvider(new IgnoredProblemsLabelProvider());
		treeViewer.setInput(new java.util.ArrayList<>());

		// Setup toolbar
		setupToolbar();

		// Setup context menu
		setupContextMenu();

		// Setup double-click listener
		treeViewer.addDoubleClickListener(event -> {
			ISelection selection = event.getSelection();
			if (selection instanceof IStructuredSelection) {
				Object selected = ((IStructuredSelection) selection).getFirstElement();
				if (selected instanceof ScanIssue) {
					ScanIssue issue = (ScanIssue) selected;
					navigateToIgnoredIssue(issue);
				}
			}
		});

		System.out.println("[IGNORED-VIEW] âœ“ Ignored Problems View created");
	}

	private void setupToolbar() {
		IToolBarManager toolbarManager = getViewSite().getActionBars().getToolBarManager();

		Action restoreAllAction = new Action("Restore All Ignored Problems") {
			@Override
			public void run() {
				System.out.println("[IGNORED-VIEW] Restoring all ignored problems...");
				ignoredStore.clearAll();
				refreshView();
			}
		};
		restoreAllAction.setToolTipText("Restore all ignored problems to active findings");
		toolbarManager.add(restoreAllAction);

		Action clearAllAction = new Action("Clear Ignored List") {
			@Override
			public void run() {
				System.out.println("[IGNORED-VIEW] Clearing all ignored problems permanently...");
				ignoredStore.clearAll();
				refreshView();
			}
		};
		clearAllAction.setToolTipText("Permanently clear the ignored problems list");
		toolbarManager.add(clearAllAction);
	}

	private void setupContextMenu() {
		Menu contextMenu = new Menu(treeViewer.getTree());
		treeViewer.getTree().setMenu(contextMenu);

		MenuItem restoreItem = new MenuItem(contextMenu, SWT.PUSH);
		restoreItem.setText("Restore This Finding");
		restoreItem.addListener(SWT.Selection, event -> {
			IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
			if (selection.getFirstElement() instanceof ScanIssue) {
				ScanIssue issue = (ScanIssue) selection.getFirstElement();
				System.out.println("[IGNORED-VIEW] Restoring finding: " + issue.getScanIssueId());
				ignoredStore.restoreProblem(issue.getScanIssueId());
				refreshView();
			}
		});

		new MenuItem(contextMenu, SWT.SEPARATOR);

		MenuItem navigateItem = new MenuItem(contextMenu, SWT.PUSH);
		navigateItem.setText("Go to Line");
		navigateItem.addListener(SWT.Selection, event -> {
			IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
			if (selection.getFirstElement() instanceof ScanIssue) {
				ScanIssue issue = (ScanIssue) selection.getFirstElement();
				navigateToIgnoredIssue(issue);
			}
		});

		new MenuItem(contextMenu, SWT.SEPARATOR);

		MenuItem deleteItem = new MenuItem(contextMenu, SWT.PUSH);
		deleteItem.setText("Delete from Ignore List");
		deleteItem.addListener(SWT.Selection, event -> {
			IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
			if (selection.getFirstElement() instanceof ScanIssue) {
				ScanIssue issue = (ScanIssue) selection.getFirstElement();
				System.out.println("[IGNORED-VIEW] Permanently removing from ignore list: " + issue.getScanIssueId());
				ignoredStore.restoreProblem(issue.getScanIssueId());
				refreshView();
			}
		});
	}

	private void navigateToIgnoredIssue(ScanIssue issue) {
		if (issue != null && issue.getFilePath() != null) {
			int lineNumber = (issue.getLocations() != null && !issue.getLocations().isEmpty())
				? issue.getLocations().get(0).getLine() : 0;
			System.out.println("[IGNORED-VIEW] Navigating to: " + issue.getFilePath() + " line " + lineNumber);
			org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
				try {
					org.eclipse.core.resources.IWorkspaceRoot root = org.eclipse.core.resources.ResourcesPlugin
							.getWorkspace().getRoot();
					// Find file in workspace by simple name
					String simpleName = new org.eclipse.core.runtime.Path(issue.getFilePath()).lastSegment();
					final org.eclipse.core.resources.IFile[] found = new org.eclipse.core.resources.IFile[1];

					root.accept(proxy -> {
						if (proxy.getType() == org.eclipse.core.resources.IResource.FILE &&
								proxy.getName().equals(simpleName)) {
							found[0] = (org.eclipse.core.resources.IFile) proxy.requestResource();
							return false;
						}
						return true;
					}, org.eclipse.core.resources.IResource.NONE);

					if (found[0] != null) {
						org.eclipse.ui.IWorkbenchWindow window = org.eclipse.ui.PlatformUI.getWorkbench()
								.getActiveWorkbenchWindow();
						if (window != null) {
							org.eclipse.ui.IWorkbenchPage page = window.getActivePage();
							if (page != null) {
								// Position cursor at the issue line using temporary marker
								if (lineNumber > 0) {
									positionCursorAtLineWithMarker(page, found[0], lineNumber);
								} else {
									// No line info, just open the file
									org.eclipse.ui.ide.IDE.openEditor(page, found[0], true);
									System.out.println("[IGNORED-VIEW] âœ“ Opened file: " + simpleName);
								}
							}
						}
					} else {
						System.out.println("[IGNORED-VIEW] File not found in workspace: " + simpleName);
					}
				} catch (Exception e) {
					System.err.println("[IGNORED-VIEW] Error navigating: " + e.getMessage());
					e.printStackTrace();
				}
			});
		}
	}

	/**
	 * Position cursor using temporary marker - same approach as native Problems View.
	 * This is the most reliable method that works with all Eclipse editors.
	 */
	private void positionCursorAtLineWithMarker(org.eclipse.ui.IWorkbenchPage page,
			org.eclipse.core.resources.IFile file, int lineNumber) {
		try {
			// Create temporary marker with line number
			org.eclipse.core.resources.IMarker tempMarker = file.createMarker("org.eclipse.core.resources.textmarker");
			tempMarker.setAttribute(org.eclipse.core.resources.IMarker.LINE_NUMBER, lineNumber);
			tempMarker.setAttribute(org.eclipse.core.resources.IMarker.TRANSIENT, true);

			// Open editor
			org.eclipse.ui.IEditorPart editor = org.eclipse.ui.ide.IDE.openEditor(page, file, true);
			System.out.println("[IGNORED-VIEW] âœ“ Opened file: " + file.getName());

			// Use IDE.gotoMarker to position cursor (same as native Problems View)
			if (editor != null) {
				org.eclipse.ui.ide.IDE.gotoMarker(editor, tempMarker);
				System.out.println("[IGNORED-VIEW] âœ“ Cursor positioned at line " + lineNumber);
			}

			// Delete the temporary marker
			try {
				tempMarker.delete();
			} catch (Exception e) {
				System.out.println("[IGNORED-VIEW] Could not delete temporary marker: " + e.getMessage());
			}
		} catch (Exception e) {
			System.err.println("[IGNORED-VIEW] Error positioning cursor: " + e.getMessage());
			// Fallback: just open the file
			try {
				org.eclipse.ui.ide.IDE.openEditor(page, file, true);
				System.out.println("[IGNORED-VIEW] âœ“ Opened file (fallback): " + file.getName());
			} catch (Exception fallbackEx) {
				System.err.println("[IGNORED-VIEW] Error in fallback: " + fallbackEx.getMessage());
			}
		}
	}

	@Override
	public void onIgnoredProblemsChanged() {
		System.out.println("[IGNORED-VIEW] Ignored problems changed, refreshing view...");
		refreshView();
	}

	private void refreshView() {
		if (treeViewer != null && !treeViewer.getTree().isDisposed()) {
			try {
				// Get all ignored issues including cached findings from the Findings View
				List<ScanIssue> ignoredIssues = ignoredStore.getAllIgnoredProblems(allIssues);

				System.out.println("[IGNORED-VIEW] Displaying " + ignoredIssues.size() +
						" ignored findings (Total known: " + (allIssues != null ? allIssues.size() : 0) + ")");

				treeViewer.setInput(ignoredIssues);
				treeViewer.expandAll();
			} catch (Exception e) {
				System.err.println("[IGNORED-VIEW] Error refreshing view: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	/**
	 * Update the view with all issues.
	 */
	public void updateIssues(List<ScanIssue> issues) {
		this.allIssues = issues;
		refreshView();
	}

	@Override
	public void setFocus() {
		if (treeViewer != null) {
			treeViewer.getTree().setFocus();
		}
	}

	@Override
	public void dispose() {
		ignoredStore.removeListener(this);
		super.dispose();
	}
}

