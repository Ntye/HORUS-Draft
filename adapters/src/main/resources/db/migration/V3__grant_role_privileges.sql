-- Segregation of duties (CLAUDE.md §11): enforced by which credential a repository holds,
-- not by code discipline. horus_ingest, horus_screen and horus_audit are created by
-- docker/db-init/01-extensions-and-roles.sh before these migrations ever run.

GRANT USAGE ON SCHEMA public TO horus_ingest, horus_screen, horus_audit;

-- horus_ingest: writes watchlist tables. Versions are never updated once written (I-6) --
-- the sole exception is list_version.status, which legitimately transitions
-- STAGED -> ACTIVE -> SUPERSEDED, so that column alone gets an UPDATE grant.
GRANT SELECT, INSERT ON list_version TO horus_ingest;
GRANT UPDATE (status) ON list_version TO horus_ingest;
GRANT SELECT, INSERT ON watch_entity TO horus_ingest;
GRANT SELECT, INSERT ON watch_entity_version TO horus_ingest;
GRANT SELECT, INSERT ON watch_name TO horus_ingest;
GRANT SELECT, INSERT ON watch_dob TO horus_ingest;
GRANT SELECT, INSERT ON watch_identifier TO horus_ingest;
GRANT SELECT, INSERT ON watch_country TO horus_ingest;
GRANT SELECT, INSERT ON watch_address TO horus_ingest;
GRANT SELECT, INSERT ON watch_link TO horus_ingest;
GRANT SELECT, INSERT ON watch_external_source TO horus_ingest;
GRANT SELECT, INSERT ON watch_designation TO horus_ingest;
GRANT SELECT, INSERT ON watch_ownership TO horus_ingest;

-- horus_screen: reads the watchlist and the active configuration to screen against.
GRANT SELECT ON list_version TO horus_screen;
GRANT SELECT ON watch_entity TO horus_screen;
GRANT SELECT ON watch_entity_version TO horus_screen;
GRANT SELECT ON watch_name TO horus_screen;
GRANT SELECT ON watch_dob TO horus_screen;
GRANT SELECT ON watch_identifier TO horus_screen;
GRANT SELECT ON watch_country TO horus_screen;
GRANT SELECT ON watch_address TO horus_screen;
GRANT SELECT ON watch_link TO horus_screen;
GRANT SELECT ON watch_external_source TO horus_screen;
GRANT SELECT ON watch_designation TO horus_screen;
GRANT SELECT ON watch_ownership TO horus_screen;
GRANT SELECT ON config_version TO horus_screen;
-- NOTE: horus_screen's screening-table writes are deferred -- screening_request,
-- screening_subject and screening_candidate don't exist yet; see Step 9.

-- horus_audit: append-only evidence (I-5). INSERT and SELECT only, nothing else, ever.
GRANT SELECT, INSERT ON audit_event TO horus_audit;
