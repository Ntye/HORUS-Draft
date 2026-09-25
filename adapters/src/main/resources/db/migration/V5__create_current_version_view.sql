-- "Current" version of an entity = its newest version across the source's ACTIVE and SUPERSEDED
-- loads up to and including the active one. Unchanged entities are not re-versioned on later
-- loads (I-6), so the active load alone holds only what changed. A FAILED load (including one
-- rolled back) is excluded, and so is anything loaded after the active version.
-- Ordering key is list_version.loaded_at, which must be strictly increasing per source.
CREATE VIEW current_watch_entity_version AS
SELECT DISTINCT ON (wev.entity_id) wev.*, lv.source_id
FROM watch_entity_version wev
JOIN list_version lv ON lv.list_version_id = wev.list_version_id
JOIN list_version active ON active.source_id = lv.source_id AND active.status = 'ACTIVE'
WHERE lv.status IN ('ACTIVE', 'SUPERSEDED')
  AND lv.loaded_at <= active.loaded_at
ORDER BY wev.entity_id, lv.loaded_at DESC;

GRANT SELECT ON current_watch_entity_version TO horus_ingest, horus_screen;
