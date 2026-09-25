package horus.domain.listversion;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.ListVersionId;
import java.time.Instant;
import java.util.Map;

// Measured per load, not declared -- 0% coverage (mapped, rarely populated) != unmapped != unsupported.
public record SourceCapabilities(
        ListVersionId listVersionId,
        String sourceId,
        Map<CanonicalSlot, Double> coverage,
        Instant derivedAt) {

    public SourceCapabilities {
        if (listVersionId == null) {
            throw new IllegalArgumentException("listVersionId must not be null");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (coverage == null) {
            throw new IllegalArgumentException("coverage must not be null");
        }
        for (Double value : coverage.values()) {
            if (value == null || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException("coverage values must be between 0 and 1");
            }
        }
        if (derivedAt == null) {
            throw new IllegalArgumentException("derivedAt must not be null");
        }
        coverage = Map.copyOf(coverage);
    }

    public boolean supports(CanonicalSlot slot) {
        return coverage.containsKey(slot);
    }

    public boolean supports(CanonicalSlot slot, double minCoverage) {
        Double value = coverage.get(slot);
        return value != null && value >= minCoverage;
    }
}
