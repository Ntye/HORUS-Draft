-- Step 9: screening evidence. Like audit_event (I-5) these tables are append-only: horus_screen is
-- granted INSERT and SELECT and nothing else, so a stored screening can never be edited or deleted
-- by the role that produced it. The whole explanation is stored as JSON (I-3): reading a screening
-- back never recomputes it.

CREATE TABLE screening_request (
    screening_id        UUID PRIMARY KEY,
    consumer_system     VARCHAR(200) NOT NULL,
    consumer_reference  VARCHAR(200),
    profile_id          VARCHAR(200) NOT NULL,
    requested_by        VARCHAR(200) NOT NULL,
    requested_at        TIMESTAMPTZ NOT NULL,
    -- I-12: provenance is mandatory. A set of list versions, and never empty.
    list_version_ids    UUID[] NOT NULL CHECK (cardinality(list_version_ids) > 0),
    config_version_id   UUID NOT NULL REFERENCES config_version (config_version_id),
    pipeline_version    VARCHAR(50) NOT NULL,
    outcome             VARCHAR(20) NOT NULL
        CHECK (outcome IN ('NO_MATCH', 'POSSIBLE_MATCH', 'STRONG_MATCH', 'ERROR')),
    failure_reason      VARCHAR(2000),
    idempotency_key     VARCHAR(200) NOT NULL UNIQUE
);

CREATE INDEX idx_screening_request_requested_at ON screening_request (requested_at);

CREATE TABLE screening_subject (
    subject_id          UUID PRIMARY KEY,
    screening_id        UUID NOT NULL REFERENCES screening_request (screening_id),
    subject_reference   VARCHAR(200),
    query_name          VARCHAR(500) NOT NULL,
    candidate_count     INT NOT NULL CHECK (candidate_count >= 0),
    top_score           INT NOT NULL CHECK (top_score BETWEEN 0 AND 100),
    outcome             VARCHAR(20) NOT NULL
        CHECK (outcome IN ('NO_MATCH', 'POSSIBLE_MATCH', 'STRONG_MATCH', 'ERROR')),
    ordinal             INT NOT NULL CHECK (ordinal >= 1),
    UNIQUE (screening_id, ordinal)
);

CREATE INDEX idx_screening_subject_screening_id ON screening_subject (screening_id);

CREATE TABLE screening_candidate (
    candidate_id                UUID PRIMARY KEY,
    subject_id                  UUID NOT NULL REFERENCES screening_subject (subject_id),
    source_id                   VARCHAR(100) NOT NULL,
    entity_id                   UUID NOT NULL REFERENCES watch_entity (entity_id),
    entity_version_id           UUID NOT NULL REFERENCES watch_entity_version (entity_version_id),
    composite_score             INT NOT NULL CHECK (composite_score BETWEEN 0 AND 100),
    -- JSON, not JSONB: JSONB re-orders keys, and evidence is stored and returned byte-for-byte.
    explanation_json            JSON NOT NULL,
    explanation_schema_version  VARCHAR(20) NOT NULL,
    decision_band               VARCHAR(20) NOT NULL
        CHECK (decision_band IN ('NO_MATCH', 'POSSIBLE_MATCH', 'STRONG_MATCH')),
    rank                        INT NOT NULL CHECK (rank >= 1),
    UNIQUE (subject_id, rank)
);

CREATE INDEX idx_screening_candidate_subject_id ON screening_candidate (subject_id);
CREATE INDEX idx_screening_candidate_entity_id ON screening_candidate (entity_id);

GRANT SELECT, INSERT ON screening_request, screening_subject, screening_candidate TO horus_screen;

-- I-7: configuration is versioned and immutable. Publishing is the ingest role's job (segregation
-- from the role that screens); it is insert-only -- a change is a new version, never an edit.
GRANT SELECT, INSERT ON config_version TO horus_ingest;
