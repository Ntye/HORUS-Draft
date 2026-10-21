# ADR-003 — Blocking (candidate generation) index strategy

- **Status:** Proposed
- **Date:** 2026-09-24
- **Revised:** 2026-10-21 — options re-opened for the multi-month scope (see the last section)
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
  lowest value that still meets Compliance's blocking-recall floor; ~~push the entity-status join
  after the trigram filter~~ (measured 2026-09-27 and **falsified** -- see below); partition or
  pre-filter by entity type. **Measured 2026-09-27: the risk is real and the KPI is missed at the
  shipped threshold.**
- The `EXACT_HASH` and `PHONETIC` lookups order by version id, not relevance, so if they ever
  truncate the cut is arbitrary. Not observed at this scale; flagged.
- To re-run the measurement: `docs/runbook/testing-guide.md` §11.

## Measurement on the full feed — 2026-09-27

The risk carried above is now measured. ACTIVE list: 5,990,394 entities, 10,570,243 names,
3.1 GB of `watch_name` indexes, `shared_buffers=2GB`. Probe name invented, of a deliberately
high-fanout shape. Warm cache, nothing else running, `EXPLAIN ANALYZE` so no row contents were read.

**The other two strategies are not the problem.**

| Strategy | Cold | Warm |
|---|---|---|
| `EXACT_HASH` (rare name) | 1.9 ms | 0.5 ms |
| `EXACT_HASH` (common pattern) | 8.3 ms | 0.5 ms |
| `PHONETIC` | 237.9 ms | 6.3 ms |

**Trigram against the similarity threshold**, same probe, `LIMIT 501` as shipped:

| Threshold | Name rows scanned | Latency | NFR-001 p95 (500 ms) |
|---|---|---|---|
| **0.30 — as shipped** | 37,410 | **1,444 ms** | **fails, 2.9x over** |
| 0.40 | 9,620 | 779 ms | fails |
| 0.50 | 2,260 | 493 ms | meets, with no headroom |
| 0.60 | 596 | 308 ms | meets |

A rare, high-entropy name at 0.45 took 85 ms, so the ADR's guess above — that real query names have
far higher entropy and will be cheaper — holds. The failure is specific to common name patterns, which
are exactly the ones a screening system cannot afford to be slow on.

**Lever 3 is falsified.** "Push the entity-status join after the trigram filter" does not work, because
the screenability view is not the cost:

| Variant | Latency |
|---|---|
| As shipped (join inside) | 1,444 ms |
| Trigram scan alone, no screenability join at all | 1,483 ms |
| Join applied after the trigram top-N (the proposed remedy) | **1,744 ms** |

Removing the join entirely saves nothing, and applying it after the top-N is 21% *slower* — the CTE
materialises and loses the parallel plan the shipped query gets. The cost is the trigram scan itself:
37,410 rows fetched from a 7.2 GB heap with `similarity()` computed on each. Lever 3 should be struck
from the list rather than carried forward as an option.

**What this leaves.** The threshold is the only measured lever, and it trades directly against recall —
which is the one trade I-2 forbids making blind. Raising 0.30 to 0.50 buys a 2.9x latency improvement
and costs an unknown amount of recall. **That number does not exist**, because blocking recall on real
data needs a labelled corpus and none has been built (D12 B-1).

Two things make this less grim than it looks, both untested:

1. **Blocking is a union.** Recall lost by raising the trigram threshold may be recovered by
   `EXACT_HASH` and `PHONETIC`, which cost 0.5 ms and 6.3 ms. The union's recall at threshold 0.55
   could equal its recall at 0.30. Per-strategy recall is what settles it, and
   `MeasureBlockingRecall` already reports exactly that.
2. **`shared_buffers=2GB` against 3.1 GB of indexes on a 7.2 GB table.** The dominant cost is the
   bitmap heap fetch, and the host has 31.5 GB. Raising `shared_buffers` is an operational lever that
   costs no recall at all, and it has not been tried.

**Do not raise the threshold to meet the latency KPI until the recall curve exists.** That would be
buying a measurable number with an unmeasured one, in the direction I-2 names as a regulatory breach
rather than a cost.

To re-run: `docs/runbook/testing-guide.md` §11.

## Revision 2026-10-21 — options re-opened for the multi-month scope

**Status stays Proposed.** Option 1 remains the shipped baseline. This section does **not** change
the decision. It re-opens the choice because three facts have changed since 2026-09-24:

1. **The iteration is no longer 10 days but several months.** The original reasons for rejecting
   Options 2 and 3 included "operational and licensing surface for a 10-day iteration". That reason
   no longer holds, so those options have to be judged on evidence.
2. **The full-feed measurement above shows the KPI is missed at the shipped threshold.** The
   trigram lookup took 1,444 ms at 0.30 (D12 H-6). The only measured lever, the threshold, trades
   against recall.
3. **The source is moving from a static file to the vendor's API.** Updates will arrive as frequent
   deltas. Whatever serves blocking must take a delta within 30 minutes, atomically and without
   interrupting service (NFR-005, NFR-012). The licence position for in-house use is confirmed.

### Options now under consideration

All of them sit behind the existing `BlockingIndex` port and keep its contract: `COMPLETE` /
`TRUNCATED` / `PARTIAL`, union of strategies, sorted output, bound to a `listVersionId`. Any of
them is a change confined to `horus.adapter.index`.

| | Option | What it is | Recall cost | New dependency | Status |
|---|---|---|---|---|---|
| **1a** | PostgreSQL, tuned | Option 1 as shipped, with memory sized to the index: `shared_buffers` and `effective_cache_size` raised so the 3.1 GB of `watch_name` indexes and the hot part of the 7.2 GB heap stay resident (host has 31.5 GB) | **None** | No | **Untried. Cheapest test, run first.** |
| **1b** | PostgreSQL, higher trigram threshold | Raise `HORUS_BLOCKING_TRIGRAM_THRESHOLD` from 0.30 | **Unknown.** Needs the recall curve (B-1). | No | Measured for latency only. Blocked on recall. |
| **3'** | In-memory index per list version | Built read-only from PostgreSQL (as `horus_screen`) when a version is promoted, then swapped in with one pointer change after reconciliation against database counts. Fuzzy matching runs over the **vocabulary of distinct normalised tokens**, not over all 10.57 M names, then expands through postings. Also: exact, phonetic and identifier hash maps, plus a joined-tokens key so that "al sayed" and "alsayed" meet. | To be measured per case class. Token-level search misses joins, splits and partial tokens unless the extra keys cover them. | No | Not built. Challenger if 1a fails. |
| **4** | Embedded Apache Lucene | One index per list version, memory-mapped. Fuzzy and n-gram queries return **candidates only**; Lucene's own relevance scores are discarded, because scoring stays in `horus.matching` (I-3). | Fuzzy limited to edit distance ≤ 2 per term | Yes, Apache 2.0. Needs approval (CLAUDE.md §3). | Only if 3' fails or costs too much to own. |
| 2 | External search cluster (OpenSearch / Elasticsearch) | As in the original options | — | Yes, plus a platform | Last resort, unchanged (spec §23: "evidence first, not by default"). |

**The original objections to Option 3 still apply to 3' and 4:**

- **"Rebuilt on every start".** A replica must answer `PARTIAL` until its index is loaded and bound
  to the active version. A snapshot saved per list version shortens cold start. Cold start is
  measured, not assumed.
- **"Bounded by heap".** Memory is unmeasured. Two inputs are still missing: names per entity
  (≈ 1.76 from the counts above) and **the size of the distinct token vocabulary**. A version swap
  needs room for two indexes at once.
- **"Invisible to the database roles".** The index is a derived, read-only copy built through
  `horus_screen`'s SELECT grants. It cannot write anything. Segregation of duties is unchanged,
  because the roles govern the *store*, which stays in PostgreSQL.
- **New:** replicas can briefly serve different list versions. That is acceptable only because
  every screening records its `listVersionIds` (I-12), and it must be visible on monitoring.
- **New:** API deltas need a base index plus a small overlay of changes, with periodic compaction.
  That is more code to own and test.

**A result cache is not an option for blocking.** The spec's deployment view (§12.3, §23) includes
a hot-query cache (ElastiCache). It does not generate candidates, so it cannot replace any option
above.

- Screened names are mostly unique, so the hit rate is expected to be low (unmeasured).
- A cached result is valid only for an exact
  `(normalised query, listVersionIds, configVersionId, pipelineVersion)` tuple. A key missing any
  of these serves a stale result: risk R-6 and a breach of I-12.

If a cache is proposed, it needs its own ADR with a measured hit rate. **This departs from the
spec's deployment view and is flagged for the Solution Architect.**

### Order of experiments

1. **Build the labelled blocking corpus** (D12 B-1). Everything below is judged on recall first, and
   no recall figure on real data exists yet. Case classes:
   - typo
   - transliteration variant
   - word order
   - joined or split tokens
   - missing middle token
   - initials
   - short names (≤ 2 tokens or ≤ 4 characters)
   - organisation with generic tokens
   - non-Latin input
2. **Measure the recall curve.** Per-strategy and union recall at trigram thresholds 0.30 / 0.40 /
   0.50 / 0.60 with `measure-blocking`. This settles whether `EXACT_HASH` + `PHONETIC` recover what a
   higher threshold loses (lever (a) in D12 H-6).
3. **Option 1a.** Raise `shared_buffers`, then re-run testing-guide §11 at 0.30. If p95 meets the
   budget below with no threshold change, stop here: Option 1 stands and this ADR moves to Accepted.
4. **Option 3'** behind the same port, only if 1a and the recall curve together cannot meet the
   budget. Run the same protocol on the same data.
5. **Option 4** only if 3' fails. Option 2 only if both fail.

**Measured for every option** (testing-guide §11, extended):
- p50/p95/p99 at concurrency 1 and at 50 req/s sustained (NFR-002)
- 100,000-name batch throughput (NFR-003)
- candidate set size (mean, p95, max) and the `TRUNCATED` rate
- resident memory, and peak memory during a version swap
- full-version build time, and time to apply a delta (NFR-005)
- cold start time
- byte-for-byte determinism across two runs
- that "index not ready", "index bound to a superseded version" and "one strategy throws" each
  yield `PARTIAL`

### Selection criteria (proposed, to be confirmed at review)

| Order | Criterion | Rule |
|---|---|---|
| 1 | Recall | Union recall on the real corpus ≥ **Compliance's recall floor** (still open, spec §29 Q2). Every miss is explained individually. An option below the floor is rejected, however fast. |
| 2 | Latency | Blocking p95 ≤ **150 ms**, p99 ≤ **300 ms** at 50 req/s on the full list. This leaves the rest of NFR-001's 500 ms / 1 s for normalisation, candidate assembly, scoring and persistence. *The split is a proposal, not a requirement.* |
| 3 | Operability | A delta applied within 30 min (NFR-005). Memory and cold start fit the target container. |
| 4 | Tie-break | Fewer moving parts: **1a over 1b over 3' over 4 over 2.** |

### Related gaps this revision does not close

- **M-5:** blocking settings are environment variables, not part of `config_version`, so a screening
  does not record the threshold it ran with (I-12). Whatever option wins, its tunables belong in the
  versioned config.
- **M-6:** `EXACT_HASH` and `PHONETIC` truncate in version-id order. Option 3' would inherit the same
  question.
- **M-7:** no concurrency testing. The 50 req/s measurement above is the first one.

### Open inputs

- **Compliance:** the recall floor. This blocks Acceptance.
- **Project owner:** the distinct token vocabulary size, from profiling. This sizes 3' and 4.
- **DevOps:** memory available per replica, and for PostgreSQL, in the target environment.
- **Solution Architect:** the latency split, the tie-break order, and the hot-query cache position.
