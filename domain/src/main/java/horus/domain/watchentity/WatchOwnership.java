package horus.domain.watchentity;

import horus.domain.shared.EntityVersionId;
import horus.domain.shared.Provenance;
import java.util.Optional;
import java.util.UUID;

// 50 Percent Rule input; percentages are narrative-derived (89.6% carry an explicit percent) -- §6.
public record WatchOwnership(
        UUID stakeId,
        EntityVersionId entityVersionId,
        String owner,
        Optional<String> ownerType,
        Optional<Double> percent,
        boolean percentStated,
        Optional<Provenance> provenance) {

    public WatchOwnership {
        if (stakeId == null) {
            throw new IllegalArgumentException("stakeId must not be null");
        }
        if (entityVersionId == null) {
            throw new IllegalArgumentException("entityVersionId must not be null");
        }
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        if (ownerType == null) {
            throw new IllegalArgumentException("ownerType must not be null (use Optional.empty())");
        }
        if (percent == null) {
            throw new IllegalArgumentException("percent must not be null (use Optional.empty())");
        }
        if (percent.isPresent() && (percent.get() < 0.0 || percent.get() > 100.0)) {
            throw new IllegalArgumentException("percent must be between 0 and 100");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null (use Optional.empty())");
        }
    }
}
