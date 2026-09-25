package horus.blocking;

import java.util.List;
import java.util.TreeSet;

// The only shape a strategy lookup may return. Nothing in the codebase returns a bare set of
// candidate ids: that shape cannot say "I could not finish searching", which is fail-open (I-1).
public record BlockingOutcome(Strategy strategy, Completeness completeness, List<CandidateKey> candidates) {

    public BlockingOutcome {
        if (strategy == null || completeness == null || candidates == null) {
            throw new IllegalArgumentException("strategy, completeness and candidates must not be null");
        }
        candidates = List.copyOf(new TreeSet<>(candidates));
    }

    public static BlockingOutcome partial(Strategy strategy) {
        return new BlockingOutcome(strategy, Completeness.PARTIAL, List.of());
    }
}
