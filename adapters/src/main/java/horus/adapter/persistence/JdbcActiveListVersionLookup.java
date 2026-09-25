package horus.adapter.persistence;

import horus.application.port.ActiveListVersion;
import horus.application.port.ActiveListVersionLookup;
import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.ListVersionId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Read-only on list_version, so it holds the horus_screen credential. A slot counts as a
// capability only if the load measured non-zero coverage for it: 0% coverage means the source
// cannot feed a comparator, however the column is mapped (§5 SourceCapabilities).
@Component
public final class JdbcActiveListVersionLookup implements ActiveListVersionLookup {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcActiveListVersionLookup(
            @Qualifier("screenJdbcTemplate") JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ActiveListVersion> findAllActive() {
        return jdbcTemplate.query(
                "SELECT list_version_id, source_id, pipeline_version, capabilities::text AS capabilities "
                        + "FROM list_version WHERE status = 'ACTIVE' ORDER BY list_version_id",
                (rs, rowNum) -> new ActiveListVersion(
                        new ListVersionId((UUID) rs.getObject("list_version_id")),
                        rs.getString("source_id"),
                        rs.getString("pipeline_version"),
                        slotsOf(rs.getString("capabilities"))));
    }

    private Set<CanonicalSlot> slotsOf(String json) {
        Set<CanonicalSlot> slots = EnumSet.noneOf(CanonicalSlot.class);
        if (json == null) {
            return slots;
        }
        try {
            JsonNode coverage = objectMapper.readTree(json).get("coverage");
            if (coverage != null) {
                coverage.properties().forEach(entry -> {
                    if (entry.getValue().asDouble() > 0.0) {
                        slots.add(CanonicalSlot.valueOf(entry.getKey()));
                    }
                });
            }
        } catch (JacksonException e) {
            throw new IllegalStateException("stored list_version.capabilities is not valid JSON", e);
        }
        return slots;
    }
}
