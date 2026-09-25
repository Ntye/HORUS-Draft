package horus.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Screening evidence is append-only, enforced by the database and not by code discipline (I-5,
// CLAUDE.md §11). Each test connects AS the restricted role: a test that connected as the owner
// and "failed" would prove nothing.
class ScreeningRolePrivilegesTest extends PostgresIntegrationSupport {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    private static UUID seedRequest(Connection screen, UUID configId) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("INSERT INTO config_version (config_version_id, profile_id, payload, created_by, "
                    + "created_at) VALUES ('" + configId + "', 'priv', '{}'::jsonb, 'test', now())");
        }
        UUID screeningId = UUID.randomUUID();
        try (PreparedStatement insert = screen.prepareStatement(
                "INSERT INTO screening_request (screening_id, consumer_system, profile_id, requested_by, "
                        + "requested_at, list_version_ids, config_version_id, pipeline_version, outcome, "
                        + "idempotency_key) VALUES (?, 'test', 'priv', 'op', now(), ARRAY[?]::uuid[], ?, '1.0.0', "
                        + "'NO_MATCH', ?)")) {
            insert.setObject(1, screeningId);
            insert.setObject(2, UUID.randomUUID());
            insert.setObject(3, configId);
            insert.setString(4, "priv-key-" + screeningId);
            insert.executeUpdate();
        }
        return screeningId;
    }

    @Test
    void screenRoleCanInsertAScreeningButNeverUpdateOrDeleteIt() throws Exception {
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            UUID screeningId = seedRequest(screen, UUID.randomUUID());

            SQLException update = catchThrowableOfType(SQLException.class, () -> {
                try (PreparedStatement ps = screen.prepareStatement(
                        "UPDATE screening_request SET outcome = 'ERROR' WHERE screening_id = ?")) {
                    ps.setObject(1, screeningId);
                    ps.executeUpdate();
                }
            });
            SQLException delete = catchThrowableOfType(SQLException.class, () -> {
                try (PreparedStatement ps = screen.prepareStatement(
                        "DELETE FROM screening_request WHERE screening_id = ?")) {
                    ps.setObject(1, screeningId);
                    ps.executeUpdate();
                }
            });

            assertThat((Throwable) update).isNotNull();
            assertThat(update.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat((Throwable) delete).isNotNull();
            assertThat(delete.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void ingestAndAuditRolesCannotTouchScreeningTables() throws Exception {
        for (String[] role : new String[][] {{"horus_ingest", INGEST_PASSWORD}, {"horus_audit", AUDIT_PASSWORD}}) {
            try (Connection connection = connectAs(role[0], role[1])) {
                for (String table : new String[] {"screening_request", "screening_subject", "screening_candidate"}) {
                    SQLException read = catchThrowableOfType(SQLException.class, () -> {
                        try (Statement statement = connection.createStatement()) {
                            statement.executeQuery("SELECT 1 FROM " + table + " LIMIT 1");
                        }
                    });
                    assertThat((Throwable) read).as(role[0] + " reading " + table).isNotNull();
                    assertThat(read.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
                }
            }
        }
    }

    @Test
    void configVersionsAreInsertOnlyForTheIngestRoleAndReadOnlyForScreen() throws Exception {
        UUID configId = UUID.randomUUID();
        try (Connection ingest = connectAs("horus_ingest", INGEST_PASSWORD);
                Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            try (PreparedStatement insert = ingest.prepareStatement(
                    "INSERT INTO config_version (config_version_id, profile_id, payload, created_by, created_at) "
                            + "VALUES (?, 'priv2', '{}'::jsonb, 'test', now())")) {
                insert.setObject(1, configId);
                insert.executeUpdate();
            }

            SQLException ingestUpdate = catchThrowableOfType(SQLException.class, () -> {
                try (PreparedStatement ps = ingest.prepareStatement(
                        "UPDATE config_version SET approved_by = 'me' WHERE config_version_id = ?")) {
                    ps.setObject(1, configId);
                    ps.executeUpdate();
                }
            });
            SQLException screenInsert = catchThrowableOfType(SQLException.class, () -> {
                try (PreparedStatement ps = screen.prepareStatement(
                        "INSERT INTO config_version (config_version_id, profile_id, payload, created_by, created_at) "
                                + "VALUES (?, 'priv3', '{}'::jsonb, 'test', now())")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.executeUpdate();
                }
            });

            // I-7: a configuration is immutable once published; a change is a new version.
            assertThat(ingestUpdate.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
            assertThat(screenInsert.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }
}
