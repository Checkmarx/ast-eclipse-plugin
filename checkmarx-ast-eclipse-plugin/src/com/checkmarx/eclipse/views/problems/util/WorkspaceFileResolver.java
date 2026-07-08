package com.checkmarx.eclipse.views.problems.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;

import com.checkmarx.eclipse.utils.CxLogger;
import com.checkmarx.eclipse.utils.PluginConstants;

/**
 * Resolves a finding's file name to concrete workspace {@link IFile}s.
 *
 * <p>
 * Implements the file-lookup optimization described in the design document
 * (Section 5, Fix #3): results are cached for the lifetime of a single publish
 * cycle so that N findings referring to the same file trigger only one
 * workspace traversal instead of N. Call {@link #clearCache()} before each new
 * publish to avoid serving stale results.
 * </p>
 *
 * <p>
 * A {@code IResourceProxyVisitor} is used (rather than {@code accept} on full
 * {@code IResource}s) so the workspace tree is walked as lightweight proxies
 * and the heavier {@code IResource} is materialized only on a name match.
 * </p>
 */
public class WorkspaceFileResolver {

	/** file name (last segment) -&gt; matching workspace files. */
	private final Map<String, List<IFile>> cache = new HashMap<>();

	/**
	 * Find all workspace files whose name matches the last segment of the given
	 * finding file name. Cached across calls until {@link #clearCache()}.
	 *
	 * @param fileName a file name or path; only the last segment is matched.
	 * @return matching files, never {@code null} (possibly empty).
	 */
	public List<IFile> resolve(String fileName) {
		if (fileName == null || fileName.isEmpty()) {
			return new ArrayList<>();
		}
		String simpleName = new Path(fileName).lastSegment();
		return cache.computeIfAbsent(simpleName, this::search);
	}

	/**
	 * Clears the per-cycle cache. Must be called before starting a new publish
	 * so that files added/removed since the last cycle are picked up.
	 */
	public void clearCache() {
		cache.clear();
	}

	private List<IFile> search(String simpleName) {
		final List<IFile> found = new ArrayList<>();
		try {
			ResourcesPlugin.getWorkspace().getRoot().accept(new IResourceProxyVisitor() {
				@Override
				public boolean visit(IResourceProxy proxy) throws CoreException {
					if (proxy.getType() == IResource.FILE && proxy.getName().equals(simpleName)) {
						found.add((IFile) proxy.requestResource());
					}
					return true;
				}
			}, IResource.NONE);
		} catch (CoreException e) {
			CxLogger.error(String.format(PluginConstants.ERROR_FINDING_FILE, e.getMessage()), e);
		}
		return found;
	}
}
