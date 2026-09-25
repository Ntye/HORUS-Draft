package horus.ingestion.spi;

import horus.domain.shared.CanonicalSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Baseline FROM MEASUREMENT (horus-ingestion-spi.drawio), never a guess -- see CLAUDE.md §6 for
// the numbers WorldCheckAdapter's instance of this is built from.
public record CapabilityExpectation(
        String sourceId, Map<CanonicalSlot, Double> expected, Map<CanonicalSlot, Double> tolerance) {

    public CapabilityExpectation {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (expected == null || tolerance == null) {
            throw new IllegalArgumentException("expected and tolerance must not be null");
        }
        expected = Map.copyOf(expected);
        tolerance = Map.copyOf(tolerance);
    }

    public record Deviation(CanonicalSlot slot, double expected, double actual, double tolerance) {}

    public List<Deviation> compare(Map<CanonicalSlot, Double> actual) {
        List<Deviation> deviations = new ArrayList<>();
        for (Map.Entry<CanonicalSlot, Double> e : expected.entrySet()) {
            CanonicalSlot slot = e.getKey();
            double expectedValue = e.getValue();
            double actualValue = actual.getOrDefault(slot, 0.0);
            double allowedTolerance = tolerance.getOrDefault(slot, 0.0);
            if (Math.abs(actualValue - expectedValue) > allowedTolerance) {
                deviations.add(new Deviation(slot, expectedValue, actualValue, allowedTolerance));
            }
        }
        return List.copyOf(deviations);
    }
}
