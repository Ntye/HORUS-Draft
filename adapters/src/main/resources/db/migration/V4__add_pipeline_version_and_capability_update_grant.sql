-- CLAUDE.md §5: ListVersion carries pipelineVersion and indexBinding alongside capabilities.
-- Both are additive columns on the existing list_version table (I-12 provenance).
ALTER TABLE list_version ADD COLUMN pipeline_version VARCHAR(50) NOT NULL DEFAULT '1.0.0';
ALTER TABLE list_version ALTER COLUMN pipeline_version DROP DEFAULT;
ALTER TABLE list_version ADD COLUMN index_binding VARCHAR(200);

-- capabilities and record_count are only known once the whole staged file has streamed
-- through, so -- like status (V3) -- each is written once as an update from its placeholder
-- to its measured value, and nothing else on the row. Same segregation-of-duties reasoning as
-- the status grant.
GRANT UPDATE (capabilities, record_count) ON list_version TO horus_ingest;
