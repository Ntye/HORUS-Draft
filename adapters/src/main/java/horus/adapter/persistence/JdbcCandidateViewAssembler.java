package horus.adapter.persistence;

import horus.application.port.CandidateViewAssembler;
import horus.blocking.CandidateKey;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityType;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.Provenance;
import horus.matching.CandidateDob;
import horus.matching.CandidateIdentifier;
import horus.matching.CandidateView;
import horus.normalisation.NormalisedName;
import horus.normalisation.TokenSorter;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Builds CandidateViews from the rows of the versions blocking found (CLAUDE.md §5). Names come
// from the stored, already-normalised columns: ScreenAName's pipeline-version gate guarantees they
// were produced by the same pipeline as the query (I-10), so nothing is re-normalised here.
// Rows are ordered so the view -- and therefore every explanation built from it -- is deterministic.
@Component
public final class JdbcCandidateViewAssembler implements CandidateViewAssembler {

    private static final TokenSorter TOKEN_SORTER = new TokenSorter();

    private final DataSource dataSource;

    public JdbcCandidateViewAssembler(@Qualifier("screenJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.dataSource = jdbcTemplate.getDataSource();
    }

    @Override
    public Map<EntityVersionId, CandidateView> assemble(Collection<CandidateKey> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        UUID[] versionIds = keys.stream().map(k -> k.entityVersionId().value()).sorted().toArray(UUID[]::new);
        try (Connection connection = dataSource.getConnection()) {
            Array ids = connection.createArrayOf("uuid", versionIds);
            Map<UUID, CandidateView.Builder> builders = versions(connection, ids);
            names(connection, ids, builders);
            dobs(connection, ids, builders);
            countries(connection, ids, builders);
            identifiers(connection, ids, builders);

            Map<EntityVersionId, CandidateView> views = new LinkedHashMap<>();
            for (UUID id : versionIds) {
                CandidateView.Builder builder = builders.get(id);
                if (builder != null) {
                    views.put(new EntityVersionId(id), builder.build());
                }
            }
            return views;
        } catch (SQLException e) {
            // A missing entry is treated as an error by the caller (I-1); an SQL failure must not
            // be silently turned into "no candidates", so it propagates.
            throw new IllegalStateException("candidate views could not be assembled");
        }
    }

    private static Map<UUID, CandidateView.Builder> versions(Connection connection, Array ids) throws SQLException {
        // delisted = the entity's CURRENT version is a tombstone. The candidate key points at the
        // last live version (which holds the names), so status is read from the current one.
        String sql = "SELECT wev.entity_version_id, wev.entity_id, we.source_id, wev.entity_type, "
                + "EXISTS (SELECT 1 FROM current_watch_entity_version c "
                + "WHERE c.entity_id = wev.entity_id AND c.status = 'DELISTED') AS delisted "
                + "FROM watch_entity_version wev JOIN watch_entity we ON we.entity_id = wev.entity_id "
                + "WHERE wev.entity_version_id = ANY(?)";
        Map<UUID, CandidateView.Builder> builders = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID versionId = (UUID) rs.getObject("entity_version_id");
                    builders.put(versionId, CandidateView.builder(
                                    new EntityId((UUID) rs.getObject("entity_id")), new EntityVersionId(versionId),
                                    rs.getString("source_id"), EntityType.valueOf(rs.getString("entity_type")))
                            .delisted(rs.getBoolean("delisted")));
                }
            }
        }
        return builders;
    }

    private static void names(Connection connection, Array ids, Map<UUID, CandidateView.Builder> builders)
            throws SQLException {
        String sql = "SELECT entity_version_id, name_type, raw_name, normalised_name, name_tokens, phonetic_codes, "
                + "trigrams, script FROM watch_name WHERE entity_version_id = ANY(?) "
                + "ORDER BY entity_version_id, (name_type = 'PRIMARY') DESC, raw_name, name_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CandidateView.Builder builder = builders.get((UUID) rs.getObject("entity_version_id"));
                    if (builder == null) {
                        continue;
                    }
                    List<String> tokens = List.of((String[]) rs.getArray("name_tokens").getArray());
                    NormalisedName name = new NormalisedName(
                            rs.getString("raw_name"), rs.getString("normalised_name"), tokens,
                            TOKEN_SORTER.sort(tokens), List.of((String[]) rs.getArray("phonetic_codes").getArray()),
                            List.of((String[]) rs.getArray("trigrams").getArray()),
                            rs.getString("script") == null ? "COMMON" : rs.getString("script"));
                    if ("PRIMARY".equals(rs.getString("name_type"))) {
                        builder.primaryName(name);
                    } else {
                        builder.alias(name);
                    }
                }
            }
        }
    }

    private static void dobs(Connection connection, Array ids, Map<UUID, CandidateView.Builder> builders)
            throws SQLException {
        String sql = "SELECT entity_version_id, year, month, day, age, as_of_date FROM watch_dob "
                + "WHERE entity_version_id = ANY(?) ORDER BY entity_version_id, dob_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CandidateView.Builder builder = builders.get((UUID) rs.getObject("entity_version_id"));
                    if (builder != null) {
                        java.sql.Date asOf = rs.getDate("as_of_date");
                        builder.dob(new CandidateDob(
                                optional(rs, "year"), optional(rs, "month"), optional(rs, "day"), optional(rs, "age"),
                                asOf == null ? OptionalInt.empty() : OptionalInt.of(asOf.toLocalDate().getYear())));
                    }
                }
            }
        }
    }

    private static void countries(Connection connection, Array ids, Map<UUID, CandidateView.Builder> builders)
            throws SQLException {
        String sql = "SELECT entity_version_id, COALESCE(iso_code, upper(raw_country)) AS code FROM watch_country "
                + "WHERE entity_version_id = ANY(?) ORDER BY entity_version_id, country_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CandidateView.Builder builder = builders.get((UUID) rs.getObject("entity_version_id"));
                    if (builder != null) {
                        builder.country(rs.getString("code"));
                    }
                }
            }
        }
    }

    private static void identifiers(Connection connection, Array ids, Map<UUID, CandidateView.Builder> builders)
            throws SQLException {
        String sql = "SELECT entity_version_id, normalised_id_value, provenance_rule_id, provenance_start, "
                + "provenance_end FROM watch_identifier WHERE entity_version_id = ANY(?) "
                + "ORDER BY entity_version_id, identifier_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    CandidateView.Builder builder = builders.get((UUID) rs.getObject("entity_version_id"));
                    if (builder != null) {
                        String rule = rs.getString("provenance_rule_id");
                        Optional<Provenance> provenance = rule == null
                                ? Optional.empty()
                                : Optional.of(new Provenance(rule, rs.getInt("provenance_start"),
                                        rs.getInt("provenance_end")));
                        builder.identifier(new CandidateIdentifier(rs.getString("normalised_id_value"), provenance));
                    }
                }
            }
        }
    }

    private static OptionalInt optional(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? OptionalInt.empty() : OptionalInt.of(value);
    }
}
