package horus.domain.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// I-5: append-only evidence. This is a fact once recorded -- there is no wither, no update path.
// I-11: actor/subjectId are ids (service accounts, operator logins, entity/list-version ids),
// never names -- callers must not put a screened name in payload either.
public record AuditEvent(
        UUID auditId,
        String eventType,
        String actor,
        ActorKind actorKind,
        Instant occurredAt,
        String subjectType,
        String subjectId,
        Map<String, String> payload,
        Optional<String> correlationId,
        Optional<String> previousHash) {

    public AuditEvent {
        if (auditId == null) {
            throw new IllegalArgumentException("auditId must not be null");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be blank");
        }
        if (actorKind == null) {
            throw new IllegalArgumentException("actorKind must not be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        if (subjectType == null || subjectType.isBlank()) {
            throw new IllegalArgumentException("subjectType must not be blank");
        }
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null (use Map.of())");
        }
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId must not be null (use Optional.empty())");
        }
        if (previousHash == null) {
            throw new IllegalArgumentException("previousHash must not be null (use Optional.empty())");
        }
        payload = Map.copyOf(payload);
    }
}
