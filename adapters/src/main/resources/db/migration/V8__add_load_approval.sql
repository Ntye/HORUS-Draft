-- Unattended loads. A daily scheduled ingest cannot wait for a human, but a capability deviation
-- still has to be seen by one -- so the decision moves from BEFORE promotion to AFTER it, and the
-- load carries the fact that it is owed a review.
--
-- approval_mode is how the load was invoked: REQUIRED (an operator ran it and will decide) or
-- NOT_REQUIRED (a scheduler ran it and a capability deviation is waived). The default is REQUIRED,
-- so a load that says nothing fails closed (I-1) and every row that existed before this migration
-- is correctly marked as having been human-approved.
--
-- review_state is what is owed: NONE when nothing was waived, PENDING when a deviation was waived
-- and promoted unreviewed, ACKNOWLEDGED once a human has signed it off.
--
-- Deliberately NOT waivable by this flag, whatever the mode:
--   * the 5% reject-fraction gate, the version-row count check and an abandoned load -- these are
--     integrity failures, not judgements (I-1);
--   * the anomalous-delta hold. A >20% change is the signature of a truncated delivery, and
--     promoting it writes tombstones that I-6 makes permanent.
ALTER TABLE list_version
    ADD COLUMN approval_mode VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
    ADD COLUMN review_state  VARCHAR(20) NOT NULL DEFAULT 'NONE';

ALTER TABLE list_version
    ADD CONSTRAINT list_version_approval_mode_check
        CHECK (approval_mode IN ('REQUIRED', 'NOT_REQUIRED')),
    ADD CONSTRAINT list_version_review_state_check
        CHECK (review_state IN ('NONE', 'PENDING', 'ACKNOWLEDGED'));

-- "Which loads are owed a review?" must be one cheap query, for an operator and for Internal
-- Audit. Partial, because PENDING is rare by design.
CREATE INDEX idx_list_version_pending_review ON list_version (source_id, loaded_at)
    WHERE review_state = 'PENDING';

-- horus_ingest already holds UPDATE (status) from V3 and UPDATE (capabilities, record_count) from
-- V4. Acknowledging a review updates review_state and nothing else; the column-level grant is what
-- keeps that true rather than code discipline (CLAUDE.md §11).
GRANT UPDATE (review_state) ON list_version TO horus_ingest;
