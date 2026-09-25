package horus.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchlistRolePrivilegesTest extends PostgresIntegrationSupport {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Test
    void screenRoleCannotWriteWatchlistTables() throws Exception {
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            SQLException failure = catchThrowableOfType(SQLException.class, () -> {
                try (PreparedStatement insert = screen.prepareStatement(
                        "INSERT INTO watch_entity (entity_id, source_id, source_entity_id) "
                                + "VALUES (?, 'worldcheck', 'wc-001')")) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.executeUpdate();
                }
            });

            assertThat((Throwable) failure).isNotNull();
            assertThat(failure.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    // I-6, beyond the step's explicit ask: even the role that wrote a watchlist version
    // can never update or delete it -- the database enforces this, not application discipline.
    @Test
    void ingestRoleCanInsertWatchlistDataButNeverUpdateOrDeleteAVersion() throws Exception {
        UUID entityId = UUID.randomUUID();
        UUID listVersionId = UUID.randomUUID();
        UUID entityVersionId = UUID.randomUUID();

        try (Connection ingest = connectAs("horus_ingest", INGEST_PASSWORD)) {
            insertListVersion(ingest, listVersionId);
            insertWatchEntity(ingest, entityId);
            insertWatchEntityVersion(ingest, entityVersionId, entityId, listVersionId);

            SQLException updateFailure = catchThrowableOfType(SQLException.class, () -> {
                try (PreparedStatement update = ingest.prepareStatement(
                        "UPDATE watch_entity_version SET primary_name = 'Changed' "
                                + "WHERE entity_version_id = ?")) {
                    update.setObject(1, entityVersionId);
                    update.executeUpdate();
                }
            });
            assertThat((Throwable) updateFailure).isNotNull();
            assertThat(updateFailure.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);

            SQLException deleteFailure = catchThrowableOfType(SQLException.class, () -> {
                try (PreparedStatement delete = ingest.prepareStatement(
                        "DELETE FROM watch_entity_version WHERE entity_version_id = ?")) {
                    delete.setObject(1, entityVersionId);
                    delete.executeUpdate();
                }
            });
            assertThat((Throwable) deleteFailure).isNotNull();
            assertThat(deleteFailure.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    // Positive + negative control for the column-level grant: the one legitimate mutation
    // (promoting status) works, everything else on the same row is still refused. record_count
    // is deliberately NOT used as the negative case here: V4 grants horus_ingest UPDATE on it
    // too, for the same reason as capabilities (see ingestRoleCanUpdateCapabilitiesButCannotChangeOtherColumns
    // below) -- it's only known once the whole staged file has streamed through.
    @Test
    void ingestRoleCanPromoteAListVersionButCannotChangeItsOtherColumns() throws Exception {
        UUID listVersionId = UUID.randomUUID();

        try (Connection ingest = connectAs("horus_ingest", INGEST_PASSWORD)) {
            insertListVersion(ingest, listVersionId);

            try (PreparedStatement promote = ingest.prepareStatement(
                    "UPDATE list_version SET status = 'ACTIVE' WHERE list_version_id = ?")) {
                promote.setObject(1, listVersionId);
                assertThat(promote.executeUpdate()).isEqualTo(1);
            }

            SQLException failure = catchThrowableOfType(SQLException.class, () -> {
                try (PreparedStatement update = ingest.prepareStatement(
                        "UPDATE list_version SET source_file_checksum = 'other' WHERE list_version_id = ?")) {
                    update.setObject(1, listVersionId);
                    update.executeUpdate();
                }
            });
            assertThat((Throwable) failure).isNotNull();
            assertThat(failure.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    // V4: the same column-level carve-out as status, for the same reason -- capabilities is
    // only known once the whole staged file has streamed through, so it is written once as an
    // update from NULL to its measured value, and nothing else on the row.
    @Test
    void ingestRoleCanUpdateCapabilitiesButCannotChangeOtherColumns() throws Exception {
        UUID listVersionId = UUID.randomUUID();

        try (Connection ingest = connectAs("horus_ingest", INGEST_PASSWORD)) {
            insertListVersion(ingest, listVersionId);

            try (PreparedStatement update = ingest.prepareStatement(
                    "UPDATE list_version SET capabilities = '{}'::jsonb WHERE list_version_id = ?")) {
                update.setObject(1, listVersionId);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }

            SQLException failure = catchThrowableOfType(SQLException.class, () -> {
                try (PreparedStatement update = ingest.prepareStatement(
                        "UPDATE list_version SET source_file_checksum = 'other' "
                                + "WHERE list_version_id = ?")) {
                    update.setObject(1, listVersionId);
                    update.executeUpdate();
                }
            });
            assertThat((Throwable) failure).isNotNull();
            assertThat(failure.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    private static void insertListVersion(Connection connection, UUID listVersionId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO list_version (list_version_id, source_id, format_id, "
                        + "source_file_name, source_file_checksum, loaded_at, record_count, "
                        + "status, loaded_by, pipeline_version) VALUES (?, 'worldcheck', "
                        + "'worldcheck-xml-v1', 'wc.xml', 'sha256:test', now(), 100, 'STAGED', "
                        + "'svc-ingest', '1.0.0')")) {
            insert.setObject(1, listVersionId);
            insert.executeUpdate();
        }
    }

    private static void insertWatchEntity(Connection connection, UUID entityId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO watch_entity (entity_id, source_id, source_entity_id) "
                        + "VALUES (?, 'worldcheck', ?)")) {
            insert.setObject(1, entityId);
            insert.setString(2, entityId.toString());
            insert.executeUpdate();
        }
    }

    private static void insertWatchEntityVersion(
            Connection connection, UUID entityVersionId, UUID entityId, UUID listVersionId)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO watch_entity_version (entity_version_id, entity_id, "
                        + "list_version_id, content_hash, entity_type, primary_name, status) "
                        + "VALUES (?, ?, ?, 'hash-1', 'INDIVIDUAL', 'Mohammed Al Sayed', 'ACTIVE')")) {
            insert.setObject(1, entityVersionId);
            insert.setObject(2, entityId);
            insert.setObject(3, listVersionId);
            insert.executeUpdate();
        }
    }
}
