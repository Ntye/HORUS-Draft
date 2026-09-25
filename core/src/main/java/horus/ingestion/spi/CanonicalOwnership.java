package horus.ingestion.spi;

import horus.domain.shared.Provenance;

public record CanonicalOwnership(
        String owner, String ownerType, Double percent, boolean percentStated, Provenance provenance) {

    public CanonicalOwnership {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        if (percent != null && (percent < 0.0 || percent > 100.0)) {
            throw new IllegalArgumentException("percent must be between 0 and 100");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null");
        }
    }
}
