package horus.application.blocking;

import horus.blocking.BlockingOutcome;
import horus.blocking.CandidateGenerator;
import horus.blocking.CandidateSet;
import horus.blocking.Completeness;
import horus.blocking.Strategy;
import horus.domain.shared.EntityId;
import horus.normalisation.NormalisationPipeline;
import horus.normalisation.NormalisedName;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// CLAUDE.md §10: blocking recall is measured in isolation, before scoring exists, because recall
// lost at stage 1 is invisible in stage 2 metrics. Given labelled (query, expected entity) pairs
// it reports the fraction whose expected entity is in the candidate set -- per strategy and
// combined -- and the mean candidate-set size (the cost side of the trade).
public final class MeasureBlockingRecall {

    public record LabelledPair(String query, EntityId expected) {

        public LabelledPair {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query must not be blank");
            }
            if (expected == null) {
                throw new IllegalArgumentException("expected must not be null");
            }
        }
    }

    // I-11: nothing here carries a query name. Misses are positions in the corpus, which the
    // operator can look up in their own file.
    public record Report(
            int cases,
            int combinedHits,
            int partialCases,
            int truncatedCases,
            Map<Strategy, Integer> hitsByStrategy,
            double meanCandidateSetSize,
            List<Integer> missedCaseIndexes) {

        public Report {
            hitsByStrategy = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(hitsByStrategy));
            missedCaseIndexes = List.copyOf(missedCaseIndexes);
        }

        public double recall(Strategy strategy) {
            Integer hits = hitsByStrategy.get(strategy);
            if (hits == null) {
                throw new IllegalArgumentException("strategy was not measured: " + strategy);
            }
            return hits / (double) cases;
        }

        public double combinedRecall() {
            return combinedHits / (double) cases;
        }
    }

    private final NormalisationPipeline pipeline;
    private final CandidateGenerator generator;
    private final List<Strategy> strategies;

    public MeasureBlockingRecall(NormalisationPipeline pipeline, CandidateGenerator generator) {
        if (pipeline == null || generator == null) {
            throw new IllegalArgumentException("pipeline and generator must not be null");
        }
        this.pipeline = pipeline;
        this.generator = generator;
        this.strategies = generator.strategies();
    }

    public Report execute(List<LabelledPair> corpus) {
        if (corpus == null || corpus.isEmpty()) {
            // An empty corpus has no recall; 0/0 must not read as a pass.
            throw new IllegalArgumentException("corpus must not be empty");
        }
        Map<Strategy, Integer> hitsByStrategy = new EnumMap<>(Strategy.class);
        strategies.forEach(s -> hitsByStrategy.put(s, 0));
        int combinedHits = 0;
        int partialCases = 0;
        int truncatedCases = 0;
        long totalCandidates = 0;
        List<Integer> missed = new ArrayList<>();

        for (int i = 0; i < corpus.size(); i++) {
            LabelledPair pair = corpus.get(i);
            NormalisedName query = pipeline.normalise(pair.query());
            CandidateSet set = generator.generate(query);
            totalCandidates += set.candidates().size();

            for (BlockingOutcome outcome : set.outcomes()) {
                // I-1: a PARTIAL lookup proves nothing about completeness, so it is never a hit.
                if (outcome.completeness() != Completeness.PARTIAL && contains(outcome, pair.expected())) {
                    hitsByStrategy.merge(outcome.strategy(), 1, Integer::sum);
                }
            }

            if (set.completeness() == Completeness.PARTIAL) {
                // Screening would return ERROR here -- not a false negative, but not a hit either.
                partialCases++;
            } else {
                if (set.completeness() == Completeness.TRUNCATED) {
                    truncatedCases++;
                }
                if (set.candidates().stream().anyMatch(c -> c.entityId().equals(pair.expected()))) {
                    combinedHits++;
                } else {
                    missed.add(i);
                }
            }
        }
        return new Report(corpus.size(), combinedHits, partialCases, truncatedCases, hitsByStrategy,
                totalCandidates / (double) corpus.size(), missed);
    }

    private static boolean contains(BlockingOutcome outcome, EntityId expected) {
        return outcome.candidates().stream().anyMatch(c -> c.entityId().equals(expected));
    }
}
