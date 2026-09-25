# First real load — step by step

A single-purpose runbook for loading the **real World-Check feed** for the first time (plan Step 6).
Everything here is yours to run: an agent never touches the real file (CLAUDE.md §3). Each step says
what to paste back, so the result can be diagnosed without anyone reading feed content — counts,
ordinals and coverage percentages are enough.

For the synthetic walkthrough of every other module, see `testing-guide.md`.

---

## State verified before you start (2026-09-25)

| Check | Value |
|---|---|
| Feed location | `C:\horus-data\world-check.xml` — outside the repository ✓ |
| Size | 10,610,527,365 bytes (9.88 GiB) |
| `HORUS_FEED_PATH` (user scope) | `C:\horus-data\world-check.xml` — matches ✓ |
| Repository root | no feed file present ✓ |
| Database | **dirty — reset needed.** The first real load (2026-09-25) rejected and left 10,460 committed `watch_entity` rows, 10,460 versions, 63,040 names and one `FAILED` `list_version`. Re-run Step 1 before the next attempt |
| Adapter mapping | **corrected 2026-09-25** against the path inventory, with the first-ever fixture test for it (`WorldCheckAdapterTest`, 18 cases). Full build green: 478 tests, 0 failures |
| Write failures | **no longer fatal (2026-09-25).** A row a column cannot hold now quarantines that record with a `PERSIST_FAILED:` reason, counted against the 5% gate, instead of aborting the run with a stack trace. The country NAME that caused the abort now goes to `watch_address.raw_address`, not to `country_code` |

Expected record count when this is done: **5,990,394** (CLAUDE.md §6).

---

## Step 1 — Start from an empty database  ✅ DONE (2026-09-25)

> Already carried out: 5 synthetic list versions removed with the volume; `db` healthy; roles
> `horus_admin, horus_audit, horus_ingest, horus_screen` and extensions `pg_trgm, unaccent` present;
> `SELECT count(*) FROM list_version` errors with `relation "list_version" does not exist`, which is
> the correct empty-state answer. **Re-run it only if you load the extract in Step 2b.**

The database currently holds synthetic entities under `source_id = 'worldcheck'`, the same source id
the real feed uses. Loading on top of them trips the anomalous-delta gate (5.99M vs 100), so the load
would run for hours and then halt at `AWAITING_CONFIRMATION`; the synthetic entities would be
tombstoned; and real and invented names would be interleaved in one source's version history — which
**cannot be undone**, because versions are immutable (I-6).

```powershell
cd "C:\Users\nntyeeboo\OneDrive - Afreximbank\Documents\Projects\HORUS-Draft"
. .\scripts\Set-LocalEnv.ps1
docker compose --profile tools down -v
docker compose up -d db
docker compose ps
```

Wait for `Up … (healthy)`, then confirm it is genuinely empty:

```powershell
docker compose exec -T db psql -U horus_admin -d horus -At -c "SELECT count(*) FROM list_version"
```

**Expected:** an error (`relation "list_version" does not exist`) or `0`. Anything else means you are
not starting clean — stop and say so. The schema is created automatically on the first application run.

---

## Step 2 — Prove the mapping on 200 records (do not skip)

The reject-fraction check and the capability gate both evaluate **after the whole file has streamed**
(`LoadWatchlistVersion` line 248). A wrong element name or a mis-mapped field therefore costs the
entire multi-hour run. This answers it in seconds, and prints only structure — never record content:

```powershell
.\scripts\New-FeedExtract.ps1 -SourceFile $env:HORUS_FEED_PATH -OutFile C:\horus-data\extract-200.xml -Records 200
```

**Paste back the `structure:` block.** It looks like this:

```
structure:
  root element   : <records>
  record element : <record>
  -> matches the configured XmlFormatReader("record"). No change needed.
```

| What it says | What it means | What happens next |
|---|---|---|
| `matches the configured XmlFormatReader("record")` | The parser will find records | Go to Step 3 |
| `MISMATCH … would find ZERO records` | The parser would find nothing | **Stop.** Paste the block; the mapping gets fixed and re-tested on this extract before any full run |

### 2a. Inventory the real structure (added 2026-09-25, after the first attempt failed)

Step 2 proves the *record element* name. It says nothing about the paths **inside** a record, and
that is where the first real load failed: 5,979,934 of 5,990,394 records rejected, because the
adapter's element paths were invented alongside the synthetic fixture (the fixture file says so in
its own header comment) and section 6 documents the feed's *signals*, never its *paths*.

This prints the paths, their record coverage, and which ones the adapter actually reads — names and
counts only, never values:

```powershell
.\scripts\New-FeedPathInventory.ps1 -SourceFile $env:HORUS_FEED_PATH -Records 5000
```

Five thousand records answer the mapping question in seconds. Drop `-Records` to inspect the whole
file (~4 minutes, one structural pass) when you want coverage percentages comparable with section 6 —
DOB about 27%, passport about 0.74%, country 100% — and the full `@category` vocabulary, which is the
other known gap: `WorldCheckAdapter.E_CATEGORY_TYPES` currently holds six invented `CAT-*`
placeholders.

**Paste the whole report.** The `PATHS THE ADAPTER ASKS FOR BUT THIS FILE DOES NOT HAVE` block is the
answer. The `CODE VOCABULARIES` block closes three other gaps at once: `@category` replaces the
invented `CAT-*` placeholders, `details/countries/country` settles whether the feed writes ISO codes
or country names (which is what `CountryComparator` compares a query's `--country` against), and
`details/keywords/keyword` shows the list-source codes.

### 2b. Optional but recommended — load the extract first

Twenty seconds of proof that the *fields* map, not just the element name. It has to run under
`--source=worldcheck`: that is the only source id `AdapterRegistry` registers, so a throwaway id would
be rejected before any parsing. The extract's 200 records therefore land in the real source's history,
which is why the reset below is mandatory rather than tidy-up.

```powershell
$env:HORUS_FEED_PATH = "C:\horus-data\extract-200.xml"
.\scripts\Invoke-Horus.ps1 ingest --source=worldcheck
```

**Paste the whole output.** `added=200` with `rejected=0` means the mapping works. A large `rejected`
count, or a capability-gate rejection, is exactly what this step exists to surface — and the reasons
come back as record *ordinals* and coverage figures, never names.

Then put the path back:

```powershell
$env:HORUS_FEED_PATH = [Environment]::GetEnvironmentVariable("HORUS_FEED_PATH","User")
```

…and redo **Step 1**. This is not optional: versions are immutable (I-6), so if the extract's 200
records are still present when the real load runs, they become its baseline — the delta gate trips and
those 200 get tombstoned inside the real source's history, permanently.

### 2c. Re-measure throughput before committing to the whole file (added 2026-09-25)

Two attempts on the real feed measured the persistence path, and each one moved the diagnosis:

| Attempt | Rate | Full feed | What it showed |
|---|---|---|---|
| Rows one at a time | 34 rec/s | ~47 h | `map 0% / persist 100%` — persistence was the whole cost |
| Batched per entity | 60 rec/s | ~28 h | only 1.76x: a batch scoped to one entity is usually one row |
| Batched per chunk of 1,000 entities | **1,124 rec/s** (1,470 in steady state) | **~1h07** projected | commits and round-trips both down ~1,000x |

The second attempt is why the third exists. Removing the repeated `prepare` calls left the two things
that actually cost: **one commit and about ten round-trips per entity**. Measured on this database
with `fsync` and `synchronous_commit` both on — 2,000 rows as 2,000 transactions took **5,609 ms**, the
same rows in one transaction **35.6 ms**, as one multi-row statement **21.0 ms**. Commit frequency
alone was worth **158x**, and the loader was paying it 5,990,394 times.

So the write unit is now a chunk of 1,000 entities: one transaction, one statement per table.
Identities are resolved in one round-trip per chunk too. A chunk that fails is replayed one record at
a time, so a single bad row still quarantines a single record rather than losing 999 good ones.

Also in place from the earlier attempts: `reWriteBatchedInserts=true` on the JDBC URL, and Postgres
started with `shared_buffers=2GB`, `maintenance_work_mem=1GB`, `max_wal_size=8GB`,
`checkpoint_timeout=15min`. `fsync` and `synchronous_commit` were deliberately left **on** — a load
whose committed rows can vanish on a power cut is not evidence.

Measure the effect on the **same first 100,000 records**, so the result is comparable with the 34 rec/s
baseline rather than with an estimate:

```powershell
.\scripts\New-FeedExtract.ps1 -SourceFile C:\horus-data\world-check.xml -OutFile C:\horus-data\extract-100k.xml -Records 100000
```

**Then reset, before anything else.** Burying this in prose is what made the first 2c attempt
measure nothing at all — every record was rejected on a collision with the stopped run's rows, and a
rejected record never reaches the write path being measured:

```powershell
docker compose --profile tools down -v
docker compose up -d db
docker compose ps                     # wait for Up ... (healthy)
docker compose exec -T db psql -U horus_admin -d horus -c "SHOW shared_buffers; SHOW max_wal_size;"
```

The `SHOW` confirms the recreated container picked up the bulk-load settings: expect `2GB` and `8GB`.
Then run:

```powershell
$env:HORUS_FEED_PATH = "C:\horus-data\extract-100k.xml"
$env:HORUS_EXPECTED_RECORDS = "100000"
$start = Get-Date
.\scripts\Invoke-Horus.ps1 ingest --source=worldcheck | Tee-Object -FilePath C:\horus-data\remeasure-100k.txt
"elapsed: {0}" -f ((Get-Date) - $start)
```

**Measured on 2026-09-25: 1 124 rec/s average, 1m28s for 100,000 records.** Instantaneous rate rose
across the run — 806, 1 042, 1 562, 1 470 rec/s — so the steady state is ~1,500 rec/s once the JVM is
warm. That is **18.7x** the previous attempt and **33x** the first one.

What it wrote, counted afterwards:

| | 100,000 records | Full feed (projected) |
|---|---|---|
| Rows | 659,549 (**6.6** per record) | ~35 M |
| Storage | 325 MB, of which 143 MB indexes | **~17–20 GB** |

**Disk space: have ~30 GB free** — ~20 GB of data and indexes plus up to 8 GB of WAL
(`max_wal_size=8GB`). The XML itself is 9.88 GiB and is read twice but never copied.

**Expected duration for the full feed: 1h to 4h.** The extract projects ~1h07 at steady state, and the
pure-insert floor (35 M rows at 17,500 rows/s) is ~33 minutes, so the two agree within a factor of two
— the client side is no longer the constraint. But those 17,500 rows/s were measured on a *small*
table: at 35 M rows the GIN trigram index will not fit in `shared_buffers=2GB` and maintenance will get
slower. Plan for several hours and read the progress line.

**The rate to act on:** if it falls below ~500 rec/s and keeps falling, stop the load and say so. The
remaining lever is building the four `watch_name` search indexes after the load instead of during it —
7.6x measured on this table shape — and that is a change to make deliberately, not at hour three.

**Expect `outcome: AWAITING_CONFIRMATION` and `exit=3`, and do not read it as a failure.** The
2026-09-25 run came back:

```
added=100000 amended=0 unchanged=0 delisted=0 rejected=0
failureReason: measured capabilities fell outside tolerance:
  [IDENTIFIER     expected=0.0074 actual=0.07489 tolerance=0.005,
   DATE_OF_BIRTH  expected=0.27   actual=0.73133 tolerance=0.05]
```

`rejected=0` is the line that matters: **the mapping is correct and every row was written.** The gate
compares against CLAUDE.md section 6, whose coverage figures are properties of the **whole file**,
measured by full-file profiling. An extract is the first N records — a contiguous slice, not a sample —
and the head of this feed is far richer in dates of birth and passports than the file as a whole. A
slice cannot satisfy a whole-file expectation, and the gate is right to say so.

Until 2026-09-25 this marked the load `FAILED`, and nothing moves a `FAILED` version back to `STAGED`,
so a deviation discarded the whole load — on the full feed, hours of work (D12 H-10). It now **holds**:
the rows stay `STAGED`, nothing screens against them, the previous `ACTIVE` version keeps serving, and
a person decides.

On a measurement run there is nothing to decide — reset and move on. **If Step 3 comes back this way**,
the decision is yours to take with Compliance, and it is recorded:

```powershell
# promote the held load
.\scripts\Invoke-Horus.ps1 confirm --list-version-id=<id> --decision=CONFIRM `
    --awaiting=CAPABILITY_DEVIATION --reason="<why this deviation is acceptable>"

# or discard it
.\scripts\Invoke-Horus.ps1 confirm --list-version-id=<id> --decision=REJECT `
    --awaiting=CAPABILITY_DEVIATION --reason="<why this file must not serve>"
```

`--reason` is mandatory and lands in the append-only audit trail. This is the only path by which a
person overrides a control that has just fired; an override with no recorded justification is
indistinguishable from having no control.

**Never widen the tolerances to make a load pass.** That would disarm the one control that notices the
feed changing shape — which is exactly the failure B-6 was. Deciding case by case, on the record, is
the supported way through.

Then reset both variables and redo **Step 1** again — 100,000 extract records left in place would
become the real load's baseline, and versions are immutable (I-6).

```powershell
$env:HORUS_FEED_PATH = [Environment]::GetEnvironmentVariable("HORUS_FEED_PATH","User")
$env:HORUS_EXPECTED_RECORDS = "5990394"
```

---

---

> **A retry no longer needs a reset to succeed — but a *measurement* still does.**
>
> Until 2026-09-25 a retry after a failed load rejected every record the failed attempt had reached:
> its `watch_entity` identity rows were committed, their versions belonged to a list version that
> never became `ACTIVE`, so the next run could not see them, treated every record as new, and
> collided. It cost two runs — `added=188 rejected=12` on the 200-record extract, then
> `rejected=100000` out of 100,000 on the throughput measurement. Identity is now *resolved* rather
> than asserted, so a retry adopts the rows already there (D12 H-9, closed).
>
> Two reasons a reset is still required in this runbook, neither of them that defect:
>
> - **A throughput measurement is void without one.** Rejected records skip the write path entirely,
>   so a run that rejects everything measures the rollback, not the load. That is exactly what
>   happened on the first 2c attempt.
> - **Extract records left in place become the real load's baseline.** Versions are immutable (I-6):
>   once the real feed loads on top of 200 or 100,000 extract records, the delta gate trips and those
>   records are tombstoned inside the real source's history, permanently.
>
> A load that fails now also gives up early rather than streaming the whole file: 1,000 records with
> zero successes and it stops, naming the dominant reason code. If you see
> `abandoned after 1000 records`, the condition was true at record 0 — read the reason, don't re-run.

## Step 3 — The real load

Use the wrapper (`java -jar`), **not** `gradlew bootRun`: a Gradle daemon timeout or an IDE restart
kills a `bootRun` process mid-load.

```powershell
.\scripts\Invoke-Horus.ps1 ingest --source=worldcheck | Tee-Object -FilePath C:\horus-data\ingest-log.txt
```

`Tee-Object` keeps a copy, because the report at the end is the thing that matters and a long run is
easy to lose. The log holds counts, ordinals and ids only — no names — but keep it outside the
repository anyway, with the feed.

**It now reports itself.** Every 25,000 records the load prints one line, and one more when the
record loop ends:

```
  progress: 250,000 records (4.2% of 5,990,394), 6m13s elapsed, 670 rec/s, map 71% / persist 29%, added=249,988 rejected=12, ~2h13m left
```

Set `HORUS_EXPECTED_RECORDS=5990394` first (CLAUDE.md §6) and you get the percentage and the estimate;
without it you still get records, rate and the split. That line exists because the first attempt at the
real feed ran for ninety minutes in silence and then died, and telling "stuck" from "slow" needed
queries against a live process.

**`map` versus `persist` is the number to watch.** It says which half to fix if the run is too slow:
`persist` still high after the 2c changes points at the indexes rather than the round-trips; `map` high
means the narrative parser, which
runs over text reaching 125,644 characters on every record.

**While it runs:** leave it alone. The file is read twice (once to checksum, once to parse) and a
structural pass alone took 229 s on this feed (D12 M-2), so expect hours rather than minutes — but
expect roughly the rate Step 2c measured, and if the progress lines show materially less, stop it and
say so rather than waiting it out. Memory is fine: 31.5 GB RAM against the ~0.6–1 GB the in-memory set
of 6M source ids needs on a first load.

---

## Step 4 — Paste the result

The command prints a report like this, then `exit=N`:

```
outcome: PROMOTED
listVersionId: <uuid>
added=5990394 amended=0 unchanged=0 delisted=0 rejected=0
```

**Paste all of it, including the `exit=` line.** Then these queries, which are the real acceptance
check:

```powershell
docker compose exec -T db psql -U horus_admin -d horus -c "SELECT source_id, status, record_count, pipeline_version, loaded_at FROM list_version ORDER BY loaded_at"
docker compose exec -T db psql -U horus_admin -d horus -c "SELECT jsonb_pretty(capabilities) FROM list_version WHERE status='ACTIVE'"
docker compose exec -T db psql -U horus_admin -d horus -At -c "SELECT count(*) FROM watch_entity"
```

The capability JSON is the interesting one: it is what the loader *measured*, and it can be compared
against §6 (DOB ≈0.27, passport ≈0.0074, country 1.0, names and entity type 1.0). It contains
coverage fractions, not data.

---

## How to read each outcome

| `outcome` | `exit` | Meaning | Next |
|---|---|---|---|
| `PROMOTED` | 0 | Loaded, reconciled, now serving | Paste Step 4's queries — Step 6 acceptance is then met |
| `ALREADY_LOADED` | 0 | A version with this exact checksum is already active | Nothing to do; idempotency working |
| `AWAITING_CONFIRMATION` | 3 | Staged and complete, awaiting a human decision. `failureReason` says which: **capability deviation** (the file's shape is outside the adapter's expectation) or, when it is absent, an **anomalous delta** (> 20% change against the previous active count). Nothing screens against it yet | Paste the report. A capability deviation on the full feed is a Compliance decision, then `confirm` with `--awaiting` and `--reason`. An anomalous delta on a first load means Step 1 was skipped |
| `REJECTED` | 1 | Rejected; the previous version keeps serving and nothing was promoted | Paste `failureReason`. It names one of: the reject fraction, a version-row count mismatch, or an abandoned load (`abandoned after 1000 records`) — that last one means the condition was true at record 0, so read the reason rather than re-running |
| `added=0 rejected=0` | 0 | The parser matched no records | The element name is wrong; Step 2 should have caught it |
| `rejected` ≈ the whole file | 1 | Records were found and then failed mapping — an element-path problem, not an element-name one | Run **Step 2a**. The reject reason is the same for nearly every record and the inventory names it |
| `Configuration error: HORUS_… is not set` | 78 | A credential is missing for the role that command needs | Dot-source `Set-LocalEnv.ps1` and re-run |
| A stack trace mentioning `NoSuchFileException` | non-zero | `HORUS_FEED_PATH` and the file disagree | Re-check the path |

A rejected or interrupted load leaves the previously active version serving, so nothing that was
already being served is disturbed, and on a freshly reset database a failure means nothing is served
— the fail-closed outcome (I-1), not a silent empty result.

**But a rejected load does not undo its own writes.** `TransactionAtomicityIntegrationTest` proves
atomicity **per entity version** — `JdbcWatchEntityVersionWriter` opens one transaction per entity —
not per load. Records mapped before the reject gate fired are committed and stay committed; only the
`list_version` row flips to `FAILED`. So after any outcome other than `PROMOTED` or `ALREADY_LOADED`,
check what is left behind and reset before retrying:

```powershell
docker compose exec -T db psql -U horus_admin -d horus -c "SELECT source_id, status, record_count FROM list_version" -At -c "SELECT count(*) FROM watch_entity"
```

---

## After a successful load

Step 6's acceptance criteria are: all **5,990,394** records loaded, counts reconciled, checksum
recorded, and measured capabilities matching §6 within tolerance. Once those hold, the next real-data
milestones are blocking recall (plan Step 7) and the benchmark corpus built with Compliance (Step 10)
— both in `testing-guide.md` §16, and both gating any recommendation to rely on HORUS (D12 B-1).

Screening against the real list also needs a published configuration:

```powershell
.\scripts\Invoke-Horus.ps1 publish-config --defaults --approved-by <name>
```

The shipped thresholds (alert 55, strong 75) are **uncalibrated placeholders**, not an approved
operating point — that decision is Compliance's, made on benchmark evidence (spec §16.4, D12 B-2).
