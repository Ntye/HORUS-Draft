# ADR-001 — Data model and versioning

- **Status:** Proposed
- **Date:** 2026-09-24
- **Review:** Solution Architect; Compliance (the de-listing rule affects screening policy)

## Context

HORUS ingests a licensed feed of 5,990,394 records and must be able to say, for any past
screening, *exactly which list, at which version, produced that result* (I-12, I-6). The feed
gives us no status, no de-listing flag and no designation date in its structured record; those live
only in narrative text, and 22.77 % of sanctioned records in a strided sample are removed from every
list that designated them yet remain in the feed (CLAUDE.md §6). Constraints:

- I-6: watchlist data is never hard-deleted; de-listing is a tombstone.
- I-5 / I-12: evidence is append-only and every result carries its list versions.
- Recall is binding (I-2): an entity that is de-listed *here* may still be designated *elsewhere*,
  so it must remain screenable.
- A full structural pass over the feed takes ~229 s; a load must stream (CLAUDE.md §6).

## Options considered

1. **Full snapshot per load.** Copy every entity and child row into each new list version.
   Simple to reason about; ~6 M entity rows plus all children written per load, and "what changed"
   has to be derived by diffing.
2. **Effective-dated rows (SCD-2).** One row per entity with `valid_from`/`valid_to`, updated in
   place on change. Compact, but closing a `valid_to` is an UPDATE on evidence — incompatible with
   I-6 and with a database role that has INSERT only.
3. **Stable identity + immutable content-addressed versions + tombstones (chosen).**
   `WatchEntity` is never versioned or deleted. `WatchEntityVersion` is immutable and is created
   **only when `contentHash` changes**. An entity absent from a new full load gets a `DELISTED`
   tombstone version. Every child row keys on `entityVersionId`.

## Evidence

Measured on synthetic data through the real CLI and the real database (a 100-record feed;
`scripts/New-SyntheticFeed.ps1`):

| Load | Result |
|---|---|
| v1 (first load) | `added=100 unchanged=0 delisted=0` — PROMOTED |
| v1 again | `ALREADY_LOADED` — no new rows (checksum short-circuit) |
| v2 (one record removed) | `added=0 amended=0 unchanged=99 delisted=1` — exactly one tombstone |
| v1 again (entity reappears) | `amended=1` — a new ACTIVE version on the *existing* identity |

Two defects in the first implementation of option 3 were found by exercising it beyond two loads, and
are fixed and regression-tested (`LoadWatchlistVersionIntegrationTest`, `ReconcileVersionTest`):

1. Unchanged entities keep the version row written by the load that last changed them, so the
   "current version" of an entity must be resolved **across** superseded loads. The first
   implementation only looked at the active load, so a third load re-added every unchanged entity
   and failed on the unique key. Fix: view `current_watch_entity_version` (V5).
2. A tombstone carries the previous `contentHash`; hash equality alone would call a re-listed
   entity `UNCHANGED` and leave it delisted. Fix: `ActiveEntitySnapshot.delisted`, and a
   re-appearing tombstoned entity is always `AMEND`.

**Not measured:** load time, memory and storage on the real 5.99 M-record feed. Step 6 (the real
load) is run by the operator; until then the storage advantage of option 3 over option 1 is
argued, not measured. What to measure: rows written and wall-clock time for a first load, an
unchanged reload and a typical delta; peak heap of the active-entity snapshot
(`ActiveEntityVersionLookup` holds one entry per current entity — a known, deferred cost).

## Decision

Option 3. It is the only option that satisfies I-6 with an INSERT-only role, makes an unchanged
reload a no-op, and lets a de-listed entity remain screenable via its last live version.

Two consequences of the model are decided here:

- **A tombstone has no name rows.** Blocking therefore resolves names through the entity's *last
  live* version (view `screenable_watch_entity_version`, V6), while status is read from the
  *current* version. Without this, de-listing an entity would silently remove it from screening —
  a fail-open (I-2, I-8).
- **`list_version.loaded_at` is the ordering key** for a source's loads and must be strictly
  increasing per source. It is not enforced by a constraint (that would alter an existing table);
  the loader uses an injected `Clock` (I-4).

## Consequences

- Easier: reproducing any past screening; idempotent reloads; delta reports; rollback by repointing
  the ACTIVE list version.
- Harder: every read of "the current entity" goes through a view; queries must not assume the
  active load holds every entity.
- Irreversible once real data is loaded: the identity/version split and the `entityVersionId`
  child keys. Changing them means re-ingesting the feed.
- Open: the version-state ordering relies on `loaded_at`; a unique index on `(source_id, loaded_at)`
  would make that a database guarantee. Deferred because it touches an existing table (CLAUDE.md
  §13.6) — raise with the Solution Architect.
