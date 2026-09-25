package horus.adapter.persistence;

import horus.application.port.ActiveEntitySnapshot;
import horus.application.port.ActiveEntityVersionLookup;
import horus.domain.shared.EntityId;
import horus.domain.watchentity.WatchEntityVersion;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Bulk-fetched once per load (see ActiveEntityVersionLookup's port doc for the memory tradeoff
// this accepts for the real feed).
@Component
public final class JdbcActiveEntityVersionLookup implements ActiveEntityVersionLookup {

    // V5: the newest version of each entity across the source's ACTIVE and SUPERSEDED loads up
    // to the active one. Filtering on lv.status = 'ACTIVE' alone would only see versions written
    // by the latest load and treat every unchanged entity as new.
    private static final String CURRENT_VERSION_JOIN =
            "FROM current_watch_entity_version c "
                    + "JOIN watch_entity we ON we.entity_id = c.entity_id "
                    + "WHERE c.source_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public JdbcActiveEntityVersionLookup(@Qualifier("ingestJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, ActiveEntitySnapshot> loadActiveSnapshot(String sourceId) {
        Map<String, ActiveEntitySnapshot> snapshot = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT we.source_entity_id, c.entity_id, c.content_hash, (c.status = 'DELISTED') AS delisted "
                        + CURRENT_VERSION_JOIN,
                rs -> {
                    snapshot.put(
                            rs.getString("source_entity_id"),
                            new ActiveEntitySnapshot(
                                    new EntityId((UUID) rs.getObject("entity_id")), rs.getString("content_hash"),
                                    rs.getBoolean("delisted")));
                },
                sourceId);
        return snapshot;
    }

    @Override
    public Optional<WatchEntityVersion> findActiveVersion(String sourceId, String sourceEntityId) {
        return jdbcTemplate.query(
                        "SELECT c.* " + CURRENT_VERSION_JOIN + " AND we.source_entity_id = ?",
                        (rs, rowNum) -> WatchEntityVersionRowMapper.map(rs),
                        sourceId, sourceEntityId)
                .stream().findFirst();
    }
}
