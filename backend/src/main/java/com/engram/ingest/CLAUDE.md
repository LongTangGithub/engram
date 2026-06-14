# com.engram.ingest — package invariant

Every source normalizes to `IngestedDocument`. Source-specific types (vault paths, Notion page
objects, file handles, API responses) never leak past the adapter that produces them.

Adding a source = a new `SourceAdapter` implementation. Never a new pipeline.