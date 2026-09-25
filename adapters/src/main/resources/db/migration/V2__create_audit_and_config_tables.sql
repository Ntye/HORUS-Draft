-- I-5: audit_event is append-only evidence; enforced by GRANTs (V3), not by code discipline.
CREATE TABLE audit_event (
    audit_id        UUID PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    actor           VARCHAR(200) NOT NULL,
    actor_kind      VARCHAR(50) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    subject_type    VARCHAR(100) NOT NULL,
    subject_id      VARCHAR(200) NOT NULL,
    payload         JSONB NOT NULL,
    correlation_id  VARCHAR(200),
    previous_hash   VARCHAR(200)
);

CREATE INDEX idx_audit_event_occurred_at ON audit_event (occurred_at);
CREATE INDEX idx_audit_event_subject ON audit_event (subject_type, subject_id);

-- I-7: thresholds, weights and list selection are versioned configuration, never a code release.
CREATE TABLE config_version (
    config_version_id  UUID PRIMARY KEY,
    profile_id          VARCHAR(200) NOT NULL,
    payload             JSONB NOT NULL,
    created_by          VARCHAR(200) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    approved_by         VARCHAR(200),
    effective_from      TIMESTAMPTZ
);

CREATE INDEX idx_config_version_profile_id ON config_version (profile_id);
