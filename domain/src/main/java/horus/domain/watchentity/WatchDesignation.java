package horus.domain.watchentity;

import horus.domain.shared.EntityVersionId;
import horus.domain.shared.Provenance;
import java.util.Optional;
import java.util.UUID;

// Narrative-derived: there is no status/de-listing flag in the structured record (§6).
public record WatchDesignation(
        UUID designationId,
        EntityVersionId entityVersionId,
        String listSource,
        ActionType action,
        Optional<Integer> year,
        String rawVerb,
        Optional<Provenance> provenance) {

    public WatchDesignation {
        if (designationId == null) {
            throw new IllegalArgumentException("designationId must not be null");
        }
        if (entityVersionId == null) {
            throw new IllegalArgumentException("entityVersionId must not be null");
        }
        if (listSource == null || listSource.isBlank()) {
            throw new IllegalArgumentException("listSource must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (year == null) {
            throw new IllegalArgumentException("year must not be null (use Optional.empty())");
        }
        if (rawVerb == null || rawVerb.isBlank()) {
            throw new IllegalArgumentException("rawVerb must not be blank");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null (use Optional.empty())");
        }
    }

    public boolean isRemoval() {
        return action == ActionType.REMOVAL;
    }
}
