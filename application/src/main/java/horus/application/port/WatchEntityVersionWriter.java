package horus.application.port;

import horus.domain.shared.ListVersionId;
import java.util.List;

public interface WatchEntityVersionWriter {

    /**
     * Inserts every aggregate's version and all its children as ONE unit. Called only for ADD,
     * AMEND and DELIST (tombstone) decisions -- an UNCHANGED entity is never written, which is what
     * makes a repeat load of the same file produce zero new versions.
     *
     * <p>A LIST, not one aggregate, because the transaction boundary is the throughput. Measured on
     * this database with its bulk-load settings and durability on: 2,000 rows committed one
     * transaction each took 5,609 ms; the same 2,000 rows in a single transaction took 35.6 ms, and
     * as one multi-row statement 21.0 ms. Commit frequency alone was worth **158x**. A load doing
     * one transaction per entity was paying that 5,990,394 times.
     *
     * <p>Atomicity is not weakened by this -- it is strengthened. The property the integration tests
     * protect is that a version row never exists without its children, and a chunk transaction
     * guarantees it for every aggregate in the chunk. What a chunk loses is the ability to say WHICH
     * record broke a constraint, so the caller retries a failed chunk one aggregate at a time
     * (a list of one) to isolate it. That cost is paid only on failure.
     */
    void write(List<WatchEntityVersionAggregate> aggregates);

    /**
     * How many version rows the store actually holds for this list version.
     *
     * <p>Read back from the store, not remembered from the writes, so that it is an independent
     * check: the loader's tally says what it believes it wrote, this says what is there. A
     * disagreement means rows went missing -- a rolled-back transaction counted as a success, a
     * swallowed error -- and an entity whose version row is absent is simply not screened. That is
     * a silent recall failure, the one class of defect this system exists to prevent (I-1, I-2),
     * so the loader compares the two before anything can be promoted.
     */
    long countVersionsIn(ListVersionId listVersionId);
}
