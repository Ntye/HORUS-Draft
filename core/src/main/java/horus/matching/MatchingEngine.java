package horus.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

// The pure scorer (I-9): a query and a CandidateView in, a MatchExplanation out. No I/O, no
// clock, no randomness (I-4). It scores what it is given and always returns an explanation --
// there is no code path here that removes a candidate (I-8): attributes, status, whitelist and
// distinctiveness only ever demote, with a stated reason.
//
// MatchingEngine.score(query, CandidateView, cfg, WhitelistView) per CLAUDE.md §5.
public final class MatchingEngine {

    private final Registry registry;

    public MatchingEngine(Registry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        this.registry = registry;
    }

    public MatchExplanation score(
            MatchQuery query, CandidateView candidate, MatchConfig config, WhitelistView whitelist) {
        if (query == null || candidate == null || config == null || whitelist == null) {
            throw new IllegalArgumentException("query, candidate, config and whitelist must not be null");
        }

        List<EntityTypeStrategy> strategies = query.entityType()
                .map(t -> List.of(registry.strategyFor(t)))
                .orElse(registry.strategies());
        Best best = bestNameScore(query, candidate, config, strategies);

        List<String> notes = new ArrayList<>();
        // I-2: with no entity type given, both strategies are tried and the higher kept.
        String strategyLabel = query.entityType().isPresent() ? best.strategy.id() : "BOTH_MAX:" + best.strategy.id();

        Optional<Double> cap = Optional.empty();
        if (best.strategy.appliesDistinctivenessCap()) {
            TokenWeighting weighting = best.strategy.tokenWeighting(config);
            double distinctiveWeight = 0.0;
            for (String token : query.name().tokens()) {
                distinctiveWeight += weighting.weight(token);
            }
            if (distinctiveWeight < config.minDistinctiveWeight()) {
                // §30 A10: a query with no distinctive token must not match broadly. Demoted
                // below the alert threshold with a stated reason -- still returned (I-8).
                double capValue = config.alertThreshold() - 1;
                cap = Optional.of(capValue);
                notes.add("query has no distinctive token (weight " + distinctiveWeight + " below minimum "
                        + config.minDistinctiveWeight() + "); score capped at " + capValue);
            }
        }

        List<AttributeEffect> effects = new ArrayList<>();
        for (AttributeComparator comparator : registry.comparators()) {
            if (config.enabledComparators().contains(comparator.id())) {
                effects.add(comparator.compare(query, candidate, config));
            }
        }
        WhitelistView.Status whitelistStatus = whitelist.statusFor(candidate.entityId(), candidate.entityVersionId());
        if (whitelistStatus == WhitelistView.Status.ACTIVE) {
            effects.add(AttributeEffect.of("whitelist", config.whitelistActive(),
                    "an active whitelist entry approved against this version; demoted and flagged, never suppressed"));
        } else if (whitelistStatus == WhitelistView.Status.STALE) {
            effects.add(AttributeEffect.of("whitelist", 0.0,
                    "a whitelist entry exists but is inactive: the entity changed since it was approved; not applied"));
        }

        Optional<Double> floor = effects.stream().anyMatch(AttributeEffect::forcesStrongMatch)
                ? Optional.of((double) config.strongMatchThreshold())
                : Optional.empty();

        double nameScore = MatchExplanation.nameScore(best.features);
        int composite = MatchExplanation.composite(nameScore, effects, cap, floor);

        TreeSet<String> gaps = new TreeSet<>();
        effects.forEach(e -> e.capabilityGap().ifPresent(gaps::add));

        return new MatchExplanation(
                MatchExplanation.SCHEMA_VERSION,
                strategyLabel,
                candidate.entityId(),
                candidate.entityVersionId(),
                candidate.sourceId(),
                best.name.name().raw(),
                best.name.alias(),
                best.features,
                nameScore,
                effects,
                MatchExplanation.delta(effects),
                cap,
                floor,
                composite,
                config.alertThreshold(),
                config.strongMatchThreshold(),
                DecisionBands.classify(composite, config),
                List.of(candidate.sourceId()),
                new ArrayList<>(gaps),
                notes);
    }

    private record Best(EntityTypeStrategy strategy, CandidateName name, List<MatchExplanation.FeatureScore> features,
            double nameScore) {
    }

    // The highest-scoring (strategy, name) pair. Ties keep the earlier one -- strategy order, then
    // name order (primary first) -- so the choice is deterministic (I-4).
    private Best bestNameScore(
            MatchQuery query, CandidateView candidate, MatchConfig config, List<EntityTypeStrategy> strategies) {
        Best best = null;
        for (EntityTypeStrategy strategy : strategies) {
            Map<String, Double> weights = strategy.weights(config);
            TokenWeighting weighting = strategy.tokenWeighting(config);
            for (CandidateName candidateName : candidate.names()) {
                List<MatchExplanation.FeatureScore> features = new ArrayList<>();
                for (SimilarityMeasure measure : registry.measures()) {
                    double weight = weights.getOrDefault(measure.id(), 0.0);
                    features.add(new MatchExplanation.FeatureScore(
                            measure.id(), weight, measure.score(query.name(), candidateName.name(), weighting)));
                }
                double nameScore = MatchExplanation.nameScore(features);
                if (best == null || nameScore > best.nameScore) {
                    best = new Best(strategy, candidateName, features, nameScore);
                }
            }
        }
        return best;
    }
}
