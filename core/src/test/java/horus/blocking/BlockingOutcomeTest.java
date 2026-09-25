package horus.blocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlockingOutcomeTest {

    private static CandidateKey key(long n) {
        return new CandidateKey(new EntityId(new UUID(0, n)), new EntityVersionId(new UUID(1, n)));
    }

    @Test
    void candidatesAreSortedDistinctAndImmutable() {
        BlockingOutcome outcome =
                new BlockingOutcome(Strategy.TRIGRAM, Completeness.COMPLETE, List.of(key(3), key(1), key(3)));

        assertThat(outcome.candidates()).containsExactly(key(1), key(3));
        assertThatThrownBy(() -> outcome.candidates().add(key(9))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNulls() {
        assertThatThrownBy(() -> new BlockingOutcome(null, Completeness.COMPLETE, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockingOutcome(Strategy.TRIGRAM, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockingOutcome(Strategy.TRIGRAM, Completeness.COMPLETE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partialFactoryHasNoCandidatesAndIsPartial() {
        BlockingOutcome outcome = BlockingOutcome.partial(Strategy.PHONETIC);

        assertThat(outcome.completeness()).isEqualTo(Completeness.PARTIAL);
        assertThat(outcome.candidates()).isEmpty();
    }

    @Test
    void completenessWorstOfOrdersPartialOverTruncatedOverComplete() {
        assertThat(Completeness.worst(Completeness.COMPLETE, Completeness.TRUNCATED))
                .isEqualTo(Completeness.TRUNCATED);
        assertThat(Completeness.worst(Completeness.PARTIAL, Completeness.TRUNCATED)).isEqualTo(Completeness.PARTIAL);
        assertThat(Completeness.worst(Completeness.COMPLETE, Completeness.COMPLETE)).isEqualTo(Completeness.COMPLETE);
    }

    @Test
    void indexCapabilitiesMustSupportAtLeastOneStrategy() {
        assertThatThrownBy(() -> new IndexCapabilities(Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(new IndexCapabilities(Set.of(Strategy.TRIGRAM)).supports(Strategy.TRIGRAM)).isTrue();
        assertThat(new IndexCapabilities(Set.of(Strategy.TRIGRAM)).supports(Strategy.PHONETIC)).isFalse();
    }
}
