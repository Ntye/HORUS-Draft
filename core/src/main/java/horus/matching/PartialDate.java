package horus.matching;

import java.util.OptionalInt;

// A date of birth as far as the query knows it: always a year, optionally month and day.
public record PartialDate(int year, OptionalInt month, OptionalInt day) {

    public PartialDate {
        if (month == null || day == null) {
            throw new IllegalArgumentException("month and day must not be null (use OptionalInt.empty())");
        }
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("year out of range");
        }
        if (month.isPresent() && (month.getAsInt() < 1 || month.getAsInt() > 12)) {
            throw new IllegalArgumentException("month out of range");
        }
        if (day.isPresent() && (month.isEmpty() || day.getAsInt() < 1 || day.getAsInt() > 31)) {
            throw new IllegalArgumentException("day out of range or given without a month");
        }
    }
}
