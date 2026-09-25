package horus.application.ingestion;

import horus.application.port.ListVersionRepository;
import horus.domain.audit.ActorKind;
import horus.domain.listversion.ListVersion;
import horus.domain.listversion.LoadStatus;
import horus.domain.shared.ListVersionId;
import java.util.Map;
import java.util.Optional;

// §13.1 stage 10: "rollback by repointing to the previous version". The version rolled back
// away from is marked FAILED (distinct from the ordinary SUPERSEDED a newer good load produces)
// so an audit reader can tell "retired because it was bad" from "retired because it aged out".
public final class RollBackVersion {

    private final ListVersionRepository listVersionRepository;
    private final RecordAuditEvent recordAuditEvent;

    public RollBackVersion(ListVersionRepository listVersionRepository, RecordAuditEvent recordAuditEvent) {
        this.listVersionRepository = listVersionRepository;
        this.recordAuditEvent = recordAuditEvent;
    }

    public record Command(String sourceId, String reason, String actor) {
    }

    public record Result(ListVersionId rolledBackFrom, Optional<ListVersionId> nowActive) {
    }

    public Result execute(Command command) {
        ListVersion current = listVersionRepository.findActive(command.sourceId())
                .orElseThrow(() -> new IllegalStateException("no active list version for source " + command.sourceId()));

        listVersionRepository.updateStatus(current.listVersionId(), LoadStatus.FAILED);
        Optional<ListVersion> previous = listVersionRepository.findMostRecentSuperseded(command.sourceId());
        previous.ifPresent(p -> listVersionRepository.updateStatus(p.listVersionId(), LoadStatus.ACTIVE));

        recordAuditEvent.execute(new RecordAuditEvent.Command(
                "LIST_VERSION_ROLLED_BACK",
                command.actor(),
                ActorKind.OPERATOR,
                "ListVersion",
                current.listVersionId().value().toString(),
                Map.of("reason", command.reason(),
                        "nowActive", previous.map(p -> p.listVersionId().value().toString()).orElse("none")),
                Optional.empty()));

        return new Result(current.listVersionId(), previous.map(ListVersion::listVersionId));
    }
}
