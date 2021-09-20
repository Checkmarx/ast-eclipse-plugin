package com.checkmarx.eclipse.views;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
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
	
	public static final Image CRITICAL_SEVERITY = 
			Activator.getImageDescriptor("/icons/severity-critical.png").createImage();
	
	public static final Image HIGH_SEVERITY = 
			Activator.getImageDescriptor("/icons/severity-high.png").createImage();
	
	public static final Image MEDIUM_SEVERITY = 
			Activator.getImageDescriptor("/icons/severity-medium.png").createImage();
	
	public static final Image LOW_SEVERITY = 
			Activator.getImageDescriptor("/icons/severity-low.png").createImage();


	
	IWorkbench workbench;

	private ListViewer listViewer;
	private TreeViewer viewer;
	private Action getResults, openPrefPage, abortGettingResults;	
	private DisplayModel rootModel;
	private Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
	
	private static final String RUNNING = "Scanning your project...";
	private static final String ABORTING = "abort scanning...";
	
	private List<Action> monitorActions = new ArrayList<>();
	
	private boolean alreadyRunning = false;
	
	private IPropertyChangeListener stringChangeListener;

	private CxAuth cxAuth;

	private List<CxResult> resultList;
	private CxResultOutput resultCommandOutput;
	
	
	public CheckmarxView() {
		super();
	//	getResultsForScanId();
		
	}
	
	@Override
	public void createPartControl(Composite parent) {
		createViewer(parent);
//
//		getSite().setSelectionProvider(viewer);

//		hookContextMenu();
//		


         // Create menu and toolbars.
         createActions();
         contributeToActionBars();
//         createMenu();
//         createToolbar();
 //        createContextMenu();
//         hookGlobalActions();
         
         // Restore state from the previous session.
  //    restoreState();
		
	}



	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalToolBar(bars.getToolBarManager());
		
	}

	private void fillLocalToolBar(IToolBarManager toolBarManager) {
		toolBarManager.add(getResults);
		
	}

	private void createActions() {
		getResults = new Action() {
			@Override
			public void run() {
				
				CompletableFuture.runAsync(()-> {
					getResultsForScanId();
				//	rootModel.children.clear();
					viewer.getTree().getDisplay().asyncExec(() -> viewer.refresh());
				});
				
			}
		};
		
		getResults.setText("Scan Results");
		getResults.setToolTipText("Get Results for the scan id.");
		getResults.setImageDescriptor(
				Activator.getImageDescriptor("platform:/plugin/org.eclipse.ui.browser/icons/clcl16/nav_go.png"));
		
		openPrefPage = new Action() {
			
			@Override
			public void run() {
				PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(
						shell, "com.checkmarx.eclipse.properties.preferencespage",  
						null, null);
				if (pref != null) pref.open();
			}
		};
		
		openPrefPage.setText("Preferences");
		
	}

	@Override
	public void setFocus() {

	}
	
	private void createViewer(Composite parent) {
		
	//	parent.setLayout(new FillLayout(SWT.HORIZONTAL));
					
		StringFieldEditor scanIdField = new StringFieldEditor("scanId", "Scan Id:", parent);	
	//	scanIdField.setPropertyChangeListener(stringChangeListener);
			
		
//		stringChangeListener = new IPropertyChangeListener() {
//			public void propertyChange(PropertyChangeEvent event) {
//			//	if (event.getProperty().equals())
//				//	getResultsForScanId();
//			}
//		};
		
		
		getResultsForScanId();
		

		
		viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		ColumnViewerToolTipSupport.enableFor(viewer);
		createColumns();
		
		viewer.getTree().setHeaderVisible(true);
		viewer.getTree().setLinesVisible(true);
	
		viewer.setContentProvider(new TreeContentProvider());
		//viewer.setLabelProvider();
		getSite().setSelectionProvider(viewer);

		// define layout for the viewer
		GridData gridData = new GridData();
		gridData.verticalAlignment = GridData.FILL;
		gridData.horizontalSpan = 2;
		gridData.grabExcessHorizontalSpace = true;
		gridData.grabExcessVerticalSpace = true;
		gridData.horizontalAlignment = GridData.FILL;
		viewer.getControl().setLayoutData(gridData);
		
		rootModel = DataProvider.INSTANCE.processResults(resultCommandOutput);
		viewer.setInput(rootModel);
		
	}
	
	private void createColumns() {
		TreeViewerColumn col = createTreeViewerColumn("Title", 400);
		ColumnProvider label = new ColumnProvider(this::findSeverityImage ,model -> model.name);
		col.setLabelProvider((label));

		col = createTreeViewerColumn("State", 400);
		col.setLabelProvider(new ColumnTextProvider(model -> model.state));

		col = createTreeViewerColumn("Status", 400);
		col.setLabelProvider(new ColumnTextProvider(model -> model.status));
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
		if (severity == null) return null;

		if (severity.equalsIgnoreCase("critical")) return CRITICAL_SEVERITY;
		if (severity.equalsIgnoreCase("high")) return HIGH_SEVERITY;
		if (severity.equalsIgnoreCase("medium")) return MEDIUM_SEVERITY;
		if (severity.equalsIgnoreCase("low")) return LOW_SEVERITY;
		
		return null;
	}
	
	public void getResultsForScanId() {
				
		
		CxScanConfig config = new CxScanConfig();

	    config.setBaseUri(Preferences.getServerUrl());
	    config.setTenant(Preferences.getTenant());
	    config.setApiKey(Preferences.getApiKey());
	    
		Logger log = LoggerFactory.getLogger(Authenticator.class.getName());
		
		try {
			cxAuth = new CxAuth(config, log);
			Integer result = cxAuth.cxAuthValidate();
			System.out.println("Authentication Status :" + result);
			resultCommandOutput = cxAuth.cxGetResults("e3c7a6d6-98fd-4513-84e0-2dc86a61b2a9"); //adfa3bb4-754d-4444-b8ca-67edbe767186 for sca kics and sast
			System.out.println("Result :" + resultCommandOutput.getTotalCount());
			resultList = resultCommandOutput.getResults();
			
			
		} catch (IOException | CxException | URISyntaxException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
}
