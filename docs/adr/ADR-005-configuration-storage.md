# ADR-005 — Configuration storage and validation

- **Status:** Proposed
- **Date:** 2026-09-24
- **Review:** Solution Architect; Compliance (they own the operating point, spec §16.4)

## Context

I-7: thresholds, weights and list selection are versioned configuration, never a code release, and
an invalid configuration must fail the **boot**, not a screening. Every screening records the
configuration version it used (I-12). Compliance owns the operating point; engineering supplies
evidence and a recommendation.

## Options considered

1. **Environment variables / files read at start.** Simple; not versioned, no approval record, and a
   screening cannot cite which values it ran on.
2. **A `config_version` table: immutable, versioned rows (chosen).** Each row carries the profile,
   the JSON payload, creator, approver and effective-from. A screening stores the row's id.
3. **A configuration service.** Out of proportion for this iteration.

## Evidence

- `PublishConfigVersion` validates a configuration against `Registry.validateAgainst(cfg,
  capabilities)` **before** saving; `LoadScreeningConfig` validates again before every screening.
  Both are covered by tests that assert an invalid configuration is refused and nothing is saved,
  scored or audited.
- Validation rules (each has a test in `RegistryValidationTest`): every referenced measure and
  comparator exists; weights are non-negative and sum above zero; `0 < alert < strong ≤ 100`; and a
  comparator may be **enabled only if the active sources measured non-zero coverage** for the slot it
  needs. Observed live: the default configuration was accepted against the synthetic list, and an
  invalid one is refused with every problem listed.
- Roles: `horus_ingest` may INSERT and SELECT `config_version`; `horus_screen` may only SELECT.
  Neither can UPDATE or DELETE a row. Proven in `ScreeningRolePrivilegesTest` (connected as the
  restricted roles).
- Only an **approved**, already-effective version is ever used (`approved_by` and `effective_from`
  set); the newest wins, ties broken by id (I-4).
- Blocking settings (strategy list, cap, trigram threshold) are still environment-sourced
  (`HORUS_BLOCKING_*`), validated at boot, and are **not** yet part of `config_version`.

**Not evidenced:** an approval workflow. `approved_by` is asserted by the operator who publishes;
the audit event records who claimed what. There is no second-person approval.

## Decision

Option 2. The shipped defaults (alert 55, strong 75, spec §16.2 weights) are an **uncalibrated
starting point**; publishing them is a mechanical step, not an approval of the operating point.

## Consequences

- A screening is reproducible from (list versions, config version, pipeline version).
- Changing a threshold is publishing a new row; the old one stays as evidence.
- Follow-up: move the blocking settings into `config_version`; add second-person approval when the
  admin surface (descoped) is built.
