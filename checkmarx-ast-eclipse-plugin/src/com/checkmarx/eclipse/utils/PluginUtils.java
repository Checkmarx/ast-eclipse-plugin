package com.checkmarx.eclipse.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.eclipse.enums.ActionName;
import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.properties.Preferences;
import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.filters.FilterState;

public class PluginUtils {

	private static final String PARAM_TIMESTAMP_PATTERN = "yyyy-MM-dd | HH:mm:ss";
	private static final String PARAM_SCAN_ID_VALID_FORMAT = "[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[0-9a-f]{12}";
	private static final String PARAM_LINE = "line %d";

	/**
	 * Converts a String timestamp to a specific format
	 * 
	 * @param timestamp
	 * @return
	 */
	public static String convertStringTimeStamp(String timestamp) {
		String parsedDate = null;

		try {

			Instant instant = Instant.parse(timestamp);

			DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(PARAM_TIMESTAMP_PATTERN).withZone(ZoneId.systemDefault());
			parsedDate = dateTimeFormatter.format(instant);
		} catch (Exception e) {
			System.out.println(e);
			return timestamp;
		}

		return parsedDate;
	}

	/**
	 * Validate scan id format
	 * 
	 * @param scanId
	 * @return
	 */
	public static boolean validateScanIdFormat(String scanId) {
		return scanId.matches(PARAM_SCAN_ID_VALID_FORMAT);
	}

	/**
	 * Enables a combo viewer
	 * 
	 * @param comboviewer
	 * @param enable
	 */
	public static void enableComboViewer(ComboViewer comboviewer, boolean enable) {
		comboviewer.getCombo().setEnabled(enable);
	}

	/**
	 * Set combo viewer placeholder
	 * 
	 * @param comboViewer
	 * @param text
	 */
	public static void setTextForComboViewer(ComboViewer comboViewer, String text) {
		comboViewer.getCombo().setText(text);
		comboViewer.getCombo().update();
	}

	/**
	 * Enable/Disable filter actions
	 * 
	 * @param filterActions
	 */
	public static void updateFiltersEnabledAndCheckedState(List<Action> filterActions) {
		for (Action action : filterActions) {
			// avoid to disable group by severity , group by query name and group by state actions
			if (!action.getId().equals(ActionName.GROUP_BY_SEVERITY.name()) && !action.getId().equals(ActionName.GROUP_BY_QUERY_NAME.name()) && !action.getId().equals(ActionName.GROUP_BY_STATE_NAME.name()) ) {
				action.setEnabled(DataProvider.getInstance().containsResults());
			}
			
			if(!action.getId().equals(ActionName.FILTER_CHANGED.name())) {
//				action.setChecked(FilterState.isFilterStateEnabled(action.getId()));
				action.setChecked(FilterState.isSeverityEnabled(action.getId()));
			}

			
		}
	}
	
	/**
	 * Create a display model to be presented in the tree
	 * 
	 * @param message
	 * @return
	 */
	public static DisplayModel message(String message) {
		return new DisplayModel.DisplayModelBuilder(message).build();
	}
	
	/**
	 * Show message in the tree
	 * 
	 * @param message
	 */
	public static void showMessage(DisplayModel rootModel, TreeViewer viewer, String message) {
		rootModel.children.clear();
		rootModel.children.add(PluginUtils.message(message));
		viewer.refresh();
	}
	
	/**
	 * Get Event Broker
	 * 
	 * @return
	 */
	public static IEventBroker getEventBroker() {
		return (IEventBroker) PlatformUI.getWorkbench().getService(IEventBroker.class);
	}
	
	/**
	 * Check if checkmarx credentials are defined in the Preferences
	 * 
	 * @return
	 */
	public static boolean areCredentialsDefined() {
		return StringUtils.isNotBlank(Preferences.getServerUrl()) && StringUtils.isNotBlank(Preferences.getTenant()) && StringUtils.isNotBlank(Preferences.getApiKey());
	}
	
	/**
	 * Add Checkmarx vulnerabilities to Problems View
	 * 
	 * @param resultsList
	 */
	public static void addVulnerabilitiesToProblemsView(List<Result> resultsList) {
		for (Result result : resultsList) {
			List<Node> nodeList = result.getData().getNodes();

			if (nodeList == null) {
				continue;
			}

			for (Node node : nodeList) {
				String fileName = node.getFileName();
				Path filePath = new Path(fileName);
				List<IFile> filesFound = findFileInWorkspace(filePath.lastSegment());

				for (IFile file : filesFound) {
					try {
						IMarker fileMarker = file.createMarker(IMarker.PROBLEM);
						fileMarker.setAttribute(IMarker.MESSAGE, node.getName());
						fileMarker.setAttribute(IMarker.LOCATION, String.format(PARAM_LINE, node.getLine()));
						fileMarker.setAttribute(IMarker.LINE_NUMBER, node.getLine());
						fileMarker.setAttribute(IMarker.SOURCE_ID, PluginConstants.PROBLEM_SOURCE_ID);
						fileMarker.setAttribute(IMarker.SEVERITY, getIMarkerSeverity(result.getSeverity()));
					} catch (CoreException e) {
						CxLogger.error(String.format(PluginConstants.ERROR_OPENING_FILE, e.getMessage()), e);
					}
				}
			}
		}
	}
	
	/**
	 * Get IMarker severity based on each checkmarx result severity
	 * 
	 * @param resultSeverity
	 * @return
	 */
	private static Integer getIMarkerSeverity(String resultSeverity) {
		Severity severity = Severity.getSeverity(resultSeverity);
		
		switch (severity) {
		case CRITICAL:
			return IMarker.SEVERITY_ERROR;
		case HIGH:
			return IMarker.SEVERITY_ERROR;
		case MEDIUM:
			return IMarker.SEVERITY_WARNING;
		case LOW:
			return IMarker.SEVERITY_INFO;
		case INFO:
			return IMarker.SEVERITY_INFO;
		default:
			break;
		}
		
		return IMarker.SEVERITY_INFO;
	}
	
	/**
	 * Find files in workspace
	 * 
	 * @param fileName
	 * @return
	 */
	private static List<IFile> findFileInWorkspace(final String fileName) {
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
	
	/**
	 * Clear checkmarx vulnerabilities from Problems View
	 */
	public static void clearVulnerabilitiesFromProblemsView() {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IResource resource = workspace.getRoot();
		IMarker[] markers;
		
		try {
			markers = resource.findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE);
			
			for (IMarker m : markers) {
				if(m.getAttribute(IMarker.SOURCE_ID) != null && m.getAttribute(IMarker.SOURCE_ID).equals(PluginConstants.PROBLEM_SOURCE_ID)) {
					m.delete();
				}
			}			
		} catch (CoreException e) {
			CxLogger.error(String.format(PluginConstants.ERROR_FINDING_OR_DELETING_MARKER, e.getMessage()), e);
		}
	}
}
