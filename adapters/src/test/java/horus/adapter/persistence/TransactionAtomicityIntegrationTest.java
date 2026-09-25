package horus.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.application.port.ScreeningRecord;
import horus.application.port.WatchEntityVersionAggregate;
import horus.domain.screening.ScreeningCandidate;
import horus.domain.screening.ScreeningRequest;
import horus.domain.screening.ScreeningSubject;
import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityType;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.ListVersionId;
import horus.domain.shared.QueryName;
import horus.domain.shared.ScreeningCandidateId;
import horus.domain.shared.ScreeningId;
import horus.domain.shared.ScreeningSubjectId;
import horus.domain.watchentity.EntityStatus;
import horus.domain.watchentity.NameType;
import horus.domain.watchentity.WatchEntityVersion;
import horus.domain.watchentity.WatchName;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

// A half-written evidence row is worse than none. These prove that a failure part-way through a
// write leaves NOTHING behind -- on the connections of the restricted roles, which is where the
// transaction must actually take effect. (Found in Step 9: the previous @Transactional was bound
// to the admin DataSource and never covered the ingest connection at all.)
class TransactionAtomicityIntegrationTest extends PostgresIntegrationSupport {

    @Test
    void aFailureWhileWritingAnEntityVersionsChildrenLeavesNoVersionRowBehind() throws Exception {
        UUID entityId = UUID.randomUUID();
        UUID listVersionId = UUID.randomUUID();
        try (Connection admin = adminConnection();
                Connection ingest = connectAs("horus_ingest", INGEST_PASSWORD)) {
            JdbcTemplate adminT = new JdbcTemplate(new SingleConnectionDataSource(admin, true));
            adminT.update("INSERT INTO list_version (list_version_id, source_id, format_id, source_file_name, "
                    + "source_file_checksum, loaded_at, record_count, status, loaded_by, pipeline_version) "
                    + "VALUES (?, 'atomic-src', 'f', 'f.xml', ?, now(), 1, 'STAGED', 'test', '1.0.0')",
                    listVersionId, "sha-" + listVersionId);
            adminT.update("INSERT INTO watch_entity (entity_id, source_id, source_entity_id) "
                    + "VALUES (?, 'atomic-src', 'a-1')", entityId);

            EntityVersionId versionId = new EntityVersionId(UUID.randomUUID());
            WatchEntityVersion version = new WatchEntityVersion(versionId, new EntityId(entityId),
                    new ListVersionId(listVersionId), "hash-1", EntityType.INDIVIDUAL, Optional.empty(),
                    "Zorvan Talmesk", EntityStatus.ACTIVE, List.of(), List.of(), Optional.empty(), Optional.empty());
            UUID duplicatedNameId = UUID.randomUUID();
            WatchName first = new WatchName(duplicatedNameId, versionId, NameType.PRIMARY, "Zorvan Talmesk",
                    "zorvan talmesk", List.of("zorvan", "talmesk"), List.of(), List.of(), "LATIN", "en",
                    Optional.empty());
            // Same primary key: the second child insert must fail AFTER the version row went in.
            WatchName duplicate = new WatchName(duplicatedNameId, versionId, NameType.ALIAS, "Z Talmesk",
                    "z talmesk", List.of("z", "talmesk"), List.of(), List.of(), "LATIN", "en", Optional.empty());

            var writer = new JdbcWatchEntityVersionWriter(new JdbcTemplate(new SingleConnectionDataSource(ingest, true)));
            WatchEntityVersionAggregate aggregate = new WatchEntityVersionAggregate(version, List.of(first, duplicate),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

            assertThatThrownBy(() -> writer.write(List.of(aggregate))).isInstanceOf(RuntimeException.class);

            Long versionRows = adminT.queryForObject(
                    "SELECT COUNT(*) FROM watch_entity_version WHERE entity_version_id = ?", Long.class,
                    versionId.value());
            Long nameRows = adminT.queryForObject(
                    "SELECT COUNT(*) FROM watch_name WHERE entity_version_id = ?", Long.class, versionId.value());
            assertThat(versionRows).as("version row must roll back with its children").isZero();
            assertThat(nameRows).isZero();
        }
    }

    // The unit is now a CHUNK of entities, not one entity, so the property has to hold at chunk
    // granularity: one bad row anywhere in the chunk and NOTHING from the chunk survives. That is
    // what lets the loader replay the chunk record by record to find the culprit without risking a
    // half-written entity (D12 M-3).
    @Test
    void aFailureAnywhereInAChunkLeavesNoRowFromAnyOfItsEntities() throws Exception {
        UUID listVersionId = UUID.randomUUID();
        try (Connection admin = adminConnection();
                Connection ingest = connectAs("horus_ingest", INGEST_PASSWORD)) {
            JdbcTemplate adminT = new JdbcTemplate(new SingleConnectionDataSource(admin, true));
            adminT.update("INSERT INTO list_version (list_version_id, source_id, format_id, source_file_name, "
                    + "source_file_checksum, loaded_at, record_count, status, loaded_by, pipeline_version) "
                    + "VALUES (?, 'chunk-src', 'f', 'f.xml', ?, now(), 3, 'STAGED', 'test', '1.0.0')",
                    listVersionId, "sha-" + listVersionId);

            List<WatchEntityVersionAggregate> chunk = new java.util.ArrayList<>();
            List<EntityVersionId> versionIds = new java.util.ArrayList<>();
            UUID collidingNameId = UUID.randomUUID();
            for (int i = 0; i < 3; i++) {
                UUID entityId = UUID.randomUUID();
                adminT.update("INSERT INTO watch_entity (entity_id, source_id, source_entity_id) "
                        + "VALUES (?, 'chunk-src', ?)", entityId, "c-" + i);
                EntityVersionId versionId = new EntityVersionId(UUID.randomUUID());
                versionIds.add(versionId);
                WatchEntityVersion version = new WatchEntityVersion(versionId, new EntityId(entityId),
                        new ListVersionId(listVersionId), "hash-" + i, EntityType.INDIVIDUAL, Optional.empty(),
                        "Entity " + i, EntityStatus.ACTIVE, List.of(), List.of(), Optional.empty(), Optional.empty());
                // Entities 0 and 2 share a name primary key: the third insert collides, after the
                // first two entities' rows have already gone in.
                UUID nameId = i == 1 ? UUID.randomUUID() : collidingNameId;
                WatchName name = new WatchName(nameId, versionId, NameType.PRIMARY, "Entity " + i,
                        "entity " + i, List.of("entity"), List.of(), List.of(), "LATIN", "en", Optional.empty());
                chunk.add(new WatchEntityVersionAggregate(version, List.of(name), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
            }

            var writer = new JdbcWatchEntityVersionWriter(
                    new JdbcTemplate(new SingleConnectionDataSource(ingest, true)));

            assertThatThrownBy(() -> writer.write(chunk)).isInstanceOf(RuntimeException.class);

            Long versionRows = adminT.queryForObject(
                    "SELECT COUNT(*) FROM watch_entity_version WHERE list_version_id = ?", Long.class,
                    listVersionId);
            assertThat(versionRows).as("no entity in the chunk may survive a failure in any of them").isZero();
            for (EntityVersionId versionId : versionIds) {
                assertThat(adminT.queryForObject("SELECT COUNT(*) FROM watch_name WHERE entity_version_id = ?",
                        Long.class, versionId.value())).isZero();
            }
            // The version-count check the loader runs before promoting therefore sees zero, not two.
            assertThat(writer.countVersionsIn(new ListVersionId(listVersionId))).isZero();
        }
    }

    @Test
    void aFailureWhileStoringAScreeningLeavesNoRequestOrSubjectRowBehind() throws Exception {
        UUID configId = UUID.randomUUID();
        try (Connection admin = adminConnection();
                Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            JdbcTemplate adminT = new JdbcTemplate(new SingleConnectionDataSource(admin, true));
            adminT.update("INSERT INTO config_version (config_version_id, profile_id, payload, created_by, "
                    + "created_at) VALUES (?, 'atomic', '{}'::jsonb, 'test', now())", configId);

            ScreeningId screeningId = new ScreeningId(UUID.randomUUID());
            ScreeningSubjectId subjectId = new ScreeningSubjectId(UUID.randomUUID());
            ScreeningRequest request = new ScreeningRequest(screeningId, "test", Optional.empty(), "atomic",
                    "operator", Instant.parse("2026-09-24T10:00:00Z"), Set.of(new ListVersionId(UUID.randomUUID())),
                    configId, "1.0.0", DecisionBand.POSSIBLE_MATCH, Optional.empty(), "atomic-key-" + screeningId.value());
            ScreeningSubject subject = new ScreeningSubject(subjectId, screeningId, Optional.empty(),
                    new QueryName("Zorvan Talmesk"), 1, 80, DecisionBand.POSSIBLE_MATCH);
            // References an entity that does not exist: the FK fails after request and subject went in.
            ScreeningCandidate candidate = new ScreeningCandidate(new ScreeningCandidateId(UUID.randomUUID()),
                    subjectId, "atomic-src", new EntityId(UUID.randomUUID()), new EntityVersionId(UUID.randomUUID()),
                    80, "{}", "1", DecisionBand.POSSIBLE_MATCH, 1);

            var repository = new JdbcScreeningRepository(new JdbcTemplate(new SingleConnectionDataSource(screen, true)));
            ScreeningRecord record = new ScreeningRecord(request,
                    List.of(new ScreeningRecord.SubjectRecord(subject, List.of(candidate))));

            assertThatThrownBy(() -> repository.save(record)).isInstanceOf(RuntimeException.class);

            assertThat(adminT.queryForObject("SELECT COUNT(*) FROM screening_request WHERE screening_id = ?",
                    Long.class, screeningId.value())).isZero();
            assertThat(adminT.queryForObject("SELECT COUNT(*) FROM screening_subject WHERE screening_id = ?",
                    Long.class, screeningId.value())).isZero();
        }
    }
}
