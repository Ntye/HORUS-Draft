package horus.blocking;

// I-1: COMPLETE = every match the strategy could find is here. TRUNCATED = a cap cut the list.
// PARTIAL = the search itself could not be completed; the subject must be ERROR, never a clear.
// Declaration order is severity order, which worst() relies on.
public enum Completeness {
    COMPLETE,
    TRUNCATED,
    PARTIAL;

    public static Completeness worst(Completeness a, Completeness b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
