package horus.ingestion.spi;

import java.time.LocalDate;
import java.util.Optional;

public record CanonicalDob(
        Optional<Integer> year,
        Optional<Integer> month,
        Optional<Integer> day,
        Optional<Integer> age,
        Optional<LocalDate> asOfDate,
        Optional<LocalDate> deceased,
        String sourcePath) {

    public CanonicalDob {
        if (year == null || month == null || day == null || age == null
                || asOfDate == null || deceased == null) {
            throw new IllegalArgumentException("optional fields must not be null (use Optional.empty())");
        }
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath must not be blank");
        }
        if (year.isEmpty() && age.isEmpty()) {
            throw new IllegalArgumentException("at least one of year or age must be present");
        }
    }
}
