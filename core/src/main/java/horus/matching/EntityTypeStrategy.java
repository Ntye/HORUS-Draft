package horus.matching;

import horus.domain.shared.EntityType;
import java.util.Map;

// Person and organisation names are different problems (§15.4): applying person logic to
// organisations floods analysts on "Africa Trading Company" or misses genuine matches on
// distinctive tokens. A strategy fixes the feature weights and the token weighting.
public interface EntityTypeStrategy {

    String id();

    boolean handles(EntityType type);

    Map<String, Double> weights(MatchConfig config);

    TokenWeighting tokenWeighting(MatchConfig config);

    // Organisations only: a query with no distinctive token is capped below the alert threshold.
    boolean appliesDistinctivenessCap();
}
