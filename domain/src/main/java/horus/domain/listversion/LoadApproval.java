package horus.domain.listversion;

/**
 * How a load was authorised, and what it still owes a reviewer.
 *
 * <p>One value object rather than two loose fields on {@link ListVersion}, because the pair is only
 * meaningful together: {@code PENDING} can only arise from {@code NOT_REQUIRED}, and that rule is
 * worth enforcing where it cannot be forgotten.
 */
public record LoadApproval(ApprovalMode mode, ReviewState reviewState) {

    public LoadApproval {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (reviewState == null) {
            throw new IllegalArgumentException("reviewState must not be null");
        }
        if (mode == ApprovalMode.REQUIRED && reviewState != ReviewState.NONE) {
            // A load a human had to approve before it served cannot also be owed a review after.
            throw new IllegalArgumentException("a REQUIRED load cannot carry a review state");
        }
    }

    public static LoadApproval required() {
        return new LoadApproval(ApprovalMode.REQUIRED, ReviewState.NONE);
    }

    public static LoadApproval unattended() {
        return new LoadApproval(ApprovalMode.NOT_REQUIRED, ReviewState.NONE);
    }

    /** A deviation was waived and the load promoted: a human now owes it a look. */
    public LoadApproval owingReview() {
        if (mode != ApprovalMode.NOT_REQUIRED) {
            throw new IllegalStateException("only an unattended load can owe a review");
        }
        return new LoadApproval(mode, ReviewState.PENDING);
    }

    public boolean owesReview() {
        return reviewState == ReviewState.PENDING;
    }

    public boolean waivesCapabilityDeviation() {
        return mode == ApprovalMode.NOT_REQUIRED;
    }
}
