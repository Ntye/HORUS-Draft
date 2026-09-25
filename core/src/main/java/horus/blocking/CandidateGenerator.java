package horus.blocking;

import horus.normalisation.NormalisedName;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

// Stage 1 of screening: union of strategies, worst-case completeness (I-1, I-2).
public final class CandidateGenerator {

    private final BlockingIndex index;
    private final List<Strategy> strategies;

    public CandidateGenerator(BlockingIndex index, List<Strategy> strategies) {
        if (index == null) {
            throw new IllegalArgumentException("index must not be null");
        }
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalArgumentException("at least one strategy must be configured");
        }
        if (EnumSet.copyOf(strategies).size() != strategies.size()) {
            throw new IllegalArgumentException("strategies must not repeat");
        }
        IndexCapabilities capabilities = index.capabilities();
        for (Strategy strategy : strategies) {
            // I-7: fails at construction (boot), not during a screening.
            if (!capabilities.supports(strategy)) {
                throw new IllegalStateException("configured strategy " + strategy + " is not supported by the index");
            }
        }
        this.index = index;
        this.strategies = List.copyOf(strategies);
    }

    public List<Strategy> strategies() {
        return strategies;
    }

    public CandidateSet generate(NormalisedName query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        List<BlockingOutcome> outcomes = new ArrayList<>();
        for (Strategy strategy : strategies) {
            outcomes.add(lookup(strategy, query));
        }
        return new CandidateSet(outcomes);
    }

    private BlockingOutcome lookup(Strategy strategy, NormalisedName query) {
        // I-1: an empty query would match nothing and look like a clear. It is unscreenable.
        if (query.normalised().isBlank()) {
            return BlockingOutcome.partial(strategy);
        }
        try {
            return index.lookup(new KeyProbe(strategy, query));
        } catch (RuntimeException e) {
            // I-1: an unavailable index is an explicit PARTIAL, never an empty result. The
            // exception text is dropped on purpose: it could echo the query (I-11).
            return BlockingOutcome.partial(strategy);
        }
    }
}
