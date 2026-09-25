package horus.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.CanonicalSlot;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// I-7: an invalid configuration fails the boot, never a screening.
class RegistryValidationTest {

    private static final Set<CanonicalSlot> ALL_SLOTS = EnumSet.allOf(CanonicalSlot.class);
    private final Registry registry = Registry.standard();

    @Test
    void theDefaultConfigurationIsValidAgainstAFullyCapableSource() {
        assertThatCode(() -> registry.validateAgainst(MatchConfig.defaults(), ALL_SLOTS)).doesNotThrowAnyException();
    }

    @Test
    void thresholdsMustBeOrderedAndInRange() {
        MatchConfig bad = MatchConfig.defaults().withThresholds(90, 70);
        assertThatThrownBy(() -> registry.validateAgainst(bad, ALL_SLOTS))
                .isInstanceOf(InvalidMatchConfigException.class).hasMessageContaining("threshold");

        MatchConfig tooHigh = MatchConfig.defaults().withThresholds(70, 101);
        assertThatThrownBy(() -> registry.validateAgainst(tooHigh, ALL_SLOTS))
                .isInstanceOf(InvalidMatchConfigException.class);
    }

    @Test
    void aWeightForAnUnknownMeasureIsRejected() {
        MatchConfig bad = MatchConfig.defaults().withWeights(
                Map.of("token-sort-jaro-winkler", 1.0, "no-such-measure", 1.0), Map.of("token-set-jaccard", 1.0));

        assertThatThrownBy(() -> registry.validateAgainst(bad, ALL_SLOTS))
                .isInstanceOf(InvalidMatchConfigException.class).hasMessageContaining("no-such-measure");
    }

    @Test
    void weightsMustBeNonNegativeAndSumToSomethingPositive() {
        MatchConfig negative = MatchConfig.defaults().withWeights(
                Map.of("token-sort-jaro-winkler", -1.0, "token-set-jaccard", 2.0), Map.of("token-set-jaccard", 1.0));
        MatchConfig zero = MatchConfig.defaults().withWeights(
                Map.of("token-sort-jaro-winkler", 0.0), Map.of("token-set-jaccard", 1.0));

        assertThatThrownBy(() -> registry.validateAgainst(negative, ALL_SLOTS))
                .isInstanceOf(InvalidMatchConfigException.class);
        assertThatThrownBy(() -> registry.validateAgainst(zero, ALL_SLOTS))
                .isInstanceOf(InvalidMatchConfigException.class);
    }

    @Test
    void anEnabledComparatorWhoseSlotTheSourceCannotProvideFailsTheBoot() {
        Set<CanonicalSlot> noDob = EnumSet.allOf(CanonicalSlot.class);
        noDob.remove(CanonicalSlot.DATE_OF_BIRTH);

        assertThatThrownBy(() -> registry.validateAgainst(MatchConfig.defaults(), noDob))
                .isInstanceOf(InvalidMatchConfigException.class).hasMessageContaining("dob");
    }

    @Test
    void disablingTheComparatorMakesTheSameSourceAcceptable() {
        Set<CanonicalSlot> noDob = EnumSet.allOf(CanonicalSlot.class);
        noDob.remove(CanonicalSlot.DATE_OF_BIRTH);
        MatchConfig cfg = MatchConfig.defaults().withEnabledComparators(Set.of("country", "identifier", "entity-type", "status"));

        assertThatCode(() -> registry.validateAgainst(cfg, noDob)).doesNotThrowAnyException();
    }

    @Test
    void anUnknownComparatorIdIsRejected() {
        MatchConfig cfg = MatchConfig.defaults().withEnabledComparators(Set.of("dob", "astrology"));

        assertThatThrownBy(() -> registry.validateAgainst(cfg, ALL_SLOTS))
                .isInstanceOf(InvalidMatchConfigException.class).hasMessageContaining("astrology");
    }

    @Test
    void everyProblemIsReportedTogether() {
        MatchConfig cfg = MatchConfig.defaults().withThresholds(90, 70)
                .withEnabledComparators(Set.of("astrology"));

        assertThatThrownBy(() -> registry.validateAgainst(cfg, ALL_SLOTS))
                .satisfies(e -> assertThat(e.getMessage()).contains("threshold").contains("astrology"));
    }
}
