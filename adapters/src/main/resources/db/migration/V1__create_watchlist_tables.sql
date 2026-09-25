-- I-6: watchlist data is never hard-deleted. WatchEntity is stable identity; WatchEntityVersion
-- is immutable and only ever inserted, never updated -- de-listing is a new DELISTED version
-- (a tombstone), not a mutation or a delete. Every child below keys on entity_version_id, not
-- entity_id, so historical reconstruction works for more than just names.

CREATE TABLE list_version (
    list_version_id      UUID PRIMARY KEY,
    source_id             VARCHAR(100) NOT NULL,
    format_id             VARCHAR(100) NOT NULL,
    mapping_id            UUID,
    source_file_name      VARCHAR(500) NOT NULL,
    source_file_checksum  VARCHAR(200) NOT NULL,
    vendor_published_at   TIMESTAMPTZ,
    loaded_at             TIMESTAMPTZ NOT NULL,
    record_count          BIGINT NOT NULL CHECK (record_count >= 0),
    status                VARCHAR(20) NOT NULL
        CHECK (status IN ('STAGED', 'ACTIVE', 'SUPERSEDED', 'FAILED')),
    loaded_by             VARCHAR(200) NOT NULL,
    capabilities          JSONB
);

CREATE TABLE watch_entity (
    entity_id         UUID PRIMARY KEY,
    source_id         VARCHAR(100) NOT NULL,
    source_entity_id  VARCHAR(200) NOT NULL,
    UNIQUE (source_id, source_entity_id)
);

CREATE TABLE watch_entity_version (
    entity_version_id  UUID PRIMARY KEY,
    entity_id          UUID NOT NULL REFERENCES watch_entity (entity_id),
    list_version_id    UUID NOT NULL REFERENCES list_version (list_version_id),
    content_hash        VARCHAR(200) NOT NULL,
    entity_type         VARCHAR(20) NOT NULL
        CHECK (entity_type IN ('INDIVIDUAL', 'ORGANISATION', 'VESSEL', 'AIRCRAFT', 'WEBSITE',
                                'PORT', 'COUNTRY', 'ADDRESS')),
    gender               VARCHAR(10) CHECK (gender IN ('MALE', 'FEMALE', 'UNKNOWN')),
    primary_name         VARCHAR(1000) NOT NULL,
    status               VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'DELISTED')),
    categories           TEXT[] NOT NULL DEFAULT '{}',
    list_sources         TEXT[] NOT NULL DEFAULT '{}',
    designated_at        DATE,
    delisted_at          DATE,
    -- I-6: a tombstone version is DELISTED with a date; the two must agree.
    CHECK (status <> 'DELISTED' OR delisted_at IS NOT NULL)
);

CREATE INDEX idx_watch_entity_version_entity_id ON watch_entity_version (entity_id);
CREATE INDEX idx_watch_entity_version_list_version_id ON watch_entity_version (list_version_id);

CREATE TABLE watch_name (
    name_id             UUID PRIMARY KEY,
    entity_version_id   UUID NOT NULL REFERENCES watch_entity_version (entity_version_id),
    name_type           VARCHAR(10) NOT NULL CHECK (name_type IN ('PRIMARY', 'ALIAS')),
    raw_name            VARCHAR(1000) NOT NULL,
    normalised_name     VARCHAR(1000) NOT NULL,
    name_tokens         TEXT[] NOT NULL DEFAULT '{}',
    phonetic_codes      TEXT[] NOT NULL DEFAULT '{}',
    trigrams            TEXT[] NOT NULL DEFAULT '{}',
    script              VARCHAR(50),
    language            VARCHAR(50),
    provenance_rule_id  VARCHAR(200),
    provenance_start    INT,
    provenance_end      INT
);

CREATE INDEX idx_watch_name_entity_version_id ON watch_name (entity_version_id);
-- Explicit ask: trigram GIN index on normalised names, for fuzzy candidate generation (Step 7).
CREATE INDEX idx_watch_name_normalised_trgm ON watch_name USING GIN (normalised_name gin_trgm_ops);

CREATE TABLE watch_dob (
    dob_id              UUID PRIMARY KEY,
    entity_version_id   UUID NOT NULL REFERENCES watch_entity_version (entity_version_id),
    year                INT,
    month               INT,
    day                 INT,
    age                 INT,
    as_of_date          DATE,
    deceased            DATE,
    precision           VARCHAR(20) NOT NULL
        CHECK (precision IN ('FULL_DATE', 'YEAR_MONTH', 'YEAR_ONLY', 'AGE_ONLY')),
    CHECK (year IS NOT NULL OR age IS NOT NULL)
);

CREATE INDEX idx_watch_dob_entity_version_id ON watch_dob (entity_version_id);

CREATE TABLE watch_identifier (
    identifier_id        UUID PRIMARY KEY,
    entity_version_id    UUID NOT NULL REFERENCES watch_entity_version (entity_version_id),
    id_type              VARCHAR(20) NOT NULL
        CHECK (id_type IN ('PASSPORT', 'REGISTRATION', 'NATIONAL_ID', 'TAX_ID', 'LEI',
                            'SWIFT_BIC', 'IMO_NUMBER', 'OTHER')),
    id_value             VARCHAR(200) NOT NULL,
    normalised_id_value  VARCHAR(200) NOT NULL,
    issuing_country      VARCHAR(10),
    provenance_rule_id   VARCHAR(200),
    provenance_start     INT,
    provenance_end       INT
);

CREATE INDEX idx_watch_identifier_entity_version_id ON watch_identifier (entity_version_id);

CREATE TABLE watch_country (
    country_id          UUID PRIMARY KEY,
    entity_version_id   UUID NOT NULL REFERENCES watch_entity_version (entity_version_id),
    raw_country         VARCHAR(200) NOT NULL,
    iso_code            VARCHAR(10)
);

CREATE INDEX idx_watch_country_entity_version_id ON watch_country (entity_version_id);

CREATE TABLE watch_address (
    address_id          UUID PRIMARY KEY,
    entity_version_id   UUID NOT NULL REFERENCES watch_entity_version (entity_version_id),
    country_code        VARCHAR(10),
    city                VARCHAR(200),
    state               VARCHAR(200),
    raw_address         VARCHAR(1000),
    CHECK (country_code IS NOT NULL OR city IS NOT NULL OR state IS NOT NULL
           OR raw_address IS NOT NULL)
);

CREATE INDEX idx_watch_address_entity_version_id ON watch_address (entity_version_id);

CREATE TABLE watch_link (
    link_id             UUID PRIMARY KEY,
    entity_version_id   UUID NOT NULL REFERENCES watch_entity_version (entity_version_id),
    -- No FK to watch_entity: ~4 of 13.07M links are dangling and targets can load out of
    -- order during streaming ingestion (§6). Revisit if Step 5/6 needs stronger integrity.
    target_entity_id    UUID NOT NULL,
    role                VARCHAR(100),
    percent             DOUBLE PRECISION
);

CREATE INDEX idx_watch_link_entity_version_id ON watch_link (entity_version_id);
CREATE INDEX idx_watch_link_target_entity_id ON watch_link (target_entity_id);

CREATE TABLE watch_external_source (
    external_source_id  UUID PRIMARY KEY,
    entity_version_id   UUID NOT NULL REFERENCES watch_entity_version (entity_version_id),
    uri                 VARCHAR(2000) NOT NULL
);

CREATE INDEX idx_watch_external_source_entity_version_id
    ON watch_external_source (entity_version_id);

CREATE TABLE watch_designation (
    designation_id       UUID PRIMARY KEY,
    entity_version_id    UUID NOT NULL REFERENCES watch_entity_version (entity_version_id),
    list_source          VARCHAR(200) NOT NULL,
    action                VARCHAR(20) NOT NULL
        CHECK (action IN ('ADDITION', 'REMOVAL', 'AMENDMENT', 'WARNING', 'AUTHORISATION',
                           'UNCLASSIFIED')),
    year                 INT,
    raw_verb             VARCHAR(500) NOT NULL,
    provenance_rule_id   VARCHAR(200),
    provenance_start     INT,
    provenance_end       INT
);

CREATE INDEX idx_watch_designation_entity_version_id ON watch_designation (entity_version_id);

CREATE TABLE watch_ownership (
    stake_id             UUID PRIMARY KEY,
    entity_version_id    UUID NOT NULL REFERENCES watch_entity_version (entity_version_id),
    owner                VARCHAR(500) NOT NULL,
    owner_type           VARCHAR(50),
    percent              DOUBLE PRECISION
        CHECK (percent IS NULL OR (percent >= 0 AND percent <= 100)),
    percent_stated       BOOLEAN NOT NULL,
    provenance_rule_id   VARCHAR(200),
    provenance_start     INT,
    provenance_end       INT
);

CREATE INDEX idx_watch_ownership_entity_version_id ON watch_ownership (entity_version_id);
