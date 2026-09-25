# ADR-003 — Blocking (candidate generation) index strategy

- **Status:** Proposed
- **Date:** 2026-09-24
- **Review:** Solution Architect

## Context

Recall lost at stage 1 can never be recovered downstream (spec §15.1), so candidate generation must
be generous — the **union** of strategies, never the intersection (I-2). It must never return a
bare set of ids: a lookup that could not finish must say so (`BlockingOutcome`, I-1). The index
must see de-listed entities (I-8) and only the current version of each (ADR-001). The spec's KPI
is p95 ≤ 500 ms per screening (§31).

## Options considered

1. **PostgreSQL co-located: `pg_trgm` GIN + md5 expression indexes on the same tables (chosen).**
   One system, always consistent with the data it indexes; nothing to synchronise, promote or fail
   over separately.
2. **A dedicated search engine** (e.g. an inverted-index service). Potentially faster at scale;
   adds a second store that can be out of step with the watchlist — a new fail-open path — plus
   operational and licensing surface for a 10-day iteration.
3. **In-memory index in the application process.** Fast, but rebuilt on every start, bounded by
   heap, and invisible to the database roles that segregate duties.

Chosen strategies, all in `CoLocatedIndexAdapter`: `EXACT_HASH` (md5 of the normalised name, or of
the sorted-token form so "John Smith" = "Smith John"), `TRIGRAM` (`%` operator), `PHONETIC` (md5
of the sorted Double Metaphone codes). Each fetches `limit + 1` rows so a cap is reported as
`TRUNCATED` rather than silently returned as complete.

## Evidence

**A. Recall of the strategies, measured in isolation** (CLI `measure-blocking`, five synthetic
labelled cases against the live database):

| Strategy | Recall |
|---|---|
| EXACT_HASH | 0.60 (3/5) |
| TRIGRAM | 1.00 (5/5) |
| PHONETIC | 1.00 (5/5) |
| **Union** | **1.00 (5/5)**, mean candidate set 1.8 |

Exact-hash alone misses spelling variants; the union recovers them. Five cases prove the
mechanism, not the recall of the system.

**B. Latency at 500,000 entities / 600,000 names** (PostgreSQL 16, the exact SQL the adapter runs,
median of 4–5 runs, warm cache, local Docker, parallel workers enabled; synthetic names from a
deliberately small syllable vocabulary, so name collisions are far denser than in real data —
a stress case):

| Strategy | exact name | one-char typo | most common name (11 occurrences) |
|---|---|---|---|
| EXACT_HASH | 0.15 ms | 0.13 ms | 0.41 ms |
| PHONETIC | 0.44 ms | 0.33 ms | 0.82 ms |
| TRIGRAM (threshold 0.3) | 340 ms | 226 ms | 216 ms |

**Variance caveat:** timings moved 2–3× between sessions on the same query and data (the same
one-character-typo trigram query took 79–107 ms in a later re-run of
`scripts/scale-test/02-time-blocking.sql` against 226 ms above), presumably cache and parallel-worker
state. Read the table as an order of magnitude — sub-millisecond for exact and phonetic, tens to
hundreds of milliseconds for trigram at 0.3 — not as a benchmark result.

All three used their index. Index sizes: trigram GIN 27 MB, md5 btree 27 MB, sorted-token btree
18 MB, phonetic btree 12 MB (`watch_name` heap 123 MB).

**C. Trigram cost is governed by the similarity threshold** (same dataset, one query, median):

| `pg_trgm.similarity_threshold` | latency | distinct entities matched |
|---|---|---|
| 0.3 | 225 ms | 4,330 |
| 0.4 | 78 ms | 559 |
| 0.5 | 38 ms | 179 |
| 0.6 | 39 ms | 36 |

Time is dominated by heap fetch and recheck of the matching rows, not by the index lookup, so it
scales with matches. **The recall cost of raising the threshold was not measured** — that needs the
real labelled corpus.

**Not measured:** anything at the real 5.99 M scale (≈12× the above); trigram behaviour on real
names (far higher entropy, so likely fewer matches than this stress case, but unproven); the
end-to-end p95 including scoring; behaviour under concurrent load.

## Decision

Option 1, with all three strategies in the union. The default threshold stays at 0.3 (the
recall-favouring end) and the trigram threshold, the per-strategy cap and the strategy list are
configuration read at boot (`HORUS_BLOCKING_TRIGRAM_THRESHOLD`, `HORUS_BLOCKING_MAX_CANDIDATES`,
`HORUS_BLOCKING_STRATEGIES`); an unsupported strategy or invalid value fails the boot (I-7).

## Consequences

- A failed or incomplete lookup is `PARTIAL` and makes the subject `ERROR`; a `TRUNCATED` search
  that finds nothing is also `ERROR` (it cannot assert a clear).
- **Risk to carry into the readiness review:** at 0.3 the trigram lookup alone may exceed the 500 ms
  KPI on the full feed. The levers, in order: measure on real data; raise the threshold to the
  lowest value that still meets Compliance's blocking-recall floor; push the entity-status join
  after the trigram filter; partition or pre-filter by entity type.
- The `EXACT_HASH` and `PHONETIC` lookups order by version id, not relevance, so if they ever
  truncate the cut is arbitrary. Not observed at this scale; flagged.
- To re-run the measurement: `docs/runbook/testing-guide.md` §11.
