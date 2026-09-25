package horus.application.ingestion;

/**
 * Reports how a long load is getting on, and where its time goes.
 *
 * <p>Why this exists: the first attempt at the real feed ran for an hour and a half, printed nothing,
 * and died. Answering "is it stuck or just slow?" needed database queries against a running process,
 * and answering "what is slow?" needed guesswork -- and a guess at a performance fix is as unsafe as
 * a guess at a correctness fix (§13.8). A load of 5,990,394 records must be able to say how far it
 * has come and which stage is costing the time, or the only way to tune it is by trial.
 *
 * <p>The split between mapping and persistence is the question that matters. Child rows are inserted
 * one at a time (D12 M-3) and the narrative parser runs over text reaching 125,644 characters (§6):
 * either could dominate, and batching inserts would be wasted work if parsing is the cost.
 *
 * <p>This is reporting only. Nothing here reaches the reconciliation report or the stored screening,
 * so a load stays reproducible from its input, list version and config alone (I-4).
 */
@FunctionalInterface
public interface LoadProgress {

    LoadProgress NONE = snapshot -> { };

    void onProgress(Snapshot snapshot);

    /**
     * @param records total records read so far
     * @param added entities added so far
     * @param amended entity versions amended so far
     * @param unchanged records whose content hash was unchanged
     * @param rejected records quarantined so far
     * @param mapNanos cumulative time in the source adapter's mapping
     * @param persistNanos cumulative time writing to the store
     * @param elapsedNanos wall time since the record loop started
     * @param finished true for the single summary emitted when the loop ends
     */
    record Snapshot(
            long records,
            long added,
            long amended,
            long unchanged,
            long rejected,
            long mapNanos,
            long persistNanos,
            long elapsedNanos,
            boolean finished) {

        public double recordsPerSecond() {
            return elapsedNanos <= 0 ? 0.0 : records / (elapsedNanos / 1_000_000_000.0);
        }

        /** Share of accounted-for time spent mapping, 0..1. The remainder is persistence. */
        public double mapShare() {
            long accounted = mapNanos + persistNanos;
            return accounted <= 0 ? 0.0 : mapNanos / (double) accounted;
        }

        /**
         * Seconds still to go at the current rate, for a run whose total is known in advance.
         * Returns empty when the rate is not yet meaningful or the total is unknown.
         */
        public java.util.OptionalLong secondsRemaining(long expectedTotalRecords) {
            double rate = recordsPerSecond();
            if (rate <= 0 || expectedTotalRecords <= records) {
                return java.util.OptionalLong.empty();
            }
            return java.util.OptionalLong.of((long) ((expectedTotalRecords - records) / rate));
        }
    }
}
