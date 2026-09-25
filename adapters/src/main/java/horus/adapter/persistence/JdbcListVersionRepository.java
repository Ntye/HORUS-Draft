package horus.adapter.persistence;

import horus.application.port.ListVersionRepository;
import horus.domain.listversion.ListVersion;
import horus.domain.listversion.LoadStatus;
import horus.domain.listversion.SourceCapabilities;
import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.ListVersionId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

// horus_ingest may only ever UPDATE status (V3) and capabilities/record_count (V4) on this
// table -- see WatchlistRolePrivilegesTest for the DB-enforced proof. Every other column is
// write-once at save().
@Component
public final class JdbcListVersionRepository implements ListVersionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcListVersionRepository(
            @Qualifier("ingestJdbcTemplate") JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(ListVersion listVersion) {
        jdbcTemplate.update(
                "INSERT INTO list_version (list_version_id, source_id, format_id, mapping_id, "
                        + "source_file_name, source_file_checksum, vendor_published_at, loaded_at, "
                        + "record_count, status, loaded_by, pipeline_version, index_binding) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                listVersion.listVersionId().value(),
                listVersion.sourceId(),
                listVersion.formatId(),
                listVersion.mappingId().orElse(null),
                listVersion.sourceFileName(),
                listVersion.sourceFileChecksum(),
                listVersion.vendorPublishedAt().map(Timestamp::from).orElse(null),
                Timestamp.from(listVersion.loadedAt()),
                listVersion.recordCount(),
                listVersion.status().name(),
                listVersion.loadedBy(),
                listVersion.pipelineVersion(),
                listVersion.indexBinding().orElse(null));
    }

    @Override
    public void updateStatus(ListVersionId listVersionId, LoadStatus newStatus) {
        jdbcTemplate.update(
                "UPDATE list_version SET status = ? WHERE list_version_id = ?",
                newStatus.name(), listVersionId.value());
    }

    @Override
    public void updateMeasuredTotals(ListVersionId listVersionId, long recordCount, SourceCapabilities capabilities) {
        jdbcTemplate.update(
                "UPDATE list_version SET record_count = ?, capabilities = ?::jsonb WHERE list_version_id = ?",
                recordCount, toJson(capabilities), listVersionId.value());
    }

    @Override
    public Optional<ListVersion> findById(ListVersionId listVersionId) {
        return jdbcTemplate.query(
                        "SELECT * FROM list_version WHERE list_version_id = ?", this::mapRow, listVersionId.value())
                .stream().findFirst();
    }

    @Override
    public Optional<ListVersion> findActive(String sourceId) {
        return jdbcTemplate.query(
                        "SELECT * FROM list_version WHERE source_id = ? AND status = 'ACTIVE'", this::mapRow, sourceId)
                .stream().findFirst();
    }

    @Override
    public Optional<ListVersion> findMostRecentSuperseded(String sourceId) {
        return jdbcTemplate.query(
                        "SELECT * FROM list_version WHERE source_id = ? AND status = 'SUPERSEDED' "
                                + "ORDER BY loaded_at DESC LIMIT 1",
                        this::mapRow, sourceId)
                .stream().findFirst();
    }

    private String toJson(SourceCapabilities capabilities) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode coverage = root.putObject("coverage");
        capabilities.coverage().forEach((slot, value) -> coverage.put(slot.name(), value));
        root.put("derivedAt", capabilities.derivedAt().toString());
        return root.toString();
    }

    private Optional<SourceCapabilities> parseCapabilities(
            ListVersionId listVersionId, String sourceId, String json) {
        if (json == null) {
            return Optional.empty();
        }
        JsonNode root = readTree(json);
        Map<CanonicalSlot, Double> coverage = new EnumMap<>(CanonicalSlot.class);
        root.get("coverage").properties().forEach(
                entry -> coverage.put(CanonicalSlot.valueOf(entry.getKey()), entry.getValue().asDouble()));
        Instant derivedAt = Instant.parse(root.get("derivedAt").asText());
        return Optional.of(new SourceCapabilities(listVersionId, sourceId, coverage, derivedAt));
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalStateException("stored list_version.capabilities is not valid JSON", e);
        }
    }

    private ListVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
        ListVersionId listVersionId = new ListVersionId((UUID) rs.getObject("list_version_id"));
        String sourceId = rs.getString("source_id");
        UUID mappingId = (UUID) rs.getObject("mapping_id");
        Timestamp vendorPublishedAt = rs.getTimestamp("vendor_published_at");
        String indexBinding = rs.getString("index_binding");
        return new ListVersion(
                listVersionId,
                sourceId,
                rs.getString("format_id"),
                Optional.ofNullable(mappingId),
                rs.getString("source_file_name"),
                rs.getString("source_file_checksum"),
                Optional.ofNullable(vendorPublishedAt).map(Timestamp::toInstant),
                rs.getTimestamp("loaded_at").toInstant(),
                rs.getLong("record_count"),
                LoadStatus.valueOf(rs.getString("status")),
                rs.getString("loaded_by"),
                parseCapabilities(listVersionId, sourceId, rs.getString("capabilities")),
                rs.getString("pipeline_version"),
                Optional.ofNullable(indexBinding));
    }
}
