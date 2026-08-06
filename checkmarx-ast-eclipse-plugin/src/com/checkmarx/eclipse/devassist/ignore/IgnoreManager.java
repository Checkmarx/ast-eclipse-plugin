package com.checkmarx.eclipse.devassist.ignore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;

import com.checkmarx.eclipse.devassist.backend.DevAssistScanStateHolder;
import com.checkmarx.eclipse.devassist.model.ScanEngine;
import com.checkmarx.eclipse.devassist.model.ScanIssue;
import com.checkmarx.eclipse.devassist.ui.findings.realtime.RealTimeScanJob;
import com.checkmarx.eclipse.utils.CxLogger;

/**
 * Facade for ignoring/reviving findings and keeping ignored-entry line numbers in sync
 * with new scan results.
 *
 * Mirrors JetBrains IgnoreManager. Two deliberate adaptations for Eclipse:
 * 1. Identity keys are built directly from ScanIssue's own scalar fields (title, ruleId,
 *    similarityId, packageManager/Version, secretValue, imageTag) rather than from a
 *    per-Vulnerability sub-object - Eclipse's CxFindingsView (and ProblemDecorator's
 *    annotation grouping) already treats one ScanIssue as one ignorable/decoratable unit,
 *    even when it bundles multiple Vulnerability children on the same line.
 * 2. There is no per-project workspace root, so file paths are stored/matched as absolute
 *    OS paths instead of workspace-relative paths.
 */
public final class IgnoreManager {

	private static final String LOG_TAG = "[IGNORE-MGR]";
	private static final IgnoreManager INSTANCE = new IgnoreManager();

	private static final QualifiedName STATE_HOLDER_KEY = new QualifiedName("com.checkmarx.eclipse.plugin",
			"state-holder");

	private final IgnoreFileManager fileManager = IgnoreFileManager.getInstance();

	private IgnoreManager() {
	}

	public static IgnoreManager getInstance() {
		return INSTANCE;
	}

	public void addListener(IgnoreFileManager.IgnoreListener listener) {
		fileManager.addListener(listener);
	}

	public void removeListener(IgnoreFileManager.IgnoreListener listener) {
		fileManager.removeListener(listener);
	}

	public List<IgnoreEntry> getIgnoredEntries() {
		return fileManager.getAllIgnoreEntries();
	}

	/**
	 * Ignored entries with at least one active file reference for this exact file path.
	 * Used by ProblemDecorator to paint the "ignored" gutter icon.
	 */
	public List<IgnoreEntry> getIgnoredEntriesForFile(String filePath) {
		String normalized = normalizePath(filePath);
		List<IgnoreEntry> result = new ArrayList<>();
		for (IgnoreEntry entry : fileManager.getAllIgnoreEntries()) {
			boolean hasActiveRef = entry.getFiles().stream()
					.anyMatch(f -> f.isActive() && IgnoreFileManager.pathsEqual(f.getPath(), normalized));
			if (hasActiveRef) {
				result.add(entry);
			}
		}
		return result;
	}

	/**
	 * Ignore a single finding.
	 */
	public void addIgnoredEntry(ScanIssue issueToIgnore) {
		if (issueToIgnore == null) {
			return;
		}
		String key = buildStableKey(issueToIgnore);
		if (key == null) {
			CxLogger.warning(LOG_TAG + " Cannot ignore issue, unable to build stable key: " + issueToIgnore.getTitle());
			return;
		}
		IgnoreEntry entry = fileManager.getIgnoreData().computeIfAbsent(key, k -> buildEntryShell(issueToIgnore));
		addOrActivateFileReference(entry, issueToIgnore);
		entry.setDateAdded(Instant.now().toString());
		fileManager.saveIgnoreDataToDisk();
		CxLogger.info(LOG_TAG + " Ignored: " + key);
		triggerRescan(issueToIgnore.getFilePath());
	}

	/**
	 * Ignore every currently known issue that shares the same stable key as issueToIgnore
	 * (e.g. same OSS package+version across all files, or same ASCA rule in the same file).
	 *
	 * @param issueToIgnore the issue the user right-clicked
	 * @param allKnownIssues all issues currently cached across all files (e.g. CxFindingsView's currentIssues)
	 */
	public void addAllIgnoredEntry(ScanIssue issueToIgnore, Map<String, List<ScanIssue>> allKnownIssues) {
		if (issueToIgnore == null) {
			return;
		}
		String key = buildStableKey(issueToIgnore);
		if (key == null) {
			return;
		}
		IgnoreEntry entry = fileManager.getIgnoreData().computeIfAbsent(key, k -> buildEntryShell(issueToIgnore));

		java.util.Set<String> affectedFilePaths = new java.util.HashSet<>();
		if (allKnownIssues != null) {
			for (List<ScanIssue> issues : allKnownIssues.values()) {
				if (issues == null) {
					continue;
				}
				for (ScanIssue issue : issues) {
					if (key.equals(buildStableKey(issue))) {
						addOrActivateFileReference(entry, issue);
						affectedFilePaths.add(issue.getFilePath());
					}
				}
			}
		} else {
			addOrActivateFileReference(entry, issueToIgnore);
			affectedFilePaths.add(issueToIgnore.getFilePath());
		}
		entry.setDateAdded(Instant.now().toString());
		fileManager.saveIgnoreDataToDisk();
		CxLogger.info(LOG_TAG + " Ignored all of type: " + key + " (" + affectedFilePaths.size() + " files)");
		for (String path : affectedFilePaths) {
			triggerRescan(path);
		}
	}

	/**
	 * Revive (stop ignoring) a single entry, and rescan every file it referenced.
	 */
	public void reviveEntry(IgnoreEntry entryToRevive) {
		if (entryToRevive == null) {
			return;
		}
		String key = findKeyForEntry(entryToRevive);
		if (key == null) {
			CxLogger.warning(LOG_TAG + " Cannot revive, entry not found in store: " + entryToRevive.getTitle());
			return;
		}
		List<String> pathsToRescan = new ArrayList<>();
		for (IgnoreEntry.FileReference ref : entryToRevive.getFiles()) {
			if (ref.isActive()) {
				pathsToRescan.add(ref.getPath());
			}
		}
		fileManager.reviveEntry(key);
		CxLogger.info(LOG_TAG + " Revived: " + key);
		for (String path : pathsToRescan) {
			triggerRescan(path);
		}
	}

	public void reviveEntries(List<IgnoreEntry> entries) {
		if (entries == null) {
			return;
		}
		for (IgnoreEntry entry : entries) {
			reviveEntry(entry);
		}
	}

	private String findKeyForEntry(IgnoreEntry entry) {
		for (Map.Entry<String, IgnoreEntry> mapEntry : fileManager.getIgnoreData().entrySet()) {
			if (mapEntry.getValue() == entry) {
				return mapEntry.getKey();
			}
		}
		return null;
	}

	/**
	 * True if this exact issue is currently ignored (active file reference for OSS/CONTAINERS
	 * is package-wide; for SECRETS/IAC/ASCA it must also match this issue's file).
	 */
	public boolean isIgnored(ScanIssue issue) {
		if (issue == null) {
			return false;
		}
		String key = buildStableKey(issue);
		if (key == null) {
			return false;
		}
		IgnoreEntry entry = fileManager.getIgnoreData().get(key);
		if (entry == null) {
			return false;
		}
		if (isPackageWide(issue.getScanEngine())) {
			return entry.getFiles().stream().anyMatch(IgnoreEntry.FileReference::isActive);
		}
		String normalized = normalizePath(issue.getFilePath());
		return entry.getFiles().stream()
				.anyMatch(f -> f.isActive() && IgnoreFileManager.pathsEqual(f.getPath(), normalized));
	}

	/**
	 * Returns only the issues from allIssues that are NOT ignored.
	 */
	public List<ScanIssue> filterActive(List<ScanIssue> allIssues) {
		if (allIssues == null) {
			return List.of();
		}
		List<ScanIssue> active = new ArrayList<>();
		for (ScanIssue issue : allIssues) {
			if (!isIgnored(issue)) {
				active.add(issue);
			}
		}
		return active;
	}

	/**
	 * Keeps ignored-entry line numbers in sync with a fresh, UNFILTERED scan of one file.
	 *
	 * For every ignore entry with an active file reference for this file:
	 * - if a current issue with the same stable key is found, update the stored line
	 * - if not, the finding is presumed fixed - drop this file's reference (and the whole
	 *   entry if no file references remain)
	 *
	 * Mirrors JetBrains IgnoreManager.updateLineNumbersForIgnoredEntries.
	 *
	 * @param filePath absolute path of the file that was scanned
	 * @param allIssuesForFile the FULL (not ignore-filtered) list of issues found for that file
	 */
	public void reconcileLineNumbers(String filePath, List<ScanIssue> allIssuesForFile) {
		if (filePath == null) {
			return;
		}
		String normalized = normalizePath(filePath);
		Map<String, ScanIssue> keyToIssue = new HashMap<>();
		if (allIssuesForFile != null) {
			for (ScanIssue issue : allIssuesForFile) {
				String key = buildStableKey(issue);
				if (key != null) {
					keyToIssue.put(key, issue);
				}
			}
		}

		boolean changed = false;
		List<String> keysToRemove = new ArrayList<>();
		for (Map.Entry<String, IgnoreEntry> mapEntry : fileManager.getIgnoreData().entrySet()) {
			IgnoreEntry entry = mapEntry.getValue();
			boolean hasActiveRefForThisFile = entry.getFiles().stream()
					.anyMatch(f -> f.isActive() && IgnoreFileManager.pathsEqual(f.getPath(), normalized));
			if (!hasActiveRefForThisFile) {
				continue;
			}

			ScanIssue match = keyToIssue.get(mapEntry.getKey());
			if (match != null && match.getLocations() != null && !match.getLocations().isEmpty()) {
				int newLine = match.getLocations().get(0).getLine();
				for (IgnoreEntry.FileReference ref : entry.getFiles()) {
					if (ref.isActive() && IgnoreFileManager.pathsEqual(ref.getPath(), normalized)
							&& ref.getLine() != newLine && newLine > 0) {
						ref.setLine(newLine);
						changed = true;
					}
				}
			} else {
				// Not found in the fresh scan for this file - the finding appears fixed.
				boolean removedAny = entry.getFiles()
						.removeIf(f -> IgnoreFileManager.pathsEqual(f.getPath(), normalized));
				if (removedAny) {
					changed = true;
				}
				if (entry.getFiles().isEmpty()) {
					keysToRemove.add(mapEntry.getKey());
				}
			}
		}

		for (String key : keysToRemove) {
			fileManager.getIgnoreData().remove(key);
			changed = true;
		}

		if (changed) {
			fileManager.saveIgnoreDataToDisk();
			CxLogger.info(LOG_TAG + " Reconciled ignored line numbers for: " + filePath);
		}
	}

	// ------------------------------------------------------------------
	// Stable key construction (mirrors JetBrains formatJsonKeyForIgnoreEntry)
	// ------------------------------------------------------------------

	private boolean isPackageWide(ScanEngine engine) {
		return engine == ScanEngine.OSS || engine == ScanEngine.CONTAINERS;
	}

	String buildStableKey(ScanIssue issue) {
		if (issue == null || issue.getScanEngine() == null) {
			return null;
		}
		ScanEngine engine = issue.getScanEngine();
		switch (engine) {
			case OSS:
				return join(engine, issue.getPackageManager(), issue.getTitle(), issue.getPackageVersion());
			case CONTAINERS:
				return join(engine, issue.getTitle(), issue.getImageTag());
			case SECRETS:
				return join(engine, issue.getTitle(), issue.getSecretValue(), normalizePath(issue.getFilePath()));
			case IAC:
				return join(engine, issue.getTitle(), issue.getSimilarityId(), normalizePath(issue.getFilePath()));
			case ASCA:
				return join(engine, issue.getTitle(),
						issue.getRuleId() == null ? null : issue.getRuleId().toString(),
						normalizePath(issue.getFilePath()));
			default:
				return join(engine, issue.getTitle(), normalizePath(issue.getFilePath()));
		}
	}

	private String join(ScanEngine engine, String... parts) {
		StringBuilder sb = new StringBuilder(engine.name());
		for (String part : parts) {
			sb.append(':').append(part == null ? "" : part);
		}
		return sb.toString();
	}

	private IgnoreEntry buildEntryShell(ScanIssue issue) {
		IgnoreEntry entry = new IgnoreEntry();
		entry.setType(issue.getScanEngine());
		entry.setTitle(issue.getTitle());
		entry.setSeverity(issue.getSeverity());
		entry.setDescription(issue.getDescription());
		entry.setPackageManager(issue.getPackageManager());
		entry.setPackageVersion(issue.getPackageVersion());
		entry.setSimilarityId(issue.getSimilarityId());
		entry.setRuleId(issue.getRuleId());
		entry.setSecretValue(issue.getSecretValue());
		entry.setImageTag(issue.getImageTag());
		return entry;
	}

	private void addOrActivateFileReference(IgnoreEntry entry, ScanIssue issue) {
		String path = normalizePath(issue.getFilePath());
		int line = (issue.getLocations() != null && !issue.getLocations().isEmpty())
				? issue.getLocations().get(0).getLine()
				: 0;
		for (IgnoreEntry.FileReference ref : entry.getFiles()) {
			if (IgnoreFileManager.pathsEqual(ref.getPath(), path)) {
				ref.setActive(true);
				ref.setLine(line);
				return;
			}
		}
		entry.getFiles().add(new IgnoreEntry.FileReference(path, true, line));
	}

	/**
	 * No workspace-relative normalization is possible across a multi-project Eclipse
	 * workspace, so this is currently the identity function; kept as a seam so callers
	 * don't compare raw paths directly (e.g. if case/separator normalization is needed later).
	 */
	String normalizePath(String filePath) {
		return filePath == null ? null : filePath.replace('\\', '/');
	}

	// ------------------------------------------------------------------
	// Rescan triggering (so ignore/revive is reflected immediately, not on next edit)
	// ------------------------------------------------------------------

	private void triggerRescan(String filePath) {
		try {
			if (filePath == null) {
				return;
			}
			IFile file = ResourcesPlugin.getWorkspace().getRoot()
					.getFileForLocation(new Path(filePath));
			if (file == null || !file.exists()) {
				CxLogger.warning(LOG_TAG + " Cannot resolve IFile for rescan: " + filePath);
				return;
			}
			IProject project = file.getProject();
			if (project == null || !project.isOpen()) {
				return;
			}
			DevAssistScanStateHolder stateHolder = (DevAssistScanStateHolder) project
					.getSessionProperty(STATE_HOLDER_KEY);
			if (stateHolder != null) {
				// Content hasn't changed, only ignore-state has - force the scan to actually run
				// instead of short-circuiting on an unchanged content hash.
				stateHolder.clearFileState(filePath);
			}
			new RealTimeScanJob(file, file.getName()).reschedule(0);
		} catch (Exception e) {
			CxLogger.warning(LOG_TAG + " Failed to trigger rescan for: " + filePath + " - " + e.getMessage());
		}
	}
}
