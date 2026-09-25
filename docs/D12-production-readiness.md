# D12 — Production readiness assessment

**Date:** 2026-09-24, revised 2026-09-25 (B-6 added after the first real load). **Verdict: NOT READY for production screening.**

This is a gap list, not a sales pitch. Each gap says what is missing, why it matters, and what
closing it requires. Severity: **BLOCKER** (must close before any reliance on HORUS) ·
**HIGH** (must close before production) · **MEDIUM** (close before scale-up) · **LOW** (accepted,
recorded).

The single sentence that governs this document: **match quality has never been measured on real
data**, so nothing here should be read as "nearly ready".

---

## 1. Blockers

### B-1 Match quality is unmeasured on real data
Recall and precision are unknown. Every figure in D11 comes from invented names. A screening system
whose recall is unknown cannot be relied on: a false negative is a regulatory breach (I-2).
**To close:** load the real feed (plan Step 6); build a labelled corpus with Compliance (target 200
cases; the spec §19.3 asks for **1,000** — the gap between those numbers is itself a limitation on
any conclusion drawn); run `benchmark`; review **every** missed true positive individually.

### B-2 No approved operating point
The shipped thresholds (alert 55, strong 75) are placeholders tuned so the spec's illustrative
examples land in their stated bands. They carry no information about real alert volume.
**To close:** run the threshold sweep on the real corpus, present recall *and* the resulting alert
volume and its operational cost to Compliance (spec §16.4), and publish their decision with
`publish-config --file … --approved-by <name>`.

### B-3 ~~Dependency vulnerability posture unknown~~ — CLOSED 2026-09-24
**Resolved during this iteration, but recorded because the failure mode is instructive.** The CI
dependency gate was **vacuous**: `dependencyCheckAnalyze` runs against the root project, which
declares no dependencies, so it reported "Found 0 vulnerabilities" over *"Dependencies Scanned: 0"* —
a green tick that proved nothing. It would not have failed on a finding either.

Fixed to `dependencyCheckAggregate` with `failBuildOnCVSS = 7.0`. The first honest run scanned 72
dependencies and found **3 vulnerabilities, 2 of them HIGH**: `icu4j` 75.1 (CVE-2025-5222) and
`commons-lang3` 3.14.0 via `commons-text` (CVE-2025-48924) — both **fixed by upgrade** (icu4j 77.1,
commons-text 1.15.0 → commons-lang3 3.20.0), with all tests still passing and normalisation
output unchanged. The third (`org.jacoco.ant`, CVE-2026-78254) is a false CPE match on build-time
tooling and carries a scoped, written suppression. **Current state: 0 vulnerabilities, gate proven
to fail.**

Residual, carried as **LOW**: set the `NVD_API_KEY` secret in CI (the workflow passes it already) or
every build repeats the ~41-minute NVD download; and review the JaCoCo suppression whenever JaCoCo
releases past 0.8.14.

**The lesson generalises: a green scanner is not evidence until you have read what it scanned.**

### B-4 Two known match-quality defects, unresolved by design
Both are measured and reproducible on synthetic data (ADR-002):
- **Short-token near-misses are indistinguishable from true variants.** "Ali Hassan" vs "Alia
  Hassan" scores 80, *above* the genuine variant pairs at 78 and 71. No threshold separates them.
- **Organisation scoring is not calibrated.** "Africa Trading Company" vs "East Africa Trading Co"
  lands exactly on the strong threshold; generic-token down-weighting is too weak.

**To close:** measure both against the real corpus, then either tune (distinctiveness weighting,
token-length-sensitive phonetics, a separate organisation weight set) or accept them explicitly with
Compliance as a stated precision cost per case class.
### B-6 The source adapter's element paths were never validated against the real feed
**Found by the first real load, 2026-09-25: 5,979,934 of 5,990,394 records rejected (99.83%).**
`WorldCheckAdapter` reads paths such as `person/first_name`, `date_of_birth/year` and
`identification/passport`. Those were invented alongside the synthetic fixture, which admits as much
in its own header, because section 6 documents the feed's *signals* and coverage but never its
*element paths*, and section 3 forbids reading the feed to find out. Every test passed; the fixture
and the adapter simply agreed with each other.

Three things this exposed, each worth closing on its own:

1. **No record-level name means rejection, and that is the right behaviour** — `CanonicalRecordBuilder.build()`
   throws when no name was added — but it is reached through an invented path rather than a verified one.
   The 10,460 records that *did* load are worse than the rejections: they got past `build()` on an
   **alias extracted from narrative text**, with `PRIMARY_NAME` marked absent. A record with no primary
   name is nearly unfindable by name blocking, so those are silent recall holes, not partial successes.
2. **`WatchlistSourceAdapter.requiredSlots()` is a dead control.** It is declared, and
   `WorldCheckAdapter` returns `{PRIMARY_NAME, ENTITY_TYPE}`, but nothing in production code ever
   reads it — enforcement happens incidentally, via `build()`'s `names.isEmpty()` check. Entity type is
   not enforced at all. This is the same class of failure as B-5: a control that exists in the model
   and nowhere in the path the application takes.
3. **The rejection reason is discarded.** `LoadWatchlistVersion` records
   `e.getClass().getSimpleName()`, so 5.98 M distinct failures all read `IllegalStateException`, and
   `IngestCommand` prints only `rejected.size()`. A three-hour load therefore reported *that* it
   failed and not *why* — the diagnosis needed a code reading rather than the report. The reasons are
   also retained in full in memory, one object per rejected record.

**To close:** inventory the real structure with `scripts/New-FeedPathInventory.ps1` (structural
metadata only — path names, coverage counts, and the `@category` / `@e-i` code vocabularies, which
also closes the invented `E_CATEGORY_TYPES` placeholders); correct the adapter against that evidence;
rebuild the fixture to the real shape so the tests stop confirming a fiction; enforce `requiredSlots()`
in `LoadWatchlistVersion`; and carry a controlled reason code through `RejectedRecord` into an
aggregated CLI report, with bounded retention.

**Status, 2026-09-25 (same day).** Done: the real structure was inventoried with
`scripts/New-FeedPathInventory.ps1`; `WorldCheckAdapter` was corrected against it, its mapping made
total so no feed value can quarantine a record by throwing (`1939-00-00` is a year-only birth date
and `LocalDate.parse` rejects it); **aliases are now mapped at all** — `person/names/aliases/alias`
is populated on 81.5% of the head-of-file slice and was previously ignored entirely, which was the
largest single recall gap in the system; `E_CATEGORY_TYPES` now holds the observed vocabulary;
`requiredSlots()` is enforced in `LoadWatchlistVersion`; rejection reasons are controlled codes
aggregated into the CLI report with example ordinals; and the fixture was rebuilt at the real depth
behind a new `WorldCheckAdapterTest` (18 cases). Full build green: **478 tests, 0 failures**.

Still open, and none of it provable without another real load: the four E-categories that the
head-of-file slice never showed (AIRCRAFT, WEBSITE, PORT, ADDRESS) are inferred from §6's naming,
not observed; `RejectedRecord` retention is still one object per rejected record; `RawRecord` holds
attributes in a flat map so only the last of several `<location>` elements survives; the
`watch_name.raw_name` 1000-character bound is enforced in the adapter rather than in the domain
value object, contrary to §11; and mapping every alias multiplies `watch_name` inserts — which made
throughput a first-order question, answered under M-3.

**The lesson generalises, and it is B-5's lesson again in a different place: a test is only as good as
the fixture's claim to resemble reality.** Everything grounded in section 6 held — the record count,
the element name, `@e-i`, the streaming configuration. Everything invented to fill the gaps around it
failed on contact.

### B-5 ~~Segregation of duties not in effect at runtime~~ — CLOSED 2026-09-24
**Resolved during this iteration; recorded because it is the most instructive failure found.** Every
repository connected as `horus_admin` instead of its own role: the `JdbcTemplate` beans relied on the
parameter NAME to pick among four `DataSource` beans, and `@Primary` (`adminDataSource`) outranks
name matching. The GRANTs were correct, and the negative-privilege tests passed — but those tests
build their own connections, so nothing checked what the application actually used. Every load and
screening in this iteration therefore ran with schema-owner privileges.

Fixed with `@Qualifier` on every template and `@Primary` removed, so an unqualified `DataSource`
injection now fails at startup rather than silently escalating privilege.
`JdbcTemplateRoleWiringTest` asserts `current_user` for each template and was confirmed to fail
before the fix. The full flow was then re-run on the restricted roles: everything works and **no
GRANT was missing**.

**The lesson generalises: a control is not in force until something asserts the path the application
actually takes.** Ask it of the remaining controls — H-4 especially.

---

## 2. High

### H-8 Country values are names, and the comparator is fed them as codes
Found on the second real load attempt, 2026-09-25. The feed writes country **names** --
`details/countries/country` and `location@country` hold "UNITED ARAB EMIRATES", not "AE". The
candidate view selects `COALESCE(iso_code, upper(raw_country))`, `iso_code` is never populated, and
so `CountryComparator` receives names. The CLI documents `--country` as "Country code".

A consumer screening with `--country NG` against a candidate whose country reads `NIGERIA` therefore
gets **no overlap**, and no overlap is not neutral: the comparator applies its *conflict* effect and
demotes a genuine match. Recall is the binding constraint (I-2), so a country filter that silently
penalises true positives is worse than no country filter at all.
**To close:** decide with Compliance what `--country` means to a consumer, then either normalise the
feed's names to ISO codes at ingest (populating `iso_code`, which exists for exactly this) or accept
both forms and compare insensitively. Either way, measure the effect on the benchmark before
publishing an operating point (B-2).

### H-9 ~~A failed record write leaves an orphan entity and poisons the next attempt~~ — CLOSED 2026-09-25

Found on 2026-09-25, then **observed twice**: 12 rejections on a retry of the 200-record extract, and
then **100,000 rejections out of 100,000** on the throughput re-measurement, which void the run
entirely — every record failed on its identity insert, so the batched child writes under test never
executed once.

The aborted load left `watch_entity` rows against fewer `watch_entity_version` rows, and a
`list_version` stuck at `STAGED`. Retrying without resetting rejected exactly the records the previous
attempt had already reached, with `SQL_23505:watch_entity_source_id_source_entity_id_key`. The mapping
was correct; the failure was entirely self-inflicted by the previous run.

**The reasoning error, stated plainly.** Reconciliation keys on **active versions**; identity is
permanent and independent of version status (§5). `current_watch_entity_version` deliberately excludes
`FAILED` and `STAGED` loads, so after a failed load the identity row exists while the entity is
invisible to `loadActiveSnapshot`. The `ADD` branch read "absent from the snapshot" as "safe to insert
identity" — an inference that holds only if no load has ever failed. Re-listings were never affected:
the snapshot carries a `delisted` flag, so those take `AMEND`.

**Fixed.** `WatchEntityRepository.save` became `EntityId ensure(WatchEntity)`:
`INSERT ... ON CONFLICT (source_id, source_entity_id) DO NOTHING`, then select, returning the id the
version must reference. The loader adopts whatever comes back, so a retry after a failure reuses the
identity that is already there. No migration: the constraint already existed. `DO NOTHING` rather than
`DO UPDATE`, so nothing is rewritten and I-6 holds — and the proof is a privilege check, not a comment:
`ensure` succeeds on the `horus_ingest` connection, which has only SELECT and INSERT on the table, and
a test asserts that role still lacks UPDATE.

Four Testcontainers tests cover it, on the ingest role's own connection because the behaviour under
test is the unique constraint's: an existing identity is adopted, a new one keeps the id it was
offered, the same record id under a different source stays a different entity, and no UPDATE privilege
is needed. `FakeWatchEntityRepository` now enforces the same uniqueness rule — a fake that simply
appended is why this escaped in the first place, and the application-level test of a retry after an
unpromoted load fails against the old fake.

**Also fixed, because this defect was diagnosable at record 0 and cost three minutes to discover on an
extract and would have cost hours on the feed:** a load is now abandoned once the first 1,000 records
have produced rejections and **zero** successes, reporting the dominant reason code
(`abandoned after 1000 records, none of which succeeded; most common reason …`) instead of the generic
"more than 5% of records failed validation". Requiring zero successes makes a false trip effectively
impossible, and the verdict is the one the reject gate would have reached anyway — `REJECTED`, nothing
promoted, the previous version still serving (I-1). The check sits at the **top** of the record loop:
every quarantine path `continue`s, so a check at the bottom would never be reached by a load failing
at the mapping stage, which is the commonest way for a whole file to fail (B-6).

**Still open, and deliberately not fixed here:** an aborted load leaves its `list_version` at `STAGED`
rather than `FAILED`, so nothing records the outcome. It is now cosmetic rather than harmful — the
view excludes `STAGED`, and the retry no longer collides — but the rows accumulate, and a stuck
`STAGED` row is a misleading thing for an operator to find. Tracked as M-11.


### H-10 ~~A capability deviation discarded the entire load~~ — CLOSED 2026-09-25

Found while answering an operator's question about a measurement run, which is the only reason it was
found before the real load rather than after it.

`LoadWatchlistVersion` marked a capability deviation `FAILED`, and `PromoteListVersion` accepts
`STAGED` only — nothing moves a `FAILED` version back. So a deviation threw away a completed,
consistent load: on the full feed, two to four hours of work, over a figure a person has to rule on
anyway. The 100,000-record measurement run reproduced it exactly:

```
added=100000 amended=0 unchanged=0 delisted=0 rejected=0
failureReason: measured capabilities fell outside tolerance:
  [IDENTIFIER expected=0.0074 actual=0.07489 tolerance=0.005,
   DATE_OF_BIRTH expected=0.27 actual=0.73133 tolerance=0.05]
```

`rejected=0`: the mapping was correct and every row was written. The deviation is explainable — the
section 6 figures are whole-file properties, an extract is a contiguous head slice, and the head of
this feed is far richer in dates of birth and passports than the file as a whole. But the same
question would arise on the full feed for a different reason (the profiled snapshot is not the
snapshot being loaded), and the `IDENTIFIER` band is narrow: 0.0074 ± 0.005.

**Fixed:** a deviation now HOLDS the load as `AWAITING_CONFIRMATION`, the outcome already used for an
anomalous delta. The rows stay `STAGED`; the version-row count check has already passed; an operator
resolves it with `confirm`.

Still closed by default, and the distinction is worth stating because it is the one that makes this
safe rather than a loosening: `STAGED` does not screen, the previous `ACTIVE` version keeps serving,
and only `ConfirmStagedVersion` can promote it. A capability deviation says *the file is not the shape
we expected* — a judgement. It does not say *the data written is wrong*; the count check and
`rejected=0` speak to that separately.

**`ConfirmAnomalousDelta` became `ConfirmStagedVersion`,** because recording a capability decision
under an event named `ANOMALOUS_DELTA_CONFIRMED` would have put a false statement in the append-only
audit trail, which is the artefact Internal Audit tests (I-5). Events are now
`STAGED_VERSION_CONFIRMED` / `STAGED_VERSION_REJECTED`, carrying `awaiting` (`ANOMALOUS_DELTA` or
`CAPABILITY_DEVIATION`) and a **mandatory reason**. That reason is not ceremony: this is the only path
by which a person overrides a control that has just fired, and an override with no recorded
justification is indistinguishable from not having the control — the same gap H-5 records for
configuration approval. `ConfirmCommand` therefore requires `--awaiting` and `--reason`.

Four tests: the deviation holds with rows written and status `STAGED`; an operator promotes the held
load without re-running it; a blank reason is refused; and the existing delta tests still pass under
the new names.

### H-1 The identifier path cannot *find* a candidate
An exact passport or registration match forces a strong match — but only for candidates that
**name** blocking already surfaced. Spec §15.1 lists identifier lookup as a direct
high-confidence retrieval path; it is not built. A query with a correct identifier but an
unrecognisable name is not retrieved.
**To close:** add an identifier strategy to `BlockingIndex` (the index and normalised column already
exist). Note the feed carries a structured identifier on only 0.74 % of records, so the benefit is
narrow but the failure mode is severe.

### H-2 No homoglyph / confusable-character handling
The normalisation pipeline has no confusable-character step (documented in `TransliterationStep`).
"M0hammed Al-Sayed" vs "Mohammed Al-Sayed" scores 74 — one point *below* the placeholder strong
threshold — surviving only on string-level similarity. This is the adversarial case: someone
deliberately evading screening will use exactly this substitution.
**To close:** add a confusables step (ICU provides the data), with its own tests, and re-measure
over-normalisation guards (`Ali` must still not equal `Alia`).

### H-3 Whitelist is a seam, not a feature
The domain entity and the engine's flag-and-demote behaviour exist and are tested, but there is no
storage, no CRUD, no approval, and the runtime always supplies an empty view. Whitelisting is how
analysts stop re-reviewing the same false positive; without it, alert volume never falls.
**To close:** whitelist tables, an interactor, and version-staleness handling (§21 — an entry is
automatically inactive when the entity's version changes; the engine already implements this).

### H-4 The audit trail is not tamper-evident
`audit_event.previous_hash` exists in the schema and the domain object but is **always empty**: no
hash chain is computed. Append-only GRANTs stop the application from editing evidence; they do not
detect an edit made with database-level access.
**To close:** compute each event's hash over its content plus the previous event's hash, and add a
verification query for Internal Audit. Decide with them whether per-subject or global chaining is
required.

### H-5 Configuration has no second-person approval
`publish-config --approved-by <name>` records a claim made by whoever runs the command; nothing
verifies that person approved it. Segregation of duties is enforced for *data* but not for the
*policy* that governs scoring.
**To close:** an approval step with a distinct identity (part of the descoped admin surface), or an
interim documented control — e.g. only a named release process may run `publish-config`.

### H-6 Latency at real scale is unproven, and the trigram path is the risk
The KPI is p95 ≤ 500 ms (spec §31). At 500k entities — 12× smaller than the real feed — trigram
blocking alone took 216–340 ms at threshold 0.3, and the cost scales with the number of matching
rows. The other two strategies are sub-millisecond.
**To close:** measure on the real load. Levers, in order: raise the threshold to the lowest value
meeting Compliance's blocking-recall floor; push the entity-status join after the trigram filter;
partition or pre-filter by entity type. **Do not raise the threshold without measuring the recall
it costs.**

### H-7 CI has never executed
The workflow exists and third-party actions are now SHA-pinned, but no push has run it. All three
scanner results in D11 were produced locally, with the same rule sets and commands the workflow now
uses. The dependency gate has been *observed* failing the build on a high-severity finding (twice,
before the upgrades in B-3), so that gate is no longer merely asserted.
**To close:** push a branch and confirm all four jobs pass; set the `NVD_API_KEY` secret; and prove
the Semgrep and gitleaks gates fail the build on a deliberate finding, as the dependency gate now
demonstrably does.

---

## 3. Medium

| # | Gap | Why it matters | To close |
|---|---|---|---|
| M-1 | **Active-entity snapshot is held in memory** during a load (one entry per current entity: id + hash). | At 5.99 M entities this is a real heap cost and an unmeasured scaling risk. | Measure peak heap on the real load; if needed, stream reconciliation through a temporary table or sorted merge. |
| M-2 | **The feed file is read twice** per ingest (once to checksum, once to parse). | A structural pass alone took 229 s; this doubles it. | Checksum while streaming, and short-circuit after the fact — or accept and document the cost. |
| M-3 | ~~**Child rows are inserted one at a time.**~~ **MEASURED TWICE AND FIXED 2026-09-25** — 34 rec/s, then 60 rec/s batched per entity; the unit is now a chunk of 1,000 entities per transaction. Commit frequency alone measured at 158×. See below. | 47 h, then 28 h, for a 5.99 M-record first load. | Re-measure on the first 100,000 records (runbook Step 2c); defer the four `watch_name` index builds only if still under 300 rec/s. |
| M-4 | **`list_version.loaded_at` orders a source's loads but is not DB-enforced** as unique/increasing. | Two loads with the same timestamp would make "current version" ambiguous. | A unique index on `(source_id, loaded_at)`. Deferred because it alters an existing table (CLAUDE.md §13.6). |
| M-5 | **Blocking settings are environment variables**, not part of `config_version`. | A screening records its config version but not the blocking threshold it ran with — a provenance gap (I-12). | Move `HORUS_BLOCKING_*` into the versioned config payload. |
| M-6 | **`EXACT_HASH` and `PHONETIC` order candidates by version id, not relevance.** | If they ever truncate, the cut is arbitrary rather than worst-first. Not observed at current scale. | Order by a relevance proxy, or raise the cap and rely on `TRUNCATED`. |
| M-7 | **No concurrency testing.** | Behaviour under simultaneous screenings, and an ingest concurrent with screening, is unknown. | Load-test; confirm a promotion during screening cannot yield a torn view. |
| M-8 | **Ingestion is single-source in practice.** The model supports several sources and the union of their capabilities, but only World-Check exists. | Tier 2 mapped sources are descoped; multi-source screening is untested. | Covered by the descoped Tier 2 work. |
| M-9 | **No operational monitoring.** | No metrics, health endpoint use, alerting on load freshness (KPI: list freshness ≤ 24 h) or on ERROR rates. | Minimal metrics and a freshness check in the runbook. |
| M-10 | **`query_name` is stored in `screening_subject`.** | Necessary evidence, but it is personal data at rest with no encryption or retention policy in this iteration. | Agree retention and access control with Compliance and the DPO; consider column-level encryption. |
| M-11 | **An aborted load leaves its `list_version` at `STAGED`**, not `FAILED`. | Nothing records the outcome, and the rows accumulate; an operator finding a stuck `STAGED` row cannot tell it from a load still running. Cosmetic since H-9 closed — the current-version view excludes `STAGED` and a retry no longer collides. | Mark the list version `FAILED` before an abort propagates, and have the CLI say so. |
| M-12 | **`CapabilityDeriver` measures "not declared absent", not "present".** `CanonicalRecord.has(slot)` returns `!absentSlots.contains(slot)`, so a slot an adapter never mentions measures as **100% covered**. | The capability gate is the control that notices the feed changing shape. An adapter that silently stopped populating a slot, without calling `absent(slot, reason)`, would read as fully covered and the gate would stay silent — fail-open in the one control meant to catch exactly that (I-1, I-2). Not currently biting: `WorldCheckAdapter` does declare absence for the slots that matter, which is why the real run measured DOB at 0.73 and IDENTIFIER at 0.075 rather than 1.0. Found 2026-09-25 by a test that failed for this reason. | Distinguish "populated", "asserted absent" (`xsi:nil`) and "not mentioned" in the deriver, and treat "not mentioned" as 0% coverage — or require an adapter to declare every slot it knows about. Either changes the derived figures, so the expectations in §6 must be re-checked against a full profiling pass at the same time. |

### M-3 Child rows inserted one at a time — measured, then fixed 2026-09-25

The first real load answered the "unknown and likely poor" question: **34 records/second**, with the
loader's own instrumentation reporting `map 0% / persist 100%` and about **47 hours** remaining at
100,000 records. The run was stopped.

The split mattered more than the rate. Had it come back `map` high, batching the inserts would have
been effort spent on the wrong half — the narrative parser runs over text reaching 125,644 characters
on every record (§6), and that was the plausible culprit. It was measured rather than assumed (§13.8).

A second measurement bounded the target: inserting into a table of `watch_name`'s exact shape, with all
six of its indexes, ran at **17,500 rows/second**; the same inserts with the primary key alone took
150 ms per 20,000 rows against 1,144 ms with every index — **7.6×**. The load was managing roughly 440
rows/second. The database was never the constraint; talking to it one row at a time was.

Three changes, in the order their evidence justified:

1. **Batch the child rows.** `JdbcWatchEntityVersionWriter` now writes each of the nine child tables
   through one `batchUpdate`: one prepare and one round-trip per table per entity, instead of one per
   row. Rows needing `text[]` columns previously built a fresh `PreparedStatement` each. **The
   transaction boundary is unchanged** — still one per entity — because `TransactionAtomicityIntegrationTest`
   proves a failure part-way through an entity's children leaves no version row behind, and that
   property is worth more than any throughput gain.
2. **`reWriteBatchedInserts=true`** on the ingest JDBC URL, so the driver rewrites each batch as a
   single multi-row `INSERT` rather than a pipelined sequence of single-row ones.
3. **Postgres bulk-load settings** in `docker-compose.yml`: `shared_buffers=2GB` (from the 128MB
   default), `maintenance_work_mem=1GB`, `max_wal_size=8GB`, `checkpoint_timeout=15min`,
   `effective_cache_size=6GB`, `wal_compression=on`, `random_page_cost=1.1`. `fsync` and
   `synchronous_commit` were deliberately left **on**: a load whose committed rows can vanish on a
   power cut is not evidence, and this is the load that produces the screening baseline.

Held in reserve, not done: **deferring the four `watch_name` search index builds until after the
load**. The model already supports it — `ListVersion.indexBinding` plus the `IndexReconciliation`
promote gate — and the 7.6× measurement says it is the largest remaining lever. It is not worth its
risk until a re-measurement shows it is needed, and the runbook's Step 2c sets the threshold: under
300 records/second on the first 100,000.

**What was fixed alongside it, because it would have been masked by a faster load.** `IndexReconciliation.isComplete`
checked only that *at least one* version row existed for the staged list version — so a load that lost
rows could be promoted. It cannot do better: at promote time there is no independent expectation to
compare against, and `list_version.record_count` counts records *read*, which on a reload exceeds the
versions written by however many were `UNCHANGED`. `LoadWatchlistVersion` does know the expectation, so
it now reads the stored count back through a new `WatchEntityVersionWriter.countVersionsIn` and fails
the load closed unless `added + amended + delisted` version rows are actually present — checked before
every return path that could lead to a promotion, `AWAITING_CONFIRMATION` included. An entity whose
version row silently went missing is never screened, and no benchmark would show it (I-1, I-2). The
test fake counts what it actually recorded rather than agreeing by construction, and `loseWrites(n)`
makes the failure directly testable.

Full build green after all of it: **485 tests, 0 failures**, including the two Testcontainers
atomicity tests and the end-to-end load against real Postgres.

**Addendum, same day — batching per entity was the wrong unit, and measuring said so.**

The batched writer ran the same 100,000 records: **34 → 60 rec/s**, only **1.76×**, still
`map 0% / persist 100%`. The prepares were gone; what remained was **one commit and about ten
round-trips per entity**. A batch scoped to a single entity is usually a single row — one DOB, one
country, one or a handful of names — so "batching" had bought almost nothing.

One further measurement, in an isolated database on the same tuned server with `fsync` and
`synchronous_commit` both on, settled where the cost was:

| Shape | 2,000 rows | Rate |
|---|---|---|
| 2,000 transactions, one row each | 5,609 ms | 357 rows/s |
| One transaction, 2,000 single-row statements | 35.6 ms | 56,000 rows/s |
| One transaction, one multi-row statement | 21.0 ms | 95,000 rows/s |

**Commit frequency alone was worth 158×**, and the loader was paying it 5,990,394 times. Against the
app's 16.6 ms per entity, the server-side commit accounts for ~2.8 ms; the remaining ~14 ms is client
round-trips and index maintenance. Both are fixed by the same change, which is why no further
attribution was needed before acting.

**So the write unit became a chunk of 1,000 entities.** One transaction, one statement per table
across the chunk, identities resolved in one round-trip per chunk (`ensureAll`). Commits drop from
5,990,394 to ~6,000 and round-trips by three orders of magnitude.

**The atomicity argument, because it is the only invariant in play.** A chunk transaction is *stronger*
than a per-entity one: the property the integration tests protect — a version row never exists without
its children — now holds for every entity in the chunk jointly. What a chunk cannot do is name the
record that broke a constraint, so `LoadWatchlistVersion` replays a failed chunk one record at a time.
Per-record quarantine is preserved exactly, and the replay cost is paid only on failure. Three tests
hold that line: only the offending record is quarantined, the 999 sharing its chunk are still written,
and at the adapter level a failure anywhere in a chunk leaves no row from any of its entities.

**A defect the new tests caught before the feed did.** The consecutive-failure guard counted only
*flushed* successes, so a file whose good records were still in the buffer was abandoned as if nothing
worked. It now counts buffered records too.

**DuckDB was considered and set aside.** The proposal was to stage the load in an embedded columnar
store and migrate to Postgres afterwards. The instinct — accumulate, then bulk-load — was right, and it
is what chunking does. A second engine was not, for reasons that outlive this iteration: it addresses a
client-side batching choice with new infrastructure (§13.8); Postgres already owns that path
(`COPY … FROM STDIN` via the driver already on the classpath, no new dependency to justify under §3);
two schemas for the same watchlist data is the I-10 duplication risk moved into storage, and every
divergence there is a silent-corruption path in a screening system; a DuckDB file has no roles, while
segregation of duties is enforced by which credential each repository holds and is listed "never cut"
(§12); and it would not reduce the 7.6× index cost, which has to be paid in Postgres regardless.

**Still held in reserve**, and now the only lever left: deferring the four `watch_name` search index
builds until after the load, via `ListVersion.indexBinding` and the `IndexReconciliation` promote gate.
The floor without it is ~74 minutes of insert work for the full feed (78M rows at 17,500 rows/s), so
the re-measurement decides whether it is needed. `COPY` is no longer planned — if chunking reaches the
insert floor there is nothing left for it to win.

## 4. Low / accepted and recorded

- **CJK and other logographic names are not handled**, only documented (spec §15.2 explicitly
  prefers documenting the limitation). ICU produces *something* for Han/Hangul/Kana; its usefulness
  for matching is unverified.
- **Trigram Dice is the weakest measure** and is actively harmful on transliterated Arabic (dropping
  it lifts A7 from 92 to 100). Its weight should be revisited with real evidence (ADR-002).
- **The CLI pays Spring's ~4 s start per invocation.** Acceptable for an operator harness; it is the
  reason an API is a separate piece of work.
- **Ownership percentages and link roles come from narrative text** and the ownership graph is not
  resolved (descoped).
- **A load rejects at > 5 % malformed records and halts at a > 20 % delta.** Both thresholds are
  code constants rather than configuration.
- **OneDrive interferes with Gradle's build directory** on the development machine. It escalated
  from a retryable `Unable to delete directory` to an unrecoverable `Cannot snapshot ….class: not a
  regular file` on 2026-09-25, and is now worked around by a local, uncommitted Gradle init script
  that relocates every build directory to `C:\horus-build` (testing guide §3). A checkout outside a
  synced folder avoids it outright, and CI never sees it.

## 5. What "ready" would look like

1. B-6 closed first — until the adapter maps the real feed, B-1 cannot even be attempted.
2. B-1, B-2 and B-4 closed: real-data recall and precision measured, an operating point approved by
   Compliance, and the two known match-quality defects either fixed or explicitly accepted per case
   class. (B-3 and B-5 are already closed.)
3. H-1 … H-9 closed, in particular a tamper-evident audit trail, a measured p95 within the KPI,
   and a country comparison that cannot demote a true positive for comparing a name with a code.
4. Internal Audit has tested the evidence chain end to end: pick a past screening, reconstruct its
   score by hand from the stored explanation, and confirm the list and config versions it cites.
5. The descoped scope (§12) that production actually needs — API, alerts and disposition, batch
   re-screening, admin surface — is built, and a parallel run against LSEG World-Check One has been
   completed and its disagreements explained.

## 6. Recommendation

Continue to a second iteration. Do **not** route live payment or onboarding traffic through HORUS,
and do not decommission the LSEG API path, until at minimum B-1, B-2, B-4 and B-6 are closed and a
parallel run has been reviewed by Compliance.
