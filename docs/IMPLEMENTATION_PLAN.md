# HORUS — Implementation plan (2 weeks, Scrum)

Two one-week sprints. Each step below is one Claude Code session or less.

**How to run a step:** start Claude Code in the repo root and type
`/step <number>`. It reads the step, proposes a plan, and waits. Approve,
let it implement, then **you** check the "You verify" list — that part is not
delegable.

Steps are ordered by dependency. Do not skip ahead: step 7 measures recall on
the index built in step 5, which depends on the pipeline in step 3.

---

## SPRINT 1 — "The full list is loaded, versioned and reconciled"

### Step 0 — Scaffold and guardrails  *(Day 1, morning)*

**Goal.** A repository whose architecture is enforced before any feature code
exists.

**Prompt**
> Read CLAUDE.md fully. Scaffold the Gradle multi-module build described in §4
> and §7: modules `domain`, `core`, `application`, `adapters`, `bootstrap`,
> `tools`, `architecture-tests`; Kotlin DSL; wrapper; a version catalog in
> `gradle/libs.versions.toml` using current stable versions; Java 21 toolchain.
> Declare only the inter-module dependencies allowed in §4.
>
> Write the ArchUnit rules from §4 **first**, in `architecture-tests`, with one
> placeholder class per package so the rules have something to check. Prove
> each rule works by temporarily adding a violating import, showing the test
> fail, then removing it.
>
> Add `docker-compose.yml` with PostgreSQL 16, an init script enabling
> `pg_trgm` and `unaccent`, and creating roles `horus_ingest`, `horus_screen`,
> `horus_audit` with passwords from environment variables.
>
> Add a CI workflow skeleton running build, Semgrep, OWASP dependency-check and
> gitleaks. Add `.gitignore` per the repo's existing one. No feature code.

**Done when**
- `.\gradlew.bat build` is green.
- Each ArchUnit rule has been shown failing on a deliberate violation.
- `docker compose up -d db` starts and the three roles exist.

**You verify**
- `domain`, `core`, `application` build files contain **no** Spring dependency.
- The real feed is outside the repo and `HORUS_FEED_PATH` points to it.
- `git status` shows no data files.

---

### Step 1 — Domain model  *(Day 1, afternoon)*

**Goal.** The corrected domain model from CLAUDE.md §5, pure and tested.

**Prompt**
> Implement the domain model in CLAUDE.md §5 in the `domain` module. Records for
> value objects; no setters; validation in constructors. Include `QueryName`
> with a maximum length that rejects over-length input, `DecisionBand` with
> `ERROR`, `CanonicalSlot` in `horus.domain.shared`, and the identity/version
> split with `entityVersionId` and `contentHash`. `WatchEntityVersion` is
> immutable; de-listing is a tombstone version. All children key on
> `entityVersionId`. `ScreeningRequest.listVersionIds` is a set.
> `WhitelistEntry` references the stable `entityId` plus
> `approvedAgainstVersionId`, with a method answering whether it is active for a
> given current version.
>
> Tests first. Include tests that invalid objects cannot be constructed and that
> a whitelist entry goes inactive when the version changes.

**Done when** domain tests pass; ArchUnit green; no framework imports.

**You verify** the model matches §5 field by field. This is the step most worth
reading line by line — everything else builds on it.

---

### Step 2 — Schema and roles  *(Day 2, morning)*

**Goal.** Tables for the domain, with segregation of duties enforced by the
database.

**Prompt**
> Write Flyway migrations in `adapters` for the domain model: `list_version`,
> `watch_entity`, `watch_entity_version`, the child tables keyed on
> `entity_version_id`, `audit_event`, `config_version`. Add the GRANTs: 
> `horus_ingest` writes watchlist tables; `horus_screen` reads watchlist and
> writes screening tables; `horus_audit` has **INSERT and SELECT only** on
> `audit_event`. Add trigram GIN indexes on normalised names.
>
> Write a Testcontainers integration test that connects **as `horus_audit`**
> and asserts UPDATE and DELETE on `audit_event` both fail. Write one that
> asserts `horus_screen` cannot write watchlist tables.

**Done when** migrations apply cleanly on an empty database and both
negative-privilege tests pass.

**You verify** the negative tests actually connect as the restricted role —
a test that connects as the owner and "fails" proves nothing.

---

### Step 3 — Normalisation (L1)  *(Day 2 afternoon – Day 3)*

**Goal.** The one normalisation pipeline, used identically at ingestion and
query time.

**Prompt**
> Implement `horus.normalisation` per the 12-step pipeline in
> `docs/spec/Project_Context.md` §15.2, using ICU4J for transliteration,
> commons-codec for Double Metaphone. Each step is a `NormalisationStep` with an
> id and its own test class, written first. Keep the ordered token form **and**
> the sorted form. Give the pipeline a `pipelineVersion` string.
>
> Test explicitly: Arabic, Cyrillic, accented Latin, homoglyphs (`M0hammed`),
> particles (`al`, `bin`, `ibn`, `de`, `van`), legal forms (`Ltd`, `Limited`,
> `SARL`), and over-normalisation guards (`Ali` must not equal `Alia`). Add a
> golden-file determinism test: normalise a fixed synthetic list twice and diff.
> Document the CJK limitation rather than pretending to handle it.

**Done when** every step has tests, coverage ≥ 80%, determinism test passes.

**You verify** the over-normalisation guards. Collapsing `Al-Sayed` and
`Alsayed` is right; collapsing `Ali` and `Alia` is a precision defect you will
pay for in alert volume.

---

### Step 4 — Format reader, SPI and World-Check adapter  *(Day 3 – Day 4 morning)*

**Goal.** Bytes become `CanonicalRecord`s, with the narrative extractor in its
proper home.

**Prompt**
> Implement `horus.ingestion.format` (`FormatReader`, `RawRecord` with
> `isAbsent`, `XmlFormatReader` using StAX with **coalescing off**, DTD off,
> external entities off, JAXP entity limits lifted) and `horus.ingestion.spi`
> (`WatchlistSourceAdapter`, `CanonicalRecord`, `CanonicalRecordBuilder`,
> `AdapterRegistry`, `CapabilityExpectation`, `SourceFixture`).
>
> Implement `WorldCheckAdapter` in `horus.adapter.source.worldcheck` using the
> facts in CLAUDE.md §6: entity type from (`@e-i`, `@category`), name
> composition by type, `xsi:nil` as absence, location from attributes.
>
> Migrate the existing narrative extraction code (in `legacy/narrative/`) into
> `horus.adapter.source.worldcheck.narrative`, renaming packages and keeping its
> `SelfTest` assertions as JUnit tests.
>
> Build a synthetic 20-record World-Check-shaped XML fixture — invented names —
> covering an individual, an organisation, a vessel, a record with `xsi:nil`
> DOB, a de-listed record with narrative, and an ownership record. Assert its
> exact canonical output.

**Done when** the fixture test and all migrated narrative tests pass.

**You verify** the fixture uses **no real names**, and the organisation case
produces a primary name from `last_name` alone.

---

### Step 5 — Load pipeline  *(Day 4 afternoon – Day 5 morning)*

**Goal.** Stage, reconcile, gate, promote, roll back — atomically.

**Prompt**
> Implement the ingestion interactors in `horus.application.ingestion`:
> `LoadWatchlistVersion`, `ReconcileVersion`, `PromoteListVersion`,
> `RollBackVersion`, `ConfirmStagedVersion`, `VerifySourceFixture`, plus
> `CapabilityDeriver` and `CapabilityGate`. Define the output ports they need in
> `horus.application.port`; implement them in `horus.adapter.persistence`.
>
> Reconciliation compares `contentHash`: unchanged → no new version; changed →
> new version; absent → **tombstone** version. Produce the reconciliation report
> (added / amended / delisted / rejected with reasons). A delta above 20% halts
> for operator confirmation. A capability outside tolerance of the expectation
> halts. Promotion is atomic and gated by `IndexReconciliation`. Every load and
> promotion records an audit event through `RecordAuditEvent`.
>
> Normalise names through the shared `NormalisationPipeline` during load.
> Stream; bounded memory. Wire the `ingest` CLI command.

**Done when** loading the synthetic fixture twice produces **no new versions**
the second time, and a fixture with one record removed produces exactly one
tombstone.

**You verify** that a failed load leaves the previous version serving.

---

### Step 6 — Full load and Sprint 1 review  *(Day 5)*

**Goal.** The real feed, loaded by you.

**You run** the ingest command against `HORUS_FEED_PATH`. Claude Code does not
touch the real file.

**Prompt** (for the documentation only)
> Write `docs/runbook/ingestion.md`: how to run a load, read the reconciliation
> report, confirm an anomalous delta, and roll back. Write the reconciliation
> report format document for Compliance review (deliverable D4).

**Done when**
- All 5,990,394 records loaded, counts reconciled, checksum recorded.
- Capabilities recorded on the `ListVersion` match §6 within tolerance.

**Sprint review:** demo the load and the reconciliation report to the
Solution Architect and the Compliance owner. **Retro:** 20 minutes. If the
sprint gate was missed, apply the cut line before planning Sprint 2.

---

## SPRINT 2 — "Match quality is measured, not asserted"

### Step 7 — Blocking and recall-in-isolation  *(Day 6)*

**Goal.** Candidate generation whose recall is known before scoring exists.

**Prompt**
> Implement `horus.blocking`: `BlockingIndex` port, `KeyProbe`,
> `BlockingOutcome` (COMPLETE / TRUNCATED / PARTIAL), `CandidateSet`,
> `CandidateGenerator`, `IndexCapabilities`. Any PARTIAL makes the set PARTIAL.
> Nothing returns a bare set of ids.
>
> Implement `CoLocatedIndexAdapter` in `horus.adapter.index` over PostgreSQL:
> trigram (`pg_trgm`), exact normalised hash, Double Metaphone key. Candidate
> generation is the **union** of strategies, never the intersection.
>
> Implement `MeasureBlockingRecall`: given labelled pairs (query, expected
> entity), report the fraction whose expected entity appears in the candidate
> set, per strategy and combined, plus mean candidate-set size.

**Done when** the recall measurement runs on a synthetic labelled set and
reports per-strategy recall.

**You verify** blocking recall on real data, run by you against the benchmark
corpus. If it is below the recall floor, **stop** — scoring cannot recover it.

---

### Step 8 — Matching engine (L2)  *(Day 7)*

**Goal.** A pure, explainable scorer.

**Prompt**
> Implement `horus.matching` per the spec §16: `SimilarityMeasure`
> implementations (token-sort Jaro-Winkler, token-set Jaccard, phonetic
> agreement, normalised edit distance, n-gram) reusing commons-text;
> `EntityTypeStrategy` for INDIVIDUAL and ORGANISATION with different weights
> and generic-token down-weighting for organisations; `AttributeComparator`s
> (DOB with tolerance and absence = 0, country, identifier forcing a strong
> match, entity-type mismatch demoting); `Registry` with
> `validateAgainst(cfg, capabilities)`; decision bands; `MatchExplanation` with
> `recompute()`, `sourcesConsulted`, `capabilityGaps`.
>
> The engine scores a `CandidateView`; it never reads entities. Tests first.
> Include: `recompute()` equals the stored score; a DOB conflict demotes but the
> candidate remains; the worked examples in spec §30 as tests.

**Done when** matching coverage ≥ 80% and ArchUnit confirms `horus.matching`
depends only on normalisation and the shared kernel.

**You verify** that no path removes a candidate. Search the diff for `filter`,
`remove`, `break` in scoring code and check each one.

---

### Step 9 — Screen a name  *(Day 8 morning)*

**Goal.** The end-to-end use case, driven from the CLI.

**Prompt**
> Implement `ScreenAName` in `horus.application.screening`: build `QueryName`,
> normalise, generate candidates, assemble `CandidateView`s, score, band, record
> `ScreeningRequest` / `ScreeningSubject` / `ScreeningCandidate` with the whole
> explanation JSON, and audit. Any PARTIAL candidate set yields ERROR; ERROR
> dominates across subjects. Every result carries `listVersionIds`,
> `configVersionId`, `pipelineVersion`. Add a `screen` CLI command.
>
> Add a test that captures logs during a screening and asserts the query name
> appears nowhere in them.

**Done when** an end-to-end test on the synthetic list screens a name and
returns a stored, explained, reconstructible result.

---

### Step 10 — Benchmark harness  *(Day 8 afternoon – Day 9)*

**Goal.** Evidence of match quality — the deliverable that matters most.

**Prompt**
> Implement `horus.application.benchmark`: read a labelled corpus from a path in
> `HORUS_BENCHMARK_PATH` (format: query, entity type, optional attributes,
> expected entity id or NONE, case class). Run every case through `ScreenAName`.
> Report recall, precision, F1, false-positive rate per 1,000, and a per-case
> list of every missed true positive. Implement a threshold sweep writing a
> precision/recall table across thresholds. Output CSV and a markdown summary.
> Test the harness with a synthetic corpus only.

**You run** it against the real corpus, which you and Compliance build together
(target 200 cases; §19.3 wants 1,000 — say so in D12).

**You verify** every missed true positive individually. No recommendation to
rely on HORUS may be made while an unexplained miss remains.

---

### Step 11 — Hardening and handover  *(Day 10)*

**Prompt**
> Make CI fully green including Semgrep, dependency-check and gitleaks; justify
> any suppression in writing. Write ADRs for: data model and versioning,
> similarity measure selection (citing the benchmark), index strategy, runtime
> and framework. Draft `docs/D11-final-report.md` and
> `docs/D12-production-readiness.md` from the measured results — candid gap
> list, not a sales pitch.

**Sprint review:** demo a screening with its explanation, the benchmark
results, and the gap list.

---

## Cut line

If Sprint 1 slips, cut in this order and stop when it fits:

1. Identifier and country comparators (0.74% identifier coverage makes the
   first near-useless on this feed anyway)
2. Phonetic blocking — keep trigram and exact hash
3. Benchmark corpus to 100 cases, true positives first

**Never cut:** list versioning · explanation · a benchmark of some size · the
three database roles.
