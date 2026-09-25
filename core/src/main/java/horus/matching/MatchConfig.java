package horus.matching;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// I-7: every threshold, weight and effect is configuration (a versioned ConfigVersion payload in
// Step 9), never code. This record only carries the values; Registry.validateAgainst decides
// whether they are acceptable, at boot. defaults() is the spec §16.2 illustrative starting point
// for benchmark calibration -- NOT an approved operating point (§16.4: Compliance owns that).
// The 55 / 75 thresholds are placeholders chosen so the spec §30 worked examples land in their
// stated bands on synthetic data; they say nothing about recall or precision on real names.
public record MatchConfig(
        Map<String, Double> individualWeights,
        Map<String, Double> organisationWeights,
        Set<String> genericTokens,
        double genericTokenWeight,
        double minDistinctiveWeight,
        double dobExact,
        double dobYearOnly,
        double dobConflict,
        int dobToleranceYears,
        double countryCorroboration,
        double countryConflict,
        double entityTypeMismatch,
        double delisted,
        double whitelistActive,
        int alertThreshold,
        int strongMatchThreshold,
        Set<String> enabledComparators) {

    public MatchConfig {
        if (individualWeights == null || organisationWeights == null || genericTokens == null
                || enabledComparators == null) {
            throw new IllegalArgumentException("weights, genericTokens and enabledComparators must not be null");
        }
        // Sorted copies: no configuration value may depend on hash iteration order (I-4).
        individualWeights = java.util.Collections.unmodifiableMap(new TreeMap<>(individualWeights));
        organisationWeights = java.util.Collections.unmodifiableMap(new TreeMap<>(organisationWeights));
        // Sorted too: Set.copyOf iterates in a per-JVM randomised order, which would make the stored
        // config_version payload's array order differ between runs of the same configuration.
        genericTokens = java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(genericTokens));
        enabledComparators = java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(enabledComparators));
    }

    public static MatchConfig defaults() {
        return new MatchConfig(
                Map.of("token-sort-jaro-winkler", 0.30, "token-set-jaccard", 0.20, "phonetic-agreement", 0.25,
                        "normalised-edit-distance", 0.15, "ngram-dice", 0.10),
                Map.of("token-sort-jaro-winkler", 0.25, "token-set-jaccard", 0.30, "phonetic-agreement", 0.10,
                        "normalised-edit-distance", 0.15, "ngram-dice", 0.20),
                Set.of("trading", "group", "holdings", "company", "co", "limited", "ltd", "incorporated", "inc",
                        "corporation", "corp", "llc", "plc", "gmbh", "sarl", "enterprises", "international",
                        "services", "general", "import", "export", "trade", "commercial", "investment",
                        "investments", "and"),
                0.2,
                0.5,
                10.0, 5.0, -15.0, 1,
                5.0, -5.0,
                -10.0,
                -10.0,
                -20.0,
                55, 75,
                Set.of("dob", "country", "identifier", "entity-type", "status"));
    }

    public MatchConfig withThresholds(int alert, int strong) {
        return new MatchConfig(individualWeights, organisationWeights, genericTokens, genericTokenWeight,
                minDistinctiveWeight, dobExact, dobYearOnly, dobConflict, dobToleranceYears, countryCorroboration,
                countryConflict, entityTypeMismatch, delisted, whitelistActive, alert, strong, enabledComparators);
    }

    public MatchConfig withWeights(Map<String, Double> individual, Map<String, Double> organisation) {
        return new MatchConfig(individual, organisation, genericTokens, genericTokenWeight, minDistinctiveWeight,
                dobExact, dobYearOnly, dobConflict, dobToleranceYears, countryCorroboration, countryConflict,
                entityTypeMismatch, delisted, whitelistActive, alertThreshold, strongMatchThreshold,
                enabledComparators);
    }

    public MatchConfig withEnabledComparators(Set<String> comparators) {
        return new MatchConfig(individualWeights, organisationWeights, genericTokens, genericTokenWeight,
                minDistinctiveWeight, dobExact, dobYearOnly, dobConflict, dobToleranceYears, countryCorroboration,
                countryConflict, entityTypeMismatch, delisted, whitelistActive, alertThreshold,
                strongMatchThreshold, comparators);
    }
}
