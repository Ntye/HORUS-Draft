# CLAUDE.md — HORUS Enterprise Name Screening Service

This file is loaded into every Claude Code session. It is the operating contract
for anyone — human or agent — writing code in this repository. When this file
and your instincts disagree, this file wins. When this file and
`docs/spec/Project_Context.md` disagree, the spec wins; flag the conflict.

---

## 1. What HORUS is, in five lines

Afreximbank licenses the LSEG World-Check bulk feed and also pays per call for
the World-Check One API. HORUS builds screening in-house on the data already
owned: ingest the feed into a versioned store, apply deterministic and
explainable fuzzy matching, and produce scored, explained candidates for human
disposition. **HORUS does not make compliance decisions.** Compliance owns
policy; we build a system that faithfully implements it and produces evidence
Internal Audit can test.

This iteration has **10 working days**. Scope is in §12. Read it before
proposing anything.

---

## 2. Invariants — never traded away

A change that violates one of these is wrong regardless of how much time it
saves. Every row names how you comply and how it is proven.

| # | Rule | How you comply in code | How it is proven |
|---|---|---|---|
| I-1 | **Fail closed.** Unavailability is an explicit error, never a clear. | `DecisionBand.ERROR` exists. Any `BlockingOutcome.PARTIAL` makes the whole subject `ERROR`. Across subjects, **ERROR dominates**. No `catch` that returns an empty result. | Tests that inject a partial index and assert `ERROR`. |
| I-2 | **Recall is the binding constraint.** False negative = regulatory breach; false positive = cost. | Ambiguity resolves toward *keeping* a candidate. Status "may still be designated" is true when unsure. | Benchmark recall reported first, always. |
| I-3 | **Explainable, no ML.** Every score traceable to stated rules and weights, reconstructible by hand. | `MatchExplanation` carries every feature score, weight and attribute effect. Narrative-derived values carry `Provenance(ruleId, start, end)`. | `MatchExplanation.recompute()` equals the stored score, tested. |
| I-4 | **Deterministic.** Same input + list version + config ⇒ same result. | No `HashMap` iteration order in outputs; sort before emitting. No `Instant.now()` in core — inject `Clock`. No randomness. | Golden-file tests run twice and diffed. |
| I-5 | **Evidence is append-only.** | `audit_event` and `alert_disposition_event`: INSERT + SELECT only. Corrections are new rows referencing the prior one. | Integration test: the audit DB role *fails* on UPDATE and DELETE. |
| I-6 | **Never hard-delete watchlist data.** | De-listing = a **tombstone** `WatchEntityVersion` with `status = DELISTED`. Versions are immutable. | Reconciliation test: an entity absent from load N+1 yields a tombstone, and load N remains queryable. |
| I-7 | **Configuration over code.** | Thresholds, weights, list selection in versioned `ConfigVersion`. `Registry.validateAgainst(cfg, capabilities)` fails the **boot**, not the screening. | Startup test with an invalid config asserts boot failure. |
| I-8 | **Attributes adjust, never eliminate.** | DOB conflict, entity-type mismatch, whitelist, de-listed status: all **demote and explain**. No code path removes a candidate that name similarity surfaced. | Tests assert the candidate is still present, with a lower score and a stated reason. |
| I-9 | **Matching engine is a pure library.** | `horus.matching` imports nothing but `horus.normalisation` and `horus.domain.shared`. No I/O, no Spring, no JDBC. | ArchUnit. |
| I-10 | **Normalisation is symmetric.** | Exactly **one** `NormalisationPipeline`, imported by both ingestion and screening. Never reimplement a step. | ArchUnit + a test that normalises the same string via both call paths. |
| I-11 | **No personal data in URLs, logs, or error text.** | Log ids, never names. Exceptions carry ids. | A log-capture test asserts no query name appears in any log line. |
| I-12 | **Every result carries provenance.** | `listVersionIds` (a **set**) + `configVersionId` + `pipelineVersion` on every screening. | Asserted on every screening test. |
| I-13 | **Evidence before assertion.** | Algorithm, index and threshold choices are measured and recorded in an ADR. "It's what I know" is allowed only when stated alongside the alternatives. | ADRs in `docs/adr/` cite measurements. |

---

## 3. Data handling rules for you, the agent

These protect licensed data and personal data. They are not negotiable.

- **Never open, read, grep, cat, head or sample the real World-Check feed.**
  It lives **outside this repository** at the path in `HORUS_FEED_PATH`. You
  never need its contents: the facts you need are in §6.
- **Never read profiler output** (`*.tsv` from profiling runs, benchmark
  corpora). They contain real listed names.
- **Tests use synthetic fixtures only**, under `src/test/resources/`. Invent
  names. Never copy a real listed name into a fixture, a test, a comment, or a
  commit message.
- Never read `.env`, `secrets/`, or any credential file. Configuration comes
  from environment variables resolved in `horus.bootstrap` only.
- Never run `git push`, `git reset --hard`, or anything that rewrites history.
  Propose commits; the human commits.
- Do not add a dependency without stating why, its licence, and whether an
  existing dependency already covers it.

`.claude/settings.json` denies some of this mechanically, but **permission
rules are a guardrail, not a control** — there are reported cases of deny rules
not being enforced. The real control is that confidential data is not inside
the working tree at all. Keep it that way.

---

## 4. Architecture — clean architecture, five rings

Every dependency points **inward**. That single property is the architecture.

```
0  bootstrap        composition root — the ONLY place that sees every ring
1  adapters         driving: cli, scheduler · plugins: source.* · driven: persistence, index, feed
2  application      one interactor per use case + the output ports it needs
3  core             normalisation · blocking · matching · ingestion.{format,spi,mapping}
4  domain           entities, value objects, shared kernel — depends on nothing
```

### Gradle modules

| Module | Packages | May depend on | Must NOT depend on |
|---|---|---|---|
| `domain` | `horus.domain.*` | JDK only | anything else |
| `core` | `horus.normalisation`, `horus.blocking`, `horus.matching`, `horus.ingestion.*` | `domain`, ICU4J, commons-codec, commons-text | Spring, JDBC, any adapter |
| `application` | `horus.application.*` | `core`, `domain` | Spring, JDBC, any adapter |
| `adapters` | `horus.adapter.*` | `application`, `core`, `domain`, Spring, JDBC | `bootstrap` |
| `bootstrap` | `horus.bootstrap` | everything | — |
| `tools` | `horus.tools.profiling` | `core`, `domain` | `application`, `adapters` |
| `architecture-tests` | ArchUnit rules | all (test scope) | — |

Frameworks live in the outer ring only. **If you find yourself adding a Spring
annotation to `domain`, `core` or `application`, stop — it belongs in an
adapter.**

### Rules that ArchUnit enforces

1. No class in `domain`, `core` or `application` imports `org.springframework..`, `java.sql..` or `javax.sql..`.
2. `horus.matching` depends only on `horus.normalisation` and `horus.domain.shared`.
3. `horus.domain..` depends only on `horus.domain..`.
4. No `horus.adapter.source.*` package imports another `horus.adapter.source.*` package.
5. `horus.application..` never imports `horus.adapter..`.
6. Only `horus.bootstrap` may import `horus.adapter..` from outside the adapter ring.

A failing ArchUnit test is a design conversation, not something to suppress.

### Where things go

| You are writing… | It goes in |
|---|---|
| An entity or value object | `domain` |
| A pure algorithm (a similarity measure, a normalisation step) | `core` |
| "When X happens, do Y, Z, record W" | `application` — one interactor |
| An interface the application needs from the outside world | `application.port` (or owned by the core package that consumes it, e.g. `BlockingIndex` in `horus.blocking`) |
| SQL, file I/O, HTTP, CLI parsing, scheduling | `adapters` |
| Wiring, secrets, DataSources, startup validation calls | `bootstrap` |

---

## 5. Domain model — the corrected version

This incorporates the design review. Where the class diagrams in `docs/design/`
differ, **this section is current**.

**Identity versus version.**
- `WatchEntity` — stable identity: `entityId`, `sourceId`, `sourceEntityId`.
  Unique on `(sourceId, sourceEntityId)`. Never versioned, never deleted.
- `WatchEntityVersion` — **immutable**: `entityVersionId` (PK), `entityId`,
  `listVersionId`, `contentHash`, `entityType`, `gender`, `primaryName`,
  `status` (`ACTIVE` | `DELISTED`), `categories`, `listSources`, `designatedAt`,
  `delistedAt`.
- A new version is created **only when `contentHash` changes**. An entity
  absent from a new full load gets a **tombstone** version (`DELISTED`). No
  version is ever updated.
- **Every child keys on `entityVersionId`**, not `entityId`: `WatchName`,
  `WatchDob`, `WatchIdentifier`, `WatchCountry`, `WatchAddress`, `WatchLink`,
  `WatchExternalSource`, `WatchDesignation`, `WatchOwnership`. Otherwise
  historical reconstruction (§18.2) is impossible for anything but names.

**Load.** `ListVersion` identifies a *load*: `sourceId`, `formatId`,
`mappingId` (Tier 2 only), file name and checksum, `vendorPublishedAt`,
`loadedAt`, `recordCount`, `status` (`STAGED`|`ACTIVE`|`SUPERSEDED`|`FAILED`),
`capabilities: SourceCapabilities`, `pipelineVersion`, `indexBinding`.
`IndexReconciliation` gates `promote()`.

**Screening.** `ScreeningRequest` carries `listVersionIds: Set<UUID>` (plural —
a breaking API change if left singular), `configVersionId`, `pipelineVersion`,
`outcome` (ERROR dominates across subjects), `failureReason`, `idempotencyKey`.
`ScreeningSubject` per name. `ScreeningCandidate` stores `sourceId`,
`entityId`, `entityVersionId`, and the **whole** explanation as JSON with
`explanationSchemaVersion` — reconstruction must not recompute.

**Matching.** `MatchingEngine.score(query, CandidateView, cfg, WhitelistView)`.
`CandidateView` is assembled by the application layer; comparators read it,
never the entities. `MatchExplanation` includes `sourcesConsulted` and
`capabilityGaps`.

**Blocking.** `BlockingIndex.lookup(KeyProbe): BlockingOutcome` where
completeness ∈ `COMPLETE | TRUNCATED | PARTIAL`. **Nothing in the codebase
returns a bare `Set<UUID>` of candidates** — that shape is fail-open.

**Whitelist.** `WhitelistEntry` references the **stable** `entityId` and
records `approvedAgainstVersionId`. When the current version differs, the entry
is automatically inactive (§21). It flags and demotes; it never suppresses.

**Shared kernel** (`horus.domain.shared`): `Provenance`, `EntityType`, `Gender`,
`DecisionBand` (including `ERROR`), `CanonicalSlot`, ids. `CanonicalSlot` lives
here so ingestion and matching share one vocabulary without importing each other.

---

## 6. What we measured about the feed — do not re-derive, do not contradict

Established by full-file profiling. Treat as facts; if code implies otherwise,
the code is wrong.

- **5,990,394 records, 28 categories, one structure** (32 paths, depth 5).
  Category is data, not shape: one parser covers the feed.
- **Entity type** comes from `person/@e-i`: `E` = non-individual (1,069,902);
  `M`/`F`/`U` = individual and gender (4,920,492). `E` also covers VESSEL,
  AIRCRAFT, WEBSITE, PORT, COUNTRY, ADDRESS — so canonical type is a function
  of **(`@e-i`, `@category`)**. `@category` alone is insufficient: three
  categories contain both kinds.
- **Primary name is composed.** For `E` records `first_name` is empty and the
  whole name is in `last_name`. The two signals agree to 14 records in 5.99M.
- `xsi:nil="true"` is an **asserted absence**, distinct from an empty value.
- DOB populated on **27%**; `age` + `as_of_date` on 12.4%; `deceased` present.
- Passport is the **only** structured identifier, on **0.74%** of records.
- `countries/country` is **100%** populated. Location data lives in
  `location@city/@country/@state` **attributes**, not element text.
- **There is no status, de-listing flag, or designation date in the structured
  record.** They exist only in `details/further_information` narrative.
- In a strided sample, **22.77%** of sanctioned records are removed from every
  list that designated them yet remain in the feed.
- `linked_to/uid`: 13.07M links, 2.14M targets, 100% resolution — but **no
  role, type or percentage**. Ownership percentages come from narrative
  (`DIRECT SHAREHOLDER/S`, 1.23% of records, 89.6% with an explicit percent).
- `sub-category` is a binary PEP flag (46.9% of records), not a taxonomy.
- `further_information` reaches **125,644 characters**. StAX must run with
  **coalescing off** and JAXP entity limits lifted — safe only because DTD and
  external entities are disabled.
- A full structural pass took 229 s at 310 MB heap. Stream; never load the file.

---

## 7. Tech stack

Versions are pinned in `gradle/libs.versions.toml` — never inline a version.

| Concern | Choice | Constraint |
|---|---|---|
| Language | Java **21** via Gradle toolchain | Local JDK may be newer; the toolchain decides |
| Build | Gradle, Kotlin DSL, wrapper committed | Multi-module per §4 |
| Framework | Spring Boot 4 | **`bootstrap` and `adapters` only** |
| Database | PostgreSQL 16 + `pg_trgm` + `unaccent` | Docker Compose locally |
| Migrations | Flyway | In `adapters` |
| Normalisation | ICU4J (transliteration), commons-codec (Double Metaphone), commons-text (Jaro-Winkler, Levenshtein) | Reuse primitives; implement composition only |
| XML | StAX (JDK) | Coalescing off, DTD off, external entities off |
| CLI | picocli | `adapters.cli` |
| Tests | JUnit 5, AssertJ, ArchUnit, Testcontainers | Testcontainers needs Docker running |
| Security | Semgrep (SAST), OWASP dependency-check, gitleaks | CI fails on high severity |

Everything is Java. That resolves the earlier I-10 risk of two languages each
implementing normalisation differently.

---

## 8. Commands

Created by Step 0. Windows PowerShell shown; `./gradlew` works in Git Bash.

```powershell
.\gradlew.bat build                       # compile + all tests + ArchUnit
.\gradlew.bat test                        # all tests
.\gradlew.bat :core:test                  # one module
.\gradlew.bat :architecture-tests:test    # the dependency rule alone
.\gradlew.bat dependencyCheckAnalyze      # vulnerable dependencies
docker compose up -d db                   # local Postgres with extensions + roles
.\gradlew.bat :bootstrap:bootRun --args="ingest --source=worldcheck"
```

The ingest command reads the feed from `HORUS_FEED_PATH`. The human runs it
against the real file; you run it against synthetic fixtures.

---

## 9. Coding conventions

- **Immutability by default.** Records for value objects. Entities expose no
  setters; state changes produce new objects or new rows.
- **Validate in constructors.** A `QueryName` that can exist is a valid,
  bounded `QueryName`. Invalid domain objects must be unconstructible.
- **No `null` for absence** in the domain: use `Optional`, or an explicit
  `Absent` marker where absence carries meaning (`xsi:nil`).
- **Inject time and ids.** `Clock` and `IdGenerator` ports. Core never calls
  `Instant.now()` or `UUID.randomUUID()`.
- **Deterministic output.** Sort collections before they leave a method that
  feeds scoring, persistence or explanations.
- **Errors are values where they are expected** (`BlockingOutcome`,
  `Result`-style returns); exceptions are for bugs and infrastructure failure.
- **Names from the ubiquitous language.** Use the spec's terms: `ListVersion`,
  `Disposition`, `DecisionBand`. Do not invent synonyms.
- Comment **why**, especially where a choice traces to an invariant — cite it
  (`// I-8: demote, never remove`).
- Markdown documentation lives beside the code.

---

## 10. Testing conventions

- **Core is test-first.** Every normalisation rule and every similarity measure
  gets its own tests before its implementation. NFR-030 requires ≥80% line
  coverage on normalisation and matching.
- **Determinism tests:** run twice, diff outputs byte-for-byte.
- **Per-rule normalisation tests** include Arabic, Cyrillic, accented Latin,
  homoglyphs (`M0hammed`), particles (`al`, `bin`, `de`, `van`), and
  over-normalisation guards (`Ali` ≠ `Alia`).
- **Fixture tests for adapters:** a synthetic source file whose expected
  canonical output is hand-checked. A mis-mapped name column loads cleanly and
  screens cleanly — the fixture is the only thing that catches it.
- **Integration tests** use Testcontainers Postgres, never a shared database.
- **Negative security tests** are real tests: the audit role's UPDATE must fail;
  an over-length name must be rejected; no name may appear in logs.
- **Measure blocking recall in isolation before scoring exists.** Recall lost
  at stage 1 is invisible in stage 2 metrics.

---

## 11. Security by design — applied every change

- Secrets only from environment or the secrets manager, read in `bootstrap`.
- **Three DB roles**, each handed only to its repository by `bootstrap`:
  `horus_ingest`, `horus_screen`, `horus_audit`. `horus_audit` has INSERT and
  SELECT on audit tables and nothing else. Segregation of duties is enforced by
  which credential each repository holds, not by code discipline.
- Parameterised queries only. No SQL string concatenation, anywhere.
- Input bounds live in domain value objects, so every path is bounded.
- XML parsing: DTD off, external entities off, before any limit is lifted.
- Audit is an **application use case**, not a filter or trigger — an adapter
  cannot route around it.
- CI gates: SAST, dependency scan, secret scan. A high-severity finding fails
  the build; suppressions need a written justification in the PR.

---

## 12. Scope for this iteration

**In:** ingestion of the full feed, versioned and reconciled · normalisation ·
blocking · scoring · explanation · benchmark measurement · audit of loads and
config · CLI harness · handover docs.

**Out (descoped, still in the use case diagram, grey):** REST API and gateway ·
alerts and disposition · batch and re-screening · admin UI · reference consumer
integration · parallel run against LSEG · Tier 2 mapped sources · ownership
graph resolution.

**Never cut:** list versioning · explanation generation · benchmark · the three
DB roles.

If a task pulls toward something out of scope, stop and say so.

---

## 13. How to work in this repository

1. **One step at a time** from `docs/IMPLEMENTATION_PLAN.md`. Do not start the
   next step until the current step's acceptance criteria pass.
2. **Plan first** for anything touching more than one file: state the files you
   will create or change, the tests you will write, and which invariants apply.
   Wait for approval.
3. **Tests before implementation** in `core`. Make them fail first.
4. **Run `.\gradlew.bat build` before declaring done.** Report the actual result,
   including failures. Never say "should pass".
5. **Small diffs.** A reviewer should be able to read one step's change in a
   sitting.
6. **Stop and ask** when: a requirement is ambiguous · a change would touch the
   schema of an existing table · an invariant seems to conflict with a request ·
   you would need real feed data · you are about to add a dependency.
7. **Write an ADR** (`/adr`) for any significant technical choice: alternatives
   considered, evidence, decision. Required for: data model, similarity
   measures, index strategy, configuration storage, runtime.
8. **Do not speculate about a failure's cause.** Reproduce it, identify it, then
   fix it. In a screening system a plausible wrong fix hides a silent recall
   failure.
9. **Report what happened, including what did not work.** Candour about gaps is
   the job here.

---

## 14. Definition of Done — every step

- [ ] Acceptance criteria in the plan pass.
- [ ] `.\gradlew.bat build` is green, including ArchUnit.
- [ ] New code has tests; core coverage ≥ 80%.
- [ ] No secrets, no real names, no feed data in the diff.
- [ ] Every applicable invariant has a test that would fail if it were broken.
- [ ] Docs updated beside the code; ADR written if a choice was made.
- [ ] A reviewer could read the diff in one sitting.

---

## 15. Where things are

| Path | Contents |
|---|---|
| `docs/spec/Project_Context.md` | The authoritative specification |
| `docs/IMPLEMENTATION_PLAN.md` | The step plan and ready-to-run prompts |
| `docs/design/` | Class, ingestion, use case and package diagrams (`.drawio`) |
| `docs/adr/` | Architecture decision records |
| `docs/runbook/` | Operational procedures |
| `.claude/commands/` | `/step`, `/invariants`, `/adr` |
