---
description: Run one step of the implementation plan — plan first, then build
argument-hint: [step-number]
---
Read CLAUDE.md and docs/IMPLEMENTATION_PLAN.md. Execute **Step $ARGUMENTS** only.

1. Restate the step's goal and its "Done when" criteria in your own words.
2. List every file you will create or change, the tests you will write first,
   and which invariants (I-1 … I-13) apply to this step.
3. Stop and wait for my approval before writing any code.

After approval: write tests first, implement, then run `./gradlew build` and
report the real result — including failures. Finish with a checklist against
CLAUDE.md §14 (Definition of Done), and list exactly what I must verify myself
from the step's "You verify" section. Do not begin the next step.
