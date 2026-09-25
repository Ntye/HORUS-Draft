-- Times the three blocking strategies with the EXACT SQL CoLocatedIndexAdapter runs (ADR-003).
-- Run against the scratch database populated by 01-generate.sql. Each EXPLAIN ANALYZE prints an
-- "Execution Time"; repeat blocks show cold-to-warm behaviour. Read the median of the later runs.
SET pg_trgm.similarity_threshold = 0.3;

-- A name that exists, a one-character typo of it, and the most frequent name in the data.
SELECT normalised_name AS q FROM watch_name WHERE name_type = 'PRIMARY' OFFSET 12345 LIMIT 1 \gset
SELECT normalised_name AS common, count(*) AS common_n FROM watch_name GROUP BY 1 ORDER BY 2 DESC LIMIT 1 \gset
SELECT overlay(:'q' placing 'x' from 3 for 1) AS typo \gset

\echo ## EXACT_HASH, exact name (repeat 3 times)
EXPLAIN (ANALYZE, TIMING ON) SELECT DISTINCT s.entity_id, s.entity_version_id
FROM watch_name wn JOIN screenable_watch_entity_version s ON s.entity_version_id = wn.entity_version_id
WHERE md5(wn.normalised_name) = md5(:'q')
   OR md5(horus_sorted_key(wn.name_tokens)) = md5(horus_sorted_key(string_to_array(:'q', ' ')))
ORDER BY s.entity_version_id LIMIT 501;
EXPLAIN (ANALYZE, TIMING ON) SELECT DISTINCT s.entity_id, s.entity_version_id
FROM watch_name wn JOIN screenable_watch_entity_version s ON s.entity_version_id = wn.entity_version_id
WHERE md5(wn.normalised_name) = md5(:'q')
   OR md5(horus_sorted_key(wn.name_tokens)) = md5(horus_sorted_key(string_to_array(:'q', ' ')))
ORDER BY s.entity_version_id LIMIT 501;
EXPLAIN (ANALYZE, TIMING ON) SELECT DISTINCT s.entity_id, s.entity_version_id
FROM watch_name wn JOIN screenable_watch_entity_version s ON s.entity_version_id = wn.entity_version_id
WHERE md5(wn.normalised_name) = md5(:'q')
   OR md5(horus_sorted_key(wn.name_tokens)) = md5(horus_sorted_key(string_to_array(:'q', ' ')))
ORDER BY s.entity_version_id LIMIT 501;

\echo ## TRIGRAM at threshold 0.3, one-character typo (repeat 3 times)
EXPLAIN (ANALYZE, TIMING ON) SELECT s.entity_id, s.entity_version_id
FROM watch_name wn JOIN screenable_watch_entity_version s ON s.entity_version_id = wn.entity_version_id
WHERE wn.normalised_name % :'typo'
GROUP BY s.entity_id, s.entity_version_id
ORDER BY MAX(similarity(wn.normalised_name, :'typo')) DESC, s.entity_version_id LIMIT 501;
EXPLAIN (ANALYZE, TIMING ON) SELECT s.entity_id, s.entity_version_id
FROM watch_name wn JOIN screenable_watch_entity_version s ON s.entity_version_id = wn.entity_version_id
WHERE wn.normalised_name % :'typo'
GROUP BY s.entity_id, s.entity_version_id
ORDER BY MAX(similarity(wn.normalised_name, :'typo')) DESC, s.entity_version_id LIMIT 501;
EXPLAIN (ANALYZE, TIMING ON) SELECT s.entity_id, s.entity_version_id
FROM watch_name wn JOIN screenable_watch_entity_version s ON s.entity_version_id = wn.entity_version_id
WHERE wn.normalised_name % :'typo'
GROUP BY s.entity_id, s.entity_version_id
ORDER BY MAX(similarity(wn.normalised_name, :'typo')) DESC, s.entity_version_id LIMIT 501;

\echo ## PHONETIC, exact name (repeat 3 times)
EXPLAIN (ANALYZE, TIMING ON) SELECT DISTINCT s.entity_id, s.entity_version_id
FROM watch_name wn JOIN screenable_watch_entity_version s ON s.entity_version_id = wn.entity_version_id
WHERE md5(horus_sorted_key(wn.phonetic_codes)) =
      md5(horus_sorted_key((SELECT array_agg(dmetaphone(t)) FROM unnest(string_to_array(:'q', ' ')) t)))
ORDER BY s.entity_version_id LIMIT 501;
EXPLAIN (ANALYZE, TIMING ON) SELECT DISTINCT s.entity_id, s.entity_version_id
FROM watch_name wn JOIN screenable_watch_entity_version s ON s.entity_version_id = wn.entity_version_id
WHERE md5(horus_sorted_key(wn.phonetic_codes)) =
      md5(horus_sorted_key((SELECT array_agg(dmetaphone(t)) FROM unnest(string_to_array(:'q', ' ')) t)))
ORDER BY s.entity_version_id LIMIT 501;
EXPLAIN (ANALYZE, TIMING ON) SELECT DISTINCT s.entity_id, s.entity_version_id
FROM watch_name wn JOIN screenable_watch_entity_version s ON s.entity_version_id = wn.entity_version_id
WHERE md5(horus_sorted_key(wn.phonetic_codes)) =
      md5(horus_sorted_key((SELECT array_agg(dmetaphone(t)) FROM unnest(string_to_array(:'q', ' ')) t)))
ORDER BY s.entity_version_id LIMIT 501;

\echo ## TRIGRAM candidate volume by threshold (distinct name rows matched)
SET pg_trgm.similarity_threshold = 0.3;
SELECT count(*) AS matches_at_0_3 FROM watch_name WHERE normalised_name % :'q';
SET pg_trgm.similarity_threshold = 0.4;
SELECT count(*) AS matches_at_0_4 FROM watch_name WHERE normalised_name % :'q';
SET pg_trgm.similarity_threshold = 0.5;
SELECT count(*) AS matches_at_0_5 FROM watch_name WHERE normalised_name % :'q';
SET pg_trgm.similarity_threshold = 0.6;
SELECT count(*) AS matches_at_0_6 FROM watch_name WHERE normalised_name % :'q';
