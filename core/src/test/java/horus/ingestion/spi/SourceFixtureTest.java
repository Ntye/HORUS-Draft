package horus.ingestion.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityType;
import horus.domain.watchentity.NameType;
import horus.ingestion.format.RawRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceFixtureTest {

    private static CanonicalRecord canonical() {
        return new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name")
                .build();
    }

    @Test
    void pairsRawRecordsWithTheirExpectedCanonicalOutput() {
        RawRecord raw = new RawRecord(0L, Map.of(), Map.of(), "record#0");

        SourceFixture fixture = new SourceFixture(
                UUID.randomUUID(), "worldcheck", List.of(raw), List.of(canonical()));

        assertThat(fixture.rawRecords()).hasSize(1);
        assertThat(fixture.expected()).hasSize(1);
    }

    @Test
    void rejectsMismatchedRawAndExpectedCounts() {
        RawRecord raw = new RawRecord(0L, Map.of(), Map.of(), "record#0");

        assertThatThrownBy(() -> new SourceFixture(
                UUID.randomUUID(), "worldcheck", List.of(raw), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
