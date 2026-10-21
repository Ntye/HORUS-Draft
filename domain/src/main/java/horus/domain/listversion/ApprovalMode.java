package horus.domain.listversion;

/**
 * Whether a load's judgement holds must be settled by a human before it can serve.
 *
 * <p>{@link #REQUIRED} is the default everywhere, so a caller that says nothing gets the closed
 * behaviour (I-1). {@link #NOT_REQUIRED} exists for a scheduled load, which cannot wait for anyone.
 *
 * <p>It waives exactly one condition: a capability deviation. It never waives an integrity failure
 * -- the reject-fraction gate, the version-row count check, an abandoned load -- and it never
 * waives the anomalous-delta hold, because a change over 20% of the list is the signature of a
 * truncated delivery and promoting one writes tombstones that I-6 makes permanent.
 */
public enum ApprovalMode {
    REQUIRED,
    NOT_REQUIRED
}
