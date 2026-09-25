package horus.ingestion.spi;

import static org.assertj.core.api.Assertions.assertThat;

import horus.domain.shared.CanonicalSlot;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CapabilityExpectationTest {

    @Test
    void withinToleranceProducesNoDeviation() {
        CapabilityExpectation expectation = new CapabilityExpectation(
                "worldcheck",
                Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.27),
                Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.05));

        assertThat(expectation.compare(Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.28))).isEmpty();
    }

    @Test
    void outsideToleranceProducesADeviation() {
        // §6: DOB coverage dropping from 27% toward 0% must trip immediately.
        CapabilityExpectation expectation = new CapabilityExpectation(
                "worldcheck",
                Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.27),
                Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.05));

        var deviations = expectation.compare(Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.0));

        assertThat(deviations).hasSize(1);
        assertThat(deviations.get(0).slot()).isEqualTo(CanonicalSlot.DATE_OF_BIRTH);
    }

    @Test
    void aSlotMissingFromActualIsTreatedAsZeroCoverage() {
        CapabilityExpectation expectation = new CapabilityExpectation(
                "worldcheck",
                Map.of(CanonicalSlot.IDENTIFIER, 0.0074),
                Map.of(CanonicalSlot.IDENTIFIER, 0.005));

        assertThat(expectation.compare(Map.of())).hasSize(1);
    }
}
