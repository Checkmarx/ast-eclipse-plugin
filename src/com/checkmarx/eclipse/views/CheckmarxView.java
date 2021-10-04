package com.checkmarx.eclipse.views;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.exceptions.CxException;
import com.checkmarx.ast.results.structure.CxResult;
import com.checkmarx.ast.results.structure.CxResultOutput;
import com.checkmarx.ast.scans.CxAuth;
import com.checkmarx.ast.scans.CxScanConfig;
import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.runner.Authenticator;
import com.checkmarx.eclipse.views.provider.ColumnProvider;
import com.checkmarx.eclipse.views.provider.ColumnTextProvider;
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

	private ListViewer listViewer;
	private TreeViewer viewer;
	private StringFieldEditor scanIdField;
	private Action getScanResultsAction, openPrefPageAction, abortGetResultsAction;
	private DisplayModel rootModel;
	private Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

	private static final String RUNNING = "Retriving the results for the scan id: %s .";
	private static final String ABORTING = "Aborting the retrieval of results...";

	private List<Action> monitorActions = new ArrayList<>();

	private boolean alreadyRunning = false;

	private IPropertyChangeListener stringChangeListener;

	private CxAuth cxAuth;
	
	private Text typeValueText;
	private Text severityValueText;
	private Text statusValueText;
	private Text descriptionValueText;
	private Text attackVectorValueText;

//	private List<CxResult> resultList;
//	private CxResultOutput resultCommandOutput;

	public CheckmarxView() {
		super();
		rootModel = new DisplayModel();
		DisplayModel init = new DisplayModel();
		init.name = "Paste a scanId and hit play to fetch the results.";
		rootModel.children.add(init);

//		stringChangeListener = new IPropertyChangeListener() {
//			public void propertyChange(PropertyChangeEvent event) {
//			System.out.println(scanIdField.getStringValue());
//			}
//		};
//		
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
		Composite leftCompositePanel = new Composite(parent, SWT.BORDER);	
		
		scanIdField = new StringFieldEditor("scanId", "Scan Id:", 36, leftCompositePanel);
		scanIdField.setTextLimit(36);
		scanIdField.setEmptyStringAllowed(false);
		
		
		scanIdField.getTextControl(leftCompositePanel).addListener(SWT.DefaultSelection, new Listener() {
			public void handleEvent(Event event) {
				getScanResultsAction.run();
			}
		});
		

		
		viewer = new TreeViewer(leftCompositePanel, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
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

		//configureSelectionListener
		configureTreeItemSelectionChangeEvent(viewer);
		
		
		// Original working code above
		// SECTION 2
		
		//Setting the BOLD Font for Labels
		Display display = parent.getShell().getDisplay();
		FontData systemFontData = display.getSystemFont().getFontData()[0];
		Font boldFont = new Font(display, systemFontData.getName(),
		                     systemFontData.getHeight(), SWT.BOLD);
		
		
		Composite resultInfoCompositePanel = new Composite(parent, SWT.BORDER);
		resultInfoCompositePanel.setLayout(new RowLayout(SWT.VERTICAL));

		
		Text typeText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		typeText.setFont(boldFont);
		typeText.setText("Type:");
		
		typeValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		typeValueText.setText("<Insert scanType here>");
		
		Text severity = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		severity.setFont(boldFont);
		severity.setText("Severity:");
		
		severityValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		severityValueText.setText("<Insert severity here>");
		
		Text status = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		status.setFont(boldFont);
		status.setText("Status:");
		
		statusValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		statusValueText.setText("<Insert status here>");
		
		Text description = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		description.setFont(boldFont);
		description.setText("Description:");
		
		descriptionValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		descriptionValueText.setText("<Insert description here>");

		
		//Section 3
		Composite attackVectorCompositePanel = new Composite(parent, SWT.BORDER);
		attackVectorCompositePanel.setLayout(new RowLayout(SWT.VERTICAL));
		
		Text attackVector = new Text(attackVectorCompositePanel, SWT.READ_ONLY);
		attackVector.setFont(boldFont);
		attackVector.setText("Attack Vector:");
		
		attackVectorValueText = new Text(attackVectorCompositePanel, SWT.READ_ONLY);
		attackVectorValueText.setText("<Insert attack vector here>");
		
		
		// Use this for linking the selection of result to the source file--------------
//		  viewer.addSelectionChangedListener(new ISelectionChangedListener() {
//              public void selectionChanged(SelectionChangedEvent event) {
//                      updateActionEnablement();
//              }
//      });

	}

private void configureTreeItemSelectionChangeEvent(TreeViewer viewer) {
	
	viewer.addSelectionChangedListener(new ISelectionChangedListener() {
		
		@Override
		public void selectionChanged(SelectionChangedEvent event) {
		       // if the selection is empty clear the label
		       if(event.getSelection().isEmpty()) {
		           System.out.println("Empty row selected");
		           return;
		       }
		       if(event.getSelection() instanceof IStructuredSelection) {
		           IStructuredSelection selection = (IStructuredSelection)event.getSelection();
		           DisplayModel selectedItem = (DisplayModel)selection.getFirstElement();
//		           System.out.println("Selected :" + selectedItem.getName());
		           
		           if(selectedItem.getType() !=null) {
		           typeValueText.setText(selectedItem.getType());
		           }
		           
		           if(selectedItem.getSeverity() !=null) {
		           severityValueText.setText(selectedItem.getSeverity());
		           }
		           
		           if(selectedItem.getStatus() !=null) {
		           statusValueText.setText(selectedItem.getStatus());
		           }
		           
		           if(selectedItem.getDescription() !=null) {
		           descriptionValueText.setText(selectedItem.getDescription());
		           }
		        
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

//    private void updateActionEnablement() {
//        IStructuredSelection sel = 
//                (IStructuredSelection)viewer.getSelection();
//        deleteItemAction.setEnabled(sel.size() > 0);
//}

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
