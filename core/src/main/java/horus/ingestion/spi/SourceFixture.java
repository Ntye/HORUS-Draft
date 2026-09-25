package horus.ingestion.spi;

import horus.ingestion.format.RawRecord;
import java.util.List;
import java.util.UUID;

// «canary for INGESTION» (horus-ingestion-spi.drawio): a mis-mapped column loads fine and
// screens fine, so this is what actually catches it -- at mapping time and on every reload.
public record SourceFixture(
        UUID fixtureId, String sourceId, List<RawRecord> rawRecords, List<CanonicalRecord> expected) {

    public SourceFixture {
        if (fixtureId == null) {
            throw new IllegalArgumentException("fixtureId must not be null");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (rawRecords == null || expected == null) {
            throw new IllegalArgumentException("rawRecords and expected must not be null");
        }
        if (rawRecords.size() != expected.size()) {
            throw new IllegalArgumentException(
                    "rawRecords and expected must be the same size (one expectation per raw record)");
        }
        rawRecords = List.copyOf(rawRecords);
        expected = List.copyOf(expected);
    }
}
