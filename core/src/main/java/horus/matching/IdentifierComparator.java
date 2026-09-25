package horus.matching;

import horus.domain.shared.CanonicalSlot;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

// An exact identifier match (passport, registration ...) short-circuits to a strong match
// regardless of name similarity (§16.1). Only 0.74% of feed records carry a structured
// identifier, so this is a bonus path, not the main one. A mismatch never demotes: an identifier
// that differs proves nothing when records carry several, or none (I-2, I-8).
public final class IdentifierComparator implements AttributeComparator {

    @Override
    public String id() {
        return "identifier";
    }

    @Override
    public Set<CanonicalSlot> requiredSlots() {
        return Set.of(CanonicalSlot.IDENTIFIER);
    }

    @Override
    public AttributeEffect compare(MatchQuery query, CandidateView candidate, MatchConfig config) {
        if (query.identifiers().isEmpty()) {
            return AttributeEffect.of("identifier", 0.0, "no identifier supplied; neutral");
        }
        if (candidate.identifiers().isEmpty()) {
            return AttributeEffect.neutralWithGap("identifier", "candidate has no identifier; neutral",
                    "IDENTIFIER: query supplied an identifier but the candidate record has none");
        }
        Set<String> wanted = query.identifiers().stream().map(IdentifierComparator::canonical)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (CandidateIdentifier id : candidate.identifiers()) {
            if (wanted.contains(canonical(id.value()))) {
                // The value itself is left out of the reason on purpose: it is enough to say it matched.
                return new AttributeEffect("identifier", 0.0,
                        "a supplied identifier exactly matches one on the candidate; forced to at least a strong match",
                        true, id.provenance(), Optional.empty());
            }
        }
        return AttributeEffect.of("identifier", 0.0, "no supplied identifier matches; neutral");
    }

    private static String canonical(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
