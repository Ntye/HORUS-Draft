package horus.matching;

import java.util.OptionalInt;

// A candidate's date of birth as the watchlist holds it: a full or partial date, or only an age
// as of some year (12.4% of records, CLAUDE.md §6).
public record CandidateDob(
        OptionalInt year, OptionalInt month, OptionalInt day, OptionalInt age, OptionalInt ageAsOfYear) {

    public CandidateDob {
        if (year == null || month == null || day == null || age == null || ageAsOfYear == null) {
            throw new IllegalArgumentException("no component may be null (use OptionalInt.empty())");
        }
        if (year.isEmpty() && (age.isEmpty() || ageAsOfYear.isEmpty())) {
            throw new IllegalArgumentException("a date of birth needs a year, or an age with its as-of year");
        }
    }

    // Age-derived years are approximate, so comparators allow them a year of slack.
    public boolean derivedFromAge() {
        return year.isEmpty();
    }

    public int effectiveYear() {
        return year.isPresent() ? year.getAsInt() : ageAsOfYear.getAsInt() - age.getAsInt();
    }
}
