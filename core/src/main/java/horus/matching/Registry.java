package horus.matching;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// The measures, strategies and comparators the engine knows, and the boot-time check that a
// configuration only refers to things that exist and that the source can support (I-7).
public final class Registry {

    private final List<SimilarityMeasure> measures;
    private final List<EntityTypeStrategy> strategies;
    private final List<AttributeComparator> comparators;

    public Registry(
            List<SimilarityMeasure> measures,
            List<EntityTypeStrategy> strategies,
            List<AttributeComparator> comparators) {
        if (measures == null || measures.isEmpty() || strategies == null || strategies.isEmpty()
                || comparators == null) {
            throw new IllegalArgumentException("measures and strategies must not be empty; comparators not null");
        }
        // Measures sorted by id: the order features are summed in must never depend on how the
        // registry was assembled (I-4).
        this.measures = measures.stream().sorted(Comparator.comparing(SimilarityMeasure::id)).toList();
        this.strategies = List.copyOf(strategies);
        this.comparators = List.copyOf(comparators);
    }

    public static Registry standard() {
        return new Registry(
                List.of(new TokenSortJaroWinkler(), new TokenSetJaccard(), new PhoneticAgreement(),
                        new NormalisedEditDistance(), new NGramSimilarity()),
                List.of(new IndividualStrategy(), new OrganisationStrategy()),
                List.of(new DobComparator(), new CountryComparator(), new IdentifierComparator(),
                        new EntityTypeComparator(), new StatusComparator()));
    }

    public List<SimilarityMeasure> measures() {
        return measures;
    }

    public List<EntityTypeStrategy> strategies() {
        return strategies;
    }

    public List<AttributeComparator> comparators() {
        return comparators;
    }

    public EntityTypeStrategy strategyFor(EntityType type) {
        return strategies.stream().filter(s -> s.handles(type)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no strategy handles entity type " + type));
    }

    // I-7: fails the BOOT, not a screening. Every problem is collected and reported together.
    public void validateAgainst(MatchConfig config, Set<CanonicalSlot> capabilities) {
        List<String> problems = new ArrayList<>();
        Set<String> knownMeasures = new TreeSet<>();
        measures.forEach(m -> knownMeasures.add(m.id()));

        for (EntityTypeStrategy strategy : strategies) {
            checkWeights(strategy.id(), strategy.weights(config), knownMeasures, problems);
        }
        if (!(config.genericTokenWeight() > 0.0 && config.genericTokenWeight() <= 1.0)) {
            problems.add("genericTokenWeight must be in (0, 1]");
        }
        if (config.minDistinctiveWeight() < 0.0) {
            problems.add("minDistinctiveWeight must not be negative");
        }
        if (config.dobToleranceYears() < 0) {
            problems.add("dobToleranceYears must not be negative");
        }
        if (!(config.alertThreshold() > 0 && config.alertThreshold() < config.strongMatchThreshold()
                && config.strongMatchThreshold() <= 100)) {
            problems.add("thresholds must satisfy 0 < alertThreshold < strongMatchThreshold <= 100 (were "
                    + config.alertThreshold() + ", " + config.strongMatchThreshold() + ")");
        }

        Map<String, AttributeComparator> byId = new java.util.HashMap<>();
        comparators.forEach(c -> byId.put(c.id(), c));
        for (String enabled : new TreeSet<>(config.enabledComparators())) {
            AttributeComparator comparator = byId.get(enabled);
            if (comparator == null) {
                problems.add("unknown comparator '" + enabled + "'");
                continue;
            }
            for (CanonicalSlot slot : comparator.requiredSlots()) {
                if (!capabilities.contains(slot)) {
                    problems.add("comparator '" + enabled + "' is enabled but the source has no " + slot
                            + " capability");
                }
            }
        }

        if (!problems.isEmpty()) {
            throw new InvalidMatchConfigException("invalid matching configuration: " + String.join("; ", problems));
        }
    }

    private static void checkWeights(
            String strategyId, Map<String, Double> weights, Set<String> known, List<String> problems) {
        double sum = 0.0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (!known.contains(entry.getKey())) {
                problems.add(strategyId + " weights refer to unknown measure '" + entry.getKey() + "'");
            }
            if (entry.getValue() == null || entry.getValue() < 0.0 || entry.getValue().isNaN()) {
                problems.add(strategyId + " weight for '" + entry.getKey() + "' must not be negative");
            } else {
                sum += entry.getValue();
            }
        }
        if (!(sum > 0.0)) {
            problems.add(strategyId + " weights must sum to more than zero");
        }
    }
}
