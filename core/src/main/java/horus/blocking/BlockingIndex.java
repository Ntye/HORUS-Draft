package horus.blocking;

// Port owned by the code that consumes it (CLAUDE.md §4). Implementations live in an adapter;
// horus.blocking itself performs no I/O.
public interface BlockingIndex {

    IndexCapabilities capabilities();

    // A lookup that cannot complete must say so via Completeness.PARTIAL or throw; it must never
    // return an empty COMPLETE outcome for a failure.
    BlockingOutcome lookup(KeyProbe probe);
}
