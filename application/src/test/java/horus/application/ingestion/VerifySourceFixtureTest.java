package horus.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import horus.domain.watchentity.NameType;
import horus.ingestion.format.RawRecord;
import horus.ingestion.spi.CanonicalRecord;
import horus.ingestion.spi.CanonicalRecordBuilder;
import horus.ingestion.spi.CapabilityExpectation;
import horus.ingestion.spi.SourceFixture;
import horus.ingestion.spi.WatchlistSourceAdapter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerifySourceFixtureTest {

    private static final WatchlistSourceAdapter STUB_ADAPTER = new WatchlistSourceAdapter() {
        @Override
        public String sourceId() {
            return "stub";
        }

        @Override
        public String formatId() {
            return "stub-v1";
        }

        @Override
        public CanonicalRecord toCanonical(RawRecord r) {
            String name = r.get("name").get(0);
            return new CanonicalRecordBuilder()
                    .sourceId("stub")
                    .sourceRecordId(String.valueOf(r.ordinal()))
                    .entityType(EntityType.INDIVIDUAL, "type")
                    .name(name, NameType.PRIMARY, "name")
                    .build();
        }

        @Override
        public CapabilityExpectation expectation() {
            return new CapabilityExpectation("stub", Map.of(), Map.of());
        }

        @Override
        public Set<CanonicalSlot> requiredSlots() {
            return Set.of(CanonicalSlot.PRIMARY_NAME);
        }
    };

    private static RawRecord raw(long ordinal, String name) {
        return new RawRecord(ordinal, Map.of("name", List.of(name)), Map.of(), "ref-" + ordinal);
    }

    @Test
    void passesWhenEveryRecordMatchesItsExpectation() {
        RawRecord raw = raw(1, "Ahmed Yousef");
        CanonicalRecord expected = STUB_ADAPTER.toCanonical(raw);
        SourceFixture fixture = new SourceFixture(UUID.randomUUID(), "stub", List.of(raw), List.of(expected));

        VerifySourceFixture.Result result = new VerifySourceFixture().verify(STUB_ADAPTER, fixture);

        assertThat(result.passed()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    // This is the canary Step 4 built the fixture mechanism to catch (CLAUDE.md §4): a
    // mis-mapped adapter still runs without throwing, so only an exact-output comparison finds it.
    @Test
    void failsWhenTheAdapterNoLongerProducesTheExpectedOutput() {
        RawRecord raw = raw(1, "Ahmed Yousef");
        CanonicalRecord staleExpectation = new CanonicalRecordBuilder()
                .sourceId("stub")
                .sourceRecordId("1")
                .entityType(EntityType.INDIVIDUAL, "type")
                .name("A Different Name", NameType.PRIMARY, "name")
                .build();
        SourceFixture fixture = new SourceFixture(UUID.randomUUID(), "stub", List.of(raw), List.of(staleExpectation));

        VerifySourceFixture.Result result = new VerifySourceFixture().verify(STUB_ADAPTER, fixture);

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).hasSize(1);
    }

    @Test
    void failsFastWhenFixtureSourceIdDoesNotMatchTheAdapter() {
        SourceFixture fixture = new SourceFixture(UUID.randomUUID(), "other-source", List.of(), List.of());

        VerifySourceFixture.Result result = new VerifySourceFixture().verify(STUB_ADAPTER, fixture);

        assertThat(result.passed()).isFalse();
    }
}
