# horus.narrative — narrative fact extraction

Deterministic extraction of structured facts from `further_information`, the
free-text element of the World-Check bulk feed.

## Why this exists

The structured record carries **no status, no de-listing flag, no designation
date, and no list-source attribution**. Those facts exist only in the narrative.
So do ownership percentages, and every identifier type except passport.

Measurement across all 5,990,394 records (strided sample, n=49,919):

| | |
|---|---:|
| records with any sanctions section | 3.71% |
| `DIRECT SHAREHOLDER/S` present | 1.23% |
| …of those, with an explicit percentage | 89% |
| distinct verb forms, formal sections | 858 |
| distinct verb forms, prose sections | 14,308 |

## Why deterministic and not NLP

I-3 permits no machine learning in Phase 1: every value affecting a score must
trace to a stated rule an analyst can reconstruct by hand. I-4 requires the same
input to yield the same output on re-parse during an audit years later.

The text has two registers, and only one is needed. The **formal register**
(`[SECTION]` headers, `Mon YYYY - verb`, `PRIMARY NAME:`, `SDN Ref No`) is
editorially templated with a closed vocabulary — a grammar, handled by a lexer.
The **prose register** (`[BIOGRAPHY]`, `[REPORTS]`, `[FUNDING]`) is genuine
natural language and carries nothing the engine consumes. Declining to parse it
costs no coverage.

## Why per-source parsers

Each `[SOURCE]` section reproduces that authority's own record layout verbatim.
UK OFSI writes `Designation source:`, `Sex:`, `Secondary sanctions risk:`. OFAC
writes `SDN Ref No`, `c/o`, `Cedula No.`, and parenthesised `(a.k.a. X; a.k.a. Y)`.
EU single-quotes its aliases and cites `2001/931/CFSP`.

A generic extractor cannot tell a field label from a name. That is not
hypothetical — it is what produced the first alias measurement, where
`Financial sanctions imposed in addition to an asset freeze: Trust services`
was counted as an alias.

Each parser knows its own authority's label vocabulary. That knowledge is the
entire reason the split exists.

## Layout

```
horus/narrative/
  Provenance.java              rule id + character offsets, on every value
  SectionKind.java             SANCTIONS | REGULATORY_WARNING | OWNERSHIP |
                               LAW_ENFORCEMENT | PROSE | UNKNOWN
  Section.java                 splitter and classifier
  ActionVocabulary.java        verb -> ADDITION | REMOVAL | AMENDMENT | WARNING
  Facts.java                   extracted facts + builder
  ListSources.java             header -> canonical list source
  IdentifierParser.java        LEI, SWIFT BIC, IMO, Tax ID, Group ID, SDN Ref…
  OwnershipParser.java         DIRECT SHAREHOLDER/S and relatives
  StatusDeriver.java           per-source status, then record status
  NarrativeParser.java         orchestrator
  NarrativeExtractorMain.java  CLI harness over the feed
  SelfTest.java                fixture-based checks
  source/
    SourceParser.java          interface
    SourceParserRegistry.java  resolution, in order, with coverage counters
    AbstractSourceParser.java  shared grammar + label-aware name extraction
    OfsiSourceParser.java      UK OFSI / HMT / Crown Dependencies
    OfacSourceParser.java      US OFAC SDN / Consolidated / BIS
    EuSourceParser.java        EU consolidated and CFSP-aligned
    GenericSanctionsParser.java  fallback: events only, never names
```

## Build and run

Requires JDK 17+ (records). Confirm `java` and `javac` are the same install —
a mismatch gives `UnsupportedClassVersionError`.

```powershell
javac -d out (Get-ChildItem -Recurse -Filter *.java horus | % FullName)

# 1. self-test FIRST — one second, catches rules that quietly extract nothing
java -cp out horus.narrative.SelfTest

# 2. unskewed sample across the whole file (~50k records)
java -Xmx4g -cp out horus.narrative.NarrativeExtractorMain `
     world-check.xml extract 999999999 120

# 3. full run
java -Xmx4g -cp out horus.narrative.NarrativeExtractorMain world-check.xml extract_full
```

## Output

| File | Contents |
|---|---|
| `extracted_events.tsv` | `(uid, list_source, action_type, verb, year, provenance)` |
| `extracted_ownership.tsv` | owner, type marker, percentage, stated-or-unknown |
| `extracted_identifiers.tsv` | type, value, issuing context |
| `extracted_names.tsv` | value, name type, which parser produced it |
| `extracted_status.tsv` | record status with per-source breakdown and rationale |
| `status_by_category.tsv` | de-listing exposure by category |
| `quarantine.tsv` | recognised structure no rule handled |
| `unclassified_verbs.tsv` | verbs `ActionVocabulary` is missing |
| `parser_coverage.tsv` | which parser handled what share of sections |
| `list_sources.tsv` | canonical list sources and their record counts |
| `extraction_summary.txt` | totals and a read-in-this-order checklist |

## What to check, in order

**1. `quarantine.tsv`.** The honest record of what the rules did not handle.
Never expected to be empty. Read it before believing any coverage number.
Unmapped values are logged, never silently dropped (§13.1 stage 5).

**2. `unclassified_verbs.tsv`.** Every entry weakens the status derivation. Add
the frequent ones to `ActionVocabulary` and rerun. Never let an unrecognised
verb default to a status.

**3. `extracted_names.tsv` must contain names.** If field labels appear
(`Designation source`, `Secondary sanctions risk`), that parser's label list is
incomplete and the output must not be indexed.

**4. `parser_coverage.tsv`.** A high `GENERIC` share means the next
authority-specific parser is the highest-value work available.

## Invariants this code is built around

**I-8 — status demotes, never suppresses.** Nothing here removes a candidate.
`StatusDeriver.mayStillBeDesignated()` returns true for `INDETERMINATE`. A
parser bug that read a live designation as removed would otherwise become a
silent recall failure, which is the failure mode that matters (I-2).

**I-3 — provenance on everything.** Every value carries its rule id and
character offsets so an analyst can jump to the substring and verify. Without
that, a narrative-derived score contribution is unexplainable.

**I-9 — pure.** No I/O, no framework, no shared mutable state beyond the
registry's counters. Unit-testable at scale, independently benchmarkable.

**I-4 — deterministic.** No statistics, no thresholds, no model. Identical input
yields identical output, today and at audit.

## Scope

| | |
|---|---|
| **Do** | list-source attribution, `(source, action, year)` triples, ownership, identifiers |
| **Partial** | names, only where an authority-specific parser knows the labels |
| **Never** | anything from `BIOGRAPHY`, `REPORTS`, `FUNDING` |

`GenericSanctionsParser` deliberately extracts events but never names. Injecting
label text into the name index costs precision and buys no recall.

## Known limitations

Three authorities have parsers. Everything else falls through to the generic
fallback and contributes no names — deliberate, but it means name coverage is
partial by construction. `parser_coverage.tsv` quantifies the gap.

`ActionVocabulary` was built from an observed sample. It will not cover
everything. `unclassified_verbs.tsv` is how that stays visible instead of
becoming a silent default.

Ownership extraction handles the observed `Name (TYPE) (NN%)` shape. Free-form
ownership prose is quarantined rather than guessed at.
