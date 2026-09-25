package horus.application.ingestion;

import horus.application.port.ListVersionRepository;
import horus.domain.audit.ActorKind;
import horus.domain.listversion.ListVersion;
import horus.domain.listversion.LoadStatus;
import horus.domain.shared.ListVersionId;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a STAGED list version that a load left awaiting an operator's decision.
 *
 * <p>Two things put a load here, and §13.1 names the first: an anomalous delta ("> 20% change
 * requires operator confirmation"), and a capability deviation -- the measured shape of the file
 * falling outside what the adapter expects. Both mean the same thing operationally: the data is
 * written and consistent, but something about it needs a judgement no algorithm should make.
 *
 * <p>Named for what it does rather than for one of its triggers. It was {@code ConfirmAnomalousDelta}
 * while a delta was the only way to get here; recording a capability decision under an event called
 * {@code ANOMALOUS_DELTA_CONFIRMED} would have put a false statement in the audit trail, and the
 * audit trail is the thing Internal Audit tests (I-5). Which condition was being resolved is
 * recoverable from the load's own event, and from {@code awaiting} on the decision recorded here.
 *
 * <p>A reason is required. This is the one path that lets a person override a control that has just
 * fired, and an override with no recorded justification is indistinguishable from not having the
 * control (see D12 H-5 on the same gap in configuration approval).
 *
 * <p>Resolved later, and possibly by a different process, which is exactly why
 * {@link PromoteListVersion} re-checks completeness independently rather than trusting the original
 * load's in-memory state.
 */
public final class ConfirmStagedVersion {

    public enum Decision {
        CONFIRM,
        REJECT
    }

    /** Why the load stopped and asked. Recorded with the decision so the pair stands on its own. */
    public enum AwaitingReason {
        ANOMALOUS_DELTA,
        CAPABILITY_DEVIATION
    }

    private final ListVersionRepository listVersionRepository;
    private final PromoteListVersion promoteListVersion;
    private final RecordAuditEvent recordAuditEvent;

    public ConfirmStagedVersion(
            ListVersionRepository listVersionRepository,
            PromoteListVersion promoteListVersion,
            RecordAuditEvent recordAuditEvent) {
        this.listVersionRepository = listVersionRepository;
        this.promoteListVersion = promoteListVersion;
        this.recordAuditEvent = recordAuditEvent;
    }

    public record Command(
            ListVersionId listVersionId,
            Decision decision,
            AwaitingReason awaiting,
            String reason,
            String actor) {

        public Command {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("a reason must be given for the decision");
            }
            if (reason.length() > MAX_REASON_LENGTH) {
                throw new IllegalArgumentException(
                        "reason must be at most " + MAX_REASON_LENGTH + " characters");
            }
        }
    }

    private static final int MAX_REASON_LENGTH = 500;

    public void execute(Command command) {
        ListVersion staged = listVersionRepository.findById(command.listVersionId())
                .orElseThrow(() -> new IllegalStateException("no such list version: " + command.listVersionId()));
        if (staged.status() != LoadStatus.STAGED) {
            throw new IllegalStateException(
                    "only a STAGED list version awaiting confirmation can be confirmed or rejected, was "
                            + staged.status());
        }

        Map<String, String> metadata = Map.of(
                "awaiting", command.awaiting().name(),
                "reason", command.reason());

        if (command.decision() == Decision.REJECT) {
            listVersionRepository.updateStatus(command.listVersionId(), LoadStatus.FAILED);
            recordAuditEvent.execute(new RecordAuditEvent.Command(
                    "STAGED_VERSION_REJECTED",
                    command.actor(),
                    ActorKind.OPERATOR,
                    "ListVersion",
                    command.listVersionId().value().toString(),
                    metadata,
                    Optional.empty()));
            return;
        }

        recordAuditEvent.execute(new RecordAuditEvent.Command(
                "STAGED_VERSION_CONFIRMED",
                command.actor(),
                ActorKind.OPERATOR,
                "ListVersion",
                command.listVersionId().value().toString(),
                metadata,
                Optional.empty()));
        promoteListVersion.execute(
                new PromoteListVersion.Command(command.listVersionId(), staged.sourceId(), command.actor()));
    }
}
