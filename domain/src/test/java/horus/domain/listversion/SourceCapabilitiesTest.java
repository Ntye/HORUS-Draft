package horus.domain.listversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.ListVersionId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceCapabilitiesTest {

    @Test
    void supportsReportsPresenceRegardlessOfCoverageValue() {
        SourceCapabilities capabilities = new SourceCapabilities(
                new ListVersionId(UUID.randomUUID()),
                "worldcheck",
                Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.0, CanonicalSlot.PRIMARY_NAME, 1.0),
                Instant.parse("2026-01-01T00:00:00Z"));

        // 0% coverage still means "mapped, just rarely populated" -- not unsupported.
        assertThat(capabilities.supports(CanonicalSlot.DATE_OF_BIRTH)).isTrue();
        assertThat(capabilities.supports(CanonicalSlot.ADDRESS)).isFalse();
    }

    @Test
    void supportsWithMinimumCoverageComparesTheValue() {
        SourceCapabilities capabilities = new SourceCapabilities(
                new ListVersionId(UUID.randomUUID()),
                "worldcheck",
                Map.of(CanonicalSlot.IDENTIFIER, 0.0074),
                Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(capabilities.supports(CanonicalSlot.IDENTIFIER, 0.5)).isFalse();
        assertThat(capabilities.supports(CanonicalSlot.IDENTIFIER, 0.001)).isTrue();
    }

    @Test
    void rejectsCoverageOutsideZeroToOne() {
        assertThatThrownBy(() -> new SourceCapabilities(
                new ListVersionId(UUID.randomUUID()),
                "worldcheck",
                Map.of(CanonicalSlot.PRIMARY_NAME, 1.5),
                Instant.parse("2026-01-01T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
