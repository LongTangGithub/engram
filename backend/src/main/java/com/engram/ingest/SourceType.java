package com.engram.ingest;

/**
 * Identifies the origin of an IngestedDocument.
 * Only OBSIDIAN_FOLDER is implemented now; FILE_UPLOAD and NOTION reserve the contract
 * so callers can switch on SourceType without code changes when ENG-10 lands.
 */
public enum SourceType {
    OBSIDIAN_FOLDER,
    FILE_UPLOAD,
    NOTION
}