# Database Indexing

A database index is a separate data structure that speeds up read queries at the
cost of extra writes and storage. The most common type is the B-tree index, which
keeps data sorted and allows lookups, range scans, and ordered traversal in
logarithmic time. Postgres uses B-tree indexes by default.

A hash index, by contrast, supports only equality lookups (no range scans) but can
be slightly faster for exact-match queries. Postgres also supports GIN indexes,
which are designed for composite values like arrays, JSONB, and full-text search,
where a single row contains many indexable items.

Indexes are not free: every insert, update, or delete must also update every index
on the table, so over-indexing slows down writes. A covering index is one that
includes all the columns a query needs, letting the database answer the query from
the index alone without touching the table heap — this is called an index-only scan.
