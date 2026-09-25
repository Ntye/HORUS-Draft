package horus.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatchConfigTest {

    @Test
    void setsAreStoredInSortedOrderSoSerialisationIsDeterministic() {
        // I-4: iteration order must not depend on the JVM's randomised Set.of / Set.copyOf salt.
        MatchConfig config = MatchConfig.defaults().withEnabledComparators(Set.of("status", "dob", "country"));

        assertThat(List.copyOf(config.enabledComparators())).containsExactly("country", "dob", "status");
        assertThat(List.copyOf(config.genericTokens())).isSorted();
    }

    @Test
    void weightMapsAreStoredSortedByMeasureId() {
        MatchConfig config = MatchConfig.defaults();

        assertThat(List.copyOf(config.individualWeights().keySet())).isSorted();
        assertThat(List.copyOf(config.organisationWeights().keySet())).isSorted();
    }

    @Test
    void theStoredCollectionsAreImmutable() {
        MatchConfig config = MatchConfig.defaults();

        assertThatThrownBy(() -> config.enabledComparators().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> config.individualWeights().put("x", 1.0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullComponentsAreRejected() {
        assertThatThrownBy(() -> MatchConfig.defaults().withWeights(null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
