package horus.blocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import horus.normalisation.NormalisationPipeline;
import horus.normalisation.NormalisedName;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateGeneratorTest {

    private static final NormalisationPipeline PIPELINE = NormalisationPipeline.standard();

    private static CandidateKey key(long n) {
        return new CandidateKey(new EntityId(new UUID(0, n)), new EntityVersionId(new UUID(1, n)));
    }

    // Scripted index: one canned outcome (or failure) per strategy.
    private static final class ScriptedIndex implements BlockingIndex {
        final Map<Strategy, BlockingOutcome> outcomes = new EnumMap<>(Strategy.class);
        final Set<Strategy> unsupported;
        final Set<Strategy> throwing;

        ScriptedIndex(Set<Strategy> unsupported, Set<Strategy> throwing) {
            this.unsupported = unsupported;
            this.throwing = throwing;
        }

        ScriptedIndex() {
            this(Set.of(), Set.of());
        }

        @Override
        public IndexCapabilities capabilities() {
            Set<Strategy> supported = new HashSet<>(Set.of(Strategy.values()));
            supported.removeAll(unsupported);
            return new IndexCapabilities(supported);
        }

        @Override
        public BlockingOutcome lookup(KeyProbe probe) {
            if (throwing.contains(probe.strategy())) {
                throw new IllegalStateException("index unavailable");
            }
            return outcomes.getOrDefault(
                    probe.strategy(), new BlockingOutcome(probe.strategy(), Completeness.COMPLETE, List.of()));
        }
    }

    private static NormalisedName name(String raw) {
        return PIPELINE.normalise(raw);
    }

    @Test
    void candidateSetIsTheUnionOfStrategiesNeverTheIntersection() {
        ScriptedIndex index = new ScriptedIndex();
        index.outcomes.put(Strategy.TRIGRAM,
                new BlockingOutcome(Strategy.TRIGRAM, Completeness.COMPLETE, List.of(key(1), key(2))));
        index.outcomes.put(Strategy.PHONETIC,
                new BlockingOutcome(Strategy.PHONETIC, Completeness.COMPLETE, List.of(key(2), key(3))));
        CandidateGenerator generator =
                new CandidateGenerator(index, List.of(Strategy.EXACT_HASH, Strategy.TRIGRAM, Strategy.PHONETIC));

        CandidateSet set = generator.generate(name("Zorvan Talmesk"));

        assertThat(set.completeness()).isEqualTo(Completeness.COMPLETE);
        assertThat(set.candidates()).containsExactly(key(1), key(2), key(3));
        assertThat(set.strategiesFor(key(2))).containsExactlyInAnyOrder(Strategy.TRIGRAM, Strategy.PHONETIC);
        assertThat(set.strategiesFor(key(1))).containsExactly(Strategy.TRIGRAM);
    }

    @Test
    void anyPartialStrategyMakesTheWholeSetPartial() {
        // I-1: one strategy reporting incompleteness must never be masked by the others.
        ScriptedIndex index = new ScriptedIndex();
        index.outcomes.put(Strategy.TRIGRAM,
                new BlockingOutcome(Strategy.TRIGRAM, Completeness.PARTIAL, List.of(key(1))));
        index.outcomes.put(Strategy.EXACT_HASH,
                new BlockingOutcome(Strategy.EXACT_HASH, Completeness.COMPLETE, List.of(key(2))));
        CandidateGenerator generator = new CandidateGenerator(index, List.of(Strategy.EXACT_HASH, Strategy.TRIGRAM));

        CandidateSet set = generator.generate(name("Zorvan Talmesk"));

        assertThat(set.completeness()).isEqualTo(Completeness.PARTIAL);
        assertThat(set.candidates()).containsExactly(key(1), key(2));
    }

    @Test
    void truncatedIsReportedButPartialDominatesIt() {
        ScriptedIndex truncatedOnly = new ScriptedIndex();
        truncatedOnly.outcomes.put(Strategy.TRIGRAM,
                new BlockingOutcome(Strategy.TRIGRAM, Completeness.TRUNCATED, List.of(key(1))));
        assertThat(new CandidateGenerator(truncatedOnly, List.of(Strategy.EXACT_HASH, Strategy.TRIGRAM))
                        .generate(name("Zorvan Talmesk")).completeness())
                .isEqualTo(Completeness.TRUNCATED);

        ScriptedIndex both = new ScriptedIndex();
        both.outcomes.put(Strategy.TRIGRAM,
                new BlockingOutcome(Strategy.TRIGRAM, Completeness.TRUNCATED, List.of()));
        both.outcomes.put(Strategy.EXACT_HASH,
                new BlockingOutcome(Strategy.EXACT_HASH, Completeness.PARTIAL, List.of()));
        assertThat(new CandidateGenerator(both, List.of(Strategy.EXACT_HASH, Strategy.TRIGRAM))
                        .generate(name("Zorvan Talmesk")).completeness())
                .isEqualTo(Completeness.PARTIAL);
    }

    @Test
    void aFailingIndexLookupBecomesPartialNotAnEmptyClear() {
        // I-1: no catch that returns an empty result. The failure is an explicit PARTIAL outcome.
        ScriptedIndex index = new ScriptedIndex(Set.of(), Set.of(Strategy.TRIGRAM));
        CandidateGenerator generator = new CandidateGenerator(index, List.of(Strategy.EXACT_HASH, Strategy.TRIGRAM));

        CandidateSet set = generator.generate(name("Zorvan Talmesk"));

        assertThat(set.completeness()).isEqualTo(Completeness.PARTIAL);
        assertThat(set.outcomes()).extracting(BlockingOutcome::completeness)
                .containsExactly(Completeness.COMPLETE, Completeness.PARTIAL);
    }

    @Test
    void aQueryThatNormalisesToNothingIsPartialNotAnEmptyClear() {
        // I-1: "nothing matched" is only meaningful when something was searched for.
        CandidateGenerator generator =
                new CandidateGenerator(new ScriptedIndex(), List.of(Strategy.EXACT_HASH, Strategy.TRIGRAM));

        CandidateSet set = generator.generate(name("---"));

        assertThat(set.completeness()).isEqualTo(Completeness.PARTIAL);
        assertThat(set.candidates()).isEmpty();
    }

    @Test
    void constructionFailsWhenAConfiguredStrategyIsNotSupportedByTheIndex() {
        // I-7: an invalid configuration fails the boot, not the screening.
        ScriptedIndex index = new ScriptedIndex(Set.of(Strategy.PHONETIC), Set.of());

        assertThatThrownBy(() -> new CandidateGenerator(index, List.of(Strategy.EXACT_HASH, Strategy.PHONETIC)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PHONETIC");
    }

    @Test
    void constructionRejectsAnEmptyOrDuplicatedStrategyList() {
        ScriptedIndex index = new ScriptedIndex();
        assertThatThrownBy(() -> new CandidateGenerator(index, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CandidateGenerator(index, List.of(Strategy.TRIGRAM, Strategy.TRIGRAM)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outputIsSortedAndDeterministicWhateverOrderTheIndexReturns() {
        ScriptedIndex index = new ScriptedIndex();
        index.outcomes.put(Strategy.TRIGRAM,
                new BlockingOutcome(Strategy.TRIGRAM, Completeness.COMPLETE, List.of(key(9), key(2), key(5))));
        CandidateGenerator generator = new CandidateGenerator(index, List.of(Strategy.TRIGRAM));

        CandidateSet first = generator.generate(name("Zorvan Talmesk"));
        CandidateSet second = generator.generate(name("Zorvan Talmesk"));

        assertThat(first.candidates()).containsExactly(key(2), key(5), key(9));
        assertThat(first).isEqualTo(second);
    }

    @Test
    void theProbeCarriesTheNormalisedQueryForItsStrategy() {
        List<KeyProbe> seen = new ArrayList<>();
        BlockingIndex recording = new BlockingIndex() {
            @Override
            public IndexCapabilities capabilities() {
                return new IndexCapabilities(Set.of(Strategy.values()));
            }

            @Override
            public BlockingOutcome lookup(KeyProbe probe) {
                seen.add(probe);
                return new BlockingOutcome(probe.strategy(), Completeness.COMPLETE, List.of());
            }
        };
        NormalisedName query = name("Zorvan Talmesk");

        new CandidateGenerator(recording, List.of(Strategy.EXACT_HASH, Strategy.PHONETIC)).generate(query);

        assertThat(seen).extracting(KeyProbe::strategy).containsExactly(Strategy.EXACT_HASH, Strategy.PHONETIC);
        assertThat(seen).allSatisfy(p -> assertThat(p.name()).isEqualTo(query));
    }
}
