package horus.application.ingestion.testsupport;

import horus.application.port.WatchEntityVersionAggregate;
import horus.application.port.WatchEntityVersionWriter;
import horus.domain.shared.ListVersionId;
import java.util.ArrayList;
import java.util.List;

public final class FakeWatchEntityVersionWriter implements WatchEntityVersionWriter {

    private final List<WatchEntityVersionAggregate> written = new ArrayList<>();
    private long lostWrites;
    private java.util.function.Predicate<WatchEntityVersionAggregate> failOn;

    @Override
    public void write(List<WatchEntityVersionAggregate> aggregates) {
        if (failOn != null && aggregates.stream().anyMatch(failOn)) {
            // A chunk write is all-or-nothing, so nothing from this call is recorded -- which is
            // what forces the loader to replay the chunk one record at a time.
            throw new IllegalStateException("simulated chunk write failure");
        }
        written.addAll(aggregates);
    }

    /** Makes any chunk containing a matching aggregate fail, as a constraint violation would. */
    public void failOnAggregate(java.util.function.Predicate<WatchEntityVersionAggregate> failOn) {
        this.failOn = failOn;
    }

    public List<WatchEntityVersionAggregate> written() {
        return List.copyOf(written);
    }

    // Counted from what was actually recorded, so the fake cannot agree with the loader by
    // construction -- a test that drops a write here fails the loader's count check, as the real
    // store would. loseWrites(n) makes that case directly testable.
    @Override
    public long countVersionsIn(ListVersionId listVersionId) {
        long stored = written.stream()
                .filter(a -> a.version().listVersionId().equals(listVersionId))
                .count();
        return Math.max(0, stored - lostWrites);
    }

    /** Pretends n version rows never reached the store, without the loader being told. */
    public void loseWrites(long n) {
        this.lostWrites = n;
    }
}
