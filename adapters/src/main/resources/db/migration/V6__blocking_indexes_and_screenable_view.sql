-- Step 7: candidate generation (blocking). Additive only -- new function, new indexes, new view.

-- Order-insensitive key over a token array ("Zorvan Talmesk" == "Talmesk Zorvan"), and the shared
-- form of the phonetic key. Empty tokens are dropped so a stored array and a probe array that
-- differ only by empty codes still agree. "C" collation keeps the order byte-stable, which is
-- what makes this legitimately IMMUTABLE and therefore indexable (I-4).
CREATE FUNCTION horus_sorted_key(tokens TEXT[]) RETURNS TEXT
LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$
    SELECT string_agg(t, ' ' ORDER BY t COLLATE "C") FROM unnest(tokens) AS t WHERE t <> ''
$$;

-- EXACT_HASH strategy: whole normalised name, and the sorted-token form.
CREATE INDEX idx_watch_name_normalised_md5 ON watch_name (md5(normalised_name));
CREATE INDEX idx_watch_name_sorted_tokens_md5 ON watch_name (md5(horus_sorted_key(name_tokens)));
-- PHONETIC strategy: sorted Double Metaphone codes.
CREATE INDEX idx_watch_name_phonetic_md5 ON watch_name (md5(horus_sorted_key(phonetic_codes)));
-- TRIGRAM strategy uses the GIN index already created in V1 (idx_watch_name_normalised_trgm).

-- The version whose names a screening should search, per entity: the newest ACTIVE (i.e. not
-- tombstone) version across the source's ACTIVE and SUPERSEDED loads up to the active one.
-- A tombstone carries no name rows (I-6 keeps the last live version's names), so resolving
-- through the last live version is what keeps a de-listed entity screenable: I-2 / I-8 say
-- de-listing demotes and explains, it never removes a candidate.
-- Deliberately not DISTINCT ON, so the planner can push the name predicates down through it.
CREATE VIEW screenable_watch_entity_version AS
SELECT wev.entity_version_id, wev.entity_id, lv.source_id
FROM watch_entity_version wev
JOIN list_version lv ON lv.list_version_id = wev.list_version_id
JOIN list_version active ON active.source_id = lv.source_id AND active.status = 'ACTIVE'
WHERE wev.status = 'ACTIVE'
  AND lv.status IN ('ACTIVE', 'SUPERSEDED')
  AND lv.loaded_at <= active.loaded_at
  AND NOT EXISTS (
      SELECT 1
      FROM watch_entity_version newer
      JOIN list_version newer_lv ON newer_lv.list_version_id = newer.list_version_id
      WHERE newer.entity_id = wev.entity_id
        AND newer.status = 'ACTIVE'
        AND newer_lv.status IN ('ACTIVE', 'SUPERSEDED')
        AND newer_lv.loaded_at <= active.loaded_at
        AND newer_lv.loaded_at > lv.loaded_at);

GRANT SELECT ON screenable_watch_entity_version TO horus_screen;
