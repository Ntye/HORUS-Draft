package horus.ingestion.spi;

import horus.domain.shared.CanonicalSlot;
import java.util.List;
import java.util.Map;

// I-1: a load whose measured capabilities fall outside the expected tolerance must halt, not
// screen against silently degraded data. Named per CLAUDE.md §12's ingestion interactor list.
public record CapabilityGate(boolean passes, List<CapabilityExpectation.Deviation> deviations) {

    public CapabilityGate {
        if (deviations == null) {
            throw new IllegalArgumentException("deviations must not be null");
        }
        deviations = List.copyOf(deviations);
    }

    public static CapabilityGate evaluate(
            CapabilityExpectation expectation, Map<CanonicalSlot, Double> derived) {
        if (expectation == null) {
            throw new IllegalArgumentException("expectation must not be null");
        }
        if (derived == null) {
            throw new IllegalArgumentException("derived must not be null");
        }
        List<CapabilityExpectation.Deviation> deviations = expectation.compare(derived);
        return new CapabilityGate(deviations.isEmpty(), deviations);
    }
}
