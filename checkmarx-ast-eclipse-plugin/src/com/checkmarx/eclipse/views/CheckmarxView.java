package com.checkmarx.eclipse.views;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jgit.events.RefsChangedEvent;
import org.eclipse.jgit.events.RefsChangedListener;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.osgi.service.event.EventHandler;

import com.checkmarx.ast.predicate.Predicate;
import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.PackageData;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.actions.ActionOpenPreferencesPage;
import com.checkmarx.eclipse.views.actions.ToolBarActions;
import com.checkmarx.eclipse.views.filters.FilterState;
import com.checkmarx.eclipse.views.provider.ColumnProvider;
import com.checkmarx.eclipse.views.provider.TreeContentProvider;
import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;


public class CheckmarxView extends ViewPart implements EventHandler {

	private static final String PROJECT_COMBO_VIEWER_TEXT = "Select a project";
	private static final String SCAN_COMBO_VIEWER_TEXT = "Select a scan";
	private static final String BRANCH_COMBO_VIEWER_TEXT = "Select a branch";
	private static final String LOADING_PROJECTS = "Loading projects...";
	private static final String LOADING_BRANCHES = "Loading branches...";
	private static final String LOADING_SCANS = "Loading scans...";
	private static final String NO_BRANCHES_AVAILABLE = "No branches available.";
	private static final String NO_PROJECTS_AVAILABLE = "No projects available.";
	private static final String FORMATTED_SCAN_LABEL = "%s (%s)";

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "com.checkmarx.eclipse.views.CheckmarxView";
	
	public static final Image CHECKMARX_OPEN_SETTINGS_LOGO = Activator.getImageDescriptor("/icons/checkmarx-80.png")
			.createImage();

	public static final Image CRITICAL_SEVERITY = Activator.getImageDescriptor("/icons/severity-critical.png")
			.createImage();

	public static final Image HIGH_SEVERITY = Activator.getImageDescriptor("/icons/high_untoggle.png").createImage();

	public static final Image MEDIUM_SEVERITY = Activator.getImageDescriptor("/icons/medium_untoggle.png")
			.createImage();

	public static final Image LOW_SEVERITY = Activator.getImageDescriptor("/icons/low_untoggle.png").createImage();

	public static final Image INFO_SEVERITY = Activator.getImageDescriptor("/icons/info_untoggle.png").createImage();
	
	
	public static final Image USER = Activator.getImageDescriptor("/icons/user.png").createImage();
	
	public static final Image CREATED_AT_IMAGE = Activator.getImageDescriptor("/icons/date.png").createImage();
	
	public static final Image COMMENT = Activator.getImageDescriptor("/icons/comment.png").createImage();

	private TreeViewer resultsTree;
	private ComboViewer scanIdComboViewer, projectComboViewer, branchComboViewer, triageSeverityComboViewew, triageStateComboViewer;
	private Text commentText;
	private DisplayModel rootModel;
	private String selectedSeverity, selectedState;
	private DisplayModel currentDisplayModel;
	private Button triageButton;
	private Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

	private boolean alreadyRunning = false;

	Font boldFont,titleFont;

	private Text attackVectorValueLinkText;

	private Composite resultViewComposite;
	private Composite attackVectorCompositePanel;
	private Composite openSettingsComposite;
	private CLabel titleLabel;

	private Label attackVectorLabel;
	private ToolBarActions toolBarActions;

	private EventBus pluginEventBus;

	private GlobalSettings globalSettings = new GlobalSettings();
		
	private String currentProjectId = PluginConstants.EMPTY_STRING;
	private String currentBranch =  PluginConstants.EMPTY_STRING;
	private String currentScanId =  PluginConstants.EMPTY_STRING;
	private static String currentScanIdFormmated =  PluginConstants.EMPTY_STRING;
	private List<String> currentBranches = new ArrayList<>();
		
	private boolean scansCleanedByProject = false; 
	private boolean firstTimeTriggered = false; 
	
	private Composite parent;
	private ScrolledComposite scrolledComposite;
	
	
	private boolean isPluginDraw = false;
	protected TabFolder tabFolder;
		
	public CheckmarxView() {
		super();

		rootModel = new DisplayModel.DisplayModelBuilder(PluginConstants.EMPTY_STRING).build();
		globalSettings.loadSettings();
		currentProjectId = globalSettings.getProjectId();
		currentBranch = globalSettings.getBranch();
		currentScanId = globalSettings.getScanId();
		PluginUtils.getEventBroker().subscribe(PluginConstants.TOPIC_APPLY_SETTINGS, this);
	}

	@Override
	public void dispose() {
		super.dispose();
		
		if(boldFont != null && !boldFont.isDisposed()) {
			boldFont.dispose();
		}
		
		if(titleFont != null && !titleFont.isDisposed()) {
			titleFont.dispose();
		}
		
		if(pluginEventBus != null) {
			pluginEventBus.unregister(this);
		}
		
		PluginUtils.getEventBroker().unsubscribe(this);
	}

	@Override
	public void createPartControl(Composite parent) {
		this.parent = parent;
		
		// Clear vulnerabilities from Problems View
		PluginUtils.clearVulnerabilitiesFromProblemsView();
				
		if(PluginUtils.areCredentialsDefined()) {
			drawPluginPanel();
		}else {
			drawMissingCredentialsPanel();
		}
	}
	
	
	/**
	 * Init git branch listener to update plugin to the same branch
	 */
	private void initGitBranchListener() {
		Repository.getGlobalListenerList().addRefsChangedListener(new RefsChangedListener() {
			@Override
			public void onRefsChanged(RefsChangedEvent arg) {
				try {
					
					// Trick to avoid wrong trigger on hover "switch to" menu in the first time
					if(!firstTimeTriggered) {
						firstTimeTriggered = true;
						return;
					}
					
					String gitBranch = arg.getRepository().getBranch();
					updatePluginBranchAndScans(gitBranch);
				} catch (IOException e) {
					CxLogger.error(PluginConstants.ERROR_GETTING_GIT_BRANCH, e);
				}
			}
		});
	}

	/**
	 * Update plugin with the same branch as the current workspace project's branch
	 * 
	 * @param gitBranch
	 */
	private void updatePluginBranchAndScans(String gitBranch) {
		boolean pluginBranchesContainsGitBranch = !currentBranches.isEmpty() && currentBranches.contains(gitBranch);
		
		if (!currentProjectId.isEmpty() &&  pluginBranchesContainsGitBranch) {
			disablePluginFields(true);

			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					setSelectionForBranchComboViewer(gitBranch, currentProjectId);
					setSelectionForScanIdComboViewer(PluginConstants.EMPTY_STRING, gitBranch);
					enablePluginFields(true);
				}
			});
		}
	}

	private void createContextMenu() {
		MenuManager menuManager = new MenuManager("#PopupMenu");
		menuManager.setRemoveAllWhenShown(true);
		menuManager.addMenuListener(CheckmarxView.this::fillContextMenu);
		Menu menu = menuManager.createContextMenu(resultsTree.getControl());
		resultsTree.getControl().setMenu(menu);

		getSite().registerContextMenu(menuManager, resultsTree);
	}

	private void fillContextMenu(IMenuManager manager) {
		Action openPreferencesPageAction = new ActionOpenPreferencesPage(rootModel, resultsTree, shell).createAction();
		manager.add(openPreferencesPageAction);
	}

	/**
	 * Creates the Checkmarx plugin tool bar with all actions
	 */
	private void createToolbar() {
		IActionBars actionBars = getViewSite().getActionBars();
		IToolBarManager toolBarManager = actionBars.getToolBarManager();

		pluginEventBus = new EventBus();
		pluginEventBus.register(this);

		toolBarActions = new ToolBarActions.ToolBarActionsBuilder().actionBars(actionBars).rootModel(rootModel)
				.resultsTree(resultsTree).pluginEventBus(pluginEventBus).build();

		for (Action action : toolBarActions.getToolBarActions()) {
			toolBarManager.add(action);

			// Add divider
			if (action.getId() != null && action.getId().equals(ActionName.INFO.name())) {
				toolBarManager.add(new Separator("\t"));
			}
		}
		
		if(currentScanId.isEmpty()) {
			toolBarActions.getAbortResultsAction().setEnabled(false);
			toolBarActions.getScanResultsAction().setEnabled(true);
			toolBarActions.getClearAndRefreshAction().setEnabled(true);
		}
		
		actionBars.updateActionBars();
	}

	@Override
	public void setFocus() {
		if(resultsTree != null) {
			resultsTree.getControl().setFocus();
		}
	}

	/**
	 * Draw Plugin
	 */
	private void drawPluginPanel() {	
		// Dispose missing credentials panel
		if(openSettingsComposite != null && !openSettingsComposite.isDisposed()) {
			openSettingsComposite.dispose();
		}
		
		// Define parent layout
		GridLayout parentLayout = new GridLayout();
		parentLayout.numColumns = 1;
		parentLayout.horizontalSpacing = 0;
		parentLayout.verticalSpacing = 0;
		parentLayout.marginHeight = 0;
		parentLayout.marginWidth = 0;
		parent.setLayout(parentLayout);

		// Create panel where projects, branches and scans comboboxes will be drawn
		createComboboxesPanel();

		// Create panel to view results
		createResultsPanel();
		
		// Create plugin toolBar
		createToolbar();
		
		// Create context menu
		createContextMenu();
		
		// Init git branch listener
		initGitBranchListener();

		// Refresh layout
		parent.layout();
		
		isPluginDraw = true;
	}
	
	/**
	 *  Panel where projects, branches and scans comboboxes will be drawn
	 */
	private void createComboboxesPanel() {
		Composite comboboxesComposite = new Composite(parent, SWT.NONE);
		GridLayout topLayout = new GridLayout();
		topLayout.numColumns = 3;
		comboboxesComposite.setLayout(topLayout);

		GridData topGridData = new GridData();
		topGridData.horizontalAlignment = GridData.FILL;
		topGridData.verticalAlignment = GridData.FILL;
		topGridData.grabExcessHorizontalSpace = true;
		comboboxesComposite.setLayoutData(topGridData);
		
		createProjectListComboBox(comboboxesComposite);
		createBranchComboBox(comboboxesComposite);
		createScanIdComboBox(comboboxesComposite);
		
		loadComboboxes();
	}
	
	private void loadComboboxes() {
		loadingProjects();
		loadingBranches();
		loadingScans();
		
		Display.getDefault().asyncExec(new Runnable() {
		    public void run() {
		    	List<Project> projectList = getProjects();
						    	
		    	projectComboViewer.setInput(projectList);
				
				if(currentProjectId.isEmpty() || projectList.isEmpty()) {
					PluginUtils.setTextForComboViewer(projectComboViewer, PROJECT_COMBO_VIEWER_TEXT);
					PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
					PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
					
					PluginUtils.enableComboViewer(projectComboViewer, true);
					PluginUtils.enableComboViewer(scanIdComboViewer, true);
					PluginUtils.enableComboViewer(branchComboViewer, false);
					
					return;
				}
				
				// Set project id
				String currentProjectName = getProjectFromId(projectList, currentProjectId);
				PluginUtils.setTextForComboViewer(projectComboViewer, currentProjectName);
				
				// Get branches for project id
				currentBranches = DataProvider.getInstance().getBranchesForProject(currentProjectId);
				branchComboViewer.setInput(currentBranches);
				
				PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
				PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
				
				if(!currentBranch.isEmpty()) {
					PluginUtils.setTextForComboViewer(branchComboViewer, currentBranch);
					
					List<Scan> scanList = DataProvider.getInstance().getScansForProject(currentBranch);
					scanIdComboViewer.setInput(scanList);
					
					PluginUtils.setTextForComboViewer(scanIdComboViewer, scanList.isEmpty() ? PluginConstants.COMBOBOX_SCAND_ID_NO_SCANS_AVAILABLE : SCAN_COMBO_VIEWER_TEXT);
					
					if(!currentScanId.isEmpty()) {
						String currentScanName = getScanNameFromId(scanList, currentScanId);
						currentScanIdFormmated = currentScanName;
						PluginUtils.setTextForComboViewer(scanIdComboViewer, currentScanName);

						Display.getDefault().asyncExec(new Runnable() {
						    public void run() {
						    	alreadyRunning = true;
						    	updateResultsTree(DataProvider.getInstance().getResultsForScanId(currentScanId));
						    }
						});
					}else {
						PluginUtils.enableComboViewer(projectComboViewer, true);
						PluginUtils.enableComboViewer(scanIdComboViewer, true);
						PluginUtils.enableComboViewer(branchComboViewer, true);
					}
				}else {
					PluginUtils.enableComboViewer(projectComboViewer, true);
					PluginUtils.enableComboViewer(scanIdComboViewer, true);
					PluginUtils.enableComboViewer(branchComboViewer, true);
				}
		    }
		});
	}
	
	/**
	 * Create results panel
	 */
	private void createResultsPanel() {
		Composite resultsComposite = new Composite(parent, SWT.BORDER);

		GridData bottomGridData = new GridData();
		bottomGridData.horizontalAlignment = GridData.FILL;
		bottomGridData.verticalAlignment = GridData.FILL;
		bottomGridData.grabExcessHorizontalSpace = true;
		bottomGridData.grabExcessVerticalSpace = true;

		resultsComposite.setLayout(new FillLayout(SWT.HORIZONTAL));
		resultsComposite.setLayoutData(bottomGridData);

		createTreeResultsPanel(resultsComposite);
		createResultViewPanel(resultsComposite);
		createResultVulnerabilitiesPanel(resultsComposite);
	}
	
	/**
	 * Create results' tree panel
	 * 
	 * @param resultsComposite
	 */
	private void createTreeResultsPanel(Composite resultsComposite) {
		Composite treeResultsComposite = new Composite(resultsComposite, SWT.BORDER);

		GridLayout treeResultsLayout = new GridLayout();
		treeResultsLayout.numColumns = 1;
		treeResultsLayout.marginWidth = 0;
		treeResultsLayout.marginHeight = 0;
		treeResultsComposite.setLayout(treeResultsLayout);

		GridData treeResultsLayoutData = new GridData();
		treeResultsLayoutData.horizontalAlignment = GridData.BEGINNING;
		treeResultsLayoutData.grabExcessVerticalSpace = true;
		treeResultsComposite.setLayoutData(treeResultsLayoutData);

		resultsTree = new TreeViewer(treeResultsComposite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		resultsTree.getTree().setHeaderVisible(false);
		resultsTree.getTree().setLinesVisible(true);
		resultsTree.setContentProvider(new TreeContentProvider());
		
		// configure column settings
		configureResultsTreeColumns();
		
		// define layout for the viewer
		GridData gridData = new GridData();
		gridData.verticalAlignment = GridData.FILL;
		gridData.horizontalSpan = 2;
		gridData.grabExcessHorizontalSpace = true;
		gridData.grabExcessVerticalSpace = true;
		gridData.horizontalAlignment = GridData.FILL;
		resultsTree.getControl().setLayoutData(gridData);
		resultsTree.setInput(rootModel);

		// Add selection change event for each result
		configureTreeItemSelectionChangeEvent(resultsTree);
		
		// Define an initial message in the tree
		drawResultsTreeInitialMessage();
	}
	
	/**
	 * Draw results' tree initial message
	 */
	private void drawResultsTreeInitialMessage() {
		boolean gettingResults = globalSettings.getProjectId() != null && !globalSettings.getProjectId().isEmpty()
				&& globalSettings.getScanId() != null && !globalSettings.getScanId().isEmpty();
		boolean noProjectsAvailable = projectComboViewer.getCombo().getText().equals(NO_PROJECTS_AVAILABLE);
		String message = gettingResults && !noProjectsAvailable
				? String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, globalSettings.getScanId())
				: PluginConstants.EMPTY_STRING;
		PluginUtils.showMessage(rootModel, resultsTree, message);
	}
	
	/**
	 * Create result's view panel
	 * 
	 * @param resultsComposite
	 */
	private void createResultViewPanel(Composite resultsComposite) {
		// Define bold font for labels
		Display display = parent.getShell().getDisplay();
		FontData systemFontData = display.getSystemFont().getFontData()[0];
		boldFont = new Font(display, systemFontData.getName(), systemFontData.getHeight(), SWT.BOLD);
		titleFont = new Font(display, systemFontData.getName(), systemFontData.getHeight() + 2, SWT.BOLD);

		resultViewComposite = new Composite(resultsComposite, SWT.BORDER);
		GridLayout gl_resultViewComposite = new GridLayout(1, false);
		gl_resultViewComposite.marginWidth = 0;
		gl_resultViewComposite.marginHeight = 0;
		gl_resultViewComposite.verticalSpacing = 0;
		resultViewComposite.setLayout(gl_resultViewComposite);

		titleLabel = new CLabel(resultViewComposite, SWT.NONE);
		titleLabel.setFont(titleFont);
		titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		
		Composite triageView = new Composite(resultViewComposite,SWT.NONE);
		//triageView.setLayout(new GridLayout(3, false)); //columneEqualWidth = true
		GridLayout gl_triageView = new GridLayout(3,false);
		gl_triageView.marginHeight = 10;
		triageView.setLayout(gl_triageView);
		triageView.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		
		triageSeverityComboViewew = new ComboViewer(triageView, SWT.READ_ONLY);
		Combo combo_1 = triageSeverityComboViewew.getCombo();
		GridData gd_combo_1 = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1); 
		gd_combo_1.widthHint = SWT.DEFAULT;  
		combo_1.setLayoutData(gd_combo_1);
		
		triageStateComboViewer = new ComboViewer(triageView, SWT.READ_ONLY);
		Combo combo_2 = triageStateComboViewer.getCombo();
		GridData gd_combo_2 = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		gd_combo_2.widthHint = 180;
		combo_2.setLayoutData(gd_combo_2);
		
		triageButton = new Button(triageView, SWT.FLAT | SWT.CENTER);
		triageButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 1, 1));
		triageButton.setText(PluginConstants.BTN_UPDATE);
		
		commentText = new Text(triageView,SWT.BORDER);
		commentText.setText(PluginConstants.DEFAULT_COMMENT_TXT);
		GridData commentData = new GridData(SWT.FILL,SWT.FILL,true,true,3,1);
		commentText.setEnabled(true);
		commentText.setLayoutData(commentData);
		
		commentText.addListener(SWT.FocusIn, new Listener() {
		      public void handleEvent(Event e) {
		        commentText.setText("");
		        resultViewComposite.layout();
		      }
		    });
		
		commentText.addListener(SWT.FocusOut, new Listener() {
		      public void handleEvent(Event e) {
		    	  Text textReceived = (Text) e.widget;
		    	  if(textReceived.getText() == null || textReceived.getText() =="") {
		    		  commentText.setText(PluginConstants.DEFAULT_COMMENT_TXT);
		    		  resultViewComposite.layout();
		    	  }
		      }
		    });

		scrolledComposite = new ScrolledComposite(resultViewComposite, SWT.H_SCROLL| SWT.V_SCROLL);		
	    scrolledComposite.setExpandHorizontal(true);
	    scrolledComposite.setExpandVertical(true);
		scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		
		resultViewComposite.setVisible(false);
	}
	
	/**
	 * Create result's vulnerabilities panel
	 * 
	 * @param resultsComposite
	 */
	private void createResultVulnerabilitiesPanel(Composite resultsComposite) {
		attackVectorCompositePanel = new Composite(resultsComposite, SWT.BORDER);

		GridData attackVectorCompositePanelGridData = new GridData();
		attackVectorCompositePanelGridData.horizontalAlignment = GridData.END;
		attackVectorCompositePanelGridData.grabExcessHorizontalSpace = true;
		attackVectorCompositePanelGridData.grabExcessVerticalSpace = true;

		attackVectorCompositePanel.setLayoutData(attackVectorCompositePanelGridData);
		attackVectorCompositePanel.setLayout(new RowLayout(SWT.VERTICAL));

		attackVectorLabel = new Label(attackVectorCompositePanel, SWT.NONE);
		attackVectorLabel.setFont(boldFont);

		attackVectorCompositePanel.setVisible(false);
	}
	
	/**
	 * Draw panel when Checkmarx credentials are not defined
	 */
	private void drawMissingCredentialsPanel() {
		openSettingsComposite = new Composite(parent, SWT.NONE);

		openSettingsComposite.setLayout(new GridLayout(1, true));

		final Label hidden = new Label(openSettingsComposite, SWT.NONE);
		hidden.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
		hidden.setImage(CHECKMARX_OPEN_SETTINGS_LOGO);
		hidden.setVisible(false);
		
		final Label cxLogo = new Label(openSettingsComposite, SWT.NONE);
		cxLogo.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
		cxLogo.setImage(CHECKMARX_OPEN_SETTINGS_LOGO);
		
		final Button btn = new Button(openSettingsComposite, SWT.NONE);
		btn.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
		btn.setText(PluginConstants.BTN_OPEN_SETTINGS);

		btn.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(shell, "com.checkmarx.eclipse.properties.preferencespage", null, null);

				if (pref != null) {
					pref.open();
				}
			}
		});
	}

	private void createProjectListComboBox(Composite parent) {
		projectComboViewer = new ComboViewer(parent, SWT.DROP_DOWN);
		projectComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		projectComboViewer.setInput(new ArrayList<>());
		
		GridData gridData = new GridData();
		gridData.widthHint = 400;
		projectComboViewer.getCombo().setLayoutData(gridData);
		
		projectComboViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof Project) {
					Project project = (Project) element;
					return project.getName();
				}
				return super.getText(element);
			}
		});

		projectComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();

				if (selection.size() > 0) {

					Project selectedProject = ((Project) selection.getFirstElement());

					// Avoid non-sense trigger changed when opening the combo
					if(selectedProject.getID().equals(currentProjectId)) {
						CxLogger.info(PluginConstants.INFO_CHANGE_PROJECT_EVENT_NOT_TRIGGERED);

						return;
					}		
          
					onProjectChangePluginLoading(selectedProject.getID());
					
					Display.getDefault().asyncExec(new Runnable() {
					    public void run() {
					    	currentBranches = DataProvider.getInstance().getBranchesForProject(selectedProject.getID());
							
							branchComboViewer.setInput(currentBranches);
							PluginUtils.setTextForComboViewer(branchComboViewer, currentBranches.isEmpty() ? NO_BRANCHES_AVAILABLE : BRANCH_COMBO_VIEWER_TEXT);
							
							PluginUtils.enableComboViewer(branchComboViewer, true);
							PluginUtils.enableComboViewer(scanIdComboViewer, true);
							PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
							toolBarActions.getScanResultsAction().setEnabled(true);
							toolBarActions.getClearAndRefreshAction().setEnabled(true);
							
							PluginUtils.enableComboViewer(branchComboViewer, true);
							PluginUtils.enableComboViewer(scanIdComboViewer, true);
							PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
					    }
					});
				}
			}
		});
	}

	/**
	 * Update state variables and make plugin fields loading when project changes
	 * 
	 * @param projectId
	 */
	private void onProjectChangePluginLoading(String projectId) {
		// Update state variables
		currentProjectId = projectId;
		scansCleanedByProject = true; // used to avoid non-sense trigger change in scans combobox
		DataProvider.getInstance().setCurrentResults(null);
		scanIdComboViewer.setInput(Collections.emptyList());
		
		// Store project id in preferences
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_PROJECT_ID, projectId);
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, PluginConstants.EMPTY_STRING);
		
		// Make plugin loading
		loadingBranches();
		PluginUtils.enableComboViewer(scanIdComboViewer, false);
		PluginUtils.showMessage(rootModel, resultsTree, PluginConstants.EMPTY_STRING);
		PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
		toolBarActions.getToolBarActions().forEach(action -> action.setEnabled(false));
		// Hide center and right panels
		resultViewComposite.setVisible(false);
		attackVectorCompositePanel.setVisible(false);
		// Clear vulnerabilities from Problems View
		PluginUtils.clearVulnerabilitiesFromProblemsView();
	}
	
	/**
	 * Get project name from project Id
	 * 
	 * @param projects
	 * @param projectId
	 * @return
	 */
	private String getProjectFromId(List<Project> projects, String projectId) {
		if (projects == null || projects.isEmpty()) {
			return NO_PROJECTS_AVAILABLE;
		}

		Optional<Project> project = projects.stream().filter(p -> p.getID().equals(projectId)).findFirst();

		return project.isPresent() ? project.get().getName() : PROJECT_COMBO_VIEWER_TEXT;
	}

	private void createBranchComboBox(Composite parent) {
		branchComboViewer = new ComboViewer(parent, SWT.DROP_DOWN);
		branchComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		branchComboViewer.setInput(new ArrayList<>());
		
		GridData gridData = new GridData();
		gridData.widthHint = 150;
		branchComboViewer.getCombo().setLayoutData(gridData);
		
		branchComboViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return element.toString();
			}
		});
				
		branchComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();

				if (selection.size() > 0) {
					String selectedBranch = ((String) selection.getFirstElement());

					// Avoid non-sense trigger changed when opening the combo
					if(selectedBranch.equals(currentBranch) && !scansCleanedByProject) {
						CxLogger.info(PluginConstants.INFO_CHANGE_BRANCH_EVENT_NOT_TRIGGERED);

						return;
					}
					
					onBranchChangePluginLoading(selectedBranch);
					
					Display.getDefault().asyncExec(new Runnable() {
					    public void run() {
					    	List<Scan> scanList = DataProvider.getInstance().getScansForProject(selectedBranch);
							scanIdComboViewer.setInput(scanList);
							PluginUtils.setTextForComboViewer(scanIdComboViewer, scanList.isEmpty() ? PluginConstants.COMBOBOX_SCAND_ID_NO_SCANS_AVAILABLE : SCAN_COMBO_VIEWER_TEXT);
							
							PluginUtils.enableComboViewer(projectComboViewer, true);
							PluginUtils.enableComboViewer(scanIdComboViewer, true);
							PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
							toolBarActions.getScanResultsAction().setEnabled(true);
							toolBarActions.getClearAndRefreshAction().setEnabled(true);
					    }
					});
					
					PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
				}
			}
		});
	}
	
	/**
	 * Update state variables and make plugin fields loading when branch changes
	 * 
	 * @param projectId
	 */
	private void onBranchChangePluginLoading(String branch) {
		// Update state variables
		currentBranch = branch;
		currentScanId = PluginConstants.EMPTY_STRING;
		scansCleanedByProject = false;
		DataProvider.getInstance().setCurrentResults(null);
		
		// Update preferences
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_BRANCH, branch);
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, PluginConstants.EMPTY_STRING);
		
		// Make plugin loading
		PluginUtils.enableComboViewer(projectComboViewer, false);
		loadingScans();
		PluginUtils.showMessage(rootModel, resultsTree, PluginConstants.EMPTY_STRING);
		toolBarActions.getToolBarActions().forEach(action -> action.setEnabled(false));
		// Hide center and right panels
		resultViewComposite.setVisible(false);
		attackVectorCompositePanel.setVisible(false);
		// Clear vulnerabilities from Problems View
		PluginUtils.clearVulnerabilitiesFromProblemsView();
	}
	
	private void createScanIdComboBox(Composite parent){
		scanIdComboViewer = new ComboViewer(parent, SWT.DROP_DOWN | SWT.SIMPLE);
		scanIdComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		scanIdComboViewer.setInput(new ArrayList<>());
		
		GridData gridData = new GridData();
		gridData.widthHint = 450;
		scanIdComboViewer.getCombo().setLayoutData(gridData);
		
		scanIdComboViewer.getCombo().addListener(SWT.DefaultSelection, new Listener() {
			public void handleEvent(Event event) {
				toolBarActions.getScanResultsAction().run();
			}
		});
		

		scanIdComboViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof Scan) {
					Scan scan = (Scan) element;
					return formatScanLabel(scan);
				}
				return super.getText(element);
			}
		});
		
		scanIdComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();

				if (selection.size() > 0) {

					Scan selectedScan = ((Scan) selection.getFirstElement());

					// Avoid non-sense trigger changed when opening the combo
					if(selectedScan.getID().equals(currentScanId) || alreadyRunning) {
						CxLogger.info(String.format(PluginConstants.INFO_CHANGE_SCAN_EVENT_NOT_TRIGGERED, alreadyRunning, selectedScan.getID().equals(currentScanId)));

						return;
					}
					
					onScanChangePluginLoading(selectedScan.getID());

					Display.getDefault().asyncExec(new Runnable() {
					    public void run() {
					    	alreadyRunning = true;
							updateResultsTree(DataProvider.getInstance().getResultsForScanId(selectedScan.getID()));
					    }
					});
				}
			}
		});
	}
	
	/**
	 * Update state variables and make plugin fields loading when scan changes
	 * 
	 * @param projectId
	 */
	private void onScanChangePluginLoading(String scan) {
		// Update state variables
		currentScanId = scan;
		currentScanIdFormmated = scanIdComboViewer.getCombo().getText();
		DataProvider.getInstance().setCurrentResults(null);
		
		// Store project id in preferences
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, scan);

		// Make plugin loading
		PluginUtils.showMessage(rootModel, resultsTree, String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, scan));
		PluginUtils.enableComboViewer(projectComboViewer, false);
		PluginUtils.enableComboViewer(branchComboViewer, false);
		toolBarActions.getToolBarActions().forEach(action -> action.setEnabled(action.getId().equals(ActionName.ABORT_RESULTS.name())));
		// Hide center and right panels
		resultViewComposite.setVisible(false);
		attackVectorCompositePanel.setVisible(false);
		// Clear vulnerabilities from Problems View
		PluginUtils.clearVulnerabilitiesFromProblemsView();
	}
	
	/**
	 * Get formated scan name from scan id
	 * 
	 * @param scans
	 * @param scanId
	 * @return
	 */
	private String getScanNameFromId(List<Scan> scans, String scanId) {
		if(scans.isEmpty()) {
			return PluginConstants.COMBOBOX_SCAND_ID_NO_SCANS_AVAILABLE;
		}
		
		Optional<Scan> scan = scans.stream().filter(s -> s.getID().equals(scanId)).findFirst();

		return scan.isPresent() ? formatScanLabel(scan.get()) : SCAN_COMBO_VIEWER_TEXT;
	}

	/**
	 * Formats scan's displayed label
	 * 
	 * @param scan
	 * @return
	 */
	private static String formatScanLabel(Scan scan) {
		String updatedAtDate = PluginUtils.convertStringTimeStamp(scan.getUpdatedAt());
		       
        return String.format(FORMATTED_SCAN_LABEL, scan.getID(), updatedAtDate);

	}

	/**
	 * Reverse selection - Populate project combobox and select a project id based
	 * on the chosen scan id
	 */
	private void setSelectionForProjectComboViewer() {		
		String scanId = scanIdComboViewer.getCombo().getText();
		
		if(currentScanId.equals(scanId)) {
			PluginUtils.setTextForComboViewer(scanIdComboViewer, currentScanIdFormmated);
			CxLogger.info(String.format(PluginConstants.INFO_RESULTS_ALREADY_RETRIEVED, scanId));
			return;
		}
		
		if (!PluginUtils.validateScanIdFormat(scanId)) {
			PluginUtils.showMessage(rootModel, resultsTree, PluginConstants.TREE_INVALID_SCAN_ID_FORMAT);
			return;
		}
		
		// Disable all tool bar actions except the clear and refresh action
		toolBarActions.getToolBarActions().forEach(action -> action.setEnabled(action.getId().equals(ActionName.ABORT_RESULTS.name())));
		PluginUtils.showMessage(rootModel, resultsTree, String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, scanId));
		loadingProjects();
		loadingBranches();
		resultViewComposite.setVisible(false);
		attackVectorCompositePanel.setVisible(false);		
		
		Display.getDefault().asyncExec(new Runnable() {
		    public void run() {
		    	Scan scan;
				
		    	try {
					scan = DataProvider.getInstance().getScanInformation(scanId);
				} catch (Exception e) {
					String errorMessage = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : e.getMessage();
					PluginUtils.showMessage(rootModel, resultsTree, errorMessage);
					
					PluginUtils.setTextForComboViewer(projectComboViewer, PROJECT_COMBO_VIEWER_TEXT);
					PluginUtils.enableComboViewer(projectComboViewer, true);
					
					PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
					PluginUtils.enableComboViewer(branchComboViewer, false);
					
					return;
				}
		    	
				String projectId = scan.getProjectID();
				
		    	List<Project> projectList = getProjects();
		    	
		    	projectComboViewer.setInput(projectList);
		    	
		    	if(projectList.isEmpty()) return;
				
				String projectName = getProjectFromId(projectList, projectId);
				PluginUtils.setTextForComboViewer(projectComboViewer, projectName);

				currentProjectId = projectId;
				GlobalSettings.storeInPreferences(GlobalSettings.PARAM_PROJECT_ID, currentProjectId);

				setSelectionForBranchComboViewer(scan.getBranch(), projectId);
				setSelectionForScanIdComboViewer(scan.getID(), scan.getBranch());
		    }
		});
	}
	
	/**
	 * Reverse selection - Populate branch combobox and select a branch based on the
	 * chosen scan id
	 */
	private void setSelectionForBranchComboViewer(String branchName, String projectId) {
		currentBranches = DataProvider.getInstance().getBranchesForProject(projectId);

		if (currentBranches != null) {
			branchComboViewer.setInput(currentBranches);
			
			String currentBranchName =  currentBranches.stream().filter(branch -> branchName.equals(branch)).findAny().orElse(null);

			PluginUtils.setTextForComboViewer(branchComboViewer, currentBranchName);

			currentBranch = currentBranchName;
			GlobalSettings.storeInPreferences(GlobalSettings.PARAM_BRANCH, currentBranch);
		} else {
			PluginUtils.setTextForComboViewer(branchComboViewer, NO_BRANCHES_AVAILABLE);
		}
	}

	/**
	 * Reverse selection - Populate scan id combobox and format the chosen scan id
	 */
	private void setSelectionForScanIdComboViewer(String scanId, String branch) {
		List<Scan> scanList = DataProvider.getInstance().getScansForProject(branch);
		
		scanIdComboViewer.setInput(scanList);
				
		if(scanList.isEmpty()) {
			if(Strings.isNullOrEmpty(scanId)) {
				PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_NO_SCANS_AVAILABLE);
			}else {
				scanIdComboViewer.setSelection(new StructuredSelection(scanId));
			}
			
			currentScanId = scanId;
			GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, currentScanId);
		
			return;
		}
		
		if(Strings.isNullOrEmpty(scanId)) {
			PluginUtils.setTextForComboViewer(scanIdComboViewer, SCAN_COMBO_VIEWER_TEXT);
			GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, PluginConstants.EMPTY_STRING);
			return;
		}
		
		Scan currentScan = scanList.stream().filter(scan -> scanId.equals(scan.getID())).findAny().orElse(null);

		scanIdComboViewer.setSelection(new StructuredSelection(currentScan != null ? currentScan : PluginConstants.EMPTY_STRING));
	}

	private void configureTreeItemSelectionChangeEvent(TreeViewer viewer) {
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {

			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				// if the selection is empty clear the label
				if (event.getSelection().isEmpty()) {
					return;
				}
				if (event.getSelection() instanceof IStructuredSelection) {
					IStructuredSelection selection = (IStructuredSelection) event.getSelection();
					DisplayModel selectedItem = (DisplayModel) selection.getFirstElement();
					
					if(selectedItem == null || selectedItem.getType() == null) {
						return;
					}
					
					if (selectedItem.getSeverity() != null) {
						titleLabel.setImage(findSeverityImage(selectedItem));
						titleLabel.setText(selectedItem.getName());
					}
					
					if(selectedItem.getResult() != null && selectedItem.getResult().getSimilarityId() != null) {
						createTriageSeverityAndStateCombos(selectedItem);			
						populateTriageChanges(selectedItem);

						}
					
					resultViewComposite.setVisible(true);
					resultViewComposite.layout();
					
					updateAttackVectorForSelectedTreeItem(selectedItem);
				}
			}
		});
	}
	
	/**
	 * Create combo viewers for severity and state
	 * 
	 * @param selectedItem
	 */
	private void createTriageSeverityAndStateCombos(DisplayModel selectedItem) {
		currentDisplayModel = selectedItem;
		
		String currentSeverity = selectedItem.getSeverity();
		selectedSeverity = selectedItem.getSeverity();
		String[] severity = {"HIGH","MEDIUM","LOW","INFO"};
		
		triageSeverityComboViewew.setContentProvider(ArrayContentProvider.getInstance());
		triageSeverityComboViewew.setInput(severity);
		PluginUtils.setTextForComboViewer(triageSeverityComboViewew, currentSeverity);
		
		triageSeverityComboViewew.addSelectionChangedListener(new ISelectionChangedListener() {

			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				if (selection.size() > 0) {
					selectedSeverity = ((String) selection.getFirstElement());
				}
			}
		});
		
		String currentState = selectedItem.getState();
		selectedState = selectedItem.getResult().getState();
		String[] state = {"TO_VERIFY", "NOT_EXPLOITABLE", "PROPOSED_NOT_EXPLOITABLE", "CONFIRMED", "URGENT"};
		
		triageStateComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		triageStateComboViewer.setInput(state);
		PluginUtils.setTextForComboViewer(triageStateComboViewer, currentState);
		
		triageStateComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				if (selection.size() > 0) {
					selectedState = ((String) selection.getFirstElement());
				}
			}
		});
		
		triageButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				// Call triage update. triageButton.isEnabled() is used to avoid nonsense randomly clicks triggered
				if(selectedSeverity != null && selectedState != null && triageButton.isEnabled()) {
					UUID projectId = UUID.fromString(currentProjectId);
					String similarityId = currentDisplayModel.getResult().getSimilarityId();
					String engineType = currentDisplayModel.getResult().getType();
					triageButton.setEnabled(false);
					triageButton.setText(PluginConstants.BTN_LOADING);
					commentText.setEnabled(false);
					commentText.setEditable(false);
					
					
					Display.getDefault().asyncExec(new Runnable() {
					    public void run() {	
					    	
					    	String comment = commentText.getText() != null && !commentText.getText().equalsIgnoreCase("Enter comment") ? commentText.getText() : ""; 
					    	boolean successfullyUpdate = DataProvider.getInstance().triageUpdate(projectId, similarityId, engineType, selectedState, comment, selectedSeverity);
							
					    	if(successfullyUpdate) {
					    		currentDisplayModel.setSeverity(selectedSeverity);
					    		currentDisplayModel.setState(selectedState);
								titleLabel.setImage(findSeverityImage(currentDisplayModel));
								titleLabel.setText(currentDisplayModel.getName());
								populateTriageChanges(currentDisplayModel);
								
								alreadyRunning = true;
								updateResultsTree(DataProvider.getInstance().sortResults());
								triageButton.setEnabled(true);
								triageButton.setText(PluginConstants.BTN_UPDATE);
								commentText.setEnabled(true);
								commentText.setText(PluginConstants.DEFAULT_COMMENT_TXT);
								commentText.setEditable(true);
								
								// TODO: open the selectedItem in the tree - check hidden filter scenario
								
								
					    	}else {
					    		// TODO: inform the user that update failed?
					    	}
					    }
					});
					
					resultViewComposite.layout();
				}
			}
		});
	}
	
	/**
	 * Populate list of changes for the selected vulnerability
	 * 
	 * @param selectedItem
	 */
	private void populateTriageChanges(DisplayModel selectedItem) {

		populateLoadingScreen();

		Display.getDefault().asyncExec(new Runnable() {
			public void run() {

				createDescriptionInfo(selectedItem);
				
				TabItem tbtmChanges = new TabItem(tabFolder, SWT.NONE);
				tbtmChanges.setText("Changes");
				
				Composite changesComposite = new Composite(tabFolder, SWT.NONE);
				GridLayout gl_detailsComposite = new GridLayout(1, false);
				gl_detailsComposite.marginWidth = 0;
				gl_detailsComposite.marginHeight = 0;
				changesComposite.setLayout(gl_detailsComposite);
				tbtmChanges.setControl(changesComposite);
				
				
				List<Predicate> triageDetails = getTriageInfo(UUID.fromString(currentProjectId), selectedItem.getResult().getSimilarityId(), selectedItem.getResult().getType());
				if(triageDetails.size() >0) {
					// populate changes composite based on the predicate
					for(Predicate detail : triageDetails) {
						// populate individual triage node details
						populateIndChangesData(detail,changesComposite);
					}
				}
				
				else {
					populateNoChangesData(changesComposite,tbtmChanges);
					
				}
				
				scrolledComposite.setContent(tabFolder);
				scrolledComposite.setMinSize(changesComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
			}
			
			

			private void populateNoChangesData(Composite changesComposite, TabItem tbtmChanges) {
				changesComposite = new Composite(tabFolder, SWT.NONE);
				changesComposite.setLayout(new GridLayout(1, false));
				tbtmChanges.setControl(changesComposite);
				changesComposite.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false));
				
				CLabel noChange = new CLabel(changesComposite,SWT.NONE);
				noChange.setText(PluginConstants.NO_CHANGES);
				noChange.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false, 1, 1));
				
			}



			private void populateIndChangesData(Predicate detail, Composite changesComposite) {
				CLabel createdBy = new CLabel(changesComposite,SWT.NONE);
				createdBy.setImage(USER);
				String user = detail.getCreatedBy();
				if(detail.getCreatedAt() != null) {
				String time = PluginUtils.convertStringTimeStamp(detail.getCreatedAt());
				createdBy.setText(user + " | " + time.replace("|", ""));
				}
				else {
				createdBy.setText(user);	
				}
				createdBy.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

				
				CLabel severity = new CLabel(changesComposite,SWT.NONE);
				severity.setImage(findSeverityImageString(detail.getSeverity()));
				severity.setText(detail.getSeverity());
				severity.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
				
				if(detail.getComment() != null && detail.getComment() != "") {
					CLabel comment = new CLabel(changesComposite,SWT.NONE);
					comment.setImage(COMMENT);
					comment.setText(detail.getComment());
					comment.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
				}
				
				Label label = new Label(changesComposite, SWT.SEPARATOR | SWT.HORIZONTAL);
				label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
				
			}



			private void createDescriptionInfo(DisplayModel selectedItem) {

				tabFolder = new TabFolder(scrolledComposite, SWT.NONE);
				TabItem tbtmDescription = new TabItem(tabFolder, SWT.NONE);
				tbtmDescription.setText("Description");
				
				
				Composite detailsComposite = new Composite(tabFolder, SWT.NONE);
				GridLayout gl_detailsComposite = new GridLayout(1, false);
				gl_detailsComposite.marginWidth = 0;
				gl_detailsComposite.marginHeight = 0;
				detailsComposite.setLayout(gl_detailsComposite);
				tbtmDescription.setControl(detailsComposite);

				Text descriptionTxt = new Text(detailsComposite, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI);
				descriptionTxt.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				descriptionTxt.setText(selectedItem.getResult().getDescription() != null ? selectedItem.getResult().getDescription() : "No data");
				
			}



			private Image findSeverityImageString(String severity) {
				if (severity == null)
					return null;

				if (severity.equalsIgnoreCase(Severity.CRITICAL.name()))
					return CRITICAL_SEVERITY;
				if (severity.equalsIgnoreCase(Severity.HIGH.name()))
					return HIGH_SEVERITY;
				if (severity.equalsIgnoreCase(Severity.MEDIUM.name()))
					return MEDIUM_SEVERITY;
				if (severity.equalsIgnoreCase(Severity.LOW.name()))
					return LOW_SEVERITY;
				if (severity.equalsIgnoreCase(Severity.INFO.name()))
					return INFO_SEVERITY;

				return null;
			}
		});
		
		
		
		
	}

	private void populateLoadingScreen() {
		Composite loadingScreen = new Composite(scrolledComposite, SWT.NONE);
		loadingScreen.setLayout(new GridLayout(1, false));
		loadingScreen.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false));
		
		CLabel loadingLabel = new CLabel(loadingScreen,SWT.NONE);
		loadingLabel.setText(PluginConstants.LOADING_CHANGES);
		loadingLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		commentText.setText(PluginConstants.DEFAULT_COMMENT_TXT);
		
		scrolledComposite.setContent(loadingScreen);
		//scrolledComposite.setMinSize(loadingScreen.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}

	/**
	 * Get triage information
	 * 
	 * @param projectID
	 * @param similarityId
	 * @param scanType
	 * @return
	 */
	private List<Predicate> getTriageInfo(UUID projectID,String similarityId,String scanType) {
		List<Predicate> triageList = new ArrayList<Predicate>();
		
		try {
			triageList = DataProvider.getInstance().getTriageShow(projectID, similarityId, scanType);
		} catch (Exception e) {
			String errorMessage = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : e.getMessage();
			PluginUtils.showMessage(rootModel, resultsTree, errorMessage);
		}
		
		return triageList;
	}

	private void updateAttackVectorForSelectedTreeItem(DisplayModel selectedItem) {
		clearAttackVectorSection(attackVectorCompositePanel);
		attackVectorCompositePanel.setVisible(true);

		if (selectedItem.getType().equalsIgnoreCase(PluginConstants.SCA_DEPENDENCY)) {
			attackVectorLabel.setText("Package Data: ");
			List<PackageData> packageDataList = selectedItem.getResult().getData().getPackageData();

			if (packageDataList != null && !packageDataList.isEmpty()) {

				for (PackageData packageDataItem : packageDataList) {
					Text packageDataTypeLabel = new Text(attackVectorCompositePanel, SWT.READ_ONLY);
					packageDataTypeLabel.setFont(boldFont);
					packageDataTypeLabel.setText(packageDataItem.getType());

					Link packageDataLink = new Link(attackVectorCompositePanel, SWT.NONE);
					packageDataLink.setText("<a>" + packageDataItem.getUrl() + "</a>");
				}

				attackVectorCompositePanel.layout();

			} else {
				if (attackVectorValueLinkText != null) {
					attackVectorValueLinkText.setText("Not Available.");
				}
			}

		}

		if (selectedItem.getType().equalsIgnoreCase(PluginConstants.KICS_INFRASTRUCTURE)) {
			attackVectorLabel.setText("Location: ");

			Link fileNameValueLinkText = new Link(attackVectorCompositePanel, SWT.NONE);
			String text = "<a>" + selectedItem.getResult().getData().getFileName() + "["
					+ selectedItem.getResult().getData().getLine() + "]" + "</a>";
			fileNameValueLinkText.setText(text);
			fileNameValueLinkText.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event event) {
					openTheSelectedFile(selectedItem.getResult().getData().getFileName(),
							selectedItem.getResult().getData().getLine(), null);
				}
			});

			attackVectorCompositePanel.layout();
		}

		if (selectedItem.getType().equalsIgnoreCase(PluginConstants.SAST)) {
			attackVectorLabel.setText("Attack Vector: ");

			String queryName = selectedItem.getResult().getData().getQueryName();
			String groupName = selectedItem.getResult().getData().getGroup();

			List<Node> nodesList = selectedItem.getResult().getData().getNodes();
			if (nodesList != null && nodesList.size() > 0) {
				for (Node node : nodesList) {

					String nodeName = node.getName();
					String markerDescription = groupName + "_" + queryName + "_" + nodeName;

					Link attackVectorValueLinkText = new Link(attackVectorCompositePanel, SWT.NONE);
					String text = "<a>" + node.getFileName() + "[" + node.getLine() + "," + node.getColumn() + "]"
							+ "</a>";
					attackVectorValueLinkText.setText(text);
					attackVectorValueLinkText.addListener(SWT.Selection, new Listener() {
						public void handleEvent(Event event) {
							openTheSelectedFile(node.getFileName(), node.getLine(), markerDescription);
						}
					});

					attackVectorCompositePanel.layout();
				}
			} else {
				if (attackVectorValueLinkText != null) {
					attackVectorValueLinkText.setText("Not Available.");
				}
			}
		}
	}

	private void clearAttackVectorSection(Composite attackVectorCompositePanel) {
		for (Control child : attackVectorCompositePanel.getChildren()) {
			if (!(child instanceof Label))
				child.dispose();
		}
	}

	private void openTheSelectedFile(String fileName, Integer lineNumber, String markerDescription) {
		Path filePath = new Path(fileName);
		List<IFile> filesFound = findFileInWorkspace(filePath.lastSegment());

		for (IFile file : filesFound) {
			Path fullPath = (Path) file.getFullPath();
			Path absolutePathOfFoundFile = (Path) fullPath.removeFirstSegments(1).makeAbsolute();

			if (absolutePathOfFoundFile.equals(filePath)) {
				try {
					IMarker fileMarker = file.createMarker(IMarker.TEXT);
					fileMarker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
					IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
					IDE.openEditor(page, fileMarker);
				} catch (CoreException e) {
					CxLogger.error(String.format(PluginConstants.ERROR_OPENING_FILE, e.getMessage()), e);
				}
			}
		}
	}

	private List<IFile> findFileInWorkspace(final String fileName) {
		final List<IFile> foundFiles = new ArrayList<IFile>();
		try {
			// visiting only resources proxy because we obtain the resource only when
			// matching name, thus the workspace traversal is much faster
			ResourcesPlugin.getWorkspace().getRoot().accept(new IResourceProxyVisitor() {
				@Override
				public boolean visit(IResourceProxy resourceProxy) throws CoreException {
					if (resourceProxy.getType() == IResource.FILE) {
						String resourceName = resourceProxy.getName();
						if (resourceName.equals(fileName)) {
							IFile foundFile = (IFile) resourceProxy.requestResource();
							foundFiles.add(foundFile);
						}
					}
					return true;
				}
			}, IResource.NONE);
		} catch (Exception e) {
			CxLogger.error(String.format(PluginConstants.ERROR_FINDING_FILE, e.getMessage()), e);
		}
		return foundFiles;
	}

	/**
	 * Define a configuration for tree columns
	 */
	private void configureResultsTreeColumns() {
		ColumnViewerToolTipSupport.enableFor(resultsTree);
		
		final TreeViewerColumn viewerColumn = new TreeViewerColumn(resultsTree, SWT.NONE);
		
		final TreeColumn column = viewerColumn.getColumn();
		column.setWidth(500);
		column.setResizable(true);
		column.setMoveable(false);
				
		ColumnProvider label = new ColumnProvider(this::findSeverityImage, model -> model.name);
		viewerColumn.setLabelProvider(label);
		
		getSite().setSelectionProvider(resultsTree);
	}

	private Image findSeverityImage(DisplayModel model) {
		String severity = model.severity;
		
		if (severity == null)
			return null;

		if (severity.equalsIgnoreCase(Severity.CRITICAL.name()))
			return CRITICAL_SEVERITY;
		if (severity.equalsIgnoreCase(Severity.HIGH.name()))
			return HIGH_SEVERITY;
		if (severity.equalsIgnoreCase(Severity.MEDIUM.name()))
			return MEDIUM_SEVERITY;
		if (severity.equalsIgnoreCase(Severity.LOW.name()))
			return LOW_SEVERITY;
		if (severity.equalsIgnoreCase(Severity.INFO.name()))
			return INFO_SEVERITY;

		return null;
	}

	@Subscribe
	private void listener(PluginListenerDefinition definition) {
		switch (definition.getListenerType()) {
		case FILTER_CHANGED:
		case GET_RESULTS:
			updateResultsTree(definition.getResutls());
			break;
		case CLEAN_AND_REFRESH:
			clearAndRefreshPlugin();
			break;
		case REVERSE_CALL:
			setSelectionForProjectComboViewer();
			break;
		default:
			break;
		}
	}

	/**
	 * Update results tree
	 * 
	 * @param results
	 */
	private void updateResultsTree(List<DisplayModel> results) {
		rootModel.children.clear();
		rootModel.children.addAll(results);
		resultsTree.getTree().getDisplay().asyncExec(() -> resultsTree.refresh());
		toolBarActions.getScanResultsAction().setEnabled(true);
		toolBarActions.getAbortResultsAction().setEnabled(false);
		toolBarActions.getClearAndRefreshAction().setEnabled(true);
		PluginUtils.enableComboViewer(projectComboViewer, true);
		PluginUtils.enableComboViewer(branchComboViewer, !currentProjectId.isEmpty());
		PluginUtils.enableComboViewer(scanIdComboViewer, true);
		alreadyRunning = false;

		if(results.isEmpty()) {
			PluginUtils.showMessage(rootModel, resultsTree, PluginConstants.TREE_NO_RESULTS);
		}
		
		PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
	}

	/**
	 * Clear all plugin fields and reload projects
	 */
	private void clearAndRefreshPlugin() {
		// Disable comboboxes and update placeholders
		PluginUtils.showMessage(rootModel, resultsTree, PluginConstants.EMPTY_STRING);
		PluginUtils.enableComboViewer(projectComboViewer, false);
		PluginUtils.setTextForComboViewer(projectComboViewer, LOADING_PROJECTS);
		PluginUtils.enableComboViewer(branchComboViewer, false);
		PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
		PluginUtils.enableComboViewer(scanIdComboViewer, false);
		PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
		
		// Hide center and right panels
		resultViewComposite.setVisible(false);
		attackVectorCompositePanel.setVisible(false);
		
		// Empty branches and scans comboboxes
		clearBranchComboViewer();
		clearScanIdComboViewer();
		
		// Reset filters state
		resetFiltersState();
		
		// Disable all tool bar actions except the clear and refresh action
		toolBarActions.getToolBarActions().forEach(action -> action.setEnabled(action.getId().equals(ActionName.CLEAN_AND_REFRESH.name())));
		
		// Reset state variables
		currentProjectId = PluginConstants.EMPTY_STRING;
		currentBranch = PluginConstants.EMPTY_STRING;
		currentScanId = PluginConstants.EMPTY_STRING;
		
		// Update preferences values
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_PROJECT_ID, PluginConstants.EMPTY_STRING);
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_BRANCH, PluginConstants.EMPTY_STRING);
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, PluginConstants.EMPTY_STRING);
		
		// Get projects asynchronous to avoid layout issues
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				resetProjectComboViewer();
				
				PluginUtils.enableComboViewer(projectComboViewer, true);
				PluginUtils.enableComboViewer(scanIdComboViewer, true);
				toolBarActions.getScanResultsAction().setEnabled(true);
			}
		});
		
		// Clear vulnerabilities from Problems View
		PluginUtils.clearVulnerabilitiesFromProblemsView();
	}

	/**
	 * Clears Scans' combobox
	 */
	private void clearScanIdComboViewer() {
		scanIdComboViewer.setInput(Collections.emptyList());
		scanIdComboViewer.refresh();
		PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
	}

	/**
	 * Clears Branches' combobox
	 */
	private void clearBranchComboViewer() {
		branchComboViewer.setInput(Collections.EMPTY_LIST);
		branchComboViewer.refresh();
		PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
	}

	/**
	 * Reloads Projects' combobox
	 */
	private void resetProjectComboViewer() {  		
		List<Project> projectList = getProjects();
    	projectComboViewer.setInput(projectList);
    	projectComboViewer.refresh();
    	PluginUtils.setTextForComboViewer(projectComboViewer, PROJECT_COMBO_VIEWER_TEXT);
	}

	/**
	 * Reset filters
	 */
	private void resetFiltersState() {
		DataProvider.getInstance().setCurrentScanId(null);
		DataProvider.getInstance().setCurrentResults(null);
		FilterState.resetFilters();
		PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
	}

	/**
	 * Turn projects' combobox loading and disabled
	 */
	private void loadingProjects() {
		PluginUtils.enableComboViewer(projectComboViewer, false);
		PluginUtils.setTextForComboViewer(projectComboViewer, LOADING_PROJECTS);
	}
	
	/**
	 * Turn branches' combobox loading and disabled
	 */
	private void loadingBranches() {
		PluginUtils.enableComboViewer(branchComboViewer, false);
		PluginUtils.setTextForComboViewer(branchComboViewer, LOADING_BRANCHES);
	}
	
	/**
	 * Turn scan' combobox loading and disabled
	 */
	private void loadingScans() {
		PluginUtils.enableComboViewer(scanIdComboViewer, false);
		PluginUtils.setTextForComboViewer(scanIdComboViewer, LOADING_SCANS);
	}
	
	/**
	 * Disable comboboxes
	 */
	private void disablePluginFields(boolean disableToolBar) {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				PluginUtils.enableComboViewer(projectComboViewer, false);
				PluginUtils.enableComboViewer(branchComboViewer, false);
				PluginUtils.setTextForComboViewer(branchComboViewer, PluginConstants.COMBOBOX_BRANCH_CHANGING);
				loadingScans();
				PluginUtils.showMessage(rootModel, resultsTree, PluginConstants.EMPTY_STRING);
				resultViewComposite.setVisible(false);
				attackVectorCompositePanel.setVisible(false);
				
				if(disableToolBar) {
					toolBarActions.getToolBarActions().forEach(action -> action.setEnabled(false));
				}
			}
		});
	}
	
	/**
	 * Enable comboboxes
	 */
	private void enablePluginFields(boolean enableBranchCombobox) {
		PluginUtils.enableComboViewer(projectComboViewer, true);
		PluginUtils.enableComboViewer(branchComboViewer, enableBranchCombobox);
		PluginUtils.enableComboViewer(scanIdComboViewer, true);
		boolean resultsAvailable = DataProvider.getInstance().containsResults();
				
		for(Action action : toolBarActions.getToolBarActions()) {
			String actionName = action.getId();
			
			if(actionName.equals(ActionName.ABORT_RESULTS.name()) || actionName.equals(ActionName.GROUP_BY_SEVERITY.name()) && !actionName.equals(ActionName.GROUP_BY_QUERY_NAME.name())) {
				continue;
			}
			
			if(actionName.equals(ActionName.CLEAN_AND_REFRESH.name()) || actionName.equals(ActionName.GET_RESULTS.name())) {
				action.setEnabled(true);
				continue;
			}
			
			action.setEnabled(resultsAvailable);
		}
	}

	/**
	 * Event fired when Checkmarx credentials are changed
	 */
	@Override
	public void handleEvent(org.osgi.service.event.Event arg0) {
		if(!isPluginDraw) {
			drawPluginPanel();
		}else {			
			// If authenticated successfully and the projects are empty try to get them again
			if(projectComboViewer.getCombo().getItemCount() == 0) {
				clearAndRefreshPlugin();
			}
		}
	}

	/**
	 * Get projects from AST and draw an error message in the tree if an error occurred
	 * 
	 * @return
	 */
	private List<Project> getProjects(){
		List<Project> projectList = new ArrayList<>();
		
		try {
			projectList = DataProvider.getInstance().getProjects();
		} catch (Exception e) {
			String errorMessage = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : e.getMessage();
			PluginUtils.showMessage(rootModel, resultsTree, errorMessage);
		}
		
		return projectList;
	}
}
