package horus.domain.watchentity;

import horus.domain.shared.EntityVersionId;
import java.util.Optional;
import java.util.UUID;

public record WatchCountry(
        UUID countryId,
        EntityVersionId entityVersionId,
        String rawCountry,
        Optional<String> isoCode) {

    public WatchCountry {
        if (countryId == null) {
            throw new IllegalArgumentException("countryId must not be null");
        }
        if (entityVersionId == null) {
            throw new IllegalArgumentException("entityVersionId must not be null");
        }
        if (rawCountry == null || rawCountry.isBlank()) {
            throw new IllegalArgumentException("rawCountry must not be blank");
        }
        if (isoCode == null) {
            throw new IllegalArgumentException("isoCode must not be null (use Optional.empty())");
        }
    }
}
