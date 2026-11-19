package com.checkmarx.eclipse.devassist.telemetry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the git remote URL of the repository that contains a file, for the
 * {@code repoUrl} telemetry field (scan and remediation events). Reads
 * {@code .git/config} directly - no git process is started - and never throws.
 * Credentials embedded in the URL are removed. Returns {@code null} when the file
 * is not in a git repository or has no remote.
 */
public final class GitRemoteResolver {

    private static final Pattern REMOTE_SECTION = Pattern.compile("^\\[remote\\s+\"([^\"]+)\"\\]$");
    private static final Pattern URL_LINE = Pattern.compile("^url\\s*=\\s*(.+)$");

    private GitRemoteResolver() {
    }

    public static String resolve(String filePath) {
        try {
            if (filePath == null || filePath.trim().isEmpty()) {
                return null;
            }
            Path dir = Paths.get(filePath).toAbsolutePath().getParent();
            while (dir != null) {
                Path config = dir.resolve(".git").resolve("config");
                if (Files.isRegularFile(config)) {
                    return stripCredentials(readRemoteUrl(Files.readAllLines(config, StandardCharsets.UTF_8)));
                }
                dir = dir.getParent();
            }
        } catch (IOException | RuntimeException e) {
            // best effort only
        }
        return null;
    }

    /** Picks the "origin" remote, or the first remote that has a URL. */
    static String readRemoteUrl(List<String> configLines) {
        String current = null;
        String first = null;
        for (String raw : configLines) {
            String line = raw.trim();
            Matcher section = REMOTE_SECTION.matcher(line);
            if (section.matches()) {
                current = section.group(1);
                continue;
            }
            if (line.startsWith("[")) {
                current = null;
                continue;
            }
            Matcher url = URL_LINE.matcher(line);
            if (current != null && url.matches()) {
                if ("origin".equals(current)) {
                    return url.group(1).trim();
                }
                if (first == null) {
                    first = url.group(1).trim();
                }
            }
        }
        return first;
    }

    /** {@code https://user:token@host/x} becomes {@code https://host/x}. */
    static String stripCredentials(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        return url.trim().replaceFirst("(?i)^([a-z][a-z0-9+.-]*://)[^/@]+@", "$1");
    }
}
