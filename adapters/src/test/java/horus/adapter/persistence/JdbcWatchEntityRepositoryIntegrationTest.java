package horus.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import horus.domain.shared.EntityId;
import horus.domain.watchentity.WatchEntity;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

// D12 H-9. watch_entity holds stable identity: never versioned, never deleted (CLAUDE.md §5), so a
// row outlives the load that wrote it -- including a load that failed, whose versions the
// current-version view excludes. A retry then sees the entity as new while its identity row is
// already there. Twice on the real feed that turned a clean retry into rejections on records that
// had nothing wrong with them, so these run against real Postgres on the ingest role's own
// connection: the behaviour under test is the unique constraint's, and only the database has it.
class JdbcWatchEntityRepositoryIntegrationTest extends PostgresIntegrationSupport {

    @Test
    void anIdentityThatAlreadyExistsIsAdoptedRatherThanDuplicatedOrRejected() throws Exception {
        String sourceId = "ensure-src-" + UUID.randomUUID();
        try (Connection ingest = connectAs("horus_ingest", INGEST_PASSWORD)) {
            JdbcWatchEntityRepository repository = repositoryOn(ingest);

            EntityId first = repository.ensure(
                    new WatchEntity(new EntityId(UUID.randomUUID()), sourceId, "a-1"));
            // A retry generates a fresh id, exactly as LoadWatchlistVersion's ADD branch does.
            EntityId onRetry = repository.ensure(
                    new WatchEntity(new EntityId(UUID.randomUUID()), sourceId, "a-1"));

            assertThat(onRetry).isEqualTo(first);
            assertThat(rowsFor(ingest, sourceId)).isEqualTo(1);
        }
    }

    @Test
    void aGenuinelyNewIdentityKeepsTheIdItWasOffered() throws Exception {
        String sourceId = "ensure-src-" + UUID.randomUUID();
        EntityId offered = new EntityId(UUID.randomUUID());
        try (Connection ingest = connectAs("horus_ingest", INGEST_PASSWORD)) {
            JdbcWatchEntityRepository repository = repositoryOn(ingest);

            assertThat(repository.ensure(new WatchEntity(offered, sourceId, "b-1"))).isEqualTo(offered);
            assertThat(rowsFor(ingest, sourceId)).isEqualTo(1);
        }
    }

    // Two sources may legitimately use the same record id: uniqueness is on the PAIR. If ensure()
    // conflated them, one source's entities would silently adopt another's identities.
    @Test
    void theSameRecordIdUnderADifferentSourceIsADifferentEntity() throws Exception {
        String sourceA = "ensure-src-" + UUID.randomUUID();
        String sourceB = "ensure-src-" + UUID.randomUUID();
        try (Connection ingest = connectAs("horus_ingest", INGEST_PASSWORD)) {
            JdbcWatchEntityRepository repository = repositoryOn(ingest);

            EntityId inA = repository.ensure(
                    new WatchEntity(new EntityId(UUID.randomUUID()), sourceA, "shared-1"));
            EntityId inB = repository.ensure(
                    new WatchEntity(new EntityId(UUID.randomUUID()), sourceB, "shared-1"));

            assertThat(inB).isNotEqualTo(inA);
            assertThat(rowsFor(ingest, sourceA)).isEqualTo(1);
            assertThat(rowsFor(ingest, sourceB)).isEqualTo(1);
        }
    }

    // I-6: adopting an existing identity must not need UPDATE. The ingest role holds only SELECT
    // and INSERT on watch_entity (V3), so ensure() succeeding on the role's own connection is the
    // proof that nothing is rewritten -- ON CONFLICT DO UPDATE would fail here.
    @Test
    void ensureNeedsNoUpdatePrivilegeOnTheIdentityTable() throws Exception {
        String sourceId = "ensure-src-" + UUID.randomUUID();
        try (Connection ingest = connectAs("horus_ingest", INGEST_PASSWORD)) {
            JdbcWatchEntityRepository repository = repositoryOn(ingest);
            repository.ensure(new WatchEntity(new EntityId(UUID.randomUUID()), sourceId, "c-1"));
            repository.ensure(new WatchEntity(new EntityId(UUID.randomUUID()), sourceId, "c-1"));

            Boolean hasUpdate = new JdbcTemplate(new SingleConnectionDataSource(ingest, true))
                    .queryForObject(
                            "SELECT has_table_privilege('horus_ingest', 'watch_entity', 'UPDATE')",
                            Boolean.class);
            assertThat(hasUpdate).isFalse();
        }
    }

    private static JdbcWatchEntityRepository repositoryOn(Connection connection) {
        return new JdbcWatchEntityRepository(
                new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
    }

    private static int rowsFor(Connection connection, String sourceId) {
        Integer count = new JdbcTemplate(new SingleConnectionDataSource(connection, true))
                .queryForObject("SELECT count(*) FROM watch_entity WHERE source_id = ?", Integer.class, sourceId);
        return count == null ? 0 : count;
    }
}
