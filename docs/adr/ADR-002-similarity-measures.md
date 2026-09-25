# ADR-002 — Similarity measure selection and composition

- **Status:** Proposed
- **Date:** 2026-09-24
- **Review:** Solution Architect; Compliance (thresholds and weights are policy)

## Context

Stage 2 scores each blocked candidate. Constraints: explainable, no ML, reconstructible by hand
(I-3); deterministic (I-4); attributes adjust and never eliminate (I-8); recall is binding (I-2).
Spec §15.3 lists ten candidate measures and requires the final composition to be justified
empirically against a benchmark corpus (§19.3).

## Options considered

1. **A single best measure** (e.g. Jaro-Winkler on the full string). Simple, but the worked
   examples fail in different ways: word order, particles, transliteration and initials each defeat
   a different measure.
2. **A weighted composite of five complementary measures (chosen):** token-sort Jaro-Winkler,
   token-set Jaccard, Double Metaphone agreement, normalised edit distance, trigram Dice — the
   weights and names of spec §16.2 — with person and organisation strategies (§15.4).
3. **A learned model.** Excluded by I-3 in Phase 1; recorded only so it is visible as the option not
   taken.

"It is what the spec lists" is part of the reason for option 2, and is stated as such: the spec's
§16.2 weights are *illustrative starting values*, not tuned ones.

## Evidence

**Synthetic worked examples (spec §30), per-feature scores and leave-one-out composite** — produced
by the real engine with `MatchConfig.defaults()` (alert 55, strong 75, both placeholders):

| Ex. | JW | Jacc | Phon | Edit | Ngram | Composite | Band | −JW | −Jacc | −Phon | −Edit | −Ngram |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A1 Al-Sayed / Alsayed | .95 | .33 | 1.00 | .87 | .48 | 78 | STRONG | 71 | 89 | 71 | 76 | 81 |
| A2 Ben Youssef / Bin Yousef | .94 | .00 | 1.00 | .83 | .48 | 71 | POSSIBLE | 60 | 88 | 61 | 68 | 73 |
| A3 Ltd / Limited | 1.00 | 1.00 | 1.00 | 1.00 | 1.00 | 100 | STRONG | 100 | 100 | 100 | 100 | 100 |
| A4 Africa Trading Co / East Africa… | .91 | .58 | .58 | .79 | .86 | 75 | STRONG | 70 | 82 | 77 | 74 | 72 |
| A5 J. Smith / John Smith | .78 | 1.00 | 1.00 | .67 | .62 | 85 | STRONG | 87 | 81 | 79 | 88 | 87 |
| A6 Ali Hassan / Alia Hassan | .92 | .33 | 1.00 | .90 | .71 | 80 | STRONG | 75 | 91 | 73 | 78 | 81 |
| A7 Arabic script / Al-Sayed | 1.00 | 1.00 | 1.00 | 1.00 | .17 | 92 | STRONG | 88 | 90 | 89 | 90 | 100 |
| A8 Smtih John / John Smith | .98 | .33 | .33 | .78 | .25 | 59 | POSSIBLE | 42 | 65 | 67 | 55 | 62 |
| A9 M0hammed / Mohammed | .98 | .50 | .50 | .93 | .87 | 74 | POSSIBLE | 65 | 81 | 83 | 71 | 73 |
| A10 "Trading" / Sahara Trading Ltd | .44 | .14 | .14 | .44 | .48 | 33 | NO_MATCH | 29 | 41 | 35 | 31 | 29 |

What the table shows (synthetic data, ten pairs — an illustration of behaviour, **not a
calibration**):

- No measure is redundant for recall: dropping Jaro-Winkler costs A1/A2/A8 the most (−7 to −17),
  dropping phonetic costs A1/A2/A5/A6 (−6 to −10).
- Trigram Dice is the weakest contributor and is *harmful* on A7 (dropping it lifts 92 → 100)
  because transliterated Arabic shares few trigrams with its Latin form.
- **A6 (Ali/Alia) scores 80, higher than the genuine variants A1 (78) and A2 (71).** The measures
  cannot separate a one-character different-person pair from same-person spelling variants:
  "ali" and "alia" share a Double Metaphone code. This is a known precision cost.
- **A4 scores 75 — exactly the placeholder STRONG threshold.** Generic-token down-weighting
  (`genericTokenWeight` 0.2) is not yet strong enough for organisations at these weights.

**Defects the examples exposed in the first implementation** (each now a regression test in
`WorkedExamplesTest`): before comparison-time particle views, A1 scored 47, A2 68, A5 68, A7 30,
A8 47. The measures were made symmetric and recall-favouring by (a) scoring three views of each name
— particles as written, merged into the next token ("al sayed" → "alsayed"), and dropped — and
keeping the best (b) expanding an initial in the phonetic measure, (c) scoring edit distance on both
the ordered and token-sorted form, and (d) adding a **consonant-skeleton** view only when either
name was transliterated from a non-Latin script (unvocalised Arabic produces "mhmd alsyd"). The
skeleton is deliberately *not* applied to Latin-vs-Latin names, where it would collapse Ali/Alia.

**Homoglyphs (A9):** the pipeline has no confusable-character step (documented in
`TransliterationStep`). "M0hammed" survives as the token `m0hammed` and is still surfaced (74,
POSSIBLE) by the string-level measures, but only just: it sits one point under the placeholder
STRONG threshold.

**Not measured:** any recall or precision on real names. The benchmark harness (Step 10) exists and
works, but the real labelled corpus is built and run by the operator with Compliance. This ADR
therefore **cannot** justify the weights or the thresholds; it justifies the *set* of measures and
the structure. What to measure: run `benchmark` on the corpus; re-run the leave-one-out above on
real misses; sweep `alertThreshold` and choose the point that meets Compliance's recall floor
(§16.4); test measure removal (especially trigram Dice) against recall, not against synthetic pairs.

## Decision

Option 2, with the comparison-time particle/skeleton views. Weights and thresholds are
**configuration** (I-7, ADR-005), and the shipped defaults are explicitly an uncalibrated starting
point.

## Consequences

- Easier: every score is a sum of five stated numbers plus stated effects; `recompute()` verifies it.
- Harder: taking the maximum over views is recall-favouring by construction — it raises false
  positives; the alert volume must be measured, not assumed.
- Open, to be closed by benchmark evidence: A4 (organisations), A6 (short-token near-misses), the
  weight of trigram Dice, and whether strong/alert thresholds separate variants from near-misses at
  all with these measures.
