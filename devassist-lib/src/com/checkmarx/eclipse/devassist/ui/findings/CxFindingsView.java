package com.checkmarx.eclipse.devassist.ui.findings;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.apache.commons.lang3.StringUtils;

import com.checkmarx.eclipse.devassist.ui.findings.provider.FindingsContentProvider;
import com.checkmarx.eclipse.devassist.ui.findings.provider.FindingsLabelProvider;
import com.checkmarx.eclipse.devassist.utils.DevAssistConstants;
import com.checkmarx.eclipse.common.events.SettingsTopics;
import com.checkmarx.eclipse.common.preferences.Preferences;
import com.checkmarx.eclipse.devassist.backend.Constants;
import com.checkmarx.eclipse.devassist.backend.listener.CheckmarxDocumentListener;
import com.checkmarx.eclipse.devassist.backend.listener.RealTimeScanJob;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.model.FileNodeLabel;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanDetailWithPath;
import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.remediation.RemediationManager;
import com.checkmarx.eclipse.devassist.ui.findings.actions.VulnerabilityFilterAction;
import com.checkmarx.eclipse.devassist.ui.findings.actions.VulnerabilityFilterState;
import com.checkmarx.eclipse.devassist.ignore.IgnoreFileManager;
import com.checkmarx.eclipse.devassist.ignore.IgnoreManager;
import com.checkmarx.eclipse.common.utils.CxLogger;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;

/**
 * Custom Findings View for displaying Checkmarx scan results.
 * Extends {@link ViewPart} to provide a custom view in Eclipse.
 * Manages a tree view of vulnerabilities with filtering and navigation
 * capabilities.
 * Uses {@link TreeViewer} for flexible tree rendering with custom providers.
 */
public class CxFindingsView extends ViewPart {

	public static final String ID = "com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView";
	private org.osgi.service.event.EventHandler eventHandler;
	private org.osgi.service.event.EventHandler settingsEventHandler;

	private TreeViewer treeViewer;
	private Map<String, List<ScanIssue>> currentIssues = new HashMap<>();
	private IgnoreFileManager ignoreFileManager;
	private IProject currentProject;
	Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
	public static final Image FINDINGS_PROMOTIONAL_CUBE = createScaledImage("/icons/cx-one-assist-cube.png", 240);
	private static final Image CHECKMARX_OPEN_SETTINGS_LOGO = AbstractUIPlugin
			.imageDescriptorFromPlugin(Constants.MAIN_PLUGIN_ID, "/icons/checkmarx-80.png").createImage();
	private static final Image STAR_ICON = AbstractUIPlugin
			.imageDescriptorFromPlugin(Constants.MAIN_PLUGIN_ID, "/icons/severity/star-action.svg").createImage();

	public CxFindingsView() {
		super();
	}

	@Override
	public void createPartControl(Composite parent) {
		this.parentComposite = parent;

		// Set a clean 1-column layout on parent
		GridLayout parentLayout = new GridLayout(1, true);
		parentLayout.marginWidth = 0;
		parentLayout.marginHeight = 0;
		parentLayout.horizontalSpacing = 0;
		parentLayout.verticalSpacing = 0;
		parent.setLayout(parentLayout);

		// Always subscribe to events first
		subscribeToEventBroker();

		// Initialize ignore file manager lazily when needed
		ensureIgnoreFileManagerInitialized();

		// Initial render check
		refreshViewMode();
	}

	/**
	 * Loads an image and scales it down to the given max width (maintaining aspect
	 * ratio)
	 * if it is larger than that width.
	 */
	private static Image createScaledImage(String path, int maxWidth) {
		Image original = AbstractUIPlugin.imageDescriptorFromPlugin(Constants.MAIN_PLUGIN_ID, path).createImage();
		if (original.getBounds().width <= maxWidth) {
			return original;
		}

		double scale = (double) maxWidth / original.getBounds().width;
		int scaledWidth = maxWidth;
		int scaledHeight = (int) Math.round(original.getBounds().height * scale);

		ImageData scaledData = original.getImageData().scaledTo(scaledWidth, scaledHeight);
		Image scaledImage = new Image(original.getDevice(), scaledData);
		original.dispose();
		return scaledImage;
	}

	/**
	 * Determines which panel to draw based on current credentials status.
	 */
	private void refreshViewMode() {
		if (parentComposite == null || parentComposite.isDisposed()) {
			return;
		}

		if (StringUtils.isBlank(Preferences.getApiKey())) {
			drawMissingCredentialsPanel(parentComposite);
		} else {
			loadCachedIssues();
			drawFindingsPanel(parentComposite);
		}
	}

	/**
	 * Loads initial cached scan issues from workspace session properties.
	 */
	private void loadCachedIssues() {
		try {
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			if (projects.length > 0 && projects[0].isOpen()) {
				IProject project = projects[0];
				// Use getInstance() to get the correct ProblemHolderService instance
				// (matches the same key used by getInstance() in ProblemHolderService)
				ProblemHolderService problemHolder = ProblemHolderService.getInstance(project);

				if (problemHolder != null) {
					Map<String, List<ScanIssue>> existingIssues = problemHolder.getAllScanIssues();
					if (existingIssues != null && !existingIssues.isEmpty()) {
						this.currentIssues = existingIssues;
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[FINDINGS] Error reading cached issues: " + e.getMessage());
		}
	}

	/**
	 * Ensures IgnoreFileManager is initialized. Called lazily to handle
	 * cases where projects aren't available at view creation time.
	 */
	private void ensureIgnoreFileManagerInitialized() {
		if (ignoreFileManager == null) {
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			if (projects.length > 0) {
				currentProject = projects[0];
				ignoreFileManager = IgnoreFileManager.getInstance(currentProject);
			}
		}
	}

	private Composite openSettingsComposite;

	/**
	 * Renders the missing credentials panel centered inside the view parent.
	 * Also clears all findings from the ProblemHolderService and editor annotations on logout.
	 */
	private void drawMissingCredentialsPanel(Composite parent) {
		// Clear all findings from memory BEFORE disposing UI (to ensure tab title updates)
		try {
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			if (projects.length > 0 && projects[0].isOpen()) {
				IProject project = projects[0];
				ProblemHolderService problemHolder = ProblemHolderService.getInstance(project);
				if (problemHolder != null) {
					problemHolder.clearAll();
				}
			}
		} catch (Exception e) {
			System.err.println("[FINDINGS] Error clearing findings on logout: " + e.getMessage());
		}

		// Reset current issues cache
		currentIssues.clear();

		// Update tab title immediately to remove problem count
		setPartName(DevAssistConstants.DEVASSIST_TAB);

		// Clear all annotations from open editors
		try {
			com.checkmarx.eclipse.devassist.problems.ProblemDecorator.clearAllAnnotations();
		} catch (Exception e) {
			System.err.println("[FINDINGS] Error clearing annotations on logout: " + e.getMessage());
		}

		// Dispose all existing UI components in the view container
		for (Control child : parent.getChildren()) {
			child.dispose();
		}

		clearToolbar();

		openSettingsComposite = new Composite(parent, SWT.NONE);
		openSettingsComposite.setLayout(new GridLayout(1, true));
		openSettingsComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));

		// Logo
		final Label cxLogo = new Label(openSettingsComposite, SWT.NONE);
		cxLogo.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
		cxLogo.setImage(CHECKMARX_OPEN_SETTINGS_LOGO);

		// Open Settings Button
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

		parent.layout(true, true);
	}

	private void drawFindingsPanel(Composite parent) {
		// Clear out missing credentials panel if it exists
		for (Control child : parent.getChildren()) {
			child.dispose();
		}

		SashForm sashForm = new SashForm(parent, SWT.HORIZONTAL);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sashForm.setLayout(new FillLayout());

		Composite treeComposite = new Composite(sashForm, SWT.NONE);
		treeComposite.setLayout(new FillLayout());

		treeViewer = new TreeViewer(treeComposite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		treeViewer.setContentProvider(new FindingsContentProvider());
		treeViewer.setLabelProvider(new FindingsLabelProvider());

		Composite promotionalComposite = new Composite(sashForm, SWT.NONE);
		drawPromotionalPanel(promotionalComposite);

		sashForm.setWeights(new int[] { 70, 30 });

		setupTreeListeners();

		if (!currentIssues.isEmpty()) {
			refreshTreeWithFilter();
		}

		parent.layout(true, true);

		// Setup toolbar after layout has settled to ensure proper rendering in tab bar
		Display.getDefault().asyncExec(this::setupToolbar);
	}

	/**
	 * Renders the promotional cube image and description text in the right-hand
	 * pane
	 * of the findings split view.
	 */
	private void drawPromotionalPanel(Composite promotionalComposite) {
		GridLayout layout = new GridLayout(1, false);
		layout.marginLeft = 0;
		layout.marginRight = 40;
		layout.marginHeight = 10;
		promotionalComposite.setLayout(layout);

		Label cubeLabel = new Label(promotionalComposite, SWT.NONE);
		cubeLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
		cubeLabel.setImage(FINDINGS_PROMOTIONAL_CUBE);
		Label descriptionLabel = new Label(promotionalComposite, SWT.WRAP);
		GridData descriptionData = new GridData(SWT.LEFT, SWT.CENTER, true, false);
		descriptionLabel.setLayoutData(descriptionData);
		descriptionLabel.setText(Constants.FINDINGS_PROMO_DESCRIPTION);

		// SWT.WRAP labels need an explicit widthHint to wrap and left-align under the
		// image
		// instead of growing to one unbroken line. The pane has no real bounds yet at
		// this
		// point (the parent hasn't laid out), so compute it once asynchronously after
		// the
		// initial layout, and again whenever the pane is resized (e.g. by dragging the
		// sash).
		Runnable applyWrapWidth = () -> {
			if (promotionalComposite.isDisposed()) {
				return;
			}
			int availableWidth = promotionalComposite.getClientArea().width
					- (layout.marginLeft + layout.marginRight);
			if (availableWidth > 0 && descriptionData.widthHint != availableWidth) {
				descriptionData.widthHint = availableWidth;
				promotionalComposite.layout(true);
			}
		};

		promotionalComposite.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				applyWrapWidth.run();
			}
		});
		Display.getDefault().asyncExec(applyWrapWidth);
	}

	/**
	 * Subscribes to IEventBroker for issue updates & settings changes.
	 */
	private void subscribeToEventBroker() {
		try {
			org.eclipse.e4.core.services.events.IEventBroker eventBroker = getSite()
					.getService(org.eclipse.e4.core.services.events.IEventBroker.class);

			if (eventBroker == null) {
				eventBroker = PlatformUI.getWorkbench().getService(
						org.eclipse.e4.core.services.events.IEventBroker.class);
			}

			if (eventBroker != null) {
				// Topic 1: Scan issues updated
				eventHandler = event -> {
					Object data = event.getProperty(org.eclipse.e4.core.services.events.IEventBroker.DATA);
					if (data instanceof Map<?, ?>) {
						@SuppressWarnings("unchecked")
						Map<String, List<ScanIssue>> newIssues = (Map<String, List<ScanIssue>>) data;

						Display.getDefault().asyncExec(() -> {
							this.currentIssues = newIssues;
							if (treeViewer != null && !treeViewer.getControl().isDisposed()) {
								refreshTreeWithFilter();
							}
						});
					}
				};
				eventBroker.subscribe(ProblemHolderService.ISSUES_UPDATED_TOPIC, eventHandler);

				// Topic 2: Settings/Credentials applied or changed
				settingsEventHandler = event -> {
					Display.getDefault().asyncExec(() -> {
						refreshViewMode();
					});
				};
				eventBroker.subscribe(SettingsTopics.TOPIC_APPLY_SETTINGS, settingsEventHandler);
			}
		} catch (Exception e) {
			System.err.println("[FINDINGS] Error subscribing to IEventBroker: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public void dispose() {

		// 1. Unsubscribe from IEventBroker to prevent memory leaks
		if (eventHandler != null) {
			try {
				org.eclipse.e4.core.services.events.IEventBroker eventBroker = org.eclipse.ui.PlatformUI.getWorkbench()
						.getService(
								org.eclipse.e4.core.services.events.IEventBroker.class);

				if (eventBroker != null) {
					eventBroker.unsubscribe(eventHandler);

				}
			} catch (Exception e) {
				System.err.println("[FINDINGS] Error unsubscribing from IEventBroker: " + e.getMessage());
			}
		}

		super.dispose();
	}

	private Composite parentComposite;

	/**
	 * Removes every contribution from the view toolbar.
	 */
	private void clearToolbar() {
		IToolBarManager toolbar = getViewSite().getActionBars().getToolBarManager();
		toolbar.removeAll();
		toolbar.update(true);
		getViewSite().getActionBars().updateActionBars();
	}

	private void setupToolbar() {

		IToolBarManager toolbar = getViewSite().getActionBars().getToolBarManager();

		// The same IToolBarManager instance survives every re-render of the view,
		// so previous contributions must be dropped before re-adding them.
		toolbar.removeAll();

		// Add filter actions
		VulnerabilityFilterAction.IFilterChangeListener filterListener = () -> {

			refreshTreeWithFilter();
		};

		toolbar.add(new VulnerabilityFilterAction.MaliciousFilter(filterListener));
		toolbar.add(new VulnerabilityFilterAction.CriticalFilter(filterListener));
		toolbar.add(new VulnerabilityFilterAction.HighFilter(filterListener));
		toolbar.add(new VulnerabilityFilterAction.MediumFilter(filterListener));
		toolbar.add(new VulnerabilityFilterAction.LowFilter(filterListener));

		toolbar.add(new org.eclipse.jface.action.Separator("\t"));

		// Shared Eclipse images (replace with your own icons later)
		ISharedImages images = PlatformUI.getWorkbench().getSharedImages();

		// Toggle Expand/Collapse action
		Action toggleExpandCollapseAction = new Action("Expand All", Action.AS_PUSH_BUTTON) {

			private boolean expanded = false;

			{
				setToolTipText("Collapse All Findings");
				setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_ELCL_COLLAPSEALL));
			}

			@Override
			public void run() {
				if (expanded) {
					treeViewer.collapseAll();
					setText("Expand All");
					setToolTipText("Expand All Findings");
					setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_ELCL_COLLAPSEALL_DISABLED));
				} else {
					treeViewer.expandAll();
					setText("Collapse All");
					setToolTipText("Collapse All Findings");
					setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_ELCL_COLLAPSEALL));
				}

				expanded = !expanded;
			}
		};

		toolbar.add(toggleExpandCollapseAction);

		// Add spacing before preferences button
		toolbar.add(new org.eclipse.jface.action.Separator("\t"));

		// Preferences action: opens the same preference page as the main results view
		Action openPreferencesPageAction = new Action() {
			@Override
			public void run() {
				PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(
						shell, "com.checkmarx.eclipse.properties.preferencespage", null, null);
				if (pref != null) {
					pref.open();
				}
			}
		};

		// Toolbar preferences button
		Action toolbarPreferencesAction = new Action("\u2000?", Action.AS_PUSH_BUTTON) {
			@Override
			public void run() {
				openPreferencesPageAction.run();
			}
		};

		toolbarPreferencesAction.setToolTipText("Checkmarx Preferences");
		toolbar.add(toolbarPreferencesAction);

		toolbar.update(true);
		getViewSite().getActionBars().updateActionBars();

		// Force layout update on the parent to ensure toolbar renders properly
		if (parentComposite != null && !parentComposite.isDisposed()) {
			parentComposite.layout(true, true);
		}
	}

	private void setupTreeListeners() {
		Tree tree = treeViewer.getTree();

		// Listner for redirection
		tree.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				navigateToSelectedIssue(treeViewer.getSelection());
			}
		});

		// Right-click context menu
		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				if (e.button == 3) {

					showContextMenu(e);
				}
			}
		});

	}

	private void navigateToSelectedIssue(ISelection selection) {

		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ssel = (IStructuredSelection) selection;
			Object element = ssel.getFirstElement();

			if (element instanceof ScanDetailWithPath) {
				ScanDetailWithPath detailWithPath = (ScanDetailWithPath) element;
				navigateToIssue(detailWithPath);
			}
		}
	}

	private void navigateToIssue(ScanDetailWithPath detailWithPath) {
		ScanIssue detail = detailWithPath.getDetail();

		// Use resolved file path from ScanIssue (if available) or fallback to
		// detailWithPath
		String filePath = detail.getFilePath();
		if (filePath == null) {
			filePath = detailWithPath.getFilePath();
		}
		if (detail.getLocations() != null && !detail.getLocations().isEmpty()) {
			Location location = detail.getLocations().get(0);

			openFileInEditor(filePath, location.getLine(), detail);
		} else {

		}
	}

	/**
	 * Show detailed information about an issue.
	 */
	private void showIssueDetails(ScanIssue issue) {
		StringBuilder details = new StringBuilder();
		details.append("\n========== ISSUE DETAILS ==========\n");
		details.append("Title: ").append(issue.getTitle()).append("\n");
		details.append("Severity: ").append(issue.getSeverity()).append("\n");
		details.append("Scan Engine: ").append(issue.getScanEngine()).append("\n");
		details.append("Description: ").append(issue.getDescription()).append("\n");
		details.append("Issue ID: ").append(issue.getScanIssueId()).append("\n");

		if (issue.getPackageVersion() != null) {
			details.append("Package Version: ").append(issue.getPackageVersion()).append("\n");
		}
		if (issue.getCve() != null) {
			details.append("CVE: ").append(issue.getCve()).append("\n");
		}
		if (issue.getRemediationAdvise() != null) {
			details.append("Remediation: ").append(issue.getRemediationAdvise()).append("\n");
		}
		if (issue.getLocations() != null && !issue.getLocations().isEmpty()) {
			Location loc = issue.getLocations().get(0);
			details.append("Location: Line ").append(loc.getLine()).append(", Col ").append(loc.getStartIndex())
					.append("\n");
		}

	}

	/**
	 * Show error notification to user
	 */
	private void showErrorNotification(String message) {
		org.eclipse.swt.widgets.MessageBox msgBox = new org.eclipse.swt.widgets.MessageBox(
				treeViewer.getTree().getShell(),
				org.eclipse.swt.SWT.ERROR);
		msgBox.setMessage(message);
		msgBox.setText("Checkmarx AI Assist");
		msgBox.open();
	}

	/**
	 * Ignore this specific finding and remove from the Findings View.
	 * The finding is added to the IgnoreFileManager and appears in the Ignored
	 * Findings Window.
	 */
	private void ignoreThisFinding(ScanIssue issue) {
		try {
			// Ensure project is available
			ensureIgnoreFileManagerInitialized();
			if (currentProject == null) {
				showErrorNotification("Error: No active project");
				return;
			}

			// Use IgnoreManager to add the issue (matches JetBrains implementation)
			IgnoreManager ignoreManager = IgnoreManager.getInstance(currentProject);
			ignoreManager.addIgnoredEntry(issue, DevAssistConstants.QUICK_FIX);

			// Refresh the tree to remove the ignored finding from the view
			refreshTreeWithFilter();

			CxLogger.info("Successfully ignored finding: " + issue.getTitle());

		} catch (Exception e) {
			System.err.println("[FINDINGS] ✗ Error ignoring finding: " + e.getMessage());
			e.printStackTrace();
			showErrorNotification("Failed to ignore finding: " + e.getMessage());
		}
	}

	/**
	 * Ignore all findings of the same type/package.
	 * For OSS: ignores all findings with the same package version
	 * For CONTAINERS: ignores all findings with the same image tag
	 * Matches JetBrains implementation: addAllIgnoredEntry()
	 */
	private void ignoreAllOfType(ScanIssue issue) {
		try {
			// Ensure project is available
			ensureIgnoreFileManagerInitialized();
			if (currentProject == null) {
				showErrorNotification("Error: No active project");
				return;
			}

			// Use IgnoreManager to add all matching issues (matches JetBrains implementation)
			IgnoreManager ignoreManager = IgnoreManager.getInstance(currentProject);
			ignoreManager.addAllIgnoredEntry(issue, DevAssistConstants.QUICK_FIX);

			// Refresh the tree to remove the ignored findings from the view
			refreshTreeWithFilter();

			CxLogger.info("Successfully ignored all findings of type: " + issue.getTitle());

		} catch (Exception e) {
			System.err.println("[FINDINGS] ✗ Error ignoring findings of type: " + e.getMessage());
			e.printStackTrace();
			showErrorNotification("Failed to ignore findings of this type: " + e.getMessage());
		}
	}

	/**
	 * Copy issue details to clipboard as JSON.
	 */
	private void copyIssueDetails(ScanIssue issue) {
		StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"title\": \"").append(escapeJson(issue.getTitle())).append("\",\n");
		json.append("  \"severity\": \"").append(issue.getSeverity()).append("\",\n");
		json.append("  \"scanEngine\": \"").append(issue.getScanEngine()).append("\",\n");
		json.append("  \"description\": \"").append(escapeJson(issue.getDescription())).append("\",\n");
		json.append("  \"issueId\": \"").append(issue.getScanIssueId()).append("\"\n");
		json.append("}\n");

		try {
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
					.setContents(new java.awt.datatransfer.StringSelection(json.toString()), null);

		} catch (Exception e) {

		}
	}

	private String escapeJson(String text) {
		if (text == null)
			return "";
		return text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private void openFileInEditor(String filePath, int lineNumber, ScanIssue issue) {
		try {

			IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(
					new org.eclipse.core.runtime.Path(filePath));

			if (file == null || !file.exists()) {

				return;
			}

			// 1. Open file in active workbench page
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			IEditorPart editor = IDE.openEditor(page, file);

			// **CRITICAL FIX: Navigation-based opens don't trigger IPartListener2 events**
			// Directly set up real-time scanning and apply cached decorations
			// Pass the editor to avoid re-searching for it (which fails on MavenPomEditor)
			setupRealtimeScanningForFile(file, editor);

			// 2. Ensure marker exists and explicitly set LINE_NUMBER
			createMarkerForIssue(file, issue);

			// 3. Navigate using standard ITextEditor adapter (or fall back to marker
			// navigation)
			boolean scrolledSuccessfully = scrollToLine(editor, lineNumber);
			if (!scrolledSuccessfully) {
				highlightViaMarker(editor, file, issue);
			}

		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	/**
	 * Set up real-time scanning and apply cached decorations for a file.
	 * Called when file is opened via navigation to ensure we don't miss
	 * IPartListener2 events.
	 */
	private void setupRealtimeScanningForFile(org.eclipse.core.resources.IFile file, IEditorPart editor) {
		if (file == null || editor == null) {

			return;
		}

		try {
			// Extract document for real-time scanning
			org.eclipse.jface.text.IDocument document = null;
			String fileName = file.getName();

			// Try method 1: Direct ITextEditor instance check
			if (editor instanceof org.eclipse.ui.texteditor.ITextEditor) {

				org.eclipse.ui.texteditor.ITextEditor textEditor = (org.eclipse.ui.texteditor.ITextEditor) editor;
				document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
			}

			// Try method 2: ITextEditor Adapter pattern (for MavenPomEditor, etc.)
			if (document == null) {

				org.eclipse.ui.texteditor.ITextEditor textEditor = editor
						.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class);
				if (textEditor != null) {

					document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
				}
			}

			// Try method 3: Direct IDocument adapter (some editors provide this directly)
			if (document == null) {

				document = editor.getAdapter(org.eclipse.jface.text.IDocument.class);
				if (document != null) {

				}
			}

			if (document == null) {

				return;
			}

			// Create a scan job for this file
			RealTimeScanJob scanJob = new RealTimeScanJob(file, fileName);

			// Create a document listener that reschedules the job on every keystroke
			com.checkmarx.eclipse.devassist.inspection.DevAssistScanScheduler scheduler = null;
			if (file != null) {
				try {
					org.eclipse.core.resources.IProject project = file.getProject();
					if (project != null) {
						scheduler = (com.checkmarx.eclipse.devassist.inspection.DevAssistScanScheduler) project
								.getSessionProperty(
										new org.eclipse.core.runtime.QualifiedName("com.checkmarx.eclipse.plugin",
												"scan-scheduler"));
					}
				} catch (Exception e) {
					// Ignore if scheduler not available
				}
			}
			CheckmarxDocumentListener docListener = new CheckmarxDocumentListener(fileName, scanJob, file, scheduler);

			// Register the document listener
			document.addDocumentListener(docListener);

			// Apply cached decorations if findings exist for this file
			// Pass the editor directly to avoid search issues with MavenPomEditor
			applyCachedDecorationsForFile(file, document, editor);

		} catch (Exception e) {
			System.err.println("[REALTIME-SETUP] ✗ EXCEPTION during setup: " + e.getMessage());
			System.err.println("[REALTIME-SETUP] Exception type: " + e.getClass().getName());
			System.err.println("[REALTIME-SETUP] Stack trace:");
			e.printStackTrace();
		}
	}

	/**
	 * Apply cached decorations (gutter icons, underlines) when editor is opened via
	 * navigation.
	 *
	 * Delegates to ProblemDecorator.decorateEditor() (the same entry point used by
	 * refreshTreeWithFilter()) rather than duplicating annotation-creation logic,
	 * so this path also gets marker creation, stale-annotation clearing, and the
	 * "ignored" gutter-icon pass for any .checkmarxIgnored entries on this file -
	 * matching the JetBrains plugin's restoreGutterIcons()-on-file-open behavior.
	 */
	private void applyCachedDecorationsForFile(org.eclipse.core.resources.IFile file,
			org.eclipse.jface.text.IDocument document,
			org.eclipse.ui.IEditorPart editor) {
		if (file == null || document == null || editor == null) {
			return;
		}

		try {
			String filePath = file.getLocation().toOSString();
			org.eclipse.core.resources.IProject project = file.getProject();

			if (project == null) {
				return;
			}

			// Get cached findings for this file
			ProblemHolderService problemHolder = (ProblemHolderService) project.getSessionProperty(
					new org.eclipse.core.runtime.QualifiedName("com.checkmarx.eclipse.plugin", "problem-holder"));

			if (problemHolder == null) {
				return;
			}

			java.util.List<ScanIssue> cachedIssues = problemHolder.getScanIssuesByFile(filePath);

			// Filter out non-problem/ignored issues - ProblemDecorator only needs the
			// still-active findings here; ignored ones get their own gutter icon via
			// ProblemDecorator's internal ignored-entries pass.
			IgnoreManager ignoreManager = IgnoreManager.getInstance(project);
			java.util.List<ScanIssue> activeIssues = new java.util.ArrayList<>();
			for (ScanIssue issue : cachedIssues) {
				if (issue == null || issue.getSeverity() == null) {
					continue;
				}
				if (!com.checkmarx.eclipse.devassist.utils.DevAssistUtils.isProblem(issue.getSeverity())) {
					continue;
				}
				if (ignoreManager.isIgnored(issue)) {
					continue;
				}
				activeIssues.add(issue);
			}

			com.checkmarx.eclipse.devassist.problems.ProblemDecorator.decorateEditor(file, activeIssues);

		} catch (Exception e) {
			System.err.println("[REALTIME-SETUP] Error applying cached decorations: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Scroll editor to specific line number using native Eclipse ITextEditor
	 * adapter.
	 */
	private boolean scrollToLine(IEditorPart editor, int lineNumber) {
		if (editor == null || lineNumber <= 0)
			return false;

		try {
			// Use Eclipse's standard adapter pattern instead of reflection
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
						// Line numbers in IDocument are 0-indexed
						int lineOffset = document.getLineOffset(lineNumber - 1);
						textEditor.selectAndReveal(lineOffset, 0);

						return true;
					}
				}
			}
		} catch (Exception e) {

		}
		return false;
	}

	/**
	 * Ensures a marker exists for this issue. Markers are now created eagerly for
	 * every
	 * detected finding as soon as it's decorated (see
	 * ProblemDecorator.decorateEditor(), which
	 * calls MarkerIssueMapper.ensureMarker() for each issue) - this remains as a
	 * safety net for
	 * the navigate-to-finding flow in case the file wasn't open (and therefore
	 * wasn't decorated)
	 * when the finding was first reported.
	 */
	private void createMarkerForIssue(IFile file, ScanIssue issue) {
		com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper.ensureMarker(file, issue);
	}

	/**
	 * Apply highlighting to the problematic line.
	 */
	/**
	 * Navigate to the marker that corresponds to this issue.
	 * JDT's editor will automatically underline the marker and respect
	 * the marker annotation infrastructure (no custom hover registration needed).
	 */
	private void highlightViaMarker(org.eclipse.ui.IEditorPart editor, IFile file, ScanIssue issue) {
		try {
			if (editor == null || file == null || issue == null) {
				return;
			}

			// Find the marker corresponding to this issue
			IMarker marker = com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper.findMarker(file,
					issue);
			if (marker != null && marker.exists()) {
				org.eclipse.ui.ide.IDE.gotoMarker(editor, marker);

			} else {

			}
		} catch (Exception e) {

		}
	}

	private void showContextMenu(MouseEvent e) {
		ISelection selection = treeViewer.getSelection();
		if (!(selection instanceof IStructuredSelection)) {

			return;
		}

		IStructuredSelection ssel = (IStructuredSelection) selection;
		Object element = ssel.getFirstElement();

		if (!(element instanceof ScanDetailWithPath)) {

			return;
		}

		ScanDetailWithPath detailWithPath = (ScanDetailWithPath) element;
		ScanIssue issue = detailWithPath.getDetail();

		org.eclipse.swt.widgets.Menu menu = new org.eclipse.swt.widgets.Menu(treeViewer.getTree());

		// Menu Item 1: Fix with AI Assist
		org.eclipse.swt.widgets.MenuItem fixWithAIItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		fixWithAIItem.setImage(STAR_ICON);
		fixWithAIItem.setText(DevAssistConstants.FIX_WITH_CXONE_ASSIST);
		fixWithAIItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
			@Override
			public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
				new RemediationManager().fixWithCxOneAssist(issue, DevAssistConstants.QUICK_FIX);
			}
		});

		// Menu Item 2: View Details
		org.eclipse.swt.widgets.MenuItem viewDetailsItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		viewDetailsItem.setImage(STAR_ICON);
		viewDetailsItem.setText(DevAssistConstants.VIEW_DETAILS_FIX_NAME);
		viewDetailsItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
			@Override
			public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
				new RemediationManager().viewDetails(issue, DevAssistConstants.QUICK_FIX);
			}
		});

		// Menu Item 3: Ignore This Finding
		org.eclipse.swt.widgets.MenuItem ignoreItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		ignoreItem.setImage(STAR_ICON);
		ignoreItem.setText(DevAssistConstants.IGNORE_THIS_VULNERABILITY_FIX_NAME);
		ignoreItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
			@Override
			public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {

				ignoreThisFinding(issue);
			}
		});

		// Menu Item 4: Ignore All of This Type (for OSS and CONTAINERS)
		if (issue.getScanEngine() == ScanEngine.OSS || issue.getScanEngine() == ScanEngine.CONTAINERS) {
			org.eclipse.swt.widgets.MenuItem ignoreAllItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
			ignoreAllItem.setImage(STAR_ICON);
			ignoreAllItem.setText(DevAssistConstants.IGNORE_ALL_OF_THIS_TYPE_FIX_NAME);
			ignoreAllItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
				@Override
				public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {

					ignoreAllOfType(issue);
				}
			});
		}

		// Separator
		new org.eclipse.swt.widgets.MenuItem(menu, SWT.SEPARATOR);

		// Menu Item 5: Copy Issue Details
		org.eclipse.swt.widgets.MenuItem copyItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		copyItem.setText("Copy Issue Details (JSON)");
		copyItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
			@Override
			public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {

				copyIssueDetails(issue);
			}
		});

		// Menu Item 6: Open in Terminal
		org.eclipse.swt.widgets.MenuItem terminalItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
		terminalItem.setText("Navigate to Line");
		terminalItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
			@Override
			public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {

				navigateToIssue(detailWithPath);
			}
		});

		menu.setLocation(treeViewer.getTree().toDisplay(e.x, e.y));
		menu.setVisible(true);
	}

	private void refreshTreeWithFilter() {

		// Ensure ignore manager is initialized
		ensureIgnoreFileManagerInitialized();

		// Apply active filters and refresh
		VulnerabilityFilterState filterState = VulnerabilityFilterState.getInstance();

		Map<String, List<ScanIssue>> filteredIssues = new HashMap<>();
		int totalAfter = 0;

		for (String filePath : currentIssues.keySet()) {
			List<ScanIssue> issues = currentIssues.get(filePath);
			if (issues == null)
				continue;

			List<ScanIssue> filtered = new java.util.ArrayList<>();

			for (ScanIssue issue : issues) {
				// ✅ Safe null guard FIRST before calling any methods on issue
				if (issue == null || issue.getSeverity() == null) {

					continue;
				}

				String vulnerabilityKey = currentProject != null
						? IgnoreManager.getInstance(currentProject).createJsonKeyForIgnoreEntry(issue, DevAssistConstants.QUICK_FIX)
						: "";
				boolean isIgnored = ignoreFileManager != null && ignoreFileManager.isIgnored(vulnerabilityKey);
				boolean hasFilter = filterState.hasFilter(issue.getSeverity());
				boolean isProblem = com.checkmarx.eclipse.devassist.utils.DevAssistUtils.isProblem(issue.getSeverity());

				// Filter by OK/UNKNOWN/IGNORED severity (Phase 3)
				if (!isProblem) {

					continue;
				}
				// Filter by severity preference
				if (!hasFilter) {

					continue;
				}
				// Filter out ignored problems
				if (isIgnored) {

					continue;
				}

				filtered.add(issue);
			}

			if (!filtered.isEmpty()) {
				filteredIssues.put(filePath, filtered);
				totalAfter += filtered.size();
			}
		}

		// ✅ Verify treeViewer control before manipulating UI
		if (treeViewer != null && treeViewer.getControl() != null && !treeViewer.getControl().isDisposed()) {

			// Save current expansion state to avoid full tree rebuild
			Object[] expandedElements = treeViewer.getExpandedElements();

			// Use setInput() for initial population, refresh() for subsequent updates
			Object currentInput = treeViewer.getInput();
			if (currentInput == null) {
				// First time: full tree setup with initial data
				treeViewer.setInput(filteredIssues);
				treeViewer.expandAll();
			} else {
				// Subsequent updates: use targeted refresh instead of full rebuild
				// This avoids rebuilding the entire tree on every single-file scan
				treeViewer.setInput(filteredIssues);

				// Restore expansion state for files that still exist in filtered results
				java.util.List<Object> validExpanded = new java.util.ArrayList<>();
				for (Object element : expandedElements) {
					if (element instanceof FileNodeLabel) {
						FileNodeLabel fileNode = (FileNodeLabel) element;
						if (filteredIssues.containsKey(fileNode.getFilePath())) {
							validExpanded.add(element);
						}
					}
				}

				if (!validExpanded.isEmpty()) {
					treeViewer.setExpandedElements(validExpanded.toArray());
				} else {
					// If no previous expansion state, expand all
					treeViewer.expandAll();
				}
			}
		}

		// Update view title with problem count
		if (totalAfter > 0) {
			setPartName(DevAssistConstants.DEVASSIST_TAB+ " " + totalAfter);
		} else {
			setPartName(DevAssistConstants.DEVASSIST_TAB);
		}

		// Apply decorations to open editors for all filtered findings
		// This ensures annotations are in the annotation model for hover to find them
		applyDecorationsToOpenEditors(filteredIssues);
	}

	/**
	 * Apply decorations to all open editors that have (or previously had) findings.
	 * This ensures annotations are present in the annotation model when the Findings View
	 * displays cached results, so hover can find them without waiting for a new scan.
	 *
	 * Iterates {@code currentIssues} (the unfiltered set) rather than just
	 * {@code filteredIssues}, and defaults to an empty issue list for files that
	 * dropped out of filteredIssues entirely (e.g. every finding in a file was just
	 * ignored). Without this, decorateEditor() would never be called for that file
	 * again, leaving its now-stale annotations/markers/gutter icons in place forever
	 * instead of being cleared and replaced by the "ignored" gutter icon.
	 */
	private void applyDecorationsToOpenEditors(Map<String, List<ScanIssue>> filteredIssues) {
		if (currentIssues == null || currentIssues.isEmpty()) {
			return;
		}

		try {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			if (page == null) {
				return;
			}

			for (String filePath : currentIssues.keySet()) {
				List<ScanIssue> issues = filteredIssues != null
						? filteredIssues.getOrDefault(filePath, java.util.Collections.emptyList())
						: java.util.Collections.emptyList();

				// Find if this file is currently open in an editor
				try {
					IFile file = ResourcesPlugin.getWorkspace().getRoot()
							.getFileForLocation(new org.eclipse.core.runtime.Path(filePath));
					if (file != null && file.exists()) {
						// Trigger decoration for this file's open editor (if any)
						com.checkmarx.eclipse.devassist.problems.ProblemDecorator.decorateEditor(file, issues);
					}
				} catch (Exception e) {
					// Log but continue with other files
					System.err.println("[FINDINGS] Error decorating file " + filePath + ": " + e.getMessage());
				}
			}
		} catch (Exception e) {
			System.err.println("[FINDINGS] Error applying decorations to open editors: " + e.getMessage());
		}
	}

	/**
	 * Refresh the tree with new issues. Safely dispatches to the SWT UI Thread.
	 *
	 * @param issues Map of file paths to list of scan issues
	 */
	public void refreshTree(Map<String, List<ScanIssue>> issues) {
		if (issues == null)
			return;
		// Log issues by severity
		Map<String, Long> severityCounts = new HashMap<>();
		issues.values().forEach(issueList -> {
			if (issueList != null) {
				issueList.forEach(issue -> {
					if (issue != null && issue.getSeverity() != null) {
						String severity = issue.getSeverity().toLowerCase();
						severityCounts.put(severity, severityCounts.getOrDefault(severity, 0L) + 1);
					}
				});
			}
		});

		this.currentIssues = issues;
		// ✅ Thread-safe dispatching for background updates
		org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
			if (treeViewer != null && treeViewer.getControl() != null && !treeViewer.getControl().isDisposed()) {
				refreshTreeWithFilter();
			}
		});
	}

	@Override
	public void setFocus() {
		if (treeViewer != null && treeViewer.getControl() != null && !treeViewer.getControl().isDisposed()) {
			treeViewer.getControl().setFocus();
		}
	}

	public TreeViewer getTreeViewer() {
		return treeViewer;
	}

}
