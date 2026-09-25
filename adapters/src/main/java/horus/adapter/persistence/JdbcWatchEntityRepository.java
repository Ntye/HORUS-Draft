package horus.adapter.persistence;

import horus.application.port.WatchEntityRepository;
import horus.domain.shared.EntityId;
import horus.domain.watchentity.WatchEntity;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcWatchEntityRepository implements WatchEntityRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWatchEntityRepository(@Qualifier("ingestJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, EntityId> ensureAll(List<WatchEntity> candidates) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        String sourceId = candidates.get(0).sourceId();
        List<WatchEntity> distinct = distinctBySourceEntityId(candidates, sourceId);
        try {
            // DO NOTHING, not DO UPDATE: an existing identity row is never rewritten (I-6), and
            // this statement needs no UPDATE privilege, which is how that is proven. See the port
            // doc for why an existing row is the expected case rather than an error (D12 H-9).
            jdbcTemplate.batchUpdate(
                    "INSERT INTO watch_entity (entity_id, source_id, source_entity_id) VALUES (?, ?, ?) "
                            + "ON CONFLICT (source_id, source_entity_id) DO NOTHING",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int index) throws SQLException {
                            WatchEntity candidate = distinct.get(index);
                            ps.setObject(1, candidate.entityId().value());
                            ps.setString(2, candidate.sourceId());
                            ps.setString(3, candidate.sourceEntityId());
                        }

                        @Override
                        public int getBatchSize() {
                            return distinct.size();
                        }
                    });
            // One SELECT for the whole chunk rather than RETURNING, because ON CONFLICT DO NOTHING
            // returns no row for the conflicting case -- which is precisely the case being handled.
            // Reading every id back also means the caller never has to guess which insert landed.
            return readBack(sourceId, distinct);
        } catch (RuntimeException e) {
            throw PersistenceFailures.translate(e);
        }
    }

    private Map<String, EntityId> readBack(String sourceId, List<WatchEntity> candidates) {
        String[] keys = candidates.stream().map(WatchEntity::sourceEntityId).toArray(String[]::new);
        Map<String, EntityId> resolved = new LinkedHashMap<>();
        jdbcTemplate.query(
                con -> {
                    PreparedStatement ps = con.prepareStatement(
                            "SELECT source_entity_id, entity_id FROM watch_entity "
                                    + "WHERE source_id = ? AND source_entity_id = ANY(?)");
                    ps.setString(1, sourceId);
                    ps.setArray(2, con.createArrayOf("text", keys));
                    return ps;
                },
                rs -> {
                    resolved.put(
                            rs.getString("source_entity_id"),
                            new EntityId((UUID) rs.getObject("entity_id")));
                });
        return resolved;
    }

    // The same source record id twice in one chunk is one identity, not two. Keeping the first
    // occurrence makes the result depend on file order alone (I-4).
    private static List<WatchEntity> distinctBySourceEntityId(List<WatchEntity> candidates, String sourceId) {
        Map<String, WatchEntity> byKey = new LinkedHashMap<>();
        for (WatchEntity candidate : candidates) {
            if (!candidate.sourceId().equals(sourceId)) {
                // The read-back keys on source_entity_id alone, so mixing sources in one chunk
                // would silently return another source's identities. Fail on the bug instead.
                throw new IllegalArgumentException("ensureAll requires a single sourceId per call");
            }
            byKey.putIfAbsent(candidate.sourceEntityId(), candidate);
        }
        return List.copyOf(byKey.values());
    }
}
