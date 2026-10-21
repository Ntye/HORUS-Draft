package horus.domain.listversion;

/** What a load still owes a human. */
public enum ReviewState {
    /** Nothing was waived; there is nothing to look at. */
    NONE,
    /** A capability deviation was waived and the load promoted unreviewed. A human owes it a look. */
    PENDING,
    /** A human has seen the waived deviation and signed it off, with a reason in the audit trail. */
    ACKNOWLEDGED
}
