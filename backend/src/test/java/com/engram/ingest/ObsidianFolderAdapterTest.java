package com.engram.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure filesystem tests — no DB, no Spring context.
 * Each test writes its own temp files under @TempDir.
 */
class ObsidianFolderAdapterTest {

    @TempDir Path vault;

    // ── scan: basic files including nested subfolder ──────────────────────────

    @Test
    void scan_returnsOneDocumentPerMdFile_includingNested() throws IOException {
        write(vault, "Note One.md", "# Note One\nSome content.");
        write(vault, "subfolder/Note Two.md", "# Note Two\nOther content.");

        List<IngestedDocument> docs = new ObsidianFolderAdapter(vault).scan();

        assertEquals(2, docs.size());

        IngestedDocument root   = findByRef(docs, "Note One.md");
        IngestedDocument nested = findByRef(docs, "subfolder/Note Two.md");

        // sourceRef must use forward slashes regardless of platform
        assertFalse(nested.sourceRef().contains("\\"), "sourceRef must not contain backslashes");
        assertTrue(nested.sourceRef().contains("/"),   "sourceRef must use forward slash as separator");

        // title = filename stem
        assertEquals("Note One",  root.title());
        assertEquals("Note Two",  nested.title());

        // content preserved raw
        assertEquals("# Note One\nSome content.", root.content());

        // sourceType
        assertEquals(SourceType.OBSIDIAN_FOLDER, root.sourceType());

        // lastModified non-null and plausible (after epoch)
        assertNotNull(root.lastModified());
        assertTrue(root.lastModified().toEpochMilli() > 0);

        // contentHash non-empty
        assertFalse(root.contentHash().isBlank());
        assertFalse(nested.contentHash().isBlank());
    }

    // ── scan: ignore non-.md and .obsidian/ ──────────────────────────────────

    @Test
    void scan_ignoresNonMdFiles_andObsidianDir() throws IOException {
        write(vault, "Note.md",          "real content");
        write(vault, "image.png",         new byte[]{(byte)0x89, 0x50}); // binary
        write(vault, ".obsidian/app.json","{}");
        write(vault, ".git/config",       "[core]");
        write(vault, ".trash/old.md",     "deleted");

        List<IngestedDocument> docs = new ObsidianFolderAdapter(vault).scan();

        assertEquals(1, docs.size(), "only Note.md should appear");
        assertEquals("Note.md", docs.get(0).sourceRef());
    }

    // ── hash stability ────────────────────────────────────────────────────────

    @Test
    void contentHash_stableForIdenticalContent() throws IOException {
        String content = "# Stable\nSame bytes.";
        write(vault, "a.md", content);
        write(vault, "b.md", content);

        List<IngestedDocument> docs = new ObsidianFolderAdapter(vault).scan();
        String hashA = findByRef(docs, "a.md").contentHash();
        String hashB = findByRef(docs, "b.md").contentHash();

        assertEquals(hashA, hashB, "identical content must produce identical hash");
    }

    @Test
    void contentHash_changesOnSingleByteChange() throws IOException {
        write(vault, "note.md", "hello");

        String hash1 = new ObsidianFolderAdapter(vault).scan().get(0).contentHash();

        // overwrite with one byte changed
        Files.writeString(vault.resolve("note.md"), "hellp");
        String hash2 = new ObsidianFolderAdapter(vault).scan().get(0).contentHash();

        assertNotEquals(hash1, hash2, "single byte change must produce different hash");
    }

    // ── SyncDiff classification ───────────────────────────────────────────────

    @Test
    void syncDiff_classifiesAddedChangedUnchangedRemoved() throws IOException {
        write(vault, "unchanged.md", "same");
        write(vault, "changed.md",   "old content");
        write(vault, "added.md",     "brand new");
        // "removed.md" exists only in prior map

        List<IngestedDocument> current = new ObsidianFolderAdapter(vault).scan();

        // Build prior map: unchanged has same hash; changed has a different hash; removed exists only here
        String unchangedHash = findByRef(current, "unchanged.md").contentHash();
        Map<String, String> prior = Map.of(
                "unchanged.md", unchangedHash,        // same hash → unchanged
                "changed.md",   "stale-hash-000",     // different hash → changed
                "removed.md",   "some-hash-abc"        // not in current → removed
        );

        SyncDiff.SyncResult result = SyncDiff.diff(current, prior);

        assertEquals(1, result.added().size(),     "added count");
        assertEquals(1, result.changed().size(),   "changed count");
        assertEquals(1, result.unchanged().size(), "unchanged count");
        assertEquals(1, result.removed().size(),   "removed count");

        assertEquals("added.md",     result.added().get(0).sourceRef());
        assertEquals("changed.md",   result.changed().get(0).sourceRef());
        assertEquals("unchanged.md", result.unchanged().get(0));
        assertEquals("removed.md",   result.removed().get(0));
    }

    // ── edge: empty folder ────────────────────────────────────────────────────

    @Test
    void scan_emptyFolder_returnsEmptyList() {
        List<IngestedDocument> docs = new ObsidianFolderAdapter(vault).scan();
        assertNotNull(docs);
        assertTrue(docs.isEmpty(), "empty vault must yield empty list");
    }

    // ── edge: missing folder ──────────────────────────────────────────────────

    @Test
    void constructor_missingFolder_throwsIllegalArgument(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist");
        assertThrows(IllegalArgumentException.class,
                () -> new ObsidianFolderAdapter(missing),
                "missing folder must throw IllegalArgumentException");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static void write(Path root, String rel, String content) throws IOException {
        Path target = root.resolve(rel);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private static void write(Path root, String rel, byte[] bytes) throws IOException {
        Path target = root.resolve(rel);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    private static IngestedDocument findByRef(List<IngestedDocument> docs, String ref) {
        return docs.stream()
                .filter(d -> d.sourceRef().equals(ref))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No doc with sourceRef: " + ref));
    }
}
