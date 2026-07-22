package com.checkmarx.eclipse.devassist.ui.findings;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import com.checkmarx.eclipse.devassist.ui.findings.provider.FindingsContentProvider;
import com.checkmarx.eclipse.devassist.ui.findings.provider.FindingsLabelProvider;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanDetailWithPath;
import com.checkmarx.eclipse.devassist.ui.findings.model.Location;
import com.checkmarx.eclipse.devassist.ui.findings.model.ScanEngine;
import com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper;
import com.checkmarx.eclipse.devassist.backend.ProblemHolderService;
import com.checkmarx.eclipse.devassist.ui.findings.actions.VulnerabilityFilterAction;
import com.checkmarx.eclipse.devassist.ui.findings.actions.VulnerabilityFilterState;
import com.checkmarx.eclipse.devassist.ui.findings.ignored.IgnoredProblemsStore;
import com.checkmarx.eclipse.devassist.ui.findings.ignored.IgnoredProblemsStore.IgnoredProblemsListener;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


/**
 * Custom Findings View for displaying Checkmarx scan results.
 * Extends {@link ViewPart} to provide a custom view in Eclipse.
 * Manages a tree view of vulnerabilities with filtering and navigation capabilities.
 * Uses {@link TreeViewer} for flexible tree rendering with custom providers.
 */
public class CxFindingsView extends ViewPart implements IgnoredProblemsListener {

    public static final String ID = "com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView";
    private org.osgi.service.event.EventHandler eventHandler;

    private TreeViewer treeViewer;
    private Map<String, List<ScanIssue>> currentIssues = new HashMap<>();
    private IgnoredProblemsStore ignoredStore;

    public CxFindingsView() {
        super();
    }

    
    /**
     * Subscribes to ProblemHolderService updates via Eclipse IEventBroker.
     */
    private void subscribeToEventBroker() {
        try {
            org.eclipse.e4.core.services.events.IEventBroker eventBroker = 
                getSite().getService(org.eclipse.e4.core.services.events.IEventBroker.class);

            if (eventBroker == null) {
                eventBroker = org.eclipse.ui.PlatformUI.getWorkbench().getService(
                    org.eclipse.e4.core.services.events.IEventBroker.class);
            }

            if (eventBroker != null) {
                // Save handler reference so we can unsubscribe later in dispose()
                eventHandler = event -> {
                    Object data = event.getProperty(org.eclipse.e4.core.services.events.IEventBroker.DATA);
                    
                    if (data instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, List<ScanIssue>> newIssues = (Map<String, List<ScanIssue>>) data;
                        
                        System.out.println("[FINDINGS] [EVENT-RECEIVED] Received updated scan issues from IEventBroker");

                        // UI elements MUST be updated on the SWT Display Thread
                        org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
                            if (treeViewer != null && !treeViewer.getControl().isDisposed()) {
                                this.currentIssues = newIssues;
                                refreshTreeWithFilter();
                            }
                        });
                    }
                };

                eventBroker.subscribe(com.checkmarx.eclipse.devassist.backend.ProblemHolderService.ISSUES_UPDATED_TOPIC, eventHandler);
                System.out.println("[FINDINGS] [INIT-STEP 3/5] ✓ Registered IEventBroker subscriber on topic: " 
                    + com.checkmarx.eclipse.devassist.backend.ProblemHolderService.ISSUES_UPDATED_TOPIC);
            } else {
                System.err.println("[FINDINGS] [INIT-STEP 3/5] ✗ IEventBroker service unavailable");
            }
        } catch (Exception e) {
            System.err.println("[FINDINGS] ✗ Error subscribing to IEventBroker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void dispose() {
        System.out.println("[FINDINGS] Disposing CxFindingsView...");

        // 1. Unsubscribe from IEventBroker to prevent memory leaks
        if (eventHandler != null) {
            try {
                org.eclipse.e4.core.services.events.IEventBroker eventBroker = 
                    org.eclipse.ui.PlatformUI.getWorkbench().getService(
                        org.eclipse.e4.core.services.events.IEventBroker.class);
                        
                if (eventBroker != null) {
                    eventBroker.unsubscribe(eventHandler);
                    System.out.println("[FINDINGS] ✓ Unsubscribed from IEventBroker");
                }
            } catch (Exception e) {
                System.err.println("[FINDINGS] Error unsubscribing from IEventBroker: " + e.getMessage());
            }
        }

        // 2. Unsubscribe from IgnoredProblemsStore
        if (ignoredStore != null) {
            // If your IgnoredProblemsStore supports removing listeners, call it here:
            // ignoredStore.removeListener(this);
        }

        super.dispose();
    }
    
    @Override
    public void createPartControl(Composite parent) {
        try {
            // STEP 1 & 2: Try to load cached results if ProblemHolderService exists
            System.out.println("[FINDINGS] [INIT-STEP 1/5] Getting workspace projects...");
            IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();

            if (projects.length > 0 && projects[0].isOpen()) {
                IProject project = projects[0];
                System.out.println("[FINDINGS] [INIT-STEP 1/5] ✓ Found project: " + project.getName());

                ProblemHolderService problemHolder = (ProblemHolderService) project.getSessionProperty(
                        new QualifiedName("com.checkmarx.eclipse.plugin", "problem-holder"));

                if (problemHolder != null) {
                    Map<String, List<ScanIssue>> existingIssues = problemHolder.getAllScanIssues();
                    if (existingIssues != null && !existingIssues.isEmpty()) {
                        System.out.println("[FINDINGS] [INIT-STEP 2/5] ✓ Found " + existingIssues.size() + " cached issue files");
                        this.currentIssues = existingIssues;
                    }
                } else {
                    System.out.println("[FINDINGS] [INIT-STEP 2/5] No cache found on startup. Waiting for IEventBroker events...");
                }
            }

            // STEP 3: ALWAYS subscribe to IEventBroker regardless of cache state (THE FIX)
            System.out.println("[FINDINGS] [INIT-STEP 3/5] Subscribing to EventBroker...");
            subscribeToEventBroker();

            // STEP 4: Setup UI and Ignored Store
            System.out.println("[FINDINGS] [INIT-STEP 4/5] Creating UI components...");
            ignoredStore = IgnoredProblemsStore.getInstance();
            ignoredStore.addListener(this);

            SashForm sashForm = new SashForm(parent, SWT.HORIZONTAL);
            sashForm.setLayout(new FillLayout());

            Composite treeComposite = new Composite(sashForm, SWT.NONE);
            treeComposite.setLayout(new FillLayout());

            treeViewer = new TreeViewer(treeComposite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
            treeViewer.setContentProvider(new FindingsContentProvider());
            treeViewer.setLabelProvider(new FindingsLabelProvider());

            Composite promotionalComposite = new Composite(sashForm, SWT.NONE);
            promotionalComposite.setLayout(new FillLayout());

            sashForm.setWeights(new int[] { 70, 30 });

            setupToolbar();
            setupTreeListeners();

            // STEP 5: Display cached results if any existed at startup
            System.out.println("[FINDINGS] [INIT-STEP 5/5] Initializing tree view...");
            if (!currentIssues.isEmpty()) {
                refreshTreeWithFilter();
            }

        } catch (Exception e) {
            System.err.println("[FINDINGS] ✗ ERROR during view creation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupToolbar() {
        System.out.println("[FINDINGS] Setting up toolbar with severity filters...");
        IToolBarManager toolbar = getViewSite().getActionBars().getToolBarManager();

        // Add filter actions
        VulnerabilityFilterAction.IFilterChangeListener filterListener = () -> {
            System.out.println("[FINDINGS] Filter changed - refreshing tree");
            refreshTreeWithFilter();
        };

        toolbar.add(new VulnerabilityFilterAction.MaliciousFilter(filterListener));
        toolbar.add(new VulnerabilityFilterAction.CriticalFilter(filterListener));
        toolbar.add(new VulnerabilityFilterAction.HighFilter(filterListener));
        toolbar.add(new VulnerabilityFilterAction.MediumFilter(filterListener));
        toolbar.add(new VulnerabilityFilterAction.LowFilter(filterListener));

        toolbar.update(true);
        System.out.println("[FINDINGS] Toolbar configured with 5 severity filters");
    }

    private void setupTreeListeners() {
        Tree tree = treeViewer.getTree();
        System.out.println("[FINDINGS] Setting up tree listeners...");

        //Listner for redirection
        tree.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                System.out.println("[FINDINGS] Single-click detected");
                navigateToSelectedIssue(treeViewer.getSelection());
            }
        });

        // Right-click context menu
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                if (e.button == 3) {
                    System.out.println("[FINDINGS] Right-click detected at coordinates: " + e.x + ", " + e.y);
                    showContextMenu(e);
                }
            }
        });

        System.out.println("[FINDINGS] Tree listeners configured");
    }

    private void navigateToSelectedIssue(ISelection selection) {
        System.out.println("[FINDINGS] Navigating to selected issue...");
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
            System.out.println("[FINDINGS] Navigating to: " + filePath + ", Line: " + location.getLine());
            openFileInEditor(filePath, location.getLine(), detail);
        } else {
            System.out.println("[FINDINGS] No location information available for issue: " + detail.getTitle());
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

        System.out.println(details.toString());
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
                System.out.println("[FINDINGS] ERROR: Failed to build remediation prompt");
                showErrorNotification("Failed to build prompt for this issue type");
                return;
            }

            // Send to Copilot via integration
            System.out.println("[FINDINGS] Sending prompt to Copilot...");
            boolean success = com.checkmarx.eclipse.devassist.ui.findings.integration.CopilotIntegration
                    .sendPromptToCopilot(prompt);

            if (success) {
                System.out.println("[FINDINGS] ✓ Prompt sent to Copilot successfully");
            } else {
                System.out.println("[FINDINGS] ! Copilot not available, prompt in clipboard");
            }

        } catch (Exception e) {
            System.out.println("[FINDINGS] ERROR: Exception in fixWithAIAssist: " + e.getMessage());
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
     * The finding is added to the IgnoredProblemsStore and appears in the Ignored Problems Window.
     */
    private void ignoreThisFinding(ScanIssue issue) {

        try {
            // Verify store is initialized
            if (ignoredStore == null) {
                System.err.println("[FINDINGS] ✗ ERROR: IgnoredProblemsStore is NULL!");
                showErrorNotification("Error: IgnoredProblemsStore not initialized");
                return;
            }

            System.out.println("[FINDINGS] ✓ IgnoredProblemsStore is initialized");

            // Add to ignored store with full finding details for display in Ignored Problems View
            ignoredStore.ignoreProblem(issue);
            System.out.println("[FINDINGS] ✓ Added to IgnoredProblemsStore: " + issue.getScanIssueId());

            // Check if it was actually added
            boolean isIgnored = ignoredStore.isIgnored(issue.getScanIssueId());

            // Refresh the tree to remove the ignored finding
            System.out.println("[FINDINGS] Calling refreshTreeWithFilter...");
            refreshTreeWithFilter();
            System.out.println("[FINDINGS] ✓ Findings tree refreshed - finding removed");

            System.out.println("[FINDINGS] ========================================");
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
     */
    private void ignoreAllOfType(ScanIssue issue) {
        
        try {
            int ignoredCount = 0;
            String typeIdentifier = issue.getPackageVersion() != null ? issue.getPackageVersion() : issue.getImageTag();

            // Iterate through all current issues and ignore matching ones
            for (List<ScanIssue> issues : currentIssues.values()) {
                for (ScanIssue currentIssue : issues) {
                    // Match by same type/package/image
                    if (currentIssue.getScanEngine() == issue.getScanEngine()) {
                        String currentTypeIdentifier = currentIssue.getPackageVersion() != null ?
                                currentIssue.getPackageVersion() : currentIssue.getImageTag();

                        if (typeIdentifier != null && typeIdentifier.equals(currentTypeIdentifier)) {
                            ignoredStore.ignoreProblem(currentIssue);
                            ignoredCount++;
                        }
                    }
                }
            }

            System.out.println("[FINDINGS] ✓ Ignored " + ignoredCount + " findings of this type");
            refreshTreeWithFilter();
            System.out.println("[FINDINGS] ✓ Findings tree refreshed");
            System.out.println("[FINDINGS] ========================================");
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
            System.out.println("[FINDINGS] Issue details copied to clipboard");
            System.out.println(json.toString());
        } catch (Exception e) {
            System.out.println("[FINDINGS] Failed to copy to clipboard: " + e.getMessage());
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void openFileInEditor(String filePath, int lineNumber, ScanIssue issue) {
        try {
            System.out.println("[FINDINGS] Attempting to navigate to: " + filePath + " at line " + lineNumber);

            // Try to find file in workspace
            IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(
                    new org.eclipse.core.runtime.Path(filePath));

            if (file == null || !file.exists()) {
                System.out.println("✗ File not found in workspace: " + filePath);
                System.out.println("  Checking workspace structure...");
                // List available projects for debugging
                org.eclipse.core.resources.IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
                System.out.println("  Available projects: " + projects.length);
                for (org.eclipse.core.resources.IProject p : projects) {
                    if (p.isOpen()) {
                        System.out.println("    - " + p.getName());
                    }
                }
                return;
            }

            // Open file in editor
            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            org.eclipse.ui.IEditorPart editor = IDE.openEditor(page, file);
            System.out.println("EDITOR = " + editor);
            System.out.println("EDITOR CLASS = " + editor.getClass().getName());
            System.out.println("✓ Opened file: " + filePath);

            // Scroll to line and highlight
            scrollToLine(editor, lineNumber);

            // **FIX: Create marker BEFORE trying to navigate to it (works for ALL languages)**
            createMarkerForIssue(file, issue);
            System.out.println("[FINDINGS] ✓ Created marker for issue");

            // Use marker-based navigation (now that marker exists)
            highlightViaMarker(editor, file, issue);

            System.out.println("[FINDINGS] ✓ Navigation complete - marker underline active");

        } catch (Exception e) {
            System.out.println("✗ Error navigating to file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Scroll editor to specific line number.
     * Note: Requires TextEditor which may not be available in all Eclipse configurations.
     */
    private void scrollToLine(org.eclipse.ui.IEditorPart editor, int lineNumber) {
        try {
            // Try using reflection to call selectAndReveal if available
            if (editor != null && lineNumber > 0) {
                java.lang.reflect.Method method = editor.getClass().getMethod("selectAndReveal", int.class, int.class);
                org.eclipse.jface.text.IDocument document = null;

                // Try to get document through getDocumentProvider
                try {
                    java.lang.reflect.Method getDocProvider = editor.getClass().getMethod("getDocumentProvider");
                    Object docProvider = getDocProvider.invoke(editor);
                    java.lang.reflect.Method getDoc = docProvider.getClass().getMethod("getDocument", Object.class);
                    document = (org.eclipse.jface.text.IDocument) getDoc.invoke(docProvider, editor.getEditorInput());
                } catch (Exception e) {
                    // Document not available
                }

                if (document != null && lineNumber <= document.getNumberOfLines()) {
                    int lineOffset = document.getLineOffset(lineNumber - 1);
                    method.invoke(editor, lineOffset, 0);
                    System.out.println("[FINDINGS] ✓ Scrolled to line " + lineNumber);
                }
            }
        } catch (Exception e) {
            System.out.println("[FINDINGS] Line scrolling not available in this editor: " + e.getMessage());
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
                System.out.println("[FINDINGS] ✓ Navigated to marker for: " + issue.getTitle());
            } else {
                System.out.println("[FINDINGS] No marker found for issue: " + issue.getTitle());
            }
        } catch (Exception e) {
            System.out.println("[FINDINGS] Error navigating to marker: " + e.getMessage());
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
            System.out.println("[FINDINGS] Error finding marker for issue: " + e.getMessage());
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
    private void createMarkerForIssue(IFile file, ScanIssue issue) {
        if (file == null || issue == null || issue.getLocations() == null || issue.getLocations().isEmpty()) {
            System.out.println("[FINDINGS] [MARKER-CREATE] ✗ Missing file, issue, or locations");
            return;
        }

        try {
            System.out.println("[FINDINGS] [MARKER-CREATE] ╔═══════════════════════════════════════╗");
            System.out.println("[FINDINGS] [MARKER-CREATE] ║ Creating marker for ScanIssue        ║");
            System.out.println("[FINDINGS] [MARKER-CREATE] ╚═══════════════════════════════════════╝");
            System.out.println("[FINDINGS] [MARKER-CREATE] File: " + file.getFullPath());
            System.out.println("[FINDINGS] [MARKER-CREATE] Issue: " + issue.getTitle());
            System.out.println("[FINDINGS] [MARKER-CREATE] Engine: " + issue.getScanEngine());
            System.out.println("[FINDINGS] [MARKER-CREATE] Line: " + issue.getLocations().get(0).getLine());

            // Step 1: Check if marker already exists for this issue
            IMarker existingMarker = findMarkerForIssue(file, issue);
            if (existingMarker != null && existingMarker.exists()) {
                System.out.println("[FINDINGS] [MARKER-CREATE] ✓ Marker already exists, skipping creation");
                return;
            }

            // Step 2: Create new marker using Eclipse's universal IMarker API
            // **KEY**: Uses IMarker.PROBLEM which works for ALL file types
            // - NOT language-specific (works for Java, Python, C++, JS, YAML, etc.)
            // - Marker appears in Eclipse's Problems View
            // - Can be navigated with IDE.gotoMarker()
            IMarker newMarker = file.createMarker("com.checkmarx.eclipse.plugin.checkmarxProblemMarker");
            System.out.println("[FINDINGS] [MARKER-CREATE] ✓ Marker created");

            // Step 3: Populate marker attributes using MarkerIssueMapper
            // This stores all ScanIssue data in marker for later retrieval
            com.checkmarx.eclipse.devassist.ui.findings.marker.MarkerIssueMapper.populateMarker(newMarker, issue);
            System.out.println("[FINDINGS] [MARKER-CREATE] ✓ Marker populated with issue data");

            // Step 4: Verify marker creation
            if (newMarker.exists()) {
                String markerMsg = newMarker.getAttribute(org.eclipse.core.resources.IMarker.MESSAGE, "");
                int markerLine = newMarker.getAttribute(org.eclipse.core.resources.IMarker.LINE_NUMBER, -1);
                int markerSeverity = newMarker.getAttribute(org.eclipse.core.resources.IMarker.SEVERITY, -1);

                System.out.println("[FINDINGS] [MARKER-CREATE] ✓ Marker verified:");
                System.out.println("[FINDINGS] [MARKER-CREATE]   ID: " + newMarker.getId());
                System.out.println("[FINDINGS] [MARKER-CREATE]   Message: " + markerMsg);
                System.out.println("[FINDINGS] [MARKER-CREATE]   Line: " + markerLine);
                System.out.println("[FINDINGS] [MARKER-CREATE]   Severity: " + markerSeverity);
                System.out.println("[FINDINGS] [MARKER-CREATE] ═════════════════════════════════════════");
            } else {
                System.out.println("[FINDINGS] [MARKER-CREATE] ✗ Failed to create marker!");
            }

        } catch (org.eclipse.core.runtime.CoreException e) {
            System.err.println("[FINDINGS] [MARKER-CREATE] ✗ CoreException creating marker: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[FINDINGS] [MARKER-CREATE] ✗ Error creating marker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showContextMenu(MouseEvent e) {
        ISelection selection = treeViewer.getSelection();
        if (!(selection instanceof IStructuredSelection)) {
            System.out.println("[FINDINGS] Invalid selection for context menu");
            return;
        }

        IStructuredSelection ssel = (IStructuredSelection) selection;
        Object element = ssel.getFirstElement();

        if (!(element instanceof ScanDetailWithPath)) {
            System.out.println("[FINDINGS] Context menu: Selected element is not a ScanDetailWithPath");
            return;
        }

        ScanDetailWithPath detailWithPath = (ScanDetailWithPath) element;
        ScanIssue issue = detailWithPath.getDetail();

        System.out.println("[FINDINGS] Creating context menu for: " + issue.getTitle());

        org.eclipse.swt.widgets.Menu menu = new org.eclipse.swt.widgets.Menu(treeViewer.getTree());

        // Menu Item 1: View Details
        org.eclipse.swt.widgets.MenuItem viewDetailsItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
        viewDetailsItem.setText("View Details");
        viewDetailsItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                System.out.println("[FINDINGS] Action: View Details - Issue: " + issue.getTitle());
                showIssueDetails(issue);
            }
        });

        // Menu Item 2: Fix with AI Assist
        org.eclipse.swt.widgets.MenuItem fixWithAIItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
        fixWithAIItem.setText("Fix with AI Assist");
        fixWithAIItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                System.out.println("[FINDINGS] Action: Fix with AI Assist - Issue: " + issue.getTitle());
                fixWithAIAssist(issue);
            }
        });

        // Menu Item 3: Ignore This Finding
        org.eclipse.swt.widgets.MenuItem ignoreItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
        ignoreItem.setText("Ignore This Finding");
        ignoreItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                System.out.println("[FINDINGS] Action: Ignore This Finding - Issue: " + issue.getTitle());
                ignoreThisFinding(issue);
            }
        });

        // Menu Item 4: Ignore All of This Type (for OSS and CONTAINERS)
        if (issue.getScanEngine() == ScanEngine.OSS || issue.getScanEngine() == ScanEngine.CONTAINERS) {
            org.eclipse.swt.widgets.MenuItem ignoreAllItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
            ignoreAllItem.setText("Ignore All of This Type");
            ignoreAllItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
                @Override
                public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                    System.out.println("[FINDINGS] Action: Ignore All of This Type - Issue Type: " + issue.getTitle());
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
                System.out.println("[FINDINGS] Action: Copy Issue Details - Issue: " + issue.getTitle());
                copyIssueDetails(issue);
            }
        });

        // Menu Item 6: Open in Terminal
        org.eclipse.swt.widgets.MenuItem terminalItem = new org.eclipse.swt.widgets.MenuItem(menu, SWT.PUSH);
        terminalItem.setText("Navigate to Line");
        terminalItem.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                System.out.println("[FINDINGS] Action: Navigate to Line - File: " + detailWithPath.getFilePath());
                navigateToIssue(detailWithPath);
            }
        });

        menu.setLocation(treeViewer.getTree().toDisplay(e.x, e.y));
        menu.setVisible(true);
    }

    private void refreshTreeWithFilter() {
        System.out.println("[FINDINGS] ========== REFRESH TREE START ==========");
        System.out.println("[FINDINGS] Refreshing tree with active filters and ignored problems...");
        System.out.println("[FINDINGS] Current issues: " + currentIssues.size() + " files");

        // Apply active filters and refresh
        VulnerabilityFilterState filterState = VulnerabilityFilterState.getInstance();
        System.out.println("[FINDINGS] Active filters: " + filterState.getFilters());
        System.out.println("[FINDINGS] Ignored IDs in store: " + 
                (ignoredStore != null ? ignoredStore.getIgnoredProblemIds() : "[]"));

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
                    System.out.println("[FINDINGS] WARNING: Null issue or severity detected");
                    continue;
                }

                String issueId = issue.getScanIssueId();
                boolean isIgnored = ignoredStore != null && ignoredStore.isIgnored(issueId);
                boolean hasFilter = filterState.hasFilter(issue.getSeverity());

                System.out.println("[FINDINGS] Issue: " + issue.getTitle() +
                        " | ID: " + issueId +
                        " | Ignored: " + isIgnored +
                        " | HasFilter: " + hasFilter);

                // Filter by severity preference
                if (!hasFilter) {
                    System.out.println("[FINDINGS]   -> Filtered out by severity");
                    continue;
                }
                // Filter out ignored problems
                if (isIgnored) {
                    System.out.println("[FINDINGS]   -> Filtered out because IGNORED");
                    continue;
                }

                System.out.println("[FINDINGS]   -> KEEPING");
                filtered.add(issue);
            }

            if (!filtered.isEmpty()) {
                filteredIssues.put(filePath, filtered);
                totalAfter += filtered.size();
            }
        }

        System.out.println("[FINDINGS] Total issues before filtering: " + totalBefore);
        System.out.println("[FINDINGS] Total issues after filtering: " + totalAfter);
        System.out.println("[FINDINGS] Filtered issues map: " + filteredIssues.size() + " files");

        // ✅ Verify treeViewer control before manipulating UI
        if (treeViewer != null && treeViewer.getControl() != null && !treeViewer.getControl().isDisposed()) {
            System.out.println("[FINDINGS] Setting tree input...");
            treeViewer.setInput(filteredIssues);
            System.out.println("[FINDINGS] Expanding all nodes...");
            treeViewer.expandAll();
        }

        System.out.println("[FINDINGS] ========== REFRESH TREE END ==========");
    }

    /**
     * Refresh the tree with new issues. Safely dispatches to the SWT UI Thread.
     *
     * @param issues Map of file paths to list of scan issues
     */
    public void refreshTree(Map<String, List<ScanIssue>> issues) {
        if (issues == null) return;

        System.out.println("[FINDINGS] ╔════════════════════════════════════════════╗");
        System.out.println("[FINDINGS] ║ FINDINGS VIEW: REFRESH TREE                  ║");
        System.out.println("[FINDINGS] ╚════════════════════════════════════════════╝");
        System.out.println("[FINDINGS] Input: " + issues.size() + " files");
        int totalIssues = issues.values().stream().filter(java.util.Objects::nonNull).mapToInt(List::size).sum();
        System.out.println("[FINDINGS] Total Issues: " + totalIssues);

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

        System.out.println("[FINDINGS] Severity breakdown:");
        severityCounts.forEach((severity, count) ->
            System.out.println("[FINDINGS]   - " + severity + ": " + count)
        );

        for (String filePath : issues.keySet()) {
            List<ScanIssue> fileIssues = issues.get(filePath);
            System.out.println("[FINDINGS] File: " + filePath + " → " + (fileIssues != null ? fileIssues.size() : 0) + " issues");
        }

        System.out.println("[FINDINGS] Setting currentIssues and dispatching UI update...");
        this.currentIssues = issues;

        // ✅ Thread-safe dispatching for background updates
        org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
            if (treeViewer != null && treeViewer.getControl() != null && !treeViewer.getControl().isDisposed()) {
                refreshTreeWithFilter();
            }
        });

        System.out.println("[FINDINGS] ════════════════════════════════════════════");
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
     * Listener implementation: called when ignored problems are restored or cleared.
     */
    @Override
    public void onIgnoredProblemsChanged() {
        System.out.println("[FINDINGS] Ignored problems changed - refreshing findings tree");
        if (treeViewer != null && treeViewer.getControl() != null && !treeViewer.getControl().isDisposed()) {
            treeViewer.getControl().getDisplay().asyncExec(this::refreshTreeWithFilter);
        }
    }


}
