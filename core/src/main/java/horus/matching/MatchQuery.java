package horus.matching;

import horus.domain.shared.EntityType;
import horus.normalisation.NormalisedName;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

// What is being screened. The name arrives already normalised by the shared pipeline (I-10).
// Countries are ISO-style codes and identifiers are raw values; absence is an empty Optional/Set,
// and absence is always neutral in scoring.
public record MatchQuery(
        NormalisedName name,
        Optional<EntityType> entityType,
        Optional<PartialDate> dob,
        Set<String> countries,
        Set<String> identifiers) {

    public MatchQuery {
        if (name == null || entityType == null || dob == null || countries == null || identifiers == null) {
            throw new IllegalArgumentException("no MatchQuery component may be null");
        }
        countries = java.util.Collections.unmodifiableSortedSet(new TreeSet<>(countries));
        identifiers = java.util.Collections.unmodifiableSortedSet(new TreeSet<>(identifiers));
    }
}
