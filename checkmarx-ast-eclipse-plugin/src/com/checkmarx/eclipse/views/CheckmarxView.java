package com.checkmarx.eclipse.views;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
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
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.PackageData;
import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.views.provider.ColumnProvider;
import com.checkmarx.eclipse.views.provider.TreeContentProvider;

public class CheckmarxView extends ViewPart {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "com.checkmarx.eclipse.views.CheckmarxView";

	public static final Image CRITICAL_SEVERITY = Activator.getImageDescriptor("/icons/severity-critical.png")
			.createImage();

	public static final Image HIGH_SEVERITY = Activator.getImageDescriptor("/icons/severity-high.png").createImage();

	public static final Image MEDIUM_SEVERITY = Activator.getImageDescriptor("/icons/severity-medium.png")
			.createImage();

	public static final Image LOW_SEVERITY = Activator.getImageDescriptor("/icons/severity-low.png").createImage();

//	public static final Image SAST_ICON = 
//			Activator.getImageDescriptor("platform:/plugin/org.eclipse.ui.views/icons/full/dlcl16/new.png").createImage();
//	
//	public static final Image SCA_ICON = 
//			Activator.getImageDescriptor("platform:/plugin/org.eclipse.ui.views/icons/full/dlcl16/new.png").createImage();
//	
//	public static final Image KICS_ICON = 
//			Activator.getImageDescriptor("platform:/plugin/org.eclipse.ui.views/icons/full/dlcl16/tree_mode.png").createImage();

	IWorkbench workbench;


	private TreeViewer viewer;
	private StringFieldEditor scanIdField;
	private Action getScanResultsAction, openPrefPageAction, abortGetResultsAction;
	private DisplayModel rootModel;
	private Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

	private static final String RUNNING = "Retriving the results for the scan id: %s .";
	private static final String ABORTING = "Aborting the retrieval of results...";


	private boolean alreadyRunning = false;

	private IPropertyChangeListener stringChangeListener;

	// private CxAuth cxAuth;

	Font boldFont;
	private Text typeValueText;
	private Text severityValueText;
	private Text statusValueText;
	private Text descriptionValueText;
	private Text attackVectorValueLinkText;

	private Composite attackVectorCompositePanel;
	private Composite leftCompositePanel;

//	private List<CxResult> resultList;
//	private CxResultOutput resultCommandOutput;

	public CheckmarxView() {
		super();
				
		DisplayModel init = new DisplayModel.DisplayModelBuilder("Paste a scanId and hit play to fetch the results.").build();
		rootModel = new DisplayModel.DisplayModelBuilder("").build();
		rootModel.children.add(init);

//		stringChangeListener = new IPropertyChangeListener() {
//			public void propertyChange(PropertyChangeEvent event) {
//			System.out.println(scanIdField.getStringValue());
//			}
//		};
//		
	}

	@Override
	public void dispose() {
		super.dispose();
		boldFont.dispose();
	}

	@Override
	public void createPartControl(Composite parent) {
		createViewer(parent);
		createActions();
		createToolbar();
		createContextMenu();

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
//		ITreeSelection selection = viewer.getStructuredSelection();
//		DisplayModel selected = (DisplayModel)selection.getFirstElement();
		manager.add(openPrefPageAction);
	}

	private void createToolbar() {
		IToolBarManager toolBarManager = getViewSite().getActionBars().getToolBarManager();
		toolBarManager.add(getScanResultsAction);
		toolBarManager.add(abortGetResultsAction);
	}

	private void createActions() {
		getScanResultsAction = new Action() {
			@Override
			public void run() {
				if (alreadyRunning)
					return;
				String scanId = scanIdField.getStringValue();
				if (!validateScanIdFormat(scanId)) {
					showMessage("Incorrect scanId format.");
					return;
				}

				showMessage(String.format(RUNNING, scanId));

				getScanResultsAction.setEnabled(false);
				abortGetResultsAction.setEnabled(true);


				CompletableFuture.runAsync(() -> {
					alreadyRunning = true;
					List<DisplayModel> scanResults = DataProvider.INSTANCE.getResultsForScanId(scanId);
					// List<DisplayModel> scanResults =
					// DataProvider.INSTANCE.processResultsV2(resultCommandOutput, scanId);
					rootModel.children.clear();
					rootModel.children.addAll(scanResults);
					viewer.getTree().getDisplay().asyncExec(() -> viewer.refresh());
					getScanResultsAction.setEnabled(true);
					alreadyRunning = false;
					

				});

			}
		};

		getScanResultsAction.setText("Scan Results");
		getScanResultsAction.setToolTipText("Get results for the scan id.");
		getScanResultsAction.setImageDescriptor(
				Activator.getImageDescriptor("platform:/plugin/org.eclipse.ui.browser/icons/clcl16/nav_go.png"));

		openPrefPageAction = new Action() {

			@Override
			public void run() {
				PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(shell,
						"com.checkmarx.eclipse.properties.preferencespage", null, null);
				if (pref != null)
					pref.open();
			}
		};

		openPrefPageAction.setText("Preferences");

		abortGetResultsAction = new Action() {
			@Override
			public void run() {
				showMessage(ABORTING);
				DataProvider.abort.set(true);
				abortGetResultsAction.setEnabled(false);
			}
		};
		abortGetResultsAction.setImageDescriptor(
				Activator.getImageDescriptor("platform:/plugin/org.eclipse.ui.browser/icons/clcl16/nav_stop.png"));
		abortGetResultsAction.setEnabled(false);

	}

	private boolean validateScanIdFormat(String scanId) {

		if (scanId.matches("[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[0-9a-f]{12}")) {
			return true;
		}
		return false;
	}

	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	private void createViewer(Composite parent) {

		// define a new composite for ScanID Field and ScanResults Tree
		leftCompositePanel = new Composite(parent, SWT.BORDER);

		scanIdField = new StringFieldEditor("scanId", "Scan Id:", 36, leftCompositePanel);
		scanIdField.setTextLimit(36);
		scanIdField.setEmptyStringAllowed(false);

		scanIdField.getTextControl(leftCompositePanel).addListener(SWT.DefaultSelection, new Listener() {
			public void handleEvent(Event event) {
				getScanResultsAction.run();
			}
		});

		viewer = new TreeViewer(leftCompositePanel,
				SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		ColumnViewerToolTipSupport.enableFor(viewer);
		createColumns();

		viewer.getTree().setHeaderVisible(true);
		viewer.getTree().setLinesVisible(true);

		viewer.setContentProvider(new TreeContentProvider());
		// viewer.setLabelProvider();
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

		Composite resultInfoCompositePanel = new Composite(parent, SWT.BORDER);
		resultInfoCompositePanel.setLayout(new FillLayout(SWT.VERTICAL));

		Label typeLabel = new Label(resultInfoCompositePanel, SWT.NONE);
		typeLabel.setFont(boldFont);
		typeLabel.setText("Type:");

		typeValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		typeValueText.setText("Not Available.");

		Label severityLabel = new Label(resultInfoCompositePanel, SWT.NONE);
		severityLabel.setFont(boldFont);
		severityLabel.setText("Severity:");

		severityValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		severityValueText.setText("Not Available.");

		Label statusLabel = new Label(resultInfoCompositePanel, SWT.NONE);
		statusLabel.setFont(boldFont);
		statusLabel.setText("Status:");

		statusValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		statusValueText.setText("Not Available.");

		Label descriptionLabel = new Label(resultInfoCompositePanel, SWT.NONE);
		descriptionLabel.setFont(boldFont);
		descriptionLabel.setText("Description:");

		descriptionValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		descriptionValueText.setText("Not Available.");

		// Section 3
		attackVectorCompositePanel = new Composite(parent, SWT.BORDER);
		attackVectorCompositePanel.setLayout(new RowLayout(SWT.VERTICAL));

		Label attackVectorLabel = new Label(attackVectorCompositePanel, SWT.NONE);
		attackVectorLabel.setFont(boldFont);
		attackVectorLabel.setText("Attack Vector:");

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

					// added this line to generate the view dynamically
					// createDetailsSection(viewer);

					if (selectedItem.getType() != null) {
						typeValueText.setText((selectedItem.getType()).toUpperCase());
					}

					if (selectedItem.getSeverity() != null) {
						severityValueText.setText(selectedItem.getSeverity());
					}
					
					if(selectedItem.getResult()!= null)
					{
						if (selectedItem.getResult().getStatus() != null) {
							statusValueText.setText(selectedItem.getResult().getStatus());
						}

						if (selectedItem.getResult().getData().getDescription() != null) {
							descriptionValueText.setText(selectedItem.getResult().getData().getDescription());
						} else {
							descriptionValueText.setText("Not Available.");
						}
						
					}
									

					if(selectedItem.getType()!=null)
					{
					updateAttackVectorForSelectedTreeItem(selectedItem);
					}

					// Delete the following commented code later
//		           StringBuffer toShow = new StringBuffer();
//		           for (Iterator iterator = selection.iterator(); iterator.hasNext();) {
//		               Object domain = (Model) iterator.next();
//		               String value = labelProvider.getText(domain);
//		               toShow.append(value);
//		               toShow.append(", ");
//		           }
//		           // remove the trailing comma space pair
//		           if(toShow.length() > 0) {
//		               toShow.setLength(toShow.length() - 2);
//		           }
//		           text.setText(toShow.toString());
				}
			}
		});

	}


//	private void createDetailsSection(TreeViewer treeViewer)
//	{
//		Composite parent = treeViewer.getControl().getParent();
//		
//		Display display = parent.getShell().getDisplay();
//		FontData systemFontData = display.getSystemFont().getFontData()[0];
//		Font boldFont = new Font(display, systemFontData.getName(),
//		                     systemFontData.getHeight(), SWT.BOLD);
//		
//		
//		Composite resultInfoCompositePanel = new Composite(parent, SWT.BORDER);
//		resultInfoCompositePanel.setLayout(new RowLayout(SWT.VERTICAL));
//
//		
//		Text typeText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
//		typeText.setFont(boldFont);
//		typeText.setText("Type:");
//		
//		typeValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
//	//	typeValueText.setText("Not Available.");
//		
//		Text severity = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
//		severity.setFont(boldFont);
//		severity.setText("Severity:");
//		
//		severityValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
//	//	severityValueText.setText("Not Available.");
//		
//		Text status = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
//		status.setFont(boldFont);
//		status.setText("Status:");
//		
//		statusValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
//	//	statusValueText.setText("Not Available.");
//		
//		Text description = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
//		description.setFont(boldFont);
//		description.setText("Description:");
//		
//		descriptionValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
//	//	descriptionValueText.setText("Not Available.");
//
//		
//		//Section 3
//		attackVectorCompositePanel = new Composite(parent, SWT.BORDER);
//		attackVectorCompositePanel.setLayout(new RowLayout(SWT.VERTICAL));
//		
//		Text attackVector = new Text(attackVectorCompositePanel, SWT.READ_ONLY);
//		attackVector.setFont(boldFont);
//		attackVector.setText("Attack Vector:");
//		
//		parent.layout();
//		
//		
//	}

	private void updateAttackVectorForSelectedTreeItem(DisplayModel selectedItem) {

		clearAttackVectorSection(attackVectorCompositePanel);
		
		if (selectedItem.getType().equalsIgnoreCase(PluginConstants.SCA_DEPENDENCY)) {
			
			List<PackageData> packageDataList = selectedItem.getResult().getData().getPackageData();

			if (packageDataList!= null && !packageDataList.isEmpty()) {
				
				for(PackageData packageDataItem : packageDataList)
				{
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
					String markerDescription = groupName+"_"+queryName+"_"+ nodeName;
					
					// attackVectorValueText = new Text(attackVectorCompositePanel, SWT.READ_ONLY);
					// attackVectorValueText.setText(node.getFileName() + "[" + node.getLine() + ","
					// + node.getColumn() + "]");

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
			
			if(!(child instanceof Label))
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
					// IDE.openEditor(marker); //3.0 API
					IDE.openEditor(page, fileMarker);
					// marker.delete();
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}

		}

	}

//	private void createFileMarkers(IFile file, Integer lineNumber, String markerDescription) {		
//		
//		try {
//			fileMarker = file.createMarker(IMarker.PROBLEM);
//			fileMarker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
//			fileMarker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
//			fileMarker.setAttribute(IMarker.MESSAGE, markerDescription);
//		} catch (CoreException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		// return fileMarker;
//
//	}

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
//			CxLogger.getLogger().error("Error occured while searching for file name in project",e);
			e.printStackTrace();
		}
		return foundFiles;
	}

	private void createColumns() {
		TreeViewerColumn col = createTreeViewerColumn("Title", 400);
		ColumnProvider label = new ColumnProvider(this::findSeverityImage, model -> model.name);
		col.setLabelProvider((label));

//		col = createTreeViewerColumn("State", 400);
//		col.setLabelProvider(new ColumnTextProvider(model -> model.state));
//
//		col = createTreeViewerColumn("Status", 400);
//		col.setLabelProvider(new ColumnTextProvider(model -> model.status));
//
//		col = createTreeViewerColumn("Fix", 400);
//		col.setLabelProvider(new ColumnTextProvider(model -> model.fix));
	}

	private TreeViewerColumn createTreeViewerColumn(String title, int bound) {
		final TreeViewerColumn viewerColumn = new TreeViewerColumn(viewer, SWT.NONE);
		final TreeColumn column = viewerColumn.getColumn();
		column.setText(title);
		column.setWidth(bound);
		column.setResizable(true);
		column.setMoveable(true);
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

//		if (model.getType().equalsIgnoreCase("sast")) return SAST_ICON;
//		if (model.getType().equalsIgnoreCase("infrastructure")) return KICS_ICON;
//		if (model.getType().equalsIgnoreCase("kics")) return KICS_ICON;

		return null;
	}

//	public void getResultsForScanId(String scanId) {
//		
//		CxScanConfig config = new CxScanConfig();
//
//	    config.setBaseUri(Preferences.getServerUrl());
//	    config.setTenant(Preferences.getTenant());
//	    config.setApiKey(Preferences.getApiKey());
//	    
//		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());
//		
//		try {
//			cxAuth = new CxAuth(config, log);
//			Integer result = cxAuth.cxAuthValidate();
//			System.out.println("Authentication Status :" + result);
//			System.out.println("Fetching the results for scanId :" + scanId);
//			resultCommandOutput = cxAuth.cxGetResults(scanId); //adfa3bb4-754d-4444-b8ca-67edbe767186 for sca kics and sast
//			System.out.println("Result :" + resultCommandOutput.getTotalCount());
//			resultList = resultCommandOutput.getResults();
//			
//			
//		} catch (IOException | CxException | URISyntaxException | InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//	}

	public void showMessage(String message) {
		rootModel.children.clear();
		rootModel.children.add(DataProvider.INSTANCE.message(message));
		viewer.refresh();
		// monitorActions.forEach(act -> act.setEnabled(true));
	}

}
