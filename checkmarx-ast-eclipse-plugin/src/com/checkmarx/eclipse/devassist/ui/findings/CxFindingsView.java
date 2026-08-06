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
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;

import com.checkmarx.eclipse.devassist.ui.findings.provider.FindingsContentProvider;
import com.checkmarx.eclipse.devassist.ui.findings.provider.FindingsLabelProvider;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.CheckmarxView;
import com.checkmarx.eclipse.views.actions.ActionOpenPreferencesPage;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanDetailWithPath;
import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.devassist.model.Location;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.ui.findings.actions.VulnerabilityFilterAction;
import com.checkmarx.eclipse.devassist.ui.findings.actions.VulnerabilityFilterState;
import com.checkmarx.eclipse.devassist.ignore.IgnoreManager;
import com.checkmarx.eclipse.devassist.ignore.IgnoreFileManager.IgnoreListener;
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
 * Manages a tree view of vulnerabilities with filtering and navigation capabilities.
 * Uses {@link TreeViewer} for flexible tree rendering with custom providers.
 */
public class CxFindingsView extends ViewPart implements IgnoreListener {

    public static final String ID = "com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView";
    private org.osgi.service.event.EventHandler eventHandler;
    private org.osgi.service.event.EventHandler settingsEventHandler;

    private TreeViewer treeViewer;
    private Map<String, List<ScanIssue>> currentIssues = new HashMap<>();
    private final IgnoreManager ignoreManager = IgnoreManager.getInstance();
    Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
    public static final Image FINDINGS_PROMOTIONAL_CUBE = createScaledImage("/icons/cx-one-assist-cube.png", 240);

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

        // Register ignored problems listener
        ignoreManager.addListener(this);

        // Initial render check
        refreshViewMode();
    }
    
    
     /**
      * Loads an image and scales it down to the given max width (maintaining aspect ratio)
      * if it is larger than that width.
      */
     private static Image createScaledImage(String path, int maxWidth) {
       Image original = Activator.getImageDescriptor(path).createImage();
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

        if (!PluginUtils.areCredentialsDefined()) {
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
                ProblemHolderService problemHolder = (ProblemHolderService) project.getSessionProperty(
                        new QualifiedName("com.checkmarx.eclipse.plugin", "problem-holder"));

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
        
    
    private Composite openSettingsComposite;

    /**
     * Renders the missing credentials panel centered inside the view parent.
     */
    private void drawMissingCredentialsPanel(Composite parent) {
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
        cxLogo.setImage(CheckmarxView.CHECKMARX_OPEN_SETTINGS_LOGO);

        // Open Settings Button
        Button btn = new Button(openSettingsComposite, SWT.NONE);
        btn.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        btn.setText(PluginConstants.BTN_OPEN_SETTINGS);

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

        setupToolbar();
        setupTreeListeners();

        if (!currentIssues.isEmpty()) {
            refreshTreeWithFilter();
        }

        parent.layout(true, true);
    }

    /**
     * Renders the promotional cube image and description text in the right-hand pane
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
        descriptionLabel.setText(PluginConstants.FINDINGS_PROMO_DESCRIPTION);

        // SWT.WRAP labels need an explicit widthHint to wrap and left-align under the image
        // instead of growing to one unbroken line. The pane has no real bounds yet at this
        // point (the parent hasn't laid out), so compute it once asynchronously after the
        // initial layout, and again whenever the pane is resized (e.g. by dragging the sash).
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
            org.eclipse.e4.core.services.events.IEventBroker eventBroker = 
                getSite().getService(org.eclipse.e4.core.services.events.IEventBroker.class);

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
                eventBroker.subscribe(PluginConstants.TOPIC_APPLY_SETTINGS, settingsEventHandler);
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
                org.eclipse.e4.core.services.events.IEventBroker eventBroker = 
                    org.eclipse.ui.PlatformUI.getWorkbench().getService(
                        org.eclipse.e4.core.services.events.IEventBroker.class);
                        
                if (eventBroker != null) {
                    eventBroker.unsubscribe(eventHandler);
                    
                }
            } catch (Exception e) {
                System.err.println("[FINDINGS] Error unsubscribing from IEventBroker: " + e.getMessage());
            }
        }

        // 2. Unsubscribe from IgnoreManager
        ignoreManager.removeListener(this);

        super.dispose();
    }
    
    private Composite parentComposite;


    private void initFindingsViewUI() {
        try {
            
            IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();

            if (projects.length > 0 && projects[0].isOpen()) {
                IProject project = projects[0];
                ProblemHolderService problemHolder = (ProblemHolderService) project.getSessionProperty(
                        new QualifiedName("com.checkmarx.eclipse.plugin", "problem-holder"));

                if (problemHolder != null) {
                    Map<String, List<ScanIssue>> existingIssues = problemHolder.getAllScanIssues();
                    if (existingIssues != null && !existingIssues.isEmpty()) {
                        this.currentIssues = existingIssues;
                    }
                }
            }

            subscribeToEventBroker();

            ignoreManager.addListener(this);

            drawFindingsPanel(parentComposite);

        } catch (Exception e) {
            System.err.println("[FINDINGS] Error during view creation: " + e.getMessage());
            e.printStackTrace();
        }
    }

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

        // Preferences action (same implementation as CheckmarxView)
        Action openPreferencesPageAction =
            new ActionOpenPreferencesPage(
                null,
                treeViewer,
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell())
            .createAction();

        // Toolbar preferences button
        Action toolbarPreferencesAction =
            new Action("\u2000?", Action.AS_PUSH_BUTTON) {
                @Override
                public void run() {
                    openPreferencesPageAction.run();
                }
            };

        toolbarPreferencesAction.setToolTipText("Checkmarx Preferences");
        toolbar.add(toolbarPreferencesAction);

        toolbar.update(true);
        getViewSite().getActionBars().updateActionBars();
        
    }
    private void setupTreeListeners() {
        Tree tree = treeViewer.getTree();
        

        //Listner for redirection
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

        // Use resolved file path from ScanIssue (if available) or fallback to detailWithPath
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
            details.append("Location: Line ").append(loc.getLine()).append(", Col ").append(loc.getStartIndex()).append("\n");
        }
        details.append("====================================\n");

        
    }

    /**
     * Fix issue with AI Assist.
     */
    private void fixWithAIAssist(ScanIssue issue) {
       
        try {
            // Build remediation prompt based on engine type
            String prompt = com.checkmarx.eclipse.devassist.ui.findings.integration.RemediationPromptBuilder
                    .buildRemediationPrompt(issue);

            if (prompt == null || prompt.isEmpty()) {
                
                showErrorNotification("Failed to build prompt for this issue type");
                return;
            }

            // Send to Copilot via integration
            
            boolean success = com.checkmarx.eclipse.devassist.ui.findings.integration.CopilotIntegration
                    .sendPromptToCopilot(prompt);

            if (success) {
                
            } else {
                
            }

        } catch (Exception e) {
            
            e.printStackTrace();
            showErrorNotification("Error: " + e.getMessage());
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
     * The finding is added to the ignore store and appears in the Ignored Findings view.
     */
    private void ignoreThisFinding(ScanIssue issue) {
        try {
            ignoreManager.addIgnoredEntry(issue);
            refreshTreeWithFilter();
        } catch (Exception e) {
            System.err.println("[FINDINGS] ✗ Error ignoring finding: " + e.getMessage());
            e.printStackTrace();
            showErrorNotification("Failed to ignore finding: " + e.getMessage());
        }
    }

    /**
     * Ignore all findings that share the same stable identity as issue (e.g. same OSS
     * package+version across all files, or same rule/secret/rule-in-this-file for
     * file-scoped engines). Delegates the matching itself to IgnoreManager.
     */
    private void ignoreAllOfType(ScanIssue issue) {
        try {
            ignoreManager.addAllIgnoredEntry(issue, currentIssues);
            refreshTreeWithFilter();
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
        if (text == null) return "";
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

            // 3. Navigate using standard ITextEditor adapter (or fall back to marker navigation)
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
     * Called when file is opened via navigation to ensure we don't miss IPartListener2 events.
     */
    private void setupRealtimeScanningForFile(org.eclipse.core.resources.IFile file, IEditorPart editor) {
        if (file == null || editor == null) {
            
            return;
        }

        
        

        try {
            // Extract document for real-time scanning
            org.eclipse.jface.text.IDocument document = null;
            String filePath = file.getLocation().toOSString();
            String fileName = file.getName();

            

            // Try method 1: Direct ITextEditor instance check
            if (editor instanceof org.eclipse.ui.texteditor.ITextEditor) {
                
                org.eclipse.ui.texteditor.ITextEditor textEditor = (org.eclipse.ui.texteditor.ITextEditor) editor;
                document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
            }

            // Try method 2: ITextEditor Adapter pattern (for MavenPomEditor, etc.)
            if (document == null) {
                
                org.eclipse.ui.texteditor.ITextEditor textEditor = editor.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class);
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
            com.checkmarx.eclipse.devassist.ui.findings.realtime.RealTimeScanJob scanJob =
                new com.checkmarx.eclipse.devassist.ui.findings.realtime.RealTimeScanJob(file, fileName);

            

            

            // Create a document listener that reschedules the job on every keystroke
            com.checkmarx.eclipse.devassist.inspection.DevAssistScanScheduler scheduler = null;
            if (file != null) {
                try {
                    org.eclipse.core.resources.IProject project = file.getProject();
                    if (project != null) {
                        scheduler = (com.checkmarx.eclipse.devassist.inspection.DevAssistScanScheduler) project.getSessionProperty(
                            new org.eclipse.core.runtime.QualifiedName("com.checkmarx.eclipse.plugin", "scan-scheduler"));
                    }
                } catch (Exception e) {
                    // Ignore if scheduler not available
                }
            }
            com.checkmarx.eclipse.devassist.ui.findings.realtime.CheckmarxDocumentListener docListener =
                new com.checkmarx.eclipse.devassist.ui.findings.realtime.CheckmarxDocumentListener(fileName, scanJob, file, scheduler);

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
     * Apply cached decorations (gutter icons, underlines) when editor is opened via navigation.
     * Uses the provided editor directly instead of searching for it.
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
            ProblemHolderService problemHolder =
                (ProblemHolderService) project.getSessionProperty(
                    new org.eclipse.core.runtime.QualifiedName("com.checkmarx.eclipse.plugin", "problem-holder"));

            if (problemHolder == null) {
                return;
            }

            java.util.List<ScanIssue> cachedIssues = problemHolder.getScanIssuesByFile(filePath);

            if (cachedIssues == null || cachedIssues.isEmpty()) {
                
                return;
            }

            // Apply decorations directly using the provided editor
            
            applyDecorationsDirectly(editor, file, cachedIssues);

        } catch (Exception e) {
            System.err.println("[REALTIME-SETUP] Error applying cached decorations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Apply decorations directly to the provided editor without searching for it.
     * This avoids issues with MavenPomEditor not being found by IFile comparison.
     */
    private void applyDecorationsDirectly(org.eclipse.ui.IEditorPart editor,
                                         org.eclipse.core.resources.IFile file,
                                         java.util.List<ScanIssue> scanIssues) {
        if (editor == null || file == null || scanIssues == null || scanIssues.isEmpty()) {
            return;
        }

        try {
            // Get the text editor
            org.eclipse.ui.texteditor.ITextEditor textEditor =
                editor.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class);

            if (textEditor == null) {
                
                return;
            }

            // Get document provider and input
            org.eclipse.ui.texteditor.IDocumentProvider docProvider = textEditor.getDocumentProvider();
            if (docProvider == null) {
                
                return;
            }

            // Get annotation model from the document provider (proper way for all editor types)
            org.eclipse.jface.text.source.IAnnotationModel annotationModel =
                docProvider.getAnnotationModel(textEditor.getEditorInput());

            if (annotationModel == null) {
                
                return;
            }

            

            // Get document from provider
            org.eclipse.jface.text.IDocument document = docProvider.getDocument(textEditor.getEditorInput());

            if (document == null) {
                
                return;
            }

            // Apply each issue's decoration using OSS-specific logic
            java.util.List<org.eclipse.jface.text.source.Annotation> annotations =
                new java.util.ArrayList<>();

            for (ScanIssue issue : scanIssues) {
                try {
                    // Create annotation
                    com.checkmarx.eclipse.devassist.ui.findings.editor.FindingsAnnotation annotation =
                        createAnnotationForIssue(issue);

                    if (annotation == null) {
                        continue;
                    }

                    // Calculate position (OSS = first line only)
                    org.eclipse.jface.text.Position pos = calculatePositionForIssue(document, issue);

                    if (pos != null && pos.getLength() > 0) {
                        annotationModel.addAnnotation(annotation, pos);
                        annotations.add(annotation);
                        
                    }
                } catch (Exception e) {
                    System.err.println("[REALTIME-SETUP-DIRECT] Error decorating issue: " + e.getMessage());
                }
            }

            

        } catch (Exception e) {
            System.err.println("[REALTIME-SETUP-DIRECT] ✗ Error applying decorations directly: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create annotation for an issue.
     */
    private com.checkmarx.eclipse.devassist.ui.findings.editor.FindingsAnnotation createAnnotationForIssue(ScanIssue issue) {
        try {
            String annotationType = mapSeverityToAnnotationType(issue.getSeverity());
            return new com.checkmarx.eclipse.devassist.ui.findings.editor.FindingsAnnotation(
                annotationType,
                issue.getTitle(),
                issue.getDescription()
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Map severity to annotation type.
     * Handles all 8 severity levels including OK, UNKNOWN, and IGNORED.
     */
    private String mapSeverityToAnnotationType(String severity) {
        if (severity == null) {
            return "com.checkmarx.eclipse.findings.unknown";
        }
        String upper = severity.toUpperCase();
        if (upper.contains("MALICIOUS")) {
            return "com.checkmarx.eclipse.findings.malicious";
        }
        if (upper.contains("CRITICAL") || upper.contains("ERROR")) {
            return "com.checkmarx.eclipse.findings.critical";
        }
        if (upper.contains("HIGH")) {
            return "com.checkmarx.eclipse.findings.high";
        }
        if (upper.contains("MEDIUM")) {
            return "com.checkmarx.eclipse.findings.medium";
        }
        if (upper.contains("LOW") || upper.contains("INFO")) {
            return "com.checkmarx.eclipse.findings.low";
        }
        if (upper.contains("UNKNOWN")) {
            return "com.checkmarx.eclipse.findings.unknown";
        }
        if (upper.contains("OK")) {
            return "com.checkmarx.eclipse.findings.ok";
        }
        if (upper.contains("IGNORED")) {
            return "com.checkmarx.eclipse.findings.ignored";
        }
        return "com.checkmarx.eclipse.findings.unknown";
    }

    /**
     * Calculate position for an issue (OSS = first line only).
     */
    private org.eclipse.jface.text.Position calculatePositionForIssue(org.eclipse.jface.text.IDocument document, ScanIssue issue) {
        try {
            if (issue.getLocations() == null || issue.getLocations().isEmpty()) {
                return null;
            }

            com.checkmarx.eclipse.devassist.model.Location location = issue.getLocations().get(0);
            int lineNumber = location.getLine() - 1;  // 0-based

            int lineCount = document.getNumberOfLines();
            if (lineNumber < 0 || lineNumber >= lineCount) {
                return null;
            }

            org.eclipse.jface.text.IRegion lineInfo = document.getLineInformation(lineNumber);
            int offset = lineInfo.getOffset();
            int length = lineInfo.getLength();

            // FIX: Skip leading whitespace to match ProblemDecorator.calculateRange()
            int trimOffset = getLeadingWhitespaceOffset(document, offset, length);
            int adjustedOffset = offset + trimOffset;
            int adjustedLength = Math.max(1, length - trimOffset);

            return new org.eclipse.jface.text.Position(adjustedOffset, adjustedLength);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Scroll editor to specific line number using native Eclipse ITextEditor adapter.
     */
    private boolean scrollToLine(IEditorPart editor, int lineNumber) {
        if (editor == null || lineNumber <= 0) return false;

        try {
            // Use Eclipse's standard adapter pattern instead of reflection
            org.eclipse.ui.texteditor.ITextEditor textEditor = editor.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class);
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
     * Ensures IMarker.LINE_NUMBER is explicitly set as a 1-based Integer attribute.
     */
    private void createMarkerForIssue(IFile file, ScanIssue issue) {
        if (file == null || issue == null || issue.getLocations() == null || issue.getLocations().isEmpty()) {
            return;
        }

        try {
            IMarker existingMarker = findMarkerForIssue(file, issue);
            if (existingMarker != null && existingMarker.exists()) {
                return;
            }

            // 1. Create the marker using the declared ID
            IMarker newMarker = file.createMarker("com.checkmarx.eclipse.plugin.checkmarxProblemMarker");

            // 2. Set Standard Core Eclipse Attributes (CRITICAL for Quick Fix matching)
            int lineNumber = issue.getLocations().get(0).getLine();
            newMarker.setAttribute(IMarker.LINE_NUMBER, lineNumber > 0 ? lineNumber : 1);
            newMarker.setAttribute(IMarker.MESSAGE, issue.getTitle() != null ? issue.getTitle() : "Checkmarx Finding");
            newMarker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
            newMarker.setAttribute(IMarker.USER_EDITABLE, false);

            // FIX: Set character offsets with whitespace trimming (so underline doesn't include leading spaces)
            setMarkerCharacterOffsets(newMarker, file, lineNumber);

            // 3. Populate custom attributes
            com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper.populateMarker(newMarker, issue);

            

        } catch (org.eclipse.core.runtime.CoreException e) {
            System.err.println("[FINDINGS] Error creating marker: " + e.getMessage());
            e.printStackTrace();
        }
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
            IMarker marker = findMarkerForIssue(file, issue);
            if (marker != null && marker.exists()) {
                org.eclipse.ui.ide.IDE.gotoMarker(editor, marker);
                
            } else {
                
            }
        } catch (Exception e) {
            
        }
    }

    /**
     * Find the IMarker that corresponds to a ScanIssue.
     * Matches by file, line number, and optionally title.
     */
    private IMarker findMarkerForIssue(IFile file, ScanIssue issue) {
        if (file == null || issue == null || issue.getLocations() == null || issue.getLocations().isEmpty()) {
            return null;
        }

        int issueLine = issue.getLocations().get(0).getLine();
        String issueTitle = issue.getTitle();

        try {
            IMarker[] markers = file.findMarkers("com.checkmarx.eclipse.plugin.checkmarxProblemMarker", true, org.eclipse.core.resources.IResource.DEPTH_ZERO);
            for (IMarker marker : markers) {
                int markerLine = marker.getAttribute(org.eclipse.core.resources.IMarker.LINE_NUMBER, -1);
                if (markerLine == issueLine) {
                    // Optional: also match by message prefix for better accuracy
                    String markerMsg = marker.getAttribute(org.eclipse.core.resources.IMarker.MESSAGE, "");
                    if (issueTitle == null || issueTitle.isEmpty() || markerMsg.contains(issueTitle)) {
                        return marker;
                    }
                }
            }
        } catch (Exception e) {
            
        }

        return null;
    }

    /**
     * Create a marker for a ScanIssue.
     *
     * **CRITICAL FIX**: Markers were never being created, only searched for.
     * This method creates markers on-demand when user navigates to an issue.
     *
     * **Works for ALL file types**: Java, Python, C++, JavaScript, YAML, XML, etc.
     * Uses Eclipse's universal IMarker API (not language-specific).
     *
     * Marker attributes are populated using MarkerIssueMapper to store
     * all ScanIssue data in marker attributes for later retrieval.
     *
     * @param file File to create marker in
     * @param issue ScanIssue to create marker for
     */
//    private void createMarkerForIssue(IFile file, ScanIssue issue) {
//        if (file == null || issue == null || issue.getLocations() == null || issue.getLocations().isEmpty()) {
//            
//            return;
//        }
//
//        try {
//            
//            
//            
//            
//            
//            
//            
//
//            // Step 1: Check if marker already exists for this issue
//            IMarker existingMarker = findMarkerForIssue(file, issue);
//            if (existingMarker != null && existingMarker.exists()) {
//                
//                return;
//            }
//
//            // Step 2: Create new marker using Eclipse's universal IMarker API
//            // **KEY**: Uses IMarker.PROBLEM which works for ALL file types
//            // - NOT language-specific (works for Java, Python, C++, JS, YAML, etc.)
//            // - Marker appears in Eclipse's Problems View
//            // - Can be navigated with IDE.gotoMarker()
//            IMarker newMarker = file.createMarker("com.checkmarx.eclipse.plugin.checkmarxProblemMarker");
//            
//
//            // Step 3: Populate marker attributes using MarkerIssueMapper
//            // This stores all ScanIssue data in marker for later retrieval
//            com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper.populateMarker(newMarker, issue);
//            
//
//            // Step 4: Verify marker creation
//            if (newMarker.exists()) {
//                String markerMsg = newMarker.getAttribute(org.eclipse.core.resources.IMarker.MESSAGE, "");
//                int markerLine = newMarker.getAttribute(org.eclipse.core.resources.IMarker.LINE_NUMBER, -1);
//                int markerSeverity = newMarker.getAttribute(org.eclipse.core.resources.IMarker.SEVERITY, -1);
//
//                
//                
//                
//                
//                
//                
//            } else {
//                
//            }
//
//        } catch (org.eclipse.core.runtime.CoreException e) {
//            System.err.println("[FINDINGS] [MARKER-CREATE] ✗ CoreException creating marker: " + e.getMessage());
//            e.printStackTrace();
//        } catch (Exception e) {
//            System.err.println("[FINDINGS] [MARKER-CREATE] ✗ Error creating marker: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }

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

        // Menu Item 1: View Details
        org.eclipse.swt.widgets.MenuItem viewDetailsItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
        viewDetailsItem.setText("View Details");
        viewDetailsItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                
                showIssueDetails(issue);
            }
        });

        // Menu Item 2: Fix with AI Assist
        org.eclipse.swt.widgets.MenuItem fixWithAIItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
        fixWithAIItem.setText("Fix with AI Assist");
        fixWithAIItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                
                fixWithAIAssist(issue);
            }
        });

        // Menu Item 3: Ignore This Finding
        org.eclipse.swt.widgets.MenuItem ignoreItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
        ignoreItem.setText("Ignore This Finding");
        ignoreItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                
                ignoreThisFinding(issue);
            }
        });

        // Menu Item 4: Ignore All of This Type (across all engines - matching is delegated
        // to IgnoreManager's stable per-engine key, so this isn't restricted to OSS/CONTAINERS)
        org.eclipse.swt.widgets.MenuItem ignoreAllItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
        ignoreAllItem.setText("Ignore All of This Type");
        ignoreAllItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {

                ignoreAllOfType(issue);
            }
        });

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
        
        
        

        // Apply active filters and refresh
        VulnerabilityFilterState filterState = VulnerabilityFilterState.getInstance();
        

        Map<String, List<ScanIssue>> filteredIssues = new HashMap<>();
        int totalBefore = 0;
        int totalAfter = 0;

        for (String filePath : currentIssues.keySet()) {
            List<ScanIssue> issues = currentIssues.get(filePath);
            if (issues == null) continue;

            totalBefore += issues.size();
            List<ScanIssue> filtered = new java.util.ArrayList<>();

            for (ScanIssue issue : issues) {
                // ✅ Safe null guard FIRST before calling any methods on issue
                if (issue == null || issue.getSeverity() == null) {
                    
                    continue;
                }

                boolean isIgnored = ignoreManager.isIgnored(issue);
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
                    if (element instanceof com.checkmarx.eclipse.devassist.ui.findings.model.FileNodeLabel) {
                        com.checkmarx.eclipse.devassist.ui.findings.model.FileNodeLabel fileNode =
                            (com.checkmarx.eclipse.devassist.ui.findings.model.FileNodeLabel) element;
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
        setPartName("Checkmarx One Assist Findings " + totalAfter);
    }

    /**
     * Refresh the tree with new issues. Safely dispatches to the SWT UI Thread.
     *
     * @param issues Map of file paths to list of scan issues
     */
    public void refreshTree(Map<String, List<ScanIssue>> issues) {
        if (issues == null) return;

        
        
        
        
        int totalIssues = issues.values().stream().filter(java.util.Objects::nonNull).mapToInt(List::size).sum();
        

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

        

        for (String filePath : issues.keySet()) {
            List<ScanIssue> fileIssues = issues.get(filePath);
        }

        
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

    /**
     * Listener implementation: called when the ignore store is updated (ignore/revive).
     */
    @Override
    public void onIgnoreUpdated() {
        if (treeViewer != null && treeViewer.getControl() != null && !treeViewer.getControl().isDisposed()) {
            treeViewer.getControl().getDisplay().asyncExec(this::refreshTreeWithFilter);
        }
    }


    private void setMarkerCharacterOffsets(IMarker marker, IFile file, int lineNumber) {
        try {
            org.eclipse.ui.IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) return;
            org.eclipse.ui.IWorkbenchPage page = window.getActivePage();
            if (page == null) return;
            org.eclipse.ui.IEditorPart editor = page.getActiveEditor();
            if (editor == null) return;

            org.eclipse.ui.texteditor.ITextEditor textEditor = editor.getAdapter(org.eclipse.ui.texteditor.ITextEditor.class);
            if (textEditor == null) return;

            org.eclipse.jface.text.IDocument doc = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
            if (doc == null || lineNumber <= 0 || lineNumber > doc.getNumberOfLines()) return;

            int lineIdx = lineNumber - 1;
            int lineOffset = doc.getLineOffset(lineIdx);
            int lineLen = doc.getLineLength(lineIdx);

            int trimOffset = getLeadingWhitespaceOffset(doc, lineOffset, lineLen);
            marker.setAttribute(IMarker.CHAR_START, lineOffset + trimOffset);
            marker.setAttribute(IMarker.CHAR_END, lineOffset + lineLen);

        } catch (Exception e) {
            // If we can't set char offsets, marker will still work with line-based positioning
        }
    }

    private int getLeadingWhitespaceOffset(org.eclipse.jface.text.IDocument document, int lineOffset, int lineLength) {
        try {
            String lineText = document.get(lineOffset, lineLength);
            int count = 0;
            for (int i = 0; i < lineText.length(); i++) {
                if (!Character.isWhitespace(lineText.charAt(i))) break;
                count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

}

