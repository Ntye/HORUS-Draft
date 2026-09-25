package horus.matching;

import static horus.matching.MatchTestData.candidate;
import static horus.matching.MatchTestData.query;
import static org.assertj.core.api.Assertions.assertThat;

import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityType;
import org.junit.jupiter.api.Test;

// Spec §30. The spec labels these "illustrative shape"; expected bands are taken from it verbatim.
// Bands depend on MatchConfig.defaults(), which is a starting point for benchmark calibration
// (§16.4) -- not an approved operating point. Names here are the spec's own generic examples.
class WorkedExamplesTest {

    private static final MatchConfig CFG = MatchConfig.defaults();
    private static final MatchingEngine ENGINE = new MatchingEngine(Registry.standard());

    private static MatchExplanation run(String queryName, EntityType type, String candidateName) {
        MatchExplanation e = ENGINE.score(query(queryName, type), candidate(candidateName, type).build(), CFG,
                WhitelistView.empty());
        // Every worked example must also be hand-reconstructible (I-3).
        assertThat(e.recompute()).isEqualTo(e.compositeScore());
        return e;
    }

    @Test
    void a1PhoneticAgreementAndHyphenSpacingIsAStrongMatch() {
        assertThat(run("Mohammed Al-Sayed", EntityType.INDIVIDUAL, "Muhammad Alsayed").band())
                .isEqualTo(DecisionBand.STRONG_MATCH);
    }

    @Test
    void a2TransliterationVariantsAcrossAllTokensAreAtLeastAPossibleMatch() {
        MatchExplanation e = run("Ahmed Ben Youssef", EntityType.INDIVIDUAL, "Ahmad Bin Yousef");
        assertThat(e.band()).isIn(DecisionBand.POSSIBLE_MATCH, DecisionBand.STRONG_MATCH);
    }

    @Test
    void a3LegalFormNormalisationIsAStrongMatch() {
        assertThat(run("Sahara Trading Ltd", EntityType.ORGANISATION, "Sahara Trading Limited").band())
                .isEqualTo(DecisionBand.STRONG_MATCH);
    }

    @Test
    void a4GenericTokensAreDownWeightedSoTheDistinctiveDifferenceShows() {
        // TUNING CASE: no band is prescribed. What must hold is that the shared generic words
        // do not by themselves carry the score.
        MatchExplanation e = run("Africa Trading Company", EntityType.ORGANISATION, "East Africa Trading Co");
        MatchExplanation genericOnly = run("Zorvan Trading Company", EntityType.ORGANISATION, "Harbin Trading Co");

        assertThat(genericOnly.band()).isEqualTo(DecisionBand.NO_MATCH);
        assertThat(e.compositeScore()).isGreaterThan(genericOnly.compositeScore());
    }

    @Test
    void a5InitialExpansionIsAtLeastAPossibleMatch() {
        MatchExplanation e = run("J. Smith", EntityType.INDIVIDUAL, "John Smith");
        assertThat(e.band()).isIn(DecisionBand.POSSIBLE_MATCH, DecisionBand.STRONG_MATCH);
    }

    @Test
    void a6AliHassanVersusAliaHassanIsATuningCaseThatIsAtLeastSurfaced() {
        // TUNING CASE: the spec prescribes no band. Measured behaviour with the default measures:
        // "ali" and "alia" share a Double Metaphone code, so this pair scores about as high as the
        // genuine A1 variants and cannot be separated by thresholds alone. It is surfaced for
        // review (recall first, I-2) and recorded as a known precision cost for benchmark tuning.
        MatchExplanation e = run("Ali Hassan", EntityType.INDIVIDUAL, "Alia Hassan");
        MatchExplanation exact = run("Ali Hassan", EntityType.INDIVIDUAL, "Ali Hassan");

        assertThat(e.band()).isNotEqualTo(DecisionBand.NO_MATCH);
        assertThat(e.compositeScore()).isLessThan(exact.compositeScore());
    }

    @Test
    void a7ArabicScriptQueryIsTransliteratedAndMatches() {
        MatchExplanation e = ENGINE.score(query("محمد السيد", EntityType.INDIVIDUAL),
                candidate("Mohammed Al-Sayed", EntityType.INDIVIDUAL).build(), CFG, WhitelistView.empty());

        assertThat(e.recompute()).isEqualTo(e.compositeScore());
        assertThat(e.band()).isEqualTo(DecisionBand.STRONG_MATCH);
    }

    @Test
    void a8TranspositionPlusWordOrderIsAtLeastAPossibleMatch() {
        MatchExplanation e = run("Smtih John", EntityType.INDIVIDUAL, "John Smith");
        assertThat(e.band()).isIn(DecisionBand.POSSIBLE_MATCH, DecisionBand.STRONG_MATCH);
    }

    @Test
    void a9HomoglyphNumeralSubstitutionIsAtLeastAPossibleMatch() {
        // Adversarial. The pipeline has no confusable-character step (documented gap), so the
        // token "m0hammed" survives; string-level measures must still surface it. If this fails
        // the honest outcome is a recall gap, reported -- not a loosened test.
        MatchExplanation e = run("M0hammed Al-Sayed", EntityType.INDIVIDUAL, "Mohammed Al-Sayed");
        assertThat(e.band()).isIn(DecisionBand.POSSIBLE_MATCH, DecisionBand.STRONG_MATCH);
    }

    @Test
    void a10ABareGenericWordMustNotMatchBroadly() {
        for (String candidate : new String[] {"Sahara Trading Ltd", "Trading Company Ltd", "East Africa Trading Co"}) {
            assertThat(run("Trading", EntityType.ORGANISATION, candidate).band())
                    .as(candidate).isEqualTo(DecisionBand.NO_MATCH);
        }
    }
}
