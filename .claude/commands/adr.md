---
description: Draft an Architecture Decision Record with alternatives and evidence
argument-hint: [short-title]
---
Draft an ADR titled "$ARGUMENTS" in `docs/adr/`, numbered after the highest
existing ADR, using this structure:

- **Status:** Proposed
- **Context:** the problem, and which invariants or requirements constrain it
- **Options considered:** at least two, including the one not chosen
- **Evidence:** measurements, benchmark results, or profiling figures. If no
  measurement exists yet, say so plainly and state what would need measuring —
  do not substitute assertion for evidence (I-13)
- **Decision:** what, and why the evidence supports it
- **Consequences:** what becomes easier, harder, and what is now irreversible
- **Review:** Solution Architect (and Compliance, if policy is affected)

If the decision is "it is what I know", state that explicitly alongside the
alternatives — that is permitted, hiding it is not.
