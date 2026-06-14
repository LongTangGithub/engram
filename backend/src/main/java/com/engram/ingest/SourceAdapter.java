package com.engram.ingest;

import java.util.List;

/**
 * Contract for all ingest sources. Adding a source = a new adapter, never a new pipeline.
 *
 * Implementations are constructed with their own source config (e.g. vault root path for
 * ObsidianFolderAdapter; OAuth credentials + workspace id for the future Notion adapter).
 * The interface itself does NOT assume a filesystem — scan() is an abstract "fetch current
 * documents from wherever this source lives."
 *
 * scan() returns a full List for V1. Streaming is the natural next step if vault sizes grow
 * large, but is out of scope until there is evidence it is needed.
 */
public interface SourceAdapter {

    /** The source type this adapter handles. */
    SourceType type();

    /**
     * Fetches the current set of documents from this source.
     * Callers are responsible for diffing against prior state (see SyncDiff).
     */
    List<IngestedDocument> scan();
}