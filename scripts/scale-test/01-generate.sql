-- SYNTHETIC scale dataset for measuring blocking latency (ADR-003). Every name is invented.
-- Run against a SCRATCH database that already has the project's migrations applied and the
-- pg_trgm and fuzzystrmatch extensions (see docs/runbook/testing-guide.md section 11).
-- Never run this against the `horus` database: it would pollute the working data.
--
-- 500,000 entities, 600,000 names (one alias in five). Names are built from a small syllable
-- vocabulary, so name collisions are much denser than in real data: this is a STRESS case for the
-- trigram index, not a model of the real feed. Fixed seed => the same rows every run.
\timing off
SELECT setseed(0.42);

INSERT INTO list_version (list_version_id, source_id, format_id, source_file_name, source_file_checksum,
    loaded_at, record_count, status, loaded_by, pipeline_version)
VALUES ('00000000-0000-0000-0000-000000000001', 'scale', 'f', 'f.xml', 'sha-scale', now(), 500000,
    'ACTIVE', 'test', '1.0.0');

CREATE TEMP TABLE gen AS
SELECT g AS n,
  (ARRAY['zor','tal','quil','ost','hark','bel','dro','fen','gar','hal','kes','lor','mev','nyr','pell','wex','yar',
         'cor','dun','eld','ash','bram','cael','dorn','emm','fael','gwyn','hest','ind','jor'])[1 + floor(random() * 30)::int]
  || (ARRAY['van','mesk','lon','brek','rel','wood','feld','mere','dath','kor','lin','sund','thar','vex','mond',
            'wick','ton','ley','ford','stan'])[1 + floor(random() * 20)::int] AS given,
  (ARRAY['zor','tal','quil','ost','hark','bel','dro','fen','gar','hal','kes','lor','mev','nyr','pell','wex','yar',
         'cor','dun','eld','ash','bram','cael','dorn','emm','fael','gwyn','hest','ind','jor'])[1 + floor(random() * 30)::int]
  || (ARRAY['van','mesk','lon','brek','rel','wood','feld','mere','dath','kor','lin','sund','thar','vex','mond',
            'wick','ton','ley','ford','stan'])[1 + floor(random() * 20)::int] AS family,
  gen_random_uuid() AS entity_id,
  gen_random_uuid() AS version_id
FROM generate_series(1, 500000) g;

INSERT INTO watch_entity SELECT entity_id, 'scale', 's-' || n FROM gen;

INSERT INTO watch_entity_version (entity_version_id, entity_id, list_version_id, content_hash, entity_type,
    primary_name, status)
SELECT version_id, entity_id, '00000000-0000-0000-0000-000000000001', 'h' || n, 'INDIVIDUAL',
       initcap(given) || ' ' || initcap(family), 'ACTIVE'
FROM gen;

-- dmetaphone() comes from fuzzystrmatch; the application uses commons-codec's Double Metaphone,
-- which agrees closely but is not byte-identical. That is fine for measuring index performance.
INSERT INTO watch_name (name_id, entity_version_id, name_type, raw_name, normalised_name, name_tokens,
    phonetic_codes, trigrams, script)
SELECT gen_random_uuid(), version_id, 'PRIMARY', initcap(given) || ' ' || initcap(family),
       given || ' ' || family, ARRAY[given, family], ARRAY[dmetaphone(given), dmetaphone(family)], '{}', 'LATIN'
FROM gen;

INSERT INTO watch_name (name_id, entity_version_id, name_type, raw_name, normalised_name, name_tokens,
    phonetic_codes, trigrams, script)
SELECT gen_random_uuid(), version_id, 'ALIAS', initcap(family) || ' ' || initcap(given),
       family || ' ' || given, ARRAY[family, given], ARRAY[dmetaphone(family), dmetaphone(given)], '{}', 'LATIN'
FROM gen WHERE n % 5 = 0;

ANALYZE;

SELECT (SELECT count(*) FROM watch_entity) AS entities,
       (SELECT count(*) FROM watch_name) AS names,
       (SELECT count(DISTINCT normalised_name) FROM watch_name) AS distinct_names;

SELECT pg_size_pretty(pg_relation_size('idx_watch_name_normalised_trgm')) AS trgm_gin,
       pg_size_pretty(pg_relation_size('idx_watch_name_normalised_md5')) AS md5_btree,
       pg_size_pretty(pg_relation_size('idx_watch_name_sorted_tokens_md5')) AS sorted_tokens_btree,
       pg_size_pretty(pg_relation_size('idx_watch_name_phonetic_md5')) AS phonetic_btree,
       pg_size_pretty(pg_relation_size('watch_name')) AS watch_name_heap;
