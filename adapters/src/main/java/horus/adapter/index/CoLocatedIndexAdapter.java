package horus.adapter.index;

import horus.blocking.BlockingIndex;
import horus.blocking.BlockingOutcome;
import horus.blocking.CandidateKey;
import horus.blocking.Completeness;
import horus.blocking.IndexCapabilities;
import horus.blocking.KeyProbe;
import horus.blocking.Strategy;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;

// "Co-located": the index is the watchlist database itself (pg_trgm GIN + expression indexes from
// V1/V6), so it can never be out of step with the data it indexes. Built on horus_screen, which
// can read the watchlist and nothing else (§11).
//
// I-1: every lookup fetches limit+1 rows; the extra row is the only way to know the cap cut the
// list, and that is reported as TRUNCATED rather than silently returned as complete. SQL failures
// propagate as exceptions -- CandidateGenerator turns them into PARTIAL. Nothing is caught here.
// I-11: query text only ever travels as a bound parameter; nothing is logged.
public final class CoLocatedIndexAdapter implements BlockingIndex {

    private static final String SCREENABLE_JOIN =
            "JOIN screenable_watch_entity_version s ON s.entity_version_id = wn.entity_version_id ";

    private final DataSource screenDataSource;
    private final int maxCandidatesPerStrategy;
    private final double trigramThreshold;

    public CoLocatedIndexAdapter(DataSource screenDataSource, int maxCandidatesPerStrategy, double trigramThreshold) {
        if (screenDataSource == null) {
            throw new IllegalArgumentException("screenDataSource must not be null");
        }
        // I-7: bad configuration fails at construction (boot), not during a screening.
        if (maxCandidatesPerStrategy < 1) {
            throw new IllegalArgumentException("maxCandidatesPerStrategy must be at least 1");
        }
        if (!(trigramThreshold > 0.0 && trigramThreshold <= 1.0)) {
            throw new IllegalArgumentException("trigramThreshold must be in (0, 1]");
        }
        this.screenDataSource = screenDataSource;
        this.maxCandidatesPerStrategy = maxCandidatesPerStrategy;
        this.trigramThreshold = trigramThreshold;
    }

    @Override
    public IndexCapabilities capabilities() {
        return new IndexCapabilities(EnumSet.allOf(Strategy.class));
    }

    @Override
    public BlockingOutcome lookup(KeyProbe probe) {
        Connection connection = DataSourceUtils.getConnection(screenDataSource);
        try {
            List<CandidateKey> rows = switch (probe.strategy()) {
                case EXACT_HASH -> exact(connection, probe);
                case TRIGRAM -> trigram(connection, probe);
                case PHONETIC -> phonetic(connection, probe);
            };
            boolean truncated = rows.size() > maxCandidatesPerStrategy;
            List<CandidateKey> kept = truncated ? rows.subList(0, maxCandidatesPerStrategy) : rows;
            return new BlockingOutcome(
                    probe.strategy(), truncated ? Completeness.TRUNCATED : Completeness.COMPLETE, kept);
        } catch (SQLException e) {
            throw new IllegalStateException("blocking lookup failed for strategy " + probe.strategy());
        } finally {
            DataSourceUtils.releaseConnection(connection, screenDataSource);
        }
    }

    private List<CandidateKey> exact(Connection connection, KeyProbe probe) throws SQLException {
        String sql = "SELECT DISTINCT s.entity_id, s.entity_version_id FROM watch_name wn " + SCREENABLE_JOIN
                + "WHERE md5(wn.normalised_name) = md5(?) "
                + "OR md5(horus_sorted_key(wn.name_tokens)) = md5(horus_sorted_key(?::text[])) "
                + "ORDER BY s.entity_version_id LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, probe.name().normalised());
            statement.setArray(2, textArray(connection, probe.name().tokens()));
            statement.setInt(3, maxCandidatesPerStrategy + 1);
            return read(statement);
        }
    }

    private List<CandidateKey> phonetic(Connection connection, KeyProbe probe) throws SQLException {
        List<String> codes = probe.name().phoneticCodes().stream().filter(c -> c != null && !c.isEmpty()).toList();
        if (codes.isEmpty()) {
            // The name has no phonetic key (e.g. digits only). The strategy does not apply; the
            // other strategies still search, and the generator never lets an empty query through.
            return List.of();
        }
        String sql = "SELECT DISTINCT s.entity_id, s.entity_version_id FROM watch_name wn " + SCREENABLE_JOIN
                + "WHERE md5(horus_sorted_key(wn.phonetic_codes)) = md5(horus_sorted_key(?::text[])) "
                + "ORDER BY s.entity_version_id LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, textArray(connection, codes));
            statement.setInt(2, maxCandidatesPerStrategy + 1);
            return read(statement);
        }
    }

    private List<CandidateKey> trigram(Connection connection, KeyProbe probe) throws SQLException {
        String sql = "SELECT s.entity_id, s.entity_version_id FROM watch_name wn " + SCREENABLE_JOIN
                + "WHERE wn.normalised_name % ? "
                + "GROUP BY s.entity_id, s.entity_version_id "
                + "ORDER BY MAX(similarity(wn.normalised_name, ?)) DESC, s.entity_version_id LIMIT ?";
        // The % operator reads pg_trgm.similarity_threshold; set it transaction-locally so a
        // pooled connection never carries this value into another query.
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement threshold =
                    connection.prepareStatement("SELECT set_config('pg_trgm.similarity_threshold', ?, true)")) {
                threshold.setString(1, Double.toString(trigramThreshold));
                threshold.execute();
            }
            List<CandidateKey> rows;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, probe.name().normalised());
                statement.setString(2, probe.name().normalised());
                statement.setInt(3, maxCandidatesPerStrategy + 1);
                rows = read(statement);
            }
            connection.commit();
            return rows;
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static Array textArray(Connection connection, List<String> values) throws SQLException {
        return connection.createArrayOf("text", values.toArray());
    }

    private static List<CandidateKey> read(PreparedStatement statement) throws SQLException {
        List<CandidateKey> keys = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                keys.add(new CandidateKey(
                        new EntityId((UUID) rs.getObject("entity_id")),
                        new EntityVersionId((UUID) rs.getObject("entity_version_id"))));
            }
        }
        return keys;
    }
}
