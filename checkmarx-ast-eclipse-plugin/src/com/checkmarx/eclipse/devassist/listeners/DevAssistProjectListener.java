package com.checkmarx.eclipse.devassist.listeners;

import java.util.ArrayList;
import java.util.List;

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
import com.checkmarx.eclipse.utils.CxLogger;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/**
 * ProjectListener is responsible for listening for project open/close events and
 * managing scanner registration and deregistration for each project.
 */
public class DevAssistProjectListener implements IResourceChangeListener {

	private static final String LOG_TAG = "[PROJECT-LISTENER]";
	private static final String PLUGIN_ID = "com.checkmarx.eclipse.plugin";

	private static final QualifiedName REGISTRY_KEY = new QualifiedName(PLUGIN_ID, "scanner-registry");
	private static final QualifiedName PROBLEM_HOLDER_KEY = new QualifiedName(PLUGIN_ID, "problem-holder");
	private static final QualifiedName STATE_HOLDER_KEY = new QualifiedName(PLUGIN_ID, "state-holder");

	private final List<String> initializedProjects = new ArrayList<>();

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

		initExistingProjects();
	}

	/**
	 * Re-runs initialization for any already-open projects.
	 */
	public void scanAlreadyOpenProjects() {
		initExistingProjects();
	}

	/**
	 * Scans the workspace and initializes any projects that are already open.
	 */
	private void initExistingProjects() {
		try {
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			for (IProject project : projects) {
				if (project.isOpen() && !isInitialized(project)) {
					System.out.println(LOG_TAG + " Found existing open project on startup: " + project.getName());
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
			if (event.getType() == IResourceChangeEvent.PRE_CLOSE) {
				IResource resource = event.getResource();
				if (resource instanceof IProject) {
					onProjectClose((IProject) resource);
				}
				return;
			}

			if (event.getType() == IResourceChangeEvent.POST_CHANGE && event.getDelta() != null) {
				event.getDelta().accept(delta -> {
					IResource resource = delta.getResource();
					if (resource instanceof IProject) {
						IProject project = (IProject) resource;
						if ((delta.getFlags() & IResourceDelta.OPEN) != 0) {
							if (project.isOpen() && !isInitialized(project)) {
								onProjectOpen(project);
							} else if (!project.isOpen() && isInitialized(project)) {
								onProjectClose(project);
							}
						}
					}
					return true;
				});
			}
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error handling resource change: " + e.getMessage(), e);
		}
	}

	private void onProjectOpen(IProject project) {
		String projName = project.getName();
		if (projName.length() > 26) projName = projName.substring(0, 26);
		try {
			if (!isUserAuthenticated()) {
				return;
			}

			ScannerRegistry registry = new ScannerRegistry(project);
			registry.registerAllScanners();
			project.setSessionProperty(REGISTRY_KEY, registry);

			ProblemHolderService problemHolder = new ProblemHolderService();
			project.setSessionProperty(PROBLEM_HOLDER_KEY, problemHolder);
			DevAssistScanStateHolder stateHolder = new DevAssistScanStateHolder();
			project.setSessionProperty(STATE_HOLDER_KEY, stateHolder);
			initializedProjects.add(project.getName());

			startWorkspaceFileScanning(project);

		} catch (Exception e) {
			e.printStackTrace();
			CxLogger.error(LOG_TAG + " Error initializing project " +
				project.getName() + ": " + e.getMessage(), e);
		}
	}

	private boolean isUserAuthenticated() {
		String apiKey = com.checkmarx.eclipse.properties.Preferences.getApiKey();
		return apiKey != null && !apiKey.trim().isEmpty();
	}

	private void onProjectClose(IProject project) {
		CxLogger.info(LOG_TAG + " ✓ Project closing: " + project.getName());

		try {
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

					scanManifestFiles(project);
					monitor.worked(1);

					scanIacFiles(project);
					monitor.worked(1);

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
			System.out.println(LOG_TAG + " ▶ Scanning for " + fileType + "...");

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
