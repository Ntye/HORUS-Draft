package horus.adapter.persistence;

import horus.application.port.EntityIdResolver;
import horus.domain.shared.EntityId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Read-only lookup on watch_entity, so it is handed the horus_screen credential.
@Component
public final class JdbcEntityIdResolver implements EntityIdResolver {

    private final JdbcTemplate jdbcTemplate;

    public JdbcEntityIdResolver(@Qualifier("screenJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<EntityId> resolve(String sourceId, String sourceEntityId) {
        return jdbcTemplate
                .query("SELECT entity_id FROM watch_entity WHERE source_id = ? AND source_entity_id = ?",
                        (rs, rowNum) -> new EntityId((UUID) rs.getObject("entity_id")), sourceId, sourceEntityId)
                .stream().findFirst();
    }
}
