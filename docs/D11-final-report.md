# D11 — Final report

**Iteration:** HORUS Enterprise Name Screening Service, first iteration (10 working days).
**Date:** 2026-09-24. **Status of this document:** engineering report for the Solution Architect and
the Compliance owner. It describes what was built and what was *measured*. Where no measurement
exists, it says so.

---

## 1. What this iteration delivers

A working, explainable screening pipeline driven from a CLI, with evidence stored for every
screening:

**Ingest → version → reconcile → block → score → explain → record → audit.**

| Capability | State |
|---|---|
| Ingest a source file, version it, reconcile against the active version | Built, working on synthetic data |
| De-listing as a tombstone; nothing ever hard-deleted | Built, tested across four consecutive loads |
| One normalisation pipeline, shared by ingestion and query | Built (12 steps, spec §15.2) |
| Candidate generation (blocking): exact hash, trigram, phonetic — union | Built, on PostgreSQL |
| Blocking recall measured in isolation | Built (`measure-blocking`) |
| Matching engine: 5 similarity measures, person/organisation strategies, 5 attribute comparators | Built, pure, no ML |
| Explanation stored whole, reconstructible by hand | Built, verified by `recompute()` |
| Screen a name end to end, with provenance and idempotency | Built (`screen`, `show-screening`) |
| Versioned, validated configuration | Built (`publish-config`, `config_version`) |
| Benchmark harness: recall, precision, FP/1,000, threshold sweep, per-miss list | Built (`benchmark`) |
| Three database roles enforcing segregation of duties | Built, proven by negative tests |
| Audit of loads, promotions, config publication and screenings | Built, append-only |

**Not delivered, by design** (descoped in CLAUDE.md §12): REST API and gateway · alerts and
disposition · batch and re-screening · admin UI · reference consumer integration · parallel run
against LSEG · Tier 2 mapped sources · ownership graph resolution.

## 2. Evidence

### 2.1 Automated tests

`.\gradlew.bat build` — **BUILD SUCCESSFUL**. **460 tests across 99 test classes, 0 failures,
0 skipped.**

| Module | Test classes | Tests |
|---|---:|---:|
| `core` (normalisation, blocking, matching, ingestion SPI) | 34 | 192 |
| `adapters` (real PostgreSQL via Testcontainers) | 22 | 97 |
| `application` (use cases against fakes) | 13 | 78 |
| `domain` (pure model) | 21 | 78 |
| `architecture-tests` (ArchUnit) | 6 | 6 |
| `bootstrap` (whole-application boot, role wiring, credentials) | 3 | 9 |

Line coverage where NFR-030 requires ≥ 80 %: **`horus.matching` 92.9 %**, `horus.normalisation`
95.2 %, `horus.blocking` 91.2 %, `horus.ingestion.format` 90.5 %, `horus.ingestion.spi` 81.1 %.
The gate is enforced by the build, not merely reported.

Which test guards which invariant: `docs/runbook/testing-guide.md`, Appendix B.

### 2.2 Ingestion and versioning (synthetic, through the real CLI and database)

| Load | Result |
|---|---|
| First load, 100 records | `PROMOTED added=100` |
| Same file again | `ALREADY_LOADED` — no rows written |
| One entity removed | `unchanged=99 delisted=1` — exactly one tombstone; prior versions still queryable |
| The entity returns | `amended=1` — a new ACTIVE version on the **existing** identity |

### 2.3 Blocking recall in isolation (synthetic, 5 labelled cases)

| Strategy | Recall |
|---|---|
| EXACT_HASH | 0.60 (3/5) |
| TRIGRAM | 1.00 (5/5) |
| PHONETIC | 1.00 (5/5) |
| **Union** | **1.00 (5/5)**, mean candidate set 1.8 |

Exact-hash alone misses spelling variants; the union recovers them. Five cases prove the
mechanism, **not** the recall of the system.

### 2.4 Match quality (synthetic)

All ten worked examples from spec §30 land in their stated bands (`WorkedExamplesTest`), and every
one is hand-reconstructible. Per-feature scores and a leave-one-out ablation are in
**ADR-002**, including two results that are *not* comfortable:

- **"Ali Hassan" vs "Alia Hassan" scores 80 — higher than the genuine variant pairs A1 (78) and
  A2 (71).** The measures cannot separate a one-character different-person pair from same-person
  spelling variants, because the two share a Double Metaphone code. This is a real precision cost
  that thresholds alone cannot fix.
- **"Africa Trading Company" vs "East Africa Trading Co" scores exactly 75**, the placeholder strong
  threshold. Generic-token down-weighting is not yet strong enough for organisations.

The benchmark harness runs and produces the deliverable shape (recall first, then precision, alert
volume, every miss by corpus line with a stage-1/stage-2 reason, threshold sweep, and a statement of
the run's limits). On the 10-case synthetic corpus: recall 0.857 (6 of 7; the seventh is a
deliberately mislabelled case, reported as `NOT_A_CANDIDATE`), precision 1.000, 0 false positives per
1,000 clear names.

### 2.5 Scale (synthetic, 500,000 entities / 600,000 names)

Measured with the exact SQL the blocking adapter runs (ADR-003):

| Strategy | Latency (median) |
|---|---|
| EXACT_HASH | 0.13 – 0.41 ms |
| PHONETIC | 0.33 – 0.82 ms |
| TRIGRAM at threshold 0.3 | 216 – 340 ms (79 – 107 ms in a later run) |

Trigram cost is governed by the similarity threshold: 225 ms / 4,330 entities at 0.3 falling to
38 ms / 179 at 0.5. Timings varied 2–3× between sessions; treat them as an order of magnitude.

### 2.6 Security controls

- **Segregation of duties is enforced by the database — and, since defect #9, actually in use.**
  Negative tests connect **as the restricted role** and assert refusal: `horus_audit` cannot UPDATE or
  DELETE `audit_event`; `horus_screen` cannot alter a stored screening, read the audit trail, or write
  the watchlist; `horus_ingest` cannot update a watchlist version or a config row. Verified by hand
  (testing-guide §12), at boot (`ApplicationBootTest`), and — the gap that mattered —
  `JdbcTemplateRoleWiringTest` asserts that each template the repositories actually hold connects as
  its own role. After that fix the whole flow was re-run end to end on the restricted roles: 46 of 48
  scripted checks pass (the 2 failures are an artefact of the check script's output capture, verified
  by hand), and **no GRANT was found missing** — the privileges in V1–V7 were right all along.
- **No personal data in logs.** `ScreeningEndToEndIntegrationTest` captures every Logback and JUL
  line at DEBUG during a real screening and asserts the screened name appears in none of them. The
  audit trail is queried for name fragments: zero rows.
- **Secret scanning: clean.** `gitleaks` over the working tree and the git history: no leaks, with
  one narrowly-scoped, written justification (`.gitleaks.toml`) for a sample `Idempotency-Key` UUID
  in the specification's API example — path **and** pattern must both match, so a real secret
  anywhere else is still reported.
- **SAST: clean after a fix.** Semgrep (`p/java`, `p/security-audit`, `p/owasp-top-ten`, `p/secrets`,
  `p/github-actions`) found **7 findings, all the same class**: every third-party GitHub Action was
  pinned to a mutable tag. All are now pinned to full commit SHAs with the tag recorded in a comment.
  Re-scan: **0 findings** across 166 rules and 269 targets. **No findings in the Java code, in any run.**
- **Dependency scanning: clean, after fixing a vacuous gate and two real vulnerabilities.** The CI
  gate as written scanned **nothing**: `dependencyCheckAnalyze` runs against the root project, which
  declares no dependencies of its own, so it reported "Found 0 vulnerabilities" over
  *"Dependencies Scanned: 0 (0 unique)"*. Switched to `dependencyCheckAggregate` with
  `failBuildOnCVSS = 7.0`. The first honest run scanned **72 dependencies and found 3
  vulnerabilities, 2 of them HIGH**, and correctly failed the build:

  | Dependency | CVE | Severity | Resolution |
  |---|---|---|---|
  | `commons-lang3` 3.14.0 (via `commons-text` 1.12.0) | CVE-2025-48924 | MEDIUM 5.3 | **Fixed** — `commons-text` → 1.15.0, which brings `commons-lang3` 3.20.0 (fix landed in 3.18.0) |
  | `icu4j` 75.1 | CVE-2025-5222 | HIGH 7.0 | **Fixed** — `icu4j` → 77.1 |
  | `org.jacoco.ant` 0.8.14 | CVE-2026-78254 | HIGH 7.4 | **Suppressed with justification** (see below) |

  Both upgrades were verified against the full test suite — **all tests still pass**, and
  normalisation output is unchanged, including the Arabic transliteration cases, so no re-ingestion
  is implied.

  The suppression is a genuine false positive on three independent grounds, recorded in
  `config/dependency-check-suppressions.xml`: dependency-check matched JaCoCo's
  `org.jacoco.ant:0.8.14` to the CPE `cpe:2.3:a:apache:ant:0.8.14`, but **Apache Ant has no 0.8.x
  release line** (it is at 1.x) — 0.8.14 is *JaCoCo's* version; the artifact is build-time coverage
  tooling that ships in nothing; and the vulnerable feature is Ant's `<ftp>`/`<scp>` tasks, which
  this build never invokes. JaCoCo 0.8.14 is the latest release, so no upgrade clears it. The
  suppression is scoped to that artifact **and** that CVE, so any other finding still fails.

  Final state: **71 dependencies scanned, 0 vulnerabilities, build green.** The gate is proven to
  work — it failed the build twice before these fixes.

## 3. Defects found and fixed during this iteration

Recorded because each was invisible to the tests that existed at the time, and each would have been
a production failure. All are now covered by regression tests.

| # | Defect | How it was found | Guard added |
|---|---|---|---|
| 1 | A third consecutive load crashed (`DuplicateKeyException`). Unchanged entities keep the version row from the load that last changed them, so "current version" must span **superseded** loads; the code only looked at the active one. | Added a fourth load to the integration test | `current_watch_entity_version` view (V5); `LoadWatchlistVersionIntegrationTest` |
| 2 | A **re-listed entity stayed de-listed**. A tombstone carries the previous `contentHash`, so hash equality alone called it `UNCHANGED`. A recall failure. | Reasoning about the tombstone's hash while fixing #1 | `ActiveEntitySnapshot.delisted`; `ReconcileVersionTest` |
| 3 | **The application could not start at all.** Spring proxies `@Repository` beans; CGLIB cannot subclass the `final` persistence classes. | First real boot | `@Component`; `ApplicationBootTest` |
| 4 | **The application did not create its own schema.** Spring Boot 4 moved Flyway auto-configuration to a separate starter; the app started against an empty database. | First real boot (verified: `\dt` returned nothing) | Explicit `SchemaMigrator` on the admin credential |
| 5 | **`@Transactional` was ineffective** on the ingest writer: the auto-configured manager is bound to the *primary* (admin) datasource, so it never wrapped statements on the `horus_ingest` connection. A failure mid-write could leave a half-written entity. | Reviewing #3/#4 | `TransactionTemplate` per datasource; `TransactionAtomicityIntegrationTest`, **confirmed to fail without the fix** |
| 6 | **5 of the 10 worked examples failed**: A1 scored 47 (needs 75), A2 68, A5 68, A7 30, A8 47. Particles (`al`, `bin`, `ben`) and unvocalised transliteration defeated the measures. | Writing the spec §30 tests before the implementation | Comparison-time particle/skeleton views; `WorkedExamplesTest` |
| 7 | `MatchConfig`'s `Set.of` fields iterated in the JVM's randomised order, so the stored config payload could differ between runs of the same configuration (I-4). | Invariant audit | Sorted copies; `MatchConfigTest` |
| 8 | **The CI dependency gate scanned zero dependencies** and passed vacuously (`dependencyCheckAnalyze` on a root project that declares none), and it would not have failed on a finding anyway. Two real vulnerabilities were hiding behind it. | Reading `Dependencies Scanned: 0` in the report rather than trusting "0 vulnerabilities" | `dependencyCheckAggregate` + `failBuildOnCVSS = 7.0`; §2.6 |
| 9 | **Segregation of duties was not in effect at runtime.** Every repository connected as `horus_admin`, not as its own role. The `JdbcTemplate` beans were declared `JdbcTemplate ingestJdbcTemplate(DataSource ingestDataSource)`, relying on the parameter NAME to choose among four `DataSource` beans — but Spring resolves by type first and `@Primary` (`adminDataSource`) outranks name matching. The GRANTs were correct and the negative-privilege tests passed, because those build their own connections; the application never used the restricted roles. | Running `ingest` with `HORUS_INGEST_PASSWORD` deliberately removed — it still succeeded, which it could only do on another credential | `@Qualifier` on every template, `@Primary` removed so an unqualified injection now fails at startup; `JdbcTemplateRoleWiringTest` asserts `current_user` per template and **was confirmed to fail before the fix** (`expected "horus_ingest" but was "horus_admin"`) |
| 10 | **Every command required every role's password.** `@Value` resolved all four placeholders during context refresh, so an operator running `ingest` had to hold the screening credential — and a missing one produced ~200 stack frames instead of a remedy. | An operator hit it running the real feed | Credentials resolved on first use (`LazyCredentialDataSource`); one-line message and exit **78**; `ApplicationBootTest` boots with the screening password absent, mutation-confirmed |

## 4. What is *not* evidenced

### 4.1 Nothing has been measured on real data

The real feed has never been loaded and no real name has ever been screened (CLAUDE.md §3 — the
agent may not touch it). Therefore:

- **Recall and precision on real names are unknown.** The synthetic figures in §2.4 demonstrate the
  harness, not the system's quality.
- **The thresholds are placeholders** (alert 55, strong 75), chosen so the illustrative §30
  examples land in their stated bands. They are **not** an approved operating point. Spec §16.4:
  the threshold is a business risk decision owned by Compliance, informed by benchmark evidence.
- **Ingest performance, memory and storage at 5,990,394 records are unknown.**
- The 500k-row latency figures are a synthetic stress case, roughly 12× smaller than the real feed.

### 4.2 CI has never run

The workflow exists and its actions are now SHA-pinned, but no push has exercised it. All three
scanners were run locally — Semgrep and gitleaks in containers, dependency-check through Gradle —
with the same rule sets and the same commands the workflow now uses.

The first NVD download took **41 minutes** without an API key. CI should set the `NVD_API_KEY`
secret (the workflow already passes it) or every build will pay that cost.

## 5. Conclusion

The pipeline works end to end on synthetic data, the invariants that protect against a silent recall
failure are enforced by tests that were each confirmed to fail when the protection is removed, and
the evidence a screening produces is complete enough to reconstruct a decision by hand.

**No recommendation to rely on HORUS for compliance screening can be made from this iteration**, for
one reason: match quality has not been measured on real data. The next three actions, in order, are
Step 6 (load the real feed), Step 7's real blocking-recall run, and Step 10's benchmark with
Compliance. The production-readiness gap list is D12.
