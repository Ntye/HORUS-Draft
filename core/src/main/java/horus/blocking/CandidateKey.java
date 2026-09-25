package horus.blocking;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;

// A candidate is identified, never described: names stay out of blocking outputs (I-11), and the
// application layer assembles the CandidateView from these ids.
public record CandidateKey(EntityId entityId, EntityVersionId entityVersionId) implements Comparable<CandidateKey> {

    public CandidateKey {
        if (entityId == null || entityVersionId == null) {
            throw new IllegalArgumentException("entityId and entityVersionId must not be null");
        }
    }

    // I-4: a total order so no output depends on hash iteration order.
    @Override
    public int compareTo(CandidateKey other) {
        int byVersion = entityVersionId.value().compareTo(other.entityVersionId.value());
        return byVersion != 0 ? byVersion : entityId.value().compareTo(other.entityId.value());
    }
}
