package horus.matching;

import static horus.matching.MatchTestData.candidate;
import static horus.matching.MatchTestData.entityId;
import static horus.matching.MatchTestData.n;
import static horus.matching.MatchTestData.query;
import static horus.matching.MatchTestData.versionId;
import static org.assertj.core.api.Assertions.assertThat;

import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityType;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    private static final MatchConfig CFG = MatchConfig.defaults();
    private static final MatchingEngine ENGINE = new MatchingEngine(Registry.standard());
    private static final WhitelistView NO_WHITELIST = WhitelistView.empty();

    private static MatchExplanation score(MatchQuery q, CandidateView c) {
        return ENGINE.score(q, c, CFG, NO_WHITELIST);
    }

    @Test
    void anIdenticalNameScoresAHundredAndIsAStrongMatch() {
        MatchExplanation e = score(query("Zorvan Talmesk", EntityType.INDIVIDUAL),
                candidate("Zorvan Talmesk", EntityType.INDIVIDUAL).build());

        assertThat(e.compositeScore()).isEqualTo(100);
        assertThat(e.band()).isEqualTo(DecisionBand.STRONG_MATCH);
    }

    @Test
    void anUnrelatedNameScoresLowAndIsNoMatch() {
        MatchExplanation e = score(query("Zorvan Talmesk", EntityType.INDIVIDUAL),
                candidate("Harbin Osterfeld", EntityType.INDIVIDUAL).build());

        assertThat(e.band()).isEqualTo(DecisionBand.NO_MATCH);
    }

    @Test
    void theExplanationRecomputesToTheStoredScoreAndBand() {
        // I-3: reconstructible by hand from what is stored, never from the engine.
        MatchQuery q = new MatchQuery(n("Mohammed Al Sayed"), Optional.of(EntityType.INDIVIDUAL),
                Optional.of(new PartialDate(1980, OptionalInt.empty(), OptionalInt.empty())), Set.of("EG"), Set.of());
        CandidateView c = candidate("Muhammad Alsayed", EntityType.INDIVIDUAL)
                .dob(new CandidateDob(OptionalInt.of(1980), OptionalInt.of(5), OptionalInt.of(17),
                        OptionalInt.empty(), OptionalInt.empty()))
                .country("EG").build();

        MatchExplanation e = score(q, c);

        assertThat(e.recompute()).isEqualTo(e.compositeScore());
        assertThat(e.recomputeBand()).isEqualTo(e.band());
        assertThat(e.features()).hasSize(5);
        assertThat(e.effects()).isNotEmpty();
    }

    @Test
    void aDobConflictDemotesButTheCandidateRemains() {
        // I-8: the candidate name similarity surfaced must still be present, lower, with a reason.
        MatchQuery withoutDob = query("Zorvan Talmesk", EntityType.INDIVIDUAL);
        MatchQuery withConflictingDob = new MatchQuery(n("Zorvan Talmesk"), Optional.of(EntityType.INDIVIDUAL),
                Optional.of(new PartialDate(1950, OptionalInt.empty(), OptionalInt.empty())), Set.of(), Set.of());
        CandidateView c = candidate("Zorvan Talmesk", EntityType.INDIVIDUAL)
                .dob(new CandidateDob(OptionalInt.of(1990), OptionalInt.empty(), OptionalInt.empty(),
                        OptionalInt.empty(), OptionalInt.empty())).build();

        MatchExplanation before = score(withoutDob, c);
        MatchExplanation after = score(withConflictingDob, c);

        assertThat(after).isNotNull();
        assertThat(after.compositeScore()).isLessThan(before.compositeScore());
        assertThat(after.effects()).anySatisfy(effect -> {
            assertThat(effect.attribute()).isEqualTo("dob");
            assertThat(effect.delta()).isNegative();
            assertThat(effect.reason()).isNotBlank();
        });
        assertThat(after.candidateEntityId()).isEqualTo(c.entityId());
    }

    @Test
    void nothingDemotesAScoreBelowZeroOrAboveAHundred() {
        MatchQuery q = new MatchQuery(n("Zorvan Talmesk"), Optional.of(EntityType.INDIVIDUAL),
                Optional.of(new PartialDate(1950, OptionalInt.empty(), OptionalInt.empty())), Set.of("AE"), Set.of());
        CandidateView bad = candidate("Harbin Osterfeld", EntityType.ORGANISATION)
                .dob(new CandidateDob(OptionalInt.of(1990), OptionalInt.empty(), OptionalInt.empty(),
                        OptionalInt.empty(), OptionalInt.empty()))
                .country("EG").delisted(true).build();

        MatchExplanation low = score(q, bad);
        MatchExplanation high = score(query("Zorvan Talmesk", EntityType.INDIVIDUAL),
                candidate("Zorvan Talmesk", EntityType.INDIVIDUAL).dob(new CandidateDob(OptionalInt.of(1990),
                        OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty()))
                        .country("AE").build());

        assertThat(low.compositeScore()).isBetween(0, 100);
        assertThat(high.compositeScore()).isBetween(0, 100);
    }

    @Test
    void anExactIdentifierMatchForcesAStrongMatchWhateverTheName() {
        // §16.1: short-circuits to a strong match regardless of name similarity.
        MatchQuery q = new MatchQuery(n("Completely Different Name"), Optional.empty(), Optional.empty(), Set.of(),
                Set.of("P1234567"));
        CandidateView c = candidate("Zorvan Talmesk", EntityType.INDIVIDUAL)
                .identifier(new CandidateIdentifier("P1234567", Optional.empty())).build();

        MatchExplanation e = score(q, c);

        assertThat(e.band()).isEqualTo(DecisionBand.STRONG_MATCH);
        assertThat(e.compositeScore()).isGreaterThanOrEqualTo(CFG.strongMatchThreshold());
        assertThat(e.recompute()).isEqualTo(e.compositeScore());
        assertThat(e.floor()).isPresent();
    }

    @Test
    void aDelistedEntityIsStillACandidateWithALowerScoreAndAReason() {
        // I-8 / I-2: de-listing demotes and explains; it never removes.
        CandidateView active = candidate("Zorvan Talmesk", EntityType.INDIVIDUAL).build();
        CandidateView delisted = candidate("Zorvan Talmesk", EntityType.INDIVIDUAL).delisted(true).build();
        MatchQuery q = query("Zorvan Talmesk", EntityType.INDIVIDUAL);

        MatchExplanation a = score(q, active);
        MatchExplanation d = score(q, delisted);

        assertThat(d.compositeScore()).isLessThan(a.compositeScore());
        assertThat(d.effects()).anySatisfy(e -> assertThat(e.reason()).containsIgnoringCase("de-listed"));
        assertThat(d.band()).isNotEqualTo(DecisionBand.NO_MATCH);
    }

    @Test
    void anActiveWhitelistEntryDemotesAndIsFlaggedNeverSuppressed() {
        CandidateView c = candidate("Zorvan Talmesk", EntityType.INDIVIDUAL).build();
        MatchQuery q = query("Zorvan Talmesk", EntityType.INDIVIDUAL);
        WhitelistView whitelist = new WhitelistView(Map.of(c.entityId(), c.entityVersionId()));

        MatchExplanation plain = score(q, c);
        MatchExplanation whitelisted = ENGINE.score(q, c, CFG, whitelist);

        assertThat(whitelisted).isNotNull();
        assertThat(whitelisted.compositeScore()).isLessThan(plain.compositeScore());
        assertThat(whitelisted.effects()).anySatisfy(e -> assertThat(e.attribute()).isEqualTo("whitelist"));
        assertThat(whitelisted.compositeScore()).isGreaterThan(0);
    }

    @Test
    void aStaleWhitelistEntryDoesNotDemoteButIsExplained() {
        // §21: approved against another version of the entity, so automatically inactive.
        CandidateView c = candidate("Zorvan Talmesk", EntityType.INDIVIDUAL).build();
        MatchQuery q = query("Zorvan Talmesk", EntityType.INDIVIDUAL);
        WhitelistView stale = new WhitelistView(Map.of(c.entityId(), versionId(999)));

        MatchExplanation plain = score(q, c);
        MatchExplanation withStale = ENGINE.score(q, c, CFG, stale);

        assertThat(withStale.compositeScore()).isEqualTo(plain.compositeScore());
        assertThat(withStale.effects()).anySatisfy(e -> {
            assertThat(e.attribute()).isEqualTo("whitelist");
            assertThat(e.delta()).isEqualTo(0.0);
            assertThat(e.reason()).containsIgnoringCase("inactive");
        });
    }

    @Test
    void anEntityTypeMismatchDemotesButKeepsTheCandidate() {
        MatchQuery q = query("Sahara Holdings", EntityType.INDIVIDUAL);
        MatchExplanation same = score(q, candidate("Sahara Holdings", EntityType.INDIVIDUAL).build());
        MatchExplanation mismatch = score(q, candidate("Sahara Holdings", EntityType.ORGANISATION).build());

        assertThat(mismatch.compositeScore()).isLessThan(same.compositeScore());
        assertThat(mismatch.effects()).anySatisfy(e -> assertThat(e.attribute()).isEqualTo("entity-type"));
    }

    @Test
    void theBestScoringAliasIsUsedAndReported() {
        CandidateView c = candidate("Harbin Osterfeld", EntityType.INDIVIDUAL)
                .alias(n("Zorvan Talmesk")).build();

        MatchExplanation e = score(query("Zorvan Talmesk", EntityType.INDIVIDUAL), c);

        assertThat(e.compositeScore()).isEqualTo(100);
        assertThat(e.matchedNameIsAlias()).isTrue();
    }

    @Test
    void anUnknownQueryTypeIsScoredUnderBothStrategiesKeepingTheHigher() {
        // I-2: ambiguity resolves toward keeping the candidate.
        MatchExplanation e = score(query("Sahara Trading Ltd", null),
                candidate("Sahara Trading Limited", EntityType.ORGANISATION).build());

        assertThat(e.strategy()).startsWith("BOTH");
        assertThat(e.compositeScore()).isGreaterThanOrEqualTo(CFG.strongMatchThreshold());
    }

    @Test
    void aQueryMadeOnlyOfGenericTokensIsCappedBelowTheAlertThresholdButStillReturned() {
        // §30 A10: "Trading" must not match broadly. It is demoted with a stated reason, not dropped.
        MatchExplanation e = score(query("Trading Group", EntityType.ORGANISATION),
                candidate("Trading Group", EntityType.ORGANISATION).build());

        assertThat(e).isNotNull();
        assertThat(e.band()).isEqualTo(DecisionBand.NO_MATCH);
        assertThat(e.cap()).isPresent();
        assertThat(e.notes()).anySatisfy(note -> assertThat(note).containsIgnoringCase("distinctive"));
        assertThat(e.recompute()).isEqualTo(e.compositeScore());
    }

    @Test
    void capabilityGapsNameWhatTheQuerySuppliedButTheCandidateLacks() {
        MatchQuery q = new MatchQuery(n("Zorvan Talmesk"), Optional.of(EntityType.INDIVIDUAL),
                Optional.of(new PartialDate(1980, OptionalInt.empty(), OptionalInt.empty())), Set.of("EG"), Set.of());

        MatchExplanation e = score(q, candidate("Zorvan Talmesk", EntityType.INDIVIDUAL).build());

        assertThat(e.capabilityGaps()).hasSize(2);
        assertThat(e.sourcesConsulted()).containsExactly("test-source");
    }

    @Test
    void scoringIsDeterministicAndTheJsonIsByteIdenticalAcrossRuns() {
        // I-4
        MatchQuery q = query("Mohammed Al Sayed", EntityType.INDIVIDUAL);
        CandidateView c = candidate("Muhammad Alsayed", EntityType.INDIVIDUAL).alias(n("M Sayed")).build();

        String first = score(q, c).toCanonicalJson();
        String second = score(q, c).toCanonicalJson();

        assertThat(first).isEqualTo(second);
        assertThat(first).contains("\"schemaVersion\"").doesNotContain("Mohammed");
    }

    @Test
    void theExplanationNeverContainsTheQueryName() {
        // I-11: explanations are stored evidence and are shared; the query name stays out of them.
        MatchExplanation e = score(query("Zorvan Talmesk", EntityType.INDIVIDUAL),
                candidate("Zorvan Talmesk", EntityType.INDIVIDUAL).build());

        assertThat(e.toString().toLowerCase()).doesNotContain("query");
        assertThat(entityId(1)).isEqualTo(e.candidateEntityId());
    }

    @Test
    void bandsFollowTheConfiguredThresholds() {
        assertThat(DecisionBands.classify(CFG.alertThreshold() - 1, CFG)).isEqualTo(DecisionBand.NO_MATCH);
        assertThat(DecisionBands.classify(CFG.alertThreshold(), CFG)).isEqualTo(DecisionBand.POSSIBLE_MATCH);
        assertThat(DecisionBands.classify(CFG.strongMatchThreshold() - 1, CFG)).isEqualTo(DecisionBand.POSSIBLE_MATCH);
        assertThat(DecisionBands.classify(CFG.strongMatchThreshold(), CFG)).isEqualTo(DecisionBand.STRONG_MATCH);
    }
}
