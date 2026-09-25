package horus.matching;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import java.util.Set;

// A person matched against a non-person (or the reverse) is demoted -10, never dropped (I-8).
// Only "individual-ness" is compared: an ORGANISATION query against a VESSEL is not a mismatch,
// because the feed's E-type covers organisations, vessels, aircraft and more (CLAUDE.md §6).
public final class EntityTypeComparator implements AttributeComparator {

    @Override
    public String id() {
        return "entity-type";
    }

    @Override
    public Set<CanonicalSlot> requiredSlots() {
        return Set.of(CanonicalSlot.ENTITY_TYPE);
    }

    @Override
    public AttributeEffect compare(MatchQuery query, CandidateView candidate, MatchConfig config) {
        if (query.entityType().isEmpty()) {
            return AttributeEffect.of("entity-type", 0.0, "no entity type supplied; neutral");
        }
        boolean queryIsPerson = query.entityType().get() == EntityType.INDIVIDUAL;
        boolean candidateIsPerson = candidate.entityType() == EntityType.INDIVIDUAL;
        return queryIsPerson == candidateIsPerson
                ? AttributeEffect.of("entity-type", 0.0, "entity types are compatible")
                : AttributeEffect.of("entity-type", config.entityTypeMismatch(),
                        "individual versus non-individual; demoted, not removed");
    }
}
