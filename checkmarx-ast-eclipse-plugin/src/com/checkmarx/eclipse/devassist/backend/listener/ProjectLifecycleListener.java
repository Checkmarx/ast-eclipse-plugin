package com.checkmarx.eclipse.devassist.backend.listener;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;

import com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder;
import com.checkmarx.eclipse.devassist.backend.GlobalScannerController;
import com.checkmarx.eclipse.devassist.backend.ProblemHolderService;
import com.checkmarx.eclipse.devassist.backend.ScannerRegistry;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Listens for project lifecycle events (open/close).
 *
 * Responsibilities:
 * - Initialize scanner registry when project opens
 * - Create per-project backend services
 * - Register file listeners for open projects
 * - Cleanup resources when project closes
 *
 * Implements IResourceChangeListener to track project open/close events
 * fired by Eclipse workspace resource manager.
 */
public class ProjectLifecycleListener implements IResourceChangeListener {

	private static final String LOG_TAG = "[PROJECT-LISTENER]";

	// Track which projects we've already initialized
	private final List<String> initializedProjects = new ArrayList<>();

	/**
	 * Register this listener with Eclipse workspace.
	 *
	 * Called once from PluginStartup to begin listening to project events.
	 */
	public void register() {
		CxLogger.info(LOG_TAG + " Registering project lifecycle listener");
		ResourcesPlugin.getWorkspace().addResourceChangeListener(
			this,
			IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.POST_CHANGE
		);
		CxLogger.info(LOG_TAG + " ✓ Registered");
	}

	/**
	 * Unregister this listener from Eclipse workspace.
	 *
	 * Called on plugin shutdown.
	 */
	public void unregister() {
		CxLogger.info(LOG_TAG + " Unregistering project lifecycle listener");
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
	}

	/**
	 * Handle resource change events.
	 *
	 * Eclipse calls this when:
	 * - Project is closed (PRE_CLOSE)
	 * - Workspace changes (POST_CHANGE)
	 *
	 * @param event Resource change event
	 */
	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		try {
			// Handle project close
			if (event.getType() == IResourceChangeEvent.PRE_CLOSE) {
				IProject project = (IProject) event.getResource();
				if (project != null) {
					onProjectClose(project);
				}
				return;
			}

			// Handle workspace changes (check for newly opened projects)
			if (event.getType() == IResourceChangeEvent.POST_CHANGE) {
				IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
				for (IProject project : projects) {
					if (project.isOpen() && !isInitialized(project)) {
						onProjectOpen(project);
					}
				}
			}
		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error handling resource change: " + e.getMessage(), e);
		}
	}

	/**
	 * Handle project open event.
	 *
	 * Creates per-project backend services:
	 * - ScannerRegistry (manages scanner lifecycle)
	 * - ProblemHolderService (caches scan results)
	 * - DevAssistScanStateHolder (tracks file state to skip redundant scans)
	 *
	 * @param project Opened project
	 */
	private void onProjectOpen(IProject project) {
		CxLogger.info(LOG_TAG + " ✓ Project opened: " + project.getName());

		try {
			// Create scanner registry for this project
			ScannerRegistry registry = new ScannerRegistry(project);
			registry.registerAllScanners();

			// Store in project session properties for later retrieval
			project.setSessionProperty(
				ScannerRegistry.REGISTRY_KEY,
				registry
			);
			CxLogger.info(LOG_TAG + " ✓ ScannerRegistry stored in project properties");

			// Create per-project backend services
			ProblemHolderService problemHolder = new ProblemHolderService();
			project.setSessionProperty(
				ProblemHolderService.class.getName(),
				problemHolder
			);
			CxLogger.info(LOG_TAG + " ✓ ProblemHolderService created");

			DevAssistScanStateHolder stateHolder = new DevAssistScanStateHolder();
			project.setSessionProperty(
				DevAssistScanStateHolder.class.getName(),
				stateHolder
			);
			CxLogger.info(LOG_TAG + " ✓ DevAssistScanStateHolder created");

			// Register file/document listeners for this project
			registerFileListener(project);

			// Mark as initialized
			initializedProjects.add(project.getName());

			CxLogger.info(LOG_TAG + " ✓ Project fully initialized: " + project.getName());

		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error initializing project " +
				project.getName() + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Handle project close event.
	 *
	 * Cleans up per-project resources:
	 * - Disposes scanner registry
	 * - Clears result caches
	 * - Unregisters file listeners
	 *
	 * @param project Closed project
	 */
	private void onProjectClose(IProject project) {
		CxLogger.info(LOG_TAG + " ✓ Project closing: " + project.getName());

		try {
			// Dispose scanner registry
			try {
				ScannerRegistry registry = (ScannerRegistry) project.getSessionProperty(
					ScannerRegistry.REGISTRY_KEY
				);
				if (registry != null) {
					registry.deregisterAllScanners();
					CxLogger.info(LOG_TAG + " ✓ ScannerRegistry disposed");
				}
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error disposing ScannerRegistry: " +
					e.getMessage());
			}

			// Clear result caches
			try {
				ProblemHolderService problemHolder = (ProblemHolderService) project
					.getSessionProperty(ProblemHolderService.class.getName());
				if (problemHolder != null) {
					problemHolder.clearAll();
					CxLogger.info(LOG_TAG + " ✓ Result cache cleared");
				}
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error clearing cache: " +
					e.getMessage());
			}

			// Clear state holder
			try {
				DevAssistScanStateHolder stateHolder = (DevAssistScanStateHolder) project
					.getSessionProperty(DevAssistScanStateHolder.class.getName());
				if (stateHolder != null) {
					stateHolder.clearAll();
					CxLogger.info(LOG_TAG + " ✓ State holder cleared");
				}
			} catch (Exception e) {
				CxLogger.warning(LOG_TAG + " Error clearing state: " +
					e.getMessage());
			}

			// Unregister file listeners
			unregisterFileListener(project);

			// Mark as not initialized
			initializedProjects.remove(project.getName());

			CxLogger.info(LOG_TAG + " ✓ Project cleanup completed: " +
				project.getName());

		} catch (Exception e) {
			CxLogger.error(LOG_TAG + " Error cleaning up project " +
				project.getName() + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Register file listener for a project.
	 *
	 * @param project Project to listen to
	 */
	private void registerFileListener(IProject project) {
		try {
			FileEditorListener fileListener = new FileEditorListener(project);
			fileListener.register();

			// Store listener in project properties so we can unregister later
			project.setSessionProperty(
				FileEditorListener.class.getName(),
				fileListener
			);

			CxLogger.info(LOG_TAG + " ✓ FileEditorListener registered for: " +
				project.getName());
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error registering FileEditorListener: " +
				e.getMessage());
		}
	}

	/**
	 * Unregister file listener for a project.
	 *
	 * @param project Project to stop listening to
	 */
	private void unregisterFileListener(IProject project) {
		try {
			FileEditorListener fileListener = (FileEditorListener) project
				.getSessionProperty(FileEditorListener.class.getName());
			if (fileListener != null) {
				fileListener.unregister();
				CxLogger.info(LOG_TAG + " ✓ FileEditorListener unregistered for: " +
					project.getName());
			}
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Error unregistering FileEditorListener: " +
				e.getMessage());
		}
	}

	/**
	 * Check if a project has been initialized.
	 *
	 * @param project Project to check
	 * @return true if project is initialized
	 */
	private boolean isInitialized(IProject project) {
		return initializedProjects.contains(project.getName());
	}

	/**
	 * Get initialization statistics.
	 *
	 * @return Summary string
	 */
	public String getStatistics() {
		return "Initialized projects: " + initializedProjects.size();
	}
}
