package horus.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import horus.application.ingestion.LoadWatchlistVersion;
import horus.application.ingestion.PromoteListVersion;
import horus.application.ingestion.ReconcileVersion;
import horus.application.ingestion.RecordAuditEvent;
import horus.application.port.ActiveEntityVersionLookup;
import horus.application.port.AuditEventPort;
import horus.application.port.IdGenerator;
import horus.application.port.IndexReconciliation;
import horus.application.port.ListVersionRepository;
import horus.application.port.WatchEntityRepository;
import horus.application.port.WatchEntityVersionWriter;
import horus.domain.listversion.LoadStatus;
import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import horus.domain.watchentity.NameType;
import horus.ingestion.format.FormatReader;
import horus.ingestion.format.RawRecord;
import horus.ingestion.format.XmlFormatReader;
import horus.ingestion.spi.AdapterRegistry;
import horus.ingestion.spi.CanonicalRecord;
import horus.ingestion.spi.CanonicalRecordBuilder;
import horus.ingestion.spi.CapabilityExpectation;
import horus.ingestion.spi.WatchlistSourceAdapter;
import horus.normalisation.NormalisationPipeline;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.ObjectMapper;

// The application-layer LoadWatchlistVersionTest proves the reconciliation logic against fakes;
// this proves the real wiring (JSONB round-tripping, array columns, the V4 column-level grants,
// transactions) actually works against Postgres (CLAUDE.md §10: integration tests use
// Testcontainers, never a shared database).
//
// Uses a small self-authored stub adapter/fixture rather than WorldCheckAdapter: that adapter's
// CapabilityExpectation is calibrated against the real feed's population statistics (CLAUDE.md
// §6 -- e.g. DOB coverage 27% +/-5%), which a handful of synthetic records can never match, and
// would trip the capability gate for reasons unrelated to what this test is proving.
class LoadWatchlistVersionIntegrationTest extends PostgresIntegrationSupport {

    private static final String SOURCE_ID = "stub-source";

    private static final WatchlistSourceAdapter STUB_ADAPTER = new WatchlistSourceAdapter() {
        @Override
        public String sourceId() {
            return SOURCE_ID;
        }

        @Override
        public String formatId() {
            return "stub-xml-v1";
        }

        @Override
        public CanonicalRecord toCanonical(RawRecord r) {
            return new CanonicalRecordBuilder()
                    .sourceId(SOURCE_ID)
                    .sourceRecordId(r.attributes().get("@id"))
                    .entityType(EntityType.INDIVIDUAL, "@id")
                    .name(r.get("name").get(0), NameType.PRIMARY, "name")
                    .build();
        }

        @Override
        public CapabilityExpectation expectation() {
            return new CapabilityExpectation(SOURCE_ID, Map.of(), Map.of());
        }

        @Override
        public Set<CanonicalSlot> requiredSlots() {
            return Set.of(CanonicalSlot.PRIMARY_NAME);
        }
    };

    private static byte[] fixtureWithRecords(int count) {
        StringBuilder xml = new StringBuilder("<records>");
        for (int i = 1; i <= count; i++) {
            xml.append("<record id=\"e-").append(i).append("\"><name>Person ").append(i)
                    .append("</name></record>");
        }
        xml.append("</records>");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String checksumOf(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void loadsAFixtureEndToEndReloadsIdempotentlyAndTombstonesARemovedRecord() throws Exception {
        try (Connection ingestConnection = connectAs("horus_ingest", INGEST_PASSWORD);
                Connection auditConnection = connectAs("horus_audit", AUDIT_PASSWORD)) {
            JdbcTemplate ingestJdbcTemplate =
                    new JdbcTemplate(new SingleConnectionDataSource(ingestConnection, true));
            JdbcTemplate auditJdbcTemplate =
                    new JdbcTemplate(new SingleConnectionDataSource(auditConnection, true));
            ObjectMapper objectMapper = new ObjectMapper();

            ListVersionRepository listVersionRepository =
                    new JdbcListVersionRepository(ingestJdbcTemplate, objectMapper);
            WatchEntityRepository watchEntityRepository = new JdbcWatchEntityRepository(ingestJdbcTemplate);
            WatchEntityVersionWriter watchEntityVersionWriter =
                    new JdbcWatchEntityVersionWriter(ingestJdbcTemplate);
            ActiveEntityVersionLookup activeEntityVersionLookup =
                    new JdbcActiveEntityVersionLookup(ingestJdbcTemplate);
            IndexReconciliation indexReconciliation = new JdbcIndexReconciliation(ingestJdbcTemplate);
            AuditEventPort auditEventPort = new JdbcAuditEventPort(auditJdbcTemplate, objectMapper);

            // list_version.loaded_at orders a source's loads (V5), so it must advance per call.
            Clock clock = new Clock() {
                private Instant now = Instant.parse("2026-09-21T01:00:00Z");

                @Override
                public ZoneOffset getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(java.time.ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    now = now.plusSeconds(60);
                    return now;
                }
            };
            IdGenerator idGenerator = UUID::randomUUID;
            RecordAuditEvent recordAuditEvent = new RecordAuditEvent(auditEventPort, idGenerator, clock);
            ReconcileVersion reconcileVersion = new ReconcileVersion();
            PromoteListVersion promoteListVersion =
                    new PromoteListVersion(listVersionRepository, indexReconciliation, recordAuditEvent);

            AdapterRegistry adapterRegistry = new AdapterRegistry();
            adapterRegistry.register(STUB_ADAPTER);
            Map<String, FormatReader> formatReaders =
                    Map.of(STUB_ADAPTER.formatId(), new XmlFormatReader("record"));

            LoadWatchlistVersion loadWatchlistVersion = new LoadWatchlistVersion(
                    adapterRegistry, formatReaders, NormalisationPipeline.standard(), listVersionRepository,
                    watchEntityRepository, watchEntityVersionWriter, activeEntityVersionLookup,
                    reconcileVersion, promoteListVersion, recordAuditEvent, idGenerator, clock);

            byte[] tenRecords = fixtureWithRecords(10);
            String checksum = checksumOf(tenRecords);

            LoadWatchlistVersion.Result first = loadWatchlistVersion.execute(new LoadWatchlistVersion.Command(
                    SOURCE_ID, new ByteArrayInputStream(tenRecords), "fixture-10.xml", checksum,
                    Optional.empty(), "svc-ingest-test"));

            assertThat(first.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.PROMOTED);
            assertThat(first.reconciliationReport().added()).isEqualTo(10);
            assertThat(first.reconciliationReport().rejected()).isEmpty();
            assertThat(listVersionRepository.findActive(SOURCE_ID)).isPresent();
            assertThat(listVersionRepository.findActive(SOURCE_ID).orElseThrow().status())
                    .isEqualTo(LoadStatus.ACTIVE);

            // "Done when": loading the same fixture twice produces no new versions the second time.
            LoadWatchlistVersion.Result second = loadWatchlistVersion.execute(new LoadWatchlistVersion.Command(
                    SOURCE_ID, new ByteArrayInputStream(tenRecords), "fixture-10.xml", checksum,
                    Optional.empty(), "svc-ingest-test"));

            assertThat(second.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.ALREADY_LOADED);
            assertThat(second.listVersionId()).isEqualTo(first.listVersionId());

            Long entityVersionCount = ingestJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM watch_entity_version WHERE list_version_id = ?",
                    Long.class, first.listVersionId().value());
            assertThat(entityVersionCount).isEqualTo(10L);

            // "Done when": a fixture with one record removed produces exactly one tombstone.
            byte[] nineRecords = fixtureWithRecords(9);
            LoadWatchlistVersion.Result third = loadWatchlistVersion.execute(new LoadWatchlistVersion.Command(
                    SOURCE_ID, new ByteArrayInputStream(nineRecords), "fixture-9.xml",
                    checksumOf(nineRecords), Optional.empty(), "svc-ingest-test"));

            assertThat(third.reconciliationReport().delisted()).isEqualTo(1);
            assertThat(third.reconciliationReport().added()).isZero();
            assertThat(third.reconciliationReport().amended()).isZero();
            assertThat(third.reconciliationReport().unchanged()).isEqualTo(9);
            Long delistedCount = ingestJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM watch_entity_version WHERE list_version_id = ? AND status = 'DELISTED'",
                    Long.class, third.listVersionId().value());
            assertThat(delistedCount).isEqualTo(1L);

            // Regression (found in Step 7 review): unchanged entities keep their version row in
            // the load that created it, so "current" must span superseded loads. A fourth load
            // amending one record must see 8 unchanged, 1 amended, nothing added, and must not
            // re-tombstone the entity already delisted by load three.
            String amended = new String(nineRecords, StandardCharsets.UTF_8)
                    .replace("Person 2<", "Person Two<");
            byte[] nineWithOneAmended = amended.getBytes(StandardCharsets.UTF_8);
            LoadWatchlistVersion.Result fourth = loadWatchlistVersion.execute(new LoadWatchlistVersion.Command(
                    SOURCE_ID, new ByteArrayInputStream(nineWithOneAmended), "fixture-9b.xml",
                    checksumOf(nineWithOneAmended), Optional.empty(), "svc-ingest-test"));

            assertThat(fourth.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.PROMOTED);
            assertThat(fourth.reconciliationReport().added()).isZero();
            assertThat(fourth.reconciliationReport().amended()).isEqualTo(1);
            assertThat(fourth.reconciliationReport().unchanged()).isEqualTo(8);
            assertThat(fourth.reconciliationReport().delisted()).isZero();
        }
    }
}
