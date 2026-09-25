# ADR-004 — Runtime and framework

- **Status:** Proposed
- **Date:** 2026-09-24
- **Review:** Solution Architect

## Context

Clean architecture in five rings with dependencies pointing inward (CLAUDE.md §4): frameworks live
only in `bootstrap` and `adapters`. One language across ingestion and screening so the single
normalisation pipeline cannot diverge (I-10). Ten working days. The team knows the JVM.

## Options considered

1. **Java 21 + Spring Boot 4 + Gradle multi-module + picocli + PostgreSQL 16 + Flyway (chosen).**
2. **Java + a lighter stack** (plain JDBC, no Spring; a hand-rolled composition root). Fewer moving
   parts, but the project would re-implement configuration binding, datasource wiring and test
   support that Spring provides.
3. **Python or Kotlin services.** Rejected on I-10: normalisation would be implemented by two
   languages, or ingestion and screening would need a cross-language contract.

"It is what the team knows" is stated openly as part of the reasoning for option 1; the
architecture-enforcing part (ArchUnit) is what keeps that familiarity from eroding the ring rules.

## Evidence

**What the real application boot exposed (Step 9), which no unit or hand-wired integration test
could see.** The application had never been started against a database. Booting it for the first time
found three defects, all now fixed and guarded by `ApplicationBootTest` (boots the whole Spring
context against a Testcontainers Postgres):

1. **It could not start.** Spring wraps every `@Repository` bean in an exception-translation proxy;
   CGLIB cannot subclass the `final` persistence classes (`Cannot subclass final class
   JdbcListVersionRepository`). Fix: `@Component`. `JdbcTemplate` already translates exceptions.
2. **It did not migrate its own schema.** Spring Boot 4 moved Flyway auto-configuration into a
   separate starter that is not on the classpath; the application started against an empty database
   (verified: `\dt` on the Compose database returned no relations). Fix: an explicit
   `SchemaMigrator` (adapters) invoked by `SchemaConfig` (bootstrap) on the admin credential, so a
   failed migration fails the boot (I-7). No dependency was added.
3. **`@Transactional` on the ingest writer was ineffective.** The auto-configured transaction
   manager is bound to the *primary* (admin) datasource, so it never wrapped statements executed on
   the `horus_ingest` connection. Fix: a `TransactionTemplate` bound to each repository's own
   datasource. Proven by `TransactionAtomicityIntegrationTest`, which was confirmed to fail when
   the transaction is removed.

**Enforcement:** six ArchUnit rules (CLAUDE.md §4) run in `:architecture-tests:test` and pass.
`horus.matching` imports only `horus.normalisation` and `horus.domain.shared` (I-9).

## Decision

Option 1. Segregation of duties is by credential: `horus_ingest`, `horus_screen`, `horus_audit`
(and `horus_admin`, used only to migrate), each handed only to the repositories that need it;
`ApplicationBootTest` asserts `current_user` for each datasource.

## Consequences

- Any new persistence class must be exercised through the real Spring context (the boot test) — a
  hand-wired test cannot catch proxying, migration or transaction-manager binding faults.
- The picocli commands run inside the Spring context, so each CLI invocation pays the ~4 s Spring
  start; acceptable for an operator harness, not for an API (out of scope this iteration).
- Boot 4's modular auto-configuration means "it worked in Boot 3" is not evidence; anything that
  relied on an auto-configuration must be re-verified at boot.
