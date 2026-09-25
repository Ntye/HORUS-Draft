 # Runbook — Watchlist ingestion

Scope: the `ingest`, `confirm` and `rollback` CLI commands built in Step 5
(`horus.adapter.cli`), driving the interactors in `horus.application.ingestion`.
Covers §20.4's "ingestion failure diagnosis and re-run" and "list rollback to a
previous version." The rest of §20.4's runbook items (threshold change,
performance triage, consumer onboarding, whitelist, alert backlog, audit
extract, disaster recovery) are out of this iteration's scope (CLAUDE.md §12)
and not covered here.

---

## 1. Prerequisites

- The three DB roles exist and the schema is migrated (Step 0/2). `bootRun`
  applies pending Flyway migrations automatically, against `horus_admin`.
- Environment variables (read only in `horus.bootstrap`, per CLAUDE.md §3/§9):

  | Variable | Required for | Notes |
  |---|---|---|
  | `HORUS_DB_ADMIN_PASSWORD` | every run | Flyway + the `horus_admin` connection |
  | `HORUS_INGEST_PASSWORD` | every run | the ingest-side repositories |
  | `HORUS_AUDIT_PASSWORD` | every run | audit event recording |
  | `HORUS_DB_HOST`, `HORUS_DB_PORT`, `HORUS_DB_NAME` | optional | default `localhost`, `5432`, `horus` |
  | `HORUS_FEED_PATH` | `ingest` only | absolute path to the source file. **Never** a path inside this repository. |
  | `HORUS_OPERATOR` | `confirm`, `rollback` | identifies who is confirming/rejecting/rolling back, for the audit trail. `ingest` defaults its `loadedBy` audit actor to `svc-ingest` if unset. |

- `docker compose up -d db` for a local Postgres with the roles and extensions
  already created (Step 0).

**Claude Code / any AI agent working in this repo must never set `HORUS_FEED_PATH`
to a file it can read, and must never open the file it points at.** Running the
commands below against the real feed is the human operator's job.

---

## 2. Running a load

```powershell
$env:HORUS_FEED_PATH = "D:\feeds\worldcheck\wc_2026_09_21.xml"
.\gradlew.bat :bootstrap:bootRun --args="ingest --source=worldcheck"
```

What happens, in order (§13.1 ACQUIRE through PROMOTE, as actually implemented
in `LoadWatchlistVersion`):

1. The file is read once to compute its SHA-256 checksum.
2. If a currently-`ACTIVE` list version for `worldcheck` has the **same
   checksum**, the command prints `outcome: ALREADY_LOADED` and stops — no new
   list version, no re-parsing. This is the file-level idempotency guard
   (re-running the identical file is a safe no-op).
3. Otherwise a new list version is staged (`status = STAGED`) and the file is
   streamed record by record: parsed, normalised through the one shared
   pipeline, and reconciled entity-by-entity against whatever is currently
   `ACTIVE` — see §3 for exactly what "reconciled" means.
4. If more than 5% of records fail parsing/mapping, the load is marked
   `FAILED` and stops here. The previous `ACTIVE` version is untouched.
5. Otherwise, entities present in the previous active version but not seen in
   this file become tombstones (`status = DELISTED`), and the measured
   capabilities (per-field coverage) are recorded on the list version.
6. If the measured capabilities fall outside `WorldCheckAdapter`'s expected
   tolerance (CLAUDE.md §6 — e.g. DOB coverage collapsing from ~27% toward
   0%), the load is marked `FAILED` and stops here too.
7. Otherwise, if the total changed fraction (added + amended + delisted,
   divided by the previous active count) exceeds **20%**, the version is left
   `STAGED` and the command prints `outcome: AWAITING_CONFIRMATION` — see §4.
   A source's **first-ever** load never hits this gate (there is no baseline
   to be anomalous relative to).
8. Otherwise the version is promoted: it becomes `ACTIVE`, the previous
   `ACTIVE` version (if any) becomes `SUPERSEDED`, and the command prints
   `outcome: PROMOTED`.

Every one of steps 4-8 records an `audit_event` row (see §6).

**Exit codes:** `0` = `PROMOTED` or `ALREADY_LOADED`; `1` = `REJECTED`; `2` =
`HORUS_FEED_PATH` not set; `3` = `AWAITING_CONFIRMATION` (not an error — a
required human decision).

---

## 3. Reading the reconciliation report

The command prints, after the outcome:

```
outcome: PROMOTED
listVersionId: 3f6a1e2c-...
added=1204 amended=318 unchanged=5988712 delisted=160 rejected=0
```

| Field | Meaning |
|---|---|
| `added` | Entities present in this file with no prior active version — a genuinely new listing (or the first-ever load). |
| `amended` | Entities that already had an active version, but whose content hash changed (name, DOB, identifiers, countries, addresses, designations or ownership — see `ContentHasher`). A new, immutable `WatchEntityVersion` row is written; the previous one is never touched (I-6). |
| `unchanged` | Entities whose content hash matches the previous active version exactly. **No row is written for these** — this is what makes re-running an unchanged file produce zero new versions. |
| `delisted` | Entities that were active before but do not appear in this file at all. Each becomes a tombstone version (`status = DELISTED`), never a delete (I-6). |
| `rejected` | Records that failed to parse or map at all, with a reason per record, keyed by the source's record ordinal — **never by name** (I-11). If present, read every one individually; more than 5% aborts the load outright (see §2 step 4). |

If the outcome is `REJECTED`, a `failureReason` line follows explaining why
(reject-fraction exceeded, or which capability slots deviated from
tolerance and by how much).

The full field-by-field format, aimed at Compliance rather than an operator,
is in `docs/D4-reconciliation-report-format.md`.

---

## 4. Confirming or rejecting a held list version

Two things leave a load `STAGED` and awaiting a decision, and the load's
`failureReason` says which:

- an **anomalous delta** — more than 20% change against the previous active
  count (§13.1);
- a **capability deviation** — the measured shape of the file falls outside the
  adapter's expectation. Until 2026-09-25 this failed the load outright, which
  discarded hours of work over a figure a person has to rule on anyway
  (D12 H-10).

In both cases the rows are written and the version-row count has been checked;
the *previous* version keeps serving screening traffic. Nothing about this state
is urgent, but the new data stays invisible until someone decides.

Use the wrapper, not `gradlew bootRun`: a reason contains spaces, and Gradle would split
`--args="..."` on them before picocli ever saw it.

```powershell
$env:HORUS_OPERATOR = "j.reviewer"
.\scripts\Invoke-Horus.ps1 confirm --list-version-id=3f6a1e2c-... `
    --decision=CONFIRM --awaiting=CAPABILITY_DEVIATION `
    --reason="head-of-file slice; coverage figures are whole-file properties"
```

- `--decision=CONFIRM` promotes the staged version exactly as step 8 above
  would have (supersedes the previous active version, records
  `LIST_VERSION_PROMOTED`), plus `STAGED_VERSION_CONFIRMED`.
- `--decision=REJECT` marks the staged version `FAILED` and leaves the previous
  version active — use this when the deviation or delta reflects a bad source
  file rather than a real change to the list. Records `STAGED_VERSION_REJECTED`.
- `--awaiting` is `ANOMALOUS_DELTA` or `CAPABILITY_DEVIATION`. It states what
  was being resolved, so the audit row stands on its own.
- `--reason` is **mandatory**. This is the only path by which a person
  overrides a control that has just fired, and an override with no recorded
  justification is indistinguishable from having no control. It is written to
  the append-only audit trail with the decision.

Only a version that is still `STAGED` can be confirmed or rejected; the
command refuses otherwise (e.g. if someone already actioned it).

---

## 5. Rolling back

```powershell
$env:HORUS_OPERATOR = "j.reviewer"
.\gradlew.bat :bootstrap:bootRun --args="rollback --source=worldcheck --reason=""bad DOB coverage on the 2026-09-21 load"""
```

This marks the currently `ACTIVE` version `FAILED` (not `SUPERSEDED` — the
audit trail distinguishes "retired because it was bad" from "retired because
a newer good load superseded it") and repoints the most recently
`SUPERSEDED` version for that source back to `ACTIVE`. If there was no prior
version, the command reports `nowActive: none` — the source has no active
list version until a new load is promoted.

Rollback never deletes anything (I-6): the bad version's rows remain in
`watch_entity_version` etc., just no longer reachable through the active
pointer.

---

## 6. Diagnosing a failed load

**Every failure keeps the previous `ACTIVE` version serving.** Nothing about
a failed or awaiting-confirmation load is visible to screening until someone
explicitly promotes it.

| Symptom | Where to look | Likely cause |
|---|---|---|
| `outcome: REJECTED`, `failureReason` mentions "% of records failed validation" | The printed `rejected=` count and, in `audit_event` (`event_type = 'LIST_VERSION_LOAD_REJECTED'`), the `rejectedCount` payload field | Source file schema drift, encoding corruption, a mapping regression in `WorldCheckAdapter`. Re-run `VerifySourceFixture` against the current build before re-attempting the load. |
| `outcome: REJECTED`, `failureReason` mentions "capabilities fell outside tolerance" | `audit_event` payload `deviations`, and the `capabilities` JSONB column on the `list_version` row (`list_version_id` from the printed output) | A field that's normally populated at a known rate (e.g. DOB at ~27%) collapsed or spiked — almost always a source-side or mapping problem, not a real change in the list. Do not confirm past this; fix the cause and re-run. |
| `outcome: AWAITING_CONFIRMATION` | Compare `added`/`amended`/`delisted` against the previous active count | Either a genuinely large list update (e.g. a bulk delisting event) or a bad/truncated source file. Confirm only after independently corroborating the change is real — see §4. |
| Command exits `2` | stderr: `HORUS_FEED_PATH is not set` | Environment not configured; see §1. |
| `confirm`/`rollback` throws `IllegalStateException` | stack trace names the reason (e.g. "only a STAGED list version...") | Wrong `--list-version-id`, or the version was already actioned. Check `list_version.status` for that id. |

Re-running after any fix is always safe: `ingest` is idempotent per §2 step 2,
and a `FAILED` version never blocks a subsequent load attempt.

---

## 7. Audit trail

Every load, promotion, rollback and confirmation decision records one or more
rows in `audit_event` (`horus_audit`, INSERT/SELECT only — I-5). Event types
in use: `LIST_VERSION_STAGED`, `LIST_VERSION_PROMOTED`,
`LIST_VERSION_PROMOTION_FAILED`, `LIST_VERSION_LOAD_REJECTED`,
`ANOMALOUS_DELTA_AWAITING_CONFIRMATION`, `STAGED_VERSION_CONFIRMED`,
`STAGED_VERSION_REJECTED`, `LIST_VERSION_ROLLED_BACK`. Every row's
`subject_id` is the `list_version_id`; `actor` is `loadedBy`/`HORUS_OPERATOR`.
Payloads carry only counts, ids and reasons — never a screened or listed name
(I-11).
