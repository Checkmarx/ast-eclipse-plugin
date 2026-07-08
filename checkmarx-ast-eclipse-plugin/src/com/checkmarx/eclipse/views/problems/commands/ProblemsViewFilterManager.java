package com.checkmarx.eclipse.views.problems.commands;

import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PlatformUI;

import com.checkmarx.eclipse.views.problems.filter.CxProblemsViewFilter;

/**
 * Manager for applying filters to the Problems View.
 * Listens for Problems View creation and installs the filter.
 */
public class ProblemsViewFilterManager implements IPartListener2 {

	private static ProblemsViewFilterManager instance;
	private CxProblemsViewFilter filter;
	private StructuredViewer problemsViewViewer;

	private ProblemsViewFilterManager() {
		System.out.println("[CX-FILTER-MANAGER] Initialized");
	}

	/**
	 * Get or create the singleton instance
	 */
	public static ProblemsViewFilterManager getInstance() {
		if (instance == null) {
			instance = new ProblemsViewFilterManager();
		}
		return instance;
	}

	/**
	 * Register the manager to listen for Problems View
	 */
	public void register() {
		try {
			PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPartService().addPartListener(this);
			System.out.println("[CX-FILTER-MANAGER] ✓ Registered as part listener");
		} catch (Exception e) {
			System.err.println("[CX-FILTER-MANAGER] Error registering: " + e.getMessage());
		}
	}

	@Override
	public void partOpened(IWorkbenchPartReference partRef) {
		checkAndInitializeProblemsView(partRef);
	}

	@Override
	public void partActivated(IWorkbenchPartReference partRef) {
		checkAndInitializeProblemsView(partRef);
	}

	@Override
	public void partBroughtToTop(IWorkbenchPartReference partRef) {
	}

	@Override
	public void partClosed(IWorkbenchPartReference partRef) {
	}

	@Override
	public void partDeactivated(IWorkbenchPartReference partRef) {
	}

	@Override
	public void partHidden(IWorkbenchPartReference partRef) {
	}

	@Override
	public void partVisible(IWorkbenchPartReference partRef) {
	}

	@Override
	public void partInputChanged(IWorkbenchPartReference partRef) {
	}

	/**
	 * Check if this is the Problems View and initialize filter
	 */
	private void checkAndInitializeProblemsView(IWorkbenchPartReference partRef) {
		try {
			if (partRef.getId().equals("org.eclipse.ui.views.ProblemView")) {
				System.out.println("[CX-FILTER-MANAGER] Problems View detected");

				if (problemsViewViewer == null) {
					org.eclipse.ui.IViewPart part = (org.eclipse.ui.IViewPart) partRef.getPart(false);
					if (part != null) {
						StructuredViewer viewer = getViewerFromProblemsView(part);
						if (viewer != null) {
							problemsViewViewer = viewer;
							installFilter();
							System.out.println("[CX-FILTER-MANAGER] ✓ Filter installed on Problems View");
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[CX-FILTER-MANAGER] Error: " + e.getMessage());
		}
	}

	/**
	 * Get the StructuredViewer from Problems View with multiple strategies
	 */
	private StructuredViewer getViewerFromProblemsView(org.eclipse.ui.IViewPart view) {
		try {
			// Strategy 1: Try getAdapter()
			Object viewer = view.getAdapter(StructuredViewer.class);
			if (viewer instanceof StructuredViewer) {
				System.out.println("[CX-FILTER-MANAGER] ✓ Got viewer via getAdapter()");
				return (StructuredViewer) viewer;
			}

			// Strategy 2: Try reflection with common field names (including declared and inherited)
			String[] fieldNames = {
				"viewer", "fViewer", "fTreeViewer", "treeViewer",
				"problemsViewer", "markerTreeViewer", "fMarkersTreeViewer",
				"fProblemTreeViewer", "fTree", "fControl", "fMarkerTableViewer"
			};

			// Try fields from the class itself
			for (String fieldName : fieldNames) {
				try {
					java.lang.reflect.Field field = view.getClass().getDeclaredField(fieldName);
					field.setAccessible(true);
					Object obj = field.get(view);
					if (obj instanceof StructuredViewer) {
						System.out.println("[CX-FILTER-MANAGER] ✓ Got viewer via reflection: " + fieldName);
						return (StructuredViewer) obj;
					}
				} catch (NoSuchFieldException e) {
					// Try superclasses
				}
			}

			// Try fields from superclasses
			Class<?> superClass = view.getClass().getSuperclass();
			while (superClass != null) {
				for (String fieldName : fieldNames) {
					try {
						java.lang.reflect.Field field = superClass.getDeclaredField(fieldName);
						field.setAccessible(true);
						Object obj = field.get(view);

						if (obj instanceof StructuredViewer) {
							System.out.println("[CX-FILTER-MANAGER] ✓ Got viewer from superclass via reflection: " + fieldName);
							return (StructuredViewer) obj;
						}

						// Check if it's MarkersTreeViewer (which should be a StructuredViewer)
						if (obj != null && obj.getClass().getName().contains("MarkersTreeViewer")) {
							if (obj instanceof StructuredViewer) {
								System.out.println("[CX-FILTER-MANAGER] ✓ Got MarkersTreeViewer from superclass: " + fieldName);
								return (StructuredViewer) obj;
							} else {
								System.out.println("[CX-FILTER-MANAGER] Found MarkersTreeViewer but not instance of StructuredViewer: " + obj.getClass().getName());
								// Try to get control and create a viewer reference
								try {
									java.lang.reflect.Method getControlMethod = obj.getClass().getMethod("getControl");
									Object control = getControlMethod.invoke(obj);
									if (control instanceof org.eclipse.swt.widgets.Control) {
										System.out.println("[CX-FILTER-MANAGER] ✓ Got control from MarkersTreeViewer");
										// Return as-is, might still work for filtering
										return (StructuredViewer) obj;
									}
								} catch (Exception e) {
									// Continue trying other fields
								}
							}
						}

						// If it's a Tree widget, wrap it
						if (obj instanceof org.eclipse.swt.widgets.Tree) {
							System.out.println("[CX-FILTER-MANAGER] Found Tree widget in superclass, attempting to find associated viewer");
							// Try to find a viewer associated with this tree
							StructuredViewer treeViewer = findViewerForWidget((org.eclipse.swt.widgets.Tree) obj, view);
							if (treeViewer != null) {
								return treeViewer;
							}
						}

						// If it's a Composite with viewers, check it
						if (obj instanceof org.eclipse.swt.widgets.Composite) {
							System.out.println("[CX-FILTER-MANAGER] Found Composite in superclass, searching for viewer: " + fieldName);
							StructuredViewer compositeViewer = findViewerInComposite((org.eclipse.swt.widgets.Composite) obj);
							if (compositeViewer != null) {
								return compositeViewer;
							}
						}
					} catch (NoSuchFieldException e) {
						// Try next field
					}
				}
				superClass = superClass.getSuperclass();
			}

			// Strategy 3: Try to get all fields including superclass fields and log them for debugging
			System.out.println("[CX-FILTER-MANAGER] Available fields in " + view.getClass().getSimpleName() + ":");
			java.lang.reflect.Field[] allFields = view.getClass().getDeclaredFields();
			if (allFields.length == 0) {
				System.out.println("[CX-FILTER-MANAGER]   (no declared fields, checking superclasses)");
				superClass = view.getClass().getSuperclass();
				while (superClass != null && allFields.length == 0) {
					allFields = superClass.getDeclaredFields();
					if (allFields.length > 0) {
						System.out.println("[CX-FILTER-MANAGER]   (from superclass " + superClass.getSimpleName() + ")");
					}
					superClass = superClass.getSuperclass();
				}
			}

			for (java.lang.reflect.Field field : allFields) {
				System.out.println("[CX-FILTER-MANAGER]   - " + field.getType().getSimpleName() + " " + field.getName());
			}

		} catch (Exception e) {
			System.err.println("[CX-FILTER-MANAGER] Error getting viewer: " + e.getMessage());
		}

		return null;
	}

	/**
	 * Try to find a StructuredViewer associated with a Tree widget
	 */
	private StructuredViewer findViewerForWidget(org.eclipse.swt.widgets.Tree tree, org.eclipse.ui.IViewPart view) {
		try {
			// Try reflection on the view to find a viewer field
			for (java.lang.reflect.Field field : view.getClass().getDeclaredFields()) {
				field.setAccessible(true);
				Object obj = field.get(view);
				if (obj instanceof StructuredViewer) {
					StructuredViewer sv = (StructuredViewer) obj;
					if (sv.getControl() == tree) {
						return sv;
					}
				}
			}
		} catch (Exception e) {
			// Ignore
		}
		return null;
	}

	/**
	 * Search for StructuredViewer in a composite's children
	 */
	private StructuredViewer findViewerInComposite(org.eclipse.swt.widgets.Composite composite) {
		try {
			org.eclipse.swt.widgets.Control[] children = composite.getChildren();
			for (org.eclipse.swt.widgets.Control child : children) {
				if (child.getData() instanceof StructuredViewer) {
					return (StructuredViewer) child.getData();
				}
				if (child instanceof org.eclipse.swt.widgets.Composite) {
					StructuredViewer viewer = findViewerInComposite((org.eclipse.swt.widgets.Composite) child);
					if (viewer != null) {
						return viewer;
					}
				}
			}
		} catch (Exception e) {
			// Ignore
		}
		return null;
	}

	/**
	 * Install the filter on the Problems View viewer
	 */
	private void installFilter() {
		if (problemsViewViewer == null) {
			System.out.println("[CX-FILTER-MANAGER] Cannot install filter: viewer is null");
			return;
		}

		if (filter == null) {
			filter = new CxProblemsViewFilter();
			System.out.println("[CX-FILTER-MANAGER] Created new CxProblemsViewFilter instance");
		}

		System.out.println("[CX-FILTER-MANAGER] Installing filter on viewer: " + problemsViewViewer.getClass().getSimpleName());

		// Remove filter if already present (avoid duplicates)
		try {
			problemsViewViewer.removeFilter(filter);
			System.out.println("[CX-FILTER-MANAGER] Existing filter removed");
		} catch (Exception e) {
			System.out.println("[CX-FILTER-MANAGER] Filter not previously installed (OK)");
		}

		// Add the filter
		try {
			problemsViewViewer.addFilter(filter);
			System.out.println("[CX-FILTER-MANAGER] ✓ CxProblemsViewFilter installed successfully on viewer");
		} catch (Exception e) {
			System.err.println("[CX-FILTER-MANAGER] Error installing filter: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Apply a severity filter
	 */
	public void applySeverityFilter(String severity) {
		// Try to initialize if not yet initialized
		if (filter == null || problemsViewViewer == null) {
			System.out.println("[CX-FILTER-MANAGER] Attempting lazy initialization...");
			tryInitializeProblemsView();
		}

		if (filter == null) {
			System.err.println("[CX-FILTER-MANAGER] ✗ Filter not initialized - Problems View may not be available yet");
			return;
		}

		System.out.println("[CX-FILTER-MANAGER] Applying severity filter: " + severity);

		if ("ALL".equalsIgnoreCase(severity) || "CLEAR".equalsIgnoreCase(severity)) {
			filter.clearSeverityFilter();
			System.out.println("[CX-FILTER-MANAGER] Severity filter cleared");
		} else {
			try {
				com.checkmarx.eclipse.enums.Severity sev = com.checkmarx.eclipse.enums.Severity.valueOf(severity.toUpperCase());
				filter.setSeverityFilter(sev);
				System.out.println("[CX-FILTER-MANAGER] Severity filter set to: " + sev);
			} catch (IllegalArgumentException e) {
				System.err.println("[CX-FILTER-MANAGER] Invalid severity: " + severity);
				return;
			}
		}

		// Refresh the viewer
		if (problemsViewViewer != null) {
			try {
				System.out.println("[CX-FILTER-MANAGER] Refreshing viewer (type: " + problemsViewViewer.getClass().getSimpleName() + ")...");
				problemsViewViewer.refresh();
				System.out.println("[CX-FILTER-MANAGER] ✓ Filter applied and viewer refreshed");

				// Try to expand tree if it's a TreeViewer
				if (problemsViewViewer instanceof org.eclipse.jface.viewers.TreeViewer) {
					try {
						((org.eclipse.jface.viewers.TreeViewer) problemsViewViewer).expandAll();
					} catch (Exception e) {
						// Ignore if expandAll fails
					}
				}
			} catch (Exception e) {
				System.err.println("[CX-FILTER-MANAGER] Error refreshing viewer: " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			System.err.println("[CX-FILTER-MANAGER] Cannot refresh - viewer is null");
		}
	}

	/**
	 * Attempt to initialize the Problems View viewer lazily
	 */
	private void tryInitializeProblemsView() {
		try {
			org.eclipse.ui.IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			if (window == null) {
				System.out.println("[CX-FILTER-MANAGER] No active workbench window");
				return;
			}

			org.eclipse.ui.IViewPart part = window.getActivePage().findView("org.eclipse.ui.views.ProblemView");
			if (part != null) {
				System.out.println("[CX-FILTER-MANAGER] Found Problems View: " + part.getClass().getName());
				StructuredViewer viewer = getViewerFromProblemsView(part);
				if (viewer != null) {
					problemsViewViewer = viewer;
					installFilter();
					System.out.println("[CX-FILTER-MANAGER] ✓ Lazy initialization successful");
				} else {
					System.out.println("[CX-FILTER-MANAGER] Could not find viewer in Problems View");
				}
			} else {
				System.out.println("[CX-FILTER-MANAGER] Problems View not found in active page");
			}
		} catch (Exception e) {
			System.err.println("[CX-FILTER-MANAGER] Lazy initialization failed: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Toggle a severity filter (add if not present, remove if present)
	 */
	public void toggleSeverityFilter(String severity) {
		// Try to initialize if not yet initialized
		if (filter == null || problemsViewViewer == null) {
			System.out.println("[CX-FILTER-MANAGER] Attempting lazy initialization for toggle...");
			tryInitializeProblemsView();
		}

		if (filter == null) {
			System.err.println("[CX-FILTER-MANAGER] ✗ Filter not initialized");
			return;
		}

		try {
			com.checkmarx.eclipse.enums.Severity sev = com.checkmarx.eclipse.enums.Severity.valueOf(severity.toUpperCase());

			if (filter.isSeverityFiltered(sev)) {
				filter.removeSeverityFilter(sev);
				System.out.println("[CX-FILTER-MANAGER] Removed severity: " + sev);
			} else {
				filter.addSeverityFilter(sev);
				System.out.println("[CX-FILTER-MANAGER] Added severity: " + sev);
			}

			// Refresh the viewer
			if (problemsViewViewer != null) {
				try {
					problemsViewViewer.refresh();
					System.out.println("[CX-FILTER-MANAGER] ✓ Viewer refreshed after toggle");
				} catch (Exception e) {
					System.err.println("[CX-FILTER-MANAGER] Error refreshing viewer: " + e.getMessage());
				}
			}
		} catch (IllegalArgumentException e) {
			System.err.println("[CX-FILTER-MANAGER] Invalid severity: " + severity);
		}
	}

	/**
	 * Refresh the Problems View with current filter state
	 */
	public void refreshProblemsView() {
		if (problemsViewViewer == null) {
			System.out.println("[CX-FILTER-MANAGER] Attempting to refresh, but viewer not initialized");
			tryInitializeProblemsView();
		}

		if (problemsViewViewer != null) {
			try {
				System.out.println("[CX-FILTER-MANAGER] Refreshing Problems View...");
				problemsViewViewer.refresh();
				System.out.println("[CX-FILTER-MANAGER] ✓ Problems View refreshed");
			} catch (Exception e) {
				System.err.println("[CX-FILTER-MANAGER] Error refreshing Problems View: " + e.getMessage());
			}
		}
	}

	/**
	 * Get the current filter
	 */
	public CxProblemsViewFilter getFilter() {
		return filter;
	}
}
