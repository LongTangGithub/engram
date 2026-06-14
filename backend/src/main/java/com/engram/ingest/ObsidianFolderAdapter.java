package com.engram.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Reads an Obsidian vault — a plain folder of .md files — and normalizes each file into an
 * IngestedDocument. This is the native first implementation of SourceAdapter.
 *
 * Deferred (not in scope for ENG-3):
 * - Frontmatter parsing / stripping (content is raw markdown)
 * - Wikilink / transclusion resolution
 * - Streaming (full List is fine for V1 vault sizes)
 * - Rename detection (handled as remove + add by SyncDiff)
 */
public class ObsidianFolderAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(ObsidianFolderAdapter.class);

    /** Dirs to skip — Obsidian internals, VCS, and trash. */
    private static final Set<String> IGNORED_DIRS = Set.of(".obsidian", ".git", ".trash");

    private final Path vaultRoot;

    /**
     * @param vaultRoot absolute path to the Obsidian vault root directory.
     * @throws IllegalArgumentException if the path does not exist or is not a directory.
     */
    public ObsidianFolderAdapter(Path vaultRoot) {
        if (!Files.exists(vaultRoot)) {
            throw new IllegalArgumentException("Vault root does not exist: " + vaultRoot);
        }
        if (!Files.isDirectory(vaultRoot)) {
            throw new IllegalArgumentException("Vault root is not a directory: " + vaultRoot);
        }
        this.vaultRoot = vaultRoot;
    }

    @Override
    public SourceType type() {
        return SourceType.OBSIDIAN_FOLDER;
    }

    /**
     * Walks the vault recursively, returning one IngestedDocument per .md file,
     * sorted by sourceRef (forward-slash, lexicographic). Deterministic order removes
     * a flaky-test trap and makes diffs easier to reason about.
     * Non-.md files and ignored dirs (.obsidian, .git, .trash) are silently skipped.
     * Non-UTF-8 files are skipped with a logged warning — they do not crash the scan.
     */
    @Override
    public List<IngestedDocument> scan() {
        List<IngestedDocument> results = new ArrayList<>();
        try (var stream = Files.walk(vaultRoot)) {
            stream
                .filter(p -> !isInIgnoredDir(p))
                .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".md"))
                .forEach(p -> readFile(p, results));
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk vault: " + vaultRoot, e);
        }
        results.sort((a, b) -> a.sourceRef().compareTo(b.sourceRef()));
        return results;
    }

    private void readFile(Path file, List<IngestedDocument> out) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            log.warn("Skipping non-UTF-8 file: {}", file);
            return;
        } catch (IOException e) {
            log.warn("Skipping unreadable file: {} — {}", file, e.getMessage());
            return;
        }

        Instant lastModified;
        try {
            lastModified = Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            log.warn("Could not read lastModified for {}, using epoch", file);
            lastModified = Instant.EPOCH;
        }

        String sourceRef = vaultRoot.relativize(file).toString().replace('\\', '/');
        String title     = stem(file.getFileName().toString());
        String hash      = sha256(content);

        out.add(new IngestedDocument(SourceType.OBSIDIAN_FOLDER, sourceRef, title, content, lastModified, hash));
    }

    private boolean isInIgnoredDir(Path path) {
        // Check every segment of the path relative to vault root.
        Path rel = vaultRoot.relativize(path);
        for (Path segment : rel) {
            if (IGNORED_DIRS.contains(segment.toString())) return true;
        }
        return false;
    }

    private static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e); // JVM guarantee; never happens
        }
    }
}