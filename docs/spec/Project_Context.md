================================================================================
HORUS - ENTERPRISE NAME SCREENING SERVICE (ENSS)
PROJECT CONTEXT FILE
Afreximbank | IT - Digital Solutions Development | Software Factory
================================================================================

PURPOSE OF THIS FILE
--------------------
This is a working context document. It is written to be loaded as project
knowledge so that any assistant, engineer or reviewer joining the work has the
full picture: what is being built, why, under what constraints, what must never
be compromised, and what is deliberately excluded. It is a condensation of the
project specification, not a replacement for it. Where this file and the
authoritative specification disagree, the specification wins.

Read section 3 (INVARIANTS) before writing any code.

================================================================================
1. ONE-PARAGRAPH SUMMARY
================================================================================

Afreximbank licenses the LSEG World-Check risk intelligence dataset in bulk file
form AND separately pays LSEG per call through the World-Check One API, which is
what its operational platforms actually use for name screening today. HORUS
builds the screening capability in-house on top of the bulk data the Bank
already owns: ingest the feed into a governed, versioned internal store; apply a
configurable, deterministic, explainable normalisation and fuzzy-matching
engine; and expose screening as a single secured, versioned REST API published
on the Bank's NILE gateway for consumption by any internal system. The marginal
cost of an additional screening approaches zero, integration happens once
instead of per-platform, screening policy is defined in one place by Compliance,
and every request and outcome lands in one consolidated audit trail.

It is delivered as a 16-week graduate internship assignment inside the Software
Factory, with real supervision, real acceptance criteria and phase gates.

================================================================================
2. ORGANISATIONAL CONTEXT
================================================================================

2.1 Who builds it
Digital Solutions Development (DSD) is the department within Afreximbank IT
responsible for BUILDING the Bank's digital products, as distinct from running
infrastructure or administering packaged vendor systems. Where a capability
cannot be bought, or where buying it would fragment the Bank's data and customer
experience, DSD builds it.

DSD has four capability groups:
  - Software Factory        : solution architecture and software engineering
  - Quality Assurance       : test strategy, automation, release quality gates
  - DevOps / DevSecOps      : CI/CD, environments, containers, observability
  - Data Analytics / Science / Engineering : lakehouse, pipelines, BI, ML/AI

45+ engineers, architects, analysts. Technology portfolio ~US$26 million.

2.2 Who owns the control
This distinction determines every design decision in the project:

  - Compliance Department = POLICY OWNER and CONTROL OWNER.
  - DSD                   = BUILDER and OPERATOR of the technology.

The engineer does NOT decide what constitutes a match. Compliance does. The
engineer builds a system that faithfully and demonstrably implements what
Compliance decides, and that produces evidence Internal Audit can test.

2.3 Three lines of defence
  - First line  : the business (payment ops, loan admin, procurement, RM).
                  Owns the risk. Screens as part of doing business, reviews and
                  dispositions the alerts raised on what it processes.
  - Second line : Compliance and Risk. Sets policy - which lists, what
                  thresholds, what is a match, what escalates. Independently
                  challenges first-line decisions. Owns the screening standard.
  - Third line  : Internal Audit. Independently assures the control operates as
                  designed and documented. Will test this system.

2.4 Technology estate HORUS lives in
  Integration    : Apache Camel K (Groovy DSL), Jetic, Boomi; Fiorano ESB legacy
  Messaging      : Kafka, IBM MQ, ActiveMQ / Amazon MQ, NATS JetStream
  Cloud          : AWS primary (ECS, S3, Secrets Manager, ElastiCache, SES,
                   Bedrock, Amazon MQ); Azure and GCP where warranted
  Data / AI      : Databricks, Delta Lake, Unity Catalog, MLflow; Bedrock,
                   Vertex AI
  Databases      : PostgreSQL, Oracle
  Core systems   : Oracle Finacle (core banking), Finastra FTI/FCC (trade
                   finance), Calypso (treasury), SAP S/4HANA (ERP), Salesforce
  API management : NILE / ARC / ROSETTA platform family
  Identity       : Auth0 / Okta
  Standards      : SWIFT MT and MX, ISO 20022, CBPR+

2.5 Engineering standards that are non-negotiable
  - ADRs for every significant technical choice, reviewed by the supervising
    Solution Architect
  - API-first: the OpenAPI contract is agreed and reviewed BEFORE implementation
  - Trunk-based development, pull requests, mandatory peer review, no direct
    commits to main
  - Automated testing as a delivery gate, not an afterthought (unit,
    integration, contract in the pipeline)
  - Twelve-factor principles, externalised configuration, no secrets in source
    control
  - Security by design: SAST/DAST and dependency scanning in CI, least
    privilege, encryption in transit and at rest
  - All documentation in Markdown, stored alongside the code in the repository

================================================================================
3. INVARIANTS - THE RULES THAT ARE NEVER TRADED AWAY
================================================================================

These are the load-bearing constraints. If a proposed design or shortcut
violates one of these, the proposal is wrong, regardless of how much time it
saves.

I-1  FAIL CLOSED.
     Screening unavailability surfaces as an explicit, distinguishable error.
     It is NEVER returned as, or interpreted as, a "clear" / NO_MATCH result.
     Consumers must halt the business process. This is contract-tested with
     each consumer, not merely documented (risk R-11).

I-2  THE LOSS FUNCTION IS ASYMMETRIC.
     A false negative (missing a sanctioned party) is a regulatory breach with
     potentially catastrophic consequences. A false positive is an operational
     cost. Tune toward RECALL. Industry norms accept 90-99% of alerts being
     dispositioned as false positives. Recall is the binding constraint, not
     F-score.

I-3  EVERYTHING IS EXPLAINABLE AND REPRODUCIBLE.
     Phase 1 uses NO machine learning. Every score must be traceable to a stated
     rule with a stated weight, such that an analyst can read the explanation
     and reconstruct the arithmetic by hand. This is a regulatory expectation,
     not a stylistic preference.

I-4  DETERMINISM.
     Identical input + identical list version + identical configuration = an
     identical result. Always.

I-5  IMMUTABILITY OF EVIDENCE.
     Audit records are append-only. Never updated in place. The application
     database role holds INSERT and SELECT on audit_event only - no UPDATE, no
     DELETE. Disposition corrections are new events referencing the prior one,
     never overwrites.

I-6  NEVER HARD-DELETE WATCHLIST DATA.
     A de-listed entity is marked inactive with an effective date. Historical
     screening decisions must remain reconstructible.

I-7  CONFIGURATION OVER CODE.
     Thresholds, weights, list selection and match rules change through
     versioned configuration approved by Compliance, never through a code
     release. Every change is recorded with actor, timestamp, previous value and
     new value, and requires a benchmark regression run before promotion.

I-8  ATTRIBUTES ADJUST, THEY DO NOT ELIMINATE.
     A conflicting date of birth DEMOTES a candidate. It never deletes it. A
     whitelist entry FLAGS AND DEMOTES. It never silently suppresses. An entity
     type mismatch demotes, it does not exclude.

I-9  THE MATCHING ENGINE IS A PURE LIBRARY.
     No HTTP, no database, no framework coupling. Input: normalised query plus
     candidate. Output: score plus explanation. This is what makes it unit-
     testable at scale and independently benchmarkable.

I-10 NORMALISATION IS SYMMETRIC.
     The identical normalisation pipeline is applied to watchlist names at
     ingestion time and to query names at screening time. Any asymmetry here is
     a defect, and a silent one.

I-11 NO PERSONAL DATA IN URLS, QUERY STRINGS, LOGS OR ERROR TEXT.
     Names go in the request body. Always. This avoids personal data landing in
     access logs and proxy caches.

I-12 EVERY RESPONSE CARRIES ITS PROVENANCE.
     listVersion and configVersion on every screening response, without
     exception. This is the defence against the stale-list failure mode.

I-13 EVIDENCE BEFORE ASSERTION.
     Algorithm selection, index strategy, thresholds - all chosen by
     measurement against the benchmark corpus and recorded in an ADR. Selecting
     algorithms by assertion rather than by measurement is explicitly not
     acceptable. "It is what I know" is a legitimate consideration only when
     stated explicitly alongside the alternatives.

================================================================================
4. DOMAIN PRIMER - NAME SCREENING
================================================================================

4.1 The problem in engineering terms
Given a query name (and optionally a date of birth, country, entity type and
identifier), determine whether it plausibly refers to any entity in a reference
watchlist of hundreds of thousands to millions of records, and return a ranked,
scored, EXPLAINABLE set of candidate matches.

It is fuzzy record linkage under an asymmetric loss function.

4.2 Why it is genuinely hard
  a) The loss function is asymmetric (see I-2).
  b) Names are not identifiers. They are unstable strings. The same person may
     appear as Mohammed Al-Sayed, Muhammad El Sayyid, M. AlSayed, the Arabic
     original, or with given and family names transposed. Corporate names carry
     legal-form suffixes (Ltd, Limited, SARL, GmbH, PLC, Pty) abbreviated
     inconsistently.
  c) Adversaries adapt. Parties evading screening deliberately misspell,
     transliterate differently, use nicknames, route through nominees. The
     system must be robust to INTENTIONAL perturbation, not just honest typos.
  d) Every decision must be explainable and reproducible years later.
     Auditability is a first-class functional requirement, not a logging
     concern.

4.3 Related-but-distinct disciplines (do not conflate these)
  - Name screening        : this project.
  - Transaction screening : real-time screening of payment messages in flight
                            (SWIFT MT/MX), all parties and free-text fields,
                            before release. NOT this project.
  - Transaction monitoring: behavioural, retrospective pattern analysis
                            (structuring, pass-through, corridor anomalies).
                            SAS does this. NOT this project.
  - Customer risk rating  : scoring customers for financial crime risk. SAS.
  - Case management       : full investigator workbench. NOT this project.

4.4 Reference list categories
  - Official sanctions lists (UN Consolidated, OFAC SDN + Consolidated, EU
    Consolidated, UK OFSI, national). Legally binding, non-discretionary.
  - Ownership-derived (entities >=50% owned/controlled by designated parties -
    OFAC's "50 Percent Rule"). Requires ownership data. Phase 2 candidate.
  - PEPs (heads of state, ministers, senior officials, family, close
    associates). A risk indicator triggering EDD, not a prohibition.
  - Adverse / negative media. Risk indicator requiring judgement.
  - Law enforcement / regulatory: wanted lists, debarment lists (World Bank,
    AfDB), enforcement actions. Debarment is highly relevant to vendor and
    project finance screening.
  - Internal lists: Bank blacklist, exited relationships, internal watchlist,
    whitelist of cleared false positives.

World-Check aggregates and structures most of these into a single curated
dataset with a consistent record structure, which is exactly what makes it a
viable ingestion source.

================================================================================
5. CURRENT STATE AND THE PROBLEM
================================================================================

5.1 Compliance systems of record (NOT touched by this project)
  - Actimize WLX (NICE Actimize) : name and sanctions screening for the
    Compliance Department. Established, validated, audited.
  - SAS (SAS Institute)          : transaction monitoring and customer risk
    ranking.

HORUS does not replace either. Its scope is the OPERATIONAL, PRE-COMPLIANCE
screening need that arises inside business workflow platforms - the need met
today by direct LSEG API calls.

5.2 Platforms needing screening
  - OpsWorkflow : back-office ops workflow incl. outward payment processing.
                  Screening of payment beneficiaries when a task routes to the
                  compliance queue; result must display in the task interface
                  and persist in task history.
  - Appian Loan Administration : obligors, guarantors, directors, UBOs and
                  disbursement beneficiaries at origination, drawdown and
                  periodic review.
  - VMS (Vendor Management System) : vendors, directors and UBOs at onboarding,
                  contract award and periodic recertification. Debarment list
                  exposure matters here.

Candidate future consumers: Entity Data Repository (EDR party MDM), BOOP,
Software Factory KYC recertification, customer onboarding journeys.

5.3 The four structural problems with per-call vendor integration
  COST          : priced per screening/volume. Cost scales linearly with
                  adoption. This creates a perverse incentive - teams screen
                  less than good practice suggests to contain spend. A control
                  whose usage is rationed by unit cost is a WEAKENED CONTROL.
  COMPLEXITY    : each consumer independently implements HMAC request signing,
                  the vendor case/screening object model, pagination, error and
                  retry semantics, result interpretation. Duplicated effort,
                  duplicated defect surface, duplicated maintenance.
  INCONSISTENCY : no single place where policy is defined, no single place
                  where results are recorded, no way to answer "show me
                  everything the Bank screened last quarter and what happened".
  CONCENTRATION : availability of a compliance control in multiple front-line
                  systems depends on one external endpoint.

5.4 The asset already owned
The World-Check subscription includes the bulk risk intelligence data feed - the
underlying dataset as files on a refresh cycle, containing entity records,
aliases, identifiers, categories and source references.

The Bank is paying twice: once for the data, and again per call for the
privilege of asking questions of it.

================================================================================
6. OBJECTIVES AND SUCCESS CRITERIA
================================================================================

6.1 Business objectives
  BO-1 Reduce marginal cost of screening to effectively zero per call
  BO-2 One reusable screening capability consumable by any internal system
  BO-3 One consolidated auditable record of screening activity Bank-wide
  BO-4 Reduce effort for a new platform to acquire screening capability
  BO-5 Extend screening earlier into business processes without cost pressure
  BO-6 Build internal engineering capability in financial crime screening

6.2 Delivery objectives
  PO-1 Working, tested ingestion pipeline: full + incremental, with versioning
  PO-2 Configurable matching engine with documented explainable algorithms
  PO-3 Secured versioned REST API (single + batch), published on the gateway
  PO-4 Complete audit and evidence store
  PO-5 One consuming platform integrated end-to-end as reference
  PO-6 Benchmark report vs incumbent LSEG API on a common test population
  PO-7 Complete technical, operational and handover documentation

6.3 Success criteria
  SC-1 Full watchlist ingested and queryable - 100% of source records loaded
       and reconciled by count and checksum
  SC-2 Incremental refresh operational - daily delta applied automatically
       within the agreed window, with reconciliation report
  SC-3 Latency - p95 <= 500 ms, p99 <= 1 s for single-name synchronous
  SC-4 Recall - >= 99% agreement with incumbent LSEG on true positives across
       the agreed benchmark set; EVERY disagreement individually analysed and
       documented
  SC-5 Precision measured, documented and demonstrably tunable by configuration
  SC-6 Auditability proven - any historical decision fully reconstructible
       including the list version in force at the time
  SC-7 One consuming platform screening through HORUS in non-production, signed
       off by its product owner
  SC-8 IT Security review passed
  SC-9 Documentation complete - OpenAPI spec, ADRs, runbook, data dictionary,
       test report, benchmark report

================================================================================
7. SCOPE
================================================================================

7.1 IN SCOPE
  - Ingestion, parsing, validation, normalisation, versioning, indexing of the
    World-Check bulk feed
  - Internal blacklist and whitelist support alongside vendor data
  - Name normalisation, transliteration handling, fuzzy matching for both
    individuals and organisations
  - Configurable scoring, thresholds and match rules under Compliance control
  - Synchronous single-name screening API
  - Asynchronous batch screening API for portfolio / periodic re-screening
  - Alert generation, retrieval and disposition capture
  - Comprehensive audit trail and evidence retention
  - Reference integration with ONE consuming platform (recommended: VMS or
    Appian Loan Administration)
  - Non-functional hardening: security, performance, observability, runbook
  - Full documentation set and knowledge transfer

7.2 OUT OF SCOPE (this phase)
  - Production go-live; decommissioning any existing LSEG integration
  - Replacement of Actimize WLX or SAS
  - Real-time SWIFT message interdiction
  - Adverse media free-text ingestion beyond the structured feed
  - ML-based match scoring (Phase 2 candidate; Phase 1 must be deterministic)
  - Full case management front end (a minimal disposition UI is in scope; an
    investigator workbench is not)
  - Ownership/control graph resolution for the 50 Percent Rule (Phase 2)
  - OCR or document-based screening

7.3 EXPLICIT NON-CLAIMS
HORUS does not make compliance decisions. It produces scored, explained
candidates for human disposition. It does not re-curate or enrich World-Check
content; it consumes the vendor's curation as delivered.

7.4 Assumptions
  A-1 The World-Check subscription includes bulk feed rights AND permits
      in-house screening use. TO BE FORMALLY CONFIRMED before design freeze.
      See risk R-1 - this is the project's gating dependency.
  A-2 Compliance nominates a named business owner available weekly
  A-3 Non-production feed access or representative sample from week 2
  A-4 A masked/non-production test population of names is available
  A-5 Test access to the incumbent LSEG API for benchmarking
  A-6 Standard DSD cloud, CI/CD and gateway tooling available from week 1

================================================================================
8. PERSONAS
================================================================================

Amina - Payment Services Officer (first line, OpsWorkflow).
  Needs an immediate unambiguous answer on the beneficiary: clear, hit, or needs
  review. Cares about latency (she has a queue), clarity (she is not a sanctions
  analyst), and the result being recorded so she is protected if the payment is
  later questioned.

Kwame - Compliance Analyst (second line).
  Dispositions alerts. Needs to see WHY the engine matched: which name, which
  alias, what score, which contributing features, which list, which list
  version. Needs to record his decision and rationale. Needs confidence the
  engine is not silently missing things.

Fatima - Procurement Officer (first line, VMS).
  Screens a company plus its directors and beneficial owners - several names at
  once. Wants to submit a set and receive a consolidated result.

Daniel - Platform Engineer (consumer team, Appian).
  Wants a clean OpenAPI spec, a sandbox with representative test data,
  predictable error semantics, idempotency, and a client library or reference
  snippet. Does not want to learn financial crime domain modelling to make a
  call.

================================================================================
9. FUNCTIONAL REQUIREMENTS (condensed, MoSCoW)
================================================================================

9.1 Watchlist ingestion
  FR-001 M Ingest the bulk feed as delivered: entities, aliases, identifiers,
           categories, source references
  FR-002 M Full (baseline) load and incremental (delta) updates: additions,
           amendments, deletions/de-listings
  FR-003 M Validate every run: record counts, mandatory fields, schema
           conformance, character encoding, checksum where provided
  FR-004 M Reject and quarantine a failing run without corrupting live data;
           alert operations
  FR-005 M Version every load: unique immutable id, timestamp, source file
           identity, record counts
  FR-006 M Retain historical versions sufficient to reconstruct the exact
           dataset in force at any past screening decision
  FR-007 M Reconciliation report per run: added, amended, deleted, rejected,
           with reasons
  FR-008 M Schedulable AND on-demand triggerable by an authorised operator
  FR-009 S Ingest the Bank's internal blacklist as an additional list source
  FR-010 S Whitelist maintained by Compliance, applied at scoring or
           presentation time - NEVER by silently discarding a match
  FR-011 C Ingest additional public sanctions sources (UN, OFAC, EU, OFSI)
  FR-012 C Ingest multilateral development bank debarment lists
  FR-013 M De-listing is explicit and auditable, never a silent physical delete

9.2 Matching and screening
  FR-020 M Accept a query comprising at minimum a name string
  FR-021 M Accept optional: entity type, DOB/YOB, country/nationality,
           identifier, gender, address
  FR-022 M DOB optional; its absence must not prevent screening
  FR-023 M Normalise query and watchlist names with a documented deterministic
           pipeline
  FR-024 M Match primary names AND all aliases / AKAs / low-quality AKAs
  FR-025 M Exact, near-exact and fuzzy matching: transposition, insertion,
           deletion, substitution, abbreviation, initials, word-order variation
  FR-026 M Phonetic matching appropriate to transliterated names
  FR-027 M Entity-type-appropriate logic; persons and organisations are NOT
           treated identically
  FR-028 M Numeric match score on a documented stable scale for every candidate
  FR-029 M Decision category from configurable thresholds
           (NO_MATCH / POSSIBLE_MATCH / STRONG_MATCH)
  FR-030 M Structured explanation per candidate: which name/alias matched, which
           algorithms contributed, feature-level sub-scores, which secondary
           attributes corroborated or conflicted
  FR-031 M Thresholds and weights configurable without code change or redeploy;
           every change versioned and auditable
  FR-032 S Screening PROFILES - named config sets per use case
           (payment-beneficiary, vendor-onboarding, loan-obligor) with their own
           lists, thresholds and weights
  FR-033 S Secondary-attribute logic demotes but never silently suppresses
  FR-034 S Non-Latin script transliterated to a canonical form; the
           transliteration standard applied is documented
  FR-035 M Cap and paginate candidate sets, ordered by descending score
  FR-036 M Deterministic (see I-4)

9.3 APIs
  FR-040 M Synchronous single-name endpoint
  FR-041 S Synchronous multi-name endpoint for small sets
  FR-042 M Asynchronous batch endpoint with job status polling and result
           retrieval
  FR-043 M OAuth 2.0 client credentials with scope-based authorisation
  FR-044 M Idempotency key so a retry does not create duplicate alerts
  FR-045 M Unique persistent screeningId on every response, usable to retrieve
           the full evidence record later
  FR-046 M Every response states listVersion and configVersion
  FR-047 M Path versioning (/v1/...) with a documented deprecation policy
  FR-048 M Published on the API management platform with complete OpenAPI 3.x
           and a developer sandbox
  FR-049 M Structured documented errors (RFC 7807 problem+json) with actionable
           error codes
  FR-050 S Per-consumer rate limits and quotas
  FR-051 M Endpoint to retrieve full watchlist entity detail by identifier
  FR-052 M Health, readiness and list-freshness endpoints

9.4 Alerts, disposition, audit
  FR-060 M Persistent alert record when candidates reach the alert threshold
  FR-061 M Alerts carry consuming system, business reference, requesting user or
           service identity, timestamp
  FR-062 M Authorised disposition recording: true match, false positive,
           escalated, pending - with MANDATORY rationale text
  FR-063 M Disposition history append-only; corrections are new events
  FR-064 M Immutable audit event for every screening request, response, config
           change, list load and disposition
  FR-065 M Reproduce any historical decision exactly, including list version and
           configuration in force
  FR-066 S Search and export of history by business reference, name, date range,
           consuming system, disposition
  FR-067 S Minimal web UI for Compliance: review alerts, record dispositions,
           manage whitelists, view configuration history
  FR-068 S Operational and MI reporting: volumes by consumer, alert rates,
           disposition outcomes, false positive rates, list freshness

9.5 Re-screening
  FR-070 S Periodic re-screening of a supplied population against the current
           list version
  FR-071 C Delta re-screening: on each list update, re-evaluate a registered
           population against newly added/amended entries only
  FR-072 C Report outcomes as new-hit / changed-hit / unchanged so consumers act
           only on genuine change

================================================================================
10. NON-FUNCTIONAL REQUIREMENTS
================================================================================

10.1 Performance and scale
  NFR-001 Single-name sync: p95 <= 500 ms, p99 <= 1000 ms, measured at the
          service boundary excluding gateway overhead
  NFR-002 Sustained >= 50 requests/second with horizontal scaling headroom
  NFR-003 Batch of 100 000 names within 60 minutes
  NFR-004 Baseline full ingestion within a 4-hour maintenance window
  NFR-005 Delta ingestion within 30 minutes, applied without service
          interruption
  NFR-006 Accommodate growth to >= 5 million entity records and 25 million name
          variants without architectural change

10.2 Availability and resilience
  NFR-010 99.9% availability during business hours across operating time zones
  NFR-011 No single point of failure in the serving path; multiple stateless
          replicas behind a load balancer
  NFR-012 List updates applied ATOMICALLY - consumers never observe a partially
          loaded list
  NFR-013 On failed list update the previous version stays in service; alert ops
  NFR-014 RTO 4 hours, RPO 24 hours (the source file is re-obtainable; audit
          data is NOT)
  NFR-015 Audit store backed up with point-in-time recovery
  NFR-016 Consumers receive an explicit distinguishable error when screening is
          unavailable - never a false clear. Fail closed, never fail open.

10.3 Security
  NFR-020 TLS 1.2+ on all external communication
  NFR-021 Data at rest encrypted with platform-managed keys
  NFR-022 OAuth 2.0 client credentials from the Bank IdP; no static API keys in
          application code
  NFR-023 Authorisation by scope AND by screening profile; a consumer may only
          invoke profiles it is entitled to
  NFR-024 All secrets in the platform secrets manager; none in source control,
          config files or images
  NFR-025 Segregation of duties: whoever can change thresholds cannot alter or
          delete audit records
  NFR-026 Full audit logging of administrative actions with actor identity
  NFR-027 SAST, DAST, dependency and container image scanning in CI with defined
          failure thresholds
  NFR-028 Watchlist and screening data classified CONFIDENTIAL; least privilege

10.4 Maintainability and compliance
  NFR-030 Linting, formatting, static analysis, >= 80% unit test coverage on
          matching and normalisation logic
  NFR-031 Matching engine a separately testable module with no dependency on
          transport or persistence
  NFR-032 Deployment fully automated and reproducible via CI/CD; environments as
          code
  NFR-033 Structured JSON logging with correlation IDs propagated from consumers
  NFR-034 Prometheus-compatible metrics; distributed tracing instrumented
  NFR-035 Audit and evidence retention per records retention policy (assume 7
          years minimum unless Compliance advises otherwise)
  NFR-036 Personal data per the Bank data protection standard; watchlist data
          not replicated into unmanaged environments; masked or synthetic data
          in non-production wherever feasible
  NFR-037 Documentation in Markdown alongside the code

================================================================================
11. BUSINESS RULES / SCREENING POLICY PARAMETERS
================================================================================

Owned by Compliance. Implemented as CONFIGURATION. Never hard-coded.
Values below are ILLUSTRATIVE PLACEHOLDERS pending the Week 3 workshop.

  BR-01 alertThreshold              minimum score raising an alert       75
  BR-02 strongMatchThreshold        score presented as strong            92
  BR-03 maxCandidates               max candidates per query             50
  BR-04 listSelection               which sources apply per profile      profile-specific
  BR-05 algorithmWeights            relative weight of each measure      see §13.2
  BR-06 dobToleranceYears           tolerance before DOB is conflicting  +/-1
  BR-07 dobConflictPenalty          penalty on conflicting DOB           -15
  BR-08 countryMatchBonus           bonus when country corroborates      +5
  BR-09 identifierMatchBehaviour    on exact passport/registration match force strong match
  BR-10 whitelistBehaviour          handling of cleared entities         flag and demote, never suppress
  BR-11 entityTypeMismatchBehaviour query type vs record type differ     demote, do not exclude
  BR-12 minimumNameLength           below this, tighter matching applies 3 characters
  BR-13 stopwordHandling            legal-form suffixes and connectors as low-weight tokens  enabled
  BR-14 rescreeningFrequency        periodic population re-screening     monthly

GOVERNANCE RULE: every change to any parameter above must be
  (a) approved by the Compliance control owner,
  (b) recorded with actor, timestamp, previous and new value, and
  (c) accompanied by a regression run against the benchmark test set before
      promotion to production.

================================================================================
12. TARGET ARCHITECTURE
================================================================================

12.1 Logical components
   1 Feed Acquisition     retrieve from SFTP / object storage drop, verify
                          integrity, archive raw file immutably
   2 Ingestion Pipeline   parse, validate, normalise, transform to canonical
                          model; compute match keys and phonetic codes
   3 Watchlist Store      authoritative relational store: entities, names,
                          identifiers, categories, list versions
   4 Search Index         fast candidate generation (inverted / n-gram index)
   5 Matching Engine      candidate generation, feature computation, composite
                          scoring, thresholding, explanation generation
   6 Configuration Svc    versioned profiles, thresholds, weights
   7 Screening API        REST; request validation, orchestration, shaping
   8 Batch Orchestrator   job submission, chunking, parallel execution, progress
                          tracking, result assembly
   9 Alert Store          alerts and disposition history
  10 Audit Store          append-only evidence of all events
  11 Admin & Review UI    minimal Compliance-facing interface
  12 Observability        logs, metrics, traces, alerting, list freshness

12.2 Architectural principles
  - Matching engine is a pure library (I-9)
  - TWO-STAGE RETRIEVAL: blocking/candidate generation (recall-oriented, cheap,
    index-driven) is separated from scoring (precision-oriented, expensive,
    applied to a small candidate set). NEVER score the whole list.
  - Configuration over code (I-7)
  - Immutability of evidence (I-5)
  - Fail closed (I-1)
  - API first
  - Stateless services; all state in the datastore; scale horizontally

12.3 Deployment view (target)

        +----------------------------------------------+
        |        NILE API Gateway / API Management     |
        |  OAuth2 - rate limiting - routing - logging  |
        +-------------------+--------------------------+
                            |
        +-------------------v--------------------------+
        |      HORUS Screening Service (N replicas)    |
        |      container workload on AWS ECS           |
        +---+---------------+-------------+------------+
            |               |             |
     +------v-----+  +------v-----+ +-----v--------+
     | PostgreSQL |  |  Search    | | ElastiCache  |
     | watchlist  |  |  index     | | hot-query    |
     | + alerts   |  |            | | cache        |
     | + audit    |  |            | |              |
     +------------+  +------------+ +--------------+
            ^
            |  scheduled load
     +------+-----------------------+
     |  Ingestion job (Camel K /    | <-- S3 raw file archive
     |  scheduled container task)   | <-- SFTP / vendor drop
     +------------------------------+

================================================================================
13. INGESTION PIPELINE
================================================================================

13.1 Stages and failure handling
   1 ACQUIRE    retrieve file; verify size, checksum, filename pattern
               -> on failure: alert and abort; previous version stays in service
   2 ARCHIVE    write raw file unmodified to immutable object storage with
               retention lock
               -> on failure: abort. Provenance is non-negotiable.
   3 PARSE      stream-parse the source format; do NOT load the whole file
               -> record-level errors quarantined with line reference
   4 VALIDATE   schema, mandatory fields, encoding, referential integrity
               between entities and aliases, record count vs manifest
               -> threshold-based: more than N% invalid aborts the run
   5 TRANSFORM  map to the canonical model
               -> unmapped values logged for review, NEVER silently dropped
   6 NORMALISE  apply the normalisation pipeline to every name and alias;
               compute phonetic codes and n-gram keys
   7 STAGE      write into a staging schema under a new list_version_id
   8 RECONCILE  compare staged to live: additions, amendments, deletions;
               produce reconciliation report
               -> anomalous deltas (e.g. >20% change) require operator
                  confirmation
   9 INDEX      build the search index for the staged version
  10 PROMOTE    atomically switch the active list version pointer
               -> rollback by repointing to the previous version
  11 NOTIFY     publish list.updated; update freshness metric; trigger delta
               re-screening if configured

13.2 Design notes
  - STREAM, DO NOT SLURP. The feed is large. Bounded memory. This is a
    deliberate teaching point.
  - IDEMPOTENCY. Re-running the same file must not produce duplicate records or
    corrupt state.
  - BITEMPORALITY. Record BOTH when the vendor asserted the fact AND when the
    Bank loaded it. Audit questions are asked in the vendor's time frame;
    operational questions in the Bank's.
  - NEVER HARD-DELETE (I-6).
  - CHARACTER ENCODING IS A REAL HAZARD. Assume UTF-8, VERIFY it, and test
    explicitly with Arabic, Cyrillic, Chinese and accented Latin names. Silent
    mojibake corruption of a name is a silent SCREENING FAILURE.

13.3 Schema discovery (Week 2, first data task)
The exact structure of the World-Check delivery must be established EMPIRICALLY
from the licensed feed documentation and a sample file. DO NOT ASSUME THE
STRUCTURE FROM THE SPECIFICATION - verify it against the file.

The data dictionary must cover at minimum:
  - Entity records: unique id, entity type, primary name, category/sub-category,
    list source, status, effective dates
  - Name variants: aliases, AKAs, low-quality AKAs, native-script names, name
    components where structured
  - Personal attributes: dates of birth (often multiple, often partial), places
    of birth, nationalities, gender
  - Identifiers: passport, national ID, tax and company registration numbers,
    and issuing countries
  - Addresses and jurisdictions
  - Provenance: source list references, designation dates, narrative/remarks
  - Relationships to other entities where present

================================================================================
14. CANONICAL DATA MODEL
================================================================================

Deliberately decoupled from the vendor format so additional list sources can be
onboarded without re-architecting.

14.1 Core entities
list_version      list_version_id, source_system, source_file_name,
                  source_file_checksum, vendor_published_at, loaded_at,
                  record_count, status (STAGED/ACTIVE/SUPERSEDED/FAILED),
                  loaded_by

watch_entity      entity_id (surrogate), source_entity_id (vendor key),
                  list_version_id, entity_type
                  (INDIVIDUAL/ORGANISATION/VESSEL/AIRCRAFT), primary_name,
                  categories[], list_sources[], status (ACTIVE/DELISTED),
                  designated_at, delisted_at, narrative, valid_from, valid_to

watch_name        name_id, entity_id, name_type
                  (PRIMARY/AKA/LOW_QUALITY_AKA/FORMER/NATIVE_SCRIPT), raw_name,
                  normalised_name, name_tokens[], phonetic_codes[], script,
                  language

watch_identifier  identifier_id, entity_id, id_type
                  (PASSPORT/NATIONAL_ID/REGISTRATION/TAX/LEI/OTHER), id_value,
                  normalised_id_value, issuing_country

watch_dob         dob_id, entity_id, year, month, day, precision
                  (EXACT/YEAR_ONLY/RANGE), range_from, range_to
                  -- supports PARTIAL and MULTIPLE dates

watch_address     address_id, entity_id, country_code, city, raw_address

watch_attribute   entity_id, attribute_key, attribute_value
                  -- extensible key/value for source fields not modelled

14.2 Operational entities
screening_request     screening_id, consumer_system, consumer_reference,
                      profile_id, requested_by, requested_at, query_payload (as
                      received), list_version_id, config_version_id,
                      candidate_count, top_score, outcome, duration_ms,
                      idempotency_key

screening_candidate   candidate_id, screening_id, entity_id, matched_name_id,
                      composite_score, feature_scores (JSON), decision_band,
                      rank

alert                 alert_id, screening_id, status (OPEN/IN_REVIEW/CLOSED),
                      current_disposition, assigned_to, created_at, closed_at

alert_disposition_event (append-only)
                      event_id, alert_id, disposition, rationale, actor,
                      occurred_at

whitelist_entry       whitelist_id, scope
                      (GLOBAL/PROFILE/CONSUMER_REFERENCE),
                      query_name_normalised, entity_id, justification,
                      approved_by, approved_at, expires_at

config_version        config_version_id, profile_id, payload (JSON), created_by,
                      created_at, approved_by, effective_from

audit_event (append-only; no UPDATE or DELETE granted to the application role)
                      audit_id, event_type, actor, occurred_at, subject_type,
                      subject_id, payload (JSON), correlation_id

================================================================================
15. NORMALISATION AND MATCHING - THE INTELLECTUAL CORE
================================================================================

15.1 Two-stage design
STAGE 1 - CANDIDATE GENERATION (BLOCKING)
  Reduce millions of records to tens of plausible candidates, cheaply, with high
  recall. RECALL LOST HERE CAN NEVER BE RECOVERED DOWNSTREAM, so blocking must
  be generous.
  Combine (UNION of results, not intersection):
    - inverted index lookup on normalised name tokens
    - character n-gram (trigram) index for typo tolerance - PostgreSQL pg_trgm
      with a GIN index is a strong fit
    - phonetic key lookup (Double Metaphone / Soundex bucket)
    - exact normalised-string hash lookup
    - identifier exact match (passport, registration) as a direct
      high-confidence path

STAGE 2 - SCORING
  Apply expensive similarity computation ONLY to the candidate set above.

15.2 Normalisation pipeline (applied identically at ingestion and at query time)
   1 Unicode normalisation (NFKD) and case folding    JOSE -> jose
   2 Diacritic removal                                jose -> jose
   3 Transliteration of non-Latin to Latin            (Arabic) -> mohammed
   4 Punctuation and separator handling               O'Brien-Smith -> o brien smith
   5 Whitespace collapsing and trimming
   6 Titles and honorifics removal                    Dr. Ahmed Bey -> ahmed
   7 Corporate legal-form normalisation               Limited/Ltd./LTD -> ltd (low weight)
   8 Connector-word handling                          and / & / de / al / bin consistently
   9 Tokenisation                                     mohammed al sayed -> [mohammed, al, sayed]
  10 Token sorting into a canonical form              RETAINED ALONGSIDE, NOT
                                                      INSTEAD OF, the ordered form
  11 Phonetic encoding per token                      Double Metaphone
  12 N-gram generation                                trigrams for indexing

WARNINGS
  - OVER-NORMALISATION DESTROYS SIGNAL. Al-Sayed and Alsayed should collapse.
    Ali and Alia should NOT.
  - Arabic particles (al, el, bin, ibn, abu) and Latin particles (de, van, von,
    da) require deliberate rules, not incidental treatment.
  - CJK and other logographic names need a separate strategy. DOCUMENT THE
    LIMITATION rather than pretending to handle it.
  - Some list entries are placeholders or partial names. These must not be
    allowed to match everything.

15.3 Similarity measures and their use
  Exact match             binary on normalised string   fast path
  Levenshtein             character edits               token-level, typos
  Damerau-Levenshtein     edits + transposition         keyboard transpositions
  Jaro-Winkler            prefix-weighted proximity     token-level, widely used
  Jaccard on token sets   set overlap                   word-order, missing
                                                        middle names, full-name
  Token-set / token-sort  order-invariant composite     Smith John vs John Smith
  Double Metaphone        phonetic encoding             Mohammed / Muhammad
  Soundex / NYSIIS        simpler phonetic              blocking, baseline
  N-gram cosine / Dice    sub-string overlap            compound/concatenated
  Initial matching        structural                    J. Smith vs John Smith
  Acronym handling        structural                    IBM vs International
                                                        Business Machines

REQUIREMENT: implement a set of measures, evaluate them EMPIRICALLY against the
benchmark corpus, and justify the final composition in ADR-002.

15.4 Person vs organisation logic (a common and serious defect is conflating
     these)
                   INDIVIDUAL                    ORGANISATION
  Token semantics  given/middle/family, order    distinctive core + generic
                   varies                        descriptors + legal form
  Weighting        all tokens carry signal;      generic tokens (trading, group,
                   family name often higher      holdings, company) heavily
                                                 down-weighted; distinctive
                                                 tokens carry the signal
  Missing tokens   common and tolerable          SUSPICIOUS - a missing
                                                 distinctive token is meaningful
  Phonetics        highly valuable               less valuable
  Secondary attrs  DOB, nationality, gender,     registration number, country of
                   passport                      incorporation, LEI
  Abbreviations    initials                      acronyms and trade names

Applying person logic to organisations either floods analysts with matches on
"Africa Trading Company" or misses genuine matches on distinctive tokens.

================================================================================
16. SCORING, THRESHOLDS, DECISIONING
================================================================================

16.1 Composite model (Phase 1 - deterministic and explainable)

  name_score      = SUM(w_i * similarity_i(query_name, candidate_name)) / SUM(w_i)
  attribute_delta = dob_effect + country_effect + identifier_effect
                    + entity_type_effect + list_source_effect
  composite_score = clamp(0, 100, name_score * 100 + attribute_delta)

Design constraints:
  - No ML in Phase 1 (I-3).
  - Attribute effects ADJUST the score; they never eliminate a candidate that
    name similarity has surfaced (I-8).
  - An exact identifier match (passport, company registration) SHORT-CIRCUITS to
    a strong match regardless of name similarity.

16.2 Illustrative weights (to be tuned empirically, then approved by Compliance)
  Feature                              Individual   Organisation
  Token-sort similarity (Jaro-Winkler)    0.30          0.25
  Token-set overlap (Jaccard)             0.20          0.30
  Phonetic agreement                      0.25          0.10
  Edit distance on full normalised str    0.15          0.15
  N-gram similarity                       0.10          0.20

  Attribute effect                      Adjustment
  DOB exact match                          +10
  Year of birth match only                  +5
  DOB conflict beyond tolerance            -15
  DOB absent                                 0   (NEUTRAL - never penalise
                                                 absence)
  Country / nationality corroboration       +5
  Country conflict                          -5
  Identifier exact match                   force >= strongMatchThreshold
  Entity type mismatch                     -10

16.3 Decision bands
  NO_MATCH        no candidate >= alertThreshold
                  -> proceed; result recorded
  POSSIBLE_MATCH  top score >= alertThreshold and < strongMatchThreshold
                  -> route to compliance review; do not proceed unattended
  STRONG_MATCH    top score >= strongMatchThreshold
                  -> halt; mandatory compliance review and escalation
  ERROR           screening could not be completed
                  -> HALT. Explicit error; never interpret as clear.

16.4 Threshold calibration method
  1. Assemble a labelled benchmark corpus (§19.3): known true matches, known
     near-miss non-matches, randomly sampled clear names.
  2. Score the entire corpus across a sweep of candidate thresholds.
  3. Plot precision, recall and F-measure against threshold; construct the
     ROC / precision-recall curve.
  4. Select the operating point achieving the RECALL FLOOR mandated by
     Compliance. Recall is the binding constraint, not F-score.
  5. Quantify the resulting alert volume and its operational cost, and present
     BOTH to Compliance.
  6. Record the chosen threshold, the evidence and the approval in an ADR and in
     config_version.

TEACHING POINT: threshold selection is a BUSINESS RISK DECISION informed by
engineering evidence. The engineer produces the evidence and the recommendation;
Compliance owns the decision.

================================================================================
17. API DESIGN
================================================================================

The authoritative contract is the OpenAPI 3.x document in the repository.

17.1 Endpoints
  POST /v1/screenings                        screen one or more names sync
  GET  /v1/screenings/{screeningId}          full evidence record of a past
                                             screening
  POST /v1/batch-screenings                  submit an async batch job
  GET  /v1/batch-screenings/{jobId}          poll job status
  GET  /v1/batch-screenings/{jobId}/results  retrieve results (paginated or file
                                             reference)
  GET  /v1/entities/{entityId}               full watchlist entity detail
  GET  /v1/alerts                            search alerts (filtered)
  GET  /v1/alerts/{alertId}                  retrieve an alert
  POST /v1/alerts/{alertId}/dispositions     record a disposition
  GET  /v1/profiles                          profiles available to the caller
  GET  /v1/list-versions/current             active list version and freshness
  GET  /v1/health , /v1/ready                operational probes

17.2 Request shape
  POST /v1/screenings
  Authorization: Bearer <oauth2-token>
  Idempotency-Key: 4d2a2b7c-9f1e-4a58-b0b1-6c9f2b7a1c33
  X-Correlation-Id: opsworkflow-TASK-884213
  {
    "profile": "payment-beneficiary",
    "consumerReference": "PAY-2026-0009812",
    "subjects": [
      {
        "subjectRef": "beneficiary-1",
        "name": "Mohammed Al Sayed Trading Company Ltd",
        "entityType": "ORGANISATION",
        "country": "AE",
        "identifiers": [
          { "type": "REGISTRATION", "value": "1023456", "issuingCountry": "AE" }
        ]
      },
      {
        "subjectRef": "director-1",
        "name": "Ahmed Ben Youssef",
        "entityType": "INDIVIDUAL",
        "dateOfBirth": "1971-04-12",
        "nationality": "TN"
      }
    ]
  }

17.3 Response shape (abridged)
  {
    "screeningId": "scr_01JB9X4K2T7M0QH3",
    "screenedAt": "2026-08-24T09:14:02.113Z",
    "listVersion": "wc-2026-08-24-01",
    "configVersion": "cfg-payment-beneficiary-v7",
    "profile": "payment-beneficiary",
    "consumerReference": "PAY-2026-0009812",
    "results": [
      { "subjectRef": "beneficiary-1", "decision": "NO_MATCH",
        "topScore": 41, "candidates": [] },
      { "subjectRef": "director-1", "decision": "POSSIBLE_MATCH",
        "topScore": 84, "alertId": "alrt_01JB9X4K7QW2",
        "candidates": [
          { "rank": 1, "entityId": "wce_889213",
            "matchedName": "Ahmad Bin Yousef", "matchedNameType": "AKA",
            "primaryName": "Ahmad Yousef", "entityType": "INDIVIDUAL",
            "categories": ["SANCTIONS"],
            "listSources": ["UN_CONSOLIDATED", "EU_CONSOLIDATED"],
            "score": 84, "decisionBand": "POSSIBLE_MATCH",
            "explanation": {
              "featureScores": {
                "tokenSortJaroWinkler": 0.88, "tokenSetJaccard": 0.67,
                "phoneticAgreement": 1.00, "editDistanceNormalised": 0.79,
                "ngramSimilarity": 0.74 },
              "attributeEffects": [
                { "attribute": "dateOfBirth", "effect": 0,
                  "reason": "NOT_PRESENT_ON_RECORD" },
                { "attribute": "nationality", "effect": 5,
                  "reason": "MATCH_TN" } ],
              "narrative": "Matched on alias 'Ahmad Bin Yousef'; full phonetic
                            agreement across all tokens; nationality
                            corroborates." } } ] }
    ]
  }

17.4 Error semantics
  400  malformed request or invalid profile   fix and resubmit; do not retry blindly
  401/403 auth failure / profile not permitted do not retry; escalate to platform team
  409  idempotency key reused with different payload  investigate; do not duplicate
  422  semantically invalid subject (empty name)      correct the source data
  429  rate limit exceeded                    exponential backoff with jitter
  503  screening unavailable (list not loaded, dependency down)
       -> HALT the business process. Surface an explicit error with retry and
          escalate options. NEVER proceed as if clear.

  Error body follows RFC 7807:
  {
    "type": "https://api.afreximbank.internal/errors/screening-unavailable",
    "title": "Screening service temporarily unavailable",
    "status": 503,
    "detail": "No active watchlist version is currently loaded.",
    "instance": "/v1/screenings",
    "correlationId": "opsworkflow-TASK-884213",
    "retryAfterSeconds": 30
  }

17.5 API design rules
  - Names NEVER in URLs or query strings - always in the request body (I-11)
  - screeningId is opaque, immutable and permanently resolvable
  - Responses never include free-text vendor narrative beyond what Compliance
    has approved for first-line display
  - Backward-compatible changes only within /v1; breaking changes require /v2
    with a published deprecation window
  - A reference client (Java and/or Python) is published alongside the spec to
    reduce consumer integration effort

================================================================================
18. ALERTS, AUDIT AND EVIDENCE
================================================================================

18.1 Alert lifecycle
  screening -> [score >= alertThreshold] -> ALERT CREATED (OPEN)
                                                  |
                                             IN_REVIEW (assigned to analyst)
                                                  |
              +-----------------------------------+-----------------------+
              v                                   v                       v
        FALSE_POSITIVE                       TRUE_MATCH              ESCALATED
        (+ rationale)                    (+ rationale,              (to MLRO /
              |                           block process)         senior Compliance)
              v                                   v                       v
           CLOSED                              CLOSED                  CLOSED

Every transition writes an append-only alert_disposition_event. Nothing is ever
overwritten. A reversal of an earlier decision is a NEW event referencing the
prior one.

18.2 The audit obligation - the single question the design must answer
  "On 14 March 2027, an examiner asks why payment PAY-2026-0009812 was released
   on 24 August 2026. Reconstruct it."

The system must return, FROM STORED EVIDENCE AND WITHOUT RE-COMPUTATION FROM
CURRENT DATA:
  - the exact request payload received, and from which consuming system and user
  - the list_version in force, its source file identity and checksum, and the
    vendor publication date
  - the config_version in force - every threshold and weight applied
  - every candidate returned, its score and its feature-level explanation
  - the decision band and whether an alert was raised
  - every disposition event, with actor, timestamp and rationale
  - the correlation identifier linking back to the business transaction

18.3 Audit controls
  - Application DB role: INSERT and SELECT on audit_event only
  - Optional integrity chaining: each record carries a hash of the previous
    record, making silent tampering detectable
  - Retention per policy (assume 7 years minimum pending Compliance
    confirmation); included in backup and restore testing
  - Access to audit data is itself logged

================================================================================
19. TESTING AND MODEL VALIDATION
================================================================================

19.1 Test levels
  Unit          normalisation steps, each similarity measure, scoring
                arithmetic, threshold banding, attribute effects
  Component     ingestion pipeline stages against fixture files, including
                malformed and edge-case inputs
  Integration   end-to-end: file -> store -> index -> API response
  Contract      consumer-driven contract tests with at least one platform
  Performance   latency and throughput under load; batch timing; ingestion
                window
  Security      SAST, DAST, dependency scan, authn/authz negative tests
  Match quality precision, recall, F-measure vs a labelled benchmark corpus
  Parallel run  HORUS vs incumbent LSEG API over a common population
  UAT           business validation in the consuming platform

19.2 Match quality IS the acceptance criterion that matters
Functional correctness of the API is table stakes. The question that determines
whether this system may ever carry a regulated control is: DOES IT FIND WHAT IT
MUST FIND? The benchmark work is not optional testing hygiene; it is the core
evidence of the project.

19.3 Benchmark corpus construction
  TRUE POSITIVES   watchlist entities re-presented as they realistically appear
                   in payment and onboarding data: transliteration variants,
                   word order changes, missing middle names, initials, legal-form
                   variation, typographic errors        -> recall measurement
  HARD NEGATIVES   names deliberately close to listed entities but genuinely
                   different - common Arabic, West African and Francophone name
                   patterns where partial overlap is normal
                                                        -> precision measurement
  CLEAR POPULATION randomly sampled real (masked) counterparty, vendor and
                   beneficiary names                    -> FP rate at realistic
                                                           volumes
  ADVERSARIAL      deliberate evasion: character substitution, spacing
                   manipulation, name reversal, Unicode homoglyphs
                                                        -> robustness
  EDGE CASES       single-token names, very long organisation names, names with
                   numerals, empty and whitespace-only inputs, mixed script,
                   extremely common names               -> defensive behaviour

  TARGET SIZE: at least 1 000 labelled cases, of which at least 200 are true
  positives. Every case carries a ground-truth label AGREED WITH COMPLIANCE.
  Building the corpus is a JOINT task with Compliance, not a solo one.

19.4 Metrics reported
  Recall (the binding constraint), precision, F1, false positive rate per 1 000
  screenings, alert volume projection at production scale, latency distribution,
  and a per-case analysis of EVERY disagreement with the incumbent system.

19.5 Parallel run against the incumbent - classify every disagreement
  HORUS found, LSEG did not  possible improvement, possible noise
                             -> analyse each; may indicate over-loose matching
  LSEG found, HORUS did not  POTENTIAL RECALL GAP - THE SERIOUS CLASS
                             -> root-cause EVERY SINGLE INSTANCE; fix or
                                document explicitly
  Both found, different scores  calibration difference -> expected; document the
                                mapping
  Both clear                 agreement -> count only

  NO RECOMMENDATION TO RELY ON HORUS AS A PRIMARY CONTROL MAY BE MADE WHILE ANY
  UNEXPLAINED INSTANCE OF THE SECOND CLASS REMAINS.

19.6 Model validation
A screening engine is a MODEL in the regulatory sense. Expect and plan for:
documented model methodology (the specification plus ADRs); independent
validation before production use, potentially by a party outside DSD; periodic
re-validation and tuning cycles; change control on thresholds and weights with
regression testing on every change; retention of validation evidence for
supervisory and audit review.

The intern is not expected to complete formal model validation, but IS expected
to produce the artefacts that make it possible.

================================================================================
20. OBSERVABILITY AND THE STALE-LIST FAILURE MODE
================================================================================

20.1 Key metrics and alerts
  screening request rate by consumer and profile   capacity and adoption
  latency p50/p95/p99                              alert: p95 > 500 ms for 5 min
  error rate by class                              alert: > 1% 5xx over 5 min
  LIST AGE (hours since last successful load)      alert: > 36 hours - CRITICAL
  ingestion run outcome and duration               alert: any failure - CRITICAL
  records added/amended/deleted per run            alert: delta > 20% - warning
  alert rate by profile                            tuning signal; alert on
                                                   sudden deviation from baseline
  false positive rate (from dispositions)          trend monitoring
  candidate generation recall (synthetic canary)   alert: ANY canary miss -
                                                   CRITICAL

20.2 THE STALE-LIST FAILURE MODE (read this twice)
The most dangerous failure in a screening system is NOT an outage. An outage is
visible and forces the business to stop. The dangerous failure is a service that
continues to return confident NO_MATCH results against a watchlist that stopped
updating three weeks ago.

Defences, all mandatory:
  - every response carries its listVersion
  - list age is monitored as a FIRST-CLASS CONTROL METRIC with critical alerting
  - the service transitions to a DEGRADED STATE that surfaces staleness to
    consumers when the list exceeds the agreed maximum age
  - canary screening after every list load and every configuration change

20.3 Canary screening
A synthetic canary set of names with known expected outcomes is screened on a
schedule after every list load and every configuration change. Any deviation
from expected results raises a critical alert. This is the primary defence
against SILENT degradation of match quality.

20.4 Runbook contents (a required deliverable)
Ingestion failure diagnosis and re-run; list rollback to a previous version;
threshold change and emergency revert; performance degradation triage; consumer
onboarding (credential issuance and profile assignment); whitelist entry
addition and expiry; alert backlog escalation; audit extract for an examiner
request; disaster recovery restore procedure.

================================================================================
21. RE-SCREENING
================================================================================

Screening at a point in time is necessary but not sufficient: a customer who was
clear at onboarding may be designated tomorrow.

  PERIODIC RE-SCREENING. A consuming system submits its full population (all
  active vendors, all obligors) on a scheduled cadence via the batch API.
  Results compared against the previous run; only changes reported.

  DELTA RE-SCREENING (Phase 2 candidate). On each list update, the newly added
  and amended watchlist entries are screened against a REGISTERED POPULATION.
  Far cheaper than full re-screening; detects new designations within hours
  rather than at the next cycle. Requires the Bank to hold a registered
  population, which raises data ownership questions to settle with the EDR team.

  Outcome semantics:
    NEW_HIT       subject now matches, did not previously -> raise alert, notify
    CHANGED_HIT   existing match materially changed (score, category, source)
                  -> re-review
    RESOLVED_HIT  previous match no longer applies (de-listing) -> record, close
    UNCHANGED     no material change -> no action, recorded only

  Suppressing repeat alerts on previously dispositioned matches is essential to
  avoid alert fatigue - BUT suppression must be profile-scoped, time-bounded,
  and reversed AUTOMATICALLY whenever the underlying watchlist record changes.

================================================================================
22. CONSUMER INTEGRATION
================================================================================

22.1 Common pattern
  1. Consumer obtains an OAuth 2.0 token using its own client credentials.
  2. Consumer calls POST /v1/screenings with profile, business reference,
     correlation ID and idempotency key.
  3. Consumer stores screeningId against its business object - THIS IS ITS AUDIT
     ANCHOR.
  4. Consumer branches on decision:
       NO_MATCH                       -> proceed, record the result
       POSSIBLE_MATCH / STRONG_MATCH  -> route to compliance review inside the
                                         consuming application; display
                                         candidates and explanation; BLOCK
                                         progression
       ERROR / 503                    -> explicit error with retry and escalate
                                         options; NEVER proceed
  5. Consumer displays the outcome in its task/case interface and writes it to
     its own history for local audit.

22.2 OpsWorkflow - outward payment beneficiary
  Trigger      outward payment task routed to the compliance queue
  Subjects     beneficiary name; optionally beneficiary bank and intermediaries
  Profile      payment-beneficiary
  Attributes   country; DOB optional and not required
  Display      outcome in the task interface; candidate detail visible to
               compliance users
  Persistence  result and screeningId written to task history
  On hit       task routed directly to the compliance officer in-app
  On failure   error in the task with retry/escalate; the task CANNOT be
               completed as cleared
  Note         aligns with the existing name screening EPIC already raised for
               OpsWorkflow

22.3 Appian - Loan Administration
  Triggers     obligor onboarding; facility origination; each disbursement;
               guarantor and security provider addition; periodic review
  Subjects     obligor, guarantors, directors, UBOs, disbursement beneficiaries
  Profile      loan-obligor
  Pattern      multi-subject synchronous call; results aggregated to a single
               credit-workflow gate
  Note         loan workflows are LONG-LIVED. Record the screeningId PER STAGE -
               a screening result from origination is NOT evidence for a
               disbursement six months later.

22.4 VMS - Vendor Management System
  Triggers     vendor registration; contract award; annual recertification;
               bank-detail change
  Subjects     vendor legal entity, trading names, directors, beneficial owners
  Profile      vendor-onboarding
  Note         multilateral development bank debarment lists are highly relevant
               here and should be prioritised as an additional source (FR-012)
  Pattern      multi-subject sync at onboarding; batch API for annual
               recertification of the full vendor population

22.5 Future consumers
EDR (party MDM - screening on party create and amend), BOOP, customer onboarding
journeys, Software Factory KYC recertification. The contract is designed so
these require NO server-side change beyond profile configuration and client
credential issuance.

================================================================================
23. TECHNOLOGY STACK
================================================================================

  Service runtime    Java 21 + Spring Boot 3 (alternative: Python 3.12 +
                     FastAPI). Aligns with departmental estate; strong string
                     processing; mature observability.
  Matching library   Custom module + established libraries (commons-text,
                     simmetrics; jellyfish/rapidfuzz for Python).
                     DO NOT reimplement well-tested primitives. DO implement the
                     composition logic.
  Watchlist store    PostgreSQL 16 with pg_trgm (GIN) and unaccent. Already in
                     the estate; trigram indexing gives strong fuzzy candidate
                     generation without a second data platform.
  Search index       OpenSearch / Elasticsearch - CONSIDER ONLY IF PostgreSQL
                     trigram performance proves insufficient. Evidence first,
                     not by default.
  Cache              Amazon ElastiCache (Redis) for hot queries and list
                     metadata
  Ingestion          scheduled container task, or Apache Camel K route for
                     acquisition and orchestration (Camel K is the departmental
                     integration standard)
  Object storage     Amazon S3 with object lock - immutable raw-file archive
  Batch              container task with chunked parallel workers; Databricks
                     only if volumes justify it. Avoid platform sprawl.
  API management     NILE gateway (OAuth, throttling, developer portal)
  Identity           Auth0 / Okta, OAuth 2.0 client credentials
  CI/CD              departmental pipeline with SAST, DAST, dependency and image
                     scanning - mandatory gates
  Observability      structured JSON logs, Prometheus metrics, distributed
                     tracing
  Admin UI           React SPA consuming the SAME API. No privileged back door;
                     the UI is just another client.

Environments:
  Local / dev  synthetic watchlist (5-10k records)  development, unit/component
  Test         masked or sampled subset             integration, contract,
                                                    consumer sandbox
  UAT          full watchlist, non-prod consumers   Compliance validation,
                                                    benchmarking, UAT
  Production   full watchlist                       OUT OF SCOPE for the
                                                    internship; prepared for by it

ADRs required for: language and framework; datastore and index strategy;
matching library selection; batch execution approach; configuration storage.
Every ADR states the alternatives considered and the evidence for the choice.

================================================================================
24. SECURITY, DATA PROTECTION AND LICENSING
================================================================================

24.1 Classification
Watchlist content, screening queries and screening results are ALL CONFIDENTIAL.
Screening queries in particular reveal the Bank's counterparties, beneficiaries
and vendors - commercially sensitive independent of the watchlist itself.

24.2 Access control
  - Service-to-service OAuth 2.0 client credentials, one client per consuming
    system, scoped to permitted profiles
  - Human access to the admin UI via SSO with role-based authorisation
  - Segregation of duties: configuration change, alert disposition and audit
    access are DISTINCT roles
  - Least privilege at the database level; the application role has no
    UPDATE/DELETE on audit tables

24.3 Data protection
  - No personal data in URLs, query strings, log messages or error text
  - Non-production uses masked or synthetic data wherever feasible; where a real
    watchlist subset is required in UAT it is subject to the same controls as
    production
  - Documented, audited purge procedure for data past retention
  - Confirm with Data Protection whether a DPIA is required. ASSUME YES until
    told otherwise.

24.4 Application security
  - Input validation and LENGTH LIMITS on all name fields. An unbounded name
    string is a denial-of-service vector against fuzzy matching.
  - Parameterised queries throughout; no dynamic SQL from user input
  - Rate limiting and quota enforcement per consumer at the gateway
  - Dependency, container and secret scanning in CI
  - Security review and penetration test before any production consideration

24.5 Feed operational security
  - Vendor credentials in the secrets manager, rotated on schedule
  - Integrity verification of every received file before processing
  - Immutable archive of every raw file received, with retention lock

24.6 LICENSING - THE GATING DEPENDENCY
MUST BE RESOLVED BEFORE DESIGN FREEZE.

Subscription to a risk intelligence dataset does NOT automatically confer the
right to use that data as the basis of an internally built screening engine, to
store it in arbitrary internal systems, or to serve it to multiple downstream
applications. Licence terms commonly constrain permitted use, permitted user
counts, redistribution, derivative works and retention of historical versions.

Required actions (Weeks 1-2, owned by the PROJECT SPONSOR, not the intern):
  1. Obtain and read the current LSEG/World-Check licence and schedules.
  2. Confirm IN WRITING with LSEG and Bank Legal that in-house ingestion and
     screening across the named consuming systems is permitted under the
     existing subscription.
  3. Establish whether permitted scope is affected by the number of consuming
     systems, users or screening volume.
  4. If in-house use is NOT permitted, escalate immediately. The commercial case
     may require a licence variation, and scope may need to shift toward a
     CACHING / ORCHESTRATION LAYER in front of the vendor API rather than a full
     in-house engine.

Presenting a cost saving that a licence does not permit would be a serious and
avoidable error. Establish this early.

================================================================================
25. DELIVERY PLAN - 16 WEEKS, 6 PHASES
================================================================================

  Phase 0  Weeks 1-2   Onboarding and discovery   GATE: data dictionary approved
  Phase 1  Weeks 3-5   Ingestion                  GATE: full list loaded and
                                                        reconciled
  Phase 2  Weeks 6-9   Matching engine            GATE: benchmark corpus scored
  Phase 3  Weeks 10-12 API and integration        GATE: consumer calling
                                                        successfully in test
  Phase 4  Weeks 13-14 Quality and hardening      GATE: benchmark and security
                                                        review complete
  Phase 5  Weeks 15-16 Documentation and handover GATE: handover accepted

Weeks are indicative. THE PHASE GATES ARE NOT.

WEEKS 1-2 - ONBOARDING AND DISCOVERY
  Environment access, repository, pipeline, cloud accounts, tooling.
  Domain immersion: read the specification; sessions with Compliance on
  sanctions, PEPs and screening; OBSERVE A COMPLIANCE ANALYST DISPOSITIONING
  ALERTS IN ACTIMIZE FOR AT LEAST HALF A DAY.
  Obtain and study feed documentation and a sample file.
  D1: source data dictionary - actual structure, cardinalities, data quality
      observations, record volumes
  D2: requirements confirmation memo - requirements validated or amended with
      Compliance
  Sponsor action in parallel: resolve licensing (§24.6)

WEEKS 3-5 - INGESTION PIPELINE
  Canonical schema design and review (ADR-001)
  Streaming parser, validation, transformation, normalisation at load
  List versioning, staging, reconciliation, atomic promotion, rollback
  Component tests including malformed-file and encoding cases
  D3: working ingestion pipeline, full load reconciled by count and checksum
  D4: reconciliation report format, reviewed with Compliance
  GATE: full watchlist loaded, queryable by exact name, reconciled

WEEKS 6-9 - MATCHING ENGINE
  Normalisation pipeline with an explicit test suite PER RULE
  Candidate generation strategy; MEASURE RECALL OF BLOCKING IN ISOLATION BEFORE
  SCORING EXISTS
  Implement and empirically compare similarity measures (ADR-002)
  Composite scoring, attribute effects, decision bands, explanation generation
  Build the benchmark corpus WITH Compliance (joint task)
  Threshold sweep and calibration; precision-recall curves
  D5: matching engine module with >= 80% unit coverage
  D6: initial match quality report
  GATE: benchmark corpus scored end to end with documented metrics

WEEKS 10-12 - API AND REFERENCE INTEGRATION
  OpenAPI spec DRAFTED AND REVIEWED BEFORE IMPLEMENTATION (ADR-003)
  Implement synchronous, multi-subject and batch endpoints
  Alerts, dispositions, audit persistence
  OAuth integration; publish on the gateway; developer sandbox
  Minimal admin UI for alert review and whitelist management
  Reference integration with ONE consumer - VMS or Appian recommended OVER
  OpsWorkflow, because payment-path latency and failure semantics are the least
  forgiving place to learn
  D7: published API + sandbox
  D8: reference integration working in test
  GATE: consuming system screening successfully end to end

WEEKS 13-14 - QUALITY AND HARDENING
  Threshold tuning with Compliance; formal approval of the operating point
  Performance testing against NFR targets; optimisation where needed
  Security review, remediation, clean dependency and image scans
  PARALLEL RUN against the incumbent LSEG API; analyse EVERY disagreement
  Observability: dashboards, alerts, canary screening
  D9: benchmark and parallel-run report
  D10: performance and security test reports

WEEKS 15-16 - DOCUMENTATION AND HANDOVER
  Operational runbook; disaster recovery procedure
  Consumer integration guide plus reference client
  Consolidated ADR set
  D11: final project report - what was built, what was measured, what remains,
       HONEST ASSESSMENT OF LIMITATIONS
  D12: production readiness assessment - A CANDID GAP LIST, NOT A SALES PITCH
  D13: technical presentation to DSD and Compliance
  Code, documentation and knowledge handover to the receiving squad

================================================================================
26. DESCOPING ORDER - CRITICAL
================================================================================

If the assignment falls behind - which is normal and expected - descope in THIS
order, and NEVER in the reverse direction:

  1. Admin UI (Compliance can use API tooling in the interim)
  2. Batch API (single screening is the primary need)
  3. Internal blacklist and whitelist ingestion
  4. Additional public list sources
  5. Re-screening capability

NEVER DESCOPE:
  - list versioning
  - audit trail
  - explanation generation
  - the benchmark exercise

Rationale: those four are what make the system DEFENSIBLE. A fast,
well-integrated screening engine with no evidence of its match quality is WORSE
THAN NO SYSTEM AT ALL, because it invites misplaced confidence.

================================================================================
27. DELIVERABLES CHECKLIST
================================================================================

  D1  Source data dictionary                    Wk 2   Solution Architect
  D2  Requirements confirmation memo            Wk 2   Compliance owner
  D3  Ingestion pipeline (code + tests)         Wk 5   Solution Architect
  D4  Reconciliation report format              Wk 5   Compliance owner
  D5  Matching engine module (code + tests)     Wk 9   Solution Architect
  D6  Initial match quality report              Wk 9   Compliance owner
  D7  Published API + OpenAPI spec + sandbox    Wk 12  SA + consumer team
  D8  Reference integration in test             Wk 12  Consumer product owner
  D9  Benchmark and parallel-run report         Wk 14  Compliance + Director DSD
  D10 Performance and security test reports     Wk 14  QA + IT Security
  D11 Final project report                      Wk 16  Director DSD
  D12 Production readiness assessment           Wk 16  Director DSD + Compliance
  D13 Technical presentation                    Wk 16  DSD + Compliance
  ADR set (minimum 6)                           ongoing Solution Architect
  Operational runbook                           Wk 16  DevOps
  Consumer integration guide + reference client Wk 16  Consumer teams

ADR index:
  ADR-001 Canonical watchlist data model and versioning approach
  ADR-002 Similarity algorithm selection and composite scoring design
  ADR-003 API contract design: resource model, versioning, idempotency
  ADR-004 Datastore and indexing strategy (PostgreSQL trigram vs search engine)
  ADR-005 Batch execution approach
  ADR-006 Configuration storage and change control mechanism
  ADR-007 Language, framework and runtime selection
  ADR-008 Audit store design and immutability enforcement

================================================================================
28. RISK REGISTER
================================================================================

  R-1  Licence does not permit in-house use of the bulk feed
       Medium / CRITICAL - invalidates the project premise
       -> resolve Weeks 1-2 with LSEG and Legal before design freeze. Fallback:
          reposition as a caching and orchestration layer over the vendor API,
          which still delivers reusability and audit benefits with reduced cost
          saving.

  R-2  In-house match quality inferior to the vendor engine - a recall gap
       Medium / CRITICAL
       -> mandatory benchmark and parallel run; hard recall floor as an
          acceptance criterion; no reliance on HORUS as a primary control
          without evidence and Compliance sign-off.

  R-3  False positive rate exceeds operational capacity
       Medium-High / High
       -> measure alert volume at realistic scale during benchmarking; tune with
          Compliance; present operational cost explicitly before adoption.

  R-4  Internship duration insufficient for full scope
       High / Medium
       -> phase gates with defined descoping order (§26); MVP framing;
          deliverables sequenced so stopping early still leaves value.

  R-5  Feed format changes without notice, breaking ingestion
       Medium / High
       -> schema validation with explicit failure; alerting; previous version
          stays in service; monitor vendor change notifications.

  R-6  Stale list served with confident results (silent control failure)
       Medium / CRITICAL
       -> list age as a monitored control metric with critical alerting;
          listVersion on every response; degraded-state behaviour; canary
          screening.

  R-7  Performance inadequate at production scale
       Medium / Medium
       -> early performance testing; two-stage retrieval; index strategy
          validated against realistic volumes IN PHASE 2, NOT PHASE 4.

  R-8  Compliance availability insufficient for calibration and benchmarking
       Medium / High
       -> named owner with agreed weekly commitment secured before start;
          escalate slippage promptly.

  R-9  Scope creep into a full case management system
       Medium / Medium
       -> explicit out-of-scope list enforced at phase gates.

  R-10 Key-person dependency - knowledge leaves with the intern
       High / High
       -> documentation as a graded deliverable; pair working; formal handover.

  R-11 Consuming systems treat an error response as "clear"
       Low / CRITICAL
       -> fail-closed semantics documented AND CONTRACT-TESTED with each
          consumer, not merely stated.

  R-12 Regulatory or audit challenge to an internally built screening model
       Medium / High
       -> model validation artefacts from the outset; independent validation
          planned before production; existing controls remain in place during
          evaluation.

  R-13 Personal data handling non-compliance
       Low / High
       -> DPIA assumed required; Data Protection engaged in Week 2; masked data
          in non-production.

Dependencies:
  DEP-1 Written confirmation of licensing position  Sponsor/Legal/VM    Wk 2
  DEP-2 Feed access in non-production               Compliance/VM       Wk 2
  DEP-3 Named Compliance business owner             Director Compliance Wk 1
  DEP-4 Cloud, CI/CD and gateway access             DevOps              Wk 1
  DEP-5 Masked test population of names             Consumer teams      Wk 6
  DEP-6 Test access to incumbent LSEG API           Compliance          Wk 13
  DEP-7 Consumer team engineering time              Consumer PO         Wk 10-12
  DEP-8 IT Security review slot                     IT Security         Wk 13

================================================================================
29. OPEN QUESTIONS FOR THE WEEK 3 COMPLIANCE WORKSHOP
================================================================================

  1. Which World-Check categories are in scope per profile - sanctions only, or
     PEP and adverse media as well?
  2. What RECALL FLOOR does Compliance mandate as an acceptance criterion?
  3. What alert volume is operationally sustainable per profile?
  4. Who is authorised to change thresholds, and under what approval?
  5. What is the retention period for screening evidence?
  6. May a first-line user see full candidate detail, or only a status pending
     compliance review?
  7. Is a whitelist acceptable to Compliance at all, and if so with what expiry
     and re-approval discipline?
  8. Should HORUS results be fed back into Actimize, EDR or any system of
     record?
  9. What is the required behaviour when screening is unavailable - hard stop,
     or supervised manual override with recorded justification?
 10. Which consuming system should be the reference integration?

================================================================================
30. WORKED MATCHING EXAMPLES (illustrative shape; to be completed in Phase 2
    with actual engine output)
================================================================================

  A1  Mohammed Al-Sayed     vs Muhammad Alsayed        STRONG_MATCH
      phonetic agreement; hyphen and spacing normalised
  A2  Ahmed Ben Youssef     vs Ahmad Bin Yousef        POSSIBLE_MATCH
      transliteration variants across all tokens
  A3  Sahara Trading Ltd    vs Sahara Trading Limited  STRONG_MATCH
      legal-form normalisation
  A4  Africa Trading Company vs East Africa Trading Co TUNING CASE
      generic tokens must be down-weighted; distinctive token differs
  A5  J. Smith              vs John Smith              POSSIBLE_MATCH
      initial expansion; low distinctiveness of a common name
  A6  Ali Hassan            vs Alia Hassan             TUNING CASE
      single character, different person - precision test
  A7  (Arabic script name)  vs Mohammed Al-Sayed       STRONG_MATCH
      transliteration of query required
  A8  Smtih John            vs John Smith              POSSIBLE_MATCH
      transposition plus word order
  A9  M0hammed Al-Sayed     vs Mohammed Al-Sayed       STRONG_MATCH
      homoglyph and numeral substitution - ADVERSARIAL
  A10 Trading               vs (any)                   NO_MATCH
      below distinctiveness threshold; must not match broadly

================================================================================
31. KPIs POST-IMPLEMENTATION
================================================================================

  Screening availability          >= 99.9% business hours
  p95 latency                     <= 500 ms
  List freshness                  <= 24 hours in 99% of days
  Recall against benchmark        >= agreed Compliance floor
  False positive rate             downward trend QoQ AT CONSTANT RECALL
  Consumer systems integrated     3 within 12 months of production
  Cost per screening              trending toward marginal compute cost
  Mean time to onboard a consumer <= 5 person-days

================================================================================
32. GLOSSARY
================================================================================

  Adverse media     negative news reporting relevant to financial crime risk
  AKA               "Also Known As" - an alias recorded against a listed entity
  AML / CFT         Anti-Money Laundering / Counter-Financing of Terrorism
  Blocking          candidate generation - cheaply reducing a large reference
                    set to a plausible subset before expensive scoring
  CDD / EDD         Customer Due Diligence / Enhanced Due Diligence
  Debarment list    parties excluded from contracting with a multilateral
                    institution
  Delta feed        incremental update file containing only changes since the
                    previous delivery
  Disposition       an analyst's recorded decision on an alert
  Double Metaphone  phonetic encoding producing codes for similar-sounding
                    strings
  DSD               Digital Solutions Development
  EDR               Entity Data Repository (party MDM)
  ENSS              Enterprise Name Screening Service (HORUS)
  Fail closed       on failure, deny rather than permit
  False negative    a true match the system failed to detect - THE CRITICAL
                    FAILURE MODE
  False positive    an alert on a party who is not in fact the listed entity
  Jaro-Winkler      prefix-weighted string similarity, widely used in name
                    matching
  KYC               Know Your Customer
  Levenshtein       minimum single-character edits to transform one string into
                    another
  List version      an immutable identified snapshot of the watchlist as loaded
  MLRO              Money Laundering Reporting Officer
  NILE              the Bank's API management gateway
  Normalisation     deterministic transformation of names to a canonical
                    comparable form
  OFAC / OFSI       US Office of Foreign Assets Control / UK Office of Financial
                    Sanctions Implementation
  PEP               Politically Exposed Person
  Precision         of the alerts raised, the proportion that are true matches
  Recall            of the true matches present, the proportion detected
  SAR / STR         Suspicious Activity Report / Suspicious Transaction Report
  SDN               Specially Designated Nationals - OFAC's principal list
  Three lines of defence  risk governance model separating risk ownership,
                    oversight and assurance
  Transliteration   rendering a name from one script into another
  UBO               Ultimate Beneficial Owner
  World-Check       LSEG's risk intelligence database of sanctions, PEP and
                    adverse media records

================================================================================
33. WORKING PRINCIPLES FOR ANYONE PICKING UP THIS PROJECT
================================================================================

  1. Do not speculate on the cause of an anomaly. Identify it to 100% before
     proposing a solution. In a screening system a wrong theory produces a
     plausible fix that hides a silent recall failure.
  2. Reuse what already exists. Do not reimplement well-tested string similarity
     primitives; implement the COMPOSITION logic, which is where the value and
     the accountability sit.
  3. Ask of every commit: does this move us toward a benchmarked, explainable,
     auditable screening result? If not, it is not the next task.
  4. Verify against the actual feed, never against the specification's
     description of the feed (§13.3).
  5. Measure blocking recall in isolation before scoring exists. Recall lost in
     stage 1 is invisible in stage 2 metrics.
  6. Report what the benchmark actually showed, including where the system
     underperformed. In a compliance context this is the single most important
     professional behaviour.
  7. Early bad news is good news. Late bad news is a project failure. Anything
     that materially affects scope, cost or feasibility - especially licensing
     or match quality - escalates to the sponsor immediately.

================================================================================
END OF CONTEXT FILE
================================================================================