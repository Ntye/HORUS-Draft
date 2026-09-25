package horus.matching;

import horus.domain.shared.CanonicalSlot;
import java.util.Set;

// DOB: exact +10, year only +5, conflict beyond tolerance -15, absence 0 (§16.2). Absence is
// NEUTRAL -- never penalised -- because DOB is populated on only 27% of records (CLAUDE.md §6).
// Several candidate dates: the best-scoring one counts (I-2: ambiguity resolves toward keeping).
public final class DobComparator implements AttributeComparator {

    @Override
    public String id() {
        return "dob";
    }

    @Override
    public Set<CanonicalSlot> requiredSlots() {
        return Set.of(CanonicalSlot.DATE_OF_BIRTH);
    }

    @Override
    public AttributeEffect compare(MatchQuery query, CandidateView candidate, MatchConfig config) {
        if (query.dob().isEmpty()) {
            return AttributeEffect.of("dob", 0.0, "no date of birth supplied; neutral");
        }
        PartialDate wanted = query.dob().get();
        if (candidate.dobs().isEmpty()) {
            return AttributeEffect.neutralWithGap("dob", "candidate has no date of birth; neutral",
                    "DATE_OF_BIRTH: query supplied a date of birth but the candidate record has none");
        }

        AttributeEffect best = null;
        for (CandidateDob dob : candidate.dobs()) {
            AttributeEffect effect = compareOne(wanted, dob, config);
            if (best == null || effect.delta() > best.delta()) {
                best = effect;
            }
        }
        return best;
    }

    private static AttributeEffect compareOne(PartialDate wanted, CandidateDob dob, MatchConfig config) {
        int difference = Math.abs(wanted.year() - dob.effectiveYear());
        // An age-derived year is approximate by nature: allow a year of slack before calling it a match.
        int matchSlack = dob.derivedFromAge() ? 1 : 0;

        if (difference <= matchSlack) {
            boolean fullOnBothSides = !dob.derivedFromAge() && wanted.month().isPresent() && wanted.day().isPresent()
                    && dob.month().isPresent() && dob.day().isPresent();
            if (fullOnBothSides && difference == 0 && wanted.month().getAsInt() == dob.month().getAsInt()
                    && wanted.day().getAsInt() == dob.day().getAsInt()) {
                return AttributeEffect.of("dob", config.dobExact(), "date of birth matches exactly");
            }
            return AttributeEffect.of("dob", config.dobYearOnly(), "year of birth matches");
        }
        if (difference <= config.dobToleranceYears()) {
            return AttributeEffect.of("dob", 0.0, "year of birth differs by " + difference
                    + ", within the tolerance of " + config.dobToleranceYears() + "; neutral");
        }
        return AttributeEffect.of("dob", config.dobConflict(), "year of birth differs by " + difference
                + ", beyond the tolerance of " + config.dobToleranceYears() + "; demoted, not removed");
    }
}
