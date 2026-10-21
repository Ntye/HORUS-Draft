package horus.application.ingestion;

import horus.application.port.ListVersionRepository;
import horus.domain.audit.ActorKind;
import horus.domain.listversion.ListVersion;
import horus.domain.listversion.ReviewState;
import horus.domain.shared.ListVersionId;
import java.util.Map;
import java.util.Optional;

/**
 * Signs off a capability deviation that an unattended load waived and promoted.
 *
 * <p>Without this the mode would be a way to switch a control off: the load would promote, the
 * waiver would sit in {@code review_state = PENDING} forever, and nothing would ever require a
 * person to look. The point of moving the decision from before promotion to after it is that it
 * still gets made.
 *
 * <p>A reason is mandatory, for the same purpose it is mandatory on {@link ConfirmStagedVersion}:
 * an override with no recorded justification is indistinguishable from having no control.
 *
 * <p>Note what this does NOT do -- it does not promote, demote or alter a single row of watchlist
 * data. The load is already serving. This records that a human has seen why, which is the evidence
 * Internal Audit tests (I-5).
 */
public final class AcknowledgeLoadReview {

    private static final int MAX_REASON_LENGTH = 500;

    private final ListVersionRepository listVersionRepository;
    private final RecordAuditEvent recordAuditEvent;

    public AcknowledgeLoadReview(
            ListVersionRepository listVersionRepository, RecordAuditEvent recordAuditEvent) {
        this.listVersionRepository = listVersionRepository;
        this.recordAuditEvent = recordAuditEvent;
    }

    public record Command(ListVersionId listVersionId, String reason, String actor) {

        public Command {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("a reason must be given for the acknowledgement");
            }
            if (reason.length() > MAX_REASON_LENGTH) {
                throw new IllegalArgumentException(
                        "reason must be at most " + MAX_REASON_LENGTH + " characters");
            }
            if (actor == null || actor.isBlank()) {
                throw new IllegalArgumentException("actor must not be blank");
            }
        }
    }

    public void execute(Command command) {
        ListVersion listVersion = listVersionRepository.findById(command.listVersionId())
                .orElseThrow(() -> new IllegalStateException("no such list version: " + command.listVersionId()));
        if (!listVersion.approval().owesReview()) {
            // Not an error worth hiding: acknowledging something twice, or acknowledging a load
            // that never waived anything, means the operator is looking at the wrong row.
            throw new IllegalStateException(
                    "list version " + command.listVersionId() + " owes no review, its review state is "
                            + listVersion.approval().reviewState());
        }

        listVersionRepository.updateReviewState(command.listVersionId(), ReviewState.ACKNOWLEDGED);
        recordAuditEvent.execute(new RecordAuditEvent.Command(
                "LOAD_REVIEW_ACKNOWLEDGED",
                command.actor(),
                ActorKind.OPERATOR,
                "ListVersion",
                command.listVersionId().value().toString(),
                Map.of("reason", command.reason()),
                Optional.empty()));
    }
}
