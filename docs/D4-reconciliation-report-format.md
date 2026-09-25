# D4 — Reconciliation report format

**Deliverable D4** (Project_Context.md §27, Week 5, owner: Compliance owner).
For review with Compliance: what the system reports after every watchlist
load, and what each number means for a designation decision.

This document describes the **format and meaning** of the report. It does
not contain results from a real load — Claude Code, which produced this
document, is barred from opening the licensed feed or its outputs
(CLAUDE.md §3); the first real report is produced when a human operator runs
the load described in `docs/runbook/ingestion.md`, against the real feed.

---

## 1. What the report is for

Every load of the watchlist (a full refresh or, in a later phase, an
incremental delta) must be **reconciled**: the system compares what it just
read against what is currently live, and reports exactly what changed,
before any of it becomes visible to screening. This is FR-007 ("reconciliation
report per run: added, amended, deleted, rejected, with reasons") and it is
the primary artefact Compliance uses to answer one question before signing
off on a load:

> **Does this change to the watchlist make sense, or does it look like
> something broke?**

The report is never the basis for a designation decision about a specific
person or entity — it is evidence about the *load*, not about any individual
record. HORUS does not make compliance decisions (CLAUDE.md line 1); this
report is part of the evidence trail Internal Audit and Compliance can test,
not a decision in itself.

---

## 2. Fields

| Field | Type | Meaning |
|---|---|---|
| `sourceId` | string | Which watchlist source this load was for (e.g. `worldcheck`). |
| `added` | count | Entities that were not previously on the active list and are now. Includes every entity on a source's first-ever load. |
| `amended` | count | Entities already on the active list whose details changed in some way — a new name variant, an updated date of birth, a new identifier, a changed designation or ownership stake. The *previous* version of the entity is never overwritten or deleted (CLAUDE.md I-6): both the old and new versions remain permanently queryable, so "what did we know about this entity on the day we screened against it" can always be answered. |
| `unchanged` | count | Entities whose details are byte-for-byte identical to what was already active. Nothing is written for these — they are the majority of every load after the first. |
| `delisted` | count | Entities that were on the active list before this load but do not appear in the new file at all. These are **never deleted** (I-6): each becomes a tombstone record, permanently retained, marked de-listed with the date of this load. A de-listed entity can still be surfaced by screening with a lower score and an explicit "de-listed" flag — matching is never silently suppressed by de-listing (I-8). |
| `rejected` | list of (record id, reason) | Records the system could not parse or map at all — a malformed field, an unrecognised structure. **Never contains a name or any other personal data** — only the source's own record identifier (I-11), so this list is safe to share outside the system without a data-handling review. If this list is non-empty, every entry should be individually understood before the load is trusted (CLAUDE.md §10: "measure recall... before scoring exists" applies in spirit here too — an unexplained rejection is an unexplained gap in coverage). |

Two numbers derived from the above, used to decide whether a load proceeds
automatically or needs a human decision:

- **Reject fraction** = `rejected count / total records in the file`. Above
  **5%**, the load is refused outright — nothing from it is written to the
  active list, and the previous load keeps serving. This is a data-quality
  circuit breaker, not a Compliance decision point; it should never fire in
  normal operation.
- **Delta fraction** = `(added + amended + delisted) / previous active
  count`. Above **20%**, the load is *not* refused, but it is held back from
  going live until a human confirms it is expected (see §4). A source's
  first-ever load is exempt (there is nothing to compare it against yet).

---

## 3. Worked example (synthetic — not a real load)

Illustrative only. All names and numbers below are invented for this
document; they do not describe any real run of the system.

```
outcome: PROMOTED
listVersionId: 3f6a1e2c-9c41-4b7a-9e0e-2f6a4e0b1234
added=1204 amended=318 unchanged=5988712 delisted=160 rejected=0
```

Reading this: 1,204 newly-designated entities, 318 existing entities with
some detail changed, 160 de-listings, no parsing failures, and 5,988,712
entities unchanged from the previous load — consistent with a routine daily
refresh of a list of this size. Delta fraction here is
`(1204 + 318 + 160) / (previous active count)`, comfortably under 20%, so the
load promoted automatically with no operator action needed.

A load that instead reported `delisted=1,400,000` against a similarly-sized
previous list would exceed the 20% threshold and stop at
`AWAITING_CONFIRMATION` — the kind of number that should prompt a call to
the vendor before anyone confirms it, not a routine sign-off.

---

## 4. What crossing a threshold means operationally

- **`REJECTED`** (reject fraction > 5%, or measured field coverage falls
  outside the tolerance established in CLAUDE.md §6 — e.g. date-of-birth
  coverage collapsing from its usual ~27%): nothing from this load is live.
  The previous list keeps serving screening traffic unchanged. This is a
  data-quality or pipeline problem to be fixed and re-run, not something
  Compliance needs to sign off on.
- **`AWAITING_CONFIRMATION`** (delta fraction > 20%): the load is parsed,
  reconciled and sitting ready, but nothing from it is live yet. This *is* a
  Compliance-relevant decision point — an operator (see
  `docs/runbook/ingestion.md` §4) must confirm the change is a real,
  expected event (e.g. a large vendor-side list restructuring) before it
  goes live, or reject it if it looks like a bad file.
- **`PROMOTED`**: the load is now the active list; the previous version is
  retained (marked superseded, never deleted) so any prior screening
  decision remains reconstructible against the list version that was active
  at the time.

Every one of these outcomes is recorded as an audit event (append-only,
CLAUDE.md I-5), independent of this report, so the report's numbers can
always be checked against the underlying evidence trail.
