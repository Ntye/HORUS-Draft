package horus.domain.watchentity;

import horus.domain.shared.EntityVersionId;
import horus.domain.shared.Provenance;
import java.util.Optional;
import java.util.UUID;

// Passport is the only structured identifier on the feed (0.74%) -- see CLAUDE.md §6.
public record WatchIdentifier(
        UUID identifierId,
        EntityVersionId entityVersionId,
        IdType idType,
        String idValue,
        String normalisedIdValue,
        Optional<String> issuingCountry,
        Optional<Provenance> provenance) {

    public WatchIdentifier {
        if (identifierId == null) {
            throw new IllegalArgumentException("identifierId must not be null");
        }
        if (entityVersionId == null) {
            throw new IllegalArgumentException("entityVersionId must not be null");
        }
        if (idType == null) {
            throw new IllegalArgumentException("idType must not be null");
        }
        if (idValue == null || idValue.isBlank()) {
            throw new IllegalArgumentException("idValue must not be blank");
        }
        if (normalisedIdValue == null || normalisedIdValue.isBlank()) {
            throw new IllegalArgumentException("normalisedIdValue must not be blank");
        }
        if (issuingCountry == null) {
            throw new IllegalArgumentException("issuingCountry must not be null (use Optional.empty())");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null (use Optional.empty())");
        }
    }
}
