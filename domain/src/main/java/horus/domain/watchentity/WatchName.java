package horus.domain.watchentity;

import horus.domain.shared.EntityVersionId;
import horus.domain.shared.Provenance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record WatchName(
        UUID nameId,
        EntityVersionId entityVersionId,
        NameType nameType,
        String rawName,
        String normalisedName,
        List<String> nameTokens,
        List<String> phoneticCodes,
        List<String> trigrams,
        String script,
        String language,
        Optional<Provenance> provenance) {

    public WatchName {
        if (nameId == null) {
            throw new IllegalArgumentException("nameId must not be null");
        }
        if (entityVersionId == null) {
            throw new IllegalArgumentException("entityVersionId must not be null");
        }
        if (nameType == null) {
            throw new IllegalArgumentException("nameType must not be null");
        }
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("rawName must not be blank");
        }
        if (normalisedName == null) {
            throw new IllegalArgumentException("normalisedName must not be null");
        }
        if (nameTokens == null || phoneticCodes == null || trigrams == null) {
            throw new IllegalArgumentException("nameTokens, phoneticCodes and trigrams must not be null");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null (use Optional.empty())");
        }
        nameTokens = List.copyOf(nameTokens);
        phoneticCodes = List.copyOf(phoneticCodes);
        trigrams = List.copyOf(trigrams);
    }
}
