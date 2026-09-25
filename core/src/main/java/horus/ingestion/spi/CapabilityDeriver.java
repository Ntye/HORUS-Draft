package horus.ingestion.spi;

import horus.domain.shared.CanonicalSlot;
import java.util.EnumMap;
import java.util.Map;

// Measured, not declared (CLAUDE.md §6): accumulates per-slot presence one CanonicalRecord at a
// time so a full-feed load never has to hold more than a handful of counters in memory.
public final class CapabilityDeriver {

    private final Map<CanonicalSlot, Long> presentCounts = new EnumMap<>(CanonicalSlot.class);
    private long recordsObserved;

    public void observe(CanonicalRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        recordsObserved++;
        for (CanonicalSlot slot : CanonicalSlot.values()) {
            if (record.has(slot)) {
                presentCounts.merge(slot, 1L, Long::sum);
            }
        }
    }

    public long recordsObserved() {
        return recordsObserved;
    }

    public Map<CanonicalSlot, Double> derive() {
        if (recordsObserved == 0) {
            return Map.of();
        }
        Map<CanonicalSlot, Double> coverage = new EnumMap<>(CanonicalSlot.class);
        for (Map.Entry<CanonicalSlot, Long> entry : presentCounts.entrySet()) {
            coverage.put(entry.getKey(), entry.getValue() / (double) recordsObserved);
        }
        return Map.copyOf(coverage);
    }
}
