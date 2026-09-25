package horus.application.ingestion;

import horus.application.port.IndexReconciliation;
import horus.application.port.ListVersionRepository;
import horus.domain.audit.ActorKind;
import horus.domain.listversion.ListVersion;
import horus.domain.listversion.LoadStatus;
import horus.domain.shared.ListVersionId;
import java.util.Map;
import java.util.Optional;

// §13.1 stage 10: atomically switch the active list version pointer. The only mutation here is
// list_version.status (V3's column-level grant), so "atomic" is a single UPDATE per row -- no
// multi-table transaction is required to keep this safe (I-1).
public final class PromoteListVersion {

    private final ListVersionRepository listVersionRepository;
    private final IndexReconciliation indexReconciliation;
    private final RecordAuditEvent recordAuditEvent;

    public PromoteListVersion(
            ListVersionRepository listVersionRepository,
            IndexReconciliation indexReconciliation,
            RecordAuditEvent recordAuditEvent) {
        this.listVersionRepository = listVersionRepository;
        this.indexReconciliation = indexReconciliation;
        this.recordAuditEvent = recordAuditEvent;
    }

    public record Command(ListVersionId listVersionId, String sourceId, String actor) {
    }

    public void execute(Command command) {
        ListVersion staged = listVersionRepository.findById(command.listVersionId())
                .orElseThrow(() -> new IllegalStateException("no such list version: " + command.listVersionId()));
        if (staged.status() != LoadStatus.STAGED) {
            throw new IllegalStateException(
                    "only a STAGED list version can be promoted, was " + staged.status());
        }

        // I-1: fail closed rather than promote a version whose staged data never landed.
        if (!indexReconciliation.isComplete(command.listVersionId())) {
            listVersionRepository.updateStatus(command.listVersionId(), LoadStatus.FAILED);
            recordAuditEvent.execute(new RecordAuditEvent.Command(
                    "LIST_VERSION_PROMOTION_FAILED",
                    command.actor(),
                    ActorKind.SYSTEM,
                    "ListVersion",
                    command.listVersionId().value().toString(),
                    Map.of("reason", "completeness check failed"),
                    Optional.empty()));
            throw new IllegalStateException(
                    "staged list version " + command.listVersionId() + " failed the completeness check; not promoted");
        }

        listVersionRepository.findActive(command.sourceId())
                .ifPresent(active -> listVersionRepository.updateStatus(active.listVersionId(), LoadStatus.SUPERSEDED));
        listVersionRepository.updateStatus(command.listVersionId(), LoadStatus.ACTIVE);

        recordAuditEvent.execute(new RecordAuditEvent.Command(
                "LIST_VERSION_PROMOTED",
                command.actor(),
                ActorKind.SYSTEM,
                "ListVersion",
                command.listVersionId().value().toString(),
                Map.of("sourceId", command.sourceId()),
                Optional.empty()));
    }
}
