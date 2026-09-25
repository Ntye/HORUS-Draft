package horus.application.blocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.blocking.BlockingIndex;
import horus.blocking.BlockingOutcome;
import horus.blocking.CandidateGenerator;
import horus.blocking.CandidateKey;
import horus.blocking.Completeness;
import horus.blocking.IndexCapabilities;
import horus.blocking.KeyProbe;
import horus.blocking.Strategy;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import horus.normalisation.NormalisationPipeline;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MeasureBlockingRecallTest {

    private static final NormalisationPipeline PIPELINE = NormalisationPipeline.standard();

    private static EntityId entity(long n) {
        return new EntityId(new UUID(0, n));
    }

    private static CandidateKey candidate(long n) {
        return new CandidateKey(entity(n), new EntityVersionId(new UUID(1, n)));
    }

    // Answers by the normalised query string, per strategy: no database, fully scripted.
    private static BlockingIndex index(Function<KeyProbe, BlockingOutcome> answer) {
        return new BlockingIndex() {
            @Override
            public IndexCapabilities capabilities() {
                return new IndexCapabilities(Set.of(Strategy.values()));
            }

            @Override
            public BlockingOutcome lookup(KeyProbe probe) {
                return answer.apply(probe);
            }
        };
    }

    private static BlockingOutcome outcome(KeyProbe probe, Completeness completeness, long... entities) {
        return new BlockingOutcome(
                probe.strategy(), completeness, java.util.Arrays.stream(entities).mapToObj(n -> candidate(n)).toList());
    }

    @Test
    void reportsRecallPerStrategyAndCombinedWithMeanCandidateSetSize() {
        // Case "Alpha Case" expects entity 1; "Beta Case" expects entity 2.
        // EXACT finds only Alpha's; TRIGRAM finds both; PHONETIC finds neither.
        BlockingIndex index = index(probe -> {
            boolean alpha = probe.name().normalised().startsWith("alpha");
            return switch (probe.strategy()) {
                case EXACT_HASH -> outcome(probe, Completeness.COMPLETE, alpha ? new long[] {1} : new long[] {});
                case TRIGRAM -> outcome(probe, Completeness.COMPLETE, alpha ? new long[] {1, 9} : new long[] {2});
                case PHONETIC -> outcome(probe, Completeness.COMPLETE, 7);
            };
        });
        MeasureBlockingRecall measure = new MeasureBlockingRecall(PIPELINE,
                new CandidateGenerator(index, List.of(Strategy.EXACT_HASH, Strategy.TRIGRAM, Strategy.PHONETIC)));

        MeasureBlockingRecall.Report report = measure.execute(List.of(
                new MeasureBlockingRecall.LabelledPair("Alpha Case", entity(1)),
                new MeasureBlockingRecall.LabelledPair("Beta Case", entity(2))));

        assertThat(report.cases()).isEqualTo(2);
        assertThat(report.recall(Strategy.EXACT_HASH)).isEqualTo(0.5);
        assertThat(report.recall(Strategy.TRIGRAM)).isEqualTo(1.0);
        assertThat(report.recall(Strategy.PHONETIC)).isEqualTo(0.0);
        assertThat(report.combinedRecall()).isEqualTo(1.0);
        // Union sizes: Alpha {1,9,7} = 3, Beta {2,7} = 2 -> mean 2.5.
        assertThat(report.meanCandidateSetSize()).isEqualTo(2.5);
        assertThat(report.missedCaseIndexes()).isEmpty();
    }

    @Test
    void missedCasesAreListedByPositionNeverByName() {
        // I-11: a report is shareable evidence; it must not echo the labelled names.
        BlockingIndex index = index(probe -> outcome(probe, Completeness.COMPLETE, 5));
        MeasureBlockingRecall measure =
                new MeasureBlockingRecall(PIPELINE, new CandidateGenerator(index, List.of(Strategy.TRIGRAM)));

        MeasureBlockingRecall.Report report = measure.execute(List.of(
                new MeasureBlockingRecall.LabelledPair("Alpha Case", entity(5)),
                new MeasureBlockingRecall.LabelledPair("Gamma Case", entity(6))));

        assertThat(report.combinedRecall()).isEqualTo(0.5);
        assertThat(report.missedCaseIndexes()).containsExactly(1);
        assertThat(report.toString()).doesNotContain("Gamma");
    }

    @Test
    void partialCasesAreCountedAndNeverCountedAsHits() {
        // I-1: a PARTIAL lookup that happens to contain the entity still proves nothing about
        // completeness -- it is reported separately and is not a clean hit for that strategy.
        BlockingIndex index = index(probe -> probe.strategy() == Strategy.TRIGRAM
                ? outcome(probe, Completeness.PARTIAL, 1)
                : outcome(probe, Completeness.COMPLETE, 1));
        MeasureBlockingRecall measure = new MeasureBlockingRecall(
                PIPELINE, new CandidateGenerator(index, List.of(Strategy.EXACT_HASH, Strategy.TRIGRAM)));

        MeasureBlockingRecall.Report report =
                measure.execute(List.of(new MeasureBlockingRecall.LabelledPair("Alpha Case", entity(1))));

        assertThat(report.partialCases()).isEqualTo(1);
        assertThat(report.recall(Strategy.TRIGRAM)).isEqualTo(0.0);
        assertThat(report.recall(Strategy.EXACT_HASH)).isEqualTo(1.0);
    }

    @Test
    void truncatedCasesAreCounted() {
        BlockingIndex index = index(probe -> outcome(probe, Completeness.TRUNCATED, 1));
        MeasureBlockingRecall measure =
                new MeasureBlockingRecall(PIPELINE, new CandidateGenerator(index, List.of(Strategy.TRIGRAM)));

        MeasureBlockingRecall.Report report =
                measure.execute(List.of(new MeasureBlockingRecall.LabelledPair("Alpha Case", entity(1))));

        assertThat(report.truncatedCases()).isEqualTo(1);
        assertThat(report.combinedRecall()).isEqualTo(1.0);
    }

    @Test
    void anEmptyCorpusIsRejectedRatherThanReportedAsPerfectRecall() {
        BlockingIndex index = index(probe -> outcome(probe, Completeness.COMPLETE));
        MeasureBlockingRecall measure =
                new MeasureBlockingRecall(PIPELINE, new CandidateGenerator(index, List.of(Strategy.TRIGRAM)));

        assertThatThrownBy(() -> measure.execute(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recallOfAnUnconfiguredStrategyIsRejected() {
        BlockingIndex index = index(probe -> outcome(probe, Completeness.COMPLETE, 1));
        MeasureBlockingRecall measure =
                new MeasureBlockingRecall(PIPELINE, new CandidateGenerator(index, List.of(Strategy.TRIGRAM)));
        MeasureBlockingRecall.Report report =
                measure.execute(List.of(new MeasureBlockingRecall.LabelledPair("Alpha Case", entity(1))));

        assertThatThrownBy(() -> report.recall(Strategy.PHONETIC)).isInstanceOf(IllegalArgumentException.class);
        assertThat(report.hitsByStrategy()).containsOnlyKeys(Strategy.TRIGRAM);
    }

    @Test
    void reportIsDeterministicAcrossRuns() {
        BlockingIndex index = index(probe -> outcome(probe, Completeness.COMPLETE, 3, 1, 2));
        MeasureBlockingRecall measure = new MeasureBlockingRecall(
                PIPELINE, new CandidateGenerator(index, List.of(Strategy.EXACT_HASH, Strategy.TRIGRAM)));
        List<MeasureBlockingRecall.LabelledPair> corpus = List.of(
                new MeasureBlockingRecall.LabelledPair("Alpha Case", entity(1)),
                new MeasureBlockingRecall.LabelledPair("Beta Case", entity(8)));

        assertThat(measure.execute(corpus)).isEqualTo(measure.execute(corpus));
        assertThat(Map.copyOf(measure.execute(corpus).hitsByStrategy())).isNotEmpty();
    }
}
