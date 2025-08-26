package com.checkmarx.eclipse.views;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jgit.events.RefsChangedEvent;
import org.eclipse.jgit.events.RefsChangedListener;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.statushandlers.StatusManager;
import org.osgi.service.event.EventHandler;

import com.checkmarx.ast.codebashing.CodeBashing;
import com.checkmarx.ast.learnMore.LearnMore;
import com.checkmarx.ast.learnMore.Sample;
import com.checkmarx.ast.predicate.Predicate;
import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.PackageData;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.NotificationPopUpUI;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.actions.ToolBarActions;
import com.checkmarx.eclipse.views.filters.FilterState;
import com.checkmarx.eclipse.views.provider.ColumnProvider;
import com.checkmarx.eclipse.views.provider.TreeContentProvider;
import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Timer;
import java.util.TimerTask;

public class CheckmarxView extends ViewPart implements EventHandler {

	private static final String PROJECT_COMBO_VIEWER_TEXT = "Select a project";
	private static final String SCAN_COMBO_VIEWER_TEXT = "Select a scan";
	private static final String BRANCH_COMBO_VIEWER_TEXT = "Select a branch";
	private static final String LOADING_PROJECTS = "Loading projects...";
	private static final String LOADING_BRANCHES = "Loading branches...";
	private static final String LOADING_SCANS = "Loading scans...";
	private static final String NO_BRANCHES_AVAILABLE = "No branches available.";
	private static final String NO_PROJECTS_AVAILABLE = "No projects available.";
	private static final String FORMATTED_SCAN_LABEL = "%s %s";
	private static final String FORMATTED_SCAN_LABEL_LATEST = "%s %s (%s)";
	private boolean isUpdatingCombo = false;
	private boolean resetStoredProjects = false;
	
	private Timer debounceTimer = new Timer("ProjectSearchDebounce", true);
	private TimerTask pendingSearchTask;
	private static final int DEBOUNCE_DELAY_MS = 400;
	private volatile String latestProjectSearchTerm = "";
	
	private static final int SCROLL_WIDTH = 30;
	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "com.checkmarx.eclipse.views.CheckmarxView";

	public static final Image CHECKMARX_OPEN_SETTINGS_LOGO = Activator.getImageDescriptor("/icons/checkmarx-80.png")
			.createImage();

	public static final Image CRITICAL_SEVERITY = Activator.getImageDescriptor("/icons/critical.png")
			.createImage();

	public static final Image HIGH_SEVERITY = Activator.getImageDescriptor("/icons/high_untoggle.png").createImage();

	public static final Image MEDIUM_SEVERITY = Activator.getImageDescriptor("/icons/medium_untoggle.png")
			.createImage();

	public static final Image LOW_SEVERITY = Activator.getImageDescriptor("/icons/low_untoggle.png").createImage();

	public static final Image INFO_SEVERITY = Activator.getImageDescriptor("/icons/info_untoggle.png").createImage();

	public static final Image USER = Activator.getImageDescriptor("/icons/user.png").createImage();

	public static final Image CREATED_AT_IMAGE = Activator.getImageDescriptor("/icons/date.png").createImage();

	public static final Image COMMENT = Activator.getImageDescriptor("/icons/comment.png").createImage();

	public static final Image STATE = Activator.getImageDescriptor("/icons/state.png").createImage();

	public static final Image BFL = Activator.getImageDescriptor("/icons/CxFlatLogo12x12.png").createImage();

	private TreeViewer resultsTree;
	private ComboViewer scanIdComboViewer, projectComboViewer, branchComboViewer, triageSeverityComboViewew, triageStateComboViewer;
	private ISelectionChangedListener triageSeverityComboViewerListener, triageStateComboViewerListener;
	private Text commentText;
	private DisplayModel rootModel;
	private String selectedSeverity, selectedState;
	private Button triageButton;
	private SelectionAdapter triageButtonAdapter, codeBashingAdapter;
	private Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
	private String lastApiKey;

	private boolean alreadyRunning = false;
	private static List<LearnMore> learnMoreData;

	Font boldFont, titleFont;

	UISynchronizeImpl sync;
	private Composite resultViewComposite;
	private Composite attackVectorCompositePanel;
	private Composite titleComposite;
	private Composite openSettingsComposite;
	private CLabel titleLabel;
	private Text titleText;
	private Link codeBashingLinkText;

	private CLabel attackVectorLabel;
	//private CLabel bflLabel;
	//private Text bflText;
	private Label attackVectorSeparator;
	private ToolBarActions toolBarActions;

	private EventBus pluginEventBus;

	private GlobalSettings globalSettings = new GlobalSettings();

	private String currentProjectId = PluginConstants.EMPTY_STRING;
	private String currentBranch = PluginConstants.EMPTY_STRING;
	private String currentScanId = PluginConstants.EMPTY_STRING;
	private String latestScanId = PluginConstants.EMPTY_STRING;
	private static String currentScanIdFormmated = PluginConstants.EMPTY_STRING;
	private List<String> currentBranches = new ArrayList<>();
	private List<Project> currentProjects = new ArrayList<>();
	private List<Project> storeCurrentProjects = new ArrayList<>();


	private boolean scansCleanedByProject = false;
	private boolean firstTimeTriggered = false;

	private Composite parent;
	private ScrolledComposite scrolledComposite;

	private boolean isPluginDraw = false;
	protected TabFolder tabFolder;
	protected int bflNode;

	public CheckmarxView() {
		super();
		sync = new UISynchronizeImpl(PlatformUI.getWorkbench().getDisplay());
		lastApiKey = Preferences.STORE.getString(Preferences.API_KEY);
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

		if (boldFont != null && !boldFont.isDisposed()) {
			boldFont.dispose();
		}

		if (titleFont != null && !titleFont.isDisposed()) {
			titleFont.dispose();
		}

		if (pluginEventBus != null) {
			pluginEventBus.unregister(this);
		}

		PluginUtils.getEventBroker().unsubscribe(this);
	}

	@Override
	public void createPartControl(Composite parent) {
		this.parent = parent;

		// Clear vulnerabilities from Problems View
		PluginUtils.clearVulnerabilitiesFromProblemsView();

		if (PluginUtils.areCredentialsDefined()) {
			drawPluginPanel();
		} else {
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
					if (!firstTimeTriggered) {
						firstTimeTriggered = true;
						return;
					}

					String gitBranch = arg.getRepository().getBranch();
					
					if(gitBranch.equals(currentBranch)) {
						return;
					}
					
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

		if (!currentProjectId.isEmpty() && pluginBranchesContainsGitBranch) {
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

	/**
	 * Creates the Checkmarx plugin tool bar with all actions
	 */
	private void createToolbar() {
		IActionBars actionBars = getViewSite().getActionBars();

		pluginEventBus = new EventBus();
		pluginEventBus.register(this);

		toolBarActions = new ToolBarActions.ToolBarActionsBuilder().actionBars(actionBars).rootModel(rootModel)
				.resultsTree(resultsTree).pluginEventBus(pluginEventBus).projectsCombo(projectComboViewer).branchesCombo(branchComboViewer).scansCombo(scanIdComboViewer).build();
	}

	@Override
	public void setFocus() {
		if (resultsTree != null) {
			resultsTree.getControl().setFocus();
		}
	}

	/**
	 * Draw Plugin
	 */
	private void drawPluginPanel() {
		// Dispose missing credentials panel
		if (openSettingsComposite != null && !openSettingsComposite.isDisposed()) {
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
		
		loadComboboxes();

		// Init git branch listener
		initGitBranchListener();

		// Refresh layout
		parent.layout();

		isPluginDraw = true;
	}

	/**
	 * Panel where projects, branches and scans comboboxes will be drawn
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
	}

	private void loadComboboxes() {
		loadingProjects();
		loadingBranches();
		loadingScans();
		Job job = new Job("Checkmarx: Loading projects, branches and scans...") {

			@Override
			protected IStatus run(IProgressMonitor arg0) {
				currentProjects = getProjects();
				storeCurrentProjects = currentProjects;
				sync.asyncExec(() -> {
					projectComboViewer.setInput(currentProjects);
					if (currentProjectId.isEmpty() || currentProjects.isEmpty()) {
						PluginUtils.setTextForComboViewer(projectComboViewer, PROJECT_COMBO_VIEWER_TEXT);
						PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
						PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
						PluginUtils.enableComboViewer(projectComboViewer, true);
						PluginUtils.enableComboViewer(scanIdComboViewer, true);
						PluginUtils.enableComboViewer(branchComboViewer, false);
					}
				});

				// set project ID
				String currentProjectName = getProjectFromId(currentProjects, currentProjectId);
				sync.asyncExec(() -> {
					PluginUtils.setTextForComboViewer(projectComboViewer, currentProjectName);
				});

				// Get branches for project id
				currentBranches = DataProvider.getInstance().getBranchesForProject(currentProjectId);
				sync.asyncExec(() -> {
					branchComboViewer.setInput(currentBranches);
					PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
					PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);

				});
				
				if (!currentBranch.isEmpty()) {
					updateStartScanButton(true);
					sync.asyncExec(() -> {
						PluginUtils.setTextForComboViewer(branchComboViewer, currentBranch);
					});
					List<Scan> scanList = DataProvider.getInstance().getScansForProject(currentBranch);
					latestScanId = getLatestScanFromScanList(scanList).getId();
					sync.asyncExec(() -> {
						scanIdComboViewer.setInput(scanList);
						PluginUtils.setTextForComboViewer(scanIdComboViewer,
								scanList.isEmpty() ? PluginConstants.COMBOBOX_SCAND_ID_NO_SCANS_AVAILABLE
										: SCAN_COMBO_VIEWER_TEXT);
					});

					if (!currentScanId.isEmpty()) {
						String currentScanName = getScanNameFromId(scanList, currentScanId);		
						currentScanIdFormmated = currentScanName;
						sync.asyncExec(() -> {
							PluginUtils.setTextForComboViewer(scanIdComboViewer, currentScanName);
							alreadyRunning = true;
						});
						updateResultsTree(currentScanId, false);
					} else {
						sync.asyncExec(() -> {
							PluginUtils.enableComboViewer(projectComboViewer, true);
							PluginUtils.enableComboViewer(scanIdComboViewer, true);
							PluginUtils.enableComboViewer(branchComboViewer, true);
						});
					}
				} else {
					sync.asyncExec(() -> {
						PluginUtils.enableComboViewer(projectComboViewer, true);
						PluginUtils.enableComboViewer(scanIdComboViewer, true);
						PluginUtils.enableComboViewer(branchComboViewer, !currentProjectId.isEmpty());
						updateStartScanButton(false);
					});
				}

				return Status.OK_STATUS;
			}

		};
		job.schedule();
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

		resultsTree = new TreeViewer(treeResultsComposite,
				SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
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

		Job job = new Job("Checkmarx: Retrieving results...") {
			boolean gettingResults = globalSettings.getProjectId() != null && !globalSettings.getProjectId().isEmpty()
					&& globalSettings.getScanId() != null && !globalSettings.getScanId().isEmpty();
			boolean noProjectsAvailable = projectComboViewer.getCombo().getText().equals(NO_PROJECTS_AVAILABLE);
			String message = gettingResults && !noProjectsAvailable
					? String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, globalSettings.getScanId())
					: PluginConstants.EMPTY_STRING;

			@Override
			protected IStatus run(IProgressMonitor arg0) {

				sync.asyncExec(() -> {
					PluginUtils.showMessage(rootModel, resultsTree, message);
				});
				return Status.OK_STATUS;
			}

		};
		job.schedule();
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

		titleComposite = new Composite(resultViewComposite, SWT.None);
		GridLayout titleCmpositeLayout = new GridLayout(2, false);
		titleComposite.setLayout(titleCmpositeLayout);
		titleComposite.setSize(resultViewComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		titleComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1));

		titleLabel = new CLabel(titleComposite, SWT.NONE);
		titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1));

		titleText = new Text(titleComposite, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI);
		GridData gd_text = new GridData(SWT.FILL, SWT.FILL, false, true, 1, 1);
		// gd_text.widthHint = 552;
		titleText.setFont(titleFont);
		titleText.setLayoutData(gd_text);

		// create a label for codebashing link
		Composite codeBashingComposite = new Composite(resultViewComposite, SWT.None);
		GridLayout codeBashingLayout = new GridLayout(3, false);
		codeBashingComposite.setLayout(codeBashingLayout);
		codeBashingComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1));

		CLabel codeBashingLink = new CLabel(codeBashingComposite, SWT.HORIZONTAL);
		codeBashingLink.setRightMargin(0);
		codeBashingLink.setText("Learn more at");

		CLabel clogo = new CLabel(codeBashingComposite, SWT.HORIZONTAL);
		GridData gd_clogo = new GridData(SWT.RIGHT, SWT.LEFT, false, false, 1, 1);
		gd_clogo.widthHint = 18;
		clogo.setLayoutData(gd_clogo);
		clogo.setForeground(new Color(display, new RGB(243, 106, 34)));
		clogo.setText(">_");
		clogo.setLeftMargin(0);
		clogo.setRightMargin(0);

		codeBashingLinkText = new Link(codeBashingComposite, SWT.HORIZONTAL);
		codeBashingLinkText.setText("<a>codebashing</a>");
		codeBashingLinkText.setData(PluginConstants.DATA_ID_KEY, PluginConstants.CODEBASHING_LINK_ID);

		Label separator = new Label(resultViewComposite, SWT.HORIZONTAL | SWT.SEPARATOR);
		separator.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Composite triageView = new Composite(resultViewComposite, SWT.NONE);
		GridLayout gl_triageView = new GridLayout(3, false);
		gl_triageView.marginHeight = 10;
		triageView.setLayout(gl_triageView);
		triageView.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));

		triageSeverityComboViewew = new ComboViewer(triageView, SWT.READ_ONLY);
		Combo combo_1 = triageSeverityComboViewew.getCombo();
		combo_1.setEnabled(true);
		combo_1.setData(PluginConstants.DATA_ID_KEY, PluginConstants.TRIAGE_SEVERITY_COMBO_ID);
		GridData gd_combo_1 = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		gd_combo_1.widthHint = SWT.DEFAULT;
		combo_1.setLayoutData(gd_combo_1);

		triageStateComboViewer = new ComboViewer(triageView, SWT.READ_ONLY);
		Combo combo_2 = triageStateComboViewer.getCombo();
		combo_2.setEnabled(true);
		combo_2.setData(PluginConstants.DATA_ID_KEY, PluginConstants.TRIAGE_STATE_COMBO_ID);
		GridData gd_combo_2 = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		// gd_combo_2.widthHint = 180;
		combo_2.setLayoutData(gd_combo_2);

		triageButton = new Button(triageView, SWT.FLAT | SWT.CENTER);
		triageButton.setData(PluginConstants.DATA_ID_KEY, PluginConstants.TRIAGE_BUTTON_ID);
		triageButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 1, 1));
		triageButton.setText(PluginConstants.BTN_UPDATE);

		commentText = new Text(triageView, SWT.BORDER);
		commentText.setText(PluginConstants.DEFAULT_COMMENT_TXT);
		GridData commentData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
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
				if (textReceived.getText() == null || textReceived.getText() == "") {
					commentText.setText(PluginConstants.DEFAULT_COMMENT_TXT);
					resultViewComposite.layout();
				}
			}
		});

		scrolledComposite = new ScrolledComposite(resultViewComposite, SWT.V_SCROLL);
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);
		scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scrolledComposite.setMinSize(resultViewComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));

		resultViewComposite.setVisible(false);
	}

	/**
	 * Create result's vulnerabilities panel
	 * 
	 * @param resultsComposite
	 */
	private void createResultVulnerabilitiesPanel(Composite resultsComposite) {		
		attackVectorCompositePanel = new Composite(resultsComposite, SWT.BORDER);
		attackVectorCompositePanel.setLayout(new FillLayout());
		attackVectorCompositePanel.setVisible(false);
	}

	/*
	 * draw attack vector separator label
	 */
	private void drawAttackVectorSeparator(Composite parent) {
		attackVectorSeparator = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
		attackVectorSeparator.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	}

	/*
	 * draw BFL composite
	 */

	/*private void drawBFLComposite() {
		bflComposite = new Composite(attackVectorContentComposite, SWT.NONE);
		GridLayout bflCompositeLayout = new GridLayout(2, false);
		bflComposite.setLayout(bflCompositeLayout);
		bflComposite.setSize(attackVectorContentComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		GridData gd_blfComposite = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1);
		bflComposite.setLayoutData(gd_blfComposite);

		bflComposite.setBackground(attackVectorContentComposite.getBackground());

		bflLabel = new CLabel(bflComposite, SWT.HORIZONTAL);
		GridData gd_emptyLabel = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1);
		gd_emptyLabel.heightHint = PluginConstants.BFL_LABEL_HEIGHT;
		bflLabel.setLayoutData(gd_emptyLabel);
		bflLabel.setBackground(bflComposite.getBackground());

		bflText = new Text(bflComposite, SWT.WRAP | SWT.MULTI);
		GridData gd_bflText = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
		gd_bflText.widthHint = PluginConstants.BFL_TEXT_MAX_WIDTH;
		bflText.setLayoutData(gd_bflText);
		bflText.setBackground(bflComposite.getBackground());
		bflText.setData(PluginConstants.DATA_ID_KEY, PluginConstants.BEST_FIX_LOCATION);
	}*/

	/**
	 * Draw panel when Checkmarx credentials are not defined
	 */
	private void drawMissingCredentialsPanel() {
		
	    // Dispose all children to remove any previous panels
	    for (Control child : parent.getChildren()) {
	        child.dispose();
	    }
		openSettingsComposite = new Composite(parent, SWT.NONE);

		openSettingsComposite.setLayout(new GridLayout(1, true));
		
		// This is the key line: center horizontally and vertically, and expand to fill
	    openSettingsComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));

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
				PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(shell,
						"com.checkmarx.eclipse.properties.preferencespage", null, null);

				if (pref != null) {
					pref.open();
				}
			}
		});
		parent.layout(true, true);
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

					// Check if selected project exists in currentProjects
					if (!currentProjects.contains(selectedProject)) {
						// Invalid project - reset to default text and disable scan button
						PluginUtils.setTextForComboViewer(projectComboViewer, PROJECT_COMBO_VIEWER_TEXT);
						updateStartScanButton(false);
						return;
					}
					// Avoid non-sense trigger changed when opening the combo
					if (selectedProject.getId().equals(currentProjectId)) {
						CxLogger.info(PluginConstants.INFO_CHANGE_PROJECT_EVENT_NOT_TRIGGERED);
						preservCaretposition(storeCurrentProjects, selectedProject.getName());
						currentProjects = storeCurrentProjects;
						return;
					}

					onProjectChangePluginLoading(selectedProject.getId());

					Job job = new Job("Checkmarx: Loading branches...") {
						@Override
						protected IStatus run(IProgressMonitor arg0) {
							currentBranches = DataProvider.getInstance().getBranchesForProject(selectedProject.getId());
							sync.asyncExec(() -> {
								branchComboViewer.setInput(currentBranches);
								PluginUtils.setTextForComboViewer(branchComboViewer, currentBranches.isEmpty() ? NO_BRANCHES_AVAILABLE : BRANCH_COMBO_VIEWER_TEXT);
								PluginUtils.enableComboViewer(branchComboViewer, true);
								PluginUtils.enableComboViewer(scanIdComboViewer, true);
								PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
								toolBarActions.getStateFilterAction().setEnabled(true);

								PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
							});
							return Status.OK_STATUS;
						}
					};
					job.schedule();
					//After project selected and branches loaded reset the project list
					if(resetStoredProjects) {
						storeCurrentProjects.add(selectedProject);
						preservCaretposition(storeCurrentProjects, selectedProject.getName());
						currentProjects = storeCurrentProjects;
						resetStoredProjects=false;
					}
				}
			}
		});

		// Add ModifyListener to handle manual text input for projects
		projectComboViewer.getCombo().addModifyListener(e -> {
			if (isUpdatingCombo) return;
			String enteredProject = projectComboViewer.getCombo().getText().trim();
			
			// Skip search if the text is the default instruction
			if (enteredProject.equals(PROJECT_COMBO_VIEWER_TEXT) || enteredProject.equals(LOADING_PROJECTS)) {
				updateStartScanButton(false); // Disable scan button
				return;
			}
			 // If user starts typing again and list is empty, restore currentProjects
		    if (projectComboViewer.getCombo().getItemCount() == 0 && !currentProjects.isEmpty() && enteredProject.length()>0) {
		    	isUpdatingCombo = true;
		    	preservCaretposition(currentProjects,enteredProject);
		        isUpdatingCombo = false;
		    }
			
			latestProjectSearchTerm = enteredProject; // Track the latest term
			List<String> matchedProjects;
			matchedProjects = currentProjects.stream().map(Project::getName)
					.filter(name -> name != null && name.toLowerCase().contains(enteredProject.toLowerCase())).limit(100)
					.collect(Collectors.toList());
			if (matchedProjects.isEmpty()) {
				CxLogger.info("Entered project is not exist in current projects list");
				// Cancel any pending search
				if (pendingSearchTask != null) {
					pendingSearchTask.cancel();
				}
				// Schedule a new search after the debounce delay
				pendingSearchTask = new java.util.TimerTask() {
					@Override
					public void run() {
						final String searchTerm = latestProjectSearchTerm; // Capture the term for this search
						// Schedule a background job for the server search
						Job job = new Job("Checkmarx: Searching for project on server...") {
							@Override
							protected IStatus run(IProgressMonitor monitor) {
								List<Project> searchedProjects;
								try {
									searchedProjects = DataProvider.getInstance().getProjects(searchTerm);
									Display.getDefault().asyncExec(() -> {
										if (searchTerm.equals(latestProjectSearchTerm)) {
											isUpdatingCombo = true;
											// Update UI in UI thread
											if (searchedProjects != null && !searchedProjects.isEmpty()) {
												preservCaretposition(searchedProjects,searchTerm);
												currentProjects = searchedProjects;
												resetStoredProjects=true;
											} else {
												preservCaretposition(Collections.emptyList(),searchTerm);
												updateStartScanButton(false); // Disable scan button
												isUpdatingCombo = false;
												return;
											}
											isUpdatingCombo = false;
										}
									});
								} catch (Exception ex) {
									ex.printStackTrace();
								}				
								return Status.OK_STATUS;
							}
						};
						job.schedule();
					}
				};
				debounceTimer.schedule(pendingSearchTask, DEBOUNCE_DELAY_MS);
				
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

		Optional<Project> project = projects.stream().filter(p -> p.getId().equals(projectId)).findFirst();

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

					// Check if selected branch exists in currentBranches
					if (!currentBranches.contains(selectedBranch)) {
						// Invalid branch - reset to default text and disable scan button
						PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
						updateStartScanButton(false);
						return;
					}

					// Avoid non-sense trigger changed when opening the combo
					if (selectedBranch.equals(currentBranch) && !scansCleanedByProject) {
						CxLogger.info(PluginConstants.INFO_CHANGE_BRANCH_EVENT_NOT_TRIGGERED);
						return;
					}

					onBranchChangePluginLoading(selectedBranch);

					List<Scan> scanList = DataProvider.getInstance().getScansForProject(selectedBranch);
					if (!scanList.isEmpty()) {
						latestScanId = getLatestScanFromScanList(scanList).getId();
					}
					scanIdComboViewer.setInput(scanList);
					loadLatestScanByDefault(scanList);

					sync.asyncExec(new Runnable() {
						public void run() {
							PluginUtils.enableComboViewer(projectComboViewer, true);
							PluginUtils.enableComboViewer(scanIdComboViewer, true);
							PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
							toolBarActions.getStateFilterAction().setEnabled(true);
							updateStartScanButton(true);
						}
					});

					PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
				}
			}
		});

		// Add ModifyListener to handle manual text input
		branchComboViewer.getCombo().addModifyListener(e -> {
			String enteredBranch = branchComboViewer.getCombo().getText();

			// If text was modified and branch doesn't exist
			if (!currentBranches.contains(enteredBranch)) {
				updateStartScanButton(false); // Disable scan button
			} else {
				updateStartScanButton(true); // Enable scan button if branch is valid
			}
		});
	}

	private void loadLatestScanByDefault(List<Scan> scanList) {
		if(scanList.isEmpty()) {
			PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_NO_SCANS_AVAILABLE);
			return;
		} else {
			currentScanId = getLatestScanFromScanList(scanList).getId();
		}
		sync.asyncExec(() -> {
			loadingScans();
			currentScanIdFormmated = getScanNameFromId(scanList, currentScanId);
			scanIdComboViewer.setSelection(new StructuredSelection(currentScanId));
			PluginUtils.setTextForComboViewer(scanIdComboViewer, currentScanIdFormmated);
			PluginUtils.showMessage(rootModel, resultsTree, String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, latestScanId));
			alreadyRunning=true;
			updateResultsTree(currentScanId,false);
			GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, currentScanId);
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

	private void createScanIdComboBox(Composite parent) {
		scanIdComboViewer = new ComboViewer(parent, SWT.DROP_DOWN | SWT.SIMPLE);
		scanIdComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		scanIdComboViewer.setInput(new ArrayList<>());

		GridData gridData = new GridData();
		gridData.widthHint = 520;
		scanIdComboViewer.getCombo().setLayoutData(gridData);

		scanIdComboViewer.getCombo().addListener(SWT.DefaultSelection, new Listener() {
			public void handleEvent(Event event) {
				setSelectionForProjectComboViewer();
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

				Job job = new Job("Checkmarx: Getting results...") {

					@Override
					protected IStatus run(IProgressMonitor arg0) {
						IStructuredSelection selection = (IStructuredSelection) event.getSelection();

						Scan selectedScan = ((Scan) selection.getFirstElement());
						if (selectedScan != null && (selectedScan.getId().equals(currentScanId) || alreadyRunning)) {
							CxLogger.info(String.format(PluginConstants.INFO_CHANGE_SCAN_EVENT_NOT_TRIGGERED,
									alreadyRunning, selectedScan.getId().equals(currentScanId)));
							return Status.OK_STATUS;
						}
						if (selection.size() > 0) {
							sync.asyncExec(() -> {
								PluginUtils.showMessage(rootModel, resultsTree, String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, selectedScan.getId()));
								PluginUtils.enableComboViewer(projectComboViewer, false);
								PluginUtils.enableComboViewer(branchComboViewer, false);
							});
							// onScanChangePluginLoading(selectedScan.getId());
							currentScanId = selectedScan.getId();
							DataProvider.getInstance().setCurrentResults(null);
							GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, selectedScan.getId());
							sync.asyncExec(() -> {
								currentScanIdFormmated = scanIdComboViewer.getCombo().getText();
								// Hide center and right panels
								resultViewComposite.setVisible(false);
								attackVectorCompositePanel.setVisible(false);
								// Clear vulnerabilities from Problems View
								PluginUtils.clearVulnerabilitiesFromProblemsView();
								alreadyRunning = true;
							});
							updateResultsTree(selectedScan.getId(), false);

						}
						return Status.OK_STATUS;
					}
				};

				job.schedule();

			}
		});
	}

	/**
	 * Get formated scan name from scan id
	 * 
	 * @param scans
	 * @param scanId
	 * @return
	 */
	private String getScanNameFromId(List<Scan> scans, String scanId) {
		if (scans.isEmpty()) {
			return PluginConstants.COMBOBOX_SCAND_ID_NO_SCANS_AVAILABLE;
		}

		Optional<Scan> scan = scans.stream().filter(s -> s.getId().equals(scanId)).findFirst();

		return scan.isPresent() ? formatScanLabel(scan.get()) : SCAN_COMBO_VIEWER_TEXT;
	}

	/**
	 * Formats scan's displayed label
	 * 
	 * @param scan
	 * @return
	 */
	private String formatScanLabel(Scan scan) {
		String formattedString = "";
		String updatedAtDate = PluginUtils.convertStringTimeStamp(scan.getUpdatedAt());
		if(!latestScanId.isEmpty() && scan.getId().equalsIgnoreCase(latestScanId)) {
			formattedString =  String.format(FORMATTED_SCAN_LABEL_LATEST,  updatedAtDate ,scan.getId() ,"latest");
		} else {
			
			formattedString =  String.format(FORMATTED_SCAN_LABEL, updatedAtDate, scan.getId());
		}
		return formattedString;
		
	}
	
	/**
	 * Retrieve latest scan from scanList
	 */
	
	private Scan getLatestScanFromScanList(List<Scan> scanList) {
		
		return scanList.get(0);
	}

	/**
	 * Reverse selection - Populate project combobox and select a project id based
	 * on the chosen scan id
	 */
	private void setSelectionForProjectComboViewer() {
		String scanId = scanIdComboViewer.getCombo().getText();
		
		if(scanId.isEmpty()) {
			PluginUtils.clearMessage(rootModel, resultsTree);
			CxLogger.info(String.format(PluginConstants.NO_SCAN_ID_PROVIDED, scanId));
			return;
		}

		if (currentScanId.equals(scanId)) {
			PluginUtils.setTextForComboViewer(scanIdComboViewer, currentScanIdFormmated);
			CxLogger.info(String.format(PluginConstants.INFO_RESULTS_ALREADY_RETRIEVED, scanId));
			return;
		}

		if (!PluginUtils.validateScanIdFormat(scanId)) {
			PluginUtils.showMessage(rootModel, resultsTree, PluginConstants.TREE_INVALID_SCAN_ID_FORMAT);
			return;
		}

		Job job = new Job("Checkmarx: Loading results...") {
			@Override
			protected IStatus run(IProgressMonitor arg0) {
				sync.asyncExec(() -> {
					PluginUtils.showMessage(rootModel, resultsTree,
							String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, scanId));
					loadingProjects();
					loadingBranches();
					resultViewComposite.setVisible(false);
					attackVectorCompositePanel.setVisible(false);
				});
				Scan scan;
				String projectId;
				try {
					scan = DataProvider.getInstance().getScanInformation(scanId);
					projectId = scan.getProjectId();
				} catch (Exception e) {
					// need to move the ui modifications inside async
					showExceptionMessage(e);

					return null;
				}
				List<Project> projectList = getProjects();
				if (projectList.isEmpty())
					return null;
				String projectName = getProjectFromId(projectList, projectId);
				currentProjectId = projectId;
				GlobalSettings.storeInPreferences(GlobalSettings.PARAM_PROJECT_ID, currentProjectId);

				sync.asyncExec(() -> {
					projectComboViewer.setInput(projectList);
					PluginUtils.setTextForComboViewer(projectComboViewer, projectName);
					setSelectionForBranchComboViewer(scan.getBranch(), projectId);
					setSelectionForScanIdComboViewer(scan.getId(), scan.getBranch());
					updateStartScanButton(true);
				});
				return Status.OK_STATUS;
			}

			private void showExceptionMessage(Exception e) {
				sync.asyncExec(() -> {
					String errorMessage = e.getCause() != null && e.getCause().getMessage() != null
							? e.getCause().getMessage()
							: e.getMessage();
					PluginUtils.showMessage(rootModel, resultsTree, errorMessage);

					PluginUtils.setTextForComboViewer(projectComboViewer, PROJECT_COMBO_VIEWER_TEXT);
					PluginUtils.enableComboViewer(projectComboViewer, true);

					PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
					PluginUtils.enableComboViewer(branchComboViewer, false);
				});

			}

		};
		job.schedule();
	}

	/**
	 * Reverse selection - Populate branch combobox and select a branch based on the
	 * chosen scan id
	 */
	private void setSelectionForBranchComboViewer(String branchName, String projectId) {
		currentBranches = DataProvider.getInstance().getBranchesForProject(projectId);

		if (currentBranches != null) {
			branchComboViewer.setInput(currentBranches);

			String currentBranchName = currentBranches.stream().filter(branch -> branchName.equals(branch)).findAny()
					.orElse(null);

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

		if (scanList.isEmpty()) {
			if (Strings.isNullOrEmpty(scanId)) {
				PluginUtils.setTextForComboViewer(scanIdComboViewer,
						PluginConstants.COMBOBOX_SCAND_ID_NO_SCANS_AVAILABLE);
			} else {
				scanIdComboViewer.setSelection(new StructuredSelection(scanId));
			}

			currentScanId = scanId;
			GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, currentScanId);

			return;
		}

		if (Strings.isNullOrEmpty(scanId)) {
			PluginUtils.setTextForComboViewer(scanIdComboViewer, SCAN_COMBO_VIEWER_TEXT);
			GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, PluginConstants.EMPTY_STRING);
			return;
		}

		Scan currentScan = scanList.stream().filter(scan -> scanId.equals(scan.getId())).findAny().orElse(null);

		scanIdComboViewer.setSelection(
				new StructuredSelection(currentScan != null ? currentScan : PluginConstants.EMPTY_STRING));

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

					if (selectedItem == null || selectedItem.getType() == null) {
						return;
					}
					if (selectedItem.getQueryName() != null) {
						populateCodeBashingToolTip(selectedItem);
					}

					if (selectedItem.getSeverity() != null) {
						populateTitleLabel(selectedItem);
					}
					Job job = new Job("Checkmarx: Loading result...") {

						@Override
						protected IStatus run(IProgressMonitor arg0) {

							if (selectedItem.getResult() != null && selectedItem.getResult().getSimilarityId() != null) {
								sync.asyncExec(() -> {
									createTriageSeverityAndStateCombos(selectedItem);
									populateTriageChanges(selectedItem);
									resultViewComposite.setVisible(true);
									resultViewComposite.layout();
									updateAttackVectorForSelectedTreeItem(selectedItem);
								});
							}
							return Status.OK_STATUS;
						}

					};
					job.schedule();
				}
			}

			private void populateTitleLabel(DisplayModel selectedItem) {
				titleLabel.setImage(findSeverityImage(selectedItem));
				titleText.setText(selectedItem.getName());
				titleLabel.layout();
				titleText.requestLayout();
			}
		});
	}

	/**
	 * Create combo viewers for severity and state
	 * 
	 * @param selectedItem
	 */
	private void createTriageSeverityAndStateCombos(DisplayModel selectedItem) {
		String currentSeverity = selectedItem.getSeverity();
		selectedSeverity = selectedItem.getSeverity();
		String[] severity = { "CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO" };

			triageSeverityComboViewew.setContentProvider(ArrayContentProvider.getInstance());
			triageSeverityComboViewew.setInput(severity);
			PluginUtils.setTextForComboViewer(triageSeverityComboViewew, currentSeverity);
	

		if (triageSeverityComboViewerListener != null) {
			triageSeverityComboViewew.removeSelectionChangedListener(triageSeverityComboViewerListener);
		}
		triageSeverityComboViewerListener = new ISelectionChangedListener() {

			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				if (selection.size() > 0) {
					selectedSeverity = ((String) selection.getFirstElement());
				}
			}
		};
		triageSeverityComboViewew.addSelectionChangedListener(triageSeverityComboViewerListener);

		String currentState = selectedItem.getState();
		selectedState = selectedItem.getResult().getState();
		
		// [AST-92100] Fetch dynamic states from DataProvider
		List<String> state = DataProvider.getInstance().getStatesForEngine(selectedItem.getType());
		
			triageStateComboViewer.setContentProvider(ArrayContentProvider.getInstance());
			triageStateComboViewer.setInput(state);
			PluginUtils.setTextForComboViewer(triageStateComboViewer, currentState);
	

		if (triageStateComboViewerListener != null) {
			triageStateComboViewer.removeSelectionChangedListener(triageStateComboViewerListener);
		}
		triageStateComboViewerListener = new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				if (selection.size() > 0) {
					selectedState = ((String) selection.getFirstElement());
				}
			}
		};
		triageStateComboViewer.addSelectionChangedListener(triageStateComboViewerListener);
		if (triageButtonAdapter != null) {
			triageButton.removeSelectionListener(triageButtonAdapter);
			
		}
		triageButtonAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				// Call triage update. triageButton.isEnabled() is used to avoid nonsense
				// randomly clicks triggered
				if (selectedSeverity != null && selectedState != null && triageButton.isEnabled()) {
					UUID projectId = UUID.fromString(currentProjectId);
					String similarityId = selectedItem.getResult().getSimilarityId();
					String engineType = selectedItem.getResult().getType();
					triageButton.setEnabled(false);
					triageButton.setText(PluginConstants.BTN_LOADING);
					commentText.setEnabled(false);
					commentText.setEditable(false);

					Job job = new Job("Checkmarx: Updating triage information...") {
						String comment = commentText.getText() != null
								&& !commentText.getText().equalsIgnoreCase("Notes (Optional or required based on tenant configuration)") ? commentText.getText()
										: "";

						@Override
						protected IStatus run(IProgressMonitor arg0) {
							try {
								DataProvider.getInstance().triageUpdate(projectId,similarityId, engineType, selectedState, comment, selectedSeverity);
								
								sync.asyncExec(() -> {
									selectedItem.setSeverity(selectedSeverity);
									selectedItem.setState(selectedState);
									titleLabel.setImage(findSeverityImage(selectedItem));
									titleText.setText(selectedItem.getName());
									populateTriageChanges(selectedItem);

									alreadyRunning = true;
									updateResultsTree(DataProvider.getInstance().sortResults(), true);
									triageButton.setEnabled(true);
									triageButton.setText(PluginConstants.BTN_UPDATE);
									commentText.setEnabled(true);
									commentText.setText(PluginConstants.DEFAULT_COMMENT_TXT);
									commentText.setEditable(true);
								});
							} catch (Exception e) {
								sync.asyncExec(() -> {
									new NotificationPopUpUI(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().getDisplay(), "Triage failed", e.getMessage(), null, null, null).open();
								});
							}

							// reset the triageButton when triage update fails
							sync.asyncExec(() -> {
								if (!triageButton.isEnabled()) {
									triageButton.setEnabled(true);
									triageButton.setText(PluginConstants.BTN_UPDATE);
								}
								if (!commentText.isEnabled()) {
									commentText.setEnabled(true);
									commentText.setText(PluginConstants.DEFAULT_COMMENT_TXT);
									commentText.setEditable(true);
								}
							});
							return Status.OK_STATUS;
						}

					};
					job.schedule();

					resultViewComposite.layout();
				}
			}
		};
		
		triageButton.addSelectionListener(triageButtonAdapter);
		
		
		boolean isSCAVulnerability = selectedItem.getType().equalsIgnoreCase(PluginConstants.SCA_DEPENDENCY);
		triageButton.setVisible(!isSCAVulnerability);
		triageButton.setEnabled(!isSCAVulnerability);
		triageStateComboViewer.getCombo().setEnabled(!isSCAVulnerability);
		triageSeverityComboViewew.getCombo().setEnabled(!isSCAVulnerability);
		commentText.setVisible(!isSCAVulnerability);
		commentText.setEnabled(!isSCAVulnerability);
	}

	/*
	 * populate tool tip for codebashing link
	 * 
	 */

	private void populateCodeBashingToolTip(DisplayModel selectedItem) {
		codeBashingLinkText.setToolTipText(
				"Learn more about " + selectedItem.getQueryName() + " using Checkmarx's eLearning platform");

		// remove the previous listeners to make sure multiple listeners are not lined
		// up
		if (codeBashingAdapter != null) {
			codeBashingLinkText.removeSelectionListener(codeBashingAdapter);
		}

		// add the latest selection event as selection adapter
		codeBashingAdapter = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				openBrowserLink(selectedItem);
			}

		};

		// bind the selection listener to the link
		codeBashingLinkText.addSelectionListener(codeBashingAdapter);

	}

	/*
	 * Open browser link on selection
	 */

	private void openBrowserLink(DisplayModel selectedItem) {
		Job job = new Job("Checkmarx: populating codeBashing link...") {
			String cve = selectedItem.getResult().getVulnerabilityDetails().getCweId() != null
					? selectedItem.getResult().getVulnerabilityDetails().getCweId()
					: "";
			String language = selectedItem.getResult().getData().getLanguageName() != null
					? selectedItem.getResult().getData().getLanguageName()
					: "";
			String queryName = selectedItem.getResult().getData().getQueryName() != null
					? selectedItem.getResult().getData().getQueryName()
					: "";

			@Override
			protected IStatus run(IProgressMonitor arg0) {
				try {
					CodeBashing codeBashing = DataProvider.getInstance().getCodeBashingLink(cve, language, queryName);
					openLink(codeBashing.getPath());
				} catch (CxException e) {
					CxLogger.info(String.format(PluginConstants.CODEBASHING, e.getMessage()));
					
					if (e.getExitCode() == PluginConstants.EXIT_CODE_LICENSE_NOT_FOUND) {
						SelectionAdapter onClickCodebashingLink = new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent event) {
								try {
									PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(event.text));
								} catch (PartInitException | MalformedURLException e) {
									CxLogger.error(String.format(PluginConstants.ERROR_GETTING_CODEBASHING_DETAILS, e.getMessage()), e);
								}
							}
						};
						
						sync.asyncExec(() -> {
							new NotificationPopUpUI(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().getDisplay(), PluginConstants.CODEBASHING,
									PluginConstants.CODEBASHING_NO_LICENSE, onClickCodebashingLink, null, null).open();
						});
					} else if (e.getExitCode() == PluginConstants.EXIT_CODE_LESSON_NOT_FOUND) {
						sync.asyncExec(() -> {
							new NotificationPopUpUI(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().getDisplay(), PluginConstants.CODEBASHING,
									PluginConstants.CODEBASHING_NO_LESSON, null, null, null).open();
						});
					}

				} catch (Exception e) {
					CxLogger.error(String.format(PluginConstants.ERROR_GETTING_CODEBASHING_DETAILS, e.getMessage()), e);
				}

				return Status.OK_STATUS;
			}

		};
		job.schedule();

	}

	private void openLink(String path) {
		try {
			PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(path));
		} catch (PartInitException | MalformedURLException e) {
			CxLogger.error(String.format(PluginConstants.ERROR_GETTING_CODEBASHING_DETAILS, e.getMessage()), e);
		}
	}

	/**
	 * Populate list of changes for the selected vulnerability
	 * 
	 * @param selectedItem
	 */
	private void populateTriageChanges(DisplayModel selectedItem) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				if (tabFolder != null) {
					tabFolder.dispose();
				}
				tabFolder = new TabFolder(scrolledComposite, SWT.NONE);
				TabItem tbtmDescription = new TabItem(tabFolder, SWT.NONE);
				tbtmDescription.setText("Description");

				ScrolledComposite descriptionScrolledComposite = new ScrolledComposite(tabFolder, SWT.V_SCROLL);
				descriptionScrolledComposite.setExpandHorizontal(true);
				descriptionScrolledComposite.setExpandVertical(true);
				descriptionScrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

				Composite detailsComposite = new Composite(descriptionScrolledComposite, SWT.NONE);
				GridLayout gl_detailsComposite = new GridLayout(1, false);
				gl_detailsComposite.marginWidth = 0;
				gl_detailsComposite.marginHeight = 0;
				detailsComposite.setLayout(gl_detailsComposite);
				tbtmDescription.setControl(descriptionScrolledComposite);

				Text descriptionTxt = new Text(detailsComposite, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI);
				descriptionTxt.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				descriptionTxt.setText(selectedItem.getResult().getDescription() != null ? selectedItem.getResult().getDescription(): "No data");

				descriptionScrolledComposite.setContent(detailsComposite);
				descriptionScrolledComposite.setMinSize(descriptionScrolledComposite.getSize().x,
						detailsComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).y);

				TabItem tbtmChanges = new TabItem(tabFolder, SWT.NONE);
				tbtmChanges.setData(PluginConstants.DATA_ID_KEY, PluginConstants.CHANGES_TAB_ID);
				tbtmChanges.setText("Changes");

				ScrolledComposite changesScrolledComposite = new ScrolledComposite(tabFolder, SWT.V_SCROLL);
				changesScrolledComposite.setExpandHorizontal(true);
				changesScrolledComposite.setExpandVertical(true);
				changesScrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

				Composite changesComposite = new Composite(changesScrolledComposite, SWT.NONE);
				GridLayout gl_changesComposite = new GridLayout(1, false);
				gl_changesComposite.marginWidth = 0;
				gl_changesComposite.marginHeight = 0;
				changesComposite.setLayout(gl_changesComposite);
				tbtmChanges.setControl(changesScrolledComposite);

				changesScrolledComposite.setContent(changesComposite);
				changesScrolledComposite.setMinSize(changesScrolledComposite.getSize().x, changesComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).y);

				scrolledComposite.setContent(tabFolder);

				tabFolder.addSelectionListener(new SelectionListener() {

					protected boolean changesLoaded = false;

					public void widgetSelected(SelectionEvent e) {
						TabItem item = (TabItem) e.item;

						if (item == null)
							return;
						if (item.getText().equalsIgnoreCase("Changes")) {

							Job job = new Job("Checkmarx: Loading triage changes...") {

								protected void populateLoadingScreen() {
									sync.asyncExec(() -> {
										Composite loadingScreen = new Composite(scrolledComposite, SWT.NONE);
										loadingScreen.setLayout(new GridLayout(1, false));
										loadingScreen.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false));

										CLabel loadingLabel = new CLabel(loadingScreen, SWT.NONE);
										loadingLabel.setText(PluginConstants.LOADING_CHANGES);
										loadingLabel
												.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

										commentText.setText(PluginConstants.DEFAULT_COMMENT_TXT);

										scrolledComposite.setContent(loadingScreen);

									});
								}

								@Override
								protected IStatus run(IProgressMonitor arg0) {
									if (!changesLoaded) {
										populateLoadingScreen();
										List<Predicate> triageDetails = getTriageInfo(UUID.fromString(currentProjectId),
												selectedItem.getResult().getSimilarityId(),
												selectedItem.getResult().getType());
										if (triageDetails.size() > 0) {
											// populate changes composite based on the predicate
											for (Predicate detail : triageDetails) {
												// populate individual triage node details
												populateIndChangesData(detail, changesComposite);
											}
											sync.asyncExec(() -> {
												changesComposite.layout();
												changesScrolledComposite.setContent(changesComposite);
												changesScrolledComposite.setMinSize(
														changesScrolledComposite.getSize().x,
														changesComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).y);
												scrolledComposite.setContent(tabFolder);
											});
											changesLoaded = true;

										} else {
											sync.asyncExec(() -> {
												Composite changesComposite = new Composite(tabFolder, SWT.None);
												changesComposite.setLayout(new GridLayout(1, false));
												tbtmChanges.setControl(changesComposite);
												changesComposite.setLayoutData(
														new GridData(SWT.FILL, SWT.BEGINNING, false, false));

												CLabel noChange = new CLabel(changesComposite, SWT.NONE);
												noChange.setText(PluginConstants.NO_CHANGES);
												noChange.setLayoutData(
														new GridData(SWT.FILL, SWT.BEGINNING, false, false, 1, 1));
												changesComposite.layout();
												changesScrolledComposite.setContent(changesComposite);
												changesScrolledComposite.setMinSize(
														changesScrolledComposite.getSize().x,
														changesComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).y);
												scrolledComposite.setContent(tabFolder);
												changesLoaded = true;
											});

										}
									}

									return Status.OK_STATUS;
								}

							};
							job.schedule();
						} else {
							return;
						}

					}

					@Override
					public void widgetDefaultSelected(SelectionEvent arg0) {
						Composite loadingScreen = new Composite(scrolledComposite, SWT.NONE);
						loadingScreen.setLayout(new GridLayout(1, false));
						loadingScreen.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false));

						CLabel loadingLabel = new CLabel(loadingScreen, SWT.NONE);
						loadingLabel.setText(PluginConstants.LOADING_CHANGES);
						loadingLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

						commentText.setText(PluginConstants.DEFAULT_COMMENT_TXT);

						scrolledComposite.setContent(loadingScreen);

					}

				});

			}

			private void populateIndChangesData(Predicate detail, Composite changesComposite) {
				sync.asyncExec(() -> {
					CLabel createdBy = new CLabel(changesComposite, SWT.NONE);
					createdBy.setImage(USER);
					String user = detail.getCreatedBy();
					if (detail.getCreatedAt() != null) {
						String time = PluginUtils.convertStringTimeStamp(detail.getCreatedAt());
						createdBy.setText(user + " | " + time.replace("|", ""));
					} else {
						createdBy.setText(user);
					}
					createdBy.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

					CLabel severity = new CLabel(changesComposite, SWT.NONE);
					severity.setImage(findSeverityImageString(detail.getSeverity()));
					severity.setText(detail.getSeverity());
					severity.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

					CLabel state = new CLabel(changesComposite, SWT.NONE);
					state.setImage(STATE);
					state.setText(detail.getState());
					state.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

					if (detail.getComment() != null && detail.getComment() != "") {
						CLabel comment = new CLabel(changesComposite, SWT.NONE);
						comment.setImage(COMMENT);
						comment.setText(detail.getComment());
						comment.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
					}

					Label label = new Label(changesComposite, SWT.SEPARATOR | SWT.HORIZONTAL);
					label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
				});

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

	/**
	 * Get triage information
	 * 
	 * @param projectID
	 * @param similarityId
	 * @param scanType
	 * @return
	 */
	private List<Predicate> getTriageInfo(UUID projectID, String similarityId, String scanType) {
		List<Predicate> triageList = new ArrayList<Predicate>();

		try {
			triageList = DataProvider.getInstance().getTriageShow(projectID, similarityId, scanType);
		} catch (Exception e) {
			String errorMessage = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage()
					: e.getMessage();
			PluginUtils.showMessage(rootModel, resultsTree, errorMessage);
		}

		return triageList;
	}

	private void updateAttackVectorForSelectedTreeItem(DisplayModel selectedItem) {
		sync.asyncExec(() -> {
			clearAttackVectorSection();

			if (selectedItem.getType().equalsIgnoreCase(PluginConstants.SCA_DEPENDENCY)) {
				drawPackageData(selectedItem);
			}

			if (selectedItem.getType().equalsIgnoreCase(PluginConstants.KICS_INFRASTRUCTURE)) {
				drawVulnerabilityLocation(selectedItem);
			}

			if (selectedItem.getType().equalsIgnoreCase(PluginConstants.SAST)) {
				drawAttackVector(selectedItem);
			}
			
			layoutAttackVectorItemComposite();
		});
	}

	private void layoutAttackVectorItemComposite() {
		attackVectorCompositePanel.layout();
		attackVectorCompositePanel.setVisible(true);
	}

	private void drawPackageData(DisplayModel selectedItem) {
		ScrolledComposite sc = new ScrolledComposite(attackVectorCompositePanel, SWT.H_SCROLL | SWT.V_SCROLL);

		Composite child = new Composite(sc, SWT.NONE);
		child.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, true, true));
		child.setLayout(new GridLayout(1, false));	
		child.setBackground(attackVectorCompositePanel.getBackground());

		drawAttackVectorTitle(child, PluginConstants.PACKAGE_DATA);
		drawIndividualPackageData(child, selectedItem.getResult().getData().getPackageData());
	   
		sc.setContent(child);
		sc.setMinSize(child.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		sc.setExpandHorizontal(true);
		sc.setExpandVertical(true);
	}
	
	/**
	 * Draw attack vector title
	 * 
	 * @param parent
	 * @param title
	 */
	private void drawAttackVectorTitle(Composite parent, String title) {
		attackVectorLabel = new CLabel(parent, SWT.NONE);
		attackVectorLabel.setFont(titleFont);
		attackVectorLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		attackVectorLabel.setBackground(attackVectorCompositePanel.getBackground());
		attackVectorLabel.setText(title);
		
		attackVectorLabel.layout();
		
		drawAttackVectorSeparator(parent);
	}

	private void drawIndividualPackageData(Composite parent, List<PackageData> packageDataList) {
		if (packageDataList != null && !packageDataList.isEmpty()) {
			for (PackageData packageDataItem : packageDataList) {

				Composite listComposite = createRowComposite(parent);

				CLabel label = createRowLabel(listComposite, String.format("%s | ", packageDataItem.getType()), false);

				Link packageDataLink = createRowLink(listComposite, "<a>" + packageDataItem.getUrl() + "</a>", null);

				generateHoverListener(listComposite, label, packageDataLink);
			}
		} else {
			createRowLabel(parent, "Not available.", false);
		}
	}

	private void drawAttackVector(DisplayModel selectedItem) {
		final TabFolder tabFolder = new TabFolder(attackVectorCompositePanel, SWT.NONE);

		final TabItem attackVectorTab = new TabItem(tabFolder, SWT.NONE);
		attackVectorTab.setText(PluginConstants.ATTACK_VECTOR);

		final TabItem learnMoreTab = new TabItem(tabFolder, SWT.NONE);
		learnMoreTab.setText(PluginConstants.LEARN_MORE);

		final TabItem remediationExamplesTab = new TabItem(tabFolder, SWT.NONE);
		remediationExamplesTab.setText(PluginConstants.REMEDIATION_EXAMPLES);
		
		drawSASTAttackVector(selectedItem, tabFolder, attackVectorTab);
		
		learnMoreData = null;
		
		tabFolder.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {}

			@Override
			public void widgetSelected(SelectionEvent event) {
				String tab = event.item != null ? ((TabItem) event.item).getText() : StringUtils.EMPTY;
				
				switch (tab) {
					case PluginConstants.LEARN_MORE: 
						drawSASTLearnMore(selectedItem, tabFolder, learnMoreTab);
					case PluginConstants.REMEDIATION_EXAMPLES:
						drawSASTRemediationExamples(selectedItem, tabFolder, remediationExamplesTab);
				default: return;
				}
			}
		});
	}
	
	/**
	 * Draw SAST Attack Vector tab
	 * 
	 * @param selectedItem
	 * @param folder
	 * @param attackVectorTab
	 */
	private void drawSASTAttackVector(DisplayModel selectedItem, TabFolder folder, TabItem attackVectorTab) {
		final ScrolledComposite attackVectorScrolledComposite = new ScrolledComposite(folder, SWT.V_SCROLL | SWT.H_SCROLL);
		attackVectorScrolledComposite.setExpandVertical(true);
		attackVectorScrolledComposite.setExpandHorizontal(true);
		attackVectorTab.setControl(attackVectorScrolledComposite);

		final Composite attackVectorComposite = new Composite(attackVectorScrolledComposite, SWT.NONE);
		attackVectorComposite.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, true, true));
		attackVectorComposite.setLayout(new GridLayout(1, false));	
		attackVectorScrolledComposite.setContent(attackVectorComposite);
		
		String queryName = selectedItem.getResult().getData().getQueryName();
		String groupName = selectedItem.getResult().getData().getGroup();
		List<Node> nodesList = selectedItem.getResult().getData().getNodes();

		drawIndividualAttackVectorData(attackVectorComposite, queryName, groupName, nodesList, false);
		
		attackVectorScrolledComposite.setMinSize(attackVectorComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}
	
	/**
	 * Draw SAST Learn More tab
	 * 
	 * @param selectedItem
	 * @param folder
	 * @param learnMoreTab
	 */
	private void drawSASTLearnMore(DisplayModel selectedItem, TabFolder folder, TabItem learnMoreTab) {	
		final ScrolledComposite learnMoreScrolledComposite = new ScrolledComposite(folder, SWT.V_SCROLL);
		learnMoreScrolledComposite.setExpandHorizontal(true);
		learnMoreScrolledComposite.setExpandVertical(true);
		
		final Composite learnMoreComposite = new Composite(learnMoreScrolledComposite, SWT.NONE);
		learnMoreComposite.setLayout(new GridLayout());
		
		learnMoreScrolledComposite.setContent(learnMoreComposite);
		learnMoreScrolledComposite.setMinSize(learnMoreComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		
		if(learnMoreData == null) {
			CLabel loadingLabel = new CLabel(learnMoreComposite, SWT.NONE);
			loadingLabel.setText(PluginConstants.LEARN_MORE_LOADING);
		}
		
		learnMoreTab.setControl(learnMoreScrolledComposite);
		
		Job job = new Job(PluginConstants.GETTING_LEARN_MORE_JOB) {
			@Override
			protected IStatus run(IProgressMonitor arg0) {
				sync.asyncExec(() -> {
					try {
						
						List<LearnMore> learnMoreData = getLearnMoreData(selectedItem.getResult().getData().getQueryId());
						
						clearLearnMoreComposite(learnMoreComposite);
						
						for(LearnMore learnMore : learnMoreData) {
							 addLearnMoreSectionsToComposite(learnMoreComposite, PluginConstants.LEARN_MORE_RISK, learnMore.getRisk().trim());
							 addLearnMoreSectionsToComposite(learnMoreComposite, PluginConstants.LEARN_MORE_CAUSE, learnMore.getCause().trim());
							 addLearnMoreSectionsToComposite(learnMoreComposite, PluginConstants.LEARN_MORE_GENERAL_RECOMMENDATIONS, learnMore.getGeneralRecommendations().trim());
							 
							// Adding CWE link in Learn More section of SAST vulnerability
							String cweId = selectedItem.getResult().getVulnerabilityDetails().getCweId();
							if (cweId != null && !cweId.isEmpty()) {
								String cweUrl = "https://cwe.mitre.org/data/definitions/" + cweId + ".html";
								Link cweLink = new Link(learnMoreComposite, SWT.NONE);
								cweLink.setText("<a>CWE-" + cweId + "</a>");
								cweLink.addListener(SWT.Selection, e -> {
									try {
										PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser()
												.openURL(new java.net.URL(cweUrl));
									} catch (Exception ex) {
										CxLogger.error("Failed to open CWE link: " + ex.getMessage(), ex);
									}
								});
							}

							 learnMoreScrolledComposite.setMinSize(learnMoreComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
							 learnMoreComposite.layout();
						}
					} catch (Exception e) {
						CxLogger.error(String.format(PluginConstants.ERROR_GETTING_LEARN_MORE, e.getMessage()), e);
						
						clearLearnMoreComposite(learnMoreComposite);
						
						Label learnMoreErrorLabel = new Label(learnMoreComposite, SWT.NONE);
						learnMoreErrorLabel.setText(e.getMessage());
						learnMoreScrolledComposite.setContent(learnMoreComposite);
					}
				});
				
				return Status.OK_STATUS;
			}
		};

		job.schedule();
	}
	
	/**
	 * Get and cache learn more data
	 * 
	 * @param queryId
	 * @return
	 * @throws Exception
	 */
	private static List<LearnMore> getLearnMoreData(String queryId) throws Exception{
		if(learnMoreData != null) {
			return learnMoreData;
		}
		
		learnMoreData = DataProvider.getInstance().learnMore(queryId);
		
		return learnMoreData;
	}
	
	/**
	 * Draw SAST Remediation Examples tab
	 * 
	 * @param selectedItem
	 * @param folder
	 * @param remediationExamplesTab
	 */
	private void drawSASTRemediationExamples(DisplayModel selectedItem, TabFolder folder, TabItem remediationExamplesTab) {	
		final ScrolledComposite remediationExamplesScrolledComposite = new ScrolledComposite(folder, SWT.V_SCROLL | SWT.BORDER);
		remediationExamplesScrolledComposite.setExpandHorizontal(true);
		remediationExamplesScrolledComposite.setExpandVertical(true);
		
		final Composite remediationExamplesComposite = new Composite(remediationExamplesScrolledComposite, SWT.NONE);
		remediationExamplesComposite.setLayout(new GridLayout());
		
		remediationExamplesScrolledComposite.setContent(remediationExamplesComposite);
		remediationExamplesScrolledComposite.setMinSize(remediationExamplesComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		
		if(learnMoreData == null) {
			Label loadingLabel = new Label(remediationExamplesComposite, SWT.NONE);
			loadingLabel.setText(PluginConstants.LEARN_MORE_LOADING);
		}
		
		remediationExamplesTab.setControl(remediationExamplesScrolledComposite);
		
		Job job = new Job(PluginConstants.GETTING_LEARN_MORE_JOB) {
			@Override
			protected IStatus run(IProgressMonitor arg0) {
				sync.asyncExec(() -> {
					try {
						
						List<LearnMore> learnMoreData = getLearnMoreData(selectedItem.getResult().getData().getQueryId());
						
						clearLearnMoreComposite(remediationExamplesComposite);
						
						for(LearnMore learnMore : learnMoreData) {
							List<Sample> samples = learnMore.getSamples();
							
							if(samples.size() == 0 ) {
								Label noRemediationLabel = new Label(remediationExamplesComposite, SWT.NONE);
								noRemediationLabel.setText(PluginConstants.NO_REMEDIATION_EXAMPLES);
								remediationExamplesScrolledComposite.setContent(remediationExamplesComposite);
								
								continue;
							}
							
							for(Sample sample : samples) {
								StyledText sampleTitle = new StyledText(remediationExamplesComposite, SWT.WRAP);
								sampleTitle.setText(String.format(PluginConstants.REMEDIATION_EXAMPLE_TITLE_FORMAT, sample.getTitle(), sample.getProgLanguage())); 
								GridData titleLayoutData = new GridData( GridData.FILL_HORIZONTAL ) ;
								titleLayoutData.grabExcessHorizontalSpace = true;
								titleLayoutData.horizontalAlignment = SWT.FILL;
								titleLayoutData.widthHint = remediationExamplesScrolledComposite.getClientArea().width - SCROLL_WIDTH;
								titleLayoutData.horizontalSpan = 2;
								sampleTitle.setLayoutData(titleLayoutData);
								sampleTitle.setMargins(2, 5, 2, 5);
																	
								Composite sampleExampleComposite = new Composite(remediationExamplesComposite, SWT.NONE);
								sampleExampleComposite.setBackground(remediationExamplesComposite.getBackground());
								GridLayout layout = new GridLayout();
								layout.marginHeight = 10;
								layout.marginWidth = 10;
								sampleExampleComposite.setLayout(layout);
								sampleExampleComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
								
								Label sampleExample = new Label(sampleExampleComposite, SWT.WRAP);
								sampleExample.setText(sample.getCode()); 
								GridData exampleLayoutData = new GridData(GridData.FILL_HORIZONTAL) ;
								exampleLayoutData.grabExcessHorizontalSpace = true;
								exampleLayoutData.horizontalAlignment = SWT.FILL;
								exampleLayoutData.widthHint = remediationExamplesScrolledComposite.getClientArea().width - SCROLL_WIDTH;
								exampleLayoutData.horizontalSpan = 2;
								sampleExample.setLayoutData(exampleLayoutData);
							
								remediationExamplesScrolledComposite.setMinSize(remediationExamplesComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
								remediationExamplesComposite.layout();
							}
						}
					} catch (Exception e) {
						CxLogger.error(String.format(PluginConstants.ERROR_GETTING_LEARN_MORE, e.getMessage()), e);
						
						clearLearnMoreComposite(remediationExamplesComposite);
						
						Label remediationErrorLabel = new Label(remediationExamplesComposite, SWT.NONE);
						remediationErrorLabel.setText(e.getMessage());
						remediationExamplesScrolledComposite.setContent(remediationExamplesComposite);
					}
				});
				
				return Status.OK_STATUS;
			}
		};

		job.schedule();
	}
	
	/**
	 * Clear Learn More composite
	 * 
	 * @param learnMoreComposite
	 */
	private void clearLearnMoreComposite(Composite learnMoreComposite) {
		for (Control child : learnMoreComposite.getChildren()) {
			child.dispose();
		}
	}
	
	/**
	 * Add Learn More sections to composite (Risk, Cause, General Recommendations)
	 * 
	 * @param composite
	 * @param title
	 * @param description
	 * @return
	 */
	private void addLearnMoreSectionsToComposite(Composite composite, String title, String description) {
		Label titleLabel = new Label(composite, SWT.WRAP);
		titleLabel.setText(title); 
		titleLabel.setFont(boldFont);

		StyledText descriptionLabel = new StyledText(composite, SWT.WRAP);
		descriptionLabel.setText(description); 
		GridData descriptionLayout = new GridData(GridData.FILL_HORIZONTAL);
		descriptionLayout.grabExcessHorizontalSpace = true;
		descriptionLayout.horizontalAlignment = SWT.FILL;
		descriptionLayout.widthHint = composite.getClientArea().width - SCROLL_WIDTH;
		descriptionLayout.horizontalSpan = 2;
		descriptionLabel.setLayoutData(descriptionLayout);
		descriptionLabel.setBottomMargin(20);
	}

	/*private void populateBFLMessage(Image image, String bflMessage) {
		bflLabel.setImage(image);
		bflText.setText(bflMessage);
		bflLabel.layout();
		bflText.requestLayout();

	}*/

	private void drawIndividualAttackVectorData(Composite parent, String queryName, String groupName, List<Node> nodesList, Boolean populateBFLNode) {		
		if (nodesList != null && !nodesList.isEmpty()) {
			for (int i = 0; i < nodesList.size(); i++) {

				Node node = nodesList.get(i);

				Composite listComposite = createRowComposite(parent);

				CLabel label = createRowLabel(listComposite, String.format("%s | %s", i + 1, node.getName()), populateBFLNode ? i == bflNode : false);

				Link attackVectorValueLinkText = createRowLink(listComposite,
						String.format("<a>%s[%d,%d]</a>", node.getFileName(), node.getLine(), node.getColumn()),
						new Listener() {
							public void handleEvent(Event event) {
								openTheSelectedFile(node.getFileName(), node.getLine(),
										groupName + "_" + queryName + "_" + node.getName());
							}
						});

				generateHoverListener(listComposite, label, attackVectorValueLinkText);
			}
		} else {
			createRowLabel(parent, "Not available.", false);
		}
	}

	/*private void populateBFLNode(Composite parent, DisplayModel selectedItem) {

		Job job = new Job("Loading BFL node") {

			Composite itemComposite;

			@Override
			protected IStatus run(IProgressMonitor arg0) {

				try {
					bflNode = DataProvider.getInstance().getBestFixLocation(UUID.fromString(currentScanId),
							selectedItem.getResult().getData().getQueryId(),
							selectedItem.getResult().getData().getNodes());
					String queryName = selectedItem.getResult().getData().getQueryName();
					String groupName = selectedItem.getResult().getData().getGroup();
					List<Node> nodesList = selectedItem.getResult().getData().getNodes();

					sync.asyncExec(() -> {
						if (bflNode != -1) {
							parent.dispose();
							itemComposite = createAttackVectorComposite();
							populateBFLMessage(BFL, PluginConstants.BFL_FOUND);
							drawIndividualAttackVectorData(itemComposite, queryName, groupName, nodesList, true);
						} else {
							populateBFLMessage(null, PluginConstants.BFL_NOT_FOUND);
						}

					});
				} catch (Exception e) {
					CxLogger.error(String.format(PluginConstants.ERROR_GETTING_BEST_FIX_LOCATION, e.getMessage()), e);
				}
				return Status.OK_STATUS;
			}

		};
		
		job.schedule();
	}*/

	private void drawVulnerabilityLocation(DisplayModel selectedItem) {		
		ScrolledComposite sc = new ScrolledComposite(attackVectorCompositePanel, SWT.H_SCROLL | SWT.V_SCROLL);

		Composite child = new Composite(sc, SWT.NONE);
		child.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, true, true));
		child.setLayout(new GridLayout(1, false));	
		child.setBackground(attackVectorCompositePanel.getBackground());

		drawAttackVectorTitle(child, PluginConstants.LOCATION);
		drawIndividualLocationData(child, selectedItem);
	   
		sc.setContent(child);
		sc.setMinSize(child.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		sc.setExpandHorizontal(true);
		sc.setExpandVertical(true);
	}

	private void drawIndividualLocationData(Composite parent, DisplayModel selectedItem) {
		Composite listComposite = createRowComposite(parent);

		CLabel label = createRowLabel(listComposite, "File | ", false);

		Link fileNameValueLinkText = createRowLink(listComposite,
				"<a>" + selectedItem.getResult().getData().getFileName() + "["
						+ selectedItem.getResult().getData().getLine() + "]" + "</a>",
				new Listener() {
					public void handleEvent(Event event) {
						openTheSelectedFile(selectedItem.getResult().getData().getFileName(),
								selectedItem.getResult().getData().getLine(), null);
					}
				});

		generateHoverListener(listComposite, label, fileNameValueLinkText);
	}

	private void generateHoverListener(Composite listComposite, CLabel label, Link fileNameValueLinkText) {
		HoverListener hoverListener = new HoverListener(Arrays.asList(listComposite, label, fileNameValueLinkText));
		hoverListener.apply();
		listComposite.layout();
		label.layout();
		fileNameValueLinkText.requestLayout();
	}

	private static Composite createRowComposite(Composite parent) {
		Composite rowComposite = new Composite(parent, SWT.NONE);
		rowComposite.setBackground(parent.getBackground());
		GridLayout layout = new GridLayout();
		layout.marginHeight = 10;
		layout.numColumns = 2;
		rowComposite.setLayout(layout);
		rowComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		return rowComposite;
	}

	private CLabel createRowLabel(Composite rowComposite, String text, Boolean isBflNode) {
		CLabel label = new CLabel(rowComposite, SWT.NONE);
		if (isBflNode) {
			label.setImage(BFL);
		} else {
			label.setImage(null);
		}
		label.setBackground(rowComposite.getBackground());
		label.setFont(boldFont);
		label.setText(text);
		label.requestLayout();
		
		return label;
	}

	private Link createRowLink(Composite rowComposite, String text, Listener selectionListener) {
		Link link = new Link(rowComposite, SWT.NONE);
		link.setBackground(rowComposite.getBackground());
		link.setText(text);
		if (selectionListener != null) {
			link.addListener(SWT.Selection, selectionListener);
		}
		return link;
	}

	private void clearAttackVectorSection() {
		for (Control child : attackVectorCompositePanel.getChildren()) {
			child.dispose();
		}
	}

	private void openTheSelectedFile(String fileName, Integer lineNumber, String markerDescription) {
		Path filePath = new Path(fileName);
		List<IFile> filesFound = PluginUtils.findFileInWorkspace(filePath.lastSegment());

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
				updateResultsTree(definition.getResutls(), false);
				break;
			case CLEAN_AND_REFRESH:
				clearAndRefreshPlugin();
				break;
			case LOAD_RESULTS_FOR_SCAN:
				setSelectionForProjectComboViewer();
				break;
			default:
				break;
		}
	}

	private void updateResultsTree(List<DisplayModel> results, boolean expand) {
		sync.asyncExec(() -> {
			rootModel.children.clear();
			rootModel.children.addAll(results);
			Object[] expanded = resultsTree.getExpandedElements();
			resultsTree.refresh();
			if (expand) {
				Set<String> expandedDMNames = new HashSet<>();
				Set<Result> visibleResults = new HashSet<>();
				for (Object o : expanded) {
					DisplayModel dm = (DisplayModel) o;
					expandedDMNames.add(removeCount(dm.getName()));
					for (DisplayModel child : dm.getChildren()) {
						Result r = child.getResult();
						if (r != null) {
							visibleResults.add(r);
						}
					}
				}
				expand(visibleResults, expandedDMNames, rootModel);
			}

			toolBarActions.getStateFilterAction().setEnabled(true);
			PluginUtils.enableComboViewer(projectComboViewer, true);
			PluginUtils.enableComboViewer(branchComboViewer, !currentProjectId.isEmpty());
			PluginUtils.enableComboViewer(scanIdComboViewer, true);
			alreadyRunning = false;

			if (results.isEmpty()) {
				PluginUtils.showMessage(rootModel, resultsTree, PluginConstants.TREE_NO_RESULTS);
			}

			PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
		});
	}

	/**
	 * Update results tree
	 * 
	 * @param results
	 * @param expand  try expanding tree to previous state
	 */
	private void updateResultsTree(String scanId, boolean expand) {
		Job job = new Job("Checkmarx: Getting results...") {

			@Override
			protected IStatus run(IProgressMonitor arg0) {
				List<DisplayModel> results = DataProvider.getInstance().getResultsForScanId(scanId);

				updateResultsTree(results, expand);
				return Status.OK_STATUS;
			}

		};
		job.schedule();
	}

	/**
	 * iterate tree and expand previously visible nodes and leafs. when finding a
	 * leaf to expand, recursively expand parents as it could have changed to a
	 * previously collapsed severity
	 * 
	 * @param visibleResults
	 * @param expandedNodes
	 * @param current
	 */
	private void expand(Set<Result> visibleResults, Set<String> expandedNodes, DisplayModel current) {
		for (DisplayModel child : current.getChildren()) {
			child.parent = current;
			if (visibleResults.contains(child.getResult())) {
				resultsTree.setExpandedState(child, true);
				DisplayModel parent = child.parent;
				while (parent != null) {
					resultsTree.setExpandedState(parent, true);
					parent = parent.parent;
				}
			} else if (child.getChildren().size() > 0 && expandedNodes.contains(removeCount(child.getName()))) {
				resultsTree.setExpandedState(child, true);
			}
			expand(visibleResults, expandedNodes, child);
		}
	}

	/**
	 * name should end in " (XYZ)" so lastIndexOf is always the token with the child
	 * count
	 */
	private static String removeCount(String name) {
		return name.substring(0, name.lastIndexOf('(') - 1);
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
		updateStartScanButton(false);

		// Hide center and right panels
		resultViewComposite.setVisible(false);
		attackVectorCompositePanel.setVisible(false);

		// Empty branches and scans comboboxes
		clearBranchComboViewer();
		clearScanIdComboViewer();

		// Reset filters state
		resetFiltersState();

		// Reset state variables
		currentProjectId = PluginConstants.EMPTY_STRING;
		currentBranch = PluginConstants.EMPTY_STRING;
		currentScanId = PluginConstants.EMPTY_STRING;

		// Update preferences values
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_PROJECT_ID, PluginConstants.EMPTY_STRING);
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_BRANCH, PluginConstants.EMPTY_STRING);
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, PluginConstants.EMPTY_STRING);

		Job projectsJob = new Job("Checkmarx: Loading projects...") {

			@Override
			protected IStatus run(IProgressMonitor arg0) {
				currentProjects = getProjects();
				storeCurrentProjects = currentProjects;
				sync.asyncExec(() -> {
					projectComboViewer.setInput(currentProjects);
					projectComboViewer.refresh();
					PluginUtils.setTextForComboViewer(projectComboViewer, PROJECT_COMBO_VIEWER_TEXT);
					PluginUtils.enableComboViewer(projectComboViewer, true);
					PluginUtils.enableComboViewer(scanIdComboViewer, true);
				});
				return Status.OK_STATUS;
			}

		};
		projectsJob.schedule();

		// Clear vulnerabilities from Problems View
		PluginUtils.clearVulnerabilitiesFromProblemsView();
		
		toolBarActions.refreshToolbar();
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
		Job job = new Job("Checkmarx: Refreshing branch and scans") {
			@Override
			protected IStatus run(IProgressMonitor arg0) {
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						PluginUtils.enableComboViewer(projectComboViewer, false);
						PluginUtils.enableComboViewer(branchComboViewer, false);
						PluginUtils.setTextForComboViewer(branchComboViewer, PluginConstants.COMBOBOX_BRANCH_CHANGING);
						loadingScans();
						PluginUtils.showMessage(rootModel, resultsTree, PluginConstants.EMPTY_STRING);
						resultViewComposite.setVisible(false);
						attackVectorCompositePanel.setVisible(false);

						if (disableToolBar) {
							toolBarActions.getToolBarActions().forEach(action -> action.setEnabled(false));
						}
					}
				});
				
				return Status.OK_STATUS;
			}

		};
		job.schedule();
	}

	/**
	 * Enable comboboxes
	 */
	private void enablePluginFields(boolean enableBranchCombobox) {
		PluginUtils.enableComboViewer(projectComboViewer, true);
		PluginUtils.enableComboViewer(branchComboViewer, enableBranchCombobox);
		PluginUtils.enableComboViewer(scanIdComboViewer, true);
		boolean resultsAvailable = DataProvider.getInstance().containsResults();

		for (Action action : toolBarActions.getToolBarActions()) {
			String actionName = action.getId();

			if (actionName.equals(ActionName.GROUP_BY_SEVERITY.name()) && !actionName.equals(ActionName.GROUP_BY_QUERY_NAME.name())) {
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
		String currentApiKey = Preferences.STORE.getString(Preferences.API_KEY);
		if (!currentApiKey.isEmpty() && !isPluginDraw) {
			drawPluginPanel();
		} else {
			// If credentials changed reload projects, branches and scans from new tenant
			if (currentApiKey.isEmpty()) {
				updateStartScanButton(false);
				drawMissingCredentialsPanel();
				//Dispose toolbar
			    if (toolBarActions != null) {
			        toolBarActions.disposeToolbar();
			        toolBarActions = null;
			    }
				isPluginDraw = false;
			} else if (lastApiKey.equalsIgnoreCase(currentApiKey)) {
				return;
			} else {
				// clear result section
				PluginUtils.clearMessage(rootModel, resultsTree);
				// Reset state variables
				currentProjectId = PluginConstants.EMPTY_STRING;
				currentBranch = PluginConstants.EMPTY_STRING;
				currentScanId = PluginConstants.EMPTY_STRING;
				loadComboboxes();
			}
			lastApiKey=currentApiKey;
		}
	}

	/**
	 * Get projects from One and draw an error message in the tree if an error
	 * occurred
	 * 
	 * @return
	 */
	private List<Project> getProjects() {
		List<Project> projectList = new ArrayList<>();

		try {
			projectList = DataProvider.getInstance().getProjects();
		} catch (Exception e) {
			String errorMessage = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage()
					: e.getMessage();
			PluginUtils.showMessage(rootModel, resultsTree, errorMessage);
		}

		return projectList;
	}
	
	/**
	 * Update scan button with proper tooltip
	 * 
	 * @param enabled
	 */
	private void updateStartScanButton(boolean enabled) {		
		if(enabled) {
			String runningScanId = GlobalSettings.getFromPreferences(GlobalSettings.PARAM_RUNNING_SCAN_ID, PluginConstants.EMPTY_STRING);
			boolean  isScanRunning = StringUtils.isNoneEmpty(runningScanId);
			boolean branchSelected = StringUtils.isNotBlank(GlobalSettings.getFromPreferences(GlobalSettings.PARAM_BRANCH, PluginConstants.EMPTY_STRING));
						
			toolBarActions.getStartScanAction().setEnabled(!isScanRunning && branchSelected);
		} else {
			toolBarActions.getStartScanAction().setEnabled(false);
		}
	}
	
	/**
	 * Store the user entered value and the caret position
	 * 
	 * @param projectList
	 * @param searchText
	 */
	private void preservCaretposition( List<Project> projectList, String searchText) {
		int caretPos = projectComboViewer.getCombo().getCaretPosition();
		projectComboViewer.setInput(projectList);
		PluginUtils.setTextForComboViewer(projectComboViewer, searchText);
		projectComboViewer.getCombo().setSelection(new Point(caretPos, caretPos));
	}
}