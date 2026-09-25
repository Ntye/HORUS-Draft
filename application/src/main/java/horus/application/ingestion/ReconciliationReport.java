package horus.application.ingestion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// FR-007: reconciliation report per run -- added, amended, deleted, rejected, with reasons.
public record ReconciliationReport(
        String sourceId,
        int added,
        int amended,
        int unchanged,
        int delisted,
        List<RejectedRecord> rejected) {

    public ReconciliationReport {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (added < 0 || amended < 0 || unchanged < 0 || delisted < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
        if (rejected == null) {
            throw new IllegalArgumentException("rejected must not be null (use List.of())");
        }
        rejected = List.copyOf(rejected);
    }

    // I-11: sourceRecordId is an id, never a name.
    public record RejectedRecord(String sourceRecordId, String reason) {
        public RejectedRecord {
            if (sourceRecordId == null || sourceRecordId.isBlank()) {
                throw new IllegalArgumentException("sourceRecordId must not be blank");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }
    }

    // Rejections are useless as a 5,979,934-line list and decisive as a tally: the reason codes
    // are a small controlled vocabulary, so one line per code says what went wrong (D12 B-6 -- the
    // first real load reported that it had failed and not why). Ordered by code, so two runs over
    // the same file print the same report (I-4).
    public Map<String, Long> reasonCounts() {
        Map<String, Long> counts = new TreeMap<>();
        for (RejectedRecord record : rejected) {
            counts.merge(record.reason(), 1L, Long::sum);
        }
        return counts;
    }

    // A few record ordinals per reason, so a specific failure can be looked at without printing
    // anything about the records themselves. Ordinals are positions in the file, never names.
    public Map<String, List<String>> sampleOrdinalsByReason(int perReason) {
        if (perReason <= 0) {
            throw new IllegalArgumentException("perReason must be positive");
        }
        Map<String, List<String>> samples = new LinkedHashMap<>();
        for (String reason : reasonCounts().keySet()) {
            samples.put(reason, rejected.stream()
                    .filter(r -> r.reason().equals(reason))
                    .limit(perReason)
                    .map(RejectedRecord::sourceRecordId)
                    .toList());
        }
        return samples;
    }

    public int changedCount() {
        return added + amended + delisted;
    }

    // §13.1: anomalous deltas (e.g. > 20% change) require operator confirmation before promote.
    public double deltaFraction(long previousActiveCount) {
        if (previousActiveCount <= 0) {
            return added > 0 ? 1.0 : 0.0;
        }
        return changedCount() / (double) previousActiveCount;
    }
}
