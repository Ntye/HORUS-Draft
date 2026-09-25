package horus.adapter.screening;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import horus.adapter.index.CoLocatedIndexAdapter;
import horus.adapter.persistence.JdbcActiveEntityVersionLookup;
import horus.adapter.persistence.JdbcActiveListVersionLookup;
import horus.adapter.persistence.JdbcAuditEventPort;
import horus.adapter.persistence.JdbcCandidateViewAssembler;
import horus.adapter.persistence.JdbcIndexReconciliation;
import horus.adapter.persistence.JdbcListVersionRepository;
import horus.adapter.persistence.JdbcMatchConfigRepository;
import horus.adapter.persistence.JdbcScreeningRepository;
import horus.adapter.persistence.JdbcWatchEntityRepository;
import horus.adapter.persistence.JdbcWatchEntityVersionWriter;
import horus.adapter.persistence.PostgresIntegrationSupport;
import horus.application.ingestion.LoadWatchlistVersion;
import horus.application.ingestion.PromoteListVersion;
import horus.application.ingestion.ReconcileVersion;
import horus.application.ingestion.RecordAuditEvent;
import horus.application.port.IdGenerator;
import horus.application.port.ScreeningRecord;
import horus.application.screening.LoadScreeningConfig;
import horus.application.screening.PublishConfigVersion;
import horus.application.screening.ScreenAName;
import horus.application.screening.ScreeningUnavailableException;
import horus.blocking.CandidateGenerator;
import horus.blocking.Strategy;
import horus.domain.audit.ActorKind;
import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.DecisionBand;
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
import horus.matching.MatchConfig;
import horus.matching.MatchingEngine;
import horus.matching.Registry;
import horus.matching.WhitelistView;
import horus.normalisation.NormalisationPipeline;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.ObjectMapper;

// Step 9 "done when": an end-to-end test on a synthetic list screens a name and returns a stored,
// explained, reconstructible result -- through the REAL ingest pipeline, the REAL Postgres
// adapters and the REAL roles (ingest writes the list, screen reads it and writes evidence, audit
// records). Every name is invented (CLAUDE.md §3).
class ScreeningEndToEndIntegrationTest extends PostgresIntegrationSupport {

    private static final String SOURCE_ID = "e2e-source";
    private static final String QUERY_NAME = "Zorvan Talmesc";

    private static final WatchlistSourceAdapter STUB_ADAPTER = new WatchlistSourceAdapter() {
        @Override
        public String sourceId() {
            return SOURCE_ID;
        }

        @Override
        public String formatId() {
            return "e2e-xml-v1";
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

    private static byte[] listOf(String... names) {
        StringBuilder xml = new StringBuilder("<records>");
        for (int i = 0; i < names.length; i++) {
            xml.append("<record id=\"e2e-").append(i + 1).append("\"><name>").append(names[i])
                    .append("</name></record>");
        }
        return xml.append("</records>").toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String checksumOf(byte[] bytes) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Captures everything logged through Logback and java.util.logging while it is open. */
    private static final class LogCapture implements AutoCloseable {
        private final ListAppender<ILoggingEvent> logback = new ListAppender<>();
        private final List<String> jul = new ArrayList<>();
        private final Handler julHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                jul.add(record.getLoggerName() + " " + record.getMessage() + " "
                        + java.util.Arrays.toString(record.getParameters()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        private final Level previousLevel = root.getLevel();
        private final java.util.logging.Logger julRoot = java.util.logging.Logger.getLogger("");
        private final java.util.logging.Level previousJulLevel = julRoot.getLevel();

        LogCapture() {
            // DEBUG on everything is stricter than the deployed default (INFO): if a name never
            // appears even here, it does not appear in production logs.
            root.setLevel(Level.DEBUG);
            logback.start();
            root.addAppender(logback);
            julRoot.setLevel(java.util.logging.Level.FINE);
            julRoot.addHandler(julHandler);
        }

        List<String> lines() {
            List<String> all = new ArrayList<>();
            logback.list.forEach(e -> all.add(e.getLoggerName() + " " + e.getFormattedMessage()
                    + (e.getThrowableProxy() == null ? "" : " " + e.getThrowableProxy().getMessage())));
            all.addAll(jul);
            return all;
        }

        @Override
        public void close() {
            root.detachAppender(logback);
            root.setLevel(previousLevel);
            julRoot.removeHandler(julHandler);
            julRoot.setLevel(previousJulLevel);
        }
    }

    private static Clock tickingClock() {
        return new Clock() {
            private Instant now = Instant.parse("2026-09-24T09:00:00Z");

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public synchronized Instant instant() {
                now = now.plusSeconds(60);
                return now;
            }
        };
    }

    @Test
    void screensASyntheticListAndReturnsAStoredExplainedReconstructibleResult() throws Exception {
        try (Connection ingestConnection = connectAs("horus_ingest", INGEST_PASSWORD);
                Connection screenConnection = connectAs("horus_screen", SCREEN_PASSWORD);
                Connection auditConnection = connectAs("horus_audit", AUDIT_PASSWORD)) {
            JdbcTemplate ingestT = new JdbcTemplate(new SingleConnectionDataSource(ingestConnection, true));
            SingleConnectionDataSource screenDs = new SingleConnectionDataSource(screenConnection, true);
            JdbcTemplate screenT = new JdbcTemplate(screenDs);
            JdbcTemplate auditT = new JdbcTemplate(new SingleConnectionDataSource(auditConnection, true));
            ObjectMapper om = new ObjectMapper();
            Clock clock = tickingClock();
            IdGenerator ids = UUID::randomUUID;
            NormalisationPipeline pipeline = NormalisationPipeline.standard();

            var auditPort = new JdbcAuditEventPort(auditT, om);
            var recordAudit = new RecordAuditEvent(auditPort, ids, clock);
            var listVersions = new JdbcListVersionRepository(ingestT, om);
            var promote = new PromoteListVersion(listVersions, new JdbcIndexReconciliation(ingestT), recordAudit);
            AdapterRegistry adapters = new AdapterRegistry();
            adapters.register(STUB_ADAPTER);
            Map<String, FormatReader> readers = Map.of(STUB_ADAPTER.formatId(), new XmlFormatReader("record"));
            var load = new LoadWatchlistVersion(adapters, readers, pipeline, listVersions,
                    new JdbcWatchEntityRepository(ingestT), new JdbcWatchEntityVersionWriter(ingestT),
                    new JdbcActiveEntityVersionLookup(ingestT), new ReconcileVersion(), promote, recordAudit, ids,
                    clock);

            // ---- 1. ingest a synthetic list through the real pipeline ----
            byte[] firstList = listOf("Zorvan Talmesk", "Quillon Marbrek", "Harbin Osterfeld", "Ostrel Vandermere");
            var loaded = load.execute(new LoadWatchlistVersion.Command(SOURCE_ID,
                    new ByteArrayInputStream(firstList), "e2e-1.xml", checksumOf(firstList), Optional.empty(),
                    "svc-e2e"));
            assertThat(loaded.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.PROMOTED);

            // ---- wire the screening side on the screen role ----
            var activeLists = new JdbcActiveListVersionLookup(screenT, om);
            var configs = new JdbcMatchConfigRepository(screenT, ingestT, om);
            var screenings = new JdbcScreeningRepository(screenT);
            Registry registry = Registry.standard();
            var generator = new CandidateGenerator(new CoLocatedIndexAdapter(screenDs, 100, 0.3),
                    List.of(Strategy.values()));
            var loadConfig = new LoadScreeningConfig(configs, activeLists, registry, clock);
            var screen = new ScreenAName(pipeline, generator, new JdbcCandidateViewAssembler(screenT),
                    new MatchingEngine(registry), loadConfig, query -> WhitelistView.empty(), screenings,
                    recordAudit, ids, clock);
            var publish = new PublishConfigVersion(configs, activeLists, registry, recordAudit, ids, clock);

            ScreenAName.Command command = new ScreenAName.Command("e2e-test", Optional.of("ref-1"), "e2e",
                    "operator-e2e", ActorKind.OPERATOR, "e2e-key-1", List.of(new ScreenAName.SubjectInput(
                            Optional.empty(), QUERY_NAME, Optional.of(EntityType.INDIVIDUAL), Optional.empty(),
                            Set.of(), Set.of())));

            // ---- 2. no approved configuration yet: screening is unavailable, never a clear ----
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> screen.execute(command))
                    .isInstanceOf(ScreeningUnavailableException.class);

            // ---- 3. publish a config the source can support (I-7 validated at publication) ----
            MatchConfig config = MatchConfig.defaults().withEnabledComparators(Set.of());
            UUID configId = publish.execute(new PublishConfigVersion.Command("e2e", config, "operator-e2e", "lead"));

            // ---- 4. screen while capturing every log line ----
            ScreenAName.Result result;
            List<String> logged;
            try (LogCapture capture = new LogCapture()) {
                result = screen.execute(command);
                logged = capture.lines();
            }

            assertThat(result.outcome()).isIn(DecisionBand.POSSIBLE_MATCH, DecisionBand.STRONG_MATCH);
            // I-12: provenance on the result.
            assertThat(result.request().listVersionIds()).contains(loaded.listVersionId());
            assertThat(result.request().configVersionId()).isEqualTo(configId);
            assertThat(result.request().pipelineVersion()).isEqualTo(pipeline.pipelineVersion());

            // I-11: the screened name appears in no log line, at DEBUG, from Logback or JUL.
            assertThat(logged).isNotEmpty();
            assertThat(logged).noneSatisfy(line -> assertThat(line.toLowerCase())
                    .contains("zorvan").contains("talmesc"));
            assertThat(logged).noneSatisfy(line -> assertThat(line.toLowerCase()).contains(QUERY_NAME.toLowerCase()));

            // ---- 5. stored, explained, reconstructible: read back as recorded, no recompute ----
            ScreeningRecord stored = screenings.findById(result.request().screeningId()).orElseThrow();
            assertThat(stored.request()).isEqualTo(result.request());
            var topCandidate = stored.subjects().get(0).candidates().get(0);
            assertThat(topCandidate.rank()).isEqualTo(1);
            assertThat(topCandidate.explanationJson())
                    .isEqualTo(result.subjects().get(0).candidates().get(0).candidate().explanationJson());
            assertThat(topCandidate.explanationJson()).contains("\"features\"").contains("\"matchedName\":\"Zorvan Talmesk\"");
            assertThat(result.subjects().get(0).candidates().get(0).explanation().orElseThrow().recompute())
                    .isEqualTo(topCandidate.compositeScore());

            // ---- 6. idempotent replay returns the same stored screening ----
            ScreenAName.Result replay = screen.execute(command);
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.request().screeningId()).isEqualTo(result.request().screeningId());

            // ---- 7. a clear name is a recorded NO_MATCH, not an absence of evidence ----
            var clear = screen.execute(new ScreenAName.Command("e2e-test", Optional.empty(), "e2e", "operator-e2e",
                    ActorKind.OPERATOR, "e2e-key-2", List.of(new ScreenAName.SubjectInput(Optional.empty(),
                            "Wexler Pomfrett", Optional.of(EntityType.INDIVIDUAL), Optional.empty(), Set.of(),
                            Set.of()))));
            assertThat(clear.outcome()).isEqualTo(DecisionBand.NO_MATCH);
            assertThat(screenings.findById(clear.request().screeningId())).isPresent();

            // ---- 8. de-list an entity by omitting it from a reload: still a screenable candidate (I-8) ----
            byte[] secondList = listOf("Zorvan Talmesk", "Harbin Osterfeld", "Ostrel Vandermere");
            var reloaded = load.execute(new LoadWatchlistVersion.Command(SOURCE_ID,
                    new ByteArrayInputStream(secondList), "e2e-2.xml", checksumOf(secondList), Optional.empty(),
                    "svc-e2e"));
            assertThat(reloaded.reconciliationReport().delisted()).isEqualTo(1);

            var delisted = screen.execute(new ScreenAName.Command("e2e-test", Optional.empty(), "e2e",
                    "operator-e2e", ActorKind.OPERATOR, "e2e-key-3", List.of(new ScreenAName.SubjectInput(
                            Optional.empty(), "Quillon Marbrek", Optional.of(EntityType.INDIVIDUAL), Optional.empty(),
                            Set.of(), Set.of()))));
            var delistedTop = delisted.subjects().get(0).candidates().get(0);
            assertThat(delisted.outcome()).isNotEqualTo(DecisionBand.NO_MATCH);
            assertThat(delistedTop.explanation().orElseThrow().matchedName()).isEqualTo("Quillon Marbrek");
            // No comparators were enabled, so this entity is not demoted here -- the point is that
            // the de-listed entity is still FOUND and scored. Status demotion is proven in the
            // matching unit tests and in the blocking integration test.
            assertThat(delistedTop.candidate().compositeScore()).isEqualTo(100);

            // ---- 9. the audit trail holds ids and counts, never the name ----
            Long auditRows = auditT.queryForObject(
                    "SELECT COUNT(*) FROM audit_event WHERE event_type = 'SCREENING_COMPLETED'", Long.class);
            assertThat(auditRows).isGreaterThanOrEqualTo(3L);
            Long leaked = auditT.queryForObject(
                    "SELECT COUNT(*) FROM audit_event WHERE payload::text ILIKE '%zorvan%' "
                            + "OR payload::text ILIKE '%marbrek%' OR subject_id ILIKE '%zorvan%'", Long.class);
            assertThat(leaked).isZero();
            assertThat(EnumSet.allOf(DecisionBand.class)).contains(result.outcome());
        }
    }
}
