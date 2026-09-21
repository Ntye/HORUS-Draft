# HORUS — Enterprise Name Screening Service

Start with **CLAUDE.md**. Then `docs/IMPLEMENTATION_PLAN.md`.

## First-time setup

1. Place the real World-Check feed **outside** this repository, e.g.
   `C:\horus-data\world-check.xml`, and set:
   ```powershell
   [Environment]::SetEnvironmentVariable("HORUS_FEED_PATH","C:\horus-data\world-check.xml","User")
   ```
2. Copy `docs/spec/Project_Context.md`, the diagrams into `docs/design/`, and the
   existing narrative code into `legacy/narrative/` (Step 4 migrates it).
3. `git init`, commit this scaffold, open Claude Code in this folder, run `/step 0`.

## Commands

See CLAUDE.md §8.
