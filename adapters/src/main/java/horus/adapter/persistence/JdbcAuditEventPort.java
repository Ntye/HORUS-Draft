package horus.adapter.persistence;

import horus.application.port.AuditEventPort;
import horus.domain.audit.AuditEvent;
import java.sql.Timestamp;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

// I-5: this class holds only the horus_audit credential in production wiring (bootstrap), and
// that role has INSERT and SELECT on audit_event and nothing else (V3, proven by
// AuditEventPrivilegesTest) -- so this port implementation could not violate append-only even
// if it tried to.
@Component
public final class JdbcAuditEventPort implements AuditEventPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAuditEventPort(
            @Qualifier("auditJdbcTemplate") JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(AuditEvent event) {
        ObjectNode payload = objectMapper.createObjectNode();
        event.payload().forEach(payload::put);
        jdbcTemplate.update(
                "INSERT INTO audit_event (audit_id, event_type, actor, actor_kind, occurred_at, "
                        + "subject_type, subject_id, payload, correlation_id, previous_hash) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
                event.auditId(),
                event.eventType(),
                event.actor(),
                event.actorKind().name(),
                Timestamp.from(event.occurredAt()),
                event.subjectType(),
                event.subjectId(),
                payload.toString(),
                event.correlationId().orElse(null),
                event.previousHash().orElse(null));
    }
}
