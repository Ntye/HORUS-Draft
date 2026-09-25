package horus.application.ingestion;

import horus.ingestion.format.RawRecord;
import horus.ingestion.spi.CanonicalRecord;
import horus.ingestion.spi.SourceFixture;
import horus.ingestion.spi.WatchlistSourceAdapter;
import java.util.ArrayList;
import java.util.List;

// I-7 spirit: a mis-mapped column loads cleanly and screens cleanly (CLAUDE.md §4), so this is
// the runtime regression guard that catches adapter drift before a load proceeds -- callable
// both as a JUnit fixture test and as a CLI pre-flight check.
public final class VerifySourceFixture {

    public record Result(boolean passed, List<String> failures) {
        public Result {
            if (failures == null) {
                throw new IllegalArgumentException("failures must not be null (use List.of())");
            }
            failures = List.copyOf(failures);
        }
    }

    public Result verify(WatchlistSourceAdapter adapter, SourceFixture fixture) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
        if (fixture == null) {
            throw new IllegalArgumentException("fixture must not be null");
        }
        if (!adapter.sourceId().equals(fixture.sourceId())) {
            return new Result(false, List.of(
                    "fixture sourceId " + fixture.sourceId() + " does not match adapter sourceId "
                            + adapter.sourceId()));
        }

        List<String> failures = new ArrayList<>();
        List<RawRecord> rawRecords = fixture.rawRecords();
        List<CanonicalRecord> expected = fixture.expected();
        for (int i = 0; i < rawRecords.size(); i++) {
            RawRecord raw = rawRecords.get(i);
            try {
                CanonicalRecord actual = adapter.toCanonical(raw);
                if (!actual.equals(expected.get(i))) {
                    failures.add("record ordinal " + raw.ordinal()
                            + ": canonical output does not match the fixture's expectation");
                }
            } catch (RuntimeException e) {
                failures.add("record ordinal " + raw.ordinal() + ": adapter threw " + e.getClass().getSimpleName());
            }
        }
        return new Result(failures.isEmpty(), failures);
    }
}
