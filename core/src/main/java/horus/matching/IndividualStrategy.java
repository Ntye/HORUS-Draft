package horus.matching;

import horus.domain.shared.EntityType;
import java.util.Map;

public final class IndividualStrategy implements EntityTypeStrategy {

    @Override
    public String id() {
        return "INDIVIDUAL";
    }

    @Override
    public boolean handles(EntityType type) {
        return type == EntityType.INDIVIDUAL;
    }

    @Override
    public Map<String, Double> weights(MatchConfig config) {
        return config.individualWeights();
    }

    // Every token of a person's name carries signal; the family name often more, but that is a
    // weight to be tuned on the benchmark, not assumed here.
    @Override
    public TokenWeighting tokenWeighting(MatchConfig config) {
        return TokenWeighting.uniform();
    }

    @Override
    public boolean appliesDistinctivenessCap() {
        return false;
    }
}
