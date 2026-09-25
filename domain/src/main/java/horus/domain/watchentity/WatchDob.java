package horus.domain.watchentity;

import horus.domain.shared.EntityVersionId;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

// I-6: 1:1 with the entity version, not 1:N -- see CLAUDE.md §6 (27% populated).
public record WatchDob(
        UUID dobId,
        EntityVersionId entityVersionId,
        Optional<Integer> year,
        Optional<Integer> month,
        Optional<Integer> day,
        Optional<Integer> age,
        Optional<LocalDate> asOfDate,
        Optional<LocalDate> deceased,
        DobPrecision precision) {

    public WatchDob {
        if (dobId == null) {
            throw new IllegalArgumentException("dobId must not be null");
        }
        if (entityVersionId == null) {
            throw new IllegalArgumentException("entityVersionId must not be null");
        }
        if (year == null || month == null || day == null || age == null
                || asOfDate == null || deceased == null) {
            throw new IllegalArgumentException("optional fields must not be null (use Optional.empty())");
        }
        if (precision == null) {
            throw new IllegalArgumentException("precision must not be null");
        }
        if (year.isEmpty() && age.isEmpty()) {
            throw new IllegalArgumentException("at least one of year or age must be present");
        }
    }
}
