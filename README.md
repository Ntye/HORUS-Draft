# HORUS — Enterprise Name Screening Service

In-house name screening built on the World-Check bulk feed Afreximbank already licenses: a versioned
store, deterministic and explainable matching, and scored candidates with the evidence to justify
them. **HORUS does not make compliance decisions** — Compliance owns policy; this implements it.

Start with **CLAUDE.md** (the operating contract). Then `docs/IMPLEMENTATION_PLAN.md`.

## Try it in 5 minutes (synthetic data, no real feed needed)

```powershell
. .\scripts\Set-LocalEnv.ps1                                   # local secrets, kept outside the repo
docker compose up -d db                                        # PostgreSQL 16 with pg_trgm + the three roles
.\scripts\New-SyntheticFeed.ps1 -OutFile "$env:HORUS_SYNTHETIC_DIR\feed-v1.xml"
$env:HORUS_FEED_PATH = "$env:HORUS_SYNTHETIC_DIR\feed-v1.xml"
.\scripts\Invoke-Horus.ps1 ingest --source=worldcheck           # load, version, reconcile
.\scripts\Invoke-Horus.ps1 publish-config --defaults --approved-by me
.\scripts\Invoke-Horus.ps1 screen --name "Zorvan Talmesc" --type INDIVIDUAL --dob 1980
```

**`docs/runbook/testing-guide.md` is the full walkthrough** — every module, from launching Docker to
browsing the data in pgAdmin, with the expected output at each step and what to do when it differs.

## Tests

```powershell
.\gradlew.bat build          # 452 tests + ArchUnit + the coverage gate. Needs Docker running.
```

Which test guards which invariant: testing-guide Appendix B.

## Where things are

| Path | Contents |
|---|---|
| `CLAUDE.md` | The operating contract: invariants, architecture, conventions |
| `docs/spec/Project_Context.md` | The authoritative specification |
| `docs/IMPLEMENTATION_PLAN.md` | The step plan |
| `docs/runbook/testing-guide.md` | **How to test everything, step by step** |
| `docs/runbook/first-real-load.md` | **Loading the real feed for the first time — start here** |
| `docs/runbook/ingestion.md` | Running a load, reading the reconciliation report, rolling back |
| `docs/adr/` | Architecture decisions, with the measurements behind them |
| `docs/D11-final-report.md` | What this iteration delivers, and what is measured |
| `docs/D12-production-readiness.md` | The candid gap list — **read before relying on HORUS** |
| `scripts/` | Local environment, synthetic feed generator, **real-feed extract tool**, CLI wrapper, sample corpora |

## First-time setup for real data

The real feed is licensed data and personal data: it lives **outside** this repository and no agent
may read it (CLAUDE.md §3).

```powershell
[Environment]::SetEnvironmentVariable("HORUS_FEED_PATH","C:\horus-data\world-check.xml","User")
```

What only a human can do — loading the real feed, measuring real recall, building the benchmark
corpus with Compliance — is listed in testing-guide §16.

## Commands

See CLAUDE.md §8, or testing-guide Appendix A for the full CLI reference and its exit codes.
