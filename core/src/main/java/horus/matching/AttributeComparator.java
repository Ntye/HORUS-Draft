package horus.matching;

import horus.domain.shared.CanonicalSlot;
import java.util.Set;

// Compares one attribute of the query with the candidate view. Must return an effect for every
// call -- a comparator can demote a candidate but never drop it (I-8) -- and absence on either
// side is neutral (0), never a penalty.
public interface AttributeComparator {

    String id();

    // What the source must be able to provide for this comparator to be meaningful; checked
    // against the source's capabilities at boot (Registry.validateAgainst, I-7).
    Set<CanonicalSlot> requiredSlots();

    AttributeEffect compare(MatchQuery query, CandidateView candidate, MatchConfig config);
}
