package com.checkmarx.eclipse.views.findings;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import com.checkmarx.eclipse.views.findings.provider.FindingsContentProvider;
import com.checkmarx.eclipse.views.findings.provider.FindingsLabelProvider;
import com.checkmarx.eclipse.views.findings.model.ScanIssue;
import com.checkmarx.eclipse.views.findings.model.ScanDetailWithPath;
import com.checkmarx.eclipse.views.findings.model.Location;
import com.checkmarx.eclipse.views.findings.model.ScanEngine;
import com.checkmarx.eclipse.views.findings.model.FileNodeLabel;
import com.checkmarx.eclipse.views.findings.actions.VulnerabilityFilterAction;
import com.checkmarx.eclipse.views.findings.actions.VulnerabilityFilterState;
import com.checkmarx.eclipse.views.problems.provider.MockProblemProvider;
import com.checkmarx.eclipse.views.problems.model.ScanProblem;
import com.checkmarx.eclipse.views.findings.ignored.IgnoredProblemsStore;
import com.checkmarx.eclipse.views.findings.ignored.IgnoredProblemsStore.IgnoredProblemsListener;
import com.checkmarx.eclipse.enums.Severity;

import java.util.ArrayList;
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

    public static final String ID = "com.checkmarx.eclipse.views.findings.CxFindingsView";

    private TreeViewer treeViewer;
    private Map<String, List<ScanIssue>> currentIssues = new HashMap<>();
    private IgnoredProblemsStore ignoredStore;

    public CxFindingsView() {
        super();
    }

    @Override
    public void createPartControl(Composite parent) {
        System.out.println("[FINDINGS] ========================================");
        System.out.println("[FINDINGS] Creating Checkmarx Findings View...");
        System.out.println("[FINDINGS] ========================================");

        try {
            // Register with IgnoredProblemsStore to listen for restore events
            ignoredStore = IgnoredProblemsStore.getInstance();
            ignoredStore.addListener(this);
            System.out.println("[FINDINGS] ✓ Registered with IgnoredProblemsStore");

            // Create main sash form for split view (findings + promotional panel)
            SashForm sashForm = new SashForm(parent, SWT.HORIZONTAL);
            sashForm.setLayout(new FillLayout());
            System.out.println("[FINDINGS] Sash form created");

            // Create findings tree
            Composite treeComposite = new Composite(sashForm, SWT.NONE);
            treeComposite.setLayout(new FillLayout());

            treeViewer = new TreeViewer(treeComposite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
            treeViewer.setContentProvider(new FindingsContentProvider());
            treeViewer.setLabelProvider(new FindingsLabelProvider());
            System.out.println("[FINDINGS] Tree viewer configured");

            // Create promotional panel (right side)
            Composite promotionalComposite = new Composite(sashForm, SWT.NONE);
            promotionalComposite.setLayout(new FillLayout());
            // TODO: Add promotional panel content

            sashForm.setWeights(new int[] { 70, 30 });
            System.out.println("[FINDINGS] UI layout configured (70/30 split)");

            // Setup toolbar
            setupToolbar();

            // Setup tree listeners
            setupTreeListeners();

            // Load dummy data for testing
            loadDummyProblems();

            System.out.println("[FINDINGS] View created successfully!");
        } catch (Exception e) {
            System.out.println("[FINDINGS] ERROR during view creation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load mock problems for testing the UI.
     */
    private void loadDummyProblems() {
        System.out.println("[FINDINGS] Loading mock problems from MockProblemProvider...");
        Map<String, List<ScanIssue>> mockIssues = new HashMap<>();

        try {
            // Load mock problems from MockProblemProvider
            MockProblemProvider mockProvider = new MockProblemProvider();
            List<ScanProblem> mockProblems = mockProvider.getProblems();
            System.out.println("[FINDINGS] ✓ Loaded " + mockProblems.size() + " mock problems");

            // Group problems by file path
            Map<String, List<ScanIssue>> groupedIssues = new HashMap<>();
            for (ScanProblem problem : mockProblems) {
                ScanIssue issue = convertScanProblemToScanIssue(problem);
                String filePath = problem.getFileName();
                groupedIssues.computeIfAbsent(filePath, k -> new ArrayList<>()).add(issue);
            }

            mockIssues.putAll(groupedIssues);
            System.out.println("[FINDINGS] ✓ Converted to " + mockIssues.size() + " file groups");
        } catch (Exception e) {
            System.out.println("[FINDINGS] Warning: Could not load mock problems from MockProblemProvider: " + e.getMessage());
            System.out.println("[FINDINGS] Falling back to inline dummy problems...");
            loadInlineDummyProblems(mockIssues);
            return;
        }

        // Refresh the tree with mock data
        refreshTree(mockIssues);
    }

    /**
     * Fallback: Load inline dummy problems if MockProblemProvider fails.
     */
    private void loadInlineDummyProblems(Map<String, List<ScanIssue>> dummyIssues) {
        System.out.println("[FINDINGS] Loading inline dummy problems...");

        // Try to find real files in workspace, fallback to dummy paths
        String[] javaFiles = findJavaFilesInWorkspace();
        System.out.println("[FINDINGS] Found " + javaFiles.length + " Java files in workspace");

        if (javaFiles.length > 0) {
            // Use real files from workspace
            System.out.println("[FINDINGS] ✓ Using real workspace files for dummy problems");

            // File 1: First Java file
            List<ScanIssue> issues1 = new ArrayList<>();
            issues1.add(createDummyIssue("SQL Injection Vulnerability", "critical", "SQL injection detected in query builder", ScanEngine.ASCA, 5));
            issues1.add(createDummyIssue("Hardcoded Password", "high", "Database password hardcoded in source", ScanEngine.SECRETS, 10));
            dummyIssues.put(javaFiles[0], issues1);

            // File 2: Second Java file if available
            if (javaFiles.length > 1) {
                List<ScanIssue> issues2 = new ArrayList<>();
                issues2.add(createDummyIssue("XSS Vulnerability", "high", "Unescaped user input in HTML output", ScanEngine.ASCA, 15));
                issues2.add(createDummyIssue("log4j-core", "critical", "Apache Log4j vulnerable to RCE", ScanEngine.OSS, 20, "2.14.1", "2.17.0"));
                dummyIssues.put(javaFiles[1], issues2);
            }
        } else {
            // Use dummy paths
            System.out.println("No workspace files found. Using dummy paths (navigation won't work)");

            List<ScanIssue> mainJavaIssues = new ArrayList<>();
            mainJavaIssues.add(createDummyIssue("SQL Injection Vulnerability", "critical", "SQL injection detected in query builder", ScanEngine.ASCA, 1));
            mainJavaIssues.add(createDummyIssue("Hardcoded Password", "high", "Database password hardcoded in source", ScanEngine.SECRETS, 2));
            mainJavaIssues.add(createDummyIssue("Missing Input Validation", "high", "User input not validated before use", ScanEngine.ASCA, 3));
            dummyIssues.put("C:\\Project\\MyApp\\src\\Main.java", mainJavaIssues);

            List<ScanIssue> utilsJavaIssues = new ArrayList<>();
            utilsJavaIssues.add(createDummyIssue("XSS Vulnerability", "high", "Unescaped user input in HTML output", ScanEngine.ASCA, 4));
            utilsJavaIssues.add(createDummyIssue("log4j-core", "critical", "Apache Log4j vulnerable to RCE", ScanEngine.OSS, 5, "2.14.1", "2.17.0"));
            utilsJavaIssues.add(createDummyIssue("API Key Exposed", "malicious", "AWS API key exposed in repository", ScanEngine.SECRETS, 6));
            dummyIssues.put("C:\\Project\\MyApp\\src\\Utils.java", utilsJavaIssues);

            List<ScanIssue> configIssues = new ArrayList<>();
            configIssues.add(createDummyIssue("Insecure Configuration", "medium", "Database credentials stored in plain text", ScanEngine.IAC, 7));
            configIssues.add(createDummyIssue("Missing TLS Configuration", "high", "TLS not enforced for external connections", ScanEngine.IAC, 8));
            dummyIssues.put("C:\\Project\\MyApp\\config\\application.yaml", configIssues);

            List<ScanIssue> dockerIssues = new ArrayList<>();
            dockerIssues.add(createDummyIssue("nginx:latest", "medium", "Base image uses latest tag instead of specific version", ScanEngine.CONTAINERS, 9));
            dockerIssues.add(createDummyIssue("openssl", "high", "OpenSSL library has known vulnerabilities", ScanEngine.CONTAINERS, 10, null, "1.1.1w"));
            dummyIssues.put("C:\\Project\\MyApp\\Dockerfile", dockerIssues);
        }

        // Refresh the tree with dummy data
        refreshTree(dummyIssues);
    }

    /**
     * Find Java files in the workspace for real problem injection.
     */
    private String[] findJavaFilesInWorkspace() {
        List<String> javaFiles = new ArrayList<>();
        try {
            org.eclipse.core.resources.IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
            for (org.eclipse.core.resources.IProject project : projects) {
                if (project.isOpen()) {
                    findJavaFilesRecursive(project, javaFiles, 5); // Limit to first 5 files
                    if (javaFiles.size() >= 2) break;
                }
            }
        } catch (Exception e) {
            System.out.println("Error finding workspace files: " + e.getMessage());
        }
        return javaFiles.toArray(new String[0]);
    }

    /**
     * Recursively find Java files in a resource.
     */
    private void findJavaFilesRecursive(org.eclipse.core.resources.IResource resource, List<String> javaFiles, int limit) throws Exception {
        if (javaFiles.size() >= limit) return;

        if (resource instanceof org.eclipse.core.resources.IFile) {
            org.eclipse.core.resources.IFile file = (org.eclipse.core.resources.IFile) resource;
            if ("java".equals(file.getFileExtension())) {
                javaFiles.add(file.getLocation().toOSString());
            }
        } else if (resource instanceof org.eclipse.core.resources.IFolder) {
            org.eclipse.core.resources.IFolder folder = (org.eclipse.core.resources.IFolder) resource;
            for (org.eclipse.core.resources.IResource child : folder.members()) {
                findJavaFilesRecursive(child, javaFiles, limit);
            }
        }
    }

    /**
     * Create a dummy scan issue for testing.
     */
    private ScanIssue createDummyIssue(String title, String severity, String description, ScanEngine engine, int lineNumber) {
        return createDummyIssue(title, severity, description, engine, lineNumber, null, null);
    }

    /**
     * Create a dummy scan issue with package/version info.
     */
    private ScanIssue createDummyIssue(String title, String severity, String description, ScanEngine engine, int lineNumber, String version, String fixedVersion) {
        ScanIssue issue = new ScanIssue();
        issue.setScanIssueId("dummy-" + System.nanoTime());
        issue.setTitle(title != null ? title : "Unknown Issue");
        issue.setSeverity(severity != null ? severity : "medium");
        issue.setDescription(description != null ? description : "No description available");
        issue.setScanEngine(engine != null ? engine : ScanEngine.ASCA);
        issue.setRemediationAdvise("Review and fix this security issue. Use input validation and parameterized queries.");

        // Set engine-specific properties
        if (engine == ScanEngine.OSS) {
            issue.setPackageVersion(version);
            issue.setPackageManager("maven");
            issue.setCve("CVE-2021-44228");
        } else if (engine == ScanEngine.CONTAINERS) {
            issue.setImageTag(version);
        } else if (engine == ScanEngine.SECRETS) {
            issue.setSecretValue("****KEY****");
        }

        // Add location info
        Location location = new Location(lineNumber, 0, 50);
        issue.setLocations(new ArrayList<>());
        issue.getLocations().add(location);

        return issue;
    }

    /**
     * Convert a ScanProblem (from Problems package) to a ScanIssue (for Findings window).
     */
    private ScanIssue convertScanProblemToScanIssue(ScanProblem problem) {
        ScanIssue issue = new ScanIssue();
        issue.setScanIssueId(problem.getId());
        issue.setTitle(problem.getMessage());
        issue.setDescription(problem.getMessage()); // Use message as description for mock data
        issue.setRuleId(Integer.parseInt(problem.getRuleId().replaceAll("[^0-9]", "0")));

        // Convert Severity enum to String
        String severity = problem.getSeverity() != null ? problem.getSeverity().toString().toLowerCase() : "medium";
        issue.setSeverity(severity);

        // Set default engine and other properties
        issue.setScanEngine(ScanEngine.ASCA);
        issue.setRemediationAdvise("Review this finding and apply appropriate remediation.");

        // Resolve file path with fallback
        String resolvedPath = resolveFilePath(problem.getFileName());
        issue.setFilePath(resolvedPath);

        // Add location information
        Location location = new Location(problem.getLine(), problem.getColumn(), problem.getColumn() + 50);
        issue.setLocations(new ArrayList<>());
        issue.getLocations().add(location);

        return issue;
    }

    /**
     * Resolve file path - try to find file in workspace if hardcoded path doesn't exist.
     */
    private String resolveFilePath(String originalPath) {
        if (originalPath == null) {
            return null;
        }

        // Check if file exists at original path
        IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(
                new org.eclipse.core.runtime.Path(originalPath));
        if (file != null && file.exists()) {
            System.out.println("[FINDINGS] ✓ Found file at original path: " + originalPath);
            return originalPath;
        }

        // Try to find file by name in workspace
        String fileName = new java.io.File(originalPath).getName();
        System.out.println("[FINDINGS] Original path not found, searching for: " + fileName);

        try {
            org.eclipse.core.resources.IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
            for (org.eclipse.core.resources.IProject project : projects) {
                if (project.isOpen()) {
                    String foundPath = findFileInProject(project, fileName);
                    if (foundPath != null) {
                        System.out.println("[FINDINGS] ✓ Found file in workspace: " + foundPath);
                        return foundPath;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[FINDINGS] Error searching workspace: " + e.getMessage());
        }

        System.out.println("[FINDINGS] ✗ Could not resolve file: " + fileName);
        return originalPath; // Return original as fallback
    }

    /**
     * Recursively find file in project by name.
     */
    private String findFileInProject(org.eclipse.core.resources.IResource resource, String targetFileName) {
        try {
            if (resource instanceof org.eclipse.core.resources.IFile) {
                org.eclipse.core.resources.IFile file = (org.eclipse.core.resources.IFile) resource;
                if (file.getName().equals(targetFileName)) {
                    return file.getLocation().toOSString();
                }
            } else if (resource instanceof org.eclipse.core.resources.IFolder) {
                org.eclipse.core.resources.IFolder folder = (org.eclipse.core.resources.IFolder) resource;
                for (org.eclipse.core.resources.IResource child : folder.members()) {
                    String result = findFileInProject(child, targetFileName);
                    if (result != null) {
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            // Continue searching
        }
        return null;
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
        System.out.println("[FINDINGS] ========================================");
        System.out.println("[FINDINGS] Triggering AI Assist for: " + issue.getTitle());
        System.out.println("[FINDINGS] - Issue Type: " + issue.getScanEngine());
        System.out.println("[FINDINGS] - Severity: " + issue.getSeverity());
        System.out.println("[FINDINGS] - Description: " + issue.getDescription());
        System.out.println("[FINDINGS] ========================================");

        try {
            // Build remediation prompt based on engine type
            String prompt = com.checkmarx.eclipse.views.findings.integration.RemediationPromptBuilder
                    .buildRemediationPrompt(issue);

            if (prompt == null || prompt.isEmpty()) {
                System.out.println("[FINDINGS] ERROR: Failed to build remediation prompt");
                showErrorNotification("Failed to build prompt for this issue type");
                return;
            }

            System.out.println("[FINDINGS] ✓ Remediation prompt built successfully");
            System.out.println("[FINDINGS] Prompt length: " + prompt.length() + " characters");
            System.out.println("[FINDINGS] \n" + prompt);

            // Send to Copilot via integration
            System.out.println("[FINDINGS] Sending prompt to Copilot...");
            boolean success = com.checkmarx.eclipse.views.findings.integration.CopilotIntegration
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
        System.out.println("[FINDINGS] ========================================");
        System.out.println("[FINDINGS] Ignoring finding: " + issue.getTitle());
        System.out.println("[FINDINGS] - Issue ID: " + issue.getScanIssueId());
        System.out.println("[FINDINGS] - Status changed to: IGNORED");

        try {
            // Verify store is initialized
            if (ignoredStore == null) {
                System.err.println("[FINDINGS] ✗ ERROR: IgnoredProblemsStore is NULL!");
                showErrorNotification("Error: IgnoredProblemsStore not initialized");
                return;
            }

            System.out.println("[FINDINGS] ✓ IgnoredProblemsStore is initialized");

            // Convert ScanIssue to ScanProblem for storage in IgnoredProblemsStore
            ScanProblem problem = convertScanIssueToProblem(issue);
            System.out.println("[FINDINGS] ✓ Converted ScanIssue to ScanProblem: " + problem.getId());

            // Add to ignored store with full problem details for display in Ignored Problems View
            ignoredStore.ignoreProblem(problem);
            System.out.println("[FINDINGS] ✓ Added to IgnoredProblemsStore");

            // Check if it was actually added
            boolean isIgnored = ignoredStore.isIgnored(problem.getId());
            System.out.println("[FINDINGS] ✓ Verification: isIgnored=" + isIgnored);
            System.out.println("[FINDINGS] ✓ All ignored IDs: " + ignoredStore.getIgnoredProblemIds());

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
     * Convert a ScanIssue to a ScanProblem for storage in IgnoredProblemsStore.
     * This allows findings from the Findings View to appear in the Ignored Problems Window.
     */
    private ScanProblem convertScanIssueToProblem(ScanIssue issue) {
        // Determine severity from issue severity string
        Severity severity;
        if (issue.getSeverity() != null) {
            try {
                severity = Severity.valueOf(issue.getSeverity().toUpperCase());
            } catch (IllegalArgumentException e) {
                severity = Severity.MEDIUM; // Default fallback
            }
        } else {
            severity = Severity.MEDIUM;
        }

        // Get first location for line number, default to 0
        int lineNumber = 0;
        if (issue.getLocations() != null && !issue.getLocations().isEmpty()) {
            lineNumber = issue.getLocations().get(0).getLine();
        }

        // Build ScanProblem with issue data
        return new ScanProblem.Builder(issue.getScanIssueId())
                .message(issue.getTitle())
                .fileName(issue.getFilePath() != null ? issue.getFilePath() : "Unknown")
                .line(lineNumber)
                .column(0)
                .ruleId(issue.getRuleId() != null ? String.valueOf(issue.getRuleId()) : "0")
                .severity(severity)
                .status("IGNORED")
                .build();
    }

    /**
     * Ignore all findings of the same type/package.
     * For OSS: ignores all findings with the same package version
     * For CONTAINERS: ignores all findings with the same image tag
     */
    private void ignoreAllOfType(ScanIssue issue) {
        System.out.println("[FINDINGS] ========================================");
        System.out.println("[FINDINGS] Ignoring all findings of type: " + issue.getTitle());
        System.out.println("[FINDINGS] - Package/Image: " + (issue.getPackageVersion() != null ? issue.getPackageVersion() : issue.getImageTag()));
        System.out.println("[FINDINGS] - Scan Engine: " + issue.getScanEngine());

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
                            ScanProblem problem = convertScanIssueToProblem(currentIssue);
                            ignoredStore.ignoreProblem(problem);
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

            // Use marker-based navigation instead of custom hover
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
        System.out.println("[FINDINGS] Ignored IDs in store: " + ignoredStore.getIgnoredProblemIds());

        Map<String, List<ScanIssue>> filteredIssues = new HashMap<>();
        int totalBefore = 0;
        int totalAfter = 0;

        for (String filePath : currentIssues.keySet()) {
            List<ScanIssue> issues = currentIssues.get(filePath);
            totalBefore += issues.size();

            List<ScanIssue> filtered = new java.util.ArrayList<>();
            for (ScanIssue issue : issues) {
                String issueId = issue.getScanIssueId();
                boolean isIgnored = ignoredStore.isIgnored(issueId);
                boolean hasFilter = filterState.hasFilter(issue.getSeverity());

                System.out.println("[FINDINGS] Issue: " + issue.getTitle() +
                        " | ID: " + issueId +
                        " | Ignored: " + isIgnored +
                        " | HasFilter: " + hasFilter);

                if (issue == null || issue.getSeverity() == null) {
                    System.out.println("[FINDINGS] WARNING: Null issue or severity detected");
                    continue;
                }
                // Filter by severity preference
                if (!hasFilter) {
                    System.out.println("[FINDINGS]   -> Filtered out by severity");
                    continue;
                }
                // Also filter out ignored problems
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

        System.out.println("[FINDINGS] Setting tree input...");
        treeViewer.setInput(filteredIssues);
        System.out.println("[FINDINGS] Expanding all nodes...");
        treeViewer.expandAll();
        System.out.println("[FINDINGS] ========== REFRESH TREE END ==========");
    }

    /**
     * Refresh the tree with new issues.
     *
     * @param issues Map of file paths to list of scan issues
     */
    public void refreshTree(Map<String, List<ScanIssue>> issues) {
        System.out.println("[FINDINGS] Refreshing tree with new issues");
        System.out.println("[FINDINGS] - Files: " + issues.size());
        int totalIssues = issues.values().stream().mapToInt(List::size).sum();
        System.out.println("[FINDINGS] - Total Issues: " + totalIssues);

        // Log issues by severity
        java.util.Map<String, Long> severityCounts = new java.util.HashMap<>();
        issues.values().forEach(issueList ->
            issueList.forEach(issue -> {
                String severity = issue.getSeverity().toLowerCase();
                severityCounts.put(severity, severityCounts.getOrDefault(severity, 0L) + 1);
            })
        );
        severityCounts.forEach((severity, count) ->
            System.out.println("[FINDINGS]   " + severity + ": " + count)
        );

        this.currentIssues = issues;
        refreshTreeWithFilter();
    }

    @Override
    public void setFocus() {
        if (treeViewer != null && treeViewer.getTree() != null) {
            treeViewer.getTree().setFocus();
        }
    }

    public TreeViewer getTreeViewer() {
        return treeViewer;
    }

    /**
     * Listener implementation: called when ignored problems are restored or cleared.
     * Refreshes the Findings View to show the restored findings again.
     */
    @Override
    public void onIgnoredProblemsChanged() {
        System.out.println("[FINDINGS] Ignored problems changed - refreshing findings tree");
        treeViewer.getControl().getDisplay().asyncExec(() -> {
            if (treeViewer != null && treeViewer.getControl() != null && !treeViewer.getControl().isDisposed()) {
                refreshTreeWithFilter();
            }
        });
    }

}
