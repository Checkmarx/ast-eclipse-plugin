package com.checkmarx.eclipse.views;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.PackageData;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.actions.ActionName;
import com.checkmarx.eclipse.views.actions.ActionOpenPreferencesPage;
import com.checkmarx.eclipse.views.actions.ToolBarActions;
import com.checkmarx.eclipse.views.filters.FilterState;
import com.checkmarx.eclipse.views.provider.ColumnProvider;
import com.checkmarx.eclipse.views.provider.TreeContentProvider;
import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

public class CheckmarxView extends ViewPart {
	
	private static final String PROJECT_COMBO_VIEWER_TEXT = "Select a project";
	private static final String SCAN_COMBO_VIEWER_TEXT = "Select a scan";
	private static final String BRANCH_COMBO_VIEWER_TEXT = "Select a branch";
	private static final String LOADING_PROJECTS = "Loading projects...";
	private static final String LOADING_BRANCHES = "Loading branches...";
	private static final String NO_BRANCHES_AVAILABLE = "No branches available.";
	private static final String NO_PROJECTS_AVAILABLE = "No projects available.";
	private static final String VERTICAL_SEPERATOR = "|";
	private static final String FORMATTED_SCAN_LABEL = "%s (%s, %s)";

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "com.checkmarx.eclipse.views.CheckmarxView";

	public static final Image CRITICAL_SEVERITY = Activator.getImageDescriptor("/icons/severity-critical.png").createImage();

	public static final Image HIGH_SEVERITY = Activator.getImageDescriptor("/icons/severity-high.png").createImage();

	public static final Image MEDIUM_SEVERITY = Activator.getImageDescriptor("/icons/severity-medium.png").createImage();

	public static final Image LOW_SEVERITY = Activator.getImageDescriptor("/icons/severity-low.png").createImage();

	public static final Image INFO_SEVERITY = Activator.getImageDescriptor("platform:/plugin/org.eclipse.ui/icons/full/obj16/info_tsk.png").createImage();
	
	private TreeViewer viewer;
	private ComboViewer scanIdComboViewer, projectComboViewer, branchComboViewer;
	private DisplayModel rootModel;
	private Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

	private boolean alreadyRunning = false;

	Font boldFont;
	private Text summaryText;
	private Text descriptionValueText;
	private Text attackVectorValueLinkText;

	private Composite topComposite;
	private Composite resultInfoCompositePanel , attackVectorCompositePanel;
	private Composite leftCompositePanel;
	
	private ToolBarActions toolBarActions;
	
	private EventBus pluginEventBus;
	
	private GlobalSettings globalSettings = new GlobalSettings();
		
	private String currentProjectId = PluginConstants.EMPTY_STRING;
	private String currentBranch =  PluginConstants.EMPTY_STRING;
	private String currentScanId =  PluginConstants.EMPTY_STRING;
	private static String currentScanIdFormmated =  PluginConstants.EMPTY_STRING;
	
	private boolean scansCleanedByProject = false; 
	private boolean firstTimeTriggered = false; 
	
	public CheckmarxView() {
		super();
		
		rootModel = new DisplayModel.DisplayModelBuilder(PluginConstants.EMPTY_STRING).build();
		globalSettings.loadSettings();
	}

	@Override
	public void dispose() {
		super.dispose();
		boldFont.dispose();
		pluginEventBus.unregister(this);
	}

	@Override
	public void createPartControl(Composite parent) {
		createViewer(parent);
		createToolbar();
		createContextMenu();
		initGitBranchListener();
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
		if (!currentBranch.equals(gitBranch)) {
			disablePluginFields();

			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					setSelectionForBranchComboViewer(gitBranch, currentProjectId);
					setSelectionForScanIdComboViewer(PluginConstants.EMPTY_STRING, gitBranch);
					enablePluginFields();
				}
			});
		}
	}
	
	private void createContextMenu() {
		MenuManager menuManager = new MenuManager("#PopupMenu");
		menuManager.setRemoveAllWhenShown(true);
		menuManager.addMenuListener(CheckmarxView.this::fillContextMenu);
		Menu menu = menuManager.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);

		getSite().registerContextMenu(menuManager, viewer);
	}

	private void fillContextMenu(IMenuManager manager) {
		Action openPreferencesPageAction = new ActionOpenPreferencesPage(rootModel, viewer, shell).createAction();
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
		
		toolBarActions = new ToolBarActions.ToolBarActionsBuilder()
				.actionBars(actionBars)
				.rootModel(rootModel)
				.resultsTree(viewer)
				.pluginEventBus(pluginEventBus)
				.build();
				
		for(Action action : toolBarActions.getToolBarActions()) {
			toolBarManager.add(action);
						
			// Add divider
			if(action.getId() != null && action.getId().equals(ActionName.INFO.name())) {
				toolBarManager.add(new Separator("\t"));
			}
		}
	}

	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	private void createViewer(Composite parent) {
		GridLayout parentLayout = new GridLayout();
		parentLayout.numColumns = 1;
		parentLayout.horizontalSpacing = 0;
		parentLayout.verticalSpacing = 0;
		parentLayout.marginHeight = 0;
		parentLayout.marginWidth = 0;
		parent.setLayout(parentLayout);
		
		//Top Bar Composite Panel
		topComposite = new Composite(parent, SWT.NONE);
		GridLayout topLayout = new GridLayout();
		topLayout.numColumns = 3;
		topComposite.setLayout(topLayout);
		
		GridData topGridData = new GridData();
		topGridData.horizontalAlignment = GridData.FILL;
		topGridData.verticalAlignment = GridData.FILL;
		topGridData.grabExcessHorizontalSpace = true;
		topComposite.setLayoutData(topGridData);
		createProjectListComboBox(topComposite);
		createBranchComboBox(topComposite);
		createScanIdComboBox(topComposite);
		
		//Bottom Panel
		Composite bottomComposite = new Composite(parent, SWT.BORDER);
		
		GridData bottomGridData = new GridData();
		bottomGridData.horizontalAlignment = GridData.FILL;
		bottomGridData.verticalAlignment = GridData.FILL;
		bottomGridData.grabExcessHorizontalSpace = true;
		bottomGridData.grabExcessVerticalSpace = true;

		bottomComposite.setLayout(new FillLayout(SWT.HORIZONTAL));
		bottomComposite.setLayoutData(bottomGridData);
		
		leftCompositePanel = new Composite(bottomComposite, SWT.BORDER);
		GridLayout 	leftCompositeLayout = new GridLayout();
		leftCompositeLayout.numColumns = 1;
		GridData leftCompositePanelGridData = new GridData();
		leftCompositePanelGridData.horizontalAlignment = GridData.BEGINNING;
		leftCompositePanelGridData.grabExcessVerticalSpace = true;
		leftCompositeLayout.marginWidth = 0;
		leftCompositeLayout.marginHeight = 0;
		
		leftCompositePanel.setLayoutData(leftCompositePanelGridData);
		leftCompositePanel.setLayout(leftCompositeLayout);				

		viewer = new TreeViewer(leftCompositePanel, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION );

		//Display initial message
		boolean gettingResults = globalSettings.getProjectId() != null && !globalSettings.getProjectId().isEmpty() && globalSettings.getScanId() != null && !globalSettings.getScanId().isEmpty();
		boolean noProjectsAvailable = projectComboViewer.getCombo().getText().equals(NO_PROJECTS_AVAILABLE);
		String message = gettingResults && !noProjectsAvailable ? String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, globalSettings.getScanId()) : PluginConstants.EMPTY_STRING;
		PluginUtils.showMessage(rootModel, viewer, message);
		
		ColumnViewerToolTipSupport.enableFor(viewer);
		createColumns();

		viewer.getTree().setHeaderVisible(false);
		viewer.getTree().setLinesVisible(true);

		viewer.setContentProvider(new TreeContentProvider());
		getSite().setSelectionProvider(viewer);

		// define layout for the viewer
		GridData gridData = new GridData();
		gridData.verticalAlignment = GridData.FILL;
		gridData.horizontalSpan = 2;
		gridData.grabExcessHorizontalSpace = true;
		gridData.grabExcessVerticalSpace = true;
		gridData.horizontalAlignment = GridData.FILL;
		viewer.getControl().setLayoutData(gridData);
		viewer.setInput(rootModel);

		// configureSelectionListener
		configureTreeItemSelectionChangeEvent(viewer);

		// SECTION 2
		// Setting the BOLD Font for Labels
		Display display = parent.getShell().getDisplay();
		FontData systemFontData = display.getSystemFont().getFontData()[0];
		boldFont = new Font(display, systemFontData.getName(), systemFontData.getHeight(), SWT.BOLD);

		resultInfoCompositePanel = new Composite(bottomComposite, SWT.BORDER);
		resultInfoCompositePanel.setLayout(new GridLayout(1, false));

		Label summaryLabel = new Label(resultInfoCompositePanel, SWT.NONE);
		summaryLabel.setFont(boldFont);
		summaryLabel.setText("Summary:");
		summaryLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));

		summaryText = new Text(resultInfoCompositePanel, SWT.READ_ONLY | SWT.WRAP);
		summaryText.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		summaryText.setText("Not Available.");


		Label descriptionLabel = new Label(resultInfoCompositePanel, SWT.NONE);
		descriptionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		descriptionLabel.setFont(boldFont);
		descriptionLabel.setText("Description:");

		descriptionValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP);
		descriptionValueText.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, true, 1, 1));
		descriptionValueText.setText("Not Available.");

		// Section 3
		attackVectorCompositePanel = new Composite(bottomComposite, SWT.BORDER);
		
		GridData attackVectorCompositePanelGridData = new GridData();
		attackVectorCompositePanelGridData.horizontalAlignment = GridData.END;
		attackVectorCompositePanelGridData.grabExcessHorizontalSpace = true;
		attackVectorCompositePanelGridData.grabExcessVerticalSpace = true;
		
		attackVectorCompositePanel.setLayoutData(attackVectorCompositePanelGridData);
		
		attackVectorCompositePanel.setLayout(new RowLayout(SWT.VERTICAL));

		Label attackVectorLabel = new Label(attackVectorCompositePanel, SWT.NONE);
		attackVectorLabel.setFont(boldFont);
		attackVectorLabel.setText("Attack Vector:");
		
		resultInfoCompositePanel.setVisible(false);
		attackVectorCompositePanel.setVisible(false);
	}
	
	private void createProjectListComboBox(Composite parent) {
		List<Project> projectList = DataProvider.getInstance().getProjects();
		currentProjectId = globalSettings.getProjectId();
		String currentProjectName = getProjectFromId(projectList, currentProjectId);

		projectComboViewer = new ComboViewer(parent, SWT.DROP_DOWN);
		projectComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		projectComboViewer.setInput(projectList);

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
										
					currentProjectId = selectedProject.getID();
					GlobalSettings.storeInPreferences(GlobalSettings.PARAM_PROJECT_ID, selectedProject.getID());
					GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, PluginConstants.EMPTY_STRING);
					
					loadingBranches();
					PluginUtils.enableComboViewer(scanIdComboViewer, false);
					PluginUtils.showMessage(rootModel, viewer, PluginConstants.EMPTY_STRING);
					DataProvider.getInstance().setCurrentResults(null);
					
					scanIdComboViewer.setInput(Collections.emptyList());
					PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
					
					scansCleanedByProject = true;
					
					Display.getDefault().asyncExec(new Runnable() {
					    public void run() {
					    	List<String> branchList = DataProvider.getInstance().getBranchesForProject(selectedProject.getID());

							if (branchList != null && !branchList.isEmpty()) {
								branchComboViewer.setInput(branchList);
								PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
							} else {
								branchComboViewer.setInput(branchList);
								PluginUtils.setTextForComboViewer(branchComboViewer, NO_BRANCHES_AVAILABLE);
							}
							
							PluginUtils.enableComboViewer(branchComboViewer, true);
							PluginUtils.enableComboViewer(scanIdComboViewer, true);
							PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
					    }
					});
				}
			}
		});
		
		PluginUtils.setTextForComboViewer(projectComboViewer, currentProjectName);
	}
	
	/**
	 * Get project name from project Id
	 * 
	 * @param projects
	 * @param projectId
	 * @return
	 */
	private String getProjectFromId(List<Project> projects, String projectId) {
		if(projects.isEmpty()) {
			return NO_PROJECTS_AVAILABLE;
		}
		
		Optional<Project> project = projects.stream().filter(p -> p.getID().equals(projectId)).findFirst();
		
		return project.isPresent() ? project.get().getName() : PROJECT_COMBO_VIEWER_TEXT;
	}
	
	private void createBranchComboBox(Composite parent) {
		currentBranch = globalSettings.getBranch();
		branchComboViewer = new ComboViewer(parent, SWT.DROP_DOWN);
		branchComboViewer.setContentProvider(ArrayContentProvider.getInstance());

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
					
					scansCleanedByProject = false;

					currentBranch = selectedBranch;
					PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_GETTING_SCANS);
					
					GlobalSettings.storeInPreferences(GlobalSettings.PARAM_BRANCH, selectedBranch);
					DataProvider.getInstance().setCurrentResults(null);
					
					List<Scan> scanList = DataProvider.getInstance().getScansForProject(selectedBranch);

					if (!scanList.isEmpty()) {
						scanIdComboViewer.setInput(scanList);
						PluginUtils.setTextForComboViewer(scanIdComboViewer, SCAN_COMBO_VIEWER_TEXT);
						PluginUtils.enableComboViewer(scanIdComboViewer, true);
					} else {
						scanIdComboViewer.setInput(scanList);
						PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_NO_SCANS_AVAILABLE);
						PluginUtils.enableComboViewer(scanIdComboViewer, true);
						currentScanId = PluginConstants.EMPTY_STRING;
						GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, PluginConstants.EMPTY_STRING);
					}
					
					PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
				}
			}
		});

		PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
		PluginUtils.enableComboViewer(branchComboViewer, false);
		
		boolean noProjectsAvailable = projectComboViewer.getCombo().getText().equals(NO_PROJECTS_AVAILABLE);
		if(noProjectsAvailable) {
			PluginUtils.setTextForComboViewer(branchComboViewer, NO_BRANCHES_AVAILABLE);
		}else {
			if (!globalSettings.getProjectId().isEmpty()) {
				List<String> branchList = DataProvider.getInstance().getBranchesForProject(globalSettings.getProjectId());
				branchComboViewer.setInput(branchList);

				PluginUtils.setTextForComboViewer(branchComboViewer, currentBranch);
				
				if(!currentBranch.isEmpty()) {
					PluginUtils.enableComboViewer(branchComboViewer, true);
				}
			}
		}
		

		GridData gridData = new GridData();
		gridData.widthHint = 150;
		branchComboViewer.getCombo().setLayoutData(gridData);
	}
	
	private void createScanIdComboBox(Composite parent){
		currentScanId = globalSettings.getScanId();
		scanIdComboViewer = new ComboViewer(parent, SWT.DROP_DOWN | SWT.SIMPLE);        
		scanIdComboViewer.setContentProvider(ArrayContentProvider.getInstance());
			
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
					
					currentScanId = selectedScan.getID();
					currentScanIdFormmated = scanIdComboViewer.getCombo().getText();
					
					GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, selectedScan.getID());

					PluginUtils.showMessage(rootModel, viewer, String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, selectedScan.getID()));
					DataProvider.getInstance().setCurrentResults(null);
					toolBarActions.getScanResultsAction().setEnabled(false);
					toolBarActions.getAbortResultsAction().setEnabled(true);
				
					Display.getDefault().asyncExec(new Runnable() {
					    public void run() {
					    	alreadyRunning = true;
							updateResultsTree(DataProvider.getInstance().getResultsForScanId(selectedScan.getID()));
					    }
					});
				}
			}
		});
		
		PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
		
		if(!globalSettings.getBranch().isEmpty()) {
			List<Scan> scanList = DataProvider.getInstance().getScansForProject(globalSettings.getBranch());
			scanIdComboViewer.setInput(scanList);
			
			String currentScanName = getScanNameFromId(scanList, currentScanId);
			currentScanIdFormmated = currentScanName;
			PluginUtils.setTextForComboViewer(scanIdComboViewer, currentScanName);
			
			if(currentScanId != null && !currentScanId.isEmpty()) {
				Display.getDefault().asyncExec(new Runnable() {
				    public void run() {
				    	alreadyRunning = true;
				    	updateResultsTree(DataProvider.getInstance().getResultsForScanId(currentScanId));
				    }
				});
			}
		}
		
		GridData gridData = new GridData();
		gridData.widthHint = 450;
		scanIdComboViewer.getCombo().setLayoutData(gridData);
		
		boolean enableScanCombo = !projectComboViewer.getCombo().getText().isEmpty();
		PluginUtils.enableComboViewer(scanIdComboViewer, enableScanCombo);

		scanIdComboViewer.getCombo().addListener(SWT.DefaultSelection, new Listener() {
			public void handleEvent(Event event) {
				toolBarActions.getScanResultsAction().run();		
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
		       
        return String.format(FORMATTED_SCAN_LABEL, scan.getID(), scan.getStatus(), updatedAtDate);
	}
	
	/**
	 * Reverse selection - Populate project combobox and select a project id based on the chosen scan id
	 */
	private void setSelectionForProjectComboViewer() {
		
		// TODO: this validation shouldn't be needed after authentication panel developments. When the authentication is not set the user won't be able to perform a reverse selection
		List<DisplayModel> validationError = DataProvider.getInstance().validateAuthentication();
		if(!validationError.isEmpty()) {
			updateResultsTree(validationError);
			return;
		}
		
		String scanId = scanIdComboViewer.getCombo().getText();
		
		if(currentScanId.equals(scanId)) {
			PluginUtils.setTextForComboViewer(scanIdComboViewer, currentScanIdFormmated);
			CxLogger.info(String.format(PluginConstants.INFO_RESULTS_ALREADY_RETRIEVED, scanId));
			return;
		}
		
		if (!PluginUtils.validateScanIdFormat(scanId)) {
			PluginUtils.showMessage(rootModel, viewer, PluginConstants.TREE_INVALID_SCAN_ID_FORMAT);
			return;
		}
		
		toolBarActions.getScanResultsAction().setEnabled(true);
		toolBarActions.getAbortResultsAction().setEnabled(false);
		PluginUtils.showMessage(rootModel, viewer, String.format(PluginConstants.RETRIEVING_RESULTS_FOR_SCAN, scanId));
		loadingProjects();
		loadingBranches();
		
		Display.getDefault().asyncExec(new Runnable() {
		    public void run() {
		    	Scan scan = DataProvider.getInstance().getScanInformation(scanId);
		    	
		    	if(scan == null) {
		    		resetProjectsAndBranchesCombo();
		    		PluginUtils.showMessage(rootModel, viewer, String.format(PluginConstants.TREE_PROVIDED_SCAN_ID_DOES_NOT_EXIST, scanId));
		    		return;
		    	}
				
				String projectId = scan.getProjectID();

				List<Project> projectList = DataProvider.getInstance().getProjects();

				if (projectList != null) {
					projectComboViewer.setInput(projectList);

					String projectName = getProjectFromId(projectList, projectId);

					PluginUtils.enableComboViewer(projectComboViewer, true);
					PluginUtils.setTextForComboViewer(projectComboViewer, projectName);

					currentProjectId = projectId;
					GlobalSettings.storeInPreferences(GlobalSettings.PARAM_PROJECT_ID, currentProjectId);
				} else {
					PluginUtils.enableComboViewer(projectComboViewer, true);
					PluginUtils.setTextForComboViewer(projectComboViewer, NO_PROJECTS_AVAILABLE);
				}

				setSelectionForBranchComboViewer(scan.getBranch(), projectId);
				setSelectionForScanIdComboViewer(scan.getID(), scan.getBranch());
				PluginUtils.enableComboViewer(branchComboViewer, true);
				PluginUtils.enableComboViewer(scanIdComboViewer, true);
		    }
		});
		
	}
	
	/**
	 * Reverse selection - Populate branch combobox and select a branch based on the chosen scan id
	 */
	private void setSelectionForBranchComboViewer(String branchName, String projectId) {
		List<String> branchList = DataProvider.getInstance().getBranchesForProject(projectId);

		if (branchList != null) {
			branchComboViewer.setInput(branchList);
			
			String currentBranchName =  branchList.stream().filter(branch -> branchName.equals(branch)).findAny().orElse(null);
			
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
					String summaryString = PluginConstants.EMPTY_STRING;

					if (selectedItem.getType() != null) {
						summaryString = (selectedItem.getType()).toUpperCase() + " " + VERTICAL_SEPERATOR + " ";
					}

					if (selectedItem.getSeverity() != null) {
						summaryString = summaryString + selectedItem.getSeverity() + " " + VERTICAL_SEPERATOR + " ";
					}

					if (selectedItem.getResult() != null) {
						if (selectedItem.getResult().getStatus() != null) {
							summaryString = summaryString + selectedItem.getResult().getStatus() + " "
									+ VERTICAL_SEPERATOR + " ";
						}

						if (selectedItem.getResult().getData().getDescription() != null) {
							descriptionValueText.setText(selectedItem.getResult().getData().getDescription());
						} else {
							descriptionValueText.setText("Not Available.");
						}
					}

					if (!summaryString.isBlank()) {
						summaryText.setText(summaryString);
					}
					resultInfoCompositePanel.setVisible(true);
					resultInfoCompositePanel.layout();
					if (selectedItem.getType() != null) {
						updateAttackVectorForSelectedTreeItem(selectedItem);
					}
				}
			}
		});
	}

	private void updateAttackVectorForSelectedTreeItem(DisplayModel selectedItem) {
		clearAttackVectorSection(attackVectorCompositePanel);
		attackVectorCompositePanel.setVisible(true);

		if (selectedItem.getType().equalsIgnoreCase(PluginConstants.SCA_DEPENDENCY)) {

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

		}

		if (selectedItem.getType().equalsIgnoreCase(PluginConstants.SAST)) {
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
			// visiting only resources proxy because we obtain the resource only when matching name, thus the workspace traversal is much faster
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

	private void createColumns() {
		TreeViewerColumn col = createTreeViewerColumn("Title", 500);
		ColumnProvider label = new ColumnProvider(this::findSeverityImage, model -> model.name);
		col.setLabelProvider((label));
	}

	private TreeViewerColumn createTreeViewerColumn(String title, int bound) {
		final TreeViewerColumn viewerColumn = new TreeViewerColumn(viewer, SWT.NONE);
		final TreeColumn column = viewerColumn.getColumn();
		column.setText(title);
		column.setWidth(bound);
		column.setResizable(true);
		column.setMoveable(false);
		return viewerColumn;
	}

	private Image findSeverityImage(DisplayModel model) {
		String severity = model.severity;
		if (severity == null)
			return null;

		if (severity.equalsIgnoreCase("critical"))
			return CRITICAL_SEVERITY;
		if (severity.equalsIgnoreCase("high"))
			return HIGH_SEVERITY;
		if (severity.equalsIgnoreCase("medium"))
			return MEDIUM_SEVERITY;
		if (severity.equalsIgnoreCase("low"))
			return LOW_SEVERITY;
		if (severity.equalsIgnoreCase("info"))
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
		viewer.getTree().getDisplay().asyncExec(() -> viewer.refresh());
		toolBarActions.getScanResultsAction().setEnabled(true);
		toolBarActions.getAbortResultsAction().setEnabled(false);
		alreadyRunning = false;
		
		if(results.isEmpty()) {
			PluginUtils.showMessage(rootModel, viewer, PluginConstants.TREE_NO_RESULTS);
		}
		
		PluginUtils.updateFiltersEnabledAndCheckedState(toolBarActions.getFilterActions());
	}
	
	/**
	 * Clear all plugin fields and reload projects
	 */
	private void clearAndRefreshPlugin() {
		resultInfoCompositePanel.setVisible(false);
		attackVectorCompositePanel.setVisible(false);

		clearResultsTreeViewer();
		leftCompositePanel.layout();

		clearScanIdComboViewer();
		clearBranchComboViewer();
		resetProjectComboViewer();

		resetFiltersState();
		
		currentProjectId = PluginConstants.EMPTY_STRING;
		currentBranch = PluginConstants.EMPTY_STRING;
		currentScanId = PluginConstants.EMPTY_STRING;
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_PROJECT_ID, PluginConstants.EMPTY_STRING);
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_BRANCH, PluginConstants.EMPTY_STRING);
		GlobalSettings.storeInPreferences(GlobalSettings.PARAM_SCAN_ID, PluginConstants.EMPTY_STRING);
	}
	
	/**
	 * Clears Results' tree
	 */
	private void clearResultsTreeViewer() {
		rootModel.children.clear();
		viewer.refresh();
	}
	
	/**
	 * Clears Scans' combobox
	 */
	private void clearScanIdComboViewer() {
		scanIdComboViewer.refresh();
		scanIdComboViewer.setInput(Collections.emptyList());
		PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_PLACEHOLDER);
	}
	
	/**
	 * Clears Branches' combobox
	 */
	private void clearBranchComboViewer() {
		PluginUtils.enableComboViewer(branchComboViewer, false);
		branchComboViewer.refresh();
		branchComboViewer.setInput(Collections.EMPTY_LIST);
		PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
	}
	
	/**
	 * Reloads Projects' combobox
	 */
	private void resetProjectComboViewer() {
		loadingProjects();
		List<Project> projectList = DataProvider.getInstance().getProjects();
		projectComboViewer.setInput(projectList);
		projectComboViewer.refresh();
		PluginUtils.setTextForComboViewer(projectComboViewer, PROJECT_COMBO_VIEWER_TEXT);
		PluginUtils.enableComboViewer(projectComboViewer, true);
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
	 * Reset values for projects and branches combo
	 */
	private void resetProjectsAndBranchesCombo() {
		currentProjectId = PluginConstants.EMPTY_STRING;
		currentBranch = PluginConstants.EMPTY_STRING;
		currentScanId = PluginConstants.EMPTY_STRING;
		currentScanIdFormmated = PluginConstants.EMPTY_STRING;
		
		PluginUtils.enableComboViewer(projectComboViewer, true);
		PluginUtils.setTextForComboViewer(projectComboViewer, PROJECT_COMBO_VIEWER_TEXT);
		
		PluginUtils.enableComboViewer(branchComboViewer, false);
		PluginUtils.setTextForComboViewer(branchComboViewer, BRANCH_COMBO_VIEWER_TEXT);
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
		PluginUtils.setTextForComboViewer(scanIdComboViewer, PluginConstants.COMBOBOX_SCAND_ID_GETTING_SCANS);
	}
	
	/**
	 * Disable comboboxes
	 */
	private void disablePluginFields() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				PluginUtils.enableComboViewer(projectComboViewer, false);
				PluginUtils.enableComboViewer(branchComboViewer, false);
				PluginUtils.setTextForComboViewer(branchComboViewer, PluginConstants.COMBOBOX_BRANCH_CHANGING);
				loadingScans();
				toolBarActions.getToolBarActions().forEach(action -> action.setEnabled(false));
			}
		});
	}
	
	/**
	 * Enable comboboxes
	 */
	private void enablePluginFields() {
		PluginUtils.enableComboViewer(projectComboViewer, true);
		PluginUtils.enableComboViewer(branchComboViewer, true);
		PluginUtils.enableComboViewer(scanIdComboViewer, true);
		
		for(Action action : toolBarActions.getToolBarActions()) {
			String actionName = action.getId();
			
			if(actionName.equals(ActionName.ABORT_RESULTS.name()) || actionName.equals(ActionName.GROUP_BY_SEVERITY.name()) && !actionName.equals(ActionName.GROUP_BY_QUERY_NAME.name())) {
				continue;
			}
			
			if(actionName.equals(ActionName.CLEAN_AND_REFRESH.name()) || actionName.equals(ActionName.GET_RESULTS.name())) {
				action.setEnabled(true);
				continue;
			}
			
			action.setEnabled(DataProvider.getInstance().containsResults());
		}
	}
}
