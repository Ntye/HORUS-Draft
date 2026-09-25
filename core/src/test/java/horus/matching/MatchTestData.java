package horus.matching;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityType;
import horus.domain.shared.EntityVersionId;
import horus.normalisation.NormalisationPipeline;
import horus.normalisation.NormalisedName;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Synthetic only (CLAUDE.md §3): every name here is invented.
final class MatchTestData {

    static final NormalisationPipeline PIPELINE = NormalisationPipeline.standard();

    private MatchTestData() {
    }

    static NormalisedName n(String raw) {
        return PIPELINE.normalise(raw);
    }

    static EntityId entityId(long n) {
        return new EntityId(new UUID(0, n));
    }

    static EntityVersionId versionId(long n) {
        return new EntityVersionId(new UUID(1, n));
    }

    static MatchQuery query(String name, EntityType type) {
        return new MatchQuery(n(name), Optional.ofNullable(type), Optional.empty(), Set.of(), Set.of());
    }

    static CandidateView.Builder candidate(String name, EntityType type) {
        return CandidateView.builder(entityId(1), versionId(1), "test-source", type).primaryName(n(name));
    }

    static List<String> ids(MatchExplanation e) {
        return e.features().stream().map(MatchExplanation.FeatureScore::measureId).toList();
    }
}
