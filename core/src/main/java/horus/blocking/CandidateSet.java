package horus.blocking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

// The union of every strategy's candidates, with which strategies surfaced each one and the
// worst completeness seen. Consumers must treat PARTIAL as ERROR (I-1).
public final class CandidateSet {

    private final Completeness completeness;
    private final List<BlockingOutcome> outcomes;
    private final TreeMap<CandidateKey, Set<Strategy>> byCandidate = new TreeMap<>();

    public CandidateSet(List<BlockingOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            throw new IllegalArgumentException("outcomes must not be empty");
        }
        this.outcomes = List.copyOf(outcomes);
        Completeness worst = Completeness.COMPLETE;
        for (BlockingOutcome outcome : this.outcomes) {
            worst = Completeness.worst(worst, outcome.completeness());
            for (CandidateKey key : outcome.candidates()) {
                byCandidate.computeIfAbsent(key, k -> EnumSet.noneOf(Strategy.class)).add(outcome.strategy());
            }
        }
        this.completeness = worst;
    }

    public Completeness completeness() {
        return completeness;
    }

    public List<BlockingOutcome> outcomes() {
        return outcomes;
    }

    public List<CandidateKey> candidates() {
        return Collections.unmodifiableList(new ArrayList<>(byCandidate.keySet()));
    }

    public Set<Strategy> strategiesFor(CandidateKey key) {
        Set<Strategy> strategies = byCandidate.get(key);
        return strategies == null ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(strategies));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CandidateSet that && outcomes.equals(that.outcomes);
    }

    @Override
    public int hashCode() {
        return outcomes.hashCode();
    }
}
