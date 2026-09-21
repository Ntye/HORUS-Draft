---
description: Audit the current uncommitted change against the invariants and security rules
---
Run `git diff` and review the change against CLAUDE.md §2 (invariants I-1 to
I-13), §3 (data handling), and §11 (security by design).

For each invariant the change touches, answer: does the change comply, and is
there a test that would **fail** if it were broken? Name the test, or say
there is none.

Then check specifically:
- any code path that removes or filters out a candidate (I-8)
- any method returning a bare `Set` of candidate ids (I-1)
- any `Instant.now()`, `UUID.randomUUID()` or unordered iteration in core (I-4)
- any Spring, `java.sql` or `javax.sql` import in domain, core or application
- any string-built SQL
- any real-looking personal name in tests, fixtures or comments
- any log statement that could print a name (I-11)

Report findings as a table: file:line, rule, finding, severity. Do not fix
anything — report only.
