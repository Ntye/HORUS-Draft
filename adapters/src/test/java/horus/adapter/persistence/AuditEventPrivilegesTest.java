package horus.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// I-5: audit_event is append-only. This connects as the real, credential-restricted
// horus_audit role -- not the migration owner -- so the negative assertions actually mean
// something (CLAUDE.md's "You verify" for this step).
class AuditEventPrivilegesTest extends PostgresIntegrationSupport {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Test
    void auditRoleCanInsertAndSelectButNeverUpdateOrDelete() throws Exception {
        UUID auditId = UUID.randomUUID();

        try (Connection audit = connectAs("horus_audit", AUDIT_PASSWORD)) {
            insertAuditEvent(audit, auditId);

            try (PreparedStatement select = audit.prepareStatement(
                    "SELECT audit_id FROM audit_event WHERE audit_id = ?")) {
                select.setObject(1, auditId);
                assertThat(select.executeQuery().next()).isTrue();
            }

            SQLException updateFailure = catchThrowableOfType(SQLException.class, () -> {
                try (PreparedStatement update = audit.prepareStatement(
                        "UPDATE audit_event SET actor = 'someone-else' WHERE audit_id = ?")) {
                    update.setObject(1, auditId);
                    update.executeUpdate();
                }
            });
            assertThat((Throwable) updateFailure).isNotNull();
            assertThat(updateFailure.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);

            SQLException deleteFailure = catchThrowableOfType(SQLException.class, () -> {
                try (PreparedStatement delete = audit.prepareStatement(
                        "DELETE FROM audit_event WHERE audit_id = ?")) {
                    delete.setObject(1, auditId);
                    delete.executeUpdate();
                }
            });
            assertThat((Throwable) deleteFailure).isNotNull();
            assertThat(deleteFailure.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    private static void insertAuditEvent(Connection connection, UUID auditId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO audit_event "
                        + "(audit_id, event_type, actor, actor_kind, occurred_at, subject_type, "
                        + "subject_id, payload) VALUES (?, ?, ?, ?, now(), ?, ?, ?::jsonb)")) {
            insert.setObject(1, auditId);
            insert.setString(2, "LIST_VERSION_PROMOTED");
            insert.setString(3, "svc-ingest");
            insert.setString(4, "SERVICE");
            insert.setString(5, "ListVersion");
            insert.setString(6, UUID.randomUUID().toString());
            insert.setString(7, "{}");
            insert.executeUpdate();
        }
    }
}
