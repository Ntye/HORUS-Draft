package horus.matching;

import horus.domain.shared.CanonicalSlot;
import java.util.Set;

// Country / nationality: any overlap corroborates (+5); both present and disjoint conflicts (-5);
// absence on either side is neutral. Countries are 100% populated on the feed but are commonly
// several per entity, so a single shared one is enough to corroborate.
public final class CountryComparator implements AttributeComparator {

    @Override
    public String id() {
        return "country";
    }

    @Override
    public Set<CanonicalSlot> requiredSlots() {
        return Set.of(CanonicalSlot.COUNTRY);
    }

    @Override
    public AttributeEffect compare(MatchQuery query, CandidateView candidate, MatchConfig config) {
        if (query.countries().isEmpty()) {
            return AttributeEffect.of("country", 0.0, "no country supplied; neutral");
        }
        if (candidate.countryCodes().isEmpty()) {
            return AttributeEffect.neutralWithGap("country", "candidate has no country; neutral",
                    "COUNTRY: query supplied a country but the candidate record has none");
        }
        boolean overlap = candidate.countryCodes().stream().anyMatch(query.countries()::contains);
        return overlap
                ? AttributeEffect.of("country", config.countryCorroboration(), "a supplied country matches the candidate")
                : AttributeEffect.of("country", config.countryConflict(),
                        "no supplied country matches the candidate; demoted, not removed");
    }
}
