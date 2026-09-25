from pathlib import Path

p = Path("docs/D12-production-readiness.md")
t = p.read_text(encoding="utf-8")

anchor = "## 2. High\n"
new_high = r"""## 2. High

### H-8 Country values are names, and the comparator is fed them as codes
Found on the second real load attempt, 2026-09-25. The feed writes country **names** --
`details/countries/country` and `location@country` hold "UNITED ARAB EMIRATES", not "AE". The
candidate view selects `COALESCE(iso_code, upper(raw_country))`, `iso_code` is never populated, and
so `CountryComparator` receives names. The CLI documents `--country` as "Country code".

A consumer screening with `--country NG` against a candidate whose country reads `NIGERIA` therefore
gets **no overlap**, which is not neutral: the comparator applies its *conflict* effect and demotes a
genuine match. Recall is the binding constraint (I-2), so a country filter that silently penalises
true positives is worse than no country filter at all.
**To close:** decide with Compliance what `--country` means to a consumer, then either normalise the
feed's names to ISO codes at ingest (populating `iso_code`, which already exists for this purpose) or
accept both forms and compare case- and form-insensitively. Whichever is chosen, measure the effect
on the benchmark before publishing an operating point (B-2).

### H-9 A failed record write leaves an orphan entity and a stuck load
Also found on 2026-09-25, and visible in the database afterwards: **12 `watch_entity` rows against 11
`watch_entity_version` rows**, plus a `list_version` left `STAGED` for ever.

`JdbcWatchEntityRepository.save` is a plain `INSERT`, and it runs before the version write, in a
separate transaction. When the version write fails, the identity row survives with no version. It is
then invisible to `loadActiveSnapshot` (which reads current versions), so the next load treats the
record as new, inserts the identity row again, and fails on it permanently. The load itself also
leaves its `list_version` in `STAGED` when it aborts rather than rejecting, so nothing ever records
the outcome.
**To close:** make the identity write idempotent against the `(source_id, source_entity_id)`
uniqueness the model already requires, or move it inside the version transaction; and have an
aborted load mark its `list_version` `FAILED` before rethrowing. Until then, a reset is the only
clean recovery -- which is what the runbook now says.

"""
assert anchor in t, "high anchor"
t = t.replace(anchor, new_high, 1)

old = """2. H-1 … H-7 closed, in particular a tamper-evident audit trail and a measured p95 within the KPI."""
new = """3. H-1 … H-9 closed, in particular a tamper-evident audit trail, a measured p95 within the KPI, and
   a country comparison that cannot demote a true positive for comparing a name with a code (H-8)."""
assert old in t, "ready anchor"
t = t.replace(old, new, 1)
t = t.replace("3. H-1 … H-9 closed", "3. H-1 … H-9 closed", 1)

p.write_text(t, encoding="utf-8")

rb = Path("docs/runbook/first-real-load.md")
r = rb.read_text(encoding="utf-8")
old2 = ("| Adapter mapping | **corrected 2026-09-25** against the path inventory, with the first-ever "
        "fixture test for it (`WorldCheckAdapterTest`, 18 cases). Full build green: 478 tests, 0 failures |")
new2 = ("| Adapter mapping | **corrected 2026-09-25** against the path inventory, with the first-ever "
        "fixture test for it (`WorldCheckAdapterTest`, 18 cases). Full build green: 478 tests, 0 failures |\n"
        "| Write failures | **no longer fatal (2026-09-25).** A row a column cannot hold now quarantines "
        "that record with a `PERSIST_FAILED:` reason and is counted against the 5% gate, instead of "
        "aborting the run with a stack trace. The country NAME that caused the abort now goes to "
        "`watch_address.raw_address`, not to `country_code` |")
assert old2 in r, "runbook state anchor"
r = r.replace(old2, new2, 1)
rb.write_text(r, encoding="utf-8")
print("docs updated")
