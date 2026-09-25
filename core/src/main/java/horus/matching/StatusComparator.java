package horus.matching;

import horus.domain.shared.CanonicalSlot;
import java.util.Set;

// I-8 / I-2: a de-listed entity is demoted and explained, never removed. Status is not a
// structured field on the feed -- it is derived from narrative -- and 22.77% of sanctioned records
// are removed from every list yet remain in the feed (CLAUDE.md §6), so "may still be designated"
// is the honest wording whenever we are not sure.
public final class StatusComparator implements AttributeComparator {

    @Override
    public String id() {
        return "status";
    }

    @Override
    public Set<CanonicalSlot> requiredSlots() {
        return Set.of(CanonicalSlot.STATUS);
    }

    @Override
    public AttributeEffect compare(MatchQuery query, CandidateView candidate, MatchConfig config) {
        if (!candidate.delisted()) {
            return AttributeEffect.of("status", 0.0, "candidate is currently listed; neutral");
        }
        return AttributeEffect.of("status", config.delisted(),
                "de-listed in the latest list version but may still be designated elsewhere; demoted, not removed");
    }
}
