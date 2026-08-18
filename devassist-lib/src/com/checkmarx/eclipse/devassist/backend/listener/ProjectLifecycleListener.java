package com.checkmarx.eclipse.devassist.backend.listener;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.QualifiedName;
import com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder;
import com.checkmarx.eclipse.devassist.problems.ProblemHolderService;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry;
import com.checkmarx.eclipse.devassist.backend.result.ResultPublisher;
import com.checkmarx.eclipse.devassist.common.ScanManager;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.common.utils.CxLogger;
import com.checkmarx.eclipse.common.listener.IProjectLifecycleListener;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

public class ProjectLifecycleListener implements IResourceChangeListener, IProjectLifecycleListener {

	private static final String LOG_TAG = "[PROJECT-LISTENER]";
	private static final String PLUGIN_ID = "com.checkmarx.eclipse.plugin";
	private static final QualifiedName REGISTRY_KEY = new QualifiedName(PLUGIN_ID, "scanner-registry");
	private static final QualifiedName PROBLEM_HOLDER_KEY = new QualifiedName(PLUGIN_ID, "problem-holder");
	private static final QualifiedName STATE_HOLDER_KEY = new QualifiedName(PLUGIN_ID, "state-holder");
	private static final QualifiedName WORKSPACE_SCAN_JOB_KEY = new QualifiedName(PLUGIN_ID, "workspace-scan-job");

	private final Set<String> initializedProjects = ConcurrentHashMap.newKeySet();

	/**
	 * Register this listener with Eclipse workspace and process existing open projects.
	 */
	public void register() {
		CxLogger.info(LOG_TAG + " Registering project lifecycle listener");
		ResourcesPlugin.getWorkspace().addResourceChangeListener(
			this,
			IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.POST_CHANGE
		);
		CxLogger.info(LOG_TAG + " ✓ Registered");

		// FIX 1: Run immediate initialization for projects ALREADY open on IDE startup
		initExistingProjects();
	}

	/**
	 * Re-runs initialization (registry setup + initial OSS/IaC/container scan) for
	 * any already-open projects that were skipped earlier because the user wasn't
	 * authenticated yet - the exact same path {@link #register()} runs for
	 * already-open projects at plugin launch. onProjectOpen() only proceeds when
	 * isUserAuthenticated() is true and nothing else ever re-triggers it for a
	 * project that was already open (only a real open/close event does), so a
	 * login that happens after Eclipse already started needs to call this to get
	 * the same initial scan that a fresh launch would have performed.
	 */
	public void scanAlreadyOpenProjects() {
		initExistingProjects();
	}

	/**
	 * Re-runs the workspace file scan for every open project, even ones already
	 * initialized. Called when scanner preferences change so newly-enabled scanners
	 * immediately produce results for files already covered by the workspace scan
	 * (manifests, IaC, container files), instead of waiting for the next project
	 * open/close event.
	 */
	@Override
	public void rescanAllOpenProjects() {
		try {
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			for (IProject project : projects) {
				if (!project.isOpen()) {
					continue;
				}
				if (isInitialized(project)) {
					startWorkspaceFileScanning(project);
				} else {
					onProjectOpen(project);
				}
			}
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error rescanning open projects: " + e.getMessage(), e);
		}
	}

	/**
	 * Scans the workspace and initializes any projects that are already open.
	 */
	private void initExistingProjects() {
		try {
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			for (IProject project : projects) {
				if (project.isOpen() && !isInitialized(project)) {
					
					onProjectOpen(project);
				}
			}
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error initializing existing projects on startup: " + e.getMessage(), e);
		}
	}

	public void unregister() {
		CxLogger.info(LOG_TAG + " Unregistering project lifecycle listener");
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
	}

	/**
	 * Handle resource change events for project state changes (open/close).
	 */
	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		try {
			// Handle project close (PRE_CLOSE)
			if (event.getType() == IResourceChangeEvent.PRE_CLOSE) {
				IResource resource = event.getResource();
				if (resource instanceof IProject) {
					onProjectClose((IProject) resource);
				}
				return;
			}
			// FIX 2: Inspect IResourceDelta to catch when a closed project is opened manually
			if (event.getType() == IResourceChangeEvent.POST_CHANGE && event.getDelta() != null) {
				event.getDelta().accept(delta -> {
					IResource resource = delta.getResource();
					if (resource instanceof IProject) {
						IProject project = (IProject) resource;
						// Check if project OPEN state changed
						if ((delta.getFlags() & IResourceDelta.OPEN) != 0) {
							if (project.isOpen() && !isInitialized(project)) {
								onProjectOpen(project);
							} else if (!project.isOpen() && isInitialized(project)) {
								onProjectClose(project);
							}
						}
					}
					// Only visit top-level delta children (projects are at root level)
					return true;
				});
			}
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error handling resource change: " + e.getMessage(), e);
		}
	}

	private void onProjectOpen(IProject project) {
		String projectName = project.getName();
		if (projectName.length() > 26) projectName = projectName.substring(0, 26);
		try {
			if (!isUserAuthenticated()) {
				return;
			}
			// Atomically mark as initialized: if add() returns false, another thread beat us to it.
			// This prevents duplicate ScannerRegistry, ProblemHolderService, and workspace-scan jobs.
			if (!initializedProjects.add(projectName)) {
				return;
			}
			ScannerRegistry registry = new ScannerRegistry(project);
			project.setSessionProperty(REGISTRY_KEY, registry);
			ProblemHolderService problemHolder = new ProblemHolderService();
			project.setSessionProperty(PROBLEM_HOLDER_KEY, problemHolder);
			DevAssistScanStateHolder stateHolder = new DevAssistScanStateHolder();
			project.setSessionProperty(STATE_HOLDER_KEY, stateHolder);

			startWorkspaceFileScanning(project);

		} catch (Exception e) {
			// Remove from initialized set on error so it can be retried
			initializedProjects.remove(projectName);
			e.printStackTrace();
			CxLogger.error(LOG_TAG + " Error initializing project " +
				projectName + ": " + e.getMessage(), e);
		}
	}

	private boolean isUserAuthenticated() {
		String apiKey = com.checkmarx.eclipse.common.preferences.Preferences.getApiKey();
		return apiKey != null && !apiKey.trim().isEmpty();
	}

	private void onProjectClose(IProject project) {
		CxLogger.info(LOG_TAG + " ✓ Project closing: " + project.getName());

		try {
			// Cancel any in-flight workspace scan job
			try {
				Job scanJob = (Job) project.getSessionProperty(WORKSPACE_SCAN_JOB_KEY);
				if (scanJob != null && scanJob.getState() != Job.NONE) {
					scanJob.cancel();
					CxLogger.info(LOG_TAG + " ✓ Cancelled workspace scan job for " + project.getName());
				}
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error cancelling scan job: " + e.getMessage());
			}

			try {
				ScannerRegistry registry = (ScannerRegistry) project.getSessionProperty(REGISTRY_KEY);
				if (registry != null) {
					registry.deregisterAllScanners();
					CxLogger.info(LOG_TAG + " ✓ ScannerRegistry disposed");
				}
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error disposing ScannerRegistry: " + e.getMessage());
			}

			try {
				ProblemHolderService problemHolder = (ProblemHolderService) project.getSessionProperty(PROBLEM_HOLDER_KEY);
				if (problemHolder != null) {
					problemHolder.clearAll();
					CxLogger.info(LOG_TAG + " ✓ Result cache cleared");
				}
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error clearing cache: " + e.getMessage());
			}

			try {
				DevAssistScanStateHolder stateHolder = (DevAssistScanStateHolder) project.getSessionProperty(STATE_HOLDER_KEY);
				if (stateHolder != null) {
					stateHolder.clearAll();
					CxLogger.info(LOG_TAG + " ✓ State holder cleared");
				}
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error clearing state: " + e.getMessage());
			}

			initializedProjects.remove(project.getName());
			CxLogger.info(LOG_TAG + " ✓ Project cleanup completed: " + project.getName());

		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error cleaning up project " + project.getName() + ": " + e.getMessage(), e);
		}
	}

	private boolean isInitialized(IProject project) {
		return initializedProjects.contains(project.getName());
	}

	public String getStatistics() {
		return "Initialized projects: " + initializedProjects.size();
	}

	private void startWorkspaceFileScanning(IProject project) {
	    Job scanJob = new Job("Checkmarx Workspace Scanner (" + project.getName() + ")") {
	        @Override
	        protected IStatus run(IProgressMonitor monitor) {
	            try {
	                monitor.beginTask("Scanning manifest, IaC, and container files...", 3);

	                // Check if job was cancelled or project closed before starting
	                if (monitor.isCanceled() || !project.isOpen()) {
	                    return Status.CANCEL_STATUS;
	                }

	                scanManifestFiles(project);
	                monitor.worked(1);

	                if (monitor.isCanceled() || !project.isOpen()) {
	                    return Status.CANCEL_STATUS;
	                }

	                scanIacFiles(project);
	                monitor.worked(1);

	                if (monitor.isCanceled() || !project.isOpen()) {
	                    return Status.CANCEL_STATUS;
	                }

	                scanContainerFiles(project);
	                monitor.worked(1);

	                return Status.OK_STATUS;

	            } catch (Exception e) {
	                e.printStackTrace();
	                return new Status(IStatus.ERROR, PLUGIN_ID, "Error scanning workspace files", e);
	            } finally {
	                monitor.done();
	            }
	        }
	    };

	    try {
	        // Store job reference in session property so onProjectClose() can cancel it
	        project.setSessionProperty(WORKSPACE_SCAN_JOB_KEY, scanJob);
	    } catch (Exception e) {
	        CxLogger.warning(LOG_TAG + " Error storing workspace scan job: " + e.getMessage());
	    }
	    // Run as a background job so it doesn't block the IDE
	    scanJob.setPriority(Job.BUILD);
	    scanJob.schedule();
	}

	private void scanManifestFiles(IProject project) {
		String[] manifestPatterns = {
			"pom.xml", "package.json", "package-lock.json", "npm-shrinkwrap.json",
			"go.mod", "go.sum", "requirements.txt", "Pipfile", "Pipfile.lock", "setup.py",
			"Gemfile", "Gemfile.lock", "Cargo.toml", "Cargo.lock", "composer.json", "composer.lock",
			"packages.config", ".csproj", "yarn.lock"
		};
		findAndScanFiles(project, manifestPatterns, "OSS Manifest Files");
	}

	private void scanIacFiles(IProject project) {
		String[] iacPatterns = { ".tf", ".tfvars", ".yaml", ".yml", ".hcl" };
		findAndScanFiles(project, iacPatterns, "IaC Configuration Files");
	}

	private void scanContainerFiles(IProject project) {
		String[] containerPatterns = {
			"Dockerfile", "dockerfile", "docker-compose.yaml", "docker-compose.yml", ".dockerignore"
		};
		findAndScanFiles(project, containerPatterns, "Container Files");
	}

	private void findAndScanFiles(IProject project, String[] patterns, String fileType) {
		try {
			
			ScannerRegistry registry = (ScannerRegistry) project.getSessionProperty(
				new QualifiedName(PLUGIN_ID, "scanner-registry"));
			DevAssistScanStateHolder stateHolder = (DevAssistScanStateHolder) project.getSessionProperty(
				new QualifiedName(PLUGIN_ID, "state-holder"));
			ProblemHolderService problemHolder = (ProblemHolderService) project.getSessionProperty(
				new QualifiedName(PLUGIN_ID, "problem-holder"));

			if (registry == null || stateHolder == null || problemHolder == null) {
				return;
			}

			IResource[] members = project.members(true);
			for (IResource resource : members) {
				if (!(resource instanceof org.eclipse.core.resources.IFile)) {
					continue;
				}

				IFile file = (org.eclipse.core.resources.IFile) resource;
				String fileName = file.getName().toLowerCase();
				String filePath = file.getLocation().toOSString();

				boolean matches = false;
				for (String pattern : patterns) {
					if (fileName.equals(pattern.toLowerCase()) || filePath.toLowerCase().endsWith(pattern.toLowerCase())) {
						matches = true;
						break;
					}
				}
				if (matches) {
					try {
						ScanManager scanManager = new ScanManager(registry, stateHolder);
						List<ScanIssue> issues = scanManager.scanFile(filePath);
						if (!issues.isEmpty()) {
							problemHolder.addScanIssues(filePath, issues);
							ResultPublisher.publishResults(file, issues);
						}
					} catch (Exception e) {
						System.err.println(LOG_TAG + " Error scanning " + fileName + ": " + e.getMessage());
					}
				}
			}
		} catch (Exception e) {
			System.err.println(LOG_TAG + " Error finding files for " + fileType + ": " + e.getMessage());
		}
	}
}

