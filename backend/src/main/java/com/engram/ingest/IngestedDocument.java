package com.engram.ingest;

import java.time.Instant;

/**
 * Normalized output of any SourceAdapter — the single type that crosses the adapter boundary.
 * Source-specific types (vault paths, Notion page objects, etc.) never leak past the adapter.
 *
 * @param sourceType   which adapter produced this document
 * @param sourceRef    stable id within the source (e.g. vault-relative path for Obsidian,
 *                     page id for Notion) — used as the diff key in SyncDiff
 * @param title        human-readable title (filename stem for Obsidian; page title for Notion)
 * @param content      raw markdown content, UTF-8; no frontmatter stripping for now
 * @param lastModified filesystem or API last-modified timestamp
 * @param contentHash  SHA-256 hex of the UTF-8 content bytes — drives incremental-sync diffing
 */
public record IngestedDocument(
        SourceType sourceType,
        String     sourceRef,
        String     title,
        String     content,
        Instant    lastModified,
        String     contentHash
) {}