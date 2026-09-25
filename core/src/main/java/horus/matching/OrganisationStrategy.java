package horus.matching;

import horus.domain.shared.EntityType;
import java.util.Map;

// Handles every non-individual type: the feed's "E" records cover organisations, vessels,
// aircraft, websites and more, all named like organisations (CLAUDE.md §6).
public final class OrganisationStrategy implements EntityTypeStrategy {

    @Override
    public String id() {
        return "ORGANISATION";
    }

    @Override
    public boolean handles(EntityType type) {
        return type != EntityType.INDIVIDUAL;
    }

    @Override
    public Map<String, Double> weights(MatchConfig config) {
        return config.organisationWeights();
    }

    @Override
    public TokenWeighting tokenWeighting(MatchConfig config) {
        return new GenericTokenWeighting(config.genericTokens(), config.genericTokenWeight());
    }

    @Override
    public boolean appliesDistinctivenessCap() {
        return true;
    }
}
