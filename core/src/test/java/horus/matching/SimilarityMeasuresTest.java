package horus.matching;

import static horus.matching.MatchTestData.n;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SimilarityMeasuresTest {

    private static final TokenWeighting UNIFORM = TokenWeighting.uniform();
    private static final TokenWeighting ORG = new GenericTokenWeighting(Set.of("trading", "co", "ltd", "group"), 0.2);

    // ---- token-sort Jaro-Winkler ----

    @Test
    void tokenSortJaroWinklerIgnoresWordOrder() {
        SimilarityMeasure m = new TokenSortJaroWinkler();

        assertThat(m.score(n("John Smith"), n("Smith John"), UNIFORM)).isEqualTo(1.0);
        assertThat(m.score(n("Zorvan Talmesk"), n("Harbin Osterfeld"), UNIFORM)).isLessThan(0.6);
    }

    @Test
    void tokenSortJaroWinklerJudgesTheDistinctiveCoreOfAnOrganisation() {
        SimilarityMeasure m = new TokenSortJaroWinkler();

        // With generic descriptors dropped both sides are just "sahara".
        assertThat(m.score(n("Sahara Trading Ltd"), n("Sahara Group Co"), ORG)).isEqualTo(1.0);
        // A name made only of generic tokens keeps them rather than becoming empty.
        assertThat(m.score(n("Trading Group"), n("Trading Group"), ORG)).isEqualTo(1.0);
    }

    // ---- token-set Jaccard ----

    @Test
    void jaccardIsOneForIdenticalTokenSetsAndZeroForDisjointOnes() {
        SimilarityMeasure m = new TokenSetJaccard();

        assertThat(m.score(n("John Smith"), n("Smith John"), UNIFORM)).isEqualTo(1.0);
        assertThat(m.score(n("John Smith"), n("Mary Jones"), UNIFORM)).isEqualTo(0.0);
    }

    @Test
    void jaccardToleratesAMissingMiddleNameProportionately() {
        SimilarityMeasure m = new TokenSetJaccard();

        // {john, smith} vs {john, paul, smith}: 2 shared of 3 in the union.
        assertThat(m.score(n("John Smith"), n("John Paul Smith"), UNIFORM)).isCloseTo(2.0 / 3.0, within(1e-9));
    }

    @Test
    void jaccardExpandsAnInitialToAnyTokenStartingWithIt() {
        SimilarityMeasure m = new TokenSetJaccard();

        // §30 A5: "J. Smith" vs "John Smith".
        assertThat(m.score(n("J. Smith"), n("John Smith"), UNIFORM)).isEqualTo(1.0);
        assertThat(m.score(n("K. Smith"), n("John Smith"), UNIFORM)).isCloseTo(1.0 / 3.0, within(1e-9));
    }

    @Test
    void jaccardDownWeightsGenericOrganisationTokens() {
        SimilarityMeasure m = new TokenSetJaccard();

        // Sharing only generic tokens is weak evidence; sharing the distinctive one is strong.
        double onlyGenericShared = m.score(n("Sahara Trading Ltd"), n("Nile Trading Ltd"), ORG);
        double distinctiveShared = m.score(n("Sahara Trading Ltd"), n("Sahara Mining Ltd"), ORG);
        assertThat(distinctiveShared).isGreaterThan(onlyGenericShared);
        assertThat(onlyGenericShared).isLessThan(0.3);
    }

    // ---- phonetic agreement ----

    @Test
    void phoneticAgreementMatchesSpellingVariants() {
        SimilarityMeasure m = new PhoneticAgreement();

        assertThat(m.score(n("Mohammed Hassan"), n("Muhammad Hasan"), UNIFORM)).isEqualTo(1.0);
        assertThat(m.score(n("Mohammed Hassan"), n("Zorvan Talmesk"), UNIFORM)).isEqualTo(0.0);
    }

    @Test
    void phoneticAgreementIsZeroWhenNeitherNameHasAPhoneticKey() {
        assertThat(new PhoneticAgreement().score(n("12345"), n("67890"), UNIFORM)).isEqualTo(0.0);
    }

    // ---- normalised edit distance ----

    @Test
    void editDistanceIsOneForIdenticalAndSeesSingleCharacterTypos() {
        SimilarityMeasure m = new NormalisedEditDistance();

        assertThat(m.score(n("Zorvan Talmesk"), n("Zorvan Talmesk"), UNIFORM)).isEqualTo(1.0);
        assertThat(m.score(n("Zorvan Talmesk"), n("Zorvan Talmesc"), UNIFORM)).isGreaterThan(0.9);
    }

    @Test
    void editDistanceCollapsesSpacingDifferences() {
        // "Al-Sayed" and "Alsayed" should collapse (§15.2 WARNINGS).
        assertThat(new NormalisedEditDistance().score(n("Al-Sayed"), n("Alsayed"), UNIFORM)).isEqualTo(1.0);
    }

    @Test
    void editDistanceDoesNotCollapseAliAndAlia() {
        // §15.2: over-normalisation guard. One edit over three characters is a big difference.
        assertThat(new NormalisedEditDistance().score(n("Ali"), n("Alia"), UNIFORM)).isLessThanOrEqualTo(0.75);
    }

    // ---- n-gram Dice ----

    @Test
    void ngramDiceIsOneForIdenticalAndDropsForDifferentNames() {
        SimilarityMeasure m = new NGramSimilarity();

        assertThat(m.score(n("Zorvan Talmesk"), n("Zorvan Talmesk"), UNIFORM)).isEqualTo(1.0);
        assertThat(m.score(n("Zorvan Talmesk"), n("Harbin Osterfeld"), UNIFORM)).isLessThan(0.2);
    }

    // ---- shared properties ----

    @Test
    void everyMeasureIsSymmetricBoundedAndDeterministic() {
        String[][] pairs = {
            {"Mohammed Al Sayed", "Muhammad Alsayed"}, {"Sahara Trading Ltd", "Sahara Group"}, {"Ali", "Alia"},
            {"J. Smith", "John Smith"}, {"Zorvan Talmesk", "Talmesk"}
        };
        for (SimilarityMeasure m : Registry.standard().measures()) {
            for (String[] p : pairs) {
                double ab = m.score(n(p[0]), n(p[1]), UNIFORM);
                double ba = m.score(n(p[1]), n(p[0]), UNIFORM);
                assertThat(ab).as(m.id()).isBetween(0.0, 1.0);
                assertThat(ab).as(m.id() + " symmetric").isCloseTo(ba, within(1e-9));
                assertThat(m.score(n(p[0]), n(p[1]), UNIFORM)).as(m.id() + " deterministic").isEqualTo(ab);
            }
        }
    }
}
