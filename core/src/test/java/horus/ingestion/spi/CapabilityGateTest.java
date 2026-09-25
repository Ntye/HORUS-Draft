package horus.ingestion.spi;

import static org.assertj.core.api.Assertions.assertThat;

import horus.domain.shared.CanonicalSlot;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CapabilityGateTest {

    @Test
    void passesWhenDerivedCoverageIsWithinTolerance() {
        CapabilityExpectation expectation = new CapabilityExpectation(
                "worldcheck",
                Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.27),
                Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.05));

        CapabilityGate gate = CapabilityGate.evaluate(expectation, Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.28));

        assertThat(gate.passes()).isTrue();
        assertThat(gate.deviations()).isEmpty();
    }

    // I-1: a capability collapse (e.g. a mapping regression wiping DOB coverage) must halt the
    // load, not proceed on silently degraded data.
    @Test
    void failsWhenDerivedCoverageCollapsesOutsideTolerance() {
        CapabilityExpectation expectation = new CapabilityExpectation(
                "worldcheck",
                Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.27),
                Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.05));

        CapabilityGate gate = CapabilityGate.evaluate(expectation, Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.0));

        assertThat(gate.passes()).isFalse();
        assertThat(gate.deviations()).hasSize(1);
        assertThat(gate.deviations().get(0).slot()).isEqualTo(CanonicalSlot.DATE_OF_BIRTH);
    }
}
