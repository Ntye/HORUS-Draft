package horus.adapter.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.adapter.persistence.PostgresIntegrationSupport;
import horus.blocking.BlockingOutcome;
import horus.blocking.CandidateGenerator;
import horus.blocking.CandidateKey;
import horus.blocking.CandidateSet;
import horus.blocking.Completeness;
import horus.blocking.KeyProbe;
import horus.blocking.Strategy;
import horus.normalisation.NormalisationPipeline;
import horus.normalisation.NormalisedName;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

// Real Postgres, real pg_trgm, and the real horus_screen role: what blocking finds is only as
// trustworthy as the SQL that finds it, and a fake index cannot prove that. All names are invented.
class CoLocatedIndexAdapterIntegrationTest extends PostgresIntegrationSupport {

    private static final NormalisationPipeline PIPELINE = NormalisationPipeline.standard();
    private static final String SOURCE = "blocking-src";

    private static final UUID LIVE_ENTITY = UUID.randomUUID();
    private static final UUID LIVE_VERSION = UUID.randomUUID();
    private static final UUID DELISTED_ENTITY = UUID.randomUUID();
    private static final UUID DELISTED_LAST_LIVE_VERSION = UUID.randomUUID();
    private static final UUID AMENDED_ENTITY = UUID.randomUUID();
    private static final UUID AMENDED_OLD_VERSION = UUID.randomUUID();
    private static final UUID AMENDED_NEW_VERSION = UUID.randomUUID();
    private static final UUID FAILED_LOAD_ENTITY = UUID.randomUUID();
    private static final UUID FAILED_LOAD_VERSION = UUID.randomUUID();

    @BeforeAll
    static void seed() throws Exception {
        try (Connection admin = adminConnection()) {
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(admin, true));
            UUID v1 = UUID.randomUUID();
            UUID v2 = UUID.randomUUID();
            UUID failed = UUID.randomUUID();
            listVersion(jdbc, v1, "SUPERSEDED", "2026-01-01T00:00:00Z");
            listVersion(jdbc, v2, "ACTIVE", "2026-02-01T00:00:00Z");
            listVersion(jdbc, failed, "FAILED", "2026-03-01T00:00:00Z");

            // Unchanged across loads: its only version sits in the SUPERSEDED load (I-6).
            entity(jdbc, LIVE_ENTITY, "live");
            version(jdbc, LIVE_VERSION, LIVE_ENTITY, v1, "ACTIVE", "Zorvan Talmesk");
            name(jdbc, LIVE_VERSION, "Zorvan Talmesk");
            name(jdbc, LIVE_VERSION, "Zed Talmesk");

            // De-listed by load two: the tombstone has no names, the last live version does.
            entity(jdbc, DELISTED_ENTITY, "delisted");
            version(jdbc, DELISTED_LAST_LIVE_VERSION, DELISTED_ENTITY, v1, "ACTIVE", "Quillon Marbrek");
            name(jdbc, DELISTED_LAST_LIVE_VERSION, "Quillon Marbrek");
            version(jdbc, UUID.randomUUID(), DELISTED_ENTITY, v2, "DELISTED", "Quillon Marbrek");

            // Amended in load two: only the newer version's name is the entity's name now.
            entity(jdbc, AMENDED_ENTITY, "amended");
            version(jdbc, AMENDED_OLD_VERSION, AMENDED_ENTITY, v1, "ACTIVE", "Ostrel Vandermeer");
            name(jdbc, AMENDED_OLD_VERSION, "Ostrel Vandermeer");
            version(jdbc, AMENDED_NEW_VERSION, AMENDED_ENTITY, v2, "ACTIVE", "Ostrel Vandermere");
            name(jdbc, AMENDED_NEW_VERSION, "Ostrel Vandermere");

            // Exists only in a load that never went live.
            entity(jdbc, FAILED_LOAD_ENTITY, "failedload");
            version(jdbc, FAILED_LOAD_VERSION, FAILED_LOAD_ENTITY, failed, "ACTIVE", "Nyrell Hazenwood");
            name(jdbc, FAILED_LOAD_VERSION, "Nyrell Hazenwood");

            // Enough near-identical names to exceed a cap of two.
            for (int i = 1; i <= 4; i++) {
                UUID entity = UUID.randomUUID();
                UUID version = UUID.randomUUID();
                entity(jdbc, entity, "trunc-" + i);
                version(jdbc, version, entity, v2, "ACTIVE", "Truncatable Sample " + i);
                name(jdbc, version, "Truncatable Sample " + i);
            }
        }
    }

    private static void listVersion(JdbcTemplate jdbc, UUID id, String status, String loadedAt) {
        jdbc.update(
                "INSERT INTO list_version (list_version_id, source_id, format_id, source_file_name, "
                        + "source_file_checksum, loaded_at, record_count, status, loaded_by, pipeline_version) "
                        + "VALUES (?, ?, 'f', 'f.xml', ?, ?::timestamptz, 1, ?, 'test', '1.0.0')",
                id, SOURCE, "sha-" + id, loadedAt, status);
    }

    private static void entity(JdbcTemplate jdbc, UUID id, String sourceEntityId) {
        jdbc.update("INSERT INTO watch_entity (entity_id, source_id, source_entity_id) VALUES (?, ?, ?)",
                id, SOURCE, sourceEntityId);
    }

    private static void version(JdbcTemplate jdbc, UUID id, UUID entityId, UUID listVersionId, String status,
            String primaryName) {
        jdbc.update(
                "INSERT INTO watch_entity_version (entity_version_id, entity_id, list_version_id, content_hash, "
                        + "entity_type, primary_name, status, delisted_at) "
                        + "VALUES (?, ?, ?, ?, 'INDIVIDUAL', ?, ?, CASE WHEN ? = 'DELISTED' THEN DATE '2026-02-01' END)",
                id, entityId, listVersionId, "h-" + id, primaryName, status, status);
    }

    private static void name(JdbcTemplate jdbc, UUID versionId, String raw) {
        // Ingested through the same pipeline the query goes through (I-10).
        NormalisedName n = PIPELINE.normalise(raw);
        jdbc.update(
                "INSERT INTO watch_name (name_id, entity_version_id, name_type, raw_name, normalised_name, "
                        + "name_tokens, phonetic_codes, trigrams, script) VALUES (?, ?, 'PRIMARY', ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), versionId, raw, n.normalised(), n.tokens().toArray(new String[0]),
                n.phoneticCodes().toArray(new String[0]), n.trigrams().toArray(new String[0]), n.script());
    }

    private static CoLocatedIndexAdapter adapter(Connection screen, int cap) {
        return new CoLocatedIndexAdapter(new SingleConnectionDataSource(screen, true), cap, 0.3);
    }

    private static BlockingOutcome lookup(CoLocatedIndexAdapter adapter, Strategy strategy, String query) {
        return adapter.lookup(new KeyProbe(strategy, PIPELINE.normalise(query)));
    }

    private static List<UUID> versions(BlockingOutcome outcome) {
        return outcome.candidates().stream().map(c -> c.entityVersionId().value()).toList();
    }

    @Test
    void exactHashFindsTheNameAndItsReorderingAndAnAlias() throws Exception {
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            CoLocatedIndexAdapter adapter = adapter(screen, 100);

            assertThat(versions(lookup(adapter, Strategy.EXACT_HASH, "Zorvan Talmesk"))).contains(LIVE_VERSION);
            assertThat(versions(lookup(adapter, Strategy.EXACT_HASH, "Talmesk Zorvan"))).contains(LIVE_VERSION);
            assertThat(versions(lookup(adapter, Strategy.EXACT_HASH, "Zed Talmesk"))).contains(LIVE_VERSION);
            assertThat(versions(lookup(adapter, Strategy.EXACT_HASH, "Zorvan Talmeskk"))).doesNotContain(LIVE_VERSION);
        }
    }

    @Test
    void trigramFindsAnEntityWhoseOnlyVersionSitsInASupersededLoad() throws Exception {
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            BlockingOutcome outcome = lookup(adapter(screen, 100), Strategy.TRIGRAM, "Zorvan Talmesc");

            assertThat(outcome.completeness()).isEqualTo(Completeness.COMPLETE);
            assertThat(versions(outcome)).contains(LIVE_VERSION);
        }
    }

    @Test
    void phoneticFindsASpellingVariant() throws Exception {
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            assertThat(versions(lookup(adapter(screen, 100), Strategy.PHONETIC, "Sorvan Talmesk")))
                    .contains(LIVE_VERSION);
        }
    }

    @Test
    void aDelistedEntityRemainsAScreenableCandidate() throws Exception {
        // I-2 / I-8: de-listing demotes and explains; it never removes a candidate.
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            CoLocatedIndexAdapter adapter = adapter(screen, 100);

            for (Strategy strategy : Strategy.values()) {
                assertThat(versions(lookup(adapter, strategy, "Quillon Marbrek")))
                        .as(strategy.name())
                        .contains(DELISTED_LAST_LIVE_VERSION);
            }
        }
    }

    @Test
    void anAmendedEntityIsFoundByItsCurrentVersionOnly() throws Exception {
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            CoLocatedIndexAdapter adapter = adapter(screen, 100);

            assertThat(versions(lookup(adapter, Strategy.EXACT_HASH, "Ostrel Vandermere")))
                    .contains(AMENDED_NEW_VERSION).doesNotContain(AMENDED_OLD_VERSION);
            assertThat(versions(lookup(adapter, Strategy.EXACT_HASH, "Ostrel Vandermeer")))
                    .doesNotContain(AMENDED_OLD_VERSION);
            // A near-miss spelling still reaches the current version through trigram blocking.
            assertThat(versions(lookup(adapter, Strategy.TRIGRAM, "Ostrel Vandermeer")))
                    .contains(AMENDED_NEW_VERSION).doesNotContain(AMENDED_OLD_VERSION);
        }
    }

    @Test
    void aLoadThatNeverWentLiveIsNotSearched() throws Exception {
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            CoLocatedIndexAdapter adapter = adapter(screen, 100);

            for (Strategy strategy : Strategy.values()) {
                assertThat(versions(lookup(adapter, strategy, "Nyrell Hazenwood")))
                        .as(strategy.name())
                        .doesNotContain(FAILED_LOAD_VERSION);
            }
        }
    }

    @Test
    void exceedingTheCapIsReportedAsTruncatedNotComplete() throws Exception {
        // I-1: silently returning the first N as if they were everything would be fail-open.
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            BlockingOutcome outcome = lookup(adapter(screen, 2), Strategy.TRIGRAM, "Truncatable Sample");

            assertThat(outcome.completeness()).isEqualTo(Completeness.TRUNCATED);
            assertThat(outcome.candidates()).hasSize(2);
        }
    }

    @Test
    void lookupsAreDeterministic() throws Exception {
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            CoLocatedIndexAdapter adapter = adapter(screen, 100);
            for (Strategy strategy : Strategy.values()) {
                assertThat(lookup(adapter, strategy, "Truncatable Sample"))
                        .isEqualTo(lookup(adapter, strategy, "Truncatable Sample"));
            }
        }
    }

    @Test
    void anUnavailableDatabaseSurfacesAsPartialThroughTheGenerator() throws Exception {
        // I-1: the adapter throws, the generator converts -- never an empty COMPLETE.
        Connection screen = connectAs("horus_screen", SCREEN_PASSWORD);
        CoLocatedIndexAdapter adapter = adapter(screen, 100);
        screen.close();
        CandidateGenerator generator = new CandidateGenerator(adapter, List.of(Strategy.values()));

        CandidateSet set = generator.generate(PIPELINE.normalise("Zorvan Talmesk"));

        assertThat(set.completeness()).isEqualTo(Completeness.PARTIAL);
        assertThat(set.candidates()).isEmpty();
    }

    @Test
    void theExceptionFromAFailedLookupNeverCarriesTheQueryName() throws Exception {
        // I-11
        Connection screen = connectAs("horus_screen", SCREEN_PASSWORD);
        CoLocatedIndexAdapter adapter = adapter(screen, 100);
        screen.close();

        assertThatThrownBy(() -> lookup(adapter, Strategy.EXACT_HASH, "Zorvan Talmesk"))
                .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContainIgnoringCase("zorvan"));
    }

    @Test
    void constructionRejectsInvalidConfiguration() throws Exception {
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            SingleConnectionDataSource ds = new SingleConnectionDataSource(screen, true);
            assertThatThrownBy(() -> new CoLocatedIndexAdapter(ds, 0, 0.3))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CoLocatedIndexAdapter(ds, 10, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CoLocatedIndexAdapter(ds, 10, 1.5))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void candidateKeysCarryTheStableEntityIdAlongsideTheVersion() throws Exception {
        try (Connection screen = connectAs("horus_screen", SCREEN_PASSWORD)) {
            List<CandidateKey> keys = lookup(adapter(screen, 100), Strategy.EXACT_HASH, "Zorvan Talmesk").candidates();

            assertThat(keys).anySatisfy(k -> {
                assertThat(k.entityId().value()).isEqualTo(LIVE_ENTITY);
                assertThat(k.entityVersionId().value()).isEqualTo(LIVE_VERSION);
            });
            assertThat(Instant.now()).isNotNull();
        }
    }
}
