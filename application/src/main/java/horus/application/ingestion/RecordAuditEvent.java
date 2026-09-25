package horus.application.ingestion;

import horus.application.port.AuditEventPort;
import horus.application.port.IdGenerator;
import horus.domain.audit.ActorKind;
import horus.domain.audit.AuditEvent;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

// I-5: every load and promotion records an audit event through this one interactor, never a
// direct write from elsewhere -- an adapter cannot route around it (CLAUDE.md §11).
public final class RecordAuditEvent {

    private final AuditEventPort auditEventPort;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public RecordAuditEvent(AuditEventPort auditEventPort, IdGenerator idGenerator, Clock clock) {
        this.auditEventPort = auditEventPort;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public record Command(
            String eventType,
            String actor,
            ActorKind actorKind,
            String subjectType,
            String subjectId,
            Map<String, String> payload,
            Optional<String> correlationId) {
    }

    public void execute(Command command) {
        auditEventPort.record(new AuditEvent(
                idGenerator.newId(),
                command.eventType(),
                command.actor(),
                command.actorKind(),
                clock.instant(),
                command.subjectType(),
                command.subjectId(),
                command.payload(),
                command.correlationId(),
                Optional.empty()));
    }
}
